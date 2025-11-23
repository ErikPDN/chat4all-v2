package com.chat4all.connector.api.dto;

import com.chat4all.common.constant.Channel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Inbound message DTO for connector interface
 * Platform-specific format transformed to internal format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundMessage {
    
    private String platformMessageId;
    
    @NotNull
    private String senderPlatformId;
    
    private String content;
    
    private String contentType;
    
    private String fileUrl;
    
    @NotNull
    private Channel channel;
    
    @NotNull
    private Instant timestamp;
    
    private Map<String, Object> metadata;
}
