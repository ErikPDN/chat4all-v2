package com.chat4all.message.service;

import com.chat4all.common.constant.Channel;
import com.chat4all.message.api.dto.CreateConversationRequest;
import com.chat4all.message.domain.Conversation;
import com.chat4all.message.domain.ConversationType;
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
import java.util.UUID;

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
     * @throws IllegalArgumentException if conversationId is null or empty
     */
    public Mono<Conversation> getOrCreateConversation(
        String conversationId, 
        Channel primaryChannel, 
        String participantId
    ) {
        log.debug("Getting or creating conversation: {}", conversationId);

        // ⚠️ CRITICAL VALIDATION: Prevent duplicate key error on null conversationId
        // This happens when webhook callbacks (READ/DELIVERED status) arrive without full message data
        if (conversationId == null || conversationId.trim().isEmpty()) {
            // Generate fallback conversationId based on participant and channel
            String fallbackId = generateFallbackConversationId(participantId, primaryChannel);
            log.warn("ConversationId is null/empty! Generating fallback ID: {} (participant: {}, channel: {})", 
                fallbackId, participantId, primaryChannel);
            conversationId = fallbackId;
        }

        final String safeConversationId = conversationId; // Final for lambda

        return conversationRepository.findByConversationId(safeConversationId)
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Creating new conversation: {} for participant: {}", safeConversationId, participantId);
                
                Instant now = Instant.now();

                Conversation newConversation = Conversation.builder()
                    .conversationId(safeConversationId)
                    .type(ConversationType.ONE_TO_ONE)
                    .participants(List.of(participantId, "system-bot-001")) // Simple ID list
                    .primaryChannel(primaryChannel)
                    .archived(false)
                    .messageCount(0)
                    .lastMessageAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

                return conversationRepository.save(newConversation);
            }))
            .doOnSuccess(conv -> log.debug("Conversation ready: {}", safeConversationId));
    }

    /**
     * Generates a fallback conversationId when the original is null/empty.
     * 
     * Format: "fallback-{channel}-{participantId}-{timestamp}"
     * Example: "fallback-WHATSAPP-user123-1732588800000"
     * 
     * This prevents duplicate key errors in MongoDB while maintaining uniqueness.
     * 
     * @param participantId Participant identifier
     * @param channel Communication channel
     * @return Generated fallback conversation ID
     */
    private String generateFallbackConversationId(String participantId, Channel channel) {
        String sanitizedParticipant = (participantId != null && !participantId.isEmpty()) 
            ? participantId.replaceAll("[^a-zA-Z0-9-]", "") 
            : "unknown";
        String channelName = (channel != null) ? channel.name() : "UNKNOWN";
        long timestamp = System.currentTimeMillis();
        
        return String.format("fallback-%s-%s-%d", channelName, sanitizedParticipant, timestamp);
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
     * Creates a new conversation (User Story 4: Group Conversation Support)
     * 
     * Business Rules:
     * - ONE_TO_ONE: Checks if conversation already exists between 2 participants
     * - GROUP: Always creates a new conversation (no duplicate check)
     * - Validates participant count based on conversation type
     * - Generates unique conversationId
     * 
     * Validation:
     * - ONE_TO_ONE requires exactly 2 participants
     * - GROUP requires 3-100 participants (per FR-027)
     * - Title is recommended for GROUP conversations
     * 
     * Task: T077
     * 
     * @param request CreateConversationRequest with type, participants, optional title
     * @return Mono<Conversation> Created conversation
     * @throws IllegalArgumentException if validation fails
     */
    public Mono<Conversation> createConversation(CreateConversationRequest request) {
        log.info("Creating conversation: type={}, participants={}, title={}", 
            request.getType(), request.getParticipants().size(), request.getTitle());

        // Validation: Participant count based on type
        ConversationType type = request.getType() != null ? request.getType() : ConversationType.ONE_TO_ONE;
        int participantCount = request.getParticipants().size();

        if (type == ConversationType.ONE_TO_ONE && participantCount != 2) {
            return Mono.error(new IllegalArgumentException(
                "ONE_TO_ONE conversations require exactly 2 participants, found: " + participantCount
            ));
        }

        if (type == ConversationType.GROUP && participantCount < 3) {
            return Mono.error(new IllegalArgumentException(
                "GROUP conversations require at least 3 participants, found: " + participantCount
            ));
        }

        if (participantCount > 100) {
            return Mono.error(new IllegalArgumentException(
                "Maximum 100 participants allowed per FR-027, found: " + participantCount
            ));
        }

        // For ONE_TO_ONE: Check if conversation already exists
        if (type == ConversationType.ONE_TO_ONE) {
            return checkExistingOneToOneConversation(request.getParticipants())
                .flatMap(existingConv -> {
                    log.info("ONE_TO_ONE conversation already exists: {}", existingConv.getConversationId());
                    return Mono.just(existingConv);
                })
                .switchIfEmpty(Mono.defer(() -> createNewConversation(request)));
        }

        // For GROUP: Always create new conversation
        return createNewConversation(request);
    }

    /**
     * Checks if a ONE_TO_ONE conversation already exists between two participants
     * 
     * @param participants List of 2 participant IDs
     * @return Mono<Conversation> Existing conversation or empty
     */
    private Mono<Conversation> checkExistingOneToOneConversation(List<String> participants) {
        if (participants.size() != 2) {
            return Mono.empty();
        }

        String participant1 = participants.get(0);
        String participant2 = participants.get(1);

        // Query: Find conversation where both participants exist and type is ONE_TO_ONE
        return conversationRepository.findByParticipantUserId(participant1, PageRequest.of(0, 100))
            .filter(conv -> conv.getType() == ConversationType.ONE_TO_ONE)
            .filter(conv -> conv.getParticipants() != null && 
                           conv.getParticipants().contains(participant2))
            .next();
    }

    /**
     * Creates a new conversation in MongoDB
     * 
     * @param request CreateConversationRequest
     * @return Mono<Conversation> Created conversation
     */
    private Mono<Conversation> createNewConversation(CreateConversationRequest request) {
        String conversationId = generateConversationId(request.getType());
        Instant now = Instant.now();

        ConversationType type = request.getType() != null ? request.getType() : ConversationType.ONE_TO_ONE;
        Channel primaryChannel = request.getPrimaryChannel() != null ? request.getPrimaryChannel() : Channel.INTERNAL;

        // Initialize participant join dates map (Task T080)
        java.util.Map<String, Instant> participantJoinDates = new java.util.HashMap<>();
        if (request.getParticipants() != null) {
            for (String participantId : request.getParticipants()) {
                participantJoinDates.put(participantId, now);
            }
        }

        Conversation newConversation = Conversation.builder()
            .conversationId(conversationId)
            .type(type)
            .participants(request.getParticipants())
            .participantJoinDates(participantJoinDates)
            .primaryChannel(primaryChannel)
            .title(request.getTitle())
            .archived(false)
            .messageCount(0)
            .lastMessageAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return conversationRepository.save(newConversation)
            .doOnSuccess(conv -> log.info("Conversation created: id={}, type={}, participants={}", 
                conversationId, type, request.getParticipants().size()));
    }

    /**
     * Generates a unique conversation ID
     * 
     * Format: {type}-{uuid}
     * Examples: "1:1-a1b2c3d4", "GROUP-e5f6g7h8"
     * 
     * @param type Conversation type
     * @return Generated conversation ID
     */
    private String generateConversationId(ConversationType type) {
        String typePrefix = type == ConversationType.ONE_TO_ONE ? "1-1" : "GROUP";
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s-%s", typePrefix, uuid);
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
     * @param participantType Type of participant (AGENT, CUSTOMER, BOT) - deprecated, not used in new model
     * @return Mono<Void> Completion signal
     */
    @Deprecated
    public Mono<Void> addParticipant(String conversationId, String participantId, String participantType) {
        log.info("Adding participant {} to conversation: {}", participantId, conversationId);

        return conversationRepository.findByConversationId(conversationId)
            .flatMap(conversation -> {
                // Check if participant already exists
                boolean exists = conversation.getParticipants() != null && 
                                conversation.getParticipants().contains(participantId);

                if (exists) {
                    log.warn("Participant {} already in conversation {}", participantId, conversationId);
                    return Mono.just(conversation);
                }

                // Add participant to simple ID list
                List<String> updatedParticipants = new java.util.ArrayList<>(conversation.getParticipants());
                updatedParticipants.add(participantId);
                conversation.setParticipants(updatedParticipants);
                
                return conversationRepository.save(conversation);
            })
            .doOnSuccess(conv -> log.info("Participant {} added to conversation {}", participantId, conversationId))
            .then();
    }
}
