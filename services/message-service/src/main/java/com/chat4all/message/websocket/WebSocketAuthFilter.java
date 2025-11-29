package com.chat4all.message.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * WebSocket Authentication Filter
 * 
 * Extracts userId from WebSocket handshake and adds it to session attributes.
 * 
 * Authentication Methods (in priority order):
 * 1. Query parameter: ?userId=xxx (MVP approach)
 * 2. Header: X-User-Id: xxx (API Gateway forwarded header)
 * 3. JWT token: ?token=xxx (future enhancement)
 * 
 * Architecture:
 * - Runs before WebSocket upgrade handshake
 * - Extracts userId and adds to ServerWebExchange attributes
 * - WebSocketChatHandler reads userId from session attributes
 * 
 * Security Notes:
 * - MVP: Uses query parameter for simplicity (assumes API Gateway validates user)
 * - Production: Should validate JWT token and extract userId from claims
 * - Production: Should check token expiration and signature
 * 
 * Example Usage:
 * ```javascript
 * // MVP approach (userId in query param)
 * const ws = new WebSocket('ws://localhost:8081/ws/chat?userId=user123');
 * 
 * // Production approach (JWT token in query param)
 * const ws = new WebSocket('ws://localhost:8081/ws/chat?token=eyJhbGc...');
 * ```
 * 
 * Flow:
 * 1. Client connects to /ws/chat?userId=xxx
 * 2. This filter extracts userId from query parameter
 * 3. Adds userId to ServerWebExchange attributes
 * 4. WebSocket handler reads userId from session.getAttributes().get("userId")
 * 
 * TODO: Production Security Enhancements
 * - Integrate with Spring Security OAuth2 Resource Server
 * - Validate JWT token signature using JwtDecoder
 * - Extract userId from JWT claims (sub or custom claim)
 * - Check token expiration
 * - Rate limiting per userId
 * 
 * @author Chat4All Team
 * @version 1.0.0 (MVP)
 */
@Slf4j
@Component
public class WebSocketAuthFilter implements WebFilter {

    /**
     * Filters WebSocket handshake requests to extract authentication information
     * 
     * @param exchange ServerWebExchange for the request
     * @param chain WebFilterChain to continue processing
     * @return Mono<Void> indicating completion
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Only process WebSocket chat connections
        if (!path.equals("/ws/chat")) {
            return chain.filter(exchange);
        }

        log.debug("Processing WebSocket authentication for path: {}", path);

        // Extract userId from multiple sources (priority order)
        String userId = extractUserId(request);

        if (userId == null || userId.isEmpty()) {
            log.warn("WebSocket connection rejected - no userId found in request: {}", path);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        log.info("WebSocket authentication successful: userId={}, path={}", userId, path);

        // Add userId to exchange attributes so WebSocket handler can access it
        exchange.getAttributes().put("userId", userId);

        return chain.filter(exchange);
    }

    /**
     * Extracts userId from request using multiple strategies
     * 
     * Priority order:
     * 1. Query parameter: ?userId=xxx
     * 2. Header: X-User-Id: xxx
     * 3. Header: Authorization: Bearer <token> (JWT, future enhancement)
     * 
     * @param request ServerHttpRequest
     * @return userId or null if not found
     */
    private String extractUserId(ServerHttpRequest request) {
        // Strategy 1: Query parameter (MVP approach)
        List<String> userIdParams = request.getQueryParams().get("userId");
        if (userIdParams != null && !userIdParams.isEmpty()) {
            String userId = userIdParams.get(0);
            log.debug("Extracted userId from query parameter: {}", userId);
            return userId;
        }

        // Strategy 2: X-User-Id header (API Gateway forwarded header)
        HttpHeaders headers = request.getHeaders();
        String userIdHeader = headers.getFirst("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            log.debug("Extracted userId from X-User-Id header: {}", userIdHeader);
            return userIdHeader;
        }

        // Strategy 3: JWT token (future enhancement)
        // TODO: Implement JWT validation
        // List<String> tokenParams = request.getQueryParams().get("token");
        // if (tokenParams != null && !tokenParams.isEmpty()) {
        //     String token = tokenParams.get(0);
        //     return validateJwtAndExtractUserId(token);
        // }

        log.warn("No userId found in request - checked query params and headers");
        return null;
    }

    /**
     * Validates JWT token and extracts userId from claims
     * 
     * TODO: Implement for production
     * 
     * @param token JWT token string
     * @return userId from token claims or null if invalid
     */
    // private String validateJwtAndExtractUserId(String token) {
    //     try {
    //         Jwt jwt = jwtDecoder.decode(token);
    //         String userId = jwt.getClaimAsString("sub"); // or custom claim
    //         log.debug("Extracted userId from JWT token: {}", userId);
    //         return userId;
    //     } catch (Exception e) {
    //         log.error("JWT validation failed: {}", e.getMessage());
    //         return null;
    //     }
    // }
}
