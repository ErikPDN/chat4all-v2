package com.chat4all.message.domain;

import com.chat4all.common.constant.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Message Status History Entity for MongoDB
 * 
 * Immutable audit trail for message status transitions.
 * Enables debugging, analytics, and compliance reporting.
 * 
 * Collection: message_status_history
 * 
 * Status Transition Flow:
 * - PENDING → SENT (message accepted by external platform API)
 * - SENT → DELIVERED (delivery confirmed by platform)
 * - DELIVERED → READ (read receipt received)
 * - Any status → FAILED (delivery failure at any stage)
 * 
 * Use Cases:
 * - Debugging delivery issues (trace full status lifecycle)
 * - SLA monitoring (calculate time between status transitions)
 * - Analytics (delivery success rates, platform performance)
 * - Audit compliance (immutable record of all state changes)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 * @see Message
 * @see MessageStatus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "message_status_history")
@CompoundIndexes({
    @CompoundIndex(name = "idx_message_timestamp", def = "{'messageId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_status_timestamp", def = "{'newStatus': 1, 'timestamp': -1}")
})
public class MessageStatusHistory {

    /**
     * MongoDB document ID (internal)
     */
    @Id
    private String id;

    /**
     * Message identifier this history entry belongs to
     * References messages.message_id
     */
    @Indexed
    @Field("message_id")
    private String messageId;

    /**
     * Conversation identifier for contextual queries
     * References conversations.conversation_id
     */
    @Indexed
    @Field("conversation_id")
    private String conversationId;

    /**
     * Previous status before transition
     * Null for initial PENDING status
     */
    @Field("old_status")
    private MessageStatus oldStatus;

    /**
     * New status after transition
     */
    @Field("new_status")
    private MessageStatus newStatus;

    /**
     * Timestamp when status transition occurred
     */
    private Instant timestamp;

    /**
     * Actor who triggered the status change
     * - "system" for automated transitions
     * - "router-service" for delivery confirmations
     * - "webhook-{platform}" for external platform updates
     * - User ID for manual interventions
     */
    @Field("updated_by")
    private String updatedBy;

    /**
     * Error message if transition to FAILED status
     * Contains exception details, API error codes, etc.
     */
    @Field("error_message")
    private String errorMessage;

    /**
     * Additional metadata for debugging
     * - platform_message_id: External platform's message ID
     * - retry_count: Delivery attempt number when status changed
     * - trace_id: Distributed tracing correlation ID
     * - webhook_id: Webhook event ID that triggered update
     */
    private java.util.Map<String, Object> metadata;

    /**
     * Document creation timestamp (immutable)
     */
    @Field("created_at")
    private Instant createdAt;

    /**
     * Creates a new status history entry for a status transition
     * 
     * @param messageId Message identifier
     * @param conversationId Conversation identifier
     * @param oldStatus Previous status (null for initial)
     * @param newStatus New status
     * @param updatedBy Actor triggering the change
     * @return MessageStatusHistory instance
     */
    public static MessageStatusHistory createTransition(
        String messageId,
        String conversationId,
        MessageStatus oldStatus,
        MessageStatus newStatus,
        String updatedBy
    ) {
        Instant now = Instant.now();
        return MessageStatusHistory.builder()
            .messageId(messageId)
            .conversationId(conversationId)
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .timestamp(now)
            .createdAt(now)
            .updatedBy(updatedBy)
            .build();
    }

    /**
     * Creates a status history entry for a failed transition
     * 
     * @param messageId Message identifier
     * @param conversationId Conversation identifier
     * @param oldStatus Previous status
     * @param errorMessage Error details
     * @param updatedBy Actor triggering the change
     * @return MessageStatusHistory instance with FAILED status
     */
    public static MessageStatusHistory createFailure(
        String messageId,
        String conversationId,
        MessageStatus oldStatus,
        String errorMessage,
        String updatedBy
    ) {
        Instant now = Instant.now();
        return MessageStatusHistory.builder()
            .messageId(messageId)
            .conversationId(conversationId)
            .oldStatus(oldStatus)
            .newStatus(MessageStatus.FAILED)
            .timestamp(now)
            .createdAt(now)
            .updatedBy(updatedBy)
            .errorMessage(errorMessage)
            .build();
    }
}
