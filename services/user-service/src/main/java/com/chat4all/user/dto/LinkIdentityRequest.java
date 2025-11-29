package com.chat4all.user.dto;

import com.chat4all.common.constant.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for linking an external identity to a user.
 * 
 * <p>This DTO is used when associating a platform-specific identity
 * (WhatsApp phone number, Telegram username, etc.) with an internal user profile.
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkIdentityRequest {
    
    /**
     * Platform where this identity exists.
     * Must be one of: WHATSAPP, TELEGRAM, INSTAGRAM, EMAIL.
     */
    @NotNull(message = "Platform is required")
    private Channel platform;
    
    /**
     * Platform-specific user identifier.
     * Examples:
     * - WhatsApp: +5511999999999 (E.164 format recommended)
     * - Telegram: @username or numeric user ID
     * - Instagram: username
     * - Email: email@example.com
     */
    @NotBlank(message = "Platform user ID is required")
    private String platformUserId;
    
    /**
     * Whether this identity has been verified (optional, defaults to false).
     * Should be set to true only after completing verification workflow.
     */
    private boolean verified;
}
