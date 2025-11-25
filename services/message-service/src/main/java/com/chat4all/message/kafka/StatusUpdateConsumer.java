package com.chat4all.message.kafka;

import com.chat4all.common.constant.MessageStatus;
import com.chat4all.common.event.MessageEvent;
import com.chat4all.message.service.MessageService;
import com.chat4all.message.websocket.MessageStatusWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Kafka Consumer for status-updates topic
 * 
 * Consumes status update events published by router-service and updates
 * message status in MongoDB with audit trail.
 * 
 * Topic: status-updates
 * Group ID: message-service-status-group
 * Concurrency: 3 parallel consumers
 * 
 * Event Format:
 * {
 *   "messageId": "uuid",
 *   "conversationId": "uuid",
 *   "status": "DELIVERED",
 *   "timestamp": "2025-11-24T18:30:00Z",
 *   "updatedBy": "router-service"
 * }
 * 
 * Flow:
 * 1. Receive status update event from Kafka
 * 2. Extract messageId and new status
 * 3. Call MessageService.updateStatus() (reactive)
 * 4. Update persisted in MongoDB with status history
 * 5. Acknowledge Kafka message (auto-commit)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusUpdateConsumer {

    private final MessageService messageService;
    private final MessageStatusWebSocketHandler webSocketHandler;

    /**
     * Consumes status update events from Kafka
     * 
     * @param statusUpdate Event payload containing messageId, status, etc.
     * @param partition Kafka partition for logging
     * @param offset Kafka offset for logging
     */
    @KafkaListener(
        topics = "status-updates",
        groupId = "message-service-status-group",
        containerFactory = "statusUpdateKafkaListenerContainerFactory"
    )
    public void consumeStatusUpdate(
        @Payload Map<String, Object> statusUpdate,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset
    ) {
        try {
            // Extract event data
            String messageId = (String) statusUpdate.get("messageId");
            String statusStr = (String) statusUpdate.get("status");
            String updatedBy = (String) statusUpdate.getOrDefault("updatedBy", "router-service");

            log.info("Received status update: messageId={}, status={}, partition={}, offset={}",
                messageId, statusStr, partition, offset);

            // Validate required fields
            if (messageId == null || messageId.trim().isEmpty()) {
                log.error("Invalid status update: missing messageId in event: {}", statusUpdate);
                return;
            }

            if (statusStr == null || statusStr.trim().isEmpty()) {
                log.error("Invalid status update: missing status in event: {}", statusUpdate);
                return;
            }

            // Parse status enum
            MessageStatus newStatus;
            try {
                newStatus = MessageStatus.valueOf(statusStr);
            } catch (IllegalArgumentException e) {
                log.error("Invalid status update: unknown status '{}' for message {}", statusStr, messageId);
                return;
            }

            // Update message status (reactive) - block to ensure completion before ACK
            messageService.updateStatus(messageId, newStatus, updatedBy)
                .then(messageService.getMessageById(messageId))
                .doOnSuccess(updatedMessage -> {
                    // Broadcast status update to WebSocket clients
                    if (updatedMessage != null) {
                        MessageEvent event = MessageEvent.builder()
                            .eventType(MessageEvent.EventType.STATUS_UPDATE)
                            .messageId(updatedMessage.getMessageId())
                            .conversationId(updatedMessage.getConversationId())
                            .senderId(updatedMessage.getSenderId())
                            .channel(updatedMessage.getChannel())
                            .status(updatedMessage.getStatus())
                            .timestamp(updatedMessage.getUpdatedAt())
                            .build();
                        
                        webSocketHandler.publishEvent(event);
                        log.debug("Broadcasted status update to {} WebSocket clients", 
                            webSocketHandler.getActiveSessionCount());
                    }
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    // Message not found - log warning and continue
                    log.warn("Status update for unknown message: {} (status: {})", messageId, newStatus);
                    return Mono.empty();
                })
                .onErrorResume(IllegalStateException.class, e -> {
                    // Invalid status transition - log error and continue
                    log.error("Invalid status transition for message {}: {}", messageId, e.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    // Unexpected error - log and continue
                    log.error("Error updating status for message {}: {}", messageId, e.getMessage(), e);
                    return Mono.empty();
                })
                .block(); // Block to ensure update completes before Kafka ACK

            log.debug("Successfully processed status update for message: {}", messageId);

        } catch (Exception e) {
            log.error("Error processing status update event: {}", e.getMessage(), e);
            // Don't rethrow - acknowledge and continue to prevent infinite retries
        }
    }
}
