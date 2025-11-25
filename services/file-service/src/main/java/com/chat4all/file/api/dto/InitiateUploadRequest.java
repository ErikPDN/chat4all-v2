package com.chat4all.file.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for initiating file upload
 * 
 * Client sends file metadata to initiate upload process.
 * Backend returns presigned S3 URL for direct upload.
 * 
 * Flow:
 * 1. Client sends InitiateUploadRequest
 * 2. Backend validates metadata
 * 3. Backend generates presigned PUT URL
 * 4. Backend returns InitiateUploadResponse with URL
 * 5. Client uploads file to S3 using presigned URL
 * 6. Client notifies backend of completion (future: POST /api/files/{id}/complete)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadRequest {

    /**
     * Original filename
     * 
     * Examples: photo.jpg, document.pdf, video.mp4
     * Max length: 255 characters
     */
    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename must not exceed 255 characters")
    private String filename;

    /**
     * File size in bytes
     * 
     * Used for:
     * - Quota validation
     * - Progress tracking
     * - Storage optimization
     * 
     * Must be positive (non-zero)
     */
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;

    /**
     * MIME type
     * 
     * Examples:
     * - image/jpeg
     * - image/png
     * - application/pdf
     * - video/mp4
     * - application/vnd.openxmlformats-officedocument.wordprocessingml.document (docx)
     * 
     * Used for:
     * - File type validation
     * - Content-Type header in S3
     * - Client-side rendering
     */
    @NotBlank(message = "MIME type is required")
    private String mimeType;

    /**
     * Associated message ID (optional)
     * 
     * If provided, file is linked to message immediately.
     * If null, file is "orphaned" until linked via POST /messages.
     */
    private String messageId;

    /**
     * Additional metadata (optional)
     * 
     * Examples:
     * - image dimensions: { "width": 1920, "height": 1080 }
     * - video duration: { "duration_seconds": 120 }
     * - document pages: { "page_count": 10 }
     */
    private Map<String, Object> metadata;
}
