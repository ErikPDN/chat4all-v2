package com.chat4all.router.dto;

import com.chat4all.common.constant.Channel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an external platform identity linked to a user.
 * Used to map responses from the User Service API.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalIdentityDTO {
    
    /**
     * Platform where this identity exists (WHATSAPP, TELEGRAM, INSTAGRAM)
     */
    private Channel platform;
    
    /**
     * Platform-specific user identifier (e.g., phone number for WhatsApp, username for Telegram)
     */
    private String platformUserId;
    
    /**
     * Whether this identity has been verified
     */
    private boolean verified;
}
