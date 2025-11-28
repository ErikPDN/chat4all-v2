package com.chat4all.connector.telegram.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    @NotBlank(message = "Message ID is required")
    private String messageId;
    
    @NotBlank(message = "Chat ID is required")
    private String chatId;
    
    @NotBlank(message = "Content is required")
    private String content;
    
    private String conversationId;
    private String senderId;
}
