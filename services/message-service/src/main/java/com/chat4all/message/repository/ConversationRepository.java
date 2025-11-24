package com.chat4all.message.repository;

import com.chat4all.message.domain.Conversation;
import com.chat4all.common.constant.Channel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Reactive MongoDB Repository for Conversation entity
 * 
 * Provides non-blocking CRUD operations and custom query methods for conversation management.
 * All queries leverage MongoDB indexes for optimal performance.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Repository
public interface ConversationRepository extends ReactiveMongoRepository<Conversation, String> {

    /**
     * Finds a conversation by its unique conversation ID
     * 
     * Uses unique index on conversation_id field
     * 
     * @param conversationId Unique conversation identifier
     * @return Mono containing the conversation if found
     */
    Mono<Conversation> findByConversationId(String conversationId);

    /**
     * Finds all conversations for a specific user (participant)
     * 
     * Uses compound index: {participants.user_id: 1, last_message_at: -1}
     * Returns conversations sorted by most recent activity
     * 
     * @param userId User ID (participant)
     * @param pageable Pagination parameters
     * @return Flux of conversations where user is a participant
     */
    @Query("{ 'participants.userId': ?0 }")
    Flux<Conversation> findByParticipantUserId(String userId, Pageable pageable);

    /**
     * Finds active (non-archived) conversations for a user
     * 
     * @param userId User ID (participant)
     * @param archived Archive status filter
     * @param pageable Pagination parameters
     * @return Flux of active conversations
     */
    @Query("{ 'participants.userId': ?0, 'archived': ?1 }")
    Flux<Conversation> findByParticipantUserIdAndArchived(String userId, boolean archived, Pageable pageable);

    /**
     * Finds conversations by primary channel
     * 
     * Uses compound index: {primary_channel: 1, archived: 1}
     * 
     * @param channel Platform channel (WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL)
     * @param pageable Pagination parameters
     * @return Flux of conversations on the specified channel
     */
    Flux<Conversation> findByPrimaryChannel(Channel channel, Pageable pageable);

    /**
     * Finds conversations by channel and archived status
     * 
     * Uses compound index: {primary_channel: 1, archived: 1}
     * 
     * @param channel Platform channel
     * @param archived Archive status
     * @param pageable Pagination parameters
     * @return Flux of filtered conversations
     */
    Flux<Conversation> findByPrimaryChannelAndArchived(Channel channel, boolean archived, Pageable pageable);

    /**
     * Finds recent conversations (dashboard view)
     * 
     * Uses index on last_message_at field
     * 
     * @param pageable Pagination parameters
     * @return Flux of conversations sorted by recent activity
     */
    Flux<Conversation> findAllByOrderByLastMessageAtDesc(Pageable pageable);

    /**
     * Finds conversations inactive since a specific timestamp
     * 
     * Used for auto-archival (e.g., conversations inactive for 90 days)
     * 
     * @param inactiveSince Timestamp threshold
     * @param archived Current archive status
     * @return Flux of inactive conversations
     */
    @Query("{ 'lastMessageAt': { $lt: ?0 }, 'archived': ?1 }")
    Flux<Conversation> findInactiveConversations(Instant inactiveSince, boolean archived);

    /**
     * Checks if a conversation with the given ID exists
     * 
     * Uses unique index on conversation_id field
     * More efficient than findByConversationId when only checking existence
     * 
     * @param conversationId Unique conversation identifier
     * @return Mono<Boolean> true if conversation exists, false otherwise
     */
    Mono<Boolean> existsByConversationId(String conversationId);

    /**
     * Finds conversations by type (ONE_TO_ONE or GROUP)
     * 
     * @param conversationType Conversation type
     * @param pageable Pagination parameters
     * @return Flux of conversations of the specified type
     */
    Flux<Conversation> findByConversationType(String conversationType, Pageable pageable);

    /**
     * Counts conversations for a user
     * 
     * @param userId User ID (participant)
     * @return Mono<Long> Total number of conversations
     */
    @Query(value = "{ 'participants.userId': ?0 }", count = true)
    Mono<Long> countByParticipantUserId(String userId);
}
