package com.chat4all.router.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a user from the User Service.
 * Contains the user's internal ID and all linked external platform identities.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    
    /**
     * Internal user UUID
     */
    private String id;
    
    /**
     * User's display name
     */
    private String displayName;
    
    /**
     * List of external platform identities linked to this user
     */
    private List<ExternalIdentityDTO> externalIdentities;
}
