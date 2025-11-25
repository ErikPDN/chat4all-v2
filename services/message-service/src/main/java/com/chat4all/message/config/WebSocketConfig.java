package com.chat4all.message.config;

import com.chat4all.message.websocket.MessageStatusWebSocketHandler;
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
 * WebSocket Configuration for Real-Time Message Status Updates
 * 
 * Enables WebSocket endpoint at /ws/messages for:
 * - Real-time message status updates (PENDING → SENT → DELIVERED → READ)
 * - Inbound message notifications (MESSAGE_RECEIVED events)
 * 
 * Architecture:
 * - Reactive WebSocket using Spring WebFlux
 * - Clients connect to /ws/messages
 * - Server broadcasts MESSAGE_RECEIVED and STATUS_UPDATED events
 * 
 * Example Client Usage (JavaScript):
 * ```javascript
 * const ws = new WebSocket('ws://localhost:8081/ws/messages');
 * ws.onmessage = (event) => {
 *   const update = JSON.parse(event.data);
 *   console.log('Status update:', update);
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

    /**
     * Maps WebSocket endpoints to handlers
     * 
     * @return HandlerMapping with WebSocket routes
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/ws/messages", messageStatusWebSocketHandler);

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
