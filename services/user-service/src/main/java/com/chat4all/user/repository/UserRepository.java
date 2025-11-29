package com.chat4all.user.repository;

import com.chat4all.user.domain.User;
import com.chat4all.user.domain.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserRepository - Data access layer for User entity
 * 
 * Provides CRUD operations and custom queries for User management.
 * Extends JpaRepository for standard database operations.
 * 
 * Standard Methods (from JpaRepository):
 * - save(User): Create or update user
 * - findById(UUID): Find user by ID
 * - findAll(): Get all users
 * - delete(User): Delete user
 * - existsById(UUID): Check if user exists
 * 
 * Custom Query Methods:
 * - findByEmail: Find user by email address
 * - findByUserType: Find all users of a specific type (AGENT, CUSTOMER, SYSTEM)
 * - findByDisplayNameContainingIgnoreCase: Search users by display name (case-insensitive)
 * 
 * Usage Examples:
 * 
 * 1. Create new user:
 *    User user = User.builder()
 *        .displayName("John Doe")
 *        .email("john@example.com")
 *        .userType(UserType.CUSTOMER)
 *        .build();
 *    userRepository.save(user);
 * 
 * 2. Find user by email:
 *    Optional<User> user = userRepository.findByEmail("john@example.com");
 * 
 * 3. Get all agents:
 *    List<User> agents = userRepository.findByUserType(UserType.AGENT);
 * 
 * 4. Search users by name:
 *    List<User> users = userRepository.findByDisplayNameContainingIgnoreCase("john");
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email address
     * 
     * @param email Email address to search for
     * @return Optional containing user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Find all users of a specific type
     * 
     * @param userType User type to filter by (AGENT, CUSTOMER, SYSTEM)
     * @return List of users matching the type
     */
    List<User> findByUserType(UserType userType);

    /**
     * Search users by display name (case-insensitive partial match)
     * 
     * @param displayName Display name to search for (partial match supported)
     * @return List of users with matching display names
     */
    List<User> findByDisplayNameContainingIgnoreCase(String displayName);

    /**
     * Check if a user exists with the given email
     * 
     * @param email Email address to check
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find user by ID with external identities eagerly loaded
     * Optimizes query to fetch user and identities in single query
     * 
     * @param id User ID
     * @return Optional containing user with identities if found
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.externalIdentities WHERE u.id = :id")
    Optional<User> findByIdWithIdentities(@Param("id") UUID id);

    /**
     * Count users by type
     * Useful for dashboard analytics
     * 
     * @param userType User type to count
     * @return Number of users with the specified type
     */
    long countByUserType(UserType userType);
}
