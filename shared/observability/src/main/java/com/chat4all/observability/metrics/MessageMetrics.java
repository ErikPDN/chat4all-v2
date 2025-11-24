package com.chat4all.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Custom Micrometer metrics for message processing
 * 
 * Provides metrics for:
 * - Message throughput (messages sent/received per second)
 * - Message delivery latency (P50, P95, P99)
 * - Error rates (failed deliveries, retries)
 * - Connector health (circuit breaker state)
 * - Conversation metrics (active count, message count)
 * 
 * Aligned with:
 * - Task T019
 * - Constitutional Principle VI (Full-Stack Observability)
 * - FR-037: Prometheus metrics requirement
 * - FR-040: Latency alerting requirement (<200ms P95)
 * 
 * Metrics exported:
 * - messages.sent.total{channel}
 * - messages.received.total{channel}
 * - messages.failed.total{channel, error_type}
 * - messages.retried.total{channel, attempt}
 * - messages.acceptance.latency (percentiles: 0.5, 0.95, 0.99)
 * - messages.delivery.latency{channel} (percentiles: 0.5, 0.95, 0.99)
 * - messages.routing.latency
 * - webhooks.processing.latency{channel}
 * - conversations.active.count
 * - connector.health{channel}
 */
@Slf4j
@Component
public class MessageMetrics {

    private final MeterRegistry meterRegistry;
    
    // Message throughput counters
    private final Counter messagesSentCounter;
    private final Counter messagesReceivedCounter;
    private final Counter messagesFailedCounter;
    private final Counter messagesRetriedCounter;
    
    // Latency timers
    private final Timer messageAcceptanceLatency;
    private final Timer messageDeliveryLatency;
    private final Timer messageRoutingLatency;
    private final Timer webhookProcessingLatency;

    public MessageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters with base tags
        this.messagesSentCounter = Counter.builder("messages.sent.total")
            .description("Total number of messages sent to external platforms")
            .tag("type", "outbound")
            .register(meterRegistry);
        
        this.messagesReceivedCounter = Counter.builder("messages.received.total")
            .description("Total number of messages received from external platforms")
            .tag("type", "inbound")
            .register(meterRegistry);
        
        this.messagesFailedCounter = Counter.builder("messages.failed.total")
            .description("Total number of failed message deliveries")
            .tag("type", "error")
            .register(meterRegistry);
        
        this.messagesRetriedCounter = Counter.builder("messages.retried.total")
            .description("Total number of message delivery retry attempts")
            .tag("type", "retry")
            .register(meterRegistry);
        
        // Initialize timers with percentiles and SLA boundaries
        this.messageAcceptanceLatency = Timer.builder("messages.acceptance.latency")
            .description("Time to accept message and return 202 response (API → MongoDB → Kafka)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .sla(Duration.ofMillis(50)) // Constitutional Principle V: <50ms for sync operations
            .register(meterRegistry);
        
        this.messageDeliveryLatency = Timer.builder("messages.delivery.latency")
            .description("End-to-end time from message acceptance to delivery confirmation")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .sla(
                Duration.ofMillis(200),  // Constitutional Principle V: <200ms P95
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)    // FR-040: Alert threshold
            )
            .register(meterRegistry);
        
