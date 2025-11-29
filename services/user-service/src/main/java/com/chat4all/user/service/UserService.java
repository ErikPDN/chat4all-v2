package com.chat4all.user.service;

import com.chat4all.user.domain.User;
import com.chat4all.user.dto.CreateUserRequest;
import com.chat4all.user.dto.ExternalIdentityDTO;
import com.chat4all.user.dto.UserDTO;
import com.chat4all.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing user profiles.
 * 
 * <p>This service provides business logic for creating, retrieving, and updating
 * internal user profiles. It handles the unified user identity across all messaging
 * platforms, allowing agents and customers to have a single profile that can be
 * linked to multiple external identities (WhatsApp, Telegram, Instagram, etc.).
 * 
 * <p><b>Key Responsibilities:</b>
 * <ul>
 *   <li>User creation with validation and defaults</li>
 *   <li>User retrieval with eager loading of external identities</li>
 *   <li>User profile updates</li>
 *   <li>DTO mapping for API responses</li>
 * </ul>
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    
    /**
     * Creates a new user profile.
     * 
     * <p>This method creates an internal user that can later be linked to external
     * platform identities. The user is created with the provided information and
     * default values for optional fields.
     * 
     * @param request the user creation request with displayName, email, userType
     * @return the created User entity
     * @throws IllegalArgumentException if displayName is blank or userType is null
     */
    @Transactional
    public User createUser(CreateUserRequest request) {
        log.info("Creating new user: displayName={}, userType={}", 
                 request.getDisplayName(), request.getUserType());
        
        // Validate required fields
        if (request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (request.getUserType() == null) {
            throw new IllegalArgumentException("User type is required");
        }
        
        // Check email uniqueness if provided
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            userRepository.findByEmail(request.getEmail()).ifPresent(existingUser -> {
                throw new IllegalArgumentException(
                    "User with email " + request.getEmail() + " already exists");
            });
        }
        
        // Create user entity
        User user = User.builder()
            .displayName(request.getDisplayName())
            .email(request.getEmail())
            .userType(request.getUserType())
            .metadata(request.getMetadata())
            .build();
        
        User savedUser = userRepository.save(user);
        log.info("Created user successfully: userId={}", savedUser.getId());
        
        return savedUser;
    }
    
    /**
     * Retrieves a user by ID with all external identities.
     * 
     * <p>This method uses JOIN FETCH to eagerly load all external identities
     * associated with the user, avoiding N+1 query problems. The result is
     * mapped to a UserDTO for API consumption.
     * 
     * @param userId the UUID of the user to retrieve
     * @return UserDTO with all user information and linked identities
     * @throws IllegalArgumentException if userId is null
     * @throws jakarta.persistence.EntityNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserDTO getUser(UUID userId) {
        log.debug("Retrieving user: userId={}", userId);
        
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        // Use optimized query with JOIN FETCH
        User user = userRepository.findByIdWithIdentities(userId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "User not found: " + userId));
        
        return mapToDTO(user);
    }
    
    /**
     * Retrieves all users in the system.
     * 
     * <p>This method is typically used for admin interfaces or reporting.
     * For large datasets, consider implementing pagination.
     * 
     * @return list of all users as DTOs
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        log.debug("Retrieving all users");
        
        List<User> users = userRepository.findAll();
        log.debug("Found {} users", users.size());
        
        return users.stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Updates a user's display name.
     * 
     * @param userId the UUID of the user to update
     * @param newDisplayName the new display name
     * @return the updated User entity
     * @throws jakarta.persistence.EntityNotFoundException if user not found
     * @throws IllegalArgumentException if newDisplayName is blank
     */
    @Transactional
    public User updateDisplayName(UUID userId, String newDisplayName) {
        log.info("Updating display name for user: userId={}, newDisplayName={}", 
                 userId, newDisplayName);
        
        if (newDisplayName == null || newDisplayName.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be blank");
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "User not found: " + userId));
        
        user.setDisplayName(newDisplayName);
        User updatedUser = userRepository.save(user);
        
        log.info("Updated display name successfully: userId={}", userId);
        return updatedUser;
    }
    
    /**
     * Updates a user's email address.
     * 
     * @param userId the UUID of the user to update
     * @param newEmail the new email address (must be unique)
     * @return the updated User entity
     * @throws jakarta.persistence.EntityNotFoundException if user not found
     * @throws IllegalArgumentException if email already in use
     */
    @Transactional
    public User updateEmail(UUID userId, String newEmail) {
        log.info("Updating email for user: userId={}, newEmail={}", userId, newEmail);
        
        // Check email uniqueness
        if (newEmail != null && !newEmail.isBlank()) {
            userRepository.findByEmail(newEmail).ifPresent(existingUser -> {
                if (!existingUser.getId().equals(userId)) {
                    throw new IllegalArgumentException("Email already in use");
                }
            });
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                "User not found: " + userId));
        
        user.setEmail(newEmail);
        User updatedUser = userRepository.save(user);
        
        log.info("Updated email successfully: userId={}", userId);
        return updatedUser;
    }
    
    /**
     * Maps a User entity to a UserDTO for API responses.
     * 
     * <p>This method converts the JPA entity to a data transfer object,
     * including all external identities.
     * 
     * @param user the User entity to map
     * @return UserDTO with all user information
     */
    private UserDTO mapToDTO(User user) {
        List<ExternalIdentityDTO> identities = user.getExternalIdentities().stream()
            .map(identity -> ExternalIdentityDTO.builder()
                .id(identity.getId())
                .platform(identity.getPlatform())
                .platformUserId(identity.getPlatformUserId())
                .verified(identity.isVerified())
                .linkedAt(identity.getLinkedAt())
                .build())
            .collect(Collectors.toList());
        
        return UserDTO.builder()
            .id(user.getId())
            .displayName(user.getDisplayName())
            .email(user.getEmail())
            .userType(user.getUserType())
            .metadata(user.getMetadata())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .externalIdentities(identities)
            .build();
    }
}
