package com.chat4all.connector.instagram.api;

import com.chat4all.connector.instagram.dto.InstagramWebhookPayload;
import com.chat4all.connector.instagram.dto.SendMessageRequest;
import com.chat4all.connector.instagram.dto.SendMessageResponse;
import com.chat4all.connector.instagram.service.InstagramService;
import com.chat4all.connector.instagram.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Instagram Connector Webhook Controller
 * 
 * Handles both outbound message sending and inbound webhook events from Instagram.
 * 
 * Endpoints:
 * - POST /v1/messages - Send message via Instagram
 * - GET /api/connectors/instagram/webhook - Webhook verification (Facebook requirement)
 * - POST /api/connectors/instagram/webhook - Receive inbound messages from Instagram
 * - GET /v1/health - Health check
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final InstagramService instagramService;
    private final WebhookService webhookService;
    
    @Value("${instagram.webhook.verify-token:chat4all-verify-token}")
    private String verifyToken;

    /**
     * Send message via Instagram (mock)
     * 
     * Accepts message from Router Service and simulates Instagram delivery.
     * After 2 seconds, sends READ status callback to Message Service.
     * 
     * @param request Send message request
     * @return Send message response
     */
    @PostMapping("/v1/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(
        @Valid @RequestBody SendMessageRequest request
    ) {
        log.info("[Instagram] Received message request: messageId={}, recipient={}, content='{}'",
            request.getMessageId(), request.getRecipient(), request.getContent());

        SendMessageResponse response = instagramService.sendMessage(request);

        log.info("[Instagram] Message sent successfully: messageId={}, instagramId={}",
            response.getMessageId(), response.getInstagramMessageId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Webhook verification endpoint (GET)
     * 
     * Facebook/Meta requires this endpoint for webhook setup verification.
     * When setting up webhooks in Facebook Developer Console, they send a GET request
     * with verification parameters.
     * 
     * @param mode Verification mode (should be "subscribe")
     * @param token Verification token (must match configured token)
     * @param challenge Challenge string to echo back
     * @return Challenge string if verification successful, 403 otherwise
     */
    @GetMapping("/api/connectors/instagram/webhook")
    public ResponseEntity<String> verifyWebhook(
        @RequestParam(name = "hub.mode") String mode,
        @RequestParam(name = "hub.verify_token") String token,
        @RequestParam(name = "hub.challenge") String challenge
    ) {
        log.info("[Instagram] Webhook verification request: mode={}, token={}", mode, token);

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("[Instagram] Webhook verification successful - returning challenge");
            return ResponseEntity.ok(challenge);
        } else {
            log.warn("[Instagram] Webhook verification failed - invalid token. Expected: {}, Got: {}", 
                verifyToken, token);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        }
    }

    /**
     * Webhook event receiver endpoint (POST)
     * 
     * Receives inbound messages and events from Instagram via Facebook webhook.
     * Processes the payload and forwards messages to Message Service.
     * 
     * @param payload Instagram webhook payload
     * @return HTTP 200 OK (Facebook requires 200 response within 20 seconds)
     */
    @PostMapping("/api/connectors/instagram/webhook")
    public ResponseEntity<String> receiveWebhook(
        @RequestBody InstagramWebhookPayload payload
    ) {
        log.info("[Instagram] Received webhook event: object={}, entries={}", 
            payload.getObject(), payload.getEntry() != null ? payload.getEntry().size() : 0);

        try {
            webhookService.processWebhook(payload);
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (Exception e) {
            log.error("[Instagram] Error processing webhook", e);
            // Still return 200 to acknowledge receipt (Facebook requirement)
            // The error is logged for investigation
            return ResponseEntity.ok("EVENT_RECEIVED");
        }
    }

    /**
     * Health check endpoint
     * 
     * @return OK status
     */
    @GetMapping("/v1/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Instagram Connector is healthy");
    }
}