        this.messageRoutingLatency = Timer.builder("messages.routing.latency")
            .description("Time to route message from Kafka consumer to connector delivery")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);
        
        this.webhookProcessingLatency = Timer.builder("webhooks.processing.latency")
            .description("Time to process incoming webhook from external platform")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .sla(Duration.ofMillis(100)) // Fast webhook acknowledgment
            .register(meterRegistry);
        
        log.info("MessageMetrics initialized with Micrometer registry: {}",
            meterRegistry.getClass().getSimpleName());
    }

    // ============================================================================
    // Throughput Metrics
    // ============================================================================

    /**
     * Record a message sent event
     * 
     * @param channel Channel name (WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL)
     */
    public void recordMessageSent(String channel) {
        messagesSentCounter.increment();
        meterRegistry.counter("messages.sent.by_channel", "channel", channel).increment();
        log.debug("Recorded message sent: channel={}", channel);
    }

    /**
     * Record a message received event
     * 
     * @param channel Channel name
     */
    public void recordMessageReceived(String channel) {
        messagesReceivedCounter.increment();
        meterRegistry.counter("messages.received.by_channel", "channel", channel).increment();
        log.debug("Recorded message received: channel={}", channel);
    }

    /**
     * Record a message delivery failure
     * 
     * @param channel Channel name
     * @param errorType Error category (TIMEOUT, API_ERROR, VALIDATION_ERROR, etc.)
     */
    public void recordMessageFailed(String channel, String errorType) {
        messagesFailedCounter.increment();
        meterRegistry.counter("messages.failed.by_channel", 
            "channel", channel, 
            "error_type", errorType)
            .increment();
        log.warn("Recorded message failure: channel={}, errorType={}", channel, errorType);
    }

    /**
     * Record a message retry attempt
     * 
     * @param channel Channel name
     * @param attemptNumber Current retry attempt (1, 2, 3)
     */
    public void recordMessageRetry(String channel, int attemptNumber) {
        messagesRetriedCounter.increment();
        meterRegistry.counter("messages.retried.by_channel", 
            "channel", channel, 
            "attempt", String.valueOf(attemptNumber))
            .increment();
        log.debug("Recorded message retry: channel={}, attempt={}", channel, attemptNumber);
    }

    // ============================================================================
    // Latency Metrics
    // ============================================================================

    /**
     * Record message acceptance latency (API → MongoDB → Kafka)
     * 
     * @param durationMs Duration in milliseconds
     */
    public void recordAcceptanceLatency(long durationMs) {
        messageAcceptanceLatency.record(durationMs, TimeUnit.MILLISECONDS);
        
        // Alert if exceeding 50ms threshold (Constitutional Principle V)
        if (durationMs > 50) {
            log.warn("Message acceptance latency exceeded 50ms threshold: {}ms", durationMs);
        }
        
        log.debug("Recorded acceptance latency: {}ms", durationMs);
    }

    /**
     * Record message delivery latency (acceptance → external platform confirmation)
     * 
     * @param durationMs Duration in milliseconds
     * @param channel Channel name
     */
    public void recordDeliveryLatency(long durationMs, String channel) {
        messageDeliveryLatency.record(durationMs, TimeUnit.MILLISECONDS);
        
        // Per-channel delivery latency
        Timer.builder("messages.delivery.latency.by_channel")
            .tag("channel", channel)
            .publishPercentiles(0.95)
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
        
        // Alert threshold check (FR-040: >5s triggers alert)
        if (durationMs > 5000) {
            log.error("Message delivery latency exceeded 5s alert threshold: {}ms, channel={}", 
                durationMs, channel);
        } else if (durationMs > 200) {
            // Warn if exceeding P95 target (Constitutional Principle V)
            log.warn("Message delivery latency exceeded 200ms P95 target: {}ms, channel={}", 
                durationMs, channel);
        }
        
        log.debug("Recorded delivery latency: {}ms, channel={}", durationMs, channel);
    }

    /**
     * Record message routing latency (Kafka consumer → connector delivery)
     * 
     * @param durationMs Duration in milliseconds
     */
    public void recordRoutingLatency(long durationMs) {
        messageRoutingLatency.record(durationMs, TimeUnit.MILLISECONDS);
        log.debug("Recorded routing latency: {}ms", durationMs);
    }

    /**
     * Record webhook processing latency
     * 
     * @param durationMs Duration in milliseconds
     * @param channel Channel name
     */
    public void recordWebhookProcessingLatency(long durationMs, String channel) {
        webhookProcessingLatency.record(durationMs, TimeUnit.MILLISECONDS);
        
        // Per-channel webhook processing latency
        Timer.builder("webhooks.processing.latency.by_channel")
            .tag("channel", channel)
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
        
        log.debug("Recorded webhook processing latency: {}ms, channel={}", durationMs, channel);
    }

    // ============================================================================
    // Gauge Metrics (State)
    // ============================================================================

    /**
     * Record active conversations count
     * 
     * @param count Current number of active conversations
     */
    public void recordActiveConversations(long count) {
        meterRegistry.gauge("conversations.active.count", count);
        log.debug("Recorded active conversations: {}", count);
    }

    /**
     * Record active conversations by channel
     * 
     * @param channel Channel name
     * @param count Conversation count for this channel
     */
    public void recordActiveConversationsByChannel(String channel, long count) {
        meterRegistry.gauge("conversations.active.by_channel", 
            java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("channel", channel)),
            count);
    }

    /**
     * Record connector health status
     * 
     * @param channel Channel name
     * @param isHealthy Health status (true = healthy, false = unhealthy)
     */
    public void recordConnectorHealth(String channel, boolean isHealthy) {
        meterRegistry.gauge("connector.health", 
            java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("channel", channel)),
            isHealthy ? 1.0 : 0.0);
        
        if (!isHealthy) {
            log.warn("Connector {} reported unhealthy status", channel);
        }
    }

    /**
     * Record circuit breaker state
     * 
     * @param channel Channel name
     * @param state Circuit breaker state (CLOSED=0, OPEN=1, HALF_OPEN=2)
     */
    public void recordCircuitBreakerState(String channel, int state) {
        meterRegistry.gauge("connector.circuit_breaker.state",
            java.util.Collections.singletonList(io.micrometer.core.instrument.Tag.of("channel", channel)),
            state);
        
        String stateName = state == 0 ? "CLOSED" : (state == 1 ? "OPEN" : "HALF_OPEN");
        log.info("Circuit breaker for {} is {}", channel, stateName);
    }

    // ============================================================================
    // Utility Methods
    // ============================================================================

    /**
     * Get meter registry for custom metrics
     * 
     * @return MeterRegistry instance
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    /**
     * Record custom counter metric
     * 
     * @param name Metric name
     * @param tags Key-value pairs for tags
     */
    public void recordCounter(String name, String... tags) {
        meterRegistry.counter(name, tags).increment();
    }

    /**
     * Record custom timer metric
     * 
     * @param name Metric name
     * @param durationMs Duration in milliseconds
     * @param tags Key-value pairs for tags
     */
    public void recordTimer(String name, long durationMs, String... tags) {
        meterRegistry.timer(name, tags).record(durationMs, TimeUnit.MILLISECONDS);
    }
}
