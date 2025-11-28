package com.chat4all.connector.instagram.service;

import com.chat4all.connector.instagram.dto.SendMessageRequest;
import com.chat4all.connector.instagram.dto.SendMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class InstagramService {

    @Value("${callback.base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    private final WebClient webClient;

    public InstagramService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        String instagramMessageId = "ig_" + UUID.randomUUID().toString();

        log.info("[Instagram] Simulating message delivery: messageId={}, instagramId={}",
            request.getMessageId(), instagramMessageId);

        sendReadStatusCallback(request.getMessageId(), instagramMessageId);

        return SendMessageResponse.builder()
            .messageId(request.getMessageId())
            .instagramMessageId(instagramMessageId)
            .status("SENT")
            .timestamp(Instant.now().toString())
            .build();
    }

    @Async
    public void sendReadStatusCallback(String messageId, String instagramMessageId) {
        try {
            Thread.sleep(2000);

            String callbackUrl = callbackBaseUrl + "/api/webhooks/instagram";

            log.info("[Instagram] Sending READ status callback: messageId={}, url={}",
                messageId, callbackUrl);

            String payload = String.format("""
                {
                  "messageId": "%s",
                  "instagramMessageId": "%s",
                  "status": "READ",
                  "timestamp": "%s",
                  "channel": "INSTAGRAM"
                }
                """, messageId, instagramMessageId, Instant.now().toString());

            webClient.post()
                .uri(callbackUrl)
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    response -> log.info("[Instagram] Callback sent successfully: messageId={}", messageId),
                    error -> log.error("[Instagram] Callback failed: messageId={}, error={}", 
                        messageId, error.getMessage())
                );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Instagram] Callback interrupted: messageId={}", messageId, e);
        } catch (Exception e) {
            log.error("[Instagram] Callback error: messageId={}", messageId, e);
        }
    }
}
