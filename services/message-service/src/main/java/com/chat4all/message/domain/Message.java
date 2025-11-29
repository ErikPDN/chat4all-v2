package com.chat4all.message.domain;

import com.chat4all.common.constant.Channel;
import com.chat4all.common.constant.ContentType;
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
import java.util.List;
import java.util.Map;

/**
 * Message Entity for MongoDB
 * 
 * Represents a message in the unified messaging platform.
 * Stores all message data including content, status, and metadata.
 * 
 * Collection: messages
 * Sharding key: conversation_id (ensures co-location of conversation messages)
 * 
 * Validation (enforced by MongoDB JSON Schema validator):
 * - message_id: UUIDv4 format (FR-002)
 * - content: Max 10,000 characters (FR-003)
 * - status: ENUM (PENDING, SENT, DELIVERED, READ, FAILED)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_conversation_timestamp", def = "{'conversationId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_sender_timestamp", def = "{'senderId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "idx_status_updated", def = "{'status': 1, 'updatedAt': 1}")
})
public class Message {

    /**
     * MongoDB document ID (internal)
     */
    @Id
    private String id;

    /**
     * Unique message identifier (UUIDv4)
     * Used for idempotency checks and external references
     */
    @Indexed(unique = true)
    @Field("message_id")
    private String messageId;

    /**
     * Conversation this message belongs to
     * Used as partition key for Kafka and sharding key for MongoDB
     */
    @Indexed
    @Field("conversation_id")
    private String conversationId;

    /**
     * User ID of the message sender
     * References users.user_id in PostgreSQL
     */
    @Indexed
    @Field("sender_id")
    private String senderId;

    /**
     * List of recipient user IDs
     * For one-to-one: single recipient
     * For group: multiple recipients
     */
    @Field("recipient_ids")
    private List<String> recipientIds;

    /**
     * Message text content
     * Max 10,000 characters (FR-003)
     */
    private String content;

    /**
     * Content type
     * Enum: TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, etc.
     */
    @Field("content_type")
    private ContentType contentType;

    /**
     * File ID if content_type is not TEXT
     * References files.file_id in MongoDB
     * @deprecated Use fileIds instead (supports multiple attachments)
     */
    @Deprecated
    @Field("file_id")
    private String fileId;

    /**
     * List of file attachment IDs
     * References files.file_id in MongoDB
     * Supports multiple file attachments per message (FR-019)
     * 
     * Usage:
     * - Client uploads files via POST /files/initiate
     * - Client sends message with fileIds array
     * - Message service validates all fileIds exist and status=READY
     * - Files are linked to message
     */
    @Field("file_ids")
    private List<String> fileIds;

    /**
     * Platform channel for this message
     * Enum: WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL
     */
    private Channel channel;

    /**
     * Current delivery status
     * Enum: PENDING, SENT, DELIVERED, READ, FAILED
     * State transitions: PENDING → SENT → DELIVERED → READ (or → FAILED)
     */
    private MessageStatus status;

    /**
     * Message creation timestamp
     */
    private Instant timestamp;

    /**
     * Additional metadata
     */
    private MessageMetadata metadata;

    /**
     * Document creation timestamp
     */
    @Field("created_at")
    private Instant createdAt;

    /**
     * Document last update timestamp
     */
    @Field("updated_at")
    private Instant updatedAt;

    /**
     * Message metadata nested object
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageMetadata {
        /**
         * External platform message ID
         * e.g., "wamid.XXX" for WhatsApp
         * 
         * UNIQUE INDEX: Prevents duplicate inbound messages from webhooks
         * Sparse index: Only indexed when field is present (allows null values)
         */
        @Indexed(unique = true, sparse = true)
        @Field("platform_message_id")
        private String platformMessageId;

        /**
         * Number of delivery retry attempts
         */
        @Field("retry_count")
        private Integer retryCount;

        /**
         * Error message if status is FAILED
         */
        @Field("error_message")
        private String errorMessage;

        /**
         * Additional platform-specific metadata
         */
        @Field("additional_data")
        private Map<String, Object> additionalData;
    }
}
