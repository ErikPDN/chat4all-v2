package com.chat4all.user.service;

import com.chat4all.common.constant.Channel;
import com.chat4all.user.domain.ExternalIdentity;
import com.chat4all.user.domain.User;
import com.chat4all.user.dto.LinkIdentityRequest;
import com.chat4all.user.repository.ExternalIdentityRepository;
import com.chat4all.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing identity mapping between internal users and external platforms.
 * 
 * <p>This service is the CORE component for message routing in the Chat4All system.
 * When a message arrives from WhatsApp (+5511999999999), Telegram (@username), or
 * Instagram, this service resolves the platform-specific identity to an internal
 * User, enabling the system to route the message to the correct conversation and
 * apply the appropriate business logic.
 * 
 * <p><b>Critical Use Cases:</b>
 * <ul>
 *   <li><b>Message Routing</b>: resolveUser(WHATSAPP, "+5511999999999") → User</li>
 *   <li><b>Identity Linking</b>: Link WhatsApp phone to existing user profile</li>
 *   <li><b>Multi-platform Support</b>: Same user can have WhatsApp + Telegram + Instagram</li>
 *   <li><b>Identity Unlinking</b>: Remove platform connection from user profile</li>
 * </ul>
 * 
 * <p><b>Performance Considerations:</b>
 * The resolveUser() method is called on EVERY incoming message, so it uses an optimized
 * database query with a compound index on (platform, platform_user_id) for sub-millisecond
 * lookups.
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityMappingService {
    
    private final UserRepository userRepository;
    private final ExternalIdentityRepository externalIdentityRepository;
    
    /**
     * Links an external platform identity to an internal user.
     * 
     * <p>This method creates a bidirectional mapping between a platform-specific
     * identifier (phone number, username, etc.) and an internal User profile.
     * If the identity is already linked to a different user, this method will fail
     * due to the unique constraint on (platform, platform_user_id).
     * 
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * // Link WhatsApp phone number to user
     * LinkIdentityRequest request = LinkIdentityRequest.builder()
     *     .platform(Channel.WHATSAPP)
     *     .platformUserId("+5511999999999")
     *     .verified(false)
     *     .build();
     * ExternalIdentity identity = identityMappingService.linkIdentity(userId, request);
     * }</pre>
     * 
     * @param userId the UUID of the internal user to link
     * @param request the identity linking request (platform, platformUserId, verified)
     * @return the created ExternalIdentity entity
     * @throws jakarta.persistence.EntityNotFoundException if user not found
     * @throws IllegalArgumentException if identity is already linked to a different user
     */
    @Transactional
    public ExternalIdentity linkIdentity(UUID userId, LinkIdentityRequest request) {
        log.info("Linking identity: userId={}, platform={}, platformUserId={}", 
                 userId, request.getPlatform(), request.getPlatformUserId());
        
        // Validate user exists
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "User not found: " + userId));
        
        // Check if this platform identity is already linked
        Optional<ExternalIdentity> existing = externalIdentityRepository
            .findByPlatformAndPlatformUserId(request.getPlatform(), request.getPlatformUserId());
        
        if (existing.isPresent()) {
            ExternalIdentity existingIdentity = existing.get();
            if (existingIdentity.getUser().getId().equals(userId)) {
                log.warn("Identity already linked to this user, returning existing: identityId={}", 
                         existingIdentity.getId());
                return existingIdentity;
            } else {
                throw new IllegalArgumentException(
                    String.format("Identity %s:%s is already linked to user %s",
                        request.getPlatform(), request.getPlatformUserId(), 
                        existingIdentity.getUser().getId()));
            }
        }
        
        // Create new identity mapping
        ExternalIdentity identity = ExternalIdentity.builder()
            .user(user)
            .platform(request.getPlatform())
            .platformUserId(request.getPlatformUserId())
            .verified(request.isVerified())
            .build();
        
        try {
            ExternalIdentity savedIdentity = externalIdentityRepository.save(identity);
            log.info("Linked identity successfully: identityId={}, userId={}, platform={}", 
                     savedIdentity.getId(), userId, request.getPlatform());
            return savedIdentity;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                "Failed to link identity due to constraint violation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Resolves a platform-specific identity to an internal User.
     * 
     * <p><b>THIS IS THE MOST CRITICAL METHOD IN THE ENTIRE SYSTEM.</b>
     * 
     * <p>This method is called on EVERY incoming message from WhatsApp, Telegram,
     * and Instagram to determine which internal user the message belongs to. The
     * resolution must be fast (sub-millisecond) and accurate.
     * 
     * <p><b>Database Performance:</b>
     * Uses an optimized query with compound index on (platform, platform_user_id).
     * Expected execution time: &lt;1ms for indexed lookup.
     * 
     * <p><b>Example Usage in Message Processing:</b>
     * <pre>{@code
     * // Incoming WhatsApp message from +5511999999999
     * Optional<User> user = identityMappingService.resolveUser(
     *     Channel.WHATSAPP, "+5511999999999");
     * 
     * if (user.isPresent()) {
     *     // Route message to user's conversation
     *     conversationService.addMessage(user.get().getId(), message);
     * } else {
     *     // New customer - create user profile automatically
     *     User newUser = userService.createUser(...);
     *     identityMappingService.linkIdentity(newUser.getId(), ...);
     * }
     * }</pre>
     * 
     * @param platform the messaging platform (WHATSAPP, TELEGRAM, INSTAGRAM, EMAIL)
     * @param platformUserId the platform-specific identifier (phone number, username, etc.)
     * @return Optional containing the User if identity is linked, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<User> resolveUser(Channel platform, String platformUserId) {
        log.debug("Resolving user: platform={}, platformUserId={}", platform, platformUserId);
        
        if (platform == null || platformUserId == null || platformUserId.isBlank()) {
            log.warn("Invalid resolve request: platform={}, platformUserId={}", 
                     platform, platformUserId);
            return Optional.empty();
        }
        
        // Use optimized query with compound index
        Optional<ExternalIdentity> identity = externalIdentityRepository
            .findByPlatformAndPlatformUserId(platform, platformUserId);
        
        if (identity.isPresent()) {
            User user = identity.get().getUser();
            log.debug("Resolved user: userId={}, displayName={}", user.getId(), user.getDisplayName());
            return Optional.of(user);
        } else {
            log.debug("No user found for platform={}, platformUserId={}", platform, platformUserId);
            return Optional.empty();
        }
    }
    
    /**
     * Unlinks an external identity from a user.
     * 
     * <p>This method removes the mapping between a platform identity and an internal
     * user. After unlinking, messages from this platform identity will no longer be
     * routed to the user's conversations.
     * 
     * <p><b>Important:</b> This method performs a hard delete. Consider implementing
     * soft delete or audit logging for compliance requirements.
     * 
     * @param identityId the UUID of the ExternalIdentity to unlink
     * @throws jakarta.persistence.EntityNotFoundException if identity not found
     */
    @Transactional
    public void unlinkIdentity(UUID identityId) {
        log.info("Unlinking identity: identityId={}", identityId);
        
        ExternalIdentity identity = externalIdentityRepository.findById(identityId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "Identity not found: " + identityId));
        
        UUID userId = identity.getUser().getId();
        Channel platform = identity.getPlatform();
        String platformUserId = identity.getPlatformUserId();
        
        externalIdentityRepository.delete(identity);
        
        log.info("Unlinked identity successfully: identityId={}, userId={}, platform={}, platformUserId={}", 
                 identityId, userId, platform, platformUserId);
    }
    
    /**
     * Checks if a platform identity is already linked to any user.
     * 
     * <p>This method is useful for validation before attempting to link an identity,
     * or for detecting duplicate accounts.
     * 
     * @param platform the messaging platform
     * @param platformUserId the platform-specific identifier
     * @return true if identity is already linked, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isIdentityLinked(Channel platform, String platformUserId) {
        boolean exists = externalIdentityRepository
            .existsByPlatformAndPlatformUserId(platform, platformUserId);
        
        log.debug("Identity link check: platform={}, platformUserId={}, exists={}", 
                  platform, platformUserId, exists);
        
        return exists;
    }
    
    /**
     * Retrieves an external identity by platform and platform user ID.
     * 
     * @param platform the messaging platform
     * @param platformUserId the platform-specific identifier
     * @return Optional containing the ExternalIdentity if found, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<ExternalIdentity> getIdentity(Channel platform, String platformUserId) {
        return externalIdentityRepository.findByPlatformAndPlatformUserId(platform, platformUserId);
    }
}
