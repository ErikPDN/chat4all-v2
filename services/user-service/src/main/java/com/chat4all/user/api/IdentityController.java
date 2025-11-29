package com.chat4all.user.api;

import com.chat4all.common.constant.Channel;
import com.chat4all.user.domain.ExternalIdentity;
import com.chat4all.user.domain.User;
import com.chat4all.user.dto.ExternalIdentityDTO;
import com.chat4all.user.dto.LinkIdentityRequest;
import com.chat4all.user.dto.UserDTO;
import com.chat4all.user.service.IdentityMappingService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST API controller for identity mapping operations.
 * 
 * <p>This controller provides endpoints for linking/unlinking external platform identities
 * (WhatsApp, Telegram, Instagram) to internal user profiles, and for resolving platform
 * identities to users (critical for message routing).
 * 
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>POST /api/v1/users/{userId}/identities - Link external identity to user</li>
 *   <li>DELETE /api/v1/users/{userId}/identities/{platform}/{platformUserId} - Unlink identity</li>
 *   <li>GET /api/v1/identities/resolve - Resolve platform identity to user (for testing/debugging)</li>
 * </ul>
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IdentityController {
    
    private final IdentityMappingService identityMappingService;
    
    /**
     * Links an external platform identity to a user profile.
     * 
     * <p>This endpoint creates a mapping between a platform-specific identifier
     * (WhatsApp phone number, Telegram username, etc.) and an internal user profile.
     * After linking, messages from this platform identity will be routed to the user's
     * conversations.
     * 
     * <p><b>Request Body:</b>
     * <pre>{@code
     * {
     *   "platform": "WHATSAPP",
     *   "platformUserId": "+5562999999999",
     *   "verified": false
     * }
     * }</pre>
     * 
     * <p><b>Response (201 Created):</b>
     * <pre>{@code
     * {
     *   "id": "660e8400-e29b-41d4-a716-446655440001",
     *   "platform": "WHATSAPP",
     *   "platformUserId": "+5562999999999",
     *   "verified": false,
     *   "linkedAt": "2025-11-29T12:05:00Z"
     * }
     * }</pre>
     * 
     * <p><b>Error Response (409 Conflict):</b>
     * When the identity is already linked to a different user:
     * <pre>{@code
     * {
     *   "status": 409,
     *   "error": "Identity already linked",
     *   "message": "Identity WHATSAPP:+5562999999999 is already linked to user 550e8400-..."
     * }
     * }</pre>
     * 
     * @param userId the UUID of the user to link the identity to
     * @param request the identity linking request (platform, platformUserId, verified)
     * @return ResponseEntity with created ExternalIdentityDTO and HTTP 201 Created
     * @throws EntityNotFoundException if user not found (returns 404)
     * @throws IllegalArgumentException if identity already linked to different user (returns 409)
     */
    @PostMapping("/users/{userId}/identities")
    public ResponseEntity<ExternalIdentityDTO> linkIdentity(
            @PathVariable UUID userId,
            @Valid @RequestBody LinkIdentityRequest request) {
        
        log.info("API request: Link identity - userId={}, platform={}, platformUserId={}", 
                 userId, request.getPlatform(), request.getPlatformUserId());
        
        try {
            ExternalIdentity identity = identityMappingService.linkIdentity(userId, request);
            
            ExternalIdentityDTO dto = ExternalIdentityDTO.builder()
                .id(identity.getId())
                .platform(identity.getPlatform())
                .platformUserId(identity.getPlatformUserId())
                .verified(identity.isVerified())
                .linkedAt(identity.getLinkedAt())
                .build();
            
            log.info("API response: Identity linked successfully - identityId={}", identity.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
            
        } catch (EntityNotFoundException e) {
            log.warn("API error: User not found - userId={}", userId);
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("API error: Identity already linked - {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Unlinks an external identity from a user profile.
     * 
     * <p>This endpoint removes the mapping between a platform identity and a user.
     * After unlinking, messages from this platform will no longer be routed to the
     * user's conversations.
     * 
     * <p><b>URL Parameters:</b>
     * - userId: UUID of the user
     * - platform: Platform type (WHATSAPP, TELEGRAM, INSTAGRAM, EMAIL)
     * - platformUserId: Platform-specific identifier (URL-encoded)
     * 
     * <p><b>Example:</b>
     * DELETE /api/v1/users/{userId}/identities/WHATSAPP/%2B5562999999999
     * 
     * <p><b>Response (204 No Content):</b>
     * Empty body, identity successfully unlinked.
     * 
     * <p><b>Error Response (404 Not Found):</b>
     * When identity doesn't exist or doesn't belong to the specified user.
     * 
     * @param userId the UUID of the user (for validation)
     * @param platform the platform type
     * @param platformUserId the platform-specific identifier
     * @return ResponseEntity with HTTP 204 No Content
     * @throws EntityNotFoundException if identity not found (returns 404)
     */
    @DeleteMapping("/users/{userId}/identities/{platform}/{platformUserId}")
    public ResponseEntity<Void> unlinkIdentity(
            @PathVariable UUID userId,
            @PathVariable Channel platform,
            @PathVariable String platformUserId) {
        
        log.info("API request: Unlink identity - userId={}, platform={}, platformUserId={}", 
                 userId, platform, platformUserId);
        
        // Find the identity to verify it belongs to this user
        Optional<ExternalIdentity> identityOpt = identityMappingService
            .getIdentity(platform, platformUserId);
        
        if (identityOpt.isEmpty()) {
            log.warn("API error: Identity not found - platform={}, platformUserId={}", 
                     platform, platformUserId);
            throw new EntityNotFoundException(
                String.format("Identity not found: %s:%s", platform, platformUserId));
        }
        
        ExternalIdentity identity = identityOpt.get();
        
        // Verify the identity belongs to the specified user
        if (!identity.getUser().getId().equals(userId)) {
            log.warn("API error: Identity belongs to different user - identityUserId={}, requestUserId={}", 
                     identity.getUser().getId(), userId);
            throw new IllegalArgumentException(
                String.format("Identity %s:%s does not belong to user %s", 
                    platform, platformUserId, userId));
        }
        
        // Unlink the identity
        identityMappingService.unlinkIdentity(identity.getId());
        
        log.info("API response: Identity unlinked successfully - identityId={}", identity.getId());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Resolves a platform identity to an internal user.
     * 
     * <p><b>This endpoint is primarily for testing and debugging.</b>
     * In production, identity resolution is done internally by the message routing system.
     * 
     * <p>This endpoint demonstrates the critical identity resolution functionality that
     * happens on every incoming message: resolve platform-specific identity to internal user.
     * 
     * <p><b>Query Parameters:</b>
     * - platform: Platform type (WHATSAPP, TELEGRAM, INSTAGRAM, EMAIL)
     * - id: Platform-specific identifier
     * 
     * <p><b>Example:</b>
     * GET /api/v1/identities/resolve?platform=WHATSAPP&id=%2B5562999999999
     * 
     * <p><b>Response (200 OK):</b>
     * <pre>{@code
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "displayName": "Erik Silva",
     *   "email": "erik@example.com",
     *   "userType": "CUSTOMER",
     *   "externalIdentities": [...]
     * }
     * }</pre>
     * 
     * <p><b>Response (404 Not Found):</b>
     * When no user is linked to the specified platform identity.
     * 
     * @param platform the platform type
     * @param platformUserId the platform-specific identifier
     * @return ResponseEntity with resolved User information and HTTP 200 OK
     * @throws EntityNotFoundException if no user found for identity (returns 404)
     */
    @GetMapping("/identities/resolve")
    public ResponseEntity<ResolveIdentityResponse> resolveIdentity(
            @RequestParam("platform") Channel platform,
            @RequestParam("id") String platformUserId) {
        
        log.info("API request: Resolve identity - platform={}, platformUserId={}", 
                 platform, platformUserId);
        
        Optional<User> userOpt = identityMappingService.resolveUser(platform, platformUserId);
        
        if (userOpt.isEmpty()) {
            log.warn("API error: No user found for identity - platform={}, platformUserId={}", 
                     platform, platformUserId);
            throw new EntityNotFoundException(
                String.format("No user found for identity: %s:%s", platform, platformUserId));
        }
        
        User user = userOpt.get();
        
        ResolveIdentityResponse response = new ResolveIdentityResponse(
            user.getId(),
            user.getDisplayName(),
            user.getEmail(),
            user.getUserType().name(),
            platform,
            platformUserId
        );
        
        log.info("API response: Identity resolved - userId={}, displayName={}", 
                 user.getId(), user.getDisplayName());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Global exception handler for this controller.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException e) {
        log.warn("Entity not found: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Entity not found",
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        
        // Check if this is a duplicate identity error (should be 409 Conflict)
        if (e.getMessage().contains("already linked")) {
            ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Identity already linked",
                e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        
        // Otherwise it's a bad request
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Invalid request",
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Response DTO for identity resolution endpoint.
     */
    record ResolveIdentityResponse(
        UUID userId,
        String displayName,
        String email,
        String userType,
        Channel platform,
        String platformUserId
    ) {}
    
    /**
     * Error response DTO for API error handling.
     */
    record ErrorResponse(int status, String error, String message) {}
}
