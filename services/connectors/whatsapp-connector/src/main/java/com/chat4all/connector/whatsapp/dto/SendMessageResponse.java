package com.chat4all.connector.whatsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Send Message Response DTO for WhatsApp Connector
 * 
 * Response after submitting message to WhatsApp.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResponse {

    /**
     * Internal message ID
     */
    private String messageId;

    /**
     * WhatsApp message ID (mock)
     */
    private String whatsappMessageId;

    /**
     * Delivery status
     */
    private String status;

    /**
     * Timestamp of submission
     */
    private String timestamp;
}
