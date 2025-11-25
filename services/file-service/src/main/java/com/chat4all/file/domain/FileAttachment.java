package com.chat4all.file.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;

/**
 * FileAttachment Entity for MongoDB
 * 
 * Represents a file attachment in the unified messaging platform.
 * Files are stored in S3/MinIO with metadata persisted in MongoDB.
 * 
 * Collection: files
 * 
 * TTL Index: expiresAt field (24h expiration for temporary files)
 * 
 * File Lifecycle:
 * 1. PENDING - Presigned URL generated, awaiting upload
 * 2. UPLOADED - File uploaded to S3, awaiting processing
 * 3. PROCESSING - Malware scan / thumbnail generation in progress
 * 4. READY - File ready for delivery
 * 5. FAILED - Processing failed (malware detected, invalid format)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "files")
public class FileAttachment {

    /**
     * MongoDB document ID (internal)
     */
    @Id
    private String id;

    /**
     * Unique file identifier (UUID)
     */
    @Indexed(unique = true)
    @Field("file_id")
    private String fileId;

    /**
     * Associated message ID (optional - null until message is sent)
     */
    @Indexed
    @Field("message_id")
    private String messageId;

    /**
     * Original filename
     */
    private String filename;

    /**
     * File size in bytes
     */
    @Field("file_size")
    private Long fileSize;

    /**
     * MIME type (e.g., image/jpeg, application/pdf)
     */
    @Field("mime_type")
    private String mimeType;

    /**
     * File status
     * Enum: PENDING, UPLOADED, PROCESSING, READY, FAILED
     */
    @Indexed
    private String status;

    /**
     * S3/MinIO storage URL (s3://bucket/key)
     */
    @Field("storage_url")
    private String storageUrl;

    /**
     * S3 bucket name
     */
    @Field("bucket_name")
    private String bucketName;

    /**
     * S3 object key (path within bucket)
     */
    @Field("object_key")
    private String objectKey;

    /**
     * Thumbnail URL (for images/videos)
     * Generated asynchronously after upload
     */
    @Field("thumbnail_url")
    private String thumbnailUrl;

    /**
     * File upload timestamp
     */
    @Field("uploaded_at")
    private Instant uploadedAt;

    /**
     * File expiration timestamp (for TTL index)
     * Default: 24h after upload (configurable)
     */
    @Indexed(expireAfterSeconds = 0)
    @Field("expires_at")
    private Instant expiresAt;

    /**
     * Malware scan result
     */
    @Field("scan_result")
    private ScanResult scanResult;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Record creation timestamp
     */
    @Field("created_at")
    private Instant createdAt;

    /**
     * Record last update timestamp
     */
    @Field("updated_at")
    private Instant updatedAt;

    /**
     * Malware scan result nested object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanResult {
        /**
         * Scan status
         * Enum: PENDING, CLEAN, INFECTED, FAILED
         */
        private String status;

        /**
         * Scan engine name (e.g., ClamAV)
         */
        private String engine;

        /**
         * Scan timestamp
         */
        @Field("scanned_at")
        private Instant scannedAt;

        /**
         * Threat details (if infected)
         */
        @Field("threat_name")
        private String threatName;

        /**
         * Error message (if scan failed)
         */
        @Field("error_message")
        private String errorMessage;
    }
}
