package com.chat4all.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Error Handler for API Gateway
 * 
 * Provides standardized error responses following RFC 7807 Problem Details format.
 * Ensures all errors return consistent JSON structure for better client handling.
 * 
 * Error response format:
 * {
 *   "type": "https://chat4all.com/errors/unauthorized",
 *   "title": "Unauthorized",
 *   "status": 401,
 *   "detail": "Invalid or missing JWT token",
 *   "instance": "/api/messages",
 *   "timestamp": "2025-11-23T10:00:00Z",
 *   "traceId": "550e8400-e29b-41d4-a716-446655440000"
 * }
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalErrorFilter implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * Handles all exceptions thrown during request processing.
     * 
     * Maps exceptions to appropriate HTTP status codes and generates
     * RFC 7807 Problem Details JSON responses.
     * 
     * @param exchange ServerWebExchange current request/response
     * @param ex Throwable exception that occurred
     * @return Mono<Void> completion signal
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Determine HTTP status code from exception
        HttpStatus status = determineHttpStatus(ex);
        
        // Extract trace ID from request headers (if present)
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null) {
            traceId = exchange.getRequest().getId();
        }

        // Build error response
        Map<String, Object> errorResponse = buildErrorResponse(
            status,
            ex.getMessage(),
            exchange.getRequest().getPath().value(),
            traceId
        );

        // Log the error
        logError(exchange, ex, status, traceId);

        // Set response status and content type
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Serialize error response to JSON
        byte[] responseBytes;
        try {
            responseBytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            responseBytes = "{\"error\":\"Internal server error\"}".getBytes(StandardCharsets.UTF_8);
        }

        // Write response
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(responseBytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Determines appropriate HTTP status code based on exception type.
     * 
     * @param ex Exception thrown
     * @return HttpStatus appropriate status code
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return ((ResponseStatusException) ex).getStatusCode();
        }

        // Map common exception types to status codes
        String exceptionName = ex.getClass().getSimpleName();
        return switch (exceptionName) {
            case "AccessDeniedException", "InsufficientAuthenticationException" -> HttpStatus.FORBIDDEN;
            case "AuthenticationException", "BadCredentialsException" -> HttpStatus.UNAUTHORIZED;
            case "NotFoundException" -> HttpStatus.NOT_FOUND;
            case "BadRequestException", "MethodArgumentNotValidException" -> HttpStatus.BAD_REQUEST;
            case "ServiceUnavailableException", "ConnectException", "TimeoutException" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Builds RFC 7807 Problem Details error response.
     * 
     * @param status HTTP status code
     * @param detail Error detail message
     * @param instance Request path where error occurred
     * @param traceId Trace ID for correlation
     * @return Map<String, Object> error response structure
     */
    private Map<String, Object> buildErrorResponse(
            HttpStatus status,
            String detail,
            String instance,
            String traceId) {
        
        Map<String, Object> response = new HashMap<>();
        
        // RFC 7807 required fields
        response.put("type", buildErrorTypeUri(status));
        response.put("title", status.getReasonPhrase());
        response.put("status", status.value());
        response.put("detail", sanitizeErrorMessage(detail));
        response.put("instance", instance);
        
        // Additional fields for debugging
        response.put("timestamp", Instant.now().toString());
        response.put("traceId", traceId);

        return response;
    }

    /**
     * Builds error type URI following RFC 7807 convention.
     * 
     * @param status HTTP status code
     * @return String error type URI
     */
    private String buildErrorTypeUri(HttpStatus status) {
        String errorType = switch (status) {
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "route-not-found";
            case BAD_REQUEST -> "bad-request";
            case SERVICE_UNAVAILABLE -> "service-unavailable";
            default -> "internal-server-error";
        };
        return "https://chat4all.com/errors/" + errorType;
    }

    /**
     * Sanitizes error messages to avoid exposing sensitive information.
     * 
     * @param message Raw error message
     * @return String sanitized message
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return "An error occurred while processing your request";
        }

        // Remove potentially sensitive information
        message = message.replaceAll("(?i)password[=:]\\s*\\S+", "password=***");
        message = message.replaceAll("(?i)token[=:]\\s*\\S+", "token=***");
        message = message.replaceAll("(?i)api[_-]?key[=:]\\s*\\S+", "api_key=***");

        // Truncate very long messages
        if (message.length() > 500) {
            message = message.substring(0, 497) + "...";
        }

        return message;
    }

    /**
     * Logs error with appropriate severity level.
     * 
     * @param exchange ServerWebExchange current request
     * @param ex Throwable exception
     * @param status HttpStatus response status
     * @param traceId String trace ID
     */
    private void logError(ServerWebExchange exchange, Throwable ex, HttpStatus status, String traceId) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        
        if (status.is5xxServerError()) {
            log.error("Server error [{}] {} {} - traceId: {} - {}", 
                status.value(), method, path, traceId, ex.getMessage(), ex);
        } else if (status.is4xxClientError()) {
            log.warn("Client error [{}] {} {} - traceId: {} - {}", 
                status.value(), method, path, traceId, ex.getMessage());
        } else {
            log.info("Request error [{}] {} {} - traceId: {} - {}", 
                status.value(), method, path, traceId, ex.getMessage());
        }
    }
}
