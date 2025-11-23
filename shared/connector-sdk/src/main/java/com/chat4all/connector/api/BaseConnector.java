package com.chat4all.connector.api;

import com.chat4all.connector.api.dto.DeliveryResult;
import com.chat4all.connector.api.dto.InboundMessage;
import com.chat4all.connector.api.dto.OutboundMessage;
import com.chat4all.connector.api.dto.ValidationResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * BaseConnector Abstract Class
 * Provides common infrastructure for all platform connector implementations
 * 
 * Aligned with:
 * - Constitutional Principle VII: Pluggable architecture
 * - Constitutional Principle II: High availability via circuit breaker pattern
 * - FR-008: Exponential backoff retry logic (max 3 attempts)
 * - FR-015: Circuit breaker for connector isolation
 * - Task T017
 * 
 * Features:
 * - Circuit breaker with 50% failure threshold
 * - Exponential backoff retry (max 3 attempts, 1s → 2s → 4s)
 * - Reactive WebClient for non-blocking HTTP calls
 * - Structured logging with channel context
 * - Rate limit tracking support
 * 
 * Usage:
 * <pre>
 * {@code
 * public class WhatsAppConnector extends BaseConnector {
 *     public WhatsAppConnector(WebClient webClient) {
 *         super("WHATSAPP", webClient);
 *     }
 *     
 *     @Override
 *     protected Mono<DeliveryResult> doSendMessage(OutboundMessage message) {
 *         // Platform-specific implementation
 *     }
 * }
 * }
 * </pre>
 */
@Slf4j
public abstract class BaseConnector implements MessageConnector {

    private final String channelName;
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    /**
     * Constructor for BaseConnector
     * 
     * @param channelName the channel this connector handles (WHATSAPP, TELEGRAM, INSTAGRAM)
     * @param webClient Spring WebClient for HTTP calls
     */
    protected BaseConnector(String channelName, WebClient webClient) {
        this.channelName = channelName;
        this.webClient = webClient;
        
        // Initialize circuit breaker with constitutional requirements
        this.circuitBreaker = createCircuitBreaker(channelName);
        
        // Initialize retry logic with exponential backoff
        this.retry = createRetry(channelName);
        
        log.info("Initialized {} connector with circuit breaker and retry logic", channelName);
    }

    /**
     * Create circuit breaker with 50% failure threshold
     * 
     * Configuration:
     * - Sliding window: 10 calls
     * - Failure threshold: 50%
     * - Wait duration in open state: 30 seconds
     * - Permitted calls in half-open state: 3
     * 
     * @param name circuit breaker name (channel name)
     * @return configured CircuitBreaker instance
     */
    private CircuitBreaker createCircuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(Exception.class)
            .build();
        
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        CircuitBreaker cb = registry.circuitBreaker(name + "-circuit-breaker");
        
        // Register event listeners for observability
        cb.getEventPublisher()
            .onStateTransition(event -> 
                log.warn("Circuit breaker {} state transition: {} -> {}", 
                    name, event.getStateTransition().getFromState(), 
                    event.getStateTransition().getToState()))
            .onError(event -> 
                log.error("Circuit breaker {} error: {}", name, event.getThrowable().getMessage()));
        
