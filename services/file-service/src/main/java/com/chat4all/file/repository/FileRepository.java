package com.chat4all.file.repository;

import com.chat4all.file.domain.FileAttachment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * FileAttachment MongoDB Repository
 * 
 * Provides CRUD operations and custom queries for file attachments.
 * 
 * Collection: files
 * Indexes:
 * - file_id (unique)
 * - message_id
 * - status
 * - expires_at (TTL index)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Repository
public interface FileRepository extends MongoRepository<FileAttachment, String> {

    /**
     * Find file by unique file ID
     * 
     * @param fileId File identifier
     * @return Optional<FileAttachment>
     */
    Optional<FileAttachment> findByFileId(String fileId);

    /**
     * Find all files associated with a message
     * 
     * Used when retrieving message attachments for display
     * 
     * @param messageId Message identifier
     * @return List of file attachments
     */
    List<FileAttachment> findByMessageId(String messageId);

    /**
     * Find files by status
     * 
     * Used for batch processing (e.g., scanning UPLOADED files)
     * 
     * @param status File status (PENDING, UPLOADED, PROCESSING, READY, FAILED)
     * @return List of file attachments
     */
    List<FileAttachment> findByStatus(String status);

    /**
     * Find files uploaded before a specific timestamp
     * 
     * Used for cleanup jobs
     * 
     * @param timestamp Cutoff timestamp
     * @return List of file attachments
     */
    List<FileAttachment> findByUploadedAtBefore(Instant timestamp);

    /**
     * Find files expiring before a specific timestamp
     * 
     * Used for pre-expiration notifications or manual cleanup
     * 
     * @param timestamp Cutoff timestamp
     * @return List of file attachments
     */
    List<FileAttachment> findByExpiresAtBefore(Instant timestamp);

    /**
     * Count files by status
     * 
     * Used for monitoring/metrics
     * 
     * @param status File status
     * @return Count of files
     */
    long countByStatus(String status);

    /**
     * Delete files by message ID
     * 
     * Used when a message is deleted (cascade delete attachments)
     * 
     * @param messageId Message identifier
     */
    void deleteByMessageId(String messageId);
}
