package com.chat4all.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global logging filter for API Gateway.
 * 
 * Logs all incoming requests and outgoing responses for debugging and audit purposes.
 * Captures:
 * - HTTP method and path
 * - Request headers (excluding sensitive data)
 * - Response status code
 * - Request duration
 * - Correlation ID for distributed tracing
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    
    // Sensitive headers to exclude from logging
    private static final String[] SENSITIVE_HEADERS = {
        "authorization", 
        "cookie", 
        "set-cookie",
        "x-api-key",
        "x-auth-token"
    };
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();
        
        // Log request details
        logRequest(request);
        
        // Continue filter chain and log response
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    ServerHttpResponse response = exchange.getResponse();
                    long duration = System.currentTimeMillis() - startTime;
                    logResponse(request, response, duration);
                });
    }
    
    /**
     * Log incoming request details.
     */
    private void logRequest(ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        String queryParams = request.getURI().getQuery();
        String clientIp = getClientIp(request);
        String correlationId = getCorrelationId(request);
        
        // Build log message
        StringBuilder logMsg = new StringBuilder();
        logMsg.append("Incoming Request | ");
        logMsg.append("Method: ").append(method).append(" | ");
        logMsg.append("Path: ").append(path);
        
        if (queryParams != null && !queryParams.isEmpty()) {
            logMsg.append("?").append(queryParams);
        }
        
        logMsg.append(" | Client IP: ").append(clientIp);
        
        if (correlationId != null) {
            logMsg.append(" | Correlation ID: ").append(correlationId);
        }
        
        // Log headers (excluding sensitive ones)
        String headers = getSafeHeaders(request.getHeaders());
        if (!headers.isEmpty()) {
            logMsg.append(" | Headers: ").append(headers);
        }
        
        log.info(logMsg.toString());
    }
    
    /**
     * Log outgoing response details.
     */
    private void logResponse(ServerHttpRequest request, ServerHttpResponse response, long duration) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;
        String correlationId = getCorrelationId(request);
        
        // Build log message
        StringBuilder logMsg = new StringBuilder();
        logMsg.append("Outgoing Response | ");
        logMsg.append("Method: ").append(method).append(" | ");
        logMsg.append("Path: ").append(path).append(" | ");
        logMsg.append("Status: ").append(statusCode).append(" | ");
        logMsg.append("Duration: ").append(duration).append("ms");
        
        if (correlationId != null) {
            logMsg.append(" | Correlation ID: ").append(correlationId);
        }
        
        // Log level based on status code
        if (statusCode >= 500) {
            log.error(logMsg.toString());
        } else if (statusCode >= 400) {
            log.warn(logMsg.toString());
        } else {
            log.info(logMsg.toString());
        }
    }
    
    /**
     * Extract client IP address from request.
     * Checks X-Forwarded-For header first (for proxied requests).
     */
    private String getClientIp(ServerHttpRequest request) {
        // Check X-Forwarded-For header (set by load balancers/proxies)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        // Fallback to remote address
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * Extract correlation ID from request headers.
     * Used for distributed tracing across microservices.
     */
    private String getCorrelationId(ServerHttpRequest request) {
        // Try standard correlation ID headers
        String correlationId = request.getHeaders().getFirst("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = request.getHeaders().getFirst("X-Request-ID");
        }
        if (correlationId == null) {
            correlationId = request.getHeaders().getFirst("X-Trace-ID");
        }
        return correlationId;
    }
    
    /**
     * Get request headers excluding sensitive information.
     */
    private String getSafeHeaders(HttpHeaders headers) {
        StringBuilder safeHeaders = new StringBuilder();
        
        headers.forEach((name, values) -> {
            // Skip sensitive headers
            if (isSensitiveHeader(name)) {
                return;
            }
            
            // Skip empty values
            if (values == null || values.isEmpty()) {
                return;
            }
            
            if (safeHeaders.length() > 0) {
                safeHeaders.append(", ");
            }
            
            safeHeaders.append(name).append("=");
            
            // Join multiple values with semicolon
            if (values.size() == 1) {
                safeHeaders.append(values.get(0));
            } else {
                safeHeaders.append("[").append(String.join("; ", values)).append("]");
            }
        });
        
        return safeHeaders.toString();
    }
    
    /**
     * Check if header contains sensitive information.
     */
    private boolean isSensitiveHeader(String headerName) {
        String lowerCaseName = headerName.toLowerCase();
        for (String sensitiveHeader : SENSITIVE_HEADERS) {
            if (lowerCaseName.equals(sensitiveHeader)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public int getOrder() {
        return -2; // Execute before rate limiting filter (order -1)
    }
}
