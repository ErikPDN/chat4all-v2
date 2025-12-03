package com.chat4all.observability.correlation;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka ProducerInterceptor that propagates correlation IDs to Kafka message headers.
 * 
 * <p>This interceptor automatically adds the correlation ID from MDC (Mapped Diagnostic Context)
 * to outgoing Kafka messages, enabling end-to-end tracing across asynchronous message flows.
 * 
 * <p>Usage in KafkaProducerConfig:
 * <pre>
 * props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, CorrelationIdKafkaProducerInterceptor.class.getName());
 * </pre>
 * 
 * <p>Constitutional Principle VI: Full-Stack Observability
 * 
 * @author Chat4All Team
 * @version 1.0
 * @since 2025-12-03
 */
public class CorrelationIdKafkaProducerInterceptor implements ProducerInterceptor<Object, Object> {

    /**
     * Intercepts the record before sending to Kafka broker.
     * Adds correlation ID from MDC to message headers.
     * 
     * @param record the record to be sent
     * @return the record with correlation ID header added
     */
    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        // Get correlation ID from MDC
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        
        if (correlationId != null && !correlationId.isEmpty()) {
            // Add correlation ID to Kafka message headers
            Headers headers = record.headers();
            headers.add(
                CorrelationIdFilter.CORRELATION_ID_HEADER,
                correlationId.getBytes(StandardCharsets.UTF_8)
            );
        }
        
        return record;
    }

    /**
     * Called when record metadata is available (after successful send).
     * 
     * @param metadata the metadata for the record sent (i.e. partition, offset)
     * @param exception the exception thrown during processing of this record (null if no error)
     */
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // No action needed - could add metrics here if desired
    }

    /**
     * Called when the producer is closed.
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
}
