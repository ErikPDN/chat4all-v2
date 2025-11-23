package com.chat4all.connector.api;

import com.chat4all.connector.api.dto.DeliveryResult;
import com.chat4all.connector.api.dto.InboundMessage;
import com.chat4all.connector.api.dto.OutboundMessage;
import com.chat4all.connector.api.dto.ValidationResult;
import reactor.core.publisher.Mono;

/**
 * MessageConnector Interface
 * Contract for all platform-specific connector implementations
 * 
 * Aligned with:
 * - Constitutional Principle VII: Pluggable architecture
 * - Research: External API integration pattern using Spring WebClient (reactive)
 * - Task T016
 * 
 * Implementations:
 * - WhatsAppConnector (services/connectors/whatsapp-connector)
 * - TelegramConnector (services/connectors/telegram-connector)
 * - InstagramConnector (services/connectors/instagram-connector)
 * 
 * All implementations must:
 * - Use Resilience4j circuit breaker pattern
 * - Implement exponential backoff retry logic
 * - Validate webhook signatures for security
 * - Transform platform-specific formats to/from internal MessageDTO
 * - Handle rate limiting per platform's requirements
 */
public interface MessageConnector {

    /**
     * Send message to external platform
     * 
     * Implementation requirements:
     * - Non-blocking reactive operation (returns Mono)
     * - Circuit breaker with 50% failure threshold
     * - Retry with exponential backoff (max 3 attempts)
     * - Rate limiting based on platform limits
     * - Idempotent (safe to retry with same messageId)
     * 
     * @param message outbound message to send
     * @return Mono of delivery result with platform message ID and status
     */
    Mono<DeliveryResult> sendMessage(OutboundMessage message);

    /**
     * Process incoming webhook from external platform
     * 
     * Implementation requirements:
     * - Validate webhook signature/token for security
     * - Transform platform-specific payload to internal format
     * - Handle duplicate webhook deliveries (idempotency)
     * - Return 200 OK quickly to avoid platform retries
     * - Process asynchronously after acknowledgment
     * 
     * @param payload raw webhook payload from platform
     * @param signature webhook signature header for validation
     * @return Mono of inbound message in internal format
     */
    Mono<InboundMessage> receiveWebhook(String payload, String signature);

    /**
     * Validate connector credentials
     * 
     * Implementation requirements:
     * - Call platform API health check endpoint
     * - Verify access token/credentials are valid
     * - Check required permissions/scopes
     * - Timeout after 5 seconds
     * 
     * Use cases:
     * - Channel configuration setup
     * - Periodic credential rotation validation
     * - Troubleshooting connection issues
     * 
     * @return Mono of validation result with success flag and error details
     */
    Mono<ValidationResult> validateCredentials();

    /**
     * Get the platform/channel name this connector handles
     * 
     * @return channel name (WHATSAPP, TELEGRAM, INSTAGRAM)
     */
    String getChannelName();

    /**
     * Get current rate limit status
     * 
     * @return Mono of rate limit info (remaining calls, reset time)
     */
    Mono<RateLimitInfo> getRateLimitStatus();

    /**
     * Health check for connector service
     * 
     * @return Mono of boolean indicating if connector is healthy
     */
    default Mono<Boolean> isHealthy() {
        return validateCredentials()
            .map(ValidationResult::isValid)
            .onErrorReturn(false);
    }

    /**
     * Rate limit information
     */
    record RateLimitInfo(
        int remainingCalls,
        long resetTimeEpochMs,
        int maxCallsPerWindow
    ) {}
}
