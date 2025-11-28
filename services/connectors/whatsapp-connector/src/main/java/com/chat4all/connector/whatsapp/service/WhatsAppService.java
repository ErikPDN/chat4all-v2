package com.chat4all.connector.whatsapp.service;

import com.chat4all.connector.whatsapp.dto.SendMessageRequest;
import com.chat4all.connector.whatsapp.dto.SendMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

/**
 * WhatsApp Service
 * 
 * Mock implementation of WhatsApp Business API integration.
 * 
 * Features:
 * - Simulates message delivery
 * - Sends READ status callback after 2 seconds
 * - Generates mock WhatsApp message IDs
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class WhatsAppService {

    @Value("${callback.base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    private final WebClient webClient;

    public WhatsAppService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Send message via WhatsApp (mock)
     * 
     * Simulates WhatsApp message delivery and schedules READ status callback.
     * 
     * @param request Send message request
     * @return Send message response
     */
    public SendMessageResponse sendMessage(SendMessageRequest request) {
        // Generate mock WhatsApp message ID
        String whatsappMessageId = "wamid." + UUID.randomUUID().toString();

        log.info("[WhatsApp] Simulating message delivery: messageId={}, whatsappId={}",
            request.getMessageId(), whatsappMessageId);

        // Schedule async callback to simulate READ status
        sendReadStatusCallback(request.getMessageId(), whatsappMessageId);

        return SendMessageResponse.builder()
            .messageId(request.getMessageId())
            .whatsappMessageId(whatsappMessageId)
            .status("SENT")
            .timestamp(Instant.now().toString())
            .build();
    }

    /**
     * Send READ status callback to Message Service (async)
     * 
     * Simulates WhatsApp delivery receipt after 2 seconds.
     * 
     * @param messageId Internal message ID
     * @param whatsappMessageId WhatsApp message ID
     */
    @Async
    public void sendReadStatusCallback(String messageId, String whatsappMessageId) {
        try {
            // Wait 2 seconds to simulate delivery time
            Thread.sleep(2000);

            String callbackUrl = callbackBaseUrl + "/api/webhooks/whatsapp";

            log.info("[WhatsApp] Sending READ status callback: messageId={}, url={}",
                messageId, callbackUrl);

            // Build callback payload
            String payload = String.format("""
                {
                  "messageId": "%s",
                  "whatsappMessageId": "%s",
                  "status": "READ",
                  "timestamp": "%s",
                  "channel": "WHATSAPP"
                }
                """, messageId, whatsappMessageId, Instant.now().toString());

            // Send callback to Message Service
            webClient.post()
                .uri(callbackUrl)
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    response -> log.info("[WhatsApp] Callback sent successfully: messageId={}", messageId),
                    error -> log.error("[WhatsApp] Callback failed: messageId={}, error={}", 
                        messageId, error.getMessage())
                );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[WhatsApp] Callback interrupted: messageId={}", messageId, e);
        } catch (Exception e) {
            log.error("[WhatsApp] Callback error: messageId={}", messageId, e);
        }
    }
}
