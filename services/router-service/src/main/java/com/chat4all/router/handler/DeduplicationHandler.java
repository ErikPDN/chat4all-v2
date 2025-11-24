package com.chat4all.router.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Deduplication Handler (T045)
 * 
 * Prevents duplicate message delivery by checking message_id against Redis and MongoDB.
 * 
 * Strategy (FR-006):
 * 1. Check Redis for message_id (fast in-memory check)
 * 2. If not found in Redis, check MongoDB (backup persistence layer)
 * 3. If found in either, message is a duplicate - skip processing
 * 4. If not found, mark as processed and continue
 * 
 * Redis Key Format: "router:processed:{message_id}"
 * TTL: 7 days (aligns with Kafka retention and message-service idempotency)
 * 
 * Why Two Layers?
 * - Redis: Fast in-memory check for recent messages (hot path)
 * - MongoDB: Persistent backup for messages beyond Redis TTL or after Redis restarts
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class DeduplicationHandler {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Constructor with explicit bean qualifier to resolve ambiguity.
     * 
     * Spring Boot auto-configuration creates multiple RedisTemplate beans.
     * We need the String-based template for deduplication keys.
     * 
     * @param redisTemplate The String-based Redis template bean
     */
    public DeduplicationHandler(@Qualifier("stringRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Redis key prefix for router deduplication
     */
    private static final String ROUTER_PROCESSED_PREFIX = "router:processed:";

    /**
     * TTL for deduplication keys (7 days = Kafka retention)
     */
    @Value("${app.deduplication.ttl-days:7}")
    private int ttlDays;

    /**
     * Checks if a message has already been processed by the router.
     * 
     * This prevents duplicate message delivery to connectors, which could result in
     * sending the same message twice to WhatsApp/Telegram/Instagram.
     * 
     * @param messageId Unique message identifier
     * @return true if message was already processed, false if this is the first time
     */
    public boolean isDuplicate(String messageId) {
        String key = buildKey(messageId);

        try {
            // Check Redis first (fast path)
            Boolean exists = redisTemplate.hasKey(key);
            
            if (Boolean.TRUE.equals(exists)) {
                log.warn("Duplicate message detected in Redis: {}", messageId);
                return true;
            }

            // TODO: In production, also check MongoDB here as backup
            // For MVP, Redis is sufficient with 7-day TTL
            
            log.debug("Message is new (not found in deduplication cache): {}", messageId);
            return false;

        } catch (Exception e) {
            log.error("Error checking deduplication for message {}: {}. Assuming not duplicate to avoid blocking.", 
                    messageId, e.getMessage());
            // Fail open - if Redis is down, allow processing
            // Better to risk duplicate than block all messages
            return false;
        }
    }

    /**
     * Marks a message as processed in the deduplication cache.
     * 
     * Called after successful routing to prevent future duplicate deliveries.
     * 
     * @param messageId Unique message identifier
     */
    public void markAsProcessed(String messageId) {
        String key = buildKey(messageId);

        try {
            redisTemplate.opsForValue().set(
                key, 
                "processed", 
                Duration.ofDays(ttlDays)
            );
            log.debug("Marked message as processed in deduplication cache: {}", messageId);

            // TODO: In production, also persist to MongoDB here for backup
            // This ensures deduplication survives Redis restarts

        } catch (Exception e) {
            log.error("Error marking message {} as processed: {}. Continuing anyway.", 
                    messageId, e.getMessage());
            // Non-critical failure - don't block processing
            // Worst case: message might be reprocessed if router restarts
        }
    }

    /**
     * Builds the Redis key for deduplication tracking.
     * 
     * @param messageId Unique message identifier
     * @return Redis key string
     */
    private String buildKey(String messageId) {
        return ROUTER_PROCESSED_PREFIX + messageId;
    }

    /**
     * Removes a message from deduplication cache (for testing/manual intervention).
     * 
     * WARNING: Use with caution in production!
     * 
     * @param messageId Unique message identifier
     */
    public void removeFromCache(String messageId) {
        String key = buildKey(messageId);
        try {
            redisTemplate.delete(key);
            log.warn("Removed message from deduplication cache: {}", messageId);
        } catch (Exception e) {
            log.error("Error removing message {} from cache: {}", messageId, e.getMessage());
        }
    }
}
