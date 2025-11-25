package com.chat4all.message.api;

import com.chat4all.common.constant.Channel;
import com.chat4all.message.api.dto.InboundMessageDTO;
import com.chat4all.message.domain.Message;
import com.chat4all.message.service.MessageService;
import com.chat4all.message.service.WebhookProcessorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook Controller for receiving messages from external platforms
 * 
 * Endpoints:
 * - POST /api/webhooks/{channel} - Receive inbound messages from WhatsApp/Telegram/Instagram
 * 
 * Security:
 * - Platform-specific signature validation (HMAC-SHA256 for WhatsApp/Instagram, Token for Telegram)
 * - Webhook URL must be registered with each platform
 * 
 * Flow:
 * 1. Validate webhook signature
 * 2. Transform platform payload to InboundMessageDTO
 * 3. Process message (persist, update conversation, publish event)
 * 4. Return HTTP 200 immediately (async processing)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookProcessorService webhookProcessor;
    private final MessageService messageService;

    /**
     * Generic webhook endpoint for all platforms
     * 
     * Endpoint: POST /api/webhooks/{channel}
     * 
     * Supported channels: whatsapp, telegram, instagram
     * 
     * Headers (platform-specific):
     * - WhatsApp: X-Hub-Signature-256 (HMAC-SHA256)
     * - Instagram: X-Hub-Signature (HMAC-SHA1)
     * - Telegram: X-Telegram-Bot-Api-Secret-Token
     * 
     * Example Request (WhatsApp):
     * POST /api/webhooks/whatsapp
     * X-Hub-Signature-256: sha256=abc123...
     * 
     * {
     *   "platformMessageId": "wamid.xxx",
     *   "from": "5551234567890",
     *   "text": "Hello!",
     *   "timestamp": "1637000000"
     * }
     * 
     * Response: HTTP 200 OK (immediately, processing happens async)
     * 
     * @param channel Platform channel (whatsapp, telegram, instagram)
     * @param signature Webhook signature from header (optional for dev)
     * @param rawPayload Platform-specific payload
     * @return Mono<ResponseEntity<Map>> HTTP 200 OK with status
     */
    @PostMapping("/{channel}")
    public Mono<ResponseEntity<Map<String, Object>>> receiveWebhook(
        @PathVariable String channel,
        @RequestHeader(value = "X-Hub-Signature-256", required = false) String whatsappSignature,
        @RequestHeader(value = "X-Hub-Signature", required = false) String instagramSignature,
        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String telegramToken,
        @RequestBody Map<String, Object> rawPayload
    ) {
        log.info("Webhook received from channel: {}", channel);
        log.debug("Webhook payload: {}", rawPayload);

        // Parse channel enum
        Channel platformChannel;
        try {
            platformChannel = Channel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid channel: {}", channel);
            return Mono.just(ResponseEntity
                .badRequest()
                .body(Map.of("error", "Invalid channel: " + channel)));
        }

        // Select appropriate signature based on channel
        String signature = switch (platformChannel) {
            case WHATSAPP -> whatsappSignature;
            case INSTAGRAM -> instagramSignature;
            case TELEGRAM -> telegramToken;
            default -> null;
        };

        // Validate signature
        return webhookProcessor.validateSignature(platformChannel, signature, new byte[0])
            .flatMap(isValid -> {
                if (!isValid) {
                    log.warn("Invalid webhook signature for channel: {}", channel);
                    return Mono.just(ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .<Map<String, Object>>body(Map.of("error", "Invalid signature")));
                }

                // Transform payload to standardized DTO
                return webhookProcessor.transformPayload(platformChannel, rawPayload)
                    .flatMap(inboundDto -> processInboundMessage(inboundDto, platformChannel))
                    .map(message -> ResponseEntity.ok(Map.<String, Object>of(
                        "status", "received",
                        "messageId", message.getMessageId(),
                        "conversationId", message.getConversationId()
                    )))
                    .onErrorResume(error -> {
                        log.error("Failed to process webhook from {}: {}", channel, error.getMessage(), error);
                        return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .<Map<String, Object>>body(Map.of("error", "Failed to process webhook")));
                    });
            });
    }

    /**
     * Typed webhook endpoint with DTO validation
     * 
     * Endpoint: POST /api/webhooks/{channel}/message
     * 
     * Accepts platform-agnostic InboundMessageDTO for easier testing/integration
     * 
     * Example:
     * POST /api/webhooks/whatsapp/message
     * {
     *   "platformMessageId": "wamid.xxx",
     *   "senderId": "5551234567890",
     *   "content": "Hello!",
     *   "channel": "WHATSAPP"
     * }
     * 
     * @param channel Platform channel
     * @param inboundDto Standardized inbound message DTO
     * @return Mono<ResponseEntity<Message>> HTTP 200 OK with persisted message
     */
    @PostMapping("/{channel}/message")
    public Mono<ResponseEntity<Message>> receiveTypedWebhook(
        @PathVariable String channel,
        @Valid @RequestBody InboundMessageDTO inboundDto
    ) {
        log.info("Typed webhook received from channel: {}", channel);

        // Parse channel enum
        Channel platformChannel;
        try {
            platformChannel = Channel.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid channel: {}", channel);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return processInboundMessage(inboundDto, platformChannel)
            .map(ResponseEntity::ok)
            .onErrorResume(error -> {
                log.error("Failed to process typed webhook from {}: {}", channel, error.getMessage(), error);
                return Mono.just(ResponseEntity.internalServerError().build());
            });
    }

    /**
     * Webhook verification endpoint (GET request)
     * 
     * Used by WhatsApp and Instagram to verify webhook URL during setup
     * 
     * Endpoint: GET /api/webhooks/{channel}?hub.mode=subscribe&hub.verify_token=...&hub.challenge=...
     * 
     * @param channel Platform channel
     * @param mode Verification mode (should be "subscribe")
     * @param verifyToken Verification token (configured in platform)
     * @param challenge Challenge string to echo back
     * @return HTTP 200 with challenge if valid, 403 otherwise
     */
    @GetMapping("/{channel}")
    public ResponseEntity<String> verifyWebhook(
        @PathVariable String channel,
        @RequestParam("hub.mode") String mode,
        @RequestParam("hub.verify_token") String verifyToken,
        @RequestParam("hub.challenge") String challenge
    ) {
        log.info("Webhook verification request from channel: {}", channel);

        // TODO: Validate verify_token against configured value
        // For now, accept all verification requests (dev mode)
        if ("subscribe".equals(mode)) {
            log.info("Webhook verified for channel: {}", channel);
            return ResponseEntity.ok(challenge);
        }

        log.warn("Invalid verification mode: {}", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Processes an inbound message (shared logic)
     * 
     * @param dto Inbound message DTO
     * @param channel Platform channel
     * @return Mono<Message> Persisted message
     */
    private Mono<Message> processInboundMessage(InboundMessageDTO dto, Channel channel) {
        log.debug("Processing inbound message: platformId={}, senderId={}", 
            dto.getPlatformMessageId(), dto.getSenderId());

        // Generate conversationId if not provided
        String conversationId = dto.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = webhookProcessor.generateConversationId(channel, dto.getSenderId());
        }

        // Use timestamp from payload or current time
        Instant timestamp = dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now();

        // Process inbound message
        return messageService.processInboundMessage(
            dto.getPlatformMessageId(),
            conversationId,
            dto.getSenderId(),
            dto.getContent(),
            dto.getChannel(),
            timestamp,
            dto.getMetadata(),
            channel
        );
    }
}
