package com.chat4all.connector.api;

import com.chat4all.connector.api.dto.DeliveryResult;
import com.chat4all.connector.api.dto.InboundMessage;
import com.chat4all.connector.api.dto.OutboundMessage;
import com.chat4all.connector.api.dto.ValidationResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Base implementation of MessageConnector with circuit breaker and retry logic
 * 
 * Provides common resilience patterns for all connector implementations:
 * - Circuit breaker with 50% failure threshold (Constitutional Principle II)
 * - Exponential backoff retry (max 3 attempts) (Constitutional Principle III)
 * - Rate limiting support
 * 
 * Aligned with:
 * - Task T017
 * - Research: External API integration pattern
 * - Constitutional Principles II, III
 * 
 * Usage:
 * <pre>
 * public class WhatsAppConnector extends BaseConnector {
 *     public WhatsAppConnector() {
 *         super("whatsapp");
 *     }
 *     
 *     protected Mono<DeliveryResult> doSendMessage(OutboundMessage message) {
 *         // WhatsApp-specific implementation
 *     }
 * }
 * </pre>
 */
@Slf4j
public abstract class BaseConnector implements MessageConnector {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final String connectorName;
    
    /**
     * Initialize connector with circuit breaker and retry logic
     * 
     * @param connectorName Name of the connector (whatsapp, telegram, instagram)
     */
    protected BaseConnector(String connectorName) {
        this.connectorName = connectorName;
        
        // Circuit breaker configuration - 50% failure threshold
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f) // Open circuit if 50% of calls fail
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before half-open
            .slidingWindowSize(10) // Track last 10 calls
            .minimumNumberOfCalls(5) // Minimum 5 calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 test calls in half-open state
            .recordExceptions(RuntimeException.class, java.io.IOException.class)
            .build();
        
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(connectorName);
        
        // Register circuit breaker state change listener
        this.circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker {} transitioned from {} to {}", 
                    connectorName, event.getStateTransition().getFromState(), 
                    event.getStateTransition().getToState()));
        
        // Retry configuration - exponential backoff, max 3 attempts
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3) // Maximum 3 attempts (1 original + 2 retries)
            .waitDuration(Duration.ofSeconds(1)) // Initial wait duration
            .intervalFunction(attempt -> {
                // Exponential backoff: 1s, 2s, 4s
                long backoffMs = Duration.ofSeconds((long) Math.pow(2, attempt - 1)).toMillis();
                return backoffMs;
            })
            .retryExceptions(RuntimeException.class, java.io.IOException.class)
            .ignoreExceptions(IllegalArgumentException.class) // Don't retry validation errors
            .build();
        
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        this.retry = retryRegistry.retry(connectorName);
        
        // Register retry event listeners
        this.retry.getEventPublisher()
            .onRetry(event -> 
                log.warn("Retry attempt {} for {}: {}", 
                    event.getNumberOfRetryAttempts(), connectorName, 
                    event.getLastThrowable().getMessage()));
        
        log.info("Initialized BaseConnector for {} with circuit breaker and retry", connectorName);
    }

    @Override
    public final Mono<DeliveryResult> sendMessage(OutboundMessage message) {
        log.debug("Sending message {} via {} with resilience patterns", 
            message.getMessageId(), getChannelName());
        
        return doSendMessage(message)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(RetryOperator.of(retry))
            .doOnSuccess(result -> {
                log.info("Message {} delivered successfully: status={}, platformId={}", 
                    message.getMessageId(), result.getStatus(), result.getPlatformMessageId());
            })
            .doOnError(error -> {
                log.error("Failed to deliver message {} after {} retries: {}", 
                    message.getMessageId(), retry.getRetryConfig().getMaxAttempts(), 
                    error.getMessage());
            });
    }

    @Override
    public final Mono<InboundMessage> receiveWebhook(String payload, String signature) {
        log.debug("Receiving webhook for channel {}", getChannelName());
        
        return doReceiveWebhook(payload, signature)
            .doOnSuccess(msg -> {
                log.info("Webhook processed successfully: platformMessageId={}, channel={}", 
                    msg.getPlatformMessageId(), getChannelName());
            })
            .doOnError(error -> {
                log.error("Failed to process webhook for {}: {}", 
                    getChannelName(), error.getMessage());
            });
    }

    @Override
    public final Mono<ValidationResult> validateCredentials() {
        log.debug("Validating credentials for {}", getChannelName());
        
        return doValidateCredentials()
            .timeout(Duration.ofSeconds(5))
            .doOnSuccess(result -> {
                if (result.isValid()) {
                    log.info("Credential validation for {} succeeded: {}", 
                        getChannelName(), result.getPlatformInfo());
                } else {
                    log.warn("Credential validation for {} failed: {}", 
                        getChannelName(), result.getMessage());
                }
            })
            .doOnError(error -> {
                log.error("Credential validation error for {}: {}", 
                    getChannelName(), error.getMessage());
            })
            .onErrorResume(error -> Mono.just(ValidationResult.builder()
                .valid(false)
                .message("Validation timeout or error: " + error.getMessage())
                .build()));
    }

    @Override
    public Mono<RateLimitInfo> getRateLimitStatus() {
        // Default implementation - subclasses can override
        return Mono.just(new RateLimitInfo(
            Integer.MAX_VALUE, // remainingCalls
            System.currentTimeMillis() + 60000, // resetTimeEpochMs (1 minute from now)
            Integer.MAX_VALUE // maxCallsPerWindow
        ));
    }

    /**
     * Template method for actual message sending logic
     * Implementations must provide platform-specific delivery
     * 
     * @param message Outbound message to send
     * @return Mono of delivery result
     */
    protected abstract Mono<DeliveryResult> doSendMessage(OutboundMessage message);

    /**
     * Template method for webhook processing
     * Implementations must validate signature and transform payload
     * 
     * @param payload Raw webhook payload from platform
     * @param signature Webhook signature for validation
     * @return Mono of inbound message in internal format
     */
    protected abstract Mono<InboundMessage> doReceiveWebhook(String payload, String signature);

    /**
     * Template method for credential validation
     * Implementations must call platform health check API
     * 
     * @return Mono of validation result
     */
    protected abstract Mono<ValidationResult> doValidateCredentials();

    /**
     * Get circuit breaker for monitoring and manual control
     */
    protected CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Get retry configuration for monitoring
     */
    protected Retry getRetry() {
        return retry;
    }

    /**
     * Get connector name
     */
    protected String getConnectorName() {
        return connectorName;
    }
}
