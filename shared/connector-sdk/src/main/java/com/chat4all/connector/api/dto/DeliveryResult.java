package com.chat4all.connector.api.dto;

import com.chat4all.common.constant.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Delivery result from external platform
 * Returned by MessageConnector.sendMessage()
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResult {
    
    private String platformMessageId;
    
    private MessageStatus status;
    
    private Instant timestamp;
    
    private String errorMessage;
    
    private Integer retryAfterSeconds;
    
    public boolean isSuccess() {
        return status == MessageStatus.SENT || status == MessageStatus.DELIVERED;
    }
    
    public boolean shouldRetry() {
        return status == MessageStatus.FAILED && retryAfterSeconds != null;
    }
}
