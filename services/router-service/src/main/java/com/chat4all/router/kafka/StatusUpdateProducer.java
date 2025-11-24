package com.chat4all.router.kafka;

import com.chat4all.common.constant.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Status Update Producer (T050)
 * 
 * Publishes message status updates back to Kafka for consumption by message-service.
 * 
 * Flow:
 * 1. Router delivers message to connector
 * 2. Router publishes status update to Kafka (status-updates topic)
 * 3. Message-service consumes status update (T052)
 * 4. Message-service updates MongoDB with new status
 * 
 * Status Update Message Format:
 * {
 *   "messageId": "uuid",
 *   "status": "DELIVERED",
 *   "timestamp": "2024-11-24T12:00:00Z",
 *   "updatedBy": "router-service"
 * }
 * 
 * Key Features:
 * - Fire-and-forget publishing (non-blocking)
 * - Uses messageId as partition key (ensures ordering)
 * - Idempotent (duplicate updates are harmless)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatusUpdateProducer {

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    @Value("${app.kafka.topics.status-updates:status-updates}")
    private String statusUpdatesTopic;

    /**
     * Publishes a status update for a message.
     * 
     * This is a fire-and-forget operation - we don't wait for acknowledgment.
     * If Kafka is unavailable, the message won't be updated, but delivery continues.
     * 
     * @param messageId The message ID to update
     * @param newStatus The new status
     */
    public void publishStatusUpdate(String messageId, MessageStatus newStatus) {
        log.debug("Publishing status update: messageId={}, status={}", messageId, newStatus);

        try {
            // Build status update payload
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("messageId", messageId);
            statusUpdate.put("status", newStatus.name());
            statusUpdate.put("timestamp", System.currentTimeMillis());
            statusUpdate.put("updatedBy", "router-service");

            // Publish to Kafka (fire-and-forget with callback logging)
            kafkaTemplate.send(statusUpdatesTopic, messageId, statusUpdate)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish status update: messageId={}, status={}, error={}",
                                messageId, newStatus, ex.getMessage());
                    } else {
                        log.debug("Status update published successfully: messageId={}, status={}, partition={}, offset={}",
                                messageId,
                                newStatus,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

        } catch (Exception e) {
            log.error("Error publishing status update: messageId={}, status={}, error={}",
                    messageId, newStatus, e.getMessage(), e);
            // Non-critical failure - don't block routing
        }
    }

    /**
     * Publishes a status update with additional metadata.
     * 
     * Used for error scenarios where we want to include error details.
     * 
     * @param messageId The message ID to update
     * @param newStatus The new status
     * @param errorMessage Optional error message (for FAILED status)
     */
    public void publishStatusUpdate(String messageId, MessageStatus newStatus, String errorMessage) {
        log.debug("Publishing status update with error: messageId={}, status={}, error={}",
                messageId, newStatus, errorMessage);

        try {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("messageId", messageId);
            statusUpdate.put("status", newStatus.name());
            statusUpdate.put("timestamp", System.currentTimeMillis());
            statusUpdate.put("updatedBy", "router-service");
            
            if (errorMessage != null && !errorMessage.isEmpty()) {
                statusUpdate.put("errorMessage", errorMessage);
            }

            kafkaTemplate.send(statusUpdatesTopic, messageId, statusUpdate)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish status update with error: messageId={}, error={}",
                                messageId, ex.getMessage());
                    } else {
                        log.debug("Status update with error published: messageId={}", messageId);
                    }
                });

        } catch (Exception e) {
            log.error("Error publishing status update with error: messageId={}, error={}",
                    messageId, e.getMessage(), e);
        }
    }
}
