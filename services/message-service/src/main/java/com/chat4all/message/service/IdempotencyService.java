package com.chat4all.message.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive Idempotency Service using Redis
 * 
 * Ensures exactly-once message processing by tracking message IDs in Redis.
 * Prevents duplicate message acceptance even with retries or network issues.
 * 
 * Implementation Strategy (FR-006):
 * 1. Check Redis for message_id existence (fast in-memory check)
 * 2. If not found, atomically set with TTL (7 days retention)
 * 3. MongoDB unique index provides backup deduplication
 * 
 * Redis Key Format: "idempotency:message:{message_id}"
 * TTL: 7 days (604800 seconds) - aligns with Kafka retention
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class IdempotencyService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * Constructor with explicit @Qualifier to resolve bean ambiguity.
     * Spring Boot auto-configuration creates both reactiveRedisTemplate and reactiveStringRedisTemplate beans.
     * We use reactiveStringRedisTemplate for String key-value operations.
     *
     * @param reactiveRedisTemplate The reactive Redis template bean (String, String)
     */
    public IdempotencyService(@Qualifier("reactiveStringRedisTemplate") ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    /**
     * Redis key prefix for idempotency checks
     */
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:message:";

    /**
     * TTL for idempotency keys (7 days = Kafka retention period)
     */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    /**
     * Checks if a message has already been processed (idempotency check) - reactive.
     * 
     * This method is called BEFORE accepting a message to prevent duplicates.
     * Uses Redis SET NX (set if not exists) for atomic check-and-set operation.
     * 
     * Flow:
     * 1. Try to set key in Redis with NX flag
     * 2. If set succeeds → message is new (return false)
     * 3. If set fails → message already processed (return true)
     * 
     * @param messageId Unique message identifier (UUIDv4)
     * @return Mono<Boolean> true if message was already processed, false if this is the first time
     */
    public Mono<Boolean> isDuplicate(String messageId) {
        String key = buildIdempotencyKey(messageId);

        return reactiveRedisTemplate.opsForValue()
            .setIfAbsent(key, "processed", IDEMPOTENCY_TTL)
            .map(wasSet -> {
                if (Boolean.TRUE.equals(wasSet)) {
                    // Key was successfully set → first time seeing this message
                    log.debug("Message {} is new (idempotency key set in Redis)", messageId);
                    return false; // NOT a duplicate
                } else {
                    // Key already exists → duplicate message
                    log.warn("Duplicate message detected: {} (idempotency key exists in Redis)", messageId);
                    return true; // IS a duplicate
                }
            })
            .onErrorResume(e -> {
                // Redis failure should not block message processing
                // Fall through to MongoDB duplicate check (unique index)
                log.error("Redis idempotency check failed for message {}: {}. Falling back to MongoDB check.",
                    messageId, e.getMessage());
                return Mono.just(false); // Assume not duplicate, let MongoDB enforce uniqueness
            });
    }

    /**
     * Marks a message as processed (for manual idempotency marking) - reactive.
     * 
     * This method is rarely needed as isDuplicate() already sets the key.
     * Used for edge cases where message needs to be marked without prior check.
     * 
     * @param messageId Unique message identifier
     * @return Mono<Void> Completes when message is marked as processed
     */
    public Mono<Void> markAsProcessed(String messageId) {
        String key = buildIdempotencyKey(messageId);

        return reactiveRedisTemplate.opsForValue()
            .set(key, "processed", IDEMPOTENCY_TTL)
            .doOnSuccess(success -> log.debug("Message {} marked as processed in Redis", messageId))
            .doOnError(e -> log.error("Failed to mark message {} as processed in Redis: {}",
                messageId, e.getMessage()))
            .then()
            .onErrorResume(e -> Mono.empty()); // Non-critical failure, continue processing
    }

    /**
     * Checks if a message exists in Redis idempotency cache - reactive.
     * 
     * Read-only check without modifying Redis state.
     * Used for debugging or audit purposes.
     * 
     * @param messageId Unique message identifier
     * @return Mono<Boolean> true if message exists in cache, false otherwise
     */
    public Mono<Boolean> exists(String messageId) {
        String key = buildIdempotencyKey(messageId);

        return reactiveRedisTemplate.hasKey(key)
            .onErrorResume(e -> {
                log.error("Failed to check idempotency existence for message {}: {}",
                    messageId, e.getMessage());
                return Mono.just(false);
            });
    }

    /**
     * Removes a message from idempotency cache (for testing/manual intervention) - reactive.
     * 
     * WARNING: Use with extreme caution in production!
     * Removing idempotency keys can allow duplicate processing.
     * 
     * @param messageId Unique message identifier
     * @return Mono<Boolean> true if key was deleted, false if not found
     */
    public Mono<Boolean> remove(String messageId) {
        String key = buildIdempotencyKey(messageId);

        return reactiveRedisTemplate.delete(key)
            .map(count -> count > 0)
            .doOnSuccess(deleted -> {
                if (deleted) {
                    log.warn("Idempotency key removed for message {}", messageId);
                } else {
                    log.debug("Idempotency key not found for message {}", messageId);
                }
            })
            .doOnError(e -> log.error("Failed to remove idempotency key for message {}: {}",
                messageId, e.getMessage()))
            .onErrorReturn(false);
    }

    /**
     * Extends the TTL of an idempotency key (for long-running operations) - reactive.
     * 
     * @param messageId Unique message identifier
     * @param additionalTime Additional time to add to TTL
     * @return Mono<Boolean> true if TTL was extended, false otherwise
     */
    public Mono<Boolean> extendTTL(String messageId, Duration additionalTime) {
        String key = buildIdempotencyKey(messageId);

        return reactiveRedisTemplate.expire(key, additionalTime)
            .doOnSuccess(success -> {
                if (success) {
                    log.debug("Extended TTL for message {} by {} seconds", messageId, additionalTime.getSeconds());
                }
            })
            .doOnError(e -> log.error("Failed to extend TTL for message {}: {}", messageId, e.getMessage()))
            .onErrorReturn(false);
    }

    /**
     * Builds the Redis key for idempotency tracking.
     * 
     * Format: "idempotency:message:{message_id}"
     * 
     * @param messageId Unique message identifier
     * @return Redis key string
     */
    private String buildIdempotencyKey(String messageId) {
        return IDEMPOTENCY_KEY_PREFIX + messageId;
    }
}
