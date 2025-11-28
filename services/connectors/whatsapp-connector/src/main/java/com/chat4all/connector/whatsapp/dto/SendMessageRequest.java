package com.chat4all.connector.whatsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Send Message Request DTO for WhatsApp Connector
 * 
 * Represents a message to be sent via WhatsApp Business API.
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
     * Internal message ID from Chat4All platform
     */
    @NotBlank(message = "Message ID is required")
    private String messageId;

    /**
     * WhatsApp recipient phone number (E.164 format)
     * Example: +5511999999999
     */
    @NotBlank(message = "Recipient is required")
    private String to;

    /**
     * Message content (text)
     */
    @NotBlank(message = "Content is required")
    private String content;

    /**
     * Optional conversation ID
     */
    private String conversationId;

    /**
     * Optional sender ID
     */
    private String senderId;
}
