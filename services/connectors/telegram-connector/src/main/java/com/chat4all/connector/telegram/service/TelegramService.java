package com.chat4all.connector.telegram.service;

import com.chat4all.connector.telegram.dto.SendMessageRequest;
import com.chat4all.connector.telegram.dto.SendMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class TelegramService {

    @Value("${callback.base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    private final WebClient webClient;

    public TelegramService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public SendMessageResponse sendMessage(SendMessageRequest request) {
        String telegramMessageId = "tg_" + UUID.randomUUID().toString();

        log.info("[Telegram] Simulating message delivery: messageId={}, telegramId={}",
            request.getMessageId(), telegramMessageId);

        sendReadStatusCallback(request.getMessageId(), telegramMessageId);

        return SendMessageResponse.builder()
            .messageId(request.getMessageId())
            .telegramMessageId(telegramMessageId)
            .status("SENT")
            .timestamp(Instant.now().toString())
            .build();
    }

    @Async
    public void sendReadStatusCallback(String messageId, String telegramMessageId) {
        try {
            Thread.sleep(2000);

            String callbackUrl = callbackBaseUrl + "/api/webhooks/telegram";

            log.info("[Telegram] Sending READ status callback: messageId={}, url={}",
                messageId, callbackUrl);

            String payload = String.format("""
                {
                  "messageId": "%s",
                  "telegramMessageId": "%s",
                  "status": "READ",
                  "timestamp": "%s",
                  "channel": "TELEGRAM"
                }
                """, messageId, telegramMessageId, Instant.now().toString());

            webClient.post()
                .uri(callbackUrl)
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    response -> log.info("[Telegram] Callback sent successfully: messageId={}", messageId),
                    error -> log.error("[Telegram] Callback failed: messageId={}, error={}", 
                        messageId, error.getMessage())
                );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Telegram] Callback interrupted: messageId={}", messageId, e);
        } catch (Exception e) {
            log.error("[Telegram] Callback error: messageId={}", messageId, e);
        }
    }
}
