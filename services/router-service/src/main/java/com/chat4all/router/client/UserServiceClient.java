package com.chat4all.router.client;

import com.chat4all.router.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Reactive client for communicating with the User Service.
 * Resolves internal user IDs to external platform identities.
 * 
 * <p>Uses WebClient for non-blocking, reactive HTTP calls with:
 * - Circuit breaker pattern via retry logic
 * - Timeout configuration
 * - Error handling for 404 Not Found and service unavailability
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class UserServiceClient {
    
    private final WebClient webClient;
    
    /**
     * Constructor with WebClient configured for User Service base URL.
     * 
     * @param userServiceUrl Base URL for User Service (e.g., http://localhost:8083)
     */
    public UserServiceClient(@Value("${app.services.user-service.url:http://localhost:8083}") String userServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(userServiceUrl)
                .build();
        
        log.info("UserServiceClient initialized with base URL: {}", userServiceUrl);
    }
    
    /**
     * Retrieves a user by their internal UUID from the User Service.
     * 
     * <p>Returns a {@link UserDTO} containing:
     * - User's internal ID
     * - Display name
     * - List of external platform identities (WhatsApp, Telegram, Instagram)
     * 
     * <p><b>Example Usage:</b>
     * <pre>{@code
     * userServiceClient.getUser("550e8400-e29b-41d4-a716-446655440000")
     *     .subscribe(
     *         user -> log.info("User has {} identities", user.getExternalIdentities().size()),
     *         error -> log.error("Failed to get user", error)
     *     );
     * }</pre>
     * 
     * <p><b>Error Handling:</b>
     * - 404 Not Found: Returns empty Mono
     * - 5xx Server Error: Retries up to 2 times with exponential backoff
     * - Network timeout: 10 second timeout
     * 
     * @param userId Internal user UUID
     * @return Mono emitting UserDTO if found, or empty Mono if user not found
     */
    public Mono<UserDTO> getUser(String userId) {
        log.debug("Fetching user from User Service - userId={}", userId);
        
        return webClient
                .get()
                .uri("/api/v1/users/{userId}", userId)
                .retrieve()
                .bodyToMono(UserDTO.class)
                .timeout(Duration.ofSeconds(10))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound))
                        .doBeforeRetry(retrySignal -> 
                            log.warn("Retrying User Service call - attempt {}, error: {}", 
                                    retrySignal.totalRetries() + 1, 
                                    retrySignal.failure().getMessage())
                        )
                )
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.debug("User fetched successfully - userId={}, identities={}", 
                                userId, user.getExternalIdentities() != null ? user.getExternalIdentities().size() : 0);
                    }
                })
                .doOnError(WebClientResponseException.NotFound.class, error -> 
                    log.warn("User not found in User Service - userId={}", userId)
                )
                .doOnError(error -> {
                    if (!(error instanceof WebClientResponseException.NotFound)) {
                        log.error("Error calling User Service - userId={}, error={}", userId, error.getMessage());
                    }
                })
                .onErrorResume(WebClientResponseException.NotFound.class, error -> Mono.empty());
    }
}
