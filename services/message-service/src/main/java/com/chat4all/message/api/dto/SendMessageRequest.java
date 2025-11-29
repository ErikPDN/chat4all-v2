package com.chat4all.message.api.dto;

import com.chat4all.common.constant.Channel;
import com.chat4all.common.constant.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for sending a message
 * 
 * Used by POST /messages endpoint.
 * Contains validation annotations to ensure data integrity.
 * 
 * Validation Rules (FR-003):
 * - conversationId: Required, not blank
 * - senderId: Required, not blank
 * - content: Required for text messages, max 10,000 characters
 * - channel: Required, must be valid Channel enum
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    /**
     * Conversation ID this message belongs to
     * Required field
     */
    @NotBlank(message = "conversationId is required")
    private String conversationId;

    /**
     * User ID of the message sender
     * Required field
     */
    @NotBlank(message = "senderId is required")
    private String senderId;

    /**
     * List of recipient user IDs
     * Optional - if not provided, will be derived from conversation participants
     */
    private List<@NotBlank String> recipientIds;

    /**
     * Message text content
     * Required for text messages
     * Max 10,000 characters (FR-003)
     */
    @NotBlank(message = "content is required")
    @Size(max = 10000, message = "content must not exceed 10,000 characters")
    private String content;

    /**
     * Content type
     * Default: TEXT
     * Enum: TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, etc.
     */
    @Builder.Default
    private ContentType contentType = ContentType.TEXT;

    /**
     * File ID if content type is not TEXT
     * References files.file_id in MongoDB
     * @deprecated Use fileIds instead (supports multiple attachments)
     */
    @Deprecated
    private String fileId;

    /**
     * List of file attachment IDs
     * References files.file_id in MongoDB
     * Supports multiple file attachments per message (FR-019)
     * 
     * Validation:
     * - All fileIds must exist in file-service
     * - All files must have status=READY (malware scan passed)
     * - Max 10 files per message
     * 
     * Usage:
     * 1. Client uploads files via POST /files/initiate
     * 2. Client receives fileIds in response
     * 3. Client includes fileIds in SendMessageRequest
     * 4. Message service validates files and creates message
     */
    @Size(max = 10, message = "Maximum 10 file attachments per message")
    private List<@NotBlank String> fileIds;

    /**
     * Platform channel for message delivery
     * Required field
     * Enum: WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL
     */
    @NotNull(message = "channel is required")
    private Channel channel;

    /**
     * Optional client-provided message ID for idempotency
     * If not provided, server will generate one
     */
    private String messageId;
}
