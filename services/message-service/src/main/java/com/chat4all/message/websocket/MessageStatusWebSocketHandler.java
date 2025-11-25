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
 * WebSocket Handler for Real-Time Message Status Updates
 * 
 * Handles WebSocket connections at /ws/messages and broadcasts:
 * - MESSAGE_RECEIVED events (inbound messages from customers)
 * - MESSAGE_SENT events (outbound messages sent to platforms)
 * - MESSAGE_DELIVERED events (delivery confirmations)
 * - MESSAGE_READ events (read receipts)
 * - MESSAGE_FAILED events (delivery failures)
 * 
 * Architecture:
 * - Uses Reactor Sinks for broadcasting to all connected clients
 * - Thread-safe session management with ConcurrentHashMap
 * - JSON serialization for event payloads
 * 
 * Event Flow:
 * 1. Client connects via WebSocket to /ws/messages
 * 2. Server adds session to active sessions map
 * 3. Kafka consumer receives MessageEvent
 * 4. publishEvent() is called to broadcast to all sessions
 * 5. Client receives JSON-formatted event
 * 
 * Example Event Payload:
 * {
 *   "eventType": "MESSAGE_RECEIVED",
 *   "messageId": "msg-123",
 *   "conversationId": "conv-whatsapp-5551234567890",
 *   "status": "RECEIVED",
 *   "timestamp": "2025-11-24T22:00:00Z"
 * }
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageStatusWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;

    /**
     * Sink for broadcasting events to all WebSocket clients
     * Uses multicast to support multiple subscribers
     */
    private final Sinks.Many<MessageEvent> eventSink = Sinks.many()
        .multicast()
        .onBackpressureBuffer();

    /**
     * Active WebSocket sessions
     * Key: Session ID
     * Value: WebSocketSession
     */
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Handles new WebSocket connection
     * 
     * @param session WebSocket session
     * @return Mono<Void> indicating completion
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("WebSocket client connected: {}", sessionId);

        // Add session to active sessions
        activeSessions.put(sessionId, session);

        // Subscribe to event stream and send to client
        Flux<String> messageFlux = eventSink.asFlux()
            .map(event -> {
                try {
                    return objectMapper.writeValueAsString(event);
                } catch (Exception e) {
                    log.error("Failed to serialize event: {}", e.getMessage());
                    return "{}";
                }
            })
            .doOnError(error -> log.error("Error in WebSocket session {}: {}", sessionId, error.getMessage()))
            .doFinally(signalType -> {
                log.info("WebSocket client disconnected: {} (signal: {})", sessionId, signalType);
                activeSessions.remove(sessionId);
            });

        // Send messages to client
        return session.send(messageFlux.map(session::textMessage));
    }

    /**
     * Publishes message event to all connected WebSocket clients
     * 
     * Called by Kafka consumer when new events arrive
     * 
     * @param event Message event to broadcast
     */
    public void publishEvent(MessageEvent event) {
        log.debug("Publishing event to {} WebSocket clients: type={}, messageId={}", 
            activeSessions.size(), event.getEventType(), event.getMessageId());

        // Emit event to all subscribers
        Sinks.EmitResult result = eventSink.tryEmitNext(event);
        
        if (result.isFailure()) {
            log.warn("Failed to emit event to WebSocket clients: {}", result);
        }
    }

    /**
     * Gets count of active WebSocket connections
     * 
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
