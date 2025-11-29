package com.chat4all.user.dto;

import com.chat4all.common.constant.Channel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an external identity linked to a user.
 * 
 * <p>This DTO is returned in API responses and contains all information
 * about a platform-specific identity linked to an internal user profile.
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalIdentityDTO {
    
    /**
     * Unique identifier for this identity mapping.
     */
    private UUID id;
    
    /**
     * Platform where this identity exists.
     * One of: WHATSAPP, TELEGRAM, INSTAGRAM, EMAIL.
     */
    private Channel platform;
    
    /**
     * Platform-specific user identifier.
     * Examples:
     * - WhatsApp: +5511999999999
     * - Telegram: @username or numeric user ID
     * - Instagram: username
     * - Email: email@example.com
     */
    private String platformUserId;
    
    /**
     * Whether this identity has been verified.
     * Important for high-security channels.
     */
    private boolean verified;
    
    /**
     * Timestamp when this identity was linked to the user.
     */
    private Instant linkedAt;
}
