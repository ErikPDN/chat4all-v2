package com.chat4all.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * File Upload Complete Event
 * 
 * Published to Kafka when a file upload completes and is ready for attachment to messages.
 * 
 * Event Flow:
 * 1. Client initiates upload → FileAttachment created with status=PENDING
 * 2. Client uploads file to S3 using presigned URL
 * 3. File service processes file (malware scan, thumbnail generation)
 * 4. FileAttachment status updated to READY
 * 5. FileUploadCompleteEvent published to Kafka
 * 6. Message service consumes event and can now attach file to messages
 * 
 * Kafka Topic: file-events
 * Partition Key: fileId (ensures ordering per file)
 * 
 * Consumers:
 * - Message Service: Links file to messages when SendMessageRequest includes fileIds
 * - Analytics Service: Tracks file upload metrics
 * - Audit Service: Logs file operations
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadCompleteEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique event identifier (UUIDv4)
     */
    private String eventId;

    /**
     * File identifier
     */
    private String fileId;

    /**
     * Message ID (if file was pre-associated with message)
     * Null if file uploaded independently
     */
    private String messageId;

    /**
     * Original filename
     */
    private String filename;

    /**
     * File size in bytes
     */
    private Long fileSize;

    /**
     * MIME type
     */
    private String mimeType;

    /**
     * File status after processing
     * Values: READY, FAILED
     */
    private String status;

    /**
     * S3 storage URL
     */
    private String storageUrl;

    /**
     * S3 bucket name
     */
    private String bucketName;

    /**
     * S3 object key
     */
    private String objectKey;

    /**
     * Thumbnail URL (if generated)
     * Null for non-image/video files
     */
    private String thumbnailUrl;

    /**
     * File expiration timestamp (24h TTL)
     */
    private Instant expiresAt;

    /**
     * Malware scan result
     */
    private ScanResult scanResult;

    /**
     * When the file upload completed
     */
    private Instant completedAt;

    /**
     * Event timestamp (when event was created)
     */
    private Instant timestamp;

    /**
     * Malware Scan Result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Scan status: PENDING, CLEAN, INFECTED, FAILED
         */
        private String status;

        /**
         * Scan engine name (e.g., ClamAV, MockScanner)
         */
        private String engine;

        /**
         * When scan completed
         */
        private Instant scannedAt;

        /**
         * Threat name if infected (null if clean)
         */
        private String threatName;

        /**
         * Error message if scan failed (null if successful)
         */
        private String errorMessage;
    }
}
