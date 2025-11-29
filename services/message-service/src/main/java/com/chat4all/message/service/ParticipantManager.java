package com.chat4all.message.service;

import com.chat4all.common.constant.ContentType;
import com.chat4all.common.constant.MessageStatus;
import com.chat4all.message.domain.Conversation;
import com.chat4all.message.domain.ConversationType;
import com.chat4all.message.domain.Message;
import com.chat4all.message.repository.ConversationRepository;
import com.chat4all.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing conversation participants (User Story 4 - Task T079)
 * 
 * Handles adding/removing participants from group conversations with validation
 * and automatic system message generation.
 * 
 * Business Rules:
 * - Only GROUP conversations can have dynamic participant management
 * - Maximum 100 participants per group (FR-027)
 * - System messages are generated for join/leave events
 * - Participant changes update the conversation's updated_at timestamp
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantManager {

    private static final int MAX_PARTICIPANTS = 100;
    private static final String SYSTEM_SENDER_ID = "SYSTEM";
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * Adds a participant to a group conversation
     * 
     * Business Logic:
     * - Validates conversation exists and is a GROUP
     * - Validates participant limit (max 100)
     * - Checks if participant is already in the group
     * - Adds participant to the list
     * - Generates system message: "User X joined the group"
     * - Updates conversation timestamp
     * 
     * @param conversationId Conversation identifier
     * @param userId User ID to add as participant
     * @return Mono<Conversation> Updated conversation
     * @throws IllegalArgumentException if conversation not found, not a GROUP, or limit exceeded
     */
    public Mono<Conversation> addParticipant(String conversationId, String userId) {
        log.info("Adding participant: userId={}, conversationId={}", userId, conversationId);

        return conversationRepository.findByConversationId(conversationId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                "Conversation not found: " + conversationId)))
            .flatMap(conversation -> {
                // Validate conversation type
                if (conversation.getType() != ConversationType.GROUP) {
                    return Mono.error(new IllegalArgumentException(
                        "Cannot manage participants in ONE_TO_ONE conversation: " + conversationId));
                }

                // Initialize participants list if null
                if (conversation.getParticipants() == null) {
                    conversation.setParticipants(new ArrayList<>());
                }

                // Initialize participantJoinDates map if null (Task T080)
                if (conversation.getParticipantJoinDates() == null) {
                    conversation.setParticipantJoinDates(new java.util.HashMap<>());
                }

                // Check if participant already exists
                if (conversation.getParticipants().contains(userId)) {
                    log.warn("Participant already exists in conversation: userId={}, conversationId={}", 
                        userId, conversationId);
                    return Mono.just(conversation);
                }

                // Validate participant limit
                if (conversation.getParticipants().size() >= MAX_PARTICIPANTS) {
                    return Mono.error(new IllegalArgumentException(
                        String.format("Maximum participant limit reached (%d). Cannot add more participants.", 
                            MAX_PARTICIPANTS)));
                }

                // Add participant to list and track join date (Task T080)
                Instant joinDate = Instant.now();
                conversation.getParticipants().add(userId);
                conversation.getParticipantJoinDates().put(userId, joinDate);
                conversation.setUpdatedAt(joinDate);

                // Save conversation and generate system message
                return conversationRepository.save(conversation)
                    .flatMap(savedConversation -> {
                        log.info("Participant added successfully: userId={}, conversationId={}, totalParticipants={}", 
                            userId, conversationId, savedConversation.getParticipants().size());

                        // Generate system message
                        return generateSystemMessage(
                            conversationId,
                            String.format("User '%s' joined the group", userId)
                        ).thenReturn(savedConversation);
                    });
            });
    }

    /**
     * Removes a participant from a group conversation
     * 
     * Business Logic:
     * - Validates conversation exists and is a GROUP
     * - Checks if participant exists in the group
     * - Removes participant from the list
     * - Generates system message: "User X left the group"
     * - Updates conversation timestamp
     * - Maintains minimum 2 participants (validation at API layer)
     * 
     * @param conversationId Conversation identifier
     * @param userId User ID to remove
     * @return Mono<Conversation> Updated conversation
     * @throws IllegalArgumentException if conversation not found, not a GROUP, or participant not found
     */
    public Mono<Conversation> removeParticipant(String conversationId, String userId) {
        log.info("Removing participant: userId={}, conversationId={}", userId, conversationId);

        return conversationRepository.findByConversationId(conversationId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                "Conversation not found: " + conversationId)))
            .flatMap(conversation -> {
                // Validate conversation type
                if (conversation.getType() != ConversationType.GROUP) {
                    return Mono.error(new IllegalArgumentException(
                        "Cannot manage participants in ONE_TO_ONE conversation: " + conversationId));
                }

                // Check if participants list exists
                if (conversation.getParticipants() == null || conversation.getParticipants().isEmpty()) {
                    return Mono.error(new IllegalArgumentException(
                        "No participants found in conversation: " + conversationId));
                }

                // Check if participant exists
                if (!conversation.getParticipants().contains(userId)) {
                    return Mono.error(new IllegalArgumentException(
                        String.format("User '%s' is not a participant in conversation: %s", 
                            userId, conversationId)));
                }

                // Validate minimum participants (must have at least 2 after removal)
                if (conversation.getParticipants().size() <= 2) {
                    return Mono.error(new IllegalArgumentException(
                        "Cannot remove participant: minimum 2 participants required for GROUP conversation"));
                }

                // Remove participant from list and join dates map (Task T080)
                conversation.getParticipants().remove(userId);
                if (conversation.getParticipantJoinDates() != null) {
                    conversation.getParticipantJoinDates().remove(userId);
                }
                conversation.setUpdatedAt(Instant.now());

                // Save conversation and generate system message
                return conversationRepository.save(conversation)
                    .flatMap(savedConversation -> {
                        log.info("Participant removed successfully: userId={}, conversationId={}, totalParticipants={}", 
                            userId, conversationId, savedConversation.getParticipants().size());

                        // Generate system message
                        return generateSystemMessage(
                            conversationId,
                            String.format("User '%s' left the group", userId)
                        ).thenReturn(savedConversation);
                    });
            });
    }

    /**
     * Generates a system message for participant events
     * 
     * System messages have:
     * - senderId: "SYSTEM"
     * - status: DELIVERED (no external delivery needed)
     * - contentType: TEXT
     * - No recipientIds (visible to all current participants)
     * 
     * @param conversationId Conversation identifier
     * @param messageContent System message content
     * @return Mono<Message> Generated system message
     */
    private Mono<Message> generateSystemMessage(String conversationId, String messageContent) {
        Instant now = Instant.now();
        
        Message systemMessage = Message.builder()
            .messageId(UUID.randomUUID().toString())
            .conversationId(conversationId)
            .senderId(SYSTEM_SENDER_ID)
            .content(messageContent)
            .contentType(ContentType.TEXT)
            .status(MessageStatus.DELIVERED)
            .timestamp(now)
            .createdAt(now)
            .updatedAt(now)
            .metadata(Message.MessageMetadata.builder()
                .retryCount(0)
                .additionalData(java.util.Map.of("systemMessage", true))
                .build())
            .build();

        return messageRepository.save(systemMessage)
            .doOnSuccess(msg -> log.debug("System message created: messageId={}, content={}", 
                msg.getMessageId(), messageContent))
            .doOnError(e -> log.error("Failed to create system message: conversationId={}", 
                conversationId, e));
    }

    /**
     * Retrieves conversation history for a participant
     * 
     * For Task T080 (future enhancement):
     * - New participants should only see messages from their join point forward
     * - This will require tracking join timestamp per participant
     * - Current implementation returns full history
     * 
     * @param conversationId Conversation identifier
     * @param userId User ID requesting history
     * @param limit Maximum number of messages to return
     * @return Flux<Message> Message history stream
     */
    public reactor.core.publisher.Flux<Message> getConversationHistory(
            String conversationId, 
            String userId, 
            int limit) {
        
        log.debug("Retrieving conversation history: conversationId={}, userId={}, limit={}", 
            conversationId, userId, limit);

        // TODO: Task T080 - Implement join-point filtering
        // For now, return full history ordered by timestamp descending
        return messageRepository.findByConversationIdOrderByTimestampDesc(
                conversationId, 
                org.springframework.data.domain.PageRequest.of(0, limit))
            .doOnComplete(() -> log.debug("Retrieved conversation history for user: {}", userId));
    }
}
