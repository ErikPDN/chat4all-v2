package com.chat4all.message.service;

import com.chat4all.common.constant.Channel;
import com.chat4all.message.api.dto.InboundMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Service for processing inbound webhook messages from external platforms
 * 
 * Responsibilities:
 * - Validate webhook signatures (platform-specific)
 * - Transform platform-specific formats to internal InboundMessageDTO
 * - Validate message content and structure
 * - Generate conversation IDs from platform identifiers
 * 
 * Security:
 * - Each platform has different signature validation mechanisms:
 *   - WhatsApp: X-Hub-Signature-256 HMAC-SHA256
 *   - Telegram: Secret token validation
 *   - Instagram: X-Hub-Signature HMAC-SHA1
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessorService {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Validates webhook signature for security
     * 
     * Platform-specific validation:
     * - WhatsApp: HMAC-SHA256 with app secret
     * - Telegram: Token comparison
     * - Instagram: HMAC-SHA1 with app secret
     * 
     * Dev Mode: Signature validation is skipped if active profile is 'dev'
     * 
     * @param channel Platform channel
     * @param signature Signature header from webhook
     * @param payload Raw payload bytes
     * @return Mono<Boolean> true if signature is valid
     */
    public Mono<Boolean> validateSignature(Channel channel, String signature, byte[] payload) {
        log.debug("Validating webhook signature for channel: {}", channel);

        // Skip signature validation in dev mode for easier testing
        if ("dev".equalsIgnoreCase(activeProfile)) {
            log.warn("DEV MODE: Skipping webhook signature validation for channel: {}", channel);
            return Mono.just(true);
        }

        // TODO: Implement platform-specific signature validation
        // For now, accept all signatures (dev mode)
        // In production, this MUST validate against platform secrets
        
        if (signature == null || signature.isBlank()) {
            log.warn("Missing signature for webhook from channel: {}", channel);
            // In development, allow missing signatures
            return Mono.just(true);
        }

        // Placeholder validation logic
        switch (channel) {
            case WHATSAPP:
                return validateWhatsAppSignature(signature, payload);
            case TELEGRAM:
                return validateTelegramSignature(signature, payload);
            case INSTAGRAM:
                return validateInstagramSignature(signature, payload);
            default:
                log.error("Unsupported channel for signature validation: {}", channel);
                return Mono.just(false);
        }
    }

    /**
     * Transforms platform-specific webhook payload to standardized InboundMessageDTO
     * 
     * @param channel Platform channel
     * @param rawPayload Platform-specific payload
     * @return Mono<InboundMessageDTO> Standardized message DTO
     */
    public Mono<InboundMessageDTO> transformPayload(Channel channel, Map<String, Object> rawPayload) {
        log.debug("Transforming webhook payload for channel: {}", channel);

        return switch (channel) {
            case WHATSAPP -> transformWhatsAppPayload(rawPayload);
            case TELEGRAM -> transformTelegramPayload(rawPayload);
            case INSTAGRAM -> transformInstagramPayload(rawPayload);
            default -> {
                log.error("Unsupported channel for payload transformation: {}", channel);
                yield Mono.error(new IllegalArgumentException("Unsupported channel: " + channel));
            }
        };
    }

    /**
     * Generates conversation ID from platform identifiers
     * 
     * Format: conv-{channel}-{platformIdentifier}
     * Examples:
     * - WhatsApp: conv-whatsapp-5551234567890
     * - Telegram: conv-telegram-123456789
     * 
     * @param channel Platform channel
     * @param platformIdentifier Platform-specific conversation/user identifier
     * @return Generated conversation ID
     */
    public String generateConversationId(Channel channel, String platformIdentifier) {
        String conversationId = String.format("conv-%s-%s", 
            channel.name().toLowerCase(), 
            platformIdentifier);
        
        log.debug("Generated conversation ID: {} for platform: {}", conversationId, channel);
        return conversationId;
    }

    // ========== Platform-Specific Signature Validation ==========

    private Mono<Boolean> validateWhatsAppSignature(String signature, byte[] payload) {
        // TODO: Implement HMAC-SHA256 validation with WhatsApp app secret
        // Expected format: sha256=<hex_digest>
        log.debug("WhatsApp signature validation (TODO): {}", signature);
        return Mono.just(true); // Placeholder
    }

    private Mono<Boolean> validateTelegramSignature(String signature, byte[] payload) {
        // TODO: Implement Telegram secret token validation
        log.debug("Telegram signature validation (TODO): {}", signature);
        return Mono.just(true); // Placeholder
    }

    private Mono<Boolean> validateInstagramSignature(String signature, byte[] payload) {
        // TODO: Implement HMAC-SHA1 validation with Instagram app secret
        // Expected format: sha1=<hex_digest>
        log.debug("Instagram signature validation (TODO): {}", signature);
        return Mono.just(true); // Placeholder
    }

    // ========== Platform-Specific Payload Transformation ==========

    private Mono<InboundMessageDTO> transformWhatsAppPayload(Map<String, Object> payload) {
        try {
            log.debug("Transforming WhatsApp payload: {}", payload);

            // WhatsApp webhook structure (simplified):
            // {
            //   "entry": [{
            //     "changes": [{
            //       "value": {
            //         "messages": [{
            //           "id": "wamid.xxx",
            //           "from": "5551234567890",
            //           "timestamp": "1637000000",
            //           "text": { "body": "Hello!" }
            //         }]
            //       }
            //     }]
            //   }]
            // }

            String messageId = (String) payload.getOrDefault("id", "unknown");
            String from = (String) payload.getOrDefault("from", "unknown");
            String content = (String) payload.getOrDefault("text", "");
            
            // Safe timestamp conversion - handles both Number and String types
            Object timestampObj = payload.get("timestamp");
            Instant timestamp;
            if (timestampObj instanceof Number) {
                timestamp = Instant.ofEpochSecond(((Number) timestampObj).longValue());
            } else if (timestampObj instanceof String) {
                try {
                    // Try parsing as ISO-8601 first
                    timestamp = Instant.parse((String) timestampObj);
                } catch (Exception e) {
                    // If that fails, try parsing as epoch seconds
                    timestamp = Instant.ofEpochSecond(Long.parseLong((String) timestampObj));
                }
            } else {
                timestamp = Instant.now();
            }

            return Mono.just(InboundMessageDTO.builder()
                .platformMessageId(messageId)
                .conversationId(generateConversationId(Channel.WHATSAPP, from))
                .senderId(from)
                .senderName((String) payload.get("name"))
                .content(content)
                .channel(Channel.WHATSAPP)
                .timestamp(timestamp)
                .metadata(payload)
                .build());

        } catch (Exception e) {
            log.error("Failed to transform WhatsApp payload", e);
            return Mono.error(new IllegalArgumentException("Invalid WhatsApp payload format", e));
        }
    }

    private Mono<InboundMessageDTO> transformTelegramPayload(Map<String, Object> payload) {
        try {
            log.debug("Transforming Telegram payload: {}", payload);

            // Telegram webhook structure (simplified):
            // {
            //   "update_id": 123456789,
            //   "message": {
            //     "message_id": 123,
            //     "from": { "id": 987654321, "first_name": "John" },
            //     "chat": { "id": 987654321 },
            //     "text": "Hello!"
            //   }
            // }

            String messageId = String.valueOf(payload.getOrDefault("message_id", "unknown"));
            String userId = String.valueOf(payload.getOrDefault("from_id", "unknown"));
            String content = (String) payload.getOrDefault("text", "");
            String firstName = (String) payload.getOrDefault("first_name", "");

            return Mono.just(InboundMessageDTO.builder()
                .platformMessageId(messageId)
                .conversationId(generateConversationId(Channel.TELEGRAM, userId))
                .senderId(userId)
                .senderName(firstName)
                .content(content)
                .channel(Channel.TELEGRAM)
                .timestamp(Instant.now())
                .metadata(payload)
                .build());

        } catch (Exception e) {
            log.error("Failed to transform Telegram payload", e);
            return Mono.error(new IllegalArgumentException("Invalid Telegram payload format", e));
        }
    }

    private Mono<InboundMessageDTO> transformInstagramPayload(Map<String, Object> payload) {
        try {
            log.debug("Transforming Instagram payload: {}", payload);

            // Instagram webhook structure (simplified):
            // {
            //   "sender": { "id": "1234567890" },
            //   "recipient": { "id": "0987654321" },
            //   "message": {
            //     "mid": "mid.xxx",
            //     "text": "Hello!"
            //   }
            // }

            String messageId = (String) payload.getOrDefault("mid", "unknown");
            String senderId = (String) payload.getOrDefault("sender_id", "unknown");
            String content = (String) payload.getOrDefault("text", "");

            return Mono.just(InboundMessageDTO.builder()
                .platformMessageId(messageId)
                .conversationId(generateConversationId(Channel.INSTAGRAM, senderId))
                .senderId(senderId)
                .content(content)
                .channel(Channel.INSTAGRAM)
                .timestamp(Instant.now())
                .metadata(payload)
                .build());

        } catch (Exception e) {
            log.error("Failed to transform Instagram payload", e);
            return Mono.error(new IllegalArgumentException("Invalid Instagram payload format", e));
        }
    }
}
