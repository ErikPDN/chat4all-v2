package com.chat4all.file.api;

import com.chat4all.file.api.dto.InitiateUploadRequest;
import com.chat4all.file.api.dto.InitiateUploadResponse;
import com.chat4all.file.domain.FileAttachment;
import com.chat4all.file.repository.FileRepository;
import com.chat4all.file.storage.S3StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * File Controller
 * 
 * REST API for file attachment management:
 * - POST /api/files/initiate - Initiate file upload (returns presigned URL)
 * - GET /api/files/{id} - Get file metadata
 * - GET /api/files/{id}/download - Get presigned download URL
 * 
 * Architecture:
 * - Client-side uploads (presigned URLs) - reduces server load
 * - MongoDB metadata storage
 * - S3/MinIO object storage
 * - Async processing (malware scan, thumbnail generation)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileRepository fileRepository;
    private final S3StorageService s3StorageService;

    @Value("${s3.upload-url-expiration:PT15M}")
    private Duration uploadUrlExpiration;

    @Value("${file.ttl-hours:24}")
    private int fileTtlHours;

    /**
     * Initiate file upload
     * 
     * Endpoint: POST /api/files/initiate
     * 
     * Flow:
     * 1. Validate file metadata (size, type, filename)
     * 2. Generate unique fileId and S3 object key
     * 3. Create FileAttachment record with status=PENDING
     * 4. Generate presigned S3 upload URL
     * 5. Return fileId + uploadUrl to client
     * 
     * Client then uploads file directly to S3 using PUT request.
     * 
     * @param request File metadata
     * @return InitiateUploadResponse with presigned upload URL
     */
    @PostMapping("/initiate")
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
        @Valid @RequestBody InitiateUploadRequest request
    ) {
        log.info("Initiating file upload: filename={}, size={}, mimeType={}",
            request.getFilename(), request.getFileSize(), request.getMimeType());

        // Generate unique file ID
        String fileId = UUID.randomUUID().toString();

        // Generate S3 object key with date-based partitioning
        // Format: files/{year}/{month}/{uuid}/{filename}
        // Example: files/2025/11/550e8400-e29b-41d4-a716-446655440000/photo.jpg
        String objectKey = generateObjectKey(fileId, request.getFilename());

        // Calculate expiration timestamps
        Instant now = Instant.now();
        Instant uploadUrlExpiresAt = now.plus(uploadUrlExpiration);
        Instant fileExpiresAt = now.plus(Duration.ofHours(fileTtlHours));

        // Generate presigned upload URL
        String uploadUrl = s3StorageService.generatePresignedUploadUrl(
            objectKey,
            request.getMimeType(),
            request.getFileSize()
        );

        // Create FileAttachment record
        FileAttachment fileAttachment = FileAttachment.builder()
            .fileId(fileId)
            .messageId(null) // Not yet associated with a message (will be set when message is sent)
            .filename(request.getFilename())
            .fileSize(request.getFileSize())
            .mimeType(request.getMimeType())
            .status("PENDING") // Awaiting upload
            .bucketName("chat4all-files") // TODO: get from config
            .objectKey(objectKey)
            .storageUrl(s3StorageService.buildS3Url(objectKey))
            .uploadedAt(now) // Set to current timestamp when record is created
            .expiresAt(fileExpiresAt)
            .metadata(request.getMetadata())
            .createdAt(now)
            .updatedAt(now)
            .build();

        // Persist to MongoDB
        FileAttachment savedFile = fileRepository.save(fileAttachment);

        log.info("File upload initiated: fileId={}, objectKey={}, uploadUrl expires at {}",
            fileId, objectKey, uploadUrlExpiresAt);

        // Build response
        InitiateUploadResponse response = InitiateUploadResponse.builder()
            .fileId(fileId)
            .uploadUrl(uploadUrl)
            .objectKey(objectKey)
            .expiresAt(uploadUrlExpiresAt)
            .status("PENDING")
            .message("Upload file using PUT request to uploadUrl. Include Content-Type header.")
            .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get file metadata
     * 
     * Endpoint: GET /api/files/{id}
     * 
     * Returns file status, metadata, and storage details.
     * Does NOT return presigned download URL (use /download endpoint).
     * 
     * @param fileId File identifier
     * @return FileAttachment metadata
     */
    @GetMapping("/{id}")
    public ResponseEntity<FileAttachment> getFile(@PathVariable("id") String fileId) {
        log.debug("Retrieving file metadata: fileId={}", fileId);

        return fileRepository.findByFileId(fileId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get presigned download URL
     * 
     * Endpoint: GET /api/files/{id}/download
     * 
     * Generates presigned S3 download URL (valid for 1 hour).
     * Client uses this URL to download file directly from S3.
     * 
     * @param fileId File identifier
     * @return Download URL in response body
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(@PathVariable("id") String fileId) {
        log.info("Generating download URL: fileId={}", fileId);

        return fileRepository.findByFileId(fileId)
            .map(file -> {
                // Only allow download if file is READY
                if (!"READY".equals(file.getStatus())) {
                    log.warn("File not ready for download: fileId={}, status={}", fileId, file.getStatus());
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .<DownloadUrlResponse>body(DownloadUrlResponse.builder()
                            .fileId(fileId)
                            .status(file.getStatus())
                            .message("File not ready for download. Current status: " + file.getStatus())
                            .build());
                }

                // Generate presigned download URL
                String downloadUrl = s3StorageService.generatePresignedDownloadUrl(file.getObjectKey());
                Instant expiresAt = Instant.now().plus(Duration.ofHours(1));

                DownloadUrlResponse response = DownloadUrlResponse.builder()
                    .fileId(fileId)
                    .downloadUrl(downloadUrl)
                    .filename(file.getFilename())
                    .mimeType(file.getMimeType())
                    .fileSize(file.getFileSize())
                    .expiresAt(expiresAt)
                    .status(file.getStatus())
                    .build();

                return ResponseEntity.ok(response);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Generate S3 object key with date-based partitioning
     * 
     * Format: files/{year}/{month}/{uuid}/{filename}
     * 
     * Benefits:
     * - Evenly distributed S3 keys (avoids hot partitions)
     * - Easy cleanup by date
     * - Human-readable structure
     * 
     * @param fileId Unique file identifier
     * @param filename Original filename
     * @return S3 object key
     */
    private String generateObjectKey(String fileId, String filename) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        // Sanitize filename (remove special characters)
        String sanitizedFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        return String.format("files/%d/%02d/%s/%s", year, month, fileId, sanitizedFilename);
    }

    /**
     * Download URL Response DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DownloadUrlResponse {
        private String fileId;
        private String downloadUrl;
        private String filename;
        private String mimeType;
        private Long fileSize;
        private Instant expiresAt;
        private String status;
        private String message;
    }
}
