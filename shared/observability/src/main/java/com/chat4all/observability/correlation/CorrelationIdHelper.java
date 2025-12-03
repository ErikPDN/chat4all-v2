package com.chat4all.observability.correlation;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Utility class for managing correlation IDs across the application.
 * 
 * <p>Provides helper methods for:
 * <ul>
 *   <li>Getting or generating correlation IDs</li>
 *   <li>Setting correlation ID in MDC</li>
 *   <li>Extracting correlation ID from Kafka headers</li>
 *   <li>Cleaning up correlation ID from MDC</li>
 * </ul>
 * 
 * <p>Usage in Kafka listeners:
 * <pre>
 * {@literal @}KafkaListener(topics = "chat-events")
 * public void handleMessage(ConsumerRecord<String, String> record) {
 *     String correlationId = CorrelationIdHelper.extractFromKafkaHeaders(record.headers());
 *     CorrelationIdHelper.setCorrelationId(correlationId);
 *     try {
 *         // Process message
 *     } finally {
 *         CorrelationIdHelper.clear();
 *     }
 * }
 * </pre>
 * 
 * <p>Constitutional Principle VI: Full-Stack Observability
 * 
 * @author Chat4All Team
 * @version 1.0
 * @since 2025-12-03
 */
public final class CorrelationIdHelper {

    private CorrelationIdHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the current correlation ID from MDC or generates a new one.
     * 
     * <p>This method:
     * <ol>
     *   <li>Checks MDC for existing correlation ID</li>
     *   <li>Returns existing ID if found</li>
     *   <li>Generates new UUID if not found</li>
     *   <li>Sets new ID in MDC before returning</li>
     * </ol>
     * 
     * @return correlation ID (never null)
     */
    public static String getOrGenerate() {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);
        }
        
        return correlationId;
    }

    /**
     * Gets the current correlation ID from MDC without generating a new one.
     * 
     * @return correlation ID or null if not set
     */
    public static String get() {
        return MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    }

    /**
     * Sets the correlation ID in MDC.
     * 
     * <p>Use this when extracting correlation ID from Kafka headers or other sources.
     * 
     * @param correlationId the correlation ID to set (null will be ignored)
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);
        }
    }

    /**
     * Removes correlation ID from MDC.
     * 
     * <p>Call this in finally blocks to prevent correlation ID leakage
     * between async tasks or thread reuse.
     */
    public static void clear() {
        MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    }

    /**
     * Extracts correlation ID from Kafka message headers.
     * 
     * <p>Usage:
     * <pre>
     * String correlationId = CorrelationIdHelper.extractFromKafkaHeaders(record.headers());
     * CorrelationIdHelper.setCorrelationId(correlationId);
     * </pre>
     * 
     * @param headers Kafka message headers
     * @return correlation ID or null if not found
     */
    public static String extractFromKafkaHeaders(Headers headers) {
        if (headers == null) {
            return null;
        }

        Header correlationHeader = headers.lastHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        
        if (correlationHeader != null && correlationHeader.value() != null) {
            return new String(correlationHeader.value(), StandardCharsets.UTF_8);
        }
        
        return null;
    }

    /**
     * Adds correlation ID to Kafka message headers.
     * 
     * <p>Usage in Kafka producers:
     * <pre>
     * ProducerRecord<String, String> record = new ProducerRecord<>("topic", key, value);
     * CorrelationIdHelper.addToKafkaHeaders(record.headers());
     * kafkaTemplate.send(record);
     * </pre>
     * 
     * @param headers Kafka message headers
     */
    public static void addToKafkaHeaders(Headers headers) {
        if (headers == null) {
            return;
        }

        String correlationId = getOrGenerate();
        headers.add(
            CorrelationIdFilter.CORRELATION_ID_HEADER,
            correlationId.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Executes a runnable with the given correlation ID set in MDC.
     * Automatically cleans up MDC after execution.
     * 
     * <p>Usage:
     * <pre>
     * CorrelationIdHelper.withCorrelationId(correlationId, () -> {
     *     // Code that needs correlation ID in logs
     *     logger.info("Processing message");
     * });
     * </pre>
     * 
     * @param correlationId the correlation ID to set
     * @param runnable the code to execute
     */
    public static void withCorrelationId(String correlationId, Runnable runnable) {
        String previousCorrelationId = get();
        try {
            setCorrelationId(correlationId);
            runnable.run();
        } finally {
            if (previousCorrelationId != null) {
                setCorrelationId(previousCorrelationId);
            } else {
                clear();
            }
        }
    }
}
