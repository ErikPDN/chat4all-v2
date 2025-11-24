package com.chat4all.message.kafka;

import com.chat4all.common.event.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Message Producer
 * 
 * Publishes message events to the chat-events topic.
 * Uses conversation_id as partition key to ensure ordering per conversation.
 * 
 * Topic: chat-events
 * Partitions: 10 (configured in topics.json)
 * Partition Key: conversation_id
 * 
 * Producer Configuration (from KafkaProducerConfig):
 * - Idempotence: Enabled (prevents duplicates)
 * - Acks: all (wait for all in-sync replicas)
 * - Retries: Unlimited (for transient failures)
 * - Compression: Snappy
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Kafka topic name for message events
     */
    private static final String TOPIC_CHAT_EVENTS = "chat-events";

    /**
     * Sends a message event to Kafka.
     * 
     * Uses conversation_id as partition key to ensure:
     * 1. All messages for a conversation go to the same partition
     * 2. Messages are processed in order per conversation
     * 3. Router service can consume messages with guaranteed ordering
     * 
     * Async operation with callback logging.
     * Failures are logged but do not block the caller (fire-and-forget).
     * 
     * @param event MessageEvent to publish
     */
    public void sendMessageEvent(MessageEvent event) {
        // Use conversation_id as partition key (ensures ordering per conversation)
        String partitionKey = event.getPartitionKey(); // conversation_id

        log.debug("Publishing {} event to Kafka: messageId={}, conversationId={}, partitionKey={}",
            event.getEventType(), event.getMessageId(), event.getConversationId(), partitionKey);

        try {
            // Send to Kafka (async)
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                TOPIC_CHAT_EVENTS,
                partitionKey,
                event
            );

            // Handle success
            future.thenAccept(result -> {
                int partition = result.getRecordMetadata().partition();
                long offset = result.getRecordMetadata().offset();
                log.info("Message event published successfully: messageId={}, topic={}, partition={}, offset={}",
                    event.getMessageId(), TOPIC_CHAT_EVENTS, partition, offset);
            });

            // Handle failure
            future.exceptionally(ex -> {
                log.error("Failed to publish message event: messageId={}, conversationId={}, error={}",
                    event.getMessageId(), event.getConversationId(), ex.getMessage(), ex);
                
                // TODO: Implement retry logic or DLQ for critical events
                // For now, log the error and continue (message is already persisted in MongoDB)
                
                return null;
            });

        } catch (Exception e) {
            // Catch synchronous errors (e.g., serialization failures)
            log.error("Synchronous error publishing message event: messageId={}, error={}",
                event.getMessageId(), e.getMessage(), e);
        }
    }

    /**
     * Sends a message event synchronously (blocks until sent).
     * 
     * Use this method when you need confirmation that the event was published
     * before proceeding (e.g., in critical workflows).
     * 
     * @param event MessageEvent to publish
     * @return SendResult with partition and offset information
     * @throws Exception if publishing fails
     */
    public SendResult<String, Object> sendMessageEventSync(MessageEvent event) throws Exception {
        String partitionKey = event.getPartitionKey();

        log.debug("Publishing {} event to Kafka (sync): messageId={}, conversationId={}",
            event.getEventType(), event.getMessageId(), event.getConversationId());

        SendResult<String, Object> result = kafkaTemplate.send(
            TOPIC_CHAT_EVENTS,
            partitionKey,
            event
        ).get(); // Blocks until sent

        int partition = result.getRecordMetadata().partition();
        long offset = result.getRecordMetadata().offset();

        log.info("Message event published successfully (sync): messageId={}, partition={}, offset={}",
            event.getMessageId(), partition, offset);

        return result;
    }
}
