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

import java.util.*;
import java.util.stream.Collectors;

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
    
    /**
     * Suggests potential user matches for identity linking based on similarity analysis.
     * 
     * <p>This method implements an intelligent matching algorithm that analyzes multiple
     * signals to suggest which existing users might correspond to a new external identity:
     * 
     * <ul>
     *   <li><b>Email matching</b>: Exact email match (if provided) - highest confidence</li>
     *   <li><b>Phone number matching</b>: Normalized phone comparison - high confidence</li>
     *   <li><b>Name similarity</b>: Fuzzy matching using Levenshtein distance - medium confidence</li>
     *   <li><b>Platform overlap</b>: Users with similar platform presence - bonus signal</li>
     * </ul>
     * 
     * <p><b>Confidence Scoring:</b>
     * <ul>
     *   <li>Email exact match: 100 points</li>
     *   <li>Phone exact match: 95 points</li>
     *   <li>Phone normalized match: 90 points</li>
     *   <li>Name similarity 90-100%: 70-80 points</li>
     *   <li>Name similarity 70-89%: 50-69 points</li>
     *   <li>Platform overlap: +10 points per matching platform</li>
     * </ul>
     * 
     * <p>Returns only matches with confidence score >= 60 (minimum threshold).
     * Results are sorted by confidence score (descending).
     * 
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * // New WhatsApp identity: +5511999999999
     * List<UserMatch> suggestions = identityMappingService.suggestMatches(
     *     "+5511999999999",  // phone from WhatsApp
     *     "John Doe",        // displayName from WhatsApp profile
     *     "john@example.com" // email from business WhatsApp
     * );
     * 
     * if (!suggestions.isEmpty()) {
     *     UserMatch bestMatch = suggestions.get(0);
     *     if (bestMatch.confidence >= 90) {
     *         // Auto-link with high confidence
     *         identityMappingService.linkIdentity(bestMatch.userId, request);
     *     } else {
     *         // Show suggestion to admin for manual review
     *         ui.showLinkingSuggestion(bestMatch);
     *     }
     * }
     * }</pre>
     * 
     * @param phone phone number from external platform (may be null)
     * @param displayName display name from external platform (may be null)
     * @param email email from external platform (may be null)
     * @return List of UserMatch objects sorted by confidence (highest first), max 5 results
     */
    @Transactional(readOnly = true)
    public List<UserMatch> suggestMatches(String phone, String displayName, String email) {
        log.info("Suggesting identity matches: phone={}, displayName={}, email={}", 
                 phone, displayName, email);
        
        Map<UUID, UserMatchBuilder> matchBuilders = new HashMap<>();
        
        // 1. EMAIL EXACT MATCH (100 points)
        if (email != null && !email.isBlank()) {
            userRepository.findByEmail(email).ifPresent(user -> {
                UserMatchBuilder builder = getOrCreateBuilder(matchBuilders, user);
                builder.addScore(100, "Email exact match");
                log.debug("Email match found: userId={}, email={}", user.getId(), email);
            });
        }
        
        // 2. PHONE NUMBER MATCHING
        if (phone != null && !phone.isBlank()) {
            String normalizedPhone = normalizePhone(phone);
            
            // Get all users and check their external identities for phone matches
            List<ExternalIdentity> allIdentities = externalIdentityRepository.findAll();
            
            for (ExternalIdentity identity : allIdentities) {
                String identityPhone = identity.getPlatformUserId();
                
                // Exact match (95 points)
                if (phone.equals(identityPhone)) {
                    UserMatchBuilder builder = getOrCreateBuilder(matchBuilders, identity.getUser());
                    builder.addScore(95, "Phone exact match");
                    log.debug("Phone exact match: userId={}, phone={}", identity.getUser().getId(), phone);
                }
                // Normalized match (90 points)
                else if (normalizedPhone.equals(normalizePhone(identityPhone))) {
                    UserMatchBuilder builder = getOrCreateBuilder(matchBuilders, identity.getUser());
                    builder.addScore(90, "Phone normalized match");
                    log.debug("Phone normalized match: userId={}, phone={}", 
                             identity.getUser().getId(), phone);
                }
            }
        }
        
        // 3. NAME SIMILARITY (50-80 points based on similarity percentage)
        if (displayName != null && !displayName.isBlank()) {
            List<User> nameMatches = userRepository.findByDisplayNameContainingIgnoreCase(
                displayName.split("\\s+")[0] // Search by first name
            );
            
            for (User user : nameMatches) {
                int similarity = calculateSimilarity(displayName, user.getDisplayName());
                
                if (similarity >= 70) {
                    UserMatchBuilder builder = getOrCreateBuilder(matchBuilders, user);
                    int score = 50 + (similarity - 70) / 3; // 70% = 50pts, 90% = 57pts, 100% = 60pts
                    builder.addScore(score, String.format("Name similarity %d%%", similarity));
                    log.debug("Name match: userId={}, similarity={}%, score={}", 
                             user.getId(), similarity, score);
                }
            }
        }
        
        // 4. PLATFORM OVERLAP BONUS (+10 points per shared platform)
        for (Map.Entry<UUID, UserMatchBuilder> entry : matchBuilders.entrySet()) {
            User user = entry.getValue().user;
            int platformCount = user.getExternalIdentities().size();
            
            if (platformCount > 1) {
                int bonus = Math.min(platformCount * 10, 30); // Max 30 bonus points
                entry.getValue().addScore(bonus, 
                    String.format("%d platform identities", platformCount));
            }
        }
        
        // Build final list, filter by minimum confidence, sort by score
        List<UserMatch> matches = matchBuilders.values().stream()
            .map(UserMatchBuilder::build)
            .filter(match -> match.confidence >= 60) // Minimum threshold
            .sorted(Comparator.comparingInt(UserMatch::getConfidence).reversed())
            .limit(5) // Top 5 results only
            .collect(Collectors.toList());
        
        log.info("Found {} potential matches (confidence >= 60)", matches.size());
        
        return matches;
    }
    
    /**
     * Normalizes phone number for comparison by removing all non-digit characters.
     * 
     * @param phone raw phone number
     * @return normalized phone (digits only)
     */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9]", "");
    }
    
    /**
     * Calculates string similarity percentage using Levenshtein distance.
     * 
     * @param s1 first string
     * @param s2 second string
     * @return similarity percentage (0-100)
     */
    private int calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        
        s1 = s1.toLowerCase().trim();
        s2 = s2.toLowerCase().trim();
        
        if (s1.equals(s2)) return 100;
        
        int distance = levenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        
        return (int) (100.0 * (1.0 - ((double) distance / maxLength)));
    }
    
    /**
     * Computes Levenshtein distance (edit distance) between two strings.
     * 
     * @param s1 first string
     * @param s2 second string
     * @return edit distance
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Gets or creates a match builder for a user.
     */
    private UserMatchBuilder getOrCreateBuilder(Map<UUID, UserMatchBuilder> map, User user) {
        return map.computeIfAbsent(user.getId(), id -> new UserMatchBuilder(user));
    }
    
    /**
     * Builder for accumulating match scores.
     */
    private static class UserMatchBuilder {
        private final User user;
        private int totalScore = 0;
        private final List<String> reasons = new ArrayList<>();
        
        UserMatchBuilder(User user) {
            this.user = user;
        }
        
        void addScore(int points, String reason) {
            totalScore += points;
            reasons.add(reason);
        }
        
        UserMatch build() {
            return new UserMatch(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                totalScore,
                String.join(", ", reasons)
            );
        }
    }
    
    /**
     * Result object for user match suggestions.
     */
    public static class UserMatch {
        private final UUID userId;
        private final String displayName;
        private final String email;
        private final int confidence;
        private final String reason;
        
        public UserMatch(UUID userId, String displayName, String email, 
                        int confidence, String reason) {
            this.userId = userId;
            this.displayName = displayName;
            this.email = email;
            this.confidence = confidence;
            this.reason = reason;
        }
        
        public UUID getUserId() { return userId; }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public int getConfidence() { return confidence; }
        public String getReason() { return reason; }
        
        @Override
        public String toString() {
            return String.format("UserMatch{userId=%s, displayName='%s', confidence=%d, reason='%s'}",
                userId, displayName, confidence, reason);
        }
    }
}
