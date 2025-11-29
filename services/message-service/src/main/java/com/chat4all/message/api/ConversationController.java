package com.chat4all.message.api;

import com.chat4all.message.api.dto.CreateConversationRequest;
import com.chat4all.message.domain.Conversation;
import com.chat4all.message.domain.ConversationType;
import com.chat4all.message.domain.Message;
import com.chat4all.message.repository.ConversationRepository;
import com.chat4all.message.repository.MessageRepository;
import com.chat4all.message.service.ConversationService;
import com.chat4all.message.service.ParticipantManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Reactive REST Controller for conversation operations
 * 
 * Endpoints:
 * - POST /v1/conversations - Create a new conversation (User Story 4)
 * - GET /v1/conversations/{id}/messages - Get message history with pagination
 * - GET /v1/conversations - Get conversations by participant
 * 
 * Security: Protected by API Gateway OAuth2 (scope: messages:read, messages:write)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ParticipantManager participantManager;

    /**
     * Creates a new conversation (User Story 4: Group Conversation Support)
     * 
     * Endpoint: POST /api/v1/conversations
     * 
     * Request Body: CreateConversationRequest
     * - type: ONE_TO_ONE or GROUP
     * - participants: List of participant IDs (min 2, max 100)
     * - title: Optional conversation title (recommended for groups)
     * - primaryChannel: Optional primary channel
     * 
     * Response: HTTP 201 Created with created conversation
     * Location header: /api/v1/conversations/{conversationId}
     * 
     * Business Rules:
     * - ONE_TO_ONE: Returns existing conversation if found between same 2 participants
     * - GROUP: Always creates new conversation
     * 
     * Task: T076
     * 
     * Example Request:
     * POST /api/v1/conversations
     * {
     *   "type": "GROUP",
     *   "participants": ["user-001", "user-002", "user-003"],
     *   "title": "Project Team Chat"
     * }
     * 
     * @param request CreateConversationRequest with conversation details
     * @return Mono<ResponseEntity<Conversation>> HTTP 201 with created conversation
     */
    @PostMapping
    public Mono<ResponseEntity<Conversation>> createConversation(
        @Valid @RequestBody CreateConversationRequest request
    ) {
        log.info("Creating conversation: type={}, participants={}, title={}", 
            request.getType(), request.getParticipants().size(), request.getTitle());

        return conversationService.createConversation(request)
            .map(conversation -> {
                log.info("Conversation created successfully: id={}, type={}", 
                    conversation.getConversationId(), conversation.getType());

                URI location = URI.create("/api/v1/conversations/" + conversation.getConversationId());

                return ResponseEntity
                    .created(location)
                    .body(conversation);
            })
            .onErrorResume(IllegalArgumentException.class, e -> {
                log.error("Validation error creating conversation: {}", e.getMessage());
                return Mono.just(ResponseEntity
                    .badRequest()
                    .build());
            })
            .onErrorResume(e -> {
                log.error("Error creating conversation", e);
                return Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
            });
    }

    /**
     * Retrieves conversations for a participant
     * 
     * Endpoint: GET /api/v1/conversations
     * 
     * Query Parameters:
     * - participantId: User ID to filter conversations (required)
     * - includeArchived: Whether to include archived conversations (default: false)
     * - limit: Max number of conversations to return (default: 50, max: 100)
     * 
     * Response: HTTP 200 OK with list of conversations sorted by last activity descending
     * 
     * Example:
     * GET /api/v1/conversations?participantId=user-001&limit=20
     * 
     * @param participantId User ID to filter conversations
     * @param includeArchived Whether to include archived conversations
     * @param limit Number of conversations to return (default: 50, max: 100)
     * @return Mono<ResponseEntity<ConversationListResponse>> with conversation list
     */
    @GetMapping
    public Mono<ResponseEntity<ConversationListResponse>> getConversations(
        @RequestParam String participantId,
        @RequestParam(defaultValue = "false") boolean includeArchived,
        @RequestParam(defaultValue = "50") int limit
    ) {
        log.debug("Fetching conversations for participant: {}, includeArchived={}, limit={}", 
            participantId, includeArchived, limit);

        // Validate and cap limit
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 100) {
            limit = 100;
        }

        final int finalLimit = limit;

        return conversationService.listConversationsByParticipant(participantId, includeArchived, finalLimit)
            .collectList()
            .map(conversations -> {
                log.info("Retrieved {} conversations for participant: {}", conversations.size(), participantId);

                ConversationListResponse response = ConversationListResponse.builder()
                    .participantId(participantId)
                    .conversations(conversations)
                    .includeArchived(includeArchived)
                    .count(conversations.size())
                    .build();

                return ResponseEntity.ok(response);
            })
            .defaultIfEmpty(ResponseEntity.ok(ConversationListResponse.builder()
                .participantId(participantId)
                .conversations(java.util.List.of())
                .includeArchived(includeArchived)
                .count(0)
                .build()));
    }

    /**
     * Retrieves message history for a conversation with cursor-based pagination
     * 
     * Endpoint: GET /api/v1/conversations/{id}/messages
     * 
     * Query Parameters:
     * - before: Cursor timestamp (ISO-8601) for pagination (optional)
     * - limit: Max number of messages to return (default: 50, max: 100)
     * 
     * Response: HTTP 200 OK with list of messages sorted by timestamp descending (newest first)
     * 
     * Performance:
     * - Uses compound index: {conversation_id: 1, timestamp: -1}
     * - Satisfies SC-009 requirement: <2s response time for conversation history
     * 
     * Example:
     * GET /api/v1/conversations/conv-001/messages?limit=20
     * GET /api/v1/conversations/conv-001/messages?before=2025-11-24T18:30:00Z&limit=20
     * 
     * @param conversationId Conversation identifier
     * @param before Optional cursor timestamp (pagination)
     * @param limit Number of messages to return (default: 50, max: 100)
     * @return Mono<ResponseEntity<MessageHistoryResponse>> with message list
     */
    @GetMapping("/{conversationId}/messages")
    public Mono<ResponseEntity<MessageHistoryResponse>> getMessages(
        @PathVariable String conversationId,
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) String before,
        @RequestParam(defaultValue = "50") int limit
    ) {
        log.debug("Fetching messages for conversation: {}, userId={}, before={}, limit={}", 
            conversationId, userId, before, limit);

        // Validate and cap limit
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 100) {
            limit = 100;
        }

        final int finalLimit = limit;

        // Task T080: Get conversation to check join date for filtering
        return conversationRepository.findByConversationId(conversationId)
            .flatMap(conversation -> {
                // Determine if join-date filtering applies (Task T080)
                Instant joinDate = null;
                if (userId != null && conversation.getType() == ConversationType.GROUP) {
                    if (conversation.getParticipantJoinDates() != null) {
                        joinDate = conversation.getParticipantJoinDates().get(userId);
                        if (joinDate != null) {
                            log.debug("Applying join-date filter for userId={}, joinDate={}", userId, joinDate);
                        }
                    }
                }

                final Instant filterJoinDate = joinDate;

                // Parse cursor timestamp if provided
                Mono<List<Message>> messagesMono;
                if (before != null && !before.trim().isEmpty()) {
                    try {
                        Instant cursor = Instant.parse(before);
                        log.debug("Using cursor-based pagination: before={}", cursor);

                        // Apply join date filter if needed
                        if (filterJoinDate != null) {
                            messagesMono = messageRepository
                                .findByConversationIdAndTimestampBefore(conversationId, cursor, PageRequest.of(0, finalLimit))
                                .filter(msg -> !msg.getTimestamp().isBefore(filterJoinDate))
                                .collectList();
                        } else {
                            messagesMono = messageRepository
                                .findByConversationIdAndTimestampBefore(conversationId, cursor, PageRequest.of(0, finalLimit))
                                .collectList();
                        }
                    } catch (Exception e) {
                        log.warn("Invalid 'before' parameter: {}. Ignoring cursor.", before);
                        // Fallback to first page if cursor is invalid
                        if (filterJoinDate != null) {
                            messagesMono = messageRepository
                                .findByConversationIdOrderByTimestampDesc(conversationId, PageRequest.of(0, finalLimit))
                                .filter(msg -> !msg.getTimestamp().isBefore(filterJoinDate))
                                .collectList();
                        } else {
                            messagesMono = messageRepository
                                .findByConversationIdOrderByTimestampDesc(conversationId, PageRequest.of(0, finalLimit))
                                .collectList();
                        }
                    }
                } else {
                    // First page - no cursor
                    if (filterJoinDate != null) {
                        messagesMono = messageRepository
                            .findByConversationIdOrderByTimestampDesc(conversationId, PageRequest.of(0, finalLimit))
                            .filter(msg -> !msg.getTimestamp().isBefore(filterJoinDate))
                            .collectList();
                    } else {
                        messagesMono = messageRepository
                            .findByConversationIdOrderByTimestampDesc(conversationId, PageRequest.of(0, finalLimit))
                            .collectList();
                    }
                }

                // Build response
                return messagesMono
                    .map(messages -> {
                        log.info("Retrieved {} messages for conversation: {} (userId={}, joinDateFilter={})", 
                            messages.size(), conversationId, userId, filterJoinDate != null);

                        // Calculate next cursor (oldest message timestamp)
                        String nextCursor = null;
                        if (!messages.isEmpty() && messages.size() == finalLimit) {
                            // More messages may exist - provide next cursor
                            Message oldestMessage = messages.get(messages.size() - 1);
                            nextCursor = oldestMessage.getTimestamp().toString();
                        }

                        MessageHistoryResponse response = MessageHistoryResponse.builder()
                            .conversationId(conversationId)
                            .messages(messages)
                            .nextCursor(nextCursor)
                            .hasMore(nextCursor != null)
                            .count(messages.size())
                            .build();

                        return ResponseEntity.ok(response);
                    });
            })
            .switchIfEmpty(Mono.just(ResponseEntity.ok(MessageHistoryResponse.builder()
                .conversationId(conversationId)
                .messages(List.of())
                .hasMore(false)
                .count(0)
                .build())))
            .defaultIfEmpty(ResponseEntity.ok(MessageHistoryResponse.builder()
                .conversationId(conversationId)
                .messages(List.of())
                .hasMore(false)
                .count(0)
                .build()));
    }

    /**
     * Response DTO for conversation list endpoint
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConversationListResponse {
        /**
         * Participant identifier
         */
        private String participantId;

        /**
         * List of conversations (sorted by last activity descending)
         */
        private java.util.List<Conversation> conversations;

        /**
         * Whether archived conversations are included
         */
        private boolean includeArchived;

        /**
         * Number of conversations in this response
         */
        private int count;
    }

    /**
     * Response DTO for message history endpoint
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MessageHistoryResponse {
        /**
         * Conversation identifier
         */
        private String conversationId;

        /**
         * List of messages (sorted by timestamp descending)
         */
        private List<Message> messages;

        /**
         * Cursor for next page (timestamp of oldest message in current page)
         * Use this value in the 'before' parameter to fetch the next page
         */
        private String nextCursor;

        /**
         * Indicates if more messages exist after this page
         */
        private boolean hasMore;

        /**
         * Number of messages in this response
         */
        private int count;
    }

    // ========================================
    // Participant Management Endpoints (Task T079)
    // ========================================

    /**
     * Adds a participant to a group conversation
     * 
     * Endpoint: POST /api/v1/conversations/{conversationId}/participants
     * 
     * Request Body: AddParticipantRequest
     * - userId: User ID to add to the group
     * 
     * Response: HTTP 200 OK with updated conversation
     * 
     * Business Rules:
     * - Only GROUP conversations support dynamic participant management
     * - Maximum 100 participants per group (FR-027)
     * - System message generated: "User X joined the group"
     * 
     * Task: T079
     * 
     * @param conversationId Conversation identifier
     * @param request Add participant request
     * @return Mono<ResponseEntity<Conversation>> Updated conversation
     */
    @PostMapping("/{conversationId}/participants")
    public Mono<ResponseEntity<Conversation>> addParticipant(
        @PathVariable String conversationId,
        @Valid @RequestBody AddParticipantRequest request
    ) {
        log.info("Adding participant to conversation: conversationId={}, userId={}", 
            conversationId, request.getUserId());

        return participantManager.addParticipant(conversationId, request.getUserId())
            .map(conversation -> {
                log.info("Participant added successfully: conversationId={}, userId={}, totalParticipants={}", 
                    conversationId, request.getUserId(), conversation.getParticipants().size());
                return ResponseEntity.ok(conversation);
            })
            .onErrorResume(IllegalArgumentException.class, e -> {
                log.error("Validation error adding participant: {}", e.getMessage());
                return Mono.just(ResponseEntity
                    .badRequest()
                    .build());
            })
            .onErrorResume(e -> {
                log.error("Error adding participant to conversation: conversationId={}", conversationId, e);
                return Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
            });
    }

    /**
     * Removes a participant from a group conversation
     * 
     * Endpoint: DELETE /api/v1/conversations/{conversationId}/participants/{userId}
     * 
     * Response: HTTP 200 OK with updated conversation
     * 
     * Business Rules:
     * - Only GROUP conversations support dynamic participant management
     * - Minimum 2 participants required (cannot remove if only 2 remain)
     * - System message generated: "User X left the group"
     * 
     * Task: T079
     * 
     * @param conversationId Conversation identifier
     * @param userId User ID to remove from the group
     * @return Mono<ResponseEntity<Conversation>> Updated conversation
     */
    @DeleteMapping("/{conversationId}/participants/{userId}")
    public Mono<ResponseEntity<Conversation>> removeParticipant(
        @PathVariable String conversationId,
        @PathVariable String userId
    ) {
        log.info("Removing participant from conversation: conversationId={}, userId={}", 
            conversationId, userId);

        return participantManager.removeParticipant(conversationId, userId)
            .map(conversation -> {
                log.info("Participant removed successfully: conversationId={}, userId={}, totalParticipants={}", 
                    conversationId, userId, conversation.getParticipants().size());
                return ResponseEntity.ok(conversation);
            })
            .onErrorResume(IllegalArgumentException.class, e -> {
                log.error("Validation error removing participant: {}", e.getMessage());
                return Mono.just(ResponseEntity
                    .badRequest()
                    .build());
            })
            .onErrorResume(e -> {
                log.error("Error removing participant from conversation: conversationId={}", conversationId, e);
                return Mono.just(ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
            });
    }

    /**
     * Request DTO for adding a participant to a group
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddParticipantRequest {
        @NotBlank(message = "User ID is required")
        private String userId;
    }
}
