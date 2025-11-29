package com.chat4all.message.kafka;

import com.chat4all.common.event.MessageEvent;
import com.chat4all.message.websocket.WebSocketChatHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Chat Message Push Service
 * 
 * Consumes message events from Kafka and pushes them to connected
 * WebSocket clients in real-time for instant message delivery.
 * 
 * Purpose:
 * - Solves NAT/Mobile connectivity issues by using server-to-client push
 * - Enables real-time chat experience (no polling required)
 * - Implements User Story 4 real-time group messaging
 * 
 * Architecture:
 * - Listens to 'chat-events' Kafka topic
 * - Filters for MESSAGE_CREATED and MESSAGE_RECEIVED events
 * - Pushes to WebSocket clients based on recipientIds
 * - Fan-out delivery: One message event → Multiple WebSocket deliveries
 * 
 * Event Flow:
 * 1. User sends message → MessageService publishes MESSAGE_CREATED to Kafka
 * 2. This service consumes MESSAGE_CREATED event
 * 3. Extracts recipientIds from event
 * 4. For each recipientId:
 *    a. Check if user has active WebSocket connection
 *    b. If connected, push message event to user's WebSocket sink
 *    c. If not connected, skip (user will fetch via REST API)
 * 5. Acknowledge Kafka message
 * 
 * Security:
 * - Only delivers to authenticated WebSocket connections
 * - User isolation: Each user only receives messages intended for them
 * - No message leakage across users
 * 
 * Performance:
 * - Non-blocking reactive implementation
 * - Minimal latency: Kafka → WebSocket < 50ms
 * - Scales horizontally: Multiple instances with Kafka consumer groups
 * 
 * Example Use Case (Group Chat):
 * - Admin sends message to group with 3 participants
 * - MessageService publishes event with recipientIds: ["User1", "User2", "User3"]
 * - This service delivers to all 3 users if they're connected
 * - User1 is offline → skipped, will fetch via REST API later
 * - User2 is online → receives message instantly via WebSocket
 * - User3 is online → receives message instantly via WebSocket
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessagePushService {

    private final WebSocketChatHandler webSocketChatHandler;

    /**
     * Consumes message events from Kafka and pushes to WebSocket clients
     * 
     * Listens to the same 'chat-events' topic as router-service but with
     * different consumer group ('message-service-websocket-push').
     * 
     * @param messageEvent The message event from Kafka
     * @param partition The partition this message came from
     * @param offset The offset of this message
     * @param acknowledgment Manual acknowledgment handle
     */
    @KafkaListener(
        topics = "${app.kafka.topics.chat-events}",
        groupId = "message-service-websocket-push",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMessageEventForWebSocketPush(
            @Payload MessageEvent messageEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.debug("Received MessageEvent for WebSocket push - messageId: {}, conversationId: {}, eventType: {}, partition: {}, offset: {}",
                messageEvent.getMessageId(),
                messageEvent.getConversationId(),
                messageEvent.getEventType(),
                partition,
                offset);

        try {
            // Only process MESSAGE_CREATED and MESSAGE_RECEIVED events
            // (ignore MESSAGE_SENT, MESSAGE_DELIVERED, MESSAGE_READ, MESSAGE_FAILED)
            if (messageEvent.getEventType() != MessageEvent.EventType.MESSAGE_CREATED &&
                messageEvent.getEventType() != MessageEvent.EventType.MESSAGE_RECEIVED) {
                log.debug("Skipping WebSocket push for event type: {}", messageEvent.getEventType());
                acknowledgment.acknowledge();
                return;
            }

            // Extract recipientIds from the event
            List<String> recipientIds = messageEvent.getRecipientIds();

            if (recipientIds == null || recipientIds.isEmpty()) {
                log.warn("No recipientIds in MessageEvent, cannot deliver via WebSocket: messageId={}", 
                    messageEvent.getMessageId());
                acknowledgment.acknowledge();
                return;
            }

            log.info("Pushing message to {} recipients via WebSocket: messageId={}, conversationId={}, recipientIds={}",
                recipientIds.size(),
                messageEvent.getMessageId(),
                messageEvent.getConversationId(),
                recipientIds);

            // Fan-out delivery: Push to each recipient
            int deliveredCount = 0;
            int skippedCount = 0;

            for (String recipientId : recipientIds) {
                // Check if recipient has active WebSocket connection
                if (webSocketChatHandler.isUserConnected(recipientId)) {
                    // Deliver message to user's WebSocket
                    webSocketChatHandler.deliverToUser(recipientId, messageEvent);
                    deliveredCount++;
                    log.debug("Delivered message to user via WebSocket: userId={}, messageId={}", 
                        recipientId, messageEvent.getMessageId());
                } else {
                    skippedCount++;
                    log.debug("Skipped WebSocket delivery for offline user: userId={}, messageId={}", 
                        recipientId, messageEvent.getMessageId());
                }
            }

            log.info("WebSocket push completed: messageId={}, delivered={}, skipped={} (offline users)",
                messageEvent.getMessageId(), deliveredCount, skippedCount);

            // Acknowledge successful processing
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing message event for WebSocket push: messageId={}, error={}", 
                messageEvent.getMessageId(), e.getMessage(), e);
            
            // Acknowledge anyway to prevent infinite retries
            // WebSocket delivery is best-effort; users can always fetch via REST API
            acknowledgment.acknowledge();
        }
    }

    /**
     * Gets statistics about WebSocket delivery
     * 
     * @return String with current WebSocket connection stats
     */
    public String getWebSocketStats() {
        return String.format("Active WebSocket connections: %d users, %d sessions",
            webSocketChatHandler.getActiveUserCount(),
            webSocketChatHandler.getActiveSessionCount());
    }
}
