package com.chat4all.user.api;

import com.chat4all.user.domain.User;
import com.chat4all.user.dto.CreateUserRequest;
import com.chat4all.user.dto.UserDTO;
import com.chat4all.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API controller for user management operations.
 * 
 * <p>This controller provides endpoints for creating and retrieving internal user profiles.
 * All endpoints follow RESTful conventions and return appropriate HTTP status codes.
 * 
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>POST /api/v1/users - Create new user</li>
 *   <li>GET /api/v1/users/{id} - Get user by ID with all external identities</li>
 *   <li>GET /api/v1/users - List all users</li>
 * </ul>
 * 
 * @author Chat4All Development Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    /**
     * Creates a new internal user profile.
     * 
     * <p>This endpoint creates a user with the provided information. The user can later
     * have external identities (WhatsApp, Telegram, Instagram) linked to their profile.
     * 
     * <p><b>Request Body:</b>
     * <pre>{@code
     * {
     *   "displayName": "Erik Silva",
     *   "email": "erik@example.com",
     *   "userType": "CUSTOMER",
     *   "metadata": "{\"preferences\": {\"language\": \"pt-BR\"}}"
     * }
     * }</pre>
     * 
     * <p><b>Response (201 Created):</b>
     * <pre>{@code
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "displayName": "Erik Silva",
     *   "email": "erik@example.com",
     *   "userType": "CUSTOMER",
     *   "metadata": "{\"preferences\": {\"language\": \"pt-BR\"}}",
     *   "createdAt": "2025-11-29T12:00:00Z",
     *   "updatedAt": "2025-11-29T12:00:00Z",
     *   "externalIdentities": []
     * }
     * }</pre>
     * 
     * @param request the user creation request (validated)
     * @return ResponseEntity with created UserDTO and HTTP 201 Created
     * @throws IllegalArgumentException if displayName is blank or userType is null (returns 400)
     */
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("API request: Create user - displayName={}, userType={}", 
                 request.getDisplayName(), request.getUserType());
        
        try {
            User user = userService.createUser(request);
            UserDTO userDTO = userService.getUser(user.getId());
            
            log.info("API response: User created successfully - userId={}", user.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(userDTO);
            
        } catch (IllegalArgumentException e) {
            log.warn("API error: Invalid user creation request - {}", e.getMessage());
            throw e; // Will be handled by @ControllerAdvice
        }
    }
    
    /**
     * Retrieves a user by ID with all external identities.
     * 
     * <p>This endpoint returns complete user information including all linked
     * external identities (WhatsApp, Telegram, Instagram accounts).
     * 
     * <p><b>Response (200 OK):</b>
     * <pre>{@code
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "displayName": "Erik Silva",
     *   "email": "erik@example.com",
     *   "userType": "CUSTOMER",
     *   "metadata": "{}",
     *   "createdAt": "2025-11-29T12:00:00Z",
     *   "updatedAt": "2025-11-29T12:00:00Z",
     *   "externalIdentities": [
     *     {
     *       "id": "660e8400-e29b-41d4-a716-446655440001",
     *       "platform": "WHATSAPP",
     *       "platformUserId": "+5562999999999",
     *       "verified": false,
     *       "linkedAt": "2025-11-29T12:05:00Z"
     *     }
     *   ]
     * }
     * }</pre>
     * 
     * @param userId the UUID of the user to retrieve
     * @return ResponseEntity with UserDTO and HTTP 200 OK
     * @throws EntityNotFoundException if user not found (returns 404)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUser(@PathVariable UUID userId) {
        log.debug("API request: Get user - userId={}", userId);
        
        try {
            UserDTO userDTO = userService.getUser(userId);
            log.debug("API response: User found - userId={}, identitiesCount={}", 
                     userId, userDTO.getExternalIdentities().size());
            return ResponseEntity.ok(userDTO);
            
        } catch (EntityNotFoundException e) {
            log.warn("API error: User not found - userId={}", userId);
            throw e; // Will be handled by @ControllerAdvice
        }
    }
    
    /**
     * Lists all users in the system.
     * 
     * <p>This endpoint returns all users with their basic information.
     * For large datasets, pagination should be implemented.
     * 
     * <p><b>Response (200 OK):</b>
     * <pre>{@code
     * [
     *   {
     *     "id": "550e8400-e29b-41d4-a716-446655440000",
     *     "displayName": "Erik Silva",
     *     "email": "erik@example.com",
     *     "userType": "CUSTOMER",
     *     ...
     *   },
     *   ...
     * ]
     * }</pre>
     * 
     * @return ResponseEntity with list of UserDTOs and HTTP 200 OK
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        log.debug("API request: Get all users");
        
        List<UserDTO> users = userService.getAllUsers();
        log.debug("API response: Found {} users", users.size());
        
        return ResponseEntity.ok(users);
    }
    
    /**
     * Global exception handler for this controller.
     * 
     * <p>Handles common exceptions and returns appropriate HTTP status codes:
     * <ul>
     *   <li>EntityNotFoundException → 404 Not Found</li>
     *   <li>IllegalArgumentException → 400 Bad Request</li>
     * </ul>
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException e) {
        log.warn("Entity not found: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "User not found",
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Invalid request",
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Error response DTO for API error handling.
     */
    record ErrorResponse(int status, String error, String message) {}
}
