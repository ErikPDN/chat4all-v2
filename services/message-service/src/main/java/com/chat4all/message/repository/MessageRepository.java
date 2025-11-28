package com.chat4all.message.repository;

import com.chat4all.message.domain.Message;
import com.chat4all.common.constant.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Reactive MongoDB Repository for Message entity
 * 
 * Provides non-blocking CRUD operations and custom query methods for message management.
 * All queries leverage MongoDB indexes for optimal performance.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Repository
public interface MessageRepository extends ReactiveMongoRepository<Message, String> {

    /**
     * Finds a message by its unique message ID
     * 
     * Uses unique index on message_id field
     * 
     * @param messageId Unique message identifier (UUIDv4)
     * @return Mono containing the message if found
     */
    Mono<Message> findByMessageId(String messageId);

    /**
     * Finds all messages in a conversation, sorted by timestamp descending
     * 
     * Uses compound index: {conversation_id: 1, timestamp: -1}
     * Supports SC-009 requirement: <2s response time for conversation history
     * 
     * @param conversationId Conversation identifier
     * @param pageable Pagination parameters
     * @return Flux of messages in the conversation
     */
    Flux<Message> findByConversationIdOrderByTimestampDesc(String conversationId, Pageable pageable);

    /**
     * Finds messages in a conversation before a specific timestamp (cursor-based pagination)
     * 
     * Uses compound index: {conversation_id: 1, timestamp: -1}
     * 
     * @param conversationId Conversation identifier
     * @param before Cursor timestamp
     * @param pageable Pagination parameters (limit)
     * @return Flux of messages before the cursor
     */
    @Query("{ 'conversationId': ?0, 'timestamp': { $lt: ?1 } }")
    Flux<Message> findByConversationIdAndTimestampBefore(String conversationId, Instant before, Pageable pageable);

    /**
     * Finds all messages sent by a specific user
     * 
     * Uses compound index: {sender_id: 1, timestamp: -1}
     * 
     * @param senderId User ID of the sender
     * @param pageable Pagination parameters
     * @return Flux of messages from the sender
     */
    Flux<Message> findBySenderIdOrderByTimestampDesc(String senderId, Pageable pageable);

    /**
     * Finds messages by status for retry/monitoring purposes
     * 
     * Uses compound index: {status: 1, updated_at: 1}
     * Used by retry workers to find PENDING/FAILED messages
     * 
     * @param status Message status
     * @param pageable Pagination parameters
     * @return Flux of messages with the specified status
     */
    Flux<Message> findByStatusOrderByUpdatedAtAsc(MessageStatus status, Pageable pageable);

    /**
     * Finds messages by status updated before a specific timestamp
     * 
     * Used to find stuck messages that haven't been updated in a while
     * 
     * @param status Message status
     * @param updatedBefore Timestamp threshold
     * @return Flux of stale messages
     */
    Flux<Message> findByStatusAndUpdatedAtBefore(MessageStatus status, Instant updatedBefore);

    /**
     * Checks if a message with the given ID exists (for idempotency check)
     * 
     * Uses unique index on message_id field
     * More efficient than findByMessageId when only checking existence
     * 
     * @param messageId Unique message identifier
     * @return Mono<Boolean> true if message exists, false otherwise
     */
    Mono<Boolean> existsByMessageId(String messageId);

    /**
     * Finds messages by external platform message ID
     * 
     * Uses sparse index on metadata.platform_message_id
     * Used for webhook correlation
     * 
     * @param platformMessageId External platform's message ID
     * @return Mono containing the message if found
     */
    @Query("{ 'metadata.platformMessageId': ?0 }")
    Mono<Message> findByPlatformMessageId(String platformMessageId);

    /**
     * Finds messages by external platform message ID (metadata field)
     * 
     * Uses sparse unique index on metadata.platform_message_id
     * Used for inbound webhook deduplication
     * 
     * Returns Flux to handle legacy duplicate data gracefully.
     * With unique index, there should only be one result.
     * 
     * @param platformMessageId External platform's message ID
     * @return Flux of messages (should be at most one with unique index)
     */
    @Query("{ 'metadata.platform_message_id': ?0 }")
    Flux<Message> findByMetadataPlatformMessageId(String platformMessageId);

    /**
     * Counts messages in a conversation
     * 
     * @param conversationId Conversation identifier
     * @return Mono<Long> Total number of messages
     */
    Mono<Long> countByConversationId(String conversationId);
}
