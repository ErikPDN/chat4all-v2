package com.chat4all.connector.instagram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResponse {
    private String messageId;
    private String instagramMessageId;
    private String status;
    private String timestamp;
}
