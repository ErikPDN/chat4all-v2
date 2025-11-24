package com.chat4all.message.repository;

import com.chat4all.common.constant.MessageStatus;
import com.chat4all.message.domain.MessageStatusHistory;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Reactive MongoDB Repository for MessageStatusHistory entity
 * 
 * Provides non-blocking operations for immutable status history audit trail.
 * All queries leverage compound indexes for optimal performance.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Repository
public interface MessageStatusHistoryRepository extends ReactiveMongoRepository<MessageStatusHistory, String> {

    /**
     * Finds all status history entries for a message, sorted by timestamp descending
     * 
     * Uses compound index: {message_id: 1, timestamp: -1}
     * Returns complete audit trail for a message's lifecycle
     * 
     * @param messageId Message identifier
     * @return Flux of status history entries (newest first)
     */
    Flux<MessageStatusHistory> findByMessageIdOrderByTimestampDesc(String messageId);

    /**
     * Finds all status history entries for a conversation, sorted by timestamp descending
     * 
     * Used for conversation-level analytics and debugging
     * 
     * @param conversationId Conversation identifier
     * @return Flux of status history entries for all messages in conversation
     */
    Flux<MessageStatusHistory> findByConversationIdOrderByTimestampDesc(String conversationId);

    /**
     * Finds all transitions to a specific status
     * 
     * Uses compound index: {new_status: 1, timestamp: -1}
     * Used for analytics (e.g., count all DELIVERED events in time range)
     * 
     * @param newStatus Target status
     * @return Flux of status history entries
     */
    Flux<MessageStatusHistory> findByNewStatusOrderByTimestampDesc(MessageStatus newStatus);
}
