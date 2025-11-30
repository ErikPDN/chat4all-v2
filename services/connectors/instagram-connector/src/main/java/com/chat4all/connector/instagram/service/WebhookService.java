package com.chat4all.connector.instagram.service;

import com.chat4all.connector.instagram.dto.InboundMessageDTO;
import com.chat4all.connector.instagram.dto.InstagramWebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Instagram Webhook Service
 * 
 * Processes inbound webhook events from Instagram and forwards
 * messages to the Message Service.
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class WebhookService {

    @Value("${callback.base-url:http://localhost:8081}")
    private String messageServiceUrl;

    private final WebClient webClient;

    public WebhookService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Process Instagram webhook payload
     * 
     * Extracts messages from the webhook payload and forwards them
     * to the Message Service for processing.
     * 
     * @param payload Instagram webhook payload
     */
    public void processWebhook(InstagramWebhookPayload payload) {
        if (payload == null || payload.getEntry() == null) {
            log.warn("[Instagram] Received empty webhook payload");
            return;
        }

        log.info("[Instagram] Processing webhook with {} entries", payload.getEntry().size());

        for (InstagramWebhookPayload.Entry entry : payload.getEntry()) {
            if (entry.getMessaging() == null) {
                continue;
            }

            for (InstagramWebhookPayload.Messaging messaging : entry.getMessaging()) {
                processMessagingEvent(messaging);
            }
        }
    }

    /**
     * Process individual messaging event
     * 
     * @param messaging Messaging event from webhook
     */
    private void processMessagingEvent(InstagramWebhookPayload.Messaging messaging) {
        if (messaging.getMessage() == null || messaging.getMessage().getText() == null) {
            log.debug("[Instagram] Skipping non-message event");
            return;
        }

        String senderId = messaging.getSender() != null ? messaging.getSender().getId() : null;
        String recipientId = messaging.getRecipient() != null ? messaging.getRecipient().getId() : null;
        String messageId = messaging.getMessage().getMid();
        String text = messaging.getMessage().getText();
        Long timestamp = messaging.getTimestamp();

        log.info("[Instagram] Processing inbound message: mid={}, from={}, text='{}'",
            messageId, senderId, text);

        // Build inbound message DTO
        InboundMessageDTO inboundMessage = InboundMessageDTO.builder()
            .senderId(senderId)
            .recipientId(recipientId)
            .instagramMessageId(messageId)
            .text(text)
            .timestamp(timestamp)
            .channel("INSTAGRAM")
            .build();

        // Forward to Message Service
        forwardToMessageService(inboundMessage);
    }

    /**
     * Forward inbound message to Message Service
     * 
     * Uses WebClient to POST the message to the Message Service webhook endpoint.
     * 
     * @param message Inbound message DTO
     */
    private void forwardToMessageService(InboundMessageDTO message) {
        String webhookUrl = messageServiceUrl + "/api/webhooks/instagram";

        log.info("[Instagram] Forwarding message to Message Service: url={}, messageId={}",
            webhookUrl, message.getInstagramMessageId());

        webClient.post()
            .uri(webhookUrl)
            .header("Content-Type", "application/json")
            .bodyValue(message)
            .retrieve()
            .bodyToMono(String.class)
            .doOnSuccess(response -> 
                log.info("[Instagram] Message forwarded successfully: messageId={}, response={}",
                    message.getInstagramMessageId(), response))
            .doOnError(error -> 
                log.error("[Instagram] Failed to forward message: messageId={}, error={}",
                    message.getInstagramMessageId(), error.getMessage()))
            .onErrorResume(error -> {
                // Log error but don't propagate - we already acknowledged receipt to Instagram
                return Mono.empty();
            })
            .subscribe();
    }
}
