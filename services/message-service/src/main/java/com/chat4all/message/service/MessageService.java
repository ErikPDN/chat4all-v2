package com.chat4all.message.service;

import com.chat4all.common.constant.MessageStatus;
import com.chat4all.common.event.MessageEvent;
import com.chat4all.message.domain.Message;
import com.chat4all.message.domain.MessageStatusHistory;
import com.chat4all.message.kafka.MessageProducer;
import com.chat4all.message.repository.MessageRepository;
import com.chat4all.message.repository.MessageStatusHistoryRepository;
import com.chat4all.message.websocket.MessageStatusWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Reactive Message Service
 * 
 * Core business logic for message management using reactive programming.
 * Handles message acceptance, persistence, status updates, and event publishing.
 * 
 * Key responsibilities:
 * 1. Idempotency check (via Redis)
 * 2. Message persistence (to MongoDB)
 * 3. Event publishing (to Kafka)
 * 4. Status management (PENDING → SENT → DELIVERED → READ)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageStatusHistoryRepository statusHistoryRepository;
    private final IdempotencyService idempotencyService;
    private final MessageProducer messageProducer;
    private final ConversationService conversationService;
    private final MessageStatusWebSocketHandler webSocketHandler;

    /**
     * Accepts a new message for processing (reactive).
     * 
     * This is the main entry point for outbound messages from the API.
     * 
     * Flow:
     * 1. Check idempotency (prevent duplicates)
     * 2. Generate message ID if not provided
     * 3. Set initial status to PENDING
     * 4. Persist to MongoDB
     * 5. Publish MESSAGE_CREATED event to Kafka
     * 
     * @param message Message to accept
     * @return Mono<Message> Persisted message with generated ID
     * @throws IllegalStateException if message is a duplicate
     */
    public Mono<Message> acceptMessage(Message message) {
        // Generate message ID if not provided (handles null or blank)
        if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
            String generatedId = UUID.randomUUID().toString();
            message.setMessageId(generatedId);
            log.debug("Generated new messageId: {}", generatedId);
        }

        final String messageId = message.getMessageId();

        // Idempotency check (FR-006)
        return idempotencyService.isDuplicate(messageId)
            .flatMap(isDuplicate -> {
                if (isDuplicate) {
                    log.warn("Duplicate message detected: {}", messageId);
                    return Mono.error(new IllegalStateException("Duplicate message: " + messageId));
                }

                // Set timestamps
                Instant now = Instant.now();
                if (message.getTimestamp() == null) {
                    message.setTimestamp(now);
                }
                message.setCreatedAt(now);
                message.setUpdatedAt(now);

                // Set initial status
                if (message.getStatus() == null) {
                    message.setStatus(MessageStatus.PENDING);
                }

                // Initialize metadata if null
                if (message.getMetadata() == null) {
                    message.setMetadata(Message.MessageMetadata.builder()
                        .retryCount(0)
                        .build());
                }

                // Persist to MongoDB
                return messageRepository.save(message)
                    .doOnSuccess(savedMessage -> {
                        log.info("Message accepted and persisted: {} (conversation: {})",
                            savedMessage.getMessageId(), savedMessage.getConversationId());

                        // Publish MESSAGE_CREATED event to Kafka (fire-and-forget)
                        publishMessageEvent(savedMessage, MessageEvent.EventType.MESSAGE_CREATED);
                    })
                    .doOnError(DuplicateKeyException.class, e ->
                        log.error("Duplicate key error for message: {}", messageId, e)
                    );
            });
    }

    /**
     * Updates the status of a message (reactive).
     * 
     * Validates status transitions to ensure correct state machine flow.
     * Records status change in audit history (MessageStatusHistory).
     * Publishes appropriate event to Kafka for each status change.
     * 
     * Valid transitions (FR-007):
     * - PENDING → SENT
     * - SENT → DELIVERED
     * - DELIVERED → READ
     * - Any status → FAILED
     * 
     * @param messageId Message identifier
     * @param newStatus New status to set
     * @param updatedBy Actor triggering the update (e.g., "router-service", "webhook-whatsapp")
     * @return Mono<Void> Completes when status is updated
     * @throws IllegalArgumentException if message not found
     * @throws IllegalStateException if status transition is invalid
     */
    public Mono<Void> updateStatus(String messageId, MessageStatus newStatus, String updatedBy) {
        return getMessageById(messageId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found: " + messageId)))
            .flatMap(message -> {
                MessageStatus oldStatus = message.getStatus();

                // Validate status transition
                if (!oldStatus.canTransitionTo(newStatus)) {
                    return Mono.error(new IllegalStateException(String.format(
                        "Invalid status transition for message %s: %s → %s",
                        messageId, oldStatus, newStatus)));
                }

                // Update status and timestamp
                message.setStatus(newStatus);
                message.setUpdatedAt(Instant.now());

                // Create status history entry
                MessageStatusHistory history = MessageStatusHistory.createTransition(
                    messageId,
                    message.getConversationId(),
                    oldStatus,
                    newStatus,
                    updatedBy != null ? updatedBy : "system"
                );

                // Save message and history in parallel, then publish event
                return Mono.zip(
                    messageRepository.save(message),
                    statusHistoryRepository.save(history)
                )
                .doOnSuccess(tuple -> {
                    Message savedMessage = tuple.getT1();
                    log.info("Message status updated: {} → {} (message: {}, updatedBy: {})",
                        oldStatus, newStatus, messageId, updatedBy);

                    // Publish status update event (fire-and-forget)
                    MessageEvent.EventType eventType = mapStatusToEventType(newStatus);
                    publishMessageEvent(savedMessage, eventType);
                })
                .then();
            });
    }

    /**
     * Updates the status of a message (reactive) - overload with default updatedBy
     * 
     * @param messageId Message identifier
     * @param newStatus New status to set
     * @return Mono<Void> Completes when status is updated
     */
    public Mono<Void> updateStatus(String messageId, MessageStatus newStatus) {
        return updateStatus(messageId, newStatus, "system");
    }

    /**
     * Retrieves a message by its unique ID (reactive).
     * 
     * @param messageId Message identifier
     * @return Mono<Message> containing the message if found
     */
    public Mono<Message> getMessageById(String messageId) {
        return messageRepository.findByMessageId(messageId);
    }

    /**
     * Increments the retry count for a message (reactive).
     * 
     * Called by retry workers when attempting to resend a failed message.
     * 
     * @param messageId Message identifier
     * @return Mono<Void> Completes when retry count is incremented
     */
    public Mono<Void> incrementRetryCount(String messageId) {
        return getMessageById(messageId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found: " + messageId)))
            .flatMap(message -> {
                if (message.getMetadata() == null) {
                    message.setMetadata(Message.MessageMetadata.builder().retryCount(0).build());
                }

                int newRetryCount = (message.getMetadata().getRetryCount() != null
                    ? message.getMetadata().getRetryCount() : 0) + 1;

                message.getMetadata().setRetryCount(newRetryCount);
                message.setUpdatedAt(Instant.now());

                return messageRepository.save(message)
                    .doOnSuccess(savedMessage ->
                        log.info("Retry count incremented for message {}: {} attempts", messageId, newRetryCount)
                    )
                    .then();
            });
    }

    /**
     * Marks a message as failed with an error message (reactive).
     * 
     * @param messageId Message identifier
     * @param errorMessage Error description
     * @return Mono<Void> Completes when message is marked as failed
     */
    public Mono<Void> markAsFailed(String messageId, String errorMessage) {
        return getMessageById(messageId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found: " + messageId)))
            .flatMap(message -> {
                message.setStatus(MessageStatus.FAILED);
                message.setUpdatedAt(Instant.now());

                if (message.getMetadata() == null) {
                    message.setMetadata(Message.MessageMetadata.builder().build());
                }
                message.getMetadata().setErrorMessage(errorMessage);

                return messageRepository.save(message)
                    .doOnSuccess(savedMessage -> {
                        log.error("Message {} marked as FAILED: {}", messageId, errorMessage);

                        // Publish MESSAGE_FAILED event (fire-and-forget)
                        publishMessageEvent(savedMessage, MessageEvent.EventType.MESSAGE_FAILED);
                    })
                    .then();
            });
    }

    /**
     * Processes an inbound message from external platforms (webhook).
     * 
     * This is the entry point for messages received FROM customers via WhatsApp/Telegram/Instagram.
     * 
     * Flow:
     * 1. Check idempotency using platformMessageId
     * 2. Ensure conversation exists (create if needed)
     * 3. Build Message entity with status RECEIVED
     * 4. Persist to MongoDB
     * 5. Update conversation last activity
     * 6. Publish MESSAGE_RECEIVED event to Kafka
     * 
     * @param platformMessageId Platform-specific message identifier (for deduplication)
     * @param conversationId Conversation identifier
     * @param senderId Sender's platform identifier
     * @param content Message content
     * @param channel Platform channel
     * @param timestamp Message timestamp from platform (or current time)
     * @param metadata Platform-specific metadata
     * @param primaryChannel Primary channel for conversation creation
     * @return Mono<Message> Persisted inbound message
     */
    public Mono<Message> processInboundMessage(
        String platformMessageId,
        String conversationId,
        String senderId,
        String content,
        com.chat4all.common.constant.Channel channel,
        Instant timestamp,
        java.util.Map<String, Object> metadata,
        com.chat4all.common.constant.Channel primaryChannel
    ) {
        log.debug("Processing inbound message from {}: platformId={}, conversationId={}", 
            channel, platformMessageId, conversationId);

        // Use platformMessageId for idempotency (prevents duplicate webhook deliveries)
        return idempotencyService.isDuplicate(platformMessageId)
            .flatMap(isDuplicate -> {
                if (isDuplicate) {
                    log.warn("Duplicate inbound message detected: {}", platformMessageId);
                    // Return existing message instead of error (idempotent behavior)
                    // Use .next() to get first result (handles legacy duplicates gracefully)
                    return messageRepository.findByMetadataPlatformMessageId(platformMessageId)
                        .next()
                        .switchIfEmpty(Mono.defer(() -> {
                            // Inconsistent state: Redis key exists but MongoDB document missing
                            // This can happen due to race conditions, Redis TTL mismatch, or failed saves
                            log.error("INCONSISTENT STATE: Idempotency key exists but message not found in MongoDB: {}", platformMessageId);
                            log.info("Recovering: Removing stale idempotency key and reprocessing message: {}", platformMessageId);
                            
                            // Remove stale Redis key and reprocess message (resilient recovery)
                            return idempotencyService.remove(platformMessageId)
                                .then(conversationService.getOrCreateConversation(conversationId, primaryChannel, senderId))
                                .flatMap(conversation -> {
                                    log.info("Stale idempotency key removed, reprocessing message: {}", platformMessageId);
                                    
                                    // Build inbound message (duplicate logic for recovery path)
                                    Instant now = Instant.now();
                                    Message inboundMessage = Message.builder()
                                        .messageId(UUID.randomUUID().toString())
                                        .conversationId(conversationId)
                                        .senderId(senderId)
                                        .content(content)
                                        .contentType("TEXT")
                                        .channel(channel)
                                        .status(MessageStatus.RECEIVED)
                                        .timestamp(timestamp != null ? timestamp : now)
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .metadata(Message.MessageMetadata.builder()
                                            .platformMessageId(platformMessageId)
                                            .retryCount(0)
                                            .additionalData(metadata)
                                            .build())
                                        .build();

                                    // Persist recovered message
                                    return messageRepository.save(inboundMessage)
                                        .flatMap(savedMessage -> {
                                            log.info("RECOVERED: Inbound message persisted after stale key removal: {} (platform: {})",
                                                savedMessage.getMessageId(), platformMessageId);

                                            return conversationService.updateLastActivity(conversationId, savedMessage.getTimestamp())
                                                .thenReturn(savedMessage);
                                        })
                                        .doOnSuccess(savedMessage -> {
                                            publishMessageEvent(savedMessage, MessageEvent.EventType.MESSAGE_RECEIVED);
                                        });
                                });
                        }));
                }

                // Normal path: Ensure conversation exists (create if needed)
                return conversationService.getOrCreateConversation(conversationId, primaryChannel, senderId)
                    .flatMap(conversation -> {
                        log.debug("Conversation ready for inbound message: {}", conversationId);

                        // Build inbound message
                        Instant now = Instant.now();
                        Message inboundMessage = Message.builder()
                            .messageId(UUID.randomUUID().toString())
                            .conversationId(conversationId)
                            .senderId(senderId)
                            .content(content)
                            .contentType("TEXT")
                            .channel(channel)
                            .status(MessageStatus.RECEIVED) // Inbound messages start as RECEIVED
                            .timestamp(timestamp != null ? timestamp : now)
                            .createdAt(now)
                            .updatedAt(now)
                            .metadata(Message.MessageMetadata.builder()
                                .platformMessageId(platformMessageId)
                                .retryCount(0)
                                .additionalData(metadata)
                                .build())
                            .build();

                        // Persist message
                        return messageRepository.save(inboundMessage)
                            .flatMap(savedMessage -> {
                                log.info("Inbound message persisted: {} (conversation: {}, platform: {})",
                                    savedMessage.getMessageId(), conversationId, platformMessageId);

                                // Update conversation last activity
                                return conversationService.updateLastActivity(conversationId, savedMessage.getTimestamp())
                                    .thenReturn(savedMessage);
                            })
                            .doOnSuccess(savedMessage -> {
                                // Publish MESSAGE_RECEIVED event to Kafka (fire-and-forget)
                                publishMessageEvent(savedMessage, MessageEvent.EventType.MESSAGE_RECEIVED);
                            });
                    });
            });
    }

    /**
     * Publishes a message event to Kafka.
     * 
     * Converts Message entity to MessageEvent and sends to chat-events topic.
     * Uses conversation_id as partition key for ordering guarantees.
     * 
     * @param message Message entity
     * @param eventType Event type
     */
    private void publishMessageEvent(Message message, MessageEvent.EventType eventType) {
        try {
            MessageEvent event = MessageEvent.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .contentType(message.getContentType())
                .fileId(message.getFileId())
                .channel(message.getChannel())
                .timestamp(message.getTimestamp())
                .status(message.getStatus())
                .eventType(eventType)
                .metadata(message.getMetadata() != null ? message.getMetadata().getAdditionalData() : null)
                .build();

            messageProducer.sendMessageEvent(event);
            log.debug("Published {} event for message {}", eventType, message.getMessageId());

            // Also broadcast to WebSocket clients for real-time updates
            webSocketHandler.publishEvent(event);
            log.debug("Broadcasted {} event to {} WebSocket clients", 
                eventType, webSocketHandler.getActiveSessionCount());

        } catch (Exception e) {
            // Event publishing failure should not block message acceptance
            log.error("Failed to publish {} event for message {}: {}",
                eventType, message.getMessageId(), e.getMessage(), e);
            // TODO: Consider retry logic or DLQ for failed event publishing
        }
    }

    /**
     * Maps MessageStatus to corresponding MessageEvent.EventType.
     * 
     * @param status Message status
     * @return Corresponding event type
     */
    private MessageEvent.EventType mapStatusToEventType(MessageStatus status) {
        return switch (status) {
            case PENDING -> MessageEvent.EventType.MESSAGE_CREATED;
            case RECEIVED -> MessageEvent.EventType.MESSAGE_RECEIVED;
            case SENT -> MessageEvent.EventType.MESSAGE_SENT;
            case DELIVERED -> MessageEvent.EventType.MESSAGE_DELIVERED;
            case READ -> MessageEvent.EventType.MESSAGE_READ;
            case FAILED -> MessageEvent.EventType.MESSAGE_FAILED;
        };
    }
}
