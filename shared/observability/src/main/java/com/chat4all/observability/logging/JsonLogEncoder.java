package com.chat4all.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Custom Logback encoder for structured JSON logging
 * 
 * Outputs logs in JSON format with standardized fields:
 * - timestamp (ISO-8601)
 * - level (INFO, WARN, ERROR, DEBUG, TRACE)
 * - service (from system property or environment variable)
 * - trace_id (from OpenTelemetry MDC)
 * - span_id (from OpenTelemetry MDC)
 * - message (formatted log message)
 * - logger (fully qualified class name)
 * - thread (thread name)
 * - Additional MDC fields (conversation_id, message_id, user_id, etc.)
 * - exception (if present, with stack trace)
 * 
 * Aligned with:
 * - Task T018
 * - Constitutional Principle VI (Full-Stack Observability)
 * - FR-036: Structured JSON logging requirement
 * - Research: ELK/Loki integration
 * 
 * Example output:
 * <pre>
 * {
 *   "timestamp": "2025-11-23T10:30:45.123Z",
 *   "level": "INFO",
 *   "service": "message-service",
 *   "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
 *   "span_id": "00f067aa0ba902b7",
 *   "message": "Message delivered successfully",
 *   "logger": "com.chat4all.message.service.MessageService",
 *   "thread": "http-nio-8080-exec-1",
 *   "message_id": "550e8400-e29b-41d4-a716-446655440000",
 *   "conversation_id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
 * }
 * </pre>
 */
public class JsonLogEncoder extends EncoderBase<ILoggingEvent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    private static final byte[] LINE_SEPARATOR = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
    
    private String serviceName;

    @Override
    public void start() {
        // Get service name from system property or environment variable
        this.serviceName = System.getProperty("spring.application.name");
        
        if (this.serviceName == null || this.serviceName.isEmpty()) {
            this.serviceName = System.getenv("SERVICE_NAME");
        }
        
        if (this.serviceName == null || this.serviceName.isEmpty()) {
            this.serviceName = "unknown-service";
        }
        
        super.start();
    }

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        try {
            ObjectNode logEntry = OBJECT_MAPPER.createObjectNode();
            
            // Standard fields
            logEntry.put("timestamp", ISO_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
            logEntry.put("level", event.getLevel().toString());
            logEntry.put("service", serviceName);
            logEntry.put("message", event.getFormattedMessage());
            logEntry.put("logger", event.getLoggerName());
            logEntry.put("thread", event.getThreadName());
            
            // OpenTelemetry trace context and custom MDC fields
            Map<String, String> mdcProperties = event.getMDCPropertyMap();
            if (mdcProperties != null && !mdcProperties.isEmpty()) {
                // OpenTelemetry trace context
                addMdcField(logEntry, mdcProperties, "trace_id");
                addMdcField(logEntry, mdcProperties, "span_id");
                addMdcField(logEntry, mdcProperties, "trace_flags");
                
                // Business context fields
                addMdcField(logEntry, mdcProperties, "conversation_id");
                addMdcField(logEntry, mdcProperties, "message_id");
                addMdcField(logEntry, mdcProperties, "user_id");
                addMdcField(logEntry, mdcProperties, "correlation_id");
                addMdcField(logEntry, mdcProperties, "request_id");
                addMdcField(logEntry, mdcProperties, "channel");
                
                // Add any additional custom MDC fields not already added
                for (Map.Entry<String, String> entry : mdcProperties.entrySet()) {
                    String key = entry.getKey();
                    if (!logEntry.has(key) && entry.getValue() != null) {
                        logEntry.put(key, entry.getValue());
                    }
                }
            }
            
            // Exception stack trace if present
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            if (throwableProxy != null) {
                ObjectNode exception = OBJECT_MAPPER.createObjectNode();
                exception.put("class", throwableProxy.getClassName());
                exception.put("message", throwableProxy.getMessage());
                
                // Stack trace as array of strings
                ArrayNode stackTrace = OBJECT_MAPPER.createArrayNode();
                StackTraceElementProxy[] stackTraceElements = throwableProxy.getStackTraceElementProxyArray();
                
                if (stackTraceElements != null) {
                    // Limit stack trace to first 20 elements to avoid huge logs
                    int limit = Math.min(stackTraceElements.length, 20);
                    for (int i = 0; i < limit; i++) {
                        stackTrace.add(stackTraceElements[i].getSTEAsString());
                    }
                    
                    if (stackTraceElements.length > 20) {
                        stackTrace.add("... " + (stackTraceElements.length - 20) + " more");
                    }
                }
                
                exception.set("stack_trace", stackTrace);
                
                // Caused by (if present)
                IThrowableProxy cause = throwableProxy.getCause();
                if (cause != null) {
                    ObjectNode causedBy = OBJECT_MAPPER.createObjectNode();
                    causedBy.put("class", cause.getClassName());
                    causedBy.put("message", cause.getMessage());
                    exception.set("caused_by", causedBy);
                }
                
                logEntry.set("exception", exception);
            }
            
            // Markers if present (for filtering logs)
            @SuppressWarnings("deprecation")
            var marker = event.getMarker();
            if (marker != null) {
                logEntry.put("marker", marker.getName());
            }
            
            // Serialize to JSON
            String json = OBJECT_MAPPER.writeValueAsString(logEntry);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            
            // Append line separator
            byte[] result = new byte[jsonBytes.length + LINE_SEPARATOR.length];
            System.arraycopy(jsonBytes, 0, result, 0, jsonBytes.length);
            System.arraycopy(LINE_SEPARATOR, 0, result, jsonBytes.length, LINE_SEPARATOR.length);
            
            return result;
            
        } catch (IOException e) {
            // Fallback to simple format if JSON serialization fails
            String fallback = String.format(
                "{\"timestamp\":\"%s\",\"level\":\"%s\",\"service\":\"%s\",\"message\":\"%s\",\"error\":\"JSON encoding failed: %s\"}%s",
                Instant.now().toString(),
                event.getLevel(),
                serviceName,
                escapeJson(event.getFormattedMessage()),
                escapeJson(e.getMessage()),
                System.lineSeparator()
            );
            return fallback.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Ultimate fallback
            String fallback = String.format(
                "{\"timestamp\":\"%s\",\"level\":\"ERROR\",\"message\":\"Log encoding failed\"}%s",
                Instant.now().toString(),
                System.lineSeparator()
            );
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    /**
     * Helper method to safely add MDC field to JSON object
     */
    private void addMdcField(ObjectNode logEntry, Map<String, String> mdcProperties, String fieldName) {
        String value = mdcProperties.get(fieldName);
        if (value != null && !value.isEmpty()) {
            logEntry.put(fieldName, value);
        }
    }

    /**
     * Escape special JSON characters in strings
     */
    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * Set service name (for testing or manual configuration)
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
