package com.chat4all.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MessageMetrics
 * Custom Micrometer metrics for message throughput, latency, and error rates
 * 
 * Aligned with:
 * - Constitutional Principle VI: Full-stack observability (NON-NEGOTIABLE)
 * - FR-037: Custom metrics for business KPIs
 * - FR-040: Latency threshold alerting (<5s message delivery)
 * - SC-001: API response time <500ms (P95)
 * - SC-002: Message delivery <5s (P95)
 * - Task T019
 * 
 * Metrics exported:
 * 
 * Counters:
 * - chat4all.message.send.count (tags: channel, status)
 * - chat4all.message.receive.count (tags: channel)
 * - chat4all.message.route.count (tags: channel, target_connector)
 * - chat4all.message.retry.count (tags: channel, attempt)
 * - chat4all.message.error.count (tags: channel, error_type)
 * 
 * Timers:
 * - chat4all.message.send.latency (tags: channel)
 * - chat4all.message.delivery.latency (tags: channel)
 * - chat4all.message.route.latency (tags: channel)
 * - chat4all.api.response.time (tags: endpoint, method)
 * 
 * Usage:
 * <pre>
 * {@code
 * @Service
 * public class MessageService {
 *     private final MessageMetrics metrics;
 *     
 *     public Mono<DeliveryResult> sendMessage(OutboundMessage msg) {
 *         return Mono.defer(() -> {
 *             Timer.Sample sample = metrics.startSendTimer();
 *             
 *             return doSendMessage(msg)
 *                 .doOnSuccess(result -> {
 *                     metrics.recordSendSuccess(msg.channel());
 *                     metrics.recordSendLatency(sample, msg.channel());
 *                 })
 *                 .doOnError(error -> {
 *                     metrics.recordSendError(msg.channel(), error.getClass().getSimpleName());
 *                 });
 *         });
 *     }
 * }
 * }
 * </pre>
 * 
 * Prometheus query examples:
 * - Message send rate: rate(chat4all_message_send_count_total[5m])
 * - P95 delivery latency: histogram_quantile(0.95, chat4all_message_delivery_latency_seconds)
 * - Error rate: rate(chat4all_message_error_count_total[5m])
 */
@Slf4j
@Component
public class MessageMetrics {

    private final MeterRegistry registry;

    // Metric name prefixes
    private static final String METRIC_PREFIX = "chat4all.message";
    private static final String API_PREFIX = "chat4all.api";

    // Tag keys
    private static final String TAG_CHANNEL = "channel";
    private static final String TAG_STATUS = "status";
    private static final String TAG_ERROR_TYPE = "error_type";
    private static final String TAG_ATTEMPT = "attempt";
    private static final String TAG_CONNECTOR = "target_connector";
    private static final String TAG_ENDPOINT = "endpoint";
    private static final String TAG_METHOD = "method";

    public MessageMetrics(MeterRegistry registry) {
        this.registry = registry;
        log.info("MessageMetrics initialized with registry: {}", registry.getClass().getSimpleName());
    }

    // ========================================================================
    // Message Send Metrics
    // ========================================================================

    /**
     * Start timer for message send operation
     * 
     * @return Timer.Sample to be stopped later
     */
    public Timer.Sample startSendTimer() {
        return Timer.start(registry);
    }

    /**
     * Record successful message send
     * 
     * @param channel channel name (WHATSAPP, TELEGRAM, INSTAGRAM)
     */
    public void recordSendSuccess(String channel) {
        Counter.builder(METRIC_PREFIX + ".send.count")
            .tag(TAG_CHANNEL, channel)
            .tag(TAG_STATUS, "success")
            .description("Total number of messages sent")
            .register(registry)
            .increment();
    }

    /**
     * Record failed message send
     * 
     * @param channel channel name
     * @param errorType error type/class name
     */
    public void recordSendError(String channel, String errorType) {
        Counter.builder(METRIC_PREFIX + ".error.count")
            .tag(TAG_CHANNEL, channel)
            .tag(TAG_ERROR_TYPE, errorType)
            .tag("operation", "send")
            .description("Total number of message send errors")
            .register(registry)
            .increment();
    }

