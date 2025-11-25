package com.chat4all.file.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

/**
 * S3 Storage Service
 * 
 * Handles S3/MinIO storage operations:
 * - Generate presigned URLs for client-side uploads (PUT)
 * - Generate presigned URLs for downloads (GET)
 * - Delete objects from S3
 * 
 * Presigned URLs allow clients to upload/download files directly to/from S3
 * without proxying through the backend, improving performance and reducing
 * server load (FR-024: multipart upload support for files >100MB).
 * 
 * Architecture:
 * 1. Client requests upload initiation
 * 2. Backend generates presigned PUT URL (valid for 15 minutes)
 * 3. Client uploads file directly to S3 using presigned URL
 * 4. Client notifies backend of upload completion
 * 5. Backend updates file status to UPLOADED
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${s3.bucket-name:chat4all-files}")
    private String bucketName;

    @Value("${s3.upload-url-expiration:PT15M}")
    private Duration uploadUrlExpiration; // Default: 15 minutes

    @Value("${s3.download-url-expiration:PT1H}")
    private Duration downloadUrlExpiration; // Default: 1 hour

    /**
     * Generate presigned URL for file upload (PUT)
     * 
     * Allows client to upload file directly to S3 without backend proxy.
     * URL is valid for configured expiration time (default: 15 minutes).
     * 
     * Flow:
     * 1. Backend generates presigned PUT URL
     * 2. Client receives URL and uploads file using HTTP PUT
     * 3. S3/MinIO stores file
     * 4. Client notifies backend of completion
     * 
     * @param objectKey S3 object key (path within bucket)
     * @param contentType MIME type (e.g., image/jpeg)
     * @param contentLength File size in bytes (optional)
     * @return Presigned upload URL (valid for 15 minutes)
     */
    public String generatePresignedUploadUrl(String objectKey, String contentType, Long contentLength) {
        log.debug("Generating presigned upload URL: bucket={}, key={}, contentType={}, size={}",
            bucketName, objectKey, contentType, contentLength);

        try {
            PutObjectRequest.Builder putRequestBuilder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType);

            // Add content length if provided (recommended for large files)
            if (contentLength != null) {
                putRequestBuilder.contentLength(contentLength);
            }

            PutObjectRequest putObjectRequest = putRequestBuilder.build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(uploadUrlExpiration)
                .putObjectRequest(putObjectRequest)
                .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            String uploadUrl = presignedRequest.url().toString();

            log.info("Presigned upload URL generated: key={}, url={}, expiresIn={}",
                objectKey, uploadUrl, uploadUrlExpiration);

            return uploadUrl;
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL: bucket={}, key={}",
                bucketName, objectKey, e);
            throw new RuntimeException("Failed to generate upload URL", e);
        }
    }

    /**
     * Generate presigned URL for file download (GET)
     * 
     * Allows client to download file directly from S3 without backend proxy.
     * URL is valid for configured expiration time (default: 1 hour).
     * 
     * Use cases:
     * - Share file link with external users
     * - Display images/videos in web UI
     * - Download attachments
     * 
     * @param objectKey S3 object key (path within bucket)
     * @return Presigned download URL (valid for 1 hour)
     */
    public String generatePresignedDownloadUrl(String objectKey) {
        log.debug("Generating presigned download URL: bucket={}, key={}", bucketName, objectKey);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(downloadUrlExpiration)
                .getObjectRequest(getObjectRequest)
                .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String downloadUrl = presignedRequest.url().toString();

            log.info("Presigned download URL generated: key={}, url={}, expiresIn={}",
                objectKey, downloadUrl, downloadUrlExpiration);

            return downloadUrl;
        } catch (Exception e) {
            log.error("Failed to generate presigned download URL: bucket={}, key={}",
                bucketName, objectKey, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    /**
     * Delete object from S3
     * 
     * Used when:
     * - File upload fails (cleanup pending files)
     * - Message is deleted (cascade delete attachments)
     * - TTL expires (manual cleanup before MongoDB TTL)
     * 
     * @param objectKey S3 object key (path within bucket)
     */
    public void deleteObject(String objectKey) {
        log.info("Deleting object from S3: bucket={}, key={}", bucketName, objectKey);

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

            s3Client.deleteObject(deleteRequest);

            log.info("Object deleted successfully: key={}", objectKey);
        } catch (Exception e) {
            log.error("Failed to delete object: bucket={}, key={}", bucketName, objectKey, e);
            throw new RuntimeException("Failed to delete object from S3", e);
        }
    }

    /**
     * Build S3 URL (non-presigned)
     * 
     * Format: s3://bucket/key
     * Used for internal storage reference (not accessible externally)
     * 
     * @param objectKey S3 object key
     * @return S3 URL
     */
    public String buildS3Url(String objectKey) {
        return String.format("s3://%s/%s", bucketName, objectKey);
    }

    /**
     * Extract object key from S3 URL
     * 
     * Converts: s3://bucket/key → key
     * 
     * @param s3Url S3 URL
     * @return Object key
     */
    public String extractObjectKey(String s3Url) {
        if (s3Url == null || !s3Url.startsWith("s3://")) {
            throw new IllegalArgumentException("Invalid S3 URL: " + s3Url);
        }

        String withoutProtocol = s3Url.substring(5); // Remove "s3://"
        int slashIndex = withoutProtocol.indexOf('/');

        if (slashIndex < 0) {
            throw new IllegalArgumentException("Invalid S3 URL format: " + s3Url);
        }

        return withoutProtocol.substring(slashIndex + 1);
    }
}
