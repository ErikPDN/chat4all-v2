package com.chat4all.router.connector;

import com.chat4all.common.event.MessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Connector Client Interface (T047)
 * 
 * HTTP client for communicating with external connector services.
 * 
 * Responsibilities:
 * - Make HTTP POST requests to connector services
 * - Handle HTTP errors and timeouts
 * - Support circuit breaker pattern (via Resilience4j)
 * - Return delivery success/failure status
 * 
 * Connector Service Contract:
 * - Endpoint: POST /v1/messages
 * - Request Body: { messageId, to/chatId/recipient, content, conversationId, senderId }
 * - Response: 202 ACCEPTED (success) or 4xx/5xx (failure)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * Delivers a message to the specified connector service.
     * 
     * Makes actual HTTP POST to connector /v1/messages endpoint.
     * 
     * @param messageEvent The message event to deliver
     * @param connectorUrl The base URL of the connector service
     * @return true if delivery succeeded (HTTP 202), false otherwise
     */
    public boolean deliverMessage(MessageEvent messageEvent, String connectorUrl) {
        log.debug("ConnectorClient.deliverMessage called");
        log.debug("  Message ID: {}", messageEvent.getMessageId());
        log.debug("  Connector URL: {}", connectorUrl);

        try {
            // Build request payload matching connector DTOs
            Map<String, Object> payload = buildConnectorRequest(messageEvent);

            // Make HTTP POST to connector
            WebClient webClient = webClientBuilder.baseUrl(connectorUrl).build();
            
            webClient
                .post()
                .uri("/v1/messages")
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .block();

            log.info("Connector delivery succeeded: messageId={}", messageEvent.getMessageId());
            return true;

        } catch (WebClientResponseException e) {
            log.error("Connector returned error: status={}, body={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error calling connector: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Builds the request payload for the connector service.
     * 
     * Maps MessageEvent to connector's SendMessageRequest format.
     * Field names vary by channel (to/chatId/recipient).
     * 
     * @param messageEvent The message event
     * @return Request map
     */
    private Map<String, Object> buildConnectorRequest(MessageEvent messageEvent) {
        Map<String, Object> request = new HashMap<>();
        
        request.put("messageId", messageEvent.getMessageId());
        request.put("content", messageEvent.getContent() != null ? messageEvent.getContent() : "");
        request.put("conversationId", messageEvent.getConversationId());
        request.put("senderId", messageEvent.getSenderId());
        
        // Extract recipient ID from the recipientIds list
        String recipientId;
        if (messageEvent.getRecipientIds() != null && !messageEvent.getRecipientIds().isEmpty()) {
            recipientId = messageEvent.getRecipientIds().get(0); // Use first recipient
        } else {
            // Fallback to conversationId for backward compatibility
            recipientId = messageEvent.getConversationId();
        }
        
        // Add channel-specific recipient field
        // WhatsApp uses "to", Telegram uses "chatId", Instagram uses "recipient"
        switch (messageEvent.getChannel()) {
            case WHATSAPP -> request.put("to", recipientId);
            case TELEGRAM -> request.put("chatId", recipientId);
            case INSTAGRAM -> request.put("recipient", recipientId);
            default -> request.put("to", recipientId);
        }
        
        return request;
    }

    /**
     * Validates connector credentials by calling health check endpoint.
     * 
     * Calls GET /v1/health on connector service.
     * 
     * @param connectorUrl The connector service URL
     * @return true if connector is healthy
     */
    public boolean validateConnector(String connectorUrl) {
        log.debug("ConnectorClient.validateConnector called");
        log.debug("  Connector URL: {}", connectorUrl);

        try {
            WebClient webClient = webClientBuilder.baseUrl(connectorUrl).build();
            
            String response = webClient
                .get()
                .uri("/v1/health")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            log.info("Connector health check succeeded: {}", response);
            return true;

        } catch (Exception e) {
            log.error("Connector health check failed: {}", e.getMessage());
            return false;
        }
    }
}