    /**
     * Record message send latency
     * 
     * @param sample timer sample started earlier
     * @param channel channel name
     */
    public void recordSendLatency(Timer.Sample sample, String channel) {
        sample.stop(Timer.builder(METRIC_PREFIX + ".send.latency")
            .tag(TAG_CHANNEL, channel)
            .description("Message send latency (API call to external platform)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .maximumExpectedValue(Duration.ofSeconds(10))
            .register(registry));
    }

    // ========================================================================
    // Message Receive Metrics
    // ========================================================================

    /**
     * Record message received from external platform
     * 
     * @param channel channel name
     */
    public void recordMessageReceived(String channel) {
        Counter.builder(METRIC_PREFIX + ".receive.count")
            .tag(TAG_CHANNEL, channel)
            .description("Total number of messages received from external platforms")
            .register(registry)
            .increment();
    }

    // ========================================================================
    // Message Routing Metrics
    // ========================================================================

    /**
     * Start timer for message routing operation
     * 
     * @return Timer.Sample to be stopped later
     */
    public Timer.Sample startRouteTimer() {
        return Timer.start(registry);
    }

    /**
     * Record successful message routing
     * 
     * @param channel source channel
     * @param targetConnector target connector name
     */
    public void recordRouteSuccess(String channel, String targetConnector) {
        Counter.builder(METRIC_PREFIX + ".route.count")
            .tag(TAG_CHANNEL, channel)
            .tag(TAG_CONNECTOR, targetConnector)
            .tag(TAG_STATUS, "success")
            .description("Total number of messages routed to connectors")
            .register(registry)
            .increment();
    }

    /**
     * Record message routing latency
     * 
     * @param sample timer sample started earlier
     * @param channel channel name
     */
    public void recordRouteLatency(Timer.Sample sample, String channel) {
        sample.stop(Timer.builder(METRIC_PREFIX + ".route.latency")
            .tag(TAG_CHANNEL, channel)
            .description("Message routing latency (Kafka consume to connector delivery)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .maximumExpectedValue(Duration.ofSeconds(10))
            .register(registry));
    }

    // ========================================================================
    // Message Delivery Metrics (End-to-End)
    // ========================================================================

    /**
     * Start timer for end-to-end message delivery
     * 
     * @return Timer.Sample to be stopped later
     */
    public Timer.Sample startDeliveryTimer() {
        return Timer.start(registry);
    }

    /**
     * Record end-to-end message delivery latency
     * Measured from message acceptance (POST /messages) to external platform confirmation
     * 
     * Constitutional requirement: <5s (P95) per FR-040
     * 
     * @param sample timer sample started earlier
     * @param channel channel name
     */
    public void recordDeliveryLatency(Timer.Sample sample, String channel) {
        sample.stop(Timer.builder(METRIC_PREFIX + ".delivery.latency")
            .tag(TAG_CHANNEL, channel)
            .description("End-to-end message delivery latency (API to external confirmation)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(100))
            .maximumExpectedValue(Duration.ofSeconds(30))
            .register(registry));
    }

    /**
     * Record delivery latency from duration
     * 
     * @param duration delivery duration
     * @param channel channel name
     */
    public void recordDeliveryLatency(Duration duration, String channel) {
        Timer.builder(METRIC_PREFIX + ".delivery.latency")
            .tag(TAG_CHANNEL, channel)
            .description("End-to-end message delivery latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry)
            .record(duration);
    }

    // ========================================================================
    // Retry Metrics
    // ========================================================================

    /**
     * Record message retry attempt
     * 
     * @param channel channel name
     * @param attemptNumber retry attempt number (1, 2, 3)
     */
    public void recordRetryAttempt(String channel, int attemptNumber) {
        Counter.builder(METRIC_PREFIX + ".retry.count")
            .tag(TAG_CHANNEL, channel)
            .tag(TAG_ATTEMPT, String.valueOf(attemptNumber))
            .description("Total number of message delivery retry attempts")
            .register(registry)
            .increment();
    }

    /**
     * Record message sent to dead letter queue (after all retries exhausted)
     * 
     * @param channel channel name
     * @param errorType final error type
     */
    public void recordDeadLetter(String channel, String errorType) {
        Counter.builder(METRIC_PREFIX + ".deadletter.count")
            .tag(TAG_CHANNEL, channel)
            .tag(TAG_ERROR_TYPE, errorType)
            .description("Total number of messages sent to dead letter queue")
            .register(registry)
            .increment();
    }

    // ========================================================================
    // API Metrics
    // ========================================================================

    /**
     * Start timer for API request
     * 
     * @return Timer.Sample to be stopped later
     */
    public Timer.Sample startApiTimer() {
        return Timer.start(registry);
    }

    /**
     * Record API response time
     * 
     * Constitutional requirement: <500ms (P95) per SC-001
     * 
     * @param sample timer sample started earlier
     * @param endpoint API endpoint (e.g., "/messages", "/conversations/{id}")
     * @param method HTTP method (GET, POST, PUT, DELETE)
     * @param statusCode HTTP status code
     */
    public void recordApiResponseTime(Timer.Sample sample, String endpoint, String method, int statusCode) {
        sample.stop(Timer.builder(API_PREFIX + ".response.time")
            .tag(TAG_ENDPOINT, endpoint)
            .tag(TAG_METHOD, method)
            .tag("status", String.valueOf(statusCode))
            .description("API response time")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(5))
            .register(registry));
    }

    /**
     * Record API request count
     * 
     * @param endpoint API endpoint
     * @param method HTTP method
     * @param statusCode HTTP status code
     */
    public void recordApiRequest(String endpoint, String method, int statusCode) {
        Counter.builder(API_PREFIX + ".request.count")
            .tag(TAG_ENDPOINT, endpoint)
            .tag(TAG_METHOD, method)
            .tag("status", String.valueOf(statusCode))
            .description("Total number of API requests")
            .register(registry)
            .increment();
    }

    // ========================================================================
    // Business Metrics
    // ========================================================================

    /**
     * Record active conversation count (gauge)
     * 
     * @param count current number of active conversations
     */
    public void recordActiveConversations(long count) {
        registry.gauge(METRIC_PREFIX + ".conversations.active", count);
    }

    /**
     * Record message throughput (messages per second)
     * This is calculated from counters by Prometheus
     * 
     * Query: rate(chat4all_message_send_count_total[1m])
     */
    public void recordThroughput(String channel, long messagesProcessed) {
        Counter.builder(METRIC_PREFIX + ".throughput")
            .tag(TAG_CHANNEL, channel)
            .description("Message throughput counter (use rate() in Prometheus)")
            .register(registry)
            .increment(messagesProcessed);
    }

    /**
     * Record file upload size
     * 
     * @param sizeBytes file size in bytes
     * @param mimeType file MIME type
     */
    public void recordFileUpload(long sizeBytes, String mimeType) {
        Counter.builder(METRIC_PREFIX + ".file.upload.count")
            .tag("mime_type", mimeType)
            .description("Total number of file uploads")
            .register(registry)
            .increment();
        
        Timer.builder(METRIC_PREFIX + ".file.upload.size")
            .tag("mime_type", mimeType)
            .description("File upload size distribution")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(sizeBytes, TimeUnit.MILLISECONDS); // Record size as milliseconds for histogram
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Create tags list for metrics
     * 
     * @param keyValues alternating key-value pairs
     * @return list of Tags
     */
    private List<Tag> tags(String... keyValues) {
        List<Tag> tags = new ArrayList<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length) {
                tags.add(Tag.of(keyValues[i], keyValues[i + 1]));
            }
        }
        return tags;
    }
}
