package com.chat4all.router.handler;

import com.chat4all.common.constant.Channel;
import com.chat4all.common.constant.MessageStatus;
import com.chat4all.common.event.MessageEvent;
import com.chat4all.router.connector.ConnectorClient;
import com.chat4all.router.kafka.StatusUpdateProducer;
import com.chat4all.router.retry.RetryHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routing Handler (T046)
 * 
 * Determines the target connector based on the message's channel type
 * and delegates delivery to the appropriate connector.
 * 
 * Routing Logic:
 * - WHATSAPP → WhatsApp Connector
 * - TELEGRAM → Telegram Connector
 * - INSTAGRAM → Instagram Connector
 * - INTERNAL → Skip external delivery (internal messages only)
 * 
 * Flow:
 * 1. Determine target connector URL based on channel
 * 2. Delegate to RetryHandler for resilient delivery
 * 3. Update message status based on delivery result
 * 4. Publish status update to Kafka
 * 
 * For MVP: Simulates delivery by logging and updating status to DELIVERED
 * For Production: Will make actual HTTP calls to connector services
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoutingHandler {

    private final RetryHandler retryHandler;
    private final ConnectorClient connectorClient;
    private final StatusUpdateProducer statusUpdateProducer;

    // Connector URLs (in production, these would come from service discovery or config)
    private static final String WHATSAPP_CONNECTOR_URL = "http://localhost:8091";
    private static final String TELEGRAM_CONNECTOR_URL = "http://localhost:8092";
    private static final String INSTAGRAM_CONNECTOR_URL = "http://localhost:8093";

    /**
     * Routes a message to the appropriate connector based on its channel.
     * 
     * This is the main entry point for message routing logic.
     * 
     * @param messageEvent The message event to route
     */
    public void routeMessage(MessageEvent messageEvent) {
        log.info("Routing message: messageId={}, channel={}, conversationId={}",
                messageEvent.getMessageId(),
                messageEvent.getChannel(),
                messageEvent.getConversationId());

        try {
            // Step 1: Determine target connector
            String connectorUrl = getConnectorUrl(messageEvent.getChannel());
            
            if (connectorUrl == null) {
                log.warn("No connector configured for channel: {}. Skipping external delivery.", 
                        messageEvent.getChannel());
                // For INTERNAL channel, we don't deliver externally
                updateMessageStatus(messageEvent, MessageStatus.DELIVERED);
                return;
            }

            // Step 2: Attempt delivery with retry logic
            log.info("Simulating delivery to {} connector for message: {}", 
                    messageEvent.getChannel(), messageEvent.getMessageId());
            
            boolean delivered = retryHandler.executeWithRetry(
                () -> deliverMessage(messageEvent, connectorUrl)
            );

            // Step 3: Update status based on result
            if (delivered) {
                log.info("Message successfully delivered: messageId={}, channel={}", 
                        messageEvent.getMessageId(), messageEvent.getChannel());
                updateMessageStatus(messageEvent, MessageStatus.DELIVERED);
            } else {
                log.error("Message delivery failed after retries: messageId={}, channel={}", 
                        messageEvent.getMessageId(), messageEvent.getChannel());
                updateMessageStatus(messageEvent, MessageStatus.FAILED);
            }

        } catch (Exception e) {
            log.error("Error routing message {}: {}", messageEvent.getMessageId(), e.getMessage(), e);
            updateMessageStatus(messageEvent, MessageStatus.FAILED);
        }
    }

    /**
     * Delivers a message to the specified connector.
     * 
     * Makes actual HTTP POST to connector service.
     * 
     * @param messageEvent The message to deliver
     * @param connectorUrl The connector service URL
     * @return true if delivery succeeded, false otherwise
     */
    private boolean deliverMessage(MessageEvent messageEvent, String connectorUrl) {
        log.info(">>> DELIVERING TO CONNECTOR <<<");
        log.info("    Message ID: {}", messageEvent.getMessageId());
        log.info("    Channel: {}", messageEvent.getChannel());
        log.info("    Connector URL: {}", connectorUrl);
        log.info("    Content: {}", messageEvent.getContent());

        try {
            // Make actual HTTP call to connector
            boolean success = connectorClient.deliverMessage(messageEvent, connectorUrl);
            
            log.info(">>> DELIVERY {} <<<", success ? "SUCCEEDED" : "FAILED");
            return success;
            
        } catch (Exception e) {
            log.error(">>> DELIVERY FAILED WITH ERROR: {} <<<", e.getMessage());
            throw new RuntimeException("Connector delivery failed", e);
        }
    }

    /**
     * Determines the connector URL based on the message channel.
     * 
     * @param channel The message channel
     * @return Connector URL, or null if no external delivery needed
     */
    private String getConnectorUrl(Channel channel) {
        if (channel == null) {
            log.warn("Message channel is null, cannot determine connector");
            return null;
        }

        return switch (channel) {
            case WHATSAPP -> WHATSAPP_CONNECTOR_URL;
            case TELEGRAM -> TELEGRAM_CONNECTOR_URL;
            case INSTAGRAM -> INSTAGRAM_CONNECTOR_URL;
            case INTERNAL -> null; // No external delivery for internal messages
        };
    }

    /**
     * Updates the message status and publishes update to Kafka.
     * 
     * @param messageEvent The original message event
     * @param newStatus The new status to set
     */
    private void updateMessageStatus(MessageEvent messageEvent, MessageStatus newStatus) {
        log.info("Updating message status: messageId={}, oldStatus={}, newStatus={}",
                messageEvent.getMessageId(),
                messageEvent.getStatus(),
                newStatus);

        try {
            // Publish status update to Kafka (message-service will consume and update MongoDB)
            statusUpdateProducer.publishStatusUpdate(messageEvent.getMessageId(), newStatus);
            
            log.debug("Status update published to Kafka: messageId={}, status={}", 
                    messageEvent.getMessageId(), newStatus);

        } catch (Exception e) {
            log.error("Error publishing status update for message {}: {}", 
                    messageEvent.getMessageId(), e.getMessage(), e);
            // Non-critical failure - don't block routing
        }
    }
}
