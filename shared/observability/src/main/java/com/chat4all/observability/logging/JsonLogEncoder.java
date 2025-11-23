package com.chat4all.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * JsonLogEncoder
 * Custom Logback encoder for structured JSON logging
 * 
 * Aligned with:
 * - Constitutional Principle VI: Full-stack observability (NON-NEGOTIABLE)
 * - FR-036: Structured JSON logging for all services
 * - Task T018
 * 
 * Output format:
 * <pre>
 * {
 *   "timestamp": "2025-11-23T10:30:45.123Z",
 *   "level": "INFO",
 *   "service": "message-service",
 *   "trace_id": "550e8400-e29b-41d4-a716-446655440000",
 *   "span_id": "e29b41d4a716",
 *   "message": "Message sent successfully",
 *   "logger": "com.chat4all.message.service.MessageService",
 *   "thread": "reactor-http-nio-2",
 *   "context": {
 *     "user_id": "user-123",
 *     "conversation_id": "conv-456"
 *   },
 *   "exception": {
 *     "class": "java.lang.RuntimeException",
 *     "message": "Connection timeout",
 *     "stacktrace": "..."
 *   }
 * }
 * </pre>
 * 
 * Usage in logback-spring.xml:
 * <pre>
 * {@code
 * <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
 *     <encoder class="com.chat4all.observability.logging.JsonLogEncoder">
 *         <serviceName>message-service</serviceName>
 *     </encoder>
 * </appender>
 * }
 * </pre>
 * 
 * MDC keys for distributed tracing:
 * - trace_id: OpenTelemetry trace ID (injected by OpenTelemetry Java Agent)
 * - span_id: OpenTelemetry span ID
 * - user_id: Current user ID (application-set)
 * - conversation_id: Current conversation ID (application-set)
 * - message_id: Current message ID (application-set)
 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {

    private static final DateTimeFormatter ISO_FORMATTER = 
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private static final byte[] LINE_SEPARATOR = System.lineSeparator().getBytes(StandardCharsets.UTF_8);

    private String serviceName = "chat4all-service";

    /**
     * Set the service name for log entries
     * Configured via logback-spring.xml
     * 
     * @param serviceName the service name (e.g., "message-service", "router-service")
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        try {
            ObjectNode json = OBJECT_MAPPER.createObjectNode();
            
            // Core fields (always present)
            json.put("timestamp", formatTimestamp(event.getTimeStamp()));
            json.put("level", event.getLevel().toString());
            json.put("service", serviceName);
            json.put("message", event.getFormattedMessage());
            json.put("logger", event.getLoggerName());
            json.put("thread", event.getThreadName());
            
            // Distributed tracing fields (from MDC)
            addTracingFields(json);
            
            // Application context (from MDC)
            addContextFields(json);
            
            // Exception details (if present)
            if (event.getThrowableProxy() != null) {
                addExceptionFields(json, event);
            }
            
            // Convert to JSON string and append newline
            String jsonString = OBJECT_MAPPER.writeValueAsString(json);
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            
            // Concatenate JSON + line separator
            byte[] output = new byte[jsonBytes.length + LINE_SEPARATOR.length];
            System.arraycopy(jsonBytes, 0, output, 0, jsonBytes.length);
            System.arraycopy(LINE_SEPARATOR, 0, output, jsonBytes.length, LINE_SEPARATOR.length);
            
            return output;
            
        } catch (IOException e) {
            // Fallback to plain text if JSON encoding fails
            String fallback = String.format("%s [%s] %s - %s%n",
                formatTimestamp(event.getTimeStamp()),
                event.getLevel(),
                event.getLoggerName(),
                event.getFormattedMessage());
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    /**
     * Format timestamp to ISO-8601 UTC format
     * 
     * @param timestampMillis epoch milliseconds
     * @return ISO-8601 formatted timestamp (e.g., "2025-11-23T10:30:45.123Z")
     */
    private String formatTimestamp(long timestampMillis) {
        return ISO_FORMATTER.format(Instant.ofEpochMilli(timestampMillis));
    }

    /**
     * Add distributed tracing fields from MDC
     * 
     * MDC keys:
     * - trace_id: OpenTelemetry trace ID (hex format)
     * - span_id: OpenTelemetry span ID (hex format)
     * 
     * @param json JSON object to add fields to
     */
    private void addTracingFields(ObjectNode json) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc == null) {
            return;
        }
        
        // OpenTelemetry trace ID (128-bit hex)
        String traceId = mdc.get("trace_id");
        if (traceId != null && !traceId.isEmpty()) {
            json.put("trace_id", traceId);
        }
        
        // OpenTelemetry span ID (64-bit hex)
        String spanId = mdc.get("span_id");
        if (spanId != null && !spanId.isEmpty()) {
            json.put("span_id", spanId);
        }
    }

    /**
     * Add application-specific context fields from MDC
     * 
     * MDC keys:
     * - user_id: Current user ID
     * - conversation_id: Current conversation ID
     * - message_id: Current message ID
     * - channel: Current channel (WHATSAPP, TELEGRAM, etc.)
     * - Any other custom fields
     * 
     * @param json JSON object to add fields to
     */
    private void addContextFields(ObjectNode json) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc == null || mdc.isEmpty()) {
            return;
        }
        
        // Create context object for non-tracing fields
        ObjectNode context = OBJECT_MAPPER.createObjectNode();
        
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // Skip tracing fields (already added above)
            if ("trace_id".equals(key) || "span_id".equals(key)) {
                continue;
            }
            
            // Add to context object
            if (value != null && !value.isEmpty()) {
                context.put(key, value);
            }
        }
        
        // Only add context field if it has content
        if (context.size() > 0) {
            json.set("context", context);
        }
    }

    /**
     * Add exception details to JSON
     * 
     * @param json JSON object to add fields to
     * @param event logging event with exception
     */
    private void addExceptionFields(ObjectNode json, ILoggingEvent event) {
        ObjectNode exception = OBJECT_MAPPER.createObjectNode();
        
        var throwableProxy = event.getThrowableProxy();
        exception.put("class", throwableProxy.getClassName());
        exception.put("message", throwableProxy.getMessage());
        
        // Build stacktrace string
        StringBuilder stacktrace = new StringBuilder();
        stacktrace.append(throwableProxy.getClassName())
                  .append(": ")
                  .append(throwableProxy.getMessage())
                  .append("\n");
        
        for (var element : throwableProxy.getStackTraceElementProxyArray()) {
            stacktrace.append("\tat ")
                      .append(element.getStackTraceElement().toString())
                      .append("\n");
        }
        
        // Add cause chain if present
        var cause = throwableProxy.getCause();
        while (cause != null) {
            stacktrace.append("Caused by: ")
                      .append(cause.getClassName())
                      .append(": ")
                      .append(cause.getMessage())
                      .append("\n");
            
            for (var element : cause.getStackTraceElementProxyArray()) {
                stacktrace.append("\tat ")
                          .append(element.getStackTraceElement().toString())
                          .append("\n");
            }
            
            cause = cause.getCause();
        }
        
        exception.put("stacktrace", stacktrace.toString());
        
        json.set("exception", exception);
    }

    /**
     * Static helper method to set MDC context for correlation
     * 
     * Usage in application code:
     * <pre>
     * {@code
     * JsonLogEncoder.setContext("user_id", "user-123");
     * JsonLogEncoder.setContext("conversation_id", "conv-456");
     * log.info("Processing message");
     * JsonLogEncoder.clearContext();
     * }
     * </pre>
     * 
     * @param key context key
     * @param value context value
     */
    public static void setContext(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }

    /**
     * Clear all MDC context
     * Should be called in finally block or reactive cleanup
     */
    public static void clearContext() {
        MDC.clear();
    }

    /**
     * Remove specific MDC context key
     * 
     * @param key context key to remove
     */
    public static void removeContext(String key) {
        MDC.remove(key);
    }
}
