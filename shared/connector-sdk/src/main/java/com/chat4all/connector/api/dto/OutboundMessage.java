package com.chat4all.connector.api.dto;

import com.chat4all.common.constant.Channel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Outbound message DTO for connector interface
 * Internal format to be transformed to platform-specific format by connector
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundMessage {
    
    @NotNull
    private String messageId;
    
    @NotNull
    private String conversationId;
    
    @NotNull
    private String recipientPlatformId;
    
    private String content;
    
    private String contentType;
    
    private String fileUrl;
    
    @NotNull
    private Channel channel;
    
    private Map<String, Object> metadata;
}
