package com.chat4all.file.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Multipart Upload Service
 * 
 * Handles S3 multipart uploads for files >100MB.
 * 
 * Workflow:
 * 1. Client calls initiateMultipartUpload() - gets uploadId
 * 2. Client calls generatePartUploadUrls() - gets presigned URLs for each part
 * 3. Client uploads parts directly to S3 using PUT requests
 * 4. Client calls completeMultipartUpload() - S3 assembles parts
 * 
 * Benefits:
 * - Parallel part uploads (faster for large files)
 * - Resumable uploads (retry failed parts)
 * - Reduced memory footprint (stream parts)
 * 
 * Implementation per FR-024 (files >100MB requirement)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${s3.bucket-name}")
    private String bucketName;

    @Value("${s3.upload-url-expiration:PT15M}")
    private Duration uploadUrlExpiration;

    /**
     * Part size for multipart upload
     * AWS minimum: 5MB (except last part)
     * Default: 10MB for better parallelism
     */
    private static final long PART_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Initiate multipart upload
     * 
     * Creates multipart upload session in S3.
     * Returns uploadId which must be used for all subsequent operations.
     * 
     * @param objectKey S3 object key
     * @param contentType File MIME type
     * @return Upload ID for this multipart upload session
     */
    public String initiateMultipartUpload(String objectKey, String contentType) {
        log.info("Initiating multipart upload: bucket={}, key={}, contentType={}",
            bucketName, objectKey, contentType);

        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentType(contentType)
            .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
        String uploadId = response.uploadId();

        log.info("Multipart upload initiated: uploadId={}, objectKey={}", uploadId, objectKey);

        return uploadId;
    }

    /**
     * Generate presigned URLs for uploading parts
     * 
     * Client will upload each part using PUT request to the presigned URL.
     * Parts are numbered sequentially starting from 1.
     * 
     * @param objectKey S3 object key
     * @param uploadId Upload ID from initiateMultipartUpload()
     * @param fileSize Total file size in bytes
     * @return List of presigned URLs for each part
     */
    public List<PartUploadUrl> generatePartUploadUrls(String objectKey, String uploadId, long fileSize) {
        log.info("Generating part upload URLs: objectKey={}, uploadId={}, fileSize={}",
            objectKey, uploadId, fileSize);

        // Calculate number of parts
        int partCount = (int) Math.ceil((double) fileSize / PART_SIZE);

        log.debug("File will be split into {} parts (size={} bytes each)", partCount, PART_SIZE);

        List<PartUploadUrl> partUrls = new ArrayList<>();

        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            // Calculate part size (last part may be smaller)
            long partSize = Math.min(PART_SIZE, fileSize - (partNumber - 1) * PART_SIZE);

            // Generate presigned URL for this part
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(uploadUrlExpiration)
                .uploadPartRequest(uploadPartRequest)
                .build();

            PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);
            String presignedUrl = presignedRequest.url().toString();

            partUrls.add(PartUploadUrl.builder()
                .partNumber(partNumber)
                .uploadUrl(presignedUrl)
                .partSize(partSize)
                .build());

            log.debug("Generated presigned URL for part {}/{}: size={} bytes",
                partNumber, partCount, partSize);
        }

        log.info("Generated {} presigned URLs for multipart upload", partUrls.size());

        return partUrls;
    }

    /**
     * Complete multipart upload
     * 
     * Instructs S3 to assemble all uploaded parts into final object.
     * Client must provide ETags returned from each part upload.
     * 
     * @param objectKey S3 object key
     * @param uploadId Upload ID from initiateMultipartUpload()
     * @param parts List of completed parts with ETags
     * @return S3 object ETag
     */
    public String completeMultipartUpload(String objectKey, String uploadId, List<CompletedPartInfo> parts) {
        log.info("Completing multipart upload: objectKey={}, uploadId={}, parts={}",
            objectKey, uploadId, parts.size());

        // Convert to S3 CompletedPart objects
        List<CompletedPart> completedParts = parts.stream()
            .map(part -> CompletedPart.builder()
                .partNumber(part.getPartNumber())
                .eTag(part.getETag())
                .build())
            .toList();

        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
            .parts(completedParts)
            .build();

        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .uploadId(uploadId)
            .multipartUpload(completedUpload)
            .build();

        CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(request);
        String eTag = response.eTag();

        log.info("Multipart upload completed: objectKey={}, eTag={}", objectKey, eTag);

        return eTag;
    }

    /**
     * Abort multipart upload
     * 
     * Cancels multipart upload and deletes all uploaded parts.
     * Call this if client abandons upload.
     * 
     * @param objectKey S3 object key
     * @param uploadId Upload ID from initiateMultipartUpload()
     */
    public void abortMultipartUpload(String objectKey, String uploadId) {
        log.warn("Aborting multipart upload: objectKey={}, uploadId={}", objectKey, uploadId);

        AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .uploadId(uploadId)
            .build();

        s3Client.abortMultipartUpload(request);

        log.info("Multipart upload aborted: objectKey={}, uploadId={}", objectKey, uploadId);
    }

    /**
     * Part Upload URL DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PartUploadUrl {
        /**
         * Part number (1-indexed)
         */
        private int partNumber;

        /**
         * Presigned URL for uploading this part
         */
        private String uploadUrl;

        /**
         * Size of this part in bytes
         */
        private long partSize;
    }

    /**
     * Completed Part Info DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CompletedPartInfo {
        /**
         * Part number (1-indexed)
         */
        private int partNumber;

        /**
         * ETag returned by S3 after part upload
         */
        private String eTag;
    }
}
