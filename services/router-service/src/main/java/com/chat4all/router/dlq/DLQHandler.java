package com.chat4all.router.dlq;

import com.chat4all.common.event.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Dead Letter Queue Handler (T049)
 * 
 * Handles messages that have exceeded maximum retry attempts.
 * 
 * Responsibilities (FR-009):
 * - Publish failed messages to DLQ Kafka topic (chat-events-dlq)
 * - Log failure details for monitoring and debugging
 * - Preserve original message metadata
 * - Enable manual intervention for failed messages
 * 
 * DLQ Message Format:
 * - Original MessageEvent
 * - Failure reason
 * - Timestamp of failure
 * - Number of retry attempts
 * 
 * DLQ Processing (future):
 * - Manual review dashboard
 * - Automatic retry after connector recovery
 * - Alert/notification on DLQ threshold
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DLQHandler {

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;

    @Value("${app.kafka.topics.dlq:chat-events-dlq}")
    private String dlqTopic;

    /**
     * Sends a failed message to the Dead Letter Queue.
     * 
     * Called when a message has failed delivery after all retry attempts.
     * 
     * @param messageEvent The message that failed delivery
     * @param failureReason Description of why delivery failed
     * @param retryAttempts Number of retry attempts made
     */
    public void sendToDLQ(MessageEvent messageEvent, String failureReason, int retryAttempts) {
        log.error("Sending message to DLQ: messageId={}, reason={}, attempts={}",
                messageEvent.getMessageId(),
                failureReason,
                retryAttempts);

        try {
            // In production, we'd wrap in a DLQMessage with metadata
            // For MVP, we'll just send the original event
            
            kafkaTemplate.send(dlqTopic, messageEvent.getMessageId(), messageEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message to DLQ: messageId={}, error={}",
                                messageEvent.getMessageId(), ex.getMessage(), ex);
                    } else {
                        log.info("Message sent to DLQ successfully: messageId={}, partition={}, offset={}",
                                messageEvent.getMessageId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });

        } catch (Exception e) {
            log.error("Error sending message to DLQ: messageId={}, error={}",
                    messageEvent.getMessageId(), e.getMessage(), e);
            
            // Critical failure - can't send to DLQ
            // In production, this should trigger alerts
            // For now, we'll just log to file/console
            logFailedMessage(messageEvent, failureReason, retryAttempts);
        }
    }

    /**
     * Logs a failed message to persistent storage when DLQ is unavailable.
     * 
     * Fallback mechanism for critical failures.
     * 
     * @param messageEvent The failed message
     * @param failureReason Reason for failure
     * @param retryAttempts Number of retry attempts
     */
    private void logFailedMessage(MessageEvent messageEvent, String failureReason, int retryAttempts) {
        log.error("!!! CRITICAL: Message failed and could not be sent to DLQ !!!");
        log.error("Message ID: {}", messageEvent.getMessageId());
        log.error("Conversation ID: {}", messageEvent.getConversationId());
        log.error("Channel: {}", messageEvent.getChannel());
        log.error("Content: {}", messageEvent.getContent());
        log.error("Failure Reason: {}", failureReason);
        log.error("Retry Attempts: {}", retryAttempts);
        log.error("!!! Manual intervention required !!!");

        // TODO: In production, write to persistent log file or external system
        // e.g., write to MongoDB "failed_messages" collection
        // or send to external monitoring system
    }

    /**
     * Gets the count of messages in DLQ (for monitoring).
     * 
     * MVP: Not implemented
     * Production: Query Kafka consumer lag for DLQ topic
     * 
     * @return Number of messages in DLQ
     */
    public long getDLQMessageCount() {
        // TODO: Implement DLQ monitoring
        log.debug("getDLQMessageCount called (not implemented in MVP)");
        return 0L;
    }

    /**
     * Retries a message from DLQ (for manual intervention).
     * 
     * MVP: Not implemented
     * Production: Re-publish to chat-events topic for retry
     * 
     * @param messageId The message ID to retry
     */
    public void retryFromDLQ(String messageId) {
        log.info("retryFromDLQ called for message: {} (not implemented in MVP)", messageId);
        
        // TODO: Implement manual retry from DLQ
        // 1. Fetch message from DLQ topic
        // 2. Reset retry counter
        // 3. Re-publish to chat-events topic
    }
}
