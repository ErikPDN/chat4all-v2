package com.chat4all.user.dto;

import com.chat4all.user.domain.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new user.
 * 
 * <p>This DTO is used when creating a new internal user profile through the REST API.
 * All fields are validated using Jakarta Bean Validation annotations.
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    
    /**
     * Display name for the user.
     * This name will be shown in conversations and UI elements.
     * Must not be blank and has a maximum length of 255 characters.
     */
    @NotBlank(message = "Display name is required")
    private String displayName;
    
    /**
     * Email address for the user (optional).
     * Used for notifications and can be used for identity linking.
     * Must be a valid email format if provided.
     */
    @Email(message = "Email must be valid")
    private String email;
    
    /**
     * User type classification.
     * Determines permissions and behavior in the system.
     * Must be one of: AGENT, CUSTOMER, SYSTEM.
     */
    @NotNull(message = "User type is required")
    private UserType userType;
    
    /**
     * Additional metadata as JSON string (optional).
     * Can store custom attributes, preferences, or settings.
     * Should be valid JSON format.
     */
    private String metadata;
}