        return cb;
    }

    /**
     * Create retry with exponential backoff
     * 
     * Configuration:
     * - Max attempts: 3 (per FR-008)
     * - Initial interval: 1 second
     * - Multiplier: 2.0 (exponential backoff: 1s → 2s → 4s)
     * - Retry on all exceptions except validation errors
     * 
     * @param name retry name (channel name)
     * @return configured Retry instance
     */
    private Retry createRetry(String name) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2.0))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class, SecurityException.class)
            .build();
        
        RetryRegistry registry = RetryRegistry.of(config);
        Retry r = registry.retry(name + "-retry");
        
        // Register event listeners for observability
        r.getEventPublisher()
            .onRetry(event -> 
                log.warn("Retry attempt {} for {}: {}", 
                    event.getNumberOfRetryAttempts(), name, 
                    event.getLastThrowable().getMessage()))
            .onError(event -> 
                log.error("Retry exhausted for {} after {} attempts", 
                    name, event.getNumberOfRetryAttempts()));
        
        return r;
    }

    /**
     * Send message with circuit breaker and retry protection
     * Delegates to doSendMessage() for platform-specific implementation
     */
    @Override
    public Mono<DeliveryResult> sendMessage(OutboundMessage message) {
        log.debug("Sending message {} via {} connector", message.getMessageId(), channelName);
        
        return doSendMessage(message)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .transformDeferred(io.github.resilience4j.reactor.retry.RetryOperator.of(retry))
            .doOnSuccess(result -> 
                log.info("Message {} sent successfully via {}: platform_id={}, status={}", 
                    message.getMessageId(), channelName, result.getPlatformMessageId(), result.getStatus()))
            .doOnError(error -> 
                log.error("Failed to send message {} via {} after retries: {}", 
                    message.getMessageId(), channelName, error.getMessage()));
    }

    /**
     * Receive webhook with validation
     * Delegates to doReceiveWebhook() for platform-specific implementation
     */
    @Override
    public Mono<InboundMessage> receiveWebhook(String payload, String signature) {
        log.debug("Receiving webhook for {} connector", channelName);
        
        return doReceiveWebhook(payload, signature)
            .doOnSuccess(message -> 
                log.info("Webhook processed for {}: message_id={}", channelName, message.getPlatformMessageId()))
            .doOnError(error -> 
                log.error("Failed to process webhook for {}: {}", channelName, error.getMessage()));
    }

    /**
     * Validate credentials with circuit breaker protection
     * Delegates to doValidateCredentials() for platform-specific implementation
     */
    @Override
    public Mono<ValidationResult> validateCredentials() {
        log.debug("Validating credentials for {} connector", channelName);
        
        return doValidateCredentials()
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .timeout(Duration.ofSeconds(5))
            .doOnSuccess(result -> 
                log.info("Credential validation for {}: valid={}", channelName, result.isValid()))
            .doOnError(error -> 
                log.error("Credential validation failed for {}: {}", channelName, error.getMessage()))
            .onErrorResume(error -> 
                Mono.just(ValidationResult.failure(error.getMessage(), null)));
    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    /**
     * Get the WebClient instance for making HTTP calls
     * 
     * @return configured WebClient
     */
    protected WebClient getWebClient() {
        return webClient;
    }

    /**
     * Get the CircuitBreaker instance for manual control
     * 
     * @return CircuitBreaker instance
     */
    protected CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Get the Retry instance for manual control
     * 
     * @return Retry instance
     */
    protected Retry getRetry() {
        return retry;
    }

    // ========================================================================
    // Abstract methods to be implemented by platform-specific connectors
    // ========================================================================

    /**
     * Platform-specific message sending implementation
     * 
     * Requirements:
     * - Transform OutboundMessage to platform API format
     * - Make HTTP call to platform API
     * - Parse response and return DeliveryResult
     * - Handle rate limiting (throw exception to trigger retry)
     * 
     * @param message outbound message to send
     * @return Mono of delivery result
     */
    protected abstract Mono<DeliveryResult> doSendMessage(OutboundMessage message);

    /**
     * Platform-specific webhook processing implementation
     * 
     * Requirements:
     * - Validate webhook signature/token
     * - Parse platform-specific payload format
     * - Transform to InboundMessage format
     * - Handle duplicate deliveries (check message ID)
     * 
     * @param payload raw webhook payload
     * @param signature webhook signature header
     * @return Mono of inbound message
     */
    protected abstract Mono<InboundMessage> doReceiveWebhook(String payload, String signature);

    /**
     * Platform-specific credential validation implementation
     * 
     * Requirements:
     * - Call platform health check endpoint
     * - Verify API credentials are valid
     * - Check required permissions/scopes
     * - Return ValidationResult with details
     * 
     * @return Mono of validation result
     */
    protected abstract Mono<ValidationResult> doValidateCredentials();

    /**
     * Platform-specific rate limit status implementation
     * Optional: can return default implementation
     * 
     * @return Mono of rate limit info
     */
    @Override
    public Mono<RateLimitInfo> getRateLimitStatus() {
        // Default implementation returns unlimited
        return Mono.just(new RateLimitInfo(Integer.MAX_VALUE, 0L, Integer.MAX_VALUE));
    }
}
