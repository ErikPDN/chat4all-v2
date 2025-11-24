package com.chat4all.message.api;

import com.chat4all.message.domain.Message;
import com.chat4all.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Reactive REST Controller for conversation operations
 * 
 * Endpoints:
 * - GET /v1/conversations/{id}/messages - Get message history with pagination
 * 
 * Security: Protected by API Gateway OAuth2 (scope: messages:read)
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
        @RequestParam(required = false) String before,
        @RequestParam(defaultValue = "50") int limit
    ) {
        log.debug("Fetching messages for conversation: {}, before={}, limit={}", conversationId, before, limit);

        // Validate and cap limit
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 100) {
            limit = 100;
        }

        final int finalLimit = limit;

        // Parse cursor timestamp if provided
        Mono<List<Message>> messagesMono;
        if (before != null && !before.trim().isEmpty()) {
            try {
                Instant cursor = Instant.parse(before);
                log.debug("Using cursor-based pagination: before={}", cursor);

                messagesMono = messageRepository
                    .findByConversationIdAndTimestampBefore(conversationId, cursor, PageRequest.of(0, finalLimit))
                    .collectList();
            } catch (Exception e) {
                log.warn("Invalid 'before' parameter: {}. Ignoring cursor.", before);
                // Fallback to first page if cursor is invalid
                messagesMono = messageRepository
                    .findByConversationIdOrderByTimestampDesc(conversationId, PageRequest.of(0, finalLimit))
                    .collectList();
            }
        } else {
            // First page - no cursor
            messagesMono = messageRepository
                .findByConversationIdOrderByTimestampDesc(conversationId, PageRequest.of(0, finalLimit))
                .collectList();
        }

        // Build response
        return messagesMono
            .map(messages -> {
                log.info("Retrieved {} messages for conversation: {}", messages.size(), conversationId);

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
            })
            .defaultIfEmpty(ResponseEntity.ok(MessageHistoryResponse.builder()
                .conversationId(conversationId)
                .messages(List.of())
                .hasMore(false)
                .count(0)
                .build()));
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
}
