package com.chat4all.user.repository;

import com.chat4all.common.constant.Channel;
import com.chat4all.user.domain.ExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ExternalIdentityRepository - Data access layer for ExternalIdentity entity
 * 
 * Provides CRUD operations and custom queries for managing external platform identities.
 * This is the primary interface for identity lookups during message routing.
 * 
 * Standard Methods (from JpaRepository):
 * - save(ExternalIdentity): Create or update identity
 * - findById(UUID): Find identity by ID
 * - findAll(): Get all identities
 * - delete(ExternalIdentity): Delete identity
 * - existsById(UUID): Check if identity exists
 * 
 * Custom Query Methods:
 * - findByPlatformAndPlatformUserId: Critical method for incoming message routing
 * - findByUserId: Get all identities for a specific user
 * - findByPlatform: Get all identities for a specific platform
 * - findByVerified: Filter identities by verification status
 * 
 * Usage Examples:
 * 
 * 1. Link WhatsApp identity to user:
 *    ExternalIdentity identity = ExternalIdentity.builder()
 *        .user(user)
 *        .platform(Channel.WHATSAPP)
 *        .platformUserId("+5511999999999")
 *        .verified(true)
 *        .build();
 *    externalIdentityRepository.save(identity);
 * 
 * 2. Find identity for incoming WhatsApp message:
 *    String phoneNumber = "+5511999999999";
 *    Optional<ExternalIdentity> identity = 
 *        externalIdentityRepository.findByPlatformAndPlatformUserId(
 *            Channel.WHATSAPP, phoneNumber);
 *    if (identity.isPresent()) {
 *        UUID userId = identity.get().getUser().getId();
 *        // Route message to this user
 *    }
 * 
 * 3. Get all identities for a user:
 *    List<ExternalIdentity> identities = 
 *        externalIdentityRepository.findByUserId(userId);
 *    // Display all platforms linked to user
 * 
 * Performance Considerations:
 * - findByPlatformAndPlatformUserId is optimized with composite index
 * - N+1 query prevention with JOIN FETCH in custom queries
 * - Unique constraint prevents duplicate identities
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Repository
public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    /**
     * Find identity by platform and platform-specific user ID
     * 
     * CRITICAL METHOD for message routing:
     * When a message arrives from WhatsApp/Telegram/Instagram, this method
     * resolves the external platform ID to an internal User.
     * 
     * Performance: Optimized with composite index (platform, platform_user_id)
     * 
     * @param platform Channel/platform (WHATSAPP, TELEGRAM, INSTAGRAM)
     * @param platformUserId Platform-specific user identifier (phone number, user ID, etc.)
     * @return Optional containing identity if found, empty if no match
     * 
     * Examples:
     * - findByPlatformAndPlatformUserId(WHATSAPP, "+5511999999999")
     * - findByPlatformAndPlatformUserId(TELEGRAM, "123456789")
     */
    Optional<ExternalIdentity> findByPlatformAndPlatformUserId(Channel platform, String platformUserId);

    /**
     * Get all external identities for a specific user
     * Eagerly fetches the User to avoid N+1 queries
     * 
     * @param userId User ID to find identities for
     * @return List of all external identities linked to the user
     */
    @Query("SELECT ei FROM ExternalIdentity ei JOIN FETCH ei.user WHERE ei.user.id = :userId")
    List<ExternalIdentity> findByUserId(@Param("userId") UUID userId);

    /**
     * Get all identities for a specific platform
     * Useful for analytics (e.g., "How many WhatsApp users do we have?")
     * 
     * @param platform Channel/platform to filter by
     * @return List of identities for the platform
     */
    List<ExternalIdentity> findByPlatform(Channel platform);

    /**
     * Get all verified or unverified identities
     * Useful for verification workflows
     * 
     * @param verified Verification status to filter by
     * @return List of identities matching the verification status
     */
    List<ExternalIdentity> findByVerified(boolean verified);

    /**
     * Check if an identity exists for a platform and user ID
     * Prevents duplicate identity creation
     * 
     * @param platform Channel/platform
     * @param platformUserId Platform-specific user identifier
     * @return true if identity exists, false otherwise
     */
    boolean existsByPlatformAndPlatformUserId(Channel platform, String platformUserId);

    /**
     * Count identities for a specific user
     * Useful for UI ("You have 3 linked accounts")
     * 
     * @param userId User ID
     * @return Number of external identities linked to the user
     */
    long countByUserId(UUID userId);

    /**
     * Find all verified identities for a user
     * Used for sending messages only via verified channels
     * 
     * @param userId User ID
     * @param verified Verification status
     * @return List of verified identities for the user
     */
    @Query("SELECT ei FROM ExternalIdentity ei WHERE ei.user.id = :userId AND ei.verified = :verified")
    List<ExternalIdentity> findByUserIdAndVerified(@Param("userId") UUID userId, @Param("verified") boolean verified);

    /**
     * Delete all identities for a specific user
     * Used when deleting a user account
     * 
     * @param userId User ID
     */
    void deleteByUserId(UUID userId);

    /**
     * Find identity by ID with user eagerly loaded
     * Optimizes query to avoid lazy loading issues
     * 
     * @param id Identity ID
     * @return Optional containing identity with user if found
     */
    @Query("SELECT ei FROM ExternalIdentity ei JOIN FETCH ei.user WHERE ei.id = :id")
    Optional<ExternalIdentity> findByIdWithUser(@Param("id") UUID id);
}
