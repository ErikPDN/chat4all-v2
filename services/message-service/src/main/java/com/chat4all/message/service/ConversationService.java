package com.chat4all.message.service;

import com.chat4all.common.constant.Channel;
import com.chat4all.message.domain.Conversation;
import com.chat4all.message.domain.Message;
import com.chat4all.message.repository.ConversationRepository;
import com.chat4all.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing conversations
 * 
 * Responsibilities:
 * - Create conversations (upsert logic)
 * - Retrieve conversation details
 * - Update conversation metadata (last activity, participants)
 * - List conversations by participant
 * - Manage conversation lifecycle
 * 
 * Business Rules:
 * - Conversations are created automatically when first message arrives
 * - Primary channel is determined from first message
 * - Last activity timestamp updated on each message
 * - Archived conversations excluded from default queries
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * Gets or creates a conversation (upsert logic)
     * 
     * If conversation doesn't exist, creates new one with:
     * - Generated conversationId
     * - Primary channel from message
     * - Two participants: CUSTOMER (sender) + AGENT_BOT (system)
     * - Current timestamp as createdAt and lastActivityAt
     * 
     * Note: MongoDB schema validation requires minItems: 2 for participants array.
     * We automatically add a system bot participant to satisfy this constraint.
     * 
     * @param conversationId Conversation identifier
     * @param primaryChannel Primary communication channel
     * @param participantId Initial participant ID (customer)
     * @return Mono<Conversation> Existing or newly created conversation
     */
    public Mono<Conversation> getOrCreateConversation(
        String conversationId, 
        Channel primaryChannel, 
        String participantId
    ) {
        log.debug("Getting or creating conversation: {}", conversationId);

        return conversationRepository.findByConversationId(conversationId)
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Creating new conversation: {} for participant: {}", conversationId, participantId);
                
                Instant now = Instant.now();
                
                // Customer participant (from inbound message)
                Conversation.Participant customerParticipant = Conversation.Participant.builder()
                    .userId(participantId)
                    .userType("CUSTOMER") // Default type for inbound messages
                    .joinedAt(now)
                    .build();

                // System bot participant (satisfies minItems: 2 validation)
                // This represents the automated system that will handle the conversation
                // Note: userType must be "AGENT" (MongoDB enum: ["AGENT", "CUSTOMER"])
                Conversation.Participant systemParticipant = Conversation.Participant.builder()
                    .userId("system-bot-001")
                    .userType("AGENT") // Valid MongoDB enum value
                    .joinedAt(now)
                    .build();

                Conversation newConversation = Conversation.builder()
                    .conversationId(conversationId)
                    .conversationType("1:1")
                    .participants(List.of(customerParticipant, systemParticipant)) // Now has 2 participants
                    .primaryChannel(primaryChannel)
                    .archived(false)
                    .messageCount(0)
                    .lastMessageAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                return conversationRepository.save(newConversation);
            }))
            .doOnSuccess(conv -> log.debug("Conversation ready: {}", conversationId));
    }

    /**
     * Retrieves conversation by ID
     * 
     * @param conversationId Conversation identifier
     * @return Mono<Conversation> Conversation or empty if not found
     */
    public Mono<Conversation> getConversation(String conversationId) {
        log.debug("Retrieving conversation: {}", conversationId);
        return conversationRepository.findByConversationId(conversationId);
    }

    /**
     * Retrieves messages for a conversation with pagination
     * 
     * Uses compound index: {conversation_id: 1, timestamp: -1}
     * Returns messages sorted by timestamp descending (newest first)
     * 
     * @param conversationId Conversation identifier
     * @param beforeTimestamp Cursor for pagination (optional)
     * @param limit Maximum number of messages to return
     * @return Flux<Message> Stream of messages
     */
    public Flux<Message> getMessages(String conversationId, Instant beforeTimestamp, int limit) {
        log.debug("Retrieving messages for conversation: {}, before: {}, limit: {}", 
            conversationId, beforeTimestamp, limit);

        if (beforeTimestamp != null) {
            return messageRepository.findByConversationIdAndTimestampBefore(
                conversationId, 
                beforeTimestamp, 
                PageRequest.of(0, limit)
            );
        } else {
            return messageRepository.findByConversationIdOrderByTimestampDesc(
                conversationId, 
                PageRequest.of(0, limit)
            );
        }
    }

    /**
     * Updates last activity timestamp for a conversation
     * 
     * Called automatically when new message arrives or is sent
     * 
     * @param conversationId Conversation identifier
     * @param timestamp Activity timestamp
     * @return Mono<Void> Completion signal
     */
    public Mono<Void> updateLastActivity(String conversationId, Instant timestamp) {
        log.debug("Updating last activity for conversation: {} to {}", conversationId, timestamp);

        return conversationRepository.findByConversationId(conversationId)
            .flatMap(conversation -> {
                conversation.setLastMessageAt(timestamp);
                conversation.setUpdatedAt(timestamp);
                return conversationRepository.save(conversation);
            })
            .doOnSuccess(conv -> log.debug("Last activity updated for conversation: {}", conversationId))
            .then();
    }

    /**
     * Lists conversations for a participant
     * 
     * Returns all conversations where participantId is in participants list
     * Excludes archived conversations by default
     * Sorted by last activity descending (most recent first)
     * 
     * @param participantId Participant identifier
     * @param includeArchived Whether to include archived conversations
     * @param limit Maximum number of conversations to return
     * @return Flux<Conversation> Stream of conversations
     */
    public Flux<Conversation> listConversationsByParticipant(
        String participantId, 
        boolean includeArchived,
        int limit
    ) {
        log.debug("Listing conversations for participant: {}, includeArchived: {}, limit: {}", 
            participantId, includeArchived, limit);

        if (includeArchived) {
            return conversationRepository
                .findByParticipantsUserIdOrderByLastMessageAtDesc(
                    participantId, 
                    PageRequest.of(0, limit)
                );
        } else {
            return conversationRepository
                .findByParticipantsUserIdAndArchivedOrderByLastMessageAtDesc(
                    participantId, 
                    false,
                    PageRequest.of(0, limit)
                );
        }
    }

    /**
     * Archives a conversation
     * 
     * Archived conversations are hidden from default queries but remain accessible
     * Can be unarchived later if needed
     * 
     * @param conversationId Conversation identifier
     * @return Mono<Void> Completion signal
     */
    public Mono<Void> archiveConversation(String conversationId) {
        log.info("Archiving conversation: {}", conversationId);

        return conversationRepository.findByConversationId(conversationId)
            .flatMap(conversation -> {
                conversation.setArchived(true);
                return conversationRepository.save(conversation);
            })
            .doOnSuccess(conv -> log.info("Conversation archived: {}", conversationId))
            .then();
    }

    /**
     * Unarchives a conversation
     * 
     * @param conversationId Conversation identifier
     * @return Mono<Void> Completion signal
     */
    public Mono<Void> unarchiveConversation(String conversationId) {
        log.info("Unarchiving conversation: {}", conversationId);

        return conversationRepository.findByConversationId(conversationId)
            .flatMap(conversation -> {
                conversation.setArchived(false);
                return conversationRepository.save(conversation);
            })
            .doOnSuccess(conv -> log.info("Conversation unarchived: {}", conversationId))
            .then();
    }

    /**
     * Adds a participant to a conversation
     * 
     * Used for group conversations or when adding agents to customer conversations
     * 
     * @param conversationId Conversation identifier
     * @param participantId Participant identifier
     * @param participantType Type of participant (AGENT, CUSTOMER, BOT)
     * @return Mono<Void> Completion signal
     */
    public Mono<Void> addParticipant(String conversationId, String participantId, String participantType) {
        log.info("Adding participant {} to conversation: {}", participantId, conversationId);

        return conversationRepository.findByConversationId(conversationId)
            .flatMap(conversation -> {
                // Check if participant already exists
                boolean exists = conversation.getParticipants().stream()
                    .anyMatch(p -> p.getUserId().equals(participantId));

                if (exists) {
                    log.warn("Participant {} already in conversation {}", participantId, conversationId);
                    return Mono.just(conversation);
                }

                Conversation.Participant newParticipant = Conversation.Participant.builder()
                    .userId(participantId)
                    .userType(participantType)
                    .joinedAt(Instant.now())
                    .build();

                conversation.getParticipants().add(newParticipant);
                return conversationRepository.save(conversation);
            })
            .doOnSuccess(conv -> log.info("Participant {} added to conversation {}", participantId, conversationId))
            .then();
    }
}
