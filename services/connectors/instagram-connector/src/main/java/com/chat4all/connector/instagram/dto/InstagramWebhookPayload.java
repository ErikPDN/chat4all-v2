package com.chat4all.connector.instagram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Instagram Webhook Payload
 * 
 * Represents the webhook payload structure sent by Facebook/Meta
 * for Instagram Messaging API events.
 * 
 * Structure follows Facebook's Graph API webhook format:
 * {
 *   "object": "instagram",
 *   "entry": [...]
 * }
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstagramWebhookPayload {
    
    /**
     * Object type (should be "instagram")
     */
    private String object;
    
    /**
     * Array of entry objects containing messaging events
     */
    private List<Entry> entry;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        
        /**
         * Page or Instagram Business Account ID
         */
        private String id;
        
        /**
         * Timestamp when event occurred (Unix timestamp)
         */
        private Long time;
        
        /**
         * Array of messaging events
         */
        private List<Messaging> messaging;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Messaging {
        
        /**
         * Sender information
         */
        private User sender;
        
        /**
         * Recipient information (page/business account)
         */
        private User recipient;
        
        /**
         * Timestamp when message was sent
         */
        private Long timestamp;
        
        /**
         * Message object (if this is a message event)
         */
        private Message message;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        
        /**
         * User ID (IGSID for sender, Page ID for recipient)
         */
        private String id;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        
        /**
         * Message ID
         */
        private String mid;
        
        /**
         * Message text
         */
        private String text;
    }
}
