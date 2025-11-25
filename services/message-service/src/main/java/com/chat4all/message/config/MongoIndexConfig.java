package com.chat4all.message.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * MongoDB Index Configuration
 * 
 * Ensures required indexes exist for optimal query performance.
 * Indexes are created programmatically during application startup.
 * 
 * Performance Requirements:
 * - SC-009: Message history retrieval <2s for conversations with 10K+ messages
 * - Pagination queries use compound index: {conversationId: 1, timestamp: -1}
 * 
 * Indexes Created:
 * 1. messages collection:
 *    - {conversationId: 1, timestamp: -1} - For history retrieval with pagination
 *    - {metadata.platform_message_id: 1} - For webhook idempotency checks
 * 
 * 2. conversations collection:
 *    - {participants.userId: 1, lastMessageAt: -1} - For conversation listing
 * 
 * Task: T061
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIndexConfig {

    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Creates MongoDB indexes on application startup
     * 
     * Idempotent: Indexes are created only if they don't exist
     */
    @PostConstruct
    public void createIndexes() {
        try {
            log.info("Creating MongoDB indexes for message-service...");

            // Index 1: Compound index for message history retrieval
            // Supports queries: db.messages.find({conversationId: "xxx"}).sort({timestamp: -1}).limit(50)
            createMessageHistoryIndex();

            // Index 2: Index for webhook idempotency checks
            // Supports queries: db.messages.find({"metadata.platform_message_id": "wamid.xxx"})
            createPlatformMessageIdIndex();

            // Index 3: Index for conversation listing by participant
            // Supports queries: db.conversations.find({"participants.userId": "user123"}).sort({lastMessageAt: -1})
            createConversationParticipantIndex();

            log.info("MongoDB indexes created successfully");

        } catch (Exception e) {
            // Log error but don't fail startup - indexes may already exist
            log.warn("Error creating MongoDB indexes (may already exist): {}", e.getMessage());
        }
    }

    /**
     * Creates compound index on messages collection for history retrieval
     * 
     * Index: {conversationId: 1, timestamp: -1}
     * Purpose: Optimize GET /conversations/{id}/messages?before=xxx&limit=50
     * Performance: Enables efficient pagination with descending timestamp sort
     */
    private void createMessageHistoryIndex() {
        try {
            Index index = new Index()
                .on("conversationId", Sort.Direction.ASC)
                .on("timestamp", Sort.Direction.DESC)
                .named("idx_conversation_timestamp");

            mongoTemplate.indexOps("messages")
                .ensureIndex(index)
                .subscribe(
                    success -> log.info("Created index: idx_conversation_timestamp on messages collection"),
                    error -> log.debug("Index idx_conversation_timestamp may already exist: {}", error.getMessage())
                );

        } catch (Exception e) {
            log.debug("Index idx_conversation_timestamp may already exist: {}", e.getMessage());
        }
    }

    /**
     * Creates index on platform_message_id for webhook idempotency
     * 
     * Index: {metadata.platform_message_id: 1}
     * Purpose: Fast lookup for duplicate webhook deliveries
     * Performance: Enables O(log n) lookup instead of O(n) scan
     */
    private void createPlatformMessageIdIndex() {
        try {
            Index index = new Index()
                .on("metadata.platform_message_id", Sort.Direction.ASC)
                .named("idx_platform_message_id")
                .sparse();

            mongoTemplate.indexOps("messages")
                .ensureIndex(index)
                .subscribe(
                    success -> log.info("Created index: idx_platform_message_id on messages collection"),
                    error -> log.debug("Index idx_platform_message_id may already exist: {}", error.getMessage())
                );

        } catch (Exception e) {
            log.debug("Index idx_platform_message_id may already exist: {}", e.getMessage());
        }
    }

    /**
     * Creates compound index on conversations collection for participant queries
     * 
     * Index: {participants.userId: 1, lastMessageAt: -1}
     * Purpose: Optimize GET /conversations?participantId=xxx queries
     * Performance: Enables efficient filtering and sorting
     */
    private void createConversationParticipantIndex() {
        try {
            Index index = new Index()
                .on("participants.userId", Sort.Direction.ASC)
                .on("lastMessageAt", Sort.Direction.DESC)
                .named("idx_participant_lastmessage");

            mongoTemplate.indexOps("conversations")
                .ensureIndex(index)
                .subscribe(
                    success -> log.info("Created index: idx_participant_lastmessage on conversations collection"),
                    error -> log.debug("Index idx_participant_lastmessage may already exist: {}", error.getMessage())
                );

        } catch (Exception e) {
            log.debug("Index idx_participant_lastmessage may already exist: {}", e.getMessage());
        }
    }
}
