package com.chat4all.file.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for file upload initiation
 * 
 * Contains presigned S3 URL for client-side upload.
 * 
 * Client workflow:
 * 1. Receive InitiateUploadResponse
 * 2. Upload file using HTTP PUT to uploadUrl
 * 3. Set Content-Type header to match mimeType
 * 4. (Future) Notify backend: POST /api/files/{fileId}/complete
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadResponse {

    /**
     * Unique file identifier
     * 
     * Use this ID to:
     * - Reference file in POST /messages API
     * - Check upload status: GET /api/files/{fileId}
     * - Notify completion: POST /api/files/{fileId}/complete
     */
    private String fileId;

    /**
     * Presigned S3 upload URL (PUT)
     * 
     * Valid for 15 minutes (configurable)
     * 
     * Client usage:
     * ```
     * fetch(uploadUrl, {
     *   method: 'PUT',
     *   body: fileBlob,
     *   headers: {
     *     'Content-Type': mimeType
     *   }
     * })
     * ```
     */
    private String uploadUrl;

    /**
     * S3 object key (for client reference)
     * 
     * Format: files/{year}/{month}/{uuid}/{filename}
     * Example: files/2025/11/550e8400-e29b-41d4-a716-446655440000/photo.jpg
     */
    private String objectKey;

    /**
     * Upload URL expiration timestamp
     * 
     * Client should upload before this time.
     * After expiration, client must request new URL.
     */
    private Instant expiresAt;

    /**
     * File status
     * 
     * Initial status: PENDING
     * After upload: UPLOADED
     */
    private String status;

    /**
     * Additional instructions for client (optional)
     */
    private String message;
}
