package com.chat4all.router.consumer;

import com.chat4all.common.event.MessageEvent;
import com.chat4all.router.handler.DeduplicationHandler;
import com.chat4all.router.handler.RoutingHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Message Event Consumer (T044)
 * 
 * Kafka consumer that listens to the chat-events topic and processes incoming messages.
 * 
 * Flow:
 * 1. Receive MessageEvent from Kafka
 * 2. Check for duplicates (deduplication)
 * 3. Route message to appropriate connector
 * 4. Handle success/failure
 * 5. Manually commit offset after successful processing
 * 
 * Key Features:
 * - Manual offset commit for at-least-once delivery guarantee
 * - Deduplication to prevent duplicate deliveries
 * - Error handling with DLQ fallback
 * - Distributed tracing support
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEventConsumer {

    private final DeduplicationHandler deduplicationHandler;
    private final RoutingHandler routingHandler;

    /**
     * Kafka listener for chat-events topic.
     * 
     * Consumes MessageEvent objects and routes them to appropriate connectors.
     * Uses manual acknowledgment for precise offset control.
     * 
     * @param messageEvent The message event from Kafka
     * @param partition The partition this message came from
     * @param offset The offset of this message
     * @param acknowledgment Manual acknowledgment handle
     */
    @KafkaListener(
        topics = "${app.kafka.topics.chat-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMessageEvent(
            @Payload MessageEvent messageEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received MessageEvent from Kafka - messageId: {}, conversationId: {}, channel: {}, partition: {}, offset: {}",
                messageEvent.getMessageId(),
                messageEvent.getConversationId(),
                messageEvent.getChannel(),
                partition,
                offset);

        try {
            // Step 1: Deduplication check
            log.debug("Checking for duplicate message: {}", messageEvent.getMessageId());
            if (deduplicationHandler.isDuplicate(messageEvent.getMessageId())) {
                log.warn("Duplicate message detected, skipping processing: {}", messageEvent.getMessageId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 2: Route message to appropriate connector
            log.debug("Routing message to connector: messageId={}, channel={}",
                    messageEvent.getMessageId(), messageEvent.getChannel());
            routingHandler.routeMessage(messageEvent);

            // Step 3: Mark as processed in deduplication cache
            deduplicationHandler.markAsProcessed(messageEvent.getMessageId());

            // Step 4: Acknowledge successful processing
            acknowledgment.acknowledge();
            log.info("Successfully processed and acknowledged message: {}", messageEvent.getMessageId());

        } catch (Exception e) {
            log.error("Error processing message {}: {}", messageEvent.getMessageId(), e.getMessage(), e);
            
            // Don't acknowledge - message will be redelivered
            // After max retries, Kafka will move it to DLQ (configured in Kafka)
            // For now, we'll log and continue
            log.error("Message processing failed, will be retried by Kafka: {}", messageEvent.getMessageId());
            
            // In production, you might want to implement custom DLQ logic here
            // For MVP, we rely on Kafka's retry mechanism
        }
    }
}
