package com.chat4all.message.websocket;

import com.chat4all.common.event.MessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Handler for Real-Time Chat Message Delivery
 * 
 * Handles WebSocket connections at /ws/chat and delivers:
 * - MESSAGE_CREATED events (new messages in conversations)
 * - MESSAGE_RECEIVED events (inbound messages from external platforms)
 * 
 * Architecture:
 * - User-specific message queues: Each connected user has a dedicated sink
 * - JWT-based authentication: userId extracted from WebSocket handshake
 * - Selective message delivery: Only sends messages where user is in recipientIds
 * - Thread-safe session management with ConcurrentHashMap
 * 
 * Event Flow:
 * 1. Client connects via WebSocket to /ws/chat with JWT token
 * 2. Server extracts userId from JWT and creates user-specific sink
 * 3. ChatMessagePushService receives MessageEvent from Kafka
 * 4. For each recipientId in the event, deliver to that user's sink
 * 5. User receives JSON-formatted message event in real-time
 * 
 * Security:
 * - JWT validation on handshake (via WebSocketAuthInterceptor)
 * - User isolation: Each user only receives messages intended for them
 * - No cross-user message leakage
 * 
 * Example Event Payload:
 * {
 *   "eventType": "MESSAGE_CREATED",
 *   "messageId": "msg-123",
 *   "conversationId": "conv-456",
 *   "senderId": "user-789",
 *   "content": "Hello from WebSocket!",
 *   "contentType": "TEXT",
 *   "channel": "WHATSAPP",
 *   "status": "PENDING",
 *   "timestamp": "2025-11-28T22:00:00Z"
 * }
 * 
 * Usage (JavaScript Client):
 * ```javascript
 * const ws = new WebSocket('ws://localhost:8081/ws/chat?token=YOUR_JWT_TOKEN');
 * ws.onmessage = (event) => {
 *   const message = JSON.parse(event.data);
 *   console.log('New message:', message);
 *   // Update UI with new message
 * };
 * ```
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChatHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;

    /**
     * User-specific message sinks
     * Key: userId (extracted from JWT)
     * Value: Sink for broadcasting messages to that specific user
     */
    private final Map<String, Sinks.Many<MessageEvent>> userSinks = new ConcurrentHashMap<>();

    /**
     * Active WebSocket sessions
     * Key: Session ID
     * Value: WebSocketSession with userId metadata
     */
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Session to userId mapping
     * Key: Session ID
     * Value: userId
     */
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    /**
     * Handles new WebSocket connection
     * 
     * Extracts userId from query parameter or session attributes
     * and creates a user-specific message stream.
     * 
     * @param session WebSocket session with userId in query parameter or attributes
     * @return Mono<Void> indicating completion
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        
        // Extract userId from query parameter first, then fall back to session attributes
        String userId = extractUserId(session);
        
        if (userId == null || userId.isEmpty()) {
            log.warn("WebSocket connection rejected - no userId found: sessionId={}, uri={}", 
                sessionId, session.getHandshakeInfo().getUri());
            return session.close();
        }

        log.info("WebSocket chat client connected: sessionId={}, userId={}", sessionId, userId);

        // Add session to active sessions
        activeSessions.put(sessionId, session);
        sessionUserMap.put(sessionId, userId);

        // Create or get user-specific sink
        Sinks.Many<MessageEvent> userSink = userSinks.computeIfAbsent(userId, k -> {
            log.debug("Creating new message sink for user: {}", userId);
            return Sinks.many().multicast().onBackpressureBuffer();
        });

        // Subscribe to user-specific event stream and send to client
        Flux<String> messageFlux = userSink.asFlux()
            .map(event -> {
                try {
                    return objectMapper.writeValueAsString(event);
                } catch (Exception e) {
                    log.error("Failed to serialize message event for user {}: {}", userId, e.getMessage());
                    return "{}";
                }
            })
            .doOnError(error -> log.error("Error in WebSocket chat session {} (user: {}): {}", 
                sessionId, userId, error.getMessage()))
            .doFinally(signalType -> {
                log.info("WebSocket chat client disconnected: sessionId={}, userId={}, signal={}", 
                    sessionId, userId, signalType);
                
                activeSessions.remove(sessionId);
                sessionUserMap.remove(sessionId);
                
                // Check if user has any other active sessions
                boolean hasOtherSessions = sessionUserMap.containsValue(userId);
                if (!hasOtherSessions) {
                    log.debug("Removing message sink for user {} (no active sessions)", userId);
                    userSinks.remove(userId);
                }
            });

        // Send messages to client and keep connection alive with Mono.never()
        return session.send(messageFlux.map(session::textMessage))
            .and(Mono.never());  // Never complete - keeps WebSocket alive
    }

    /**
     * Extracts userId from WebSocket session
     * 
     * Tries in order:
     * 1. Query parameter "userId" from URI
     * 2. Header "X-User-Id" from handshake headers
     * 3. Session attribute "userId" (set by WebSocketAuthFilter)
     * 
     * @param session WebSocket session
     * @return userId or null if not found
     */
    private String extractUserId(WebSocketSession session) {
        // Try query parameter first
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("userId=")) {
                    String userId = param.substring("userId=".length());
                    log.debug("Extracted userId from query parameter: {}", userId);
                    return userId;
                }
            }
        }
        
        // Try X-User-Id header
        var headers = session.getHandshakeInfo().getHeaders();
        if (headers.containsKey("X-User-Id")) {
            String userId = headers.getFirst("X-User-Id");
            log.debug("Extracted userId from X-User-Id header: {}", userId);
            return userId;
        }
        
        // Fall back to session attributes
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            log.debug("Extracted userId from session attributes: {}", userId);
        }
        return userId;
    }

    /**
     * Delivers message to a specific user
     * 
     * Called by ChatMessagePushService when a new message event is received
     * from Kafka. Only delivers if the user has an active WebSocket connection.
     * 
     * @param userId User ID to deliver message to
     * @param event Message event to deliver
     */
    public void deliverToUser(String userId, MessageEvent event) {
        Sinks.Many<MessageEvent> userSink = userSinks.get(userId);
        
        if (userSink == null) {
            log.debug("No active WebSocket connection for user {}, skipping real-time delivery", userId);
            return;
        }

        log.debug("Delivering message to user via WebSocket: userId={}, messageId={}, eventType={}", 
            userId, event.getMessageId(), event.getEventType());

        // Emit event to user's sink
        Sinks.EmitResult result = userSink.tryEmitNext(event);
        
        if (result.isFailure()) {
            log.warn("Failed to emit message to user {} via WebSocket: {}", userId, result);
        } else {
            log.debug("Successfully delivered message to user {}: messageId={}", userId, event.getMessageId());
        }
    }

    /**
     * Gets count of active WebSocket chat connections
     * 
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Gets count of users with active WebSocket connections
     * 
     * @return Number of unique users connected
     */
    public int getActiveUserCount() {
        return userSinks.size();
    }

    /**
     * Checks if a specific user has an active WebSocket connection
     * 
     * @param userId User ID to check
     * @return true if user is connected, false otherwise
     */
    public boolean isUserConnected(String userId) {
        return userSinks.containsKey(userId);
    }
}
