package com.chat4all.observability.correlation;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka ConsumerInterceptor that extracts correlation IDs from Kafka message headers.
 * 
 * <p>This interceptor automatically extracts the correlation ID from incoming Kafka messages
 * and sets it in MDC (Mapped Diagnostic Context), enabling correlation across async boundaries.
 * 
 * <p>Usage in KafkaConsumerConfig:
 * <pre>
 * props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, CorrelationIdKafkaConsumerInterceptor.class.getName());
 * </pre>
 * 
 * <p>Constitutional Principle VI: Full-Stack Observability
 * 
 * @author Chat4All Team
 * @version 1.0
 * @since 2025-12-03
 */
public class CorrelationIdKafkaConsumerInterceptor implements ConsumerInterceptor<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdKafkaConsumerInterceptor.class);

    /**
     * Intercepts records before they are processed by the consumer.
     * Extracts correlation ID from the first record and sets in MDC.
     * 
     * <p>Note: This sets MDC for the consumer thread. Individual message listeners
     * should extract correlation ID from message headers if needed.
     * 
     * @param records the records to be processed
     * @return the same records (unmodified)
     */
    @Override
    public ConsumerRecords<Object, Object> onConsume(ConsumerRecords<Object, Object> records) {
        // Extract correlation ID from first record (if available)
        records.forEach(record -> {
            String correlationId = extractCorrelationId(record.headers().headers(CorrelationIdFilter.CORRELATION_ID_HEADER));
            if (correlationId != null) {
                MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId);
                logger.debug("Extracted correlation ID from Kafka message: {}", correlationId);
            }
        });
        
        return records;
    }

    /**
     * Called when offsets are committed.
     * 
     * @param offsets the offsets being committed
     */
    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // Clean up MDC after commit
        MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    }

    /**
     * Called when the consumer is closed.
     */
    @Override
    public void close() {
        // No resources to clean up
    }

    /**
     * Configure this class with the given key-value pairs.
     * 
     * @param configs configuration parameters
     */
    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration needed
    }

    /**
     * Extracts correlation ID from Kafka headers.
     * 
     * @param headers the headers to search
     * @return correlation ID or null if not found
     */
    private String extractCorrelationId(Iterable<Header> headers) {
        if (headers != null) {
            for (Header header : headers) {
                if (CorrelationIdFilter.CORRELATION_ID_HEADER.equals(header.key())) {
                    byte[] value = header.value();
                    if (value != null && value.length > 0) {
                        return new String(value, StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return null;
    }
}
