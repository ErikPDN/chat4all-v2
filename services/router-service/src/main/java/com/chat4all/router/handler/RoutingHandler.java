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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routing Handler (T046, T078)
 * 
 * Determines the target connector based on the message's channel type
 * and delegates delivery to the appropriate connector.
 * 
 * User Story 4: Multi-Recipient Delivery (Fan-out)
 * - Supports GROUP conversations with multiple recipients
 * - Routes message to appropriate connector for each recipient
 * - Handles partial failures (one recipient fails, others succeed)
 * 
 * Routing Logic:
 * - WHATSAPP → WhatsApp Connector
 * - TELEGRAM → Telegram Connector
 * - INSTAGRAM → Instagram Connector
 * - INTERNAL → Skip external delivery (internal messages only)
 * 
 * Flow:
 * 1. Check if message is for multiple recipients (GROUP conversation)
 * 2. For each recipient, determine target connector URL based on channel
 * 3. Delegate to RetryHandler for resilient delivery
 * 4. Update message status based on delivery result (partial or full success)
 * 5. Publish status update to Kafka
 * 
 * @author Chat4All Team
 * @version 2.0.0
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
     * Routes a message to the appropriate connector(s) based on its channel and recipients.
     * 
     * This is the main entry point for message routing logic.
     * Supports both single-recipient (ONE_TO_ONE) and multi-recipient (GROUP) delivery.
     * 
     * Task: T078 - Multi-recipient delivery support
     * 
     * @param messageEvent The message event to route
     */
    public void routeMessage(MessageEvent messageEvent) {
        log.info("Routing message: messageId={}, channel={}, conversationId={}, recipients={}",
                messageEvent.getMessageId(),
                messageEvent.getChannel(),
                messageEvent.getConversationId(),
                messageEvent.getRecipientIds() != null ? messageEvent.getRecipientIds().size() : 0);

        try {
            // Check if this is a multi-recipient message (GROUP conversation)
            if (isMultiRecipientMessage(messageEvent)) {
                log.info("Multi-recipient message detected: {} recipients", 
                    messageEvent.getRecipientIds().size());
                routeMultiRecipientMessage(messageEvent);
            } else {
                // Single recipient - original routing logic
                routeSingleRecipientMessage(messageEvent);
            }

        } catch (Exception e) {
            log.error("Error routing message {}: {}", messageEvent.getMessageId(), e.getMessage(), e);
            updateMessageStatus(messageEvent, MessageStatus.FAILED);
        }
    }

    /**
     * Checks if a message should be delivered to multiple recipients.
     * 
     * @param messageEvent The message event
     * @return true if message has multiple recipients
     */
    private boolean isMultiRecipientMessage(MessageEvent messageEvent) {
        return messageEvent.getRecipientIds() != null && 
               messageEvent.getRecipientIds().size() > 1;
    }

    /**
     * Routes a message to multiple recipients (GROUP conversation fan-out).
     * 
     * Business Rules:
     * - Each recipient may use a different channel (WhatsApp, Telegram, Instagram)
     * - Delivery failures for one recipient don't block others
     * - Overall status is DELIVERED if at least one recipient succeeds
     * - Overall status is FAILED only if ALL recipients fail
     * 
     * Task: T078
     * 
     * @param messageEvent The message event with multiple recipients
     */
    private void routeMultiRecipientMessage(MessageEvent messageEvent) {
        List<String> recipients = messageEvent.getRecipientIds();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        log.info("Starting fan-out delivery to {} recipients for message: {}", 
            recipients.size(), messageEvent.getMessageId());

        // Iterate over each recipient and attempt delivery
        for (String recipientId : recipients) {
            try {
                log.debug("Delivering to recipient: {} (channel: {})", 
                    recipientId, messageEvent.getChannel());

                // Determine connector URL for this recipient's channel
                // For MVP: All recipients use the same channel from the message
                // For Production: Would resolve per-recipient channel from user preferences
                String connectorUrl = getConnectorUrl(messageEvent.getChannel());

                if (connectorUrl == null) {
                    log.warn("No connector for channel: {} (recipient: {}), skipping", 
                        messageEvent.getChannel(), recipientId);
                    continue;
                }

                // Attempt delivery with retry logic
                boolean delivered = retryHandler.executeWithRetry(
                    () -> deliverToRecipient(messageEvent, recipientId, connectorUrl)
                );

                if (delivered) {
                    successCount.incrementAndGet();
                    log.info("✓ Delivery succeeded for recipient: {} (message: {})", 
                        recipientId, messageEvent.getMessageId());
                } else {
                    failureCount.incrementAndGet();
                    log.error("✗ Delivery failed for recipient: {} (message: {})", 
                        recipientId, messageEvent.getMessageId());
                }

            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("✗ Exception delivering to recipient: {} (message: {}): {}", 
                    recipientId, messageEvent.getMessageId(), e.getMessage(), e);
                // Continue with next recipient - don't let one failure block others
            }
        }

        // Determine overall message status based on partial/full success
        MessageStatus finalStatus = determineFinalStatus(successCount.get(), failureCount.get());
        
        log.info("Fan-out delivery completed: message={}, recipients={}, success={}, failed={}, finalStatus={}",
            messageEvent.getMessageId(), recipients.size(), 
            successCount.get(), failureCount.get(), finalStatus);

        updateMessageStatus(messageEvent, finalStatus);
    }

    /**
     * Routes a message to a single recipient (ONE_TO_ONE conversation).
     * 
     * Original routing logic for backward compatibility.
     * 
     * @param messageEvent The message event
     */
    private void routeSingleRecipientMessage(MessageEvent messageEvent) {
        log.info("Single-recipient message routing: messageId={}, channel={}",
            messageEvent.getMessageId(), messageEvent.getChannel());

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
        log.info("Delivering to {} connector for message: {}", 
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
    }

    /**
     * Delivers a message to a specific recipient.
     * 
     * @param messageEvent The message to deliver
     * @param recipientId The specific recipient ID
     * @param connectorUrl The connector service URL
     * @return true if delivery succeeded
     */
    private boolean deliverToRecipient(MessageEvent messageEvent, String recipientId, String connectorUrl) {
        log.info(">>> DELIVERING TO RECIPIENT <<<");
        log.info("    Message ID: {}", messageEvent.getMessageId());
        log.info("    Recipient ID: {}", recipientId);
        log.info("    Channel: {}", messageEvent.getChannel());
        log.info("    Connector URL: {}", connectorUrl);

        try {
            // Make actual HTTP call to connector
            // Pass recipientId context for connector to handle
            boolean success = connectorClient.deliverMessage(messageEvent, connectorUrl);
            
            log.info(">>> DELIVERY {} FOR RECIPIENT: {} <<<", 
                success ? "SUCCEEDED" : "FAILED", recipientId);
            return success;
            
        } catch (Exception e) {
            log.error(">>> DELIVERY FAILED FOR RECIPIENT: {} - ERROR: {} <<<", 
                recipientId, e.getMessage());
            throw new RuntimeException("Connector delivery failed for recipient: " + recipientId, e);
        }
    }

    /**
     * Determines the final message status based on partial delivery success.
     * 
     * Logic:
     * - All succeeded → DELIVERED
     * - Some succeeded, some failed → DELIVERED (partial success)
     * - All failed → FAILED
     * 
     * @param successCount Number of successful deliveries
     * @param failureCount Number of failed deliveries
     * @return Final message status
     */
    private MessageStatus determineFinalStatus(int successCount, int failureCount) {
        if (successCount > 0) {
            // At least one delivery succeeded
            if (failureCount > 0) {
                log.warn("Partial delivery success: {} succeeded, {} failed", 
                    successCount, failureCount);
            }
            return MessageStatus.DELIVERED;
        } else {
            // All deliveries failed
            return MessageStatus.FAILED;
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
