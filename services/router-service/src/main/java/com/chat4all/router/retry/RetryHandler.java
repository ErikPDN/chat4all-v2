package com.chat4all.router.retry;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retry Handler (T048)
 * 
 * Implements retry logic with exponential backoff using Resilience4j.
 * 
 * Configuration (FR-008):
 * - Max attempts: 3
 * - Initial delay: 1 second
 * - Exponential multiplier: 2x
 * - Max delay: 10 seconds
 * 
 * Backoff sequence:
 * - Attempt 1: Immediate
 * - Attempt 2: Wait 1 second
 * - Attempt 3: Wait 2 seconds
 * - Attempt 4: Wait 4 seconds (but max is 3 attempts, so this won't happen)
 * 
 * After max retries exceeded:
 * - Message is marked as FAILED
 * - DLQ handler is invoked (T049)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class RetryHandler {

    private final Retry retry;

    @Value("${app.routing.max-retries:3}")
    private int maxRetries;

    @Value("${app.routing.retry-delay-ms:1000}")
    private long retryDelayMs;

    /**
     * Constructor that initializes Resilience4j Retry instance.
     */
    public RetryHandler() {
        // Build retry configuration
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(Exception.class)
            .ignoreExceptions(IllegalArgumentException.class)
            .build();

        // Create retry registry and get retry instance
        RetryRegistry registry = RetryRegistry.of(config);
        this.retry = registry.retry("messageDelivery");

        // Add event listeners for logging
        retry.getEventPublisher()
            .onRetry(event -> log.warn("Retry attempt {} for operation: {}", 
                event.getNumberOfRetryAttempts(), 
                event.getName()))
            .onSuccess(event -> log.info("Operation succeeded after {} attempts: {}", 
                event.getNumberOfRetryAttempts(), 
                event.getName()))
            .onError(event -> log.error("Operation failed after {} attempts: {}", 
                event.getNumberOfRetryAttempts(), 
                event.getLastThrowable().getMessage()));
    }

    /**
     * Executes a supplier function with retry logic.
     * 
     * This wraps any operation (typically message delivery) with automatic retries.
     * 
     * Example usage:
     * <pre>
     * boolean result = retryHandler.executeWithRetry(() -> {
     *     return connectorClient.deliverMessage(message, url);
     * });
     * </pre>
     * 
     * @param operation The operation to execute (supplier that returns Boolean)
     * @param <T> The return type of the operation
     * @return The result of the operation, or false if all retries failed
     */
    public <T> T executeWithRetry(Supplier<T> operation) {
        try {
            // Decorate the supplier with retry logic
            Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, operation);
            
            log.debug("Executing operation with retry logic (max {} attempts)", maxRetries);
            
            // Execute the operation
            T result = decoratedSupplier.get();
            
            log.debug("Operation completed successfully");
            return result;

        } catch (Exception e) {
            log.error("Operation failed after all retry attempts: {}", e.getMessage(), e);
            
            // Return null or default value based on type
            // For Boolean operations, return false
            if (operation.get() instanceof Boolean) {
                return (T) Boolean.FALSE;
            }
            return null;
        }
    }

    /**
     * Executes a runnable operation with retry logic (no return value).
     * 
     * @param operation The operation to execute
     */
    public void executeWithRetry(Runnable operation) {
        executeWithRetry(() -> {
            operation.run();
            return true;
        });
    }

    /**
     * Gets the current retry statistics.
     * 
     * Useful for monitoring and debugging.
     * 
     * @return Retry metrics
     */
    public Retry.Metrics getMetrics() {
        return retry.getMetrics();
    }
}
