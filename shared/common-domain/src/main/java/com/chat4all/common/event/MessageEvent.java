package com.chat4all.common.event;

import com.chat4all.common.constant.Channel;
import com.chat4all.common.constant.MessageStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Message Event for Kafka streaming
 * Published to 'chat-events' topic
 * 
 * Aligned with:
 * - Research: Kafka topic design with conversation-based partitioning
 * - Data Model: Event schema for message lifecycle events
 * - Constitutional Principle IV: Causal ordering via conversation_id partitioning
 * 
 * Partition Key: conversation_id (ensures all messages for a conversation go to same partition)
 * Retention: 7 days (configurable)
 * Schema: Avro-compatible JSON
 * 
 * Task: T015
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageEvent {

    /**
     * Event type discriminator
     * - MESSAGE_CREATED: New message received
     * - MESSAGE_SENT: Message sent to external platform
     * - MESSAGE_DELIVERED: Delivery confirmed by platform
     * - MESSAGE_READ: Read receipt received
     * - MESSAGE_FAILED: Delivery failed
     */
    @NotNull(message = "Event type is required")
    private EventType eventType;

    /**
     * Unique message identifier (UUIDv4 format)
     * Used for idempotency checks across services
     */
    @NotNull(message = "Message ID is required")
    private String messageId;

    /**
     * Conversation identifier
     * Used as Kafka partition key to ensure ordering
     */
    @NotNull(message = "Conversation ID is required")
    private String conversationId;

    /**
     * Internal user ID of the message sender
     */
    @NotNull(message = "Sender ID is required")
    private String senderId;

    /**
     * Message content (text)
     * Max 10,000 characters
     */
    private String content;

    /**
     * Content type (TEXT, FILE, IMAGE, VIDEO, AUDIO)
     */
    private String contentType;

    /**
     * File ID reference if content_type != TEXT
     */
    private String fileId;

    /**
     * Messaging channel/platform
     */
    @NotNull(message = "Channel is required")
    private Channel channel;

    /**
     * Current message status
     */
    @NotNull(message = "Status is required")
    private MessageStatus status;

    /**
     * Event timestamp (when event occurred)
     * Formatted as Unix timestamp (milliseconds since epoch)
     */
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    /**
     * Additional event metadata
     * - platform_message_id: External platform's message ID
     * - retry_count: Delivery retry attempt number
     * - error_message: Error details if status = FAILED
     * - trace_id: Distributed tracing correlation ID
     * - user_agent: Client application identifier
     */
    private Map<String, Object> metadata;

    /**
     * Event type enumeration
     */
    public enum EventType {
        /**
         * New message created in the system
         * Triggers routing to appropriate connector
         */
        MESSAGE_CREATED,

        /**
         * Message sent to external platform
         * Status updated to SENT
         */
        MESSAGE_SENT,

        /**
         * Message delivered to recipient's device
         * Status updated to DELIVERED
         */
        MESSAGE_DELIVERED,

        /**
         * Message read by recipient
         * Status updated to READ
         */
        MESSAGE_READ,

        /**
         * Message delivery failed
         * Status updated to FAILED, includes error details
         */
        MESSAGE_FAILED,

        /**
         * Status update from external platform webhook
         * Generic status change event
         */
        STATUS_UPDATE
    }

    /**
     * Get the Kafka partition key for this event
     * Ensures all messages for a conversation go to the same partition
     * 
     * @return conversation ID to be used as partition key
     */
    public String getPartitionKey() {
        return conversationId;
    }

    /**
     * Check if this event represents a terminal state
     * @return true if status is READ or FAILED
     */
    public boolean isTerminalEvent() {
        return status != null && status.isTerminal();
    }
}
