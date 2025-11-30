package com.chat4all.connector.instagram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound Message DTO
 * 
 * Represents an inbound message received from Instagram via webhook.
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
     * Instagram sender ID (IGSID or Instagram User ID)
     */
    private String senderId;
    
    /**
     * Instagram recipient ID (Page ID or Instagram Business Account ID)
     */
    private String recipientId;
    
    /**
     * Message ID from Instagram
     */
    private String instagramMessageId;
    
    /**
     * Message text content
     */
    private String text;
    
    /**
     * Timestamp when message was sent (Unix timestamp in milliseconds)
     */
    private Long timestamp;
    
    /**
     * Channel type (always "INSTAGRAM" for this connector)
     */
    @Builder.Default
    private String channel = "INSTAGRAM";
}
