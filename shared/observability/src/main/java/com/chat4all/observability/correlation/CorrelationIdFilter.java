package com.chat4all.observability.correlation;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebFilter that generates or extracts correlation IDs for distributed tracing.
 * 
 * <p>This filter ensures every request has a unique correlation ID that can be
 * traced across all microservices in the system. The correlation ID is:
 * <ul>
 *   <li>Extracted from the X-Correlation-ID header if present</li>
 *   <li>Generated as a new UUID if not present</li>
 *   <li>Added to the response headers</li>
 *   <li>Set in MDC (Mapped Diagnostic Context) for logging</li>
 *   <li>Propagated to downstream services via headers</li>
 * </ul>
 * 
 * <p>Constitutional Principle VI: Full-Stack Observability
 * 
 * @author Chat4All Team
 * @version 1.0
 * @since 2025-12-03
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    /**
     * Header name for correlation ID.
     * Standard: X-Correlation-ID (widely used in distributed systems)
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * MDC key for correlation ID in logs.
     * Allows automatic inclusion in all log statements.
     */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Filters incoming requests to ensure correlation ID is present.
     * 
     * <p>Flow:
     * <ol>
     *   <li>Extract or generate correlation ID</li>
     *   <li>Set in MDC for logging context</li>
     *   <li>Add to response headers for client tracking</li>
     *   <li>Propagate through filter chain</li>
     *   <li>Clean up MDC after request completes</li>
     * </ol>
     * 
     * @param exchange the current server exchange
     * @param chain the filter chain
     * @return Mono<Void> indicating when request handling is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Extract or generate correlation ID
        String correlationId = extractOrGenerateCorrelationId(exchange.getRequest());

        // Add correlation ID to response headers
        exchange.getResponse()
                .getHeaders()
                .add(CORRELATION_ID_HEADER, correlationId);

        // Set in MDC for logging (WebFlux uses Reactor Context, but MDC works for blocking calls)
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

        // Add correlation ID to Reactor Context for reactive logging
        return chain.filter(exchange)
                .contextWrite(context -> context.put(CORRELATION_ID_MDC_KEY, correlationId))
                .doFinally(signalType -> {
                    // Clean up MDC after request completes
                    MDC.remove(CORRELATION_ID_MDC_KEY);
                });
    }

    /**
     * Extracts correlation ID from request headers or generates a new one.
     * 
     * <p>Priority:
     * <ol>
     *   <li>X-Correlation-ID header from client/upstream service</li>
     *   <li>Generate new UUID v4 if not present</li>
     * </ol>
     * 
     * @param request the incoming HTTP request
     * @return correlation ID (never null)
     */
    private String extractOrGenerateCorrelationId(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        
        // Try to extract from headers
        String correlationId = headers.getFirst(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            // Generate new correlation ID
            correlationId = UUID.randomUUID().toString();
        }
        
        return correlationId;
    }
}
