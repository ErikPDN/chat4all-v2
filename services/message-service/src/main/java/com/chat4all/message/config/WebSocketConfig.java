package com.chat4all.message.config;

import com.chat4all.message.websocket.MessageStatusWebSocketHandler;
import com.chat4all.message.websocket.WebSocketChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket Configuration for Real-Time Communication
 * 
 * Enables two WebSocket endpoints:
 * 
 * 1. /ws/messages - Message status updates (broadcast)
 *    - Real-time message status updates (PENDING → SENT → DELIVERED → READ)
 *    - Broadcast to all connected clients
 * 
 * 2. /ws/chat - Real-time chat message delivery (user-specific)
 *    - MESSAGE_CREATED events (new messages in conversations)
 *    - MESSAGE_RECEIVED events (inbound messages from external platforms)
 *    - User-specific delivery based on recipientIds
 *    - JWT authentication required
 * 
 * Architecture:
 * - Reactive WebSocket using Spring WebFlux
 * - User-specific message queues for /ws/chat
 * - Broadcast sink for /ws/messages
 * 
 * Example Client Usage (JavaScript):
 * ```javascript
 * // Status updates (broadcast)
 * const wsStatus = new WebSocket('ws://localhost:8081/ws/messages');
 * wsStatus.onmessage = (event) => {
 *   const update = JSON.parse(event.data);
 *   console.log('Status update:', update);
 * };
 * 
 * // Chat messages (user-specific, requires JWT)
 * const wsChat = new WebSocket('ws://localhost:8081/ws/chat?token=YOUR_JWT_TOKEN');
 * wsChat.onmessage = (event) => {
 *   const message = JSON.parse(event.data);
 *   console.log('New message:', message);
 * };
 * ```
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final MessageStatusWebSocketHandler messageStatusWebSocketHandler;
    private final WebSocketChatHandler webSocketChatHandler;

    /**
     * Maps WebSocket endpoints to handlers
     * 
     * Routes:
     * - /ws/messages -> MessageStatusWebSocketHandler (status updates, broadcast)
     * - /ws/chat -> WebSocketChatHandler (chat messages, user-specific)
     * 
     * @return HandlerMapping with WebSocket routes
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws/messages", messageStatusWebSocketHandler);
        map.put("/ws/chat", webSocketChatHandler);

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(1);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }

    /**
     * Adapter for WebSocket handler
     * 
     * @return WebSocketHandlerAdapter
     */
    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
