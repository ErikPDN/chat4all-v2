package com.chat4all.message.api.dto;

import com.chat4all.common.constant.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Platform-agnostic DTO for inbound messages from external platforms (WhatsApp, Telegram, Instagram)
 * 
 * This DTO standardizes webhook payloads from different platforms into a common format
 * before processing and persistence.
 * 
 * Example Webhook Payload:
 * {
 *   "platformMessageId": "wamid.HBgNMTIzNDU2Nzg5MAkz...",
 *   "conversationId": "conv-whatsapp-5551234567890",
 *   "senderId": "5551234567890",
 *   "senderName": "João Silva",
 *   "content": "Olá, preciso de ajuda!",
 *   "channel": "WHATSAPP",
 *   "timestamp": "2025-11-24T19:30:00Z",
 *   "metadata": {
 *     "from": "5551234567890",
 *     "messageType": "text",
 *     "context": null
 *   }
 * }
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundMessageDTO {

    /**
     * Platform-specific message identifier (e.g., WhatsApp message ID, Telegram update_id)
     * Used for deduplication and tracking across platform APIs
     */
    @NotBlank(message = "Platform message ID is required")
    @Size(max = 255, message = "Platform message ID cannot exceed 255 characters")
    private String platformMessageId;

    /**
     * Conversation identifier (may be generated from platform identifiers)
     * Format: conv-{channel}-{platformConversationId}
     * Example: conv-whatsapp-5551234567890
     */
    @Size(max = 255, message = "Conversation ID cannot exceed 255 characters")
    private String conversationId;

    /**
     * Sender's platform-specific identifier (phone number, user ID, etc.)
     * Examples:
     * - WhatsApp: phone number (5551234567890)
     * - Telegram: user_id (123456789)
     * - Instagram: instagram_scoped_id (1234567890123456)
     */
    @NotBlank(message = "Sender ID is required")
    @Size(max = 255, message = "Sender ID cannot exceed 255 characters")
    private String senderId;

    /**
     * Sender's display name from platform (optional)
     * Used for user experience and potential identity matching
     */
    @Size(max = 255, message = "Sender name cannot exceed 255 characters")
    private String senderName;

    /**
     * Message content (text)
     * Maximum 10,000 characters per platform limits
     */
    @NotBlank(message = "Content is required")
    @Size(max = 10000, message = "Content cannot exceed 10,000 characters")
    private String content;

    /**
     * External platform channel (WHATSAPP, TELEGRAM, INSTAGRAM)
     */
    @NotNull(message = "Channel is required")
    private Channel channel;

    /**
     * Message timestamp from platform (if available)
     * If not provided, will use current server time
     */
    private Instant timestamp;

    /**
     * Platform-specific metadata (original webhook payload subset)
     * Preserved for debugging, audit trail, and platform-specific features
     * 
     * Examples:
     * - WhatsApp: { "from": "...", "messageType": "text", "context": {...} }
     * - Telegram: { "update_id": 123, "chat": {...}, "message_type": "text" }
     */
    private Map<String, Object> metadata;

    /**
     * File ID reference if message contains attachment
     * Links to file stored in file-service
     */
    @Size(max = 255, message = "File ID cannot exceed 255 characters")
    private String fileId;

    /**
     * Optional recipient ID for messages sent TO external platforms
     * Used when webhook reports message delivery status (not for inbound customer messages)
     */
    @Size(max = 255, message = "Recipient ID cannot exceed 255 characters")
    private String recipientId;
}
