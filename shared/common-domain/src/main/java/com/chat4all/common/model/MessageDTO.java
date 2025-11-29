package com.chat4all.common.model;

import com.chat4all.common.constant.Channel;
import com.chat4all.common.constant.ContentType;
import com.chat4all.common.constant.MessageStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Message Data Transfer Object
 * Represents a message in the unified messaging platform
 * 
 * Aligned with:
 * - FR-002: Unique message ID per message
 * - FR-003: Text content max 10,000 characters
 * - Data Model: messages collection in MongoDB
 * 
 * Task: T011
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageDTO {

    /**
     * Unique identifier for the message (UUIDv4)
     */
    @NotBlank(message = "Message ID cannot be blank")
    private String messageId;

    /**
     * ID of the conversation this message belongs to
     */
    @NotBlank(message = "Conversation ID cannot be blank")
    private String conversationId;

    /**
     * ID of the message sender (user_id)
     */
    @NotBlank(message = "Sender ID cannot be blank")
    private String senderId;

    /**
     * Message content (text)
     * Max 10,000 characters per specification
     */
    @NotBlank(message = "Content cannot be blank")
    @Size(max = 10000, message = "Content cannot exceed 10,000 characters")
    private String content;

    /**
     * Channel through which the message is sent/received
     */
    @NotNull(message = "Channel cannot be null")
    private Channel channel;

    /**
     * Message creation timestamp
     */
    @NotNull(message = "Timestamp cannot be null")
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Current status of the message
     */
    @NotNull(message = "Status cannot be null")
    @Builder.Default
    private MessageStatus status = MessageStatus.PENDING;

    /**
     * Content type (TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, etc.)
     */
    private ContentType contentType;

    /**
     * File ID reference if content_type != TEXT
     * References files collection in MongoDB
     */
    private String fileId;

    /**
     * List of recipient user IDs
     * For 1:1 conversations: single recipient
     * For group conversations: multiple recipients (max 100 per FR-027)
     */
    private List<String> recipientIds;

    /**
     * Optional metadata (platform-specific data, file references, etc.)
     * - platform_message_id: External platform's message ID
     * - retry_count: Number of delivery retry attempts
     * - error_message: Error details if status = FAILED
     */
    private Map<String, Object> metadata;

    /**
     * Record creation timestamp
     */
    private Instant createdAt;

    /**
     * Record last update timestamp
     */
    private Instant updatedAt;
}
