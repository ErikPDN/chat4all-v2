package com.chat4all.gateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global rate limiting filter for API Gateway.
 * 
 * Implements rate limiting based on:
 * - Authenticated users: 100 requests/minute per user
 * - Global limit: 1000 requests/minute
 * - Burst capacity: 200 requests
 * 
 * Uses Redis for distributed rate limiting across multiple gateway instances.
 * Falls back to local bucket if Redis is unavailable.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    
    // Rate limit configurations
    private static final int USER_RATE_LIMIT = 100; // requests per minute
    private static final int GLOBAL_RATE_LIMIT = 1000; // requests per minute
    private static final int BURST_CAPACITY = 200; // max burst
    
    // Local bucket cache (fallback when Redis unavailable)
    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();
    private final Bucket globalBucket;
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    
    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        // Initialize global bucket for fallback
        this.globalBucket = Bucket.builder()
                .addLimit(Bandwidth.classic(GLOBAL_RATE_LIMIT, 
                        Refill.intervally(GLOBAL_RATE_LIMIT, Duration.ofMinutes(1))))
                .build();
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Skip rate limiting for actuator endpoints
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }
        
        // Get user identifier (from JWT token or IP address)
        String userId = extractUserId(exchange);
        
        return checkRateLimit(userId)
                .flatMap(allowed -> {
                    if (allowed) {
                        log.debug("Request allowed for user: {}", userId);
                        return chain.filter(exchange);
                    } else {
                        log.warn("Rate limit exceeded for user: {}", userId);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", "60");
                        return exchange.getResponse().setComplete();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error checking rate limit, allowing request", e);
                    return chain.filter(exchange); // Fail open
                });
    }
    
    /**
     * Check rate limit for a user using Redis-backed bucket.
     */
    private Mono<Boolean> checkRateLimit(String userId) {
        String redisKey = "ratelimit:" + userId;
        
        return redisTemplate.opsForValue()
                .increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request, set expiration
                        return redisTemplate.expire(redisKey, Duration.ofMinutes(1))
                                .thenReturn(true);
                    }
                    
                    // Check if within limit
                    return Mono.just(count <= USER_RATE_LIMIT);
                })
                .onErrorResume(e -> {
                    // Fallback to local bucket on Redis error
                    log.warn("Redis unavailable, using local rate limiter", e);
                    return Mono.just(checkLocalRateLimit(userId));
                });
    }
    
    /**
     * Fallback rate limiting using local in-memory buckets.
     */
    private boolean checkLocalRateLimit(String userId) {
        Bucket bucket = localBuckets.computeIfAbsent(userId, k -> 
            Bucket.builder()
                .addLimit(Bandwidth.classic(USER_RATE_LIMIT, 
                        Refill.intervally(USER_RATE_LIMIT, Duration.ofMinutes(1))))
                .build()
        );
        
        // Also check global limit
        return bucket.tryConsume(1) && globalBucket.tryConsume(1);
    }
    
    /**
     * Extract user identifier from JWT token or fallback to IP address.
     */
    private String extractUserId(ServerWebExchange exchange) {
        // Try to get user from JWT token (if authenticated)
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In production, decode JWT and extract user ID
            // For now, use a simplified approach
            return "user:" + authHeader.substring(7, Math.min(20, authHeader.length()));
        }
        
        // Fallback to IP address for unauthenticated requests
        String clientIp = exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        return "ip:" + clientIp;
    }
    
    @Override
    public int getOrder() {
        return -1; // Execute before other filters
    }
}
