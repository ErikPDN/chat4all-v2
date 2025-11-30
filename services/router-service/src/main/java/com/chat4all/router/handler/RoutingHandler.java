package com.chat4all.router.handler;

import com.chat4all.common.constant.Channel;
import com.chat4all.common.constant.MessageStatus;
import com.chat4all.common.event.MessageEvent;
import com.chat4all.router.client.UserServiceClient;
import com.chat4all.router.connector.ConnectorClient;
import com.chat4all.router.dto.ExternalIdentityDTO;
import com.chat4all.router.dto.UserDTO;
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
 * User Story 5: Identity Resolution Integration
 * - Resolves internal user IDs to external platform identities
 * - Fan-out to multiple platforms for single user (WhatsApp + Telegram + Instagram)
 * - Handles UUID-based recipient IDs vs direct platform IDs
 * 
 * Routing Logic:
 * - WHATSAPP → WhatsApp Connector
 * - TELEGRAM → Telegram Connector
 * - INSTAGRAM → Instagram Connector
 * - INTERNAL → Skip external delivery (internal messages only)
 * 
 * Identity Resolution Flow:
 * 1. Check if recipientId is a UUID (internal user) or platform ID (direct)
 * 2. If UUID: Call User Service to resolve external identities
 * 3. Fan-out message to all linked platform identities
 * 4. If direct platform ID: Send directly to specified platform
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
    private final UserServiceClient userServiceClient;

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
     * Identity Resolution Logic:
     * - If recipientId is a UUID: Resolve to external identities via User Service
     *   → Fan-out to all linked platforms (WhatsApp + Telegram + Instagram)
     * - If recipientId is not a UUID: Treat as direct platform ID
     *   → Send directly to the specified channel
     * 
     * @param messageEvent The message to deliver
     * @param recipientId The specific recipient ID (either internal UUID or platform ID)
     * @param connectorUrl The connector service URL
     * @return true if delivery succeeded to at least one identity
     */
    private boolean deliverToRecipient(MessageEvent messageEvent, String recipientId, String connectorUrl) {
        log.info(">>> DELIVERING TO RECIPIENT <<<");
        log.info("    Message ID: {}", messageEvent.getMessageId());
        log.info("    Recipient ID: {}", recipientId);
        log.info("    Channel: {}", messageEvent.getChannel());
        log.info("    Connector URL: {}", connectorUrl);

        try {
            // Check if recipientId is a UUID (internal user ID)
            if (isUUID(recipientId)) {
                log.info("Recipient ID is UUID - resolving to external identities via User Service");
                return deliverToInternalUser(messageEvent, recipientId);
            } else {
                // Direct platform ID - send directly
                log.info("Recipient ID is direct platform ID - delivering directly");
                return deliverDirectly(messageEvent, recipientId, connectorUrl);
            }
            
        } catch (Exception e) {
            log.error(">>> DELIVERY FAILED FOR RECIPIENT: {} - ERROR: {} <<<", 
                recipientId, e.getMessage());
            throw new RuntimeException("Connector delivery failed for recipient: " + recipientId, e);
        }
    }

    /**
     * Delivers a message to an internal user by resolving their external identities.
     * 
     * Fan-out Strategy:
     * - Resolves user UUID to all linked platform identities
     * - Delivers to ALL identities (multi-platform delivery)
     * - Returns success if AT LEAST ONE identity delivery succeeds
     * 
     * @param messageEvent The message to deliver
     * @param userId Internal user UUID
     * @return true if at least one platform delivery succeeded
     */
    private boolean deliverToInternalUser(MessageEvent messageEvent, String userId) {
        log.info("Resolving internal user to external identities: userId={}", userId);
        
        try {
            // Call User Service to get external identities
            UserDTO user = userServiceClient.getUser(userId).block();
            
            if (user == null) {
                log.warn("User not found in User Service: userId={}", userId);
                return false;
            }
            
            List<ExternalIdentityDTO> identities = user.getExternalIdentities();
            
            if (identities == null || identities.isEmpty()) {
                log.warn("User has no linked external identities: userId={}, displayName={}", 
                    userId, user.getDisplayName());
                return false;
            }
            
            log.info("User has {} linked identities - fanning out message", identities.size());
            
            // Fan-out to all external identities
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            
            for (ExternalIdentityDTO identity : identities) {
                try {
                    log.info("Delivering to identity: platform={}, platformUserId={}", 
                        identity.getPlatform(), identity.getPlatformUserId());
                    
                    // Get connector URL for this platform
                    String connectorUrl = getConnectorUrl(identity.getPlatform());
                    
                    if (connectorUrl == null) {
                        log.warn("No connector for platform: {}, skipping", identity.getPlatform());
                        continue;
                    }
                    
                    // Deliver to this specific platform identity
                    boolean delivered = deliverDirectly(messageEvent, identity.getPlatformUserId(), connectorUrl);
                    
                    if (delivered) {
                        successCount.incrementAndGet();
                        log.info("✓ Delivered to {}: {}", identity.getPlatform(), identity.getPlatformUserId());
                    } else {
                        failureCount.incrementAndGet();
                        log.error("✗ Failed to deliver to {}: {}", identity.getPlatform(), identity.getPlatformUserId());
                    }
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("✗ Exception delivering to {}: {} - {}", 
                        identity.getPlatform(), identity.getPlatformUserId(), e.getMessage());
                    // Continue with other identities
                }
            }
            
            log.info("Identity fan-out completed: userId={}, total={}, success={}, failed={}", 
                userId, identities.size(), successCount.get(), failureCount.get());
            
            // Success if at least one identity delivery succeeded
            return successCount.get() > 0;
            
        } catch (Exception e) {
            log.error("Error resolving user identities: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Delivers a message directly to a platform-specific ID.
     * 
     * @param messageEvent The message to deliver
     * @param platformUserId The platform-specific user ID
     * @param connectorUrl The connector URL
     * @return true if delivery succeeded
     */
    private boolean deliverDirectly(MessageEvent messageEvent, String platformUserId, String connectorUrl) {
        log.debug("Direct delivery: platformUserId={}, connectorUrl={}", platformUserId, connectorUrl);
        
        try {
            boolean success = connectorClient.deliverMessage(messageEvent, connectorUrl);
            log.info(">>> DIRECT DELIVERY {} <<<", success ? "SUCCEEDED" : "FAILED");
            return success;
        } catch (Exception e) {
            log.error(">>> DIRECT DELIVERY FAILED: {} <<<", e.getMessage());
            throw new RuntimeException("Direct connector delivery failed", e);
        }
    }

    /**
     * Checks if a string is a valid UUID format.
     * 
     * @param value The string to check
     * @return true if the string matches UUID format
     */
    private boolean isUUID(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        
        // UUID format: 8-4-4-4-12 hexadecimal digits
        // Example: 550e8400-e29b-41d4-a716-446655440000
        String uuidRegex = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        return value.matches(uuidRegex);
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
