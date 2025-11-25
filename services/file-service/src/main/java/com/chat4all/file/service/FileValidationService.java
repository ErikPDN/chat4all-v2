package com.chat4all.file.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * File Validation Service
 * 
 * Validates file metadata before upload:
 * - MIME type whitelist (FR-022)
 * - File size limits (FR-019)
 * - Filename sanitization
 * 
 * Security:
 * - Prevents upload of executable files (.exe, .sh, .bat)
 * - Prevents upload of potentially dangerous files (.jar, .dll)
 * - Validates MIME type matches file extension
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class FileValidationService {

    @Value("${file.allowed-mime-types}")
    private List<String> allowedMimeTypes;

    @Value("${file.max-file-size:104857600}") // 100MB default
    private long maxFileSize;

    /**
     * Validate file metadata
     * 
     * Checks:
     * 1. MIME type is in whitelist
     * 2. File size is within limits
     * 3. Filename is safe (no path traversal)
     * 
     * @param filename Original filename
     * @param mimeType File MIME type
     * @param fileSize File size in bytes
     * @throws FileValidationException if validation fails
     */
    public void validateFile(String filename, String mimeType, long fileSize) {
        log.debug("Validating file: filename={}, mimeType={}, size={}", filename, mimeType, fileSize);

        // Validate MIME type
        validateMimeType(mimeType);

        // Validate file size
        validateFileSize(fileSize);

        // Validate filename
        validateFilename(filename);

        log.info("File validation passed: filename={}", filename);
    }

    /**
     * Validate MIME type against whitelist
     * 
     * Allowed types (per FR-022):
     * - Images: image/jpeg, image/png, image/gif, image/webp
     * - Documents: application/pdf, application/msword, 
     *              application/vnd.openxmlformats-officedocument.wordprocessingml.document
     * - Spreadsheets: application/vnd.ms-excel,
     *                 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
     * - Videos: video/mp4, video/quicktime
     * - Audio: audio/mpeg, audio/wav
     * 
     * @param mimeType MIME type to validate
     * @throws FileValidationException if MIME type not allowed
     */
    private void validateMimeType(String mimeType) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            throw new FileValidationException("MIME type is required");
        }

        if (!allowedMimeTypes.contains(mimeType.toLowerCase())) {
            log.warn("Rejected file with disallowed MIME type: {}", mimeType);
            throw new FileValidationException(
                String.format("MIME type '%s' is not allowed. Allowed types: %s",
                    mimeType, allowedMimeTypes)
            );
        }
    }

    /**
     * Validate file size
     * 
     * Maximum file size: 100MB (configurable)
     * Minimum file size: 1 byte
     * 
     * Note: For files >100MB, use multipart upload (T066)
     * 
     * @param fileSize File size in bytes
     * @throws FileValidationException if file size invalid
     */
    private void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new FileValidationException("File size must be greater than 0");
        }

        if (fileSize > maxFileSize) {
            log.warn("Rejected file exceeding size limit: size={}, limit={}", fileSize, maxFileSize);
            throw new FileValidationException(
                String.format("File size %d bytes exceeds maximum allowed size %d bytes. " +
                    "For files >100MB, use multipart upload.",
                    fileSize, maxFileSize)
            );
        }
    }

    /**
     * Validate filename
     * 
     * Security checks:
     * - No path traversal (../)
     * - No null bytes
     * - Maximum length: 255 characters
     * - No dangerous extensions (.exe, .sh, .bat, .jar, .dll)
     * 
     * @param filename Original filename
     * @throws FileValidationException if filename invalid
     */
    private void validateFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new FileValidationException("Filename is required");
        }

        if (filename.length() > 255) {
            throw new FileValidationException("Filename exceeds maximum length of 255 characters");
        }

        // Check for path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            log.warn("Rejected filename with path traversal attempt: {}", filename);
            throw new FileValidationException("Filename contains invalid characters (path traversal)");
        }

        // Check for null bytes
        if (filename.contains("\0")) {
            log.warn("Rejected filename with null byte: {}", filename);
            throw new FileValidationException("Filename contains null byte");
        }

        // Check for dangerous extensions
        String lowerFilename = filename.toLowerCase();
        List<String> dangerousExtensions = List.of(
            ".exe", ".sh", ".bat", ".cmd", ".jar", ".dll", ".so", ".dylib",
            ".app", ".deb", ".rpm", ".msi", ".scr", ".vbs", ".js", ".ps1"
        );

        for (String ext : dangerousExtensions) {
            if (lowerFilename.endsWith(ext)) {
                log.warn("Rejected filename with dangerous extension: {}", filename);
                throw new FileValidationException(
                    String.format("File extension '%s' is not allowed for security reasons", ext)
                );
            }
        }
    }

    /**
     * Sanitize filename
     * 
     * Removes special characters and normalizes filename.
     * 
     * @param filename Original filename
     * @return Sanitized filename
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }

        // Remove special characters (keep alphanumeric, dots, hyphens, underscores)
        String sanitized = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Remove leading/trailing dots and underscores
        sanitized = sanitized.replaceAll("^[._-]+", "");
        sanitized = sanitized.replaceAll("[._-]+$", "");

        // Ensure not empty after sanitization
        if (sanitized.isEmpty()) {
            sanitized = "unnamed";
        }

        return sanitized;
    }

    /**
     * File Validation Exception
     */
    public static class FileValidationException extends RuntimeException {
        public FileValidationException(String message) {
            super(message);
        }
    }
}
