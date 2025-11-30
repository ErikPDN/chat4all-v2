package com.chat4all.user.dto;

import com.chat4all.user.domain.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a user profile with all associated information.
 * 
 * <p>This DTO is returned in API responses and includes the user's basic
 * information plus all linked external identities. This provides a complete
 * view of a user's profile across all messaging platforms.
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    
    /**
     * Unique identifier for this user.
     */
    private UUID id;
    
    /**
     * Display name shown in conversations.
     */
    private String displayName;
    
    /**
     * Email address (may be null).
     */
    private String email;
    
    /**
     * User classification: AGENT, CUSTOMER, or SYSTEM.
     */
    private UserType userType;
    
    /**
     * Additional metadata as JSON string.
     */
    private String metadata;
    
    /**
     * Timestamp when user profile was created.
     */
    private Instant createdAt;
    
    /**
     * Timestamp of last profile update.
     */
    private Instant updatedAt;
    
    /**
     * List of all external identities linked to this user.
     * Includes WhatsApp, Telegram, Instagram, Email accounts.
     */
    private List<ExternalIdentityDTO> externalIdentities;
}
