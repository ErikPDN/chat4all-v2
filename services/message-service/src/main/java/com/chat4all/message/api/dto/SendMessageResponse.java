package com.chat4all.message.api.dto;

import com.chat4all.common.constant.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for send message operation
 * 
 * Returned by POST /messages endpoint (HTTP 202 Accepted).
 * Provides immediate confirmation with message ID for status tracking.
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
     * Unique message identifier
     * Client uses this to track message status via GET /messages/{id}/status
     */
    private String messageId;

    /**
     * Conversation ID
     */
    private String conversationId;

    /**
     * Current message status (typically PENDING at this point)
     */
    private MessageStatus status;

    /**
     * Message acceptance timestamp
     */
    private Instant acceptedAt;

    /**
     * Status tracking endpoint
     * Format: /api/messages/{messageId}/status
     */
    private String statusUrl;
}
