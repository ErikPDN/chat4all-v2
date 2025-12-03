package com.chat4all.connector.telegram.service;

import com.chat4all.connector.telegram.client.TelegramApiClient;
import com.chat4all.connector.telegram.dto.SendMessageRequest;
import com.chat4all.connector.telegram.dto.SendMessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Slf4j
@Service
public class TelegramService {

    @Value("${callback.base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    private final TelegramApiClient telegramApiClient;
    private final WebClient webClient;

    public TelegramService(TelegramApiClient telegramApiClient, WebClient.Builder webClientBuilder) {
        this.telegramApiClient = telegramApiClient;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Envia mensagem usando a API REAL do Telegram
     */
    public SendMessageResponse sendMessage(SendMessageRequest request) {
        try {
            log.info("[Telegram] Sending message via Telegram Bot API: messageId={}, chatId={}, contentLength={}",
                request.getMessageId(), request.getChatId(), request.getContent().length());

            // Chamada REAL para a API do Telegram
            TelegramApiClient.TelegramSendMessageResponse telegramResponse = 
                telegramApiClient.sendMessage(request.getChatId(), request.getContent());

            String telegramMessageId = String.valueOf(telegramResponse.getResult().getMessageId());

            log.info("[Telegram] Message sent successfully via Telegram API: messageId={}, telegramMessageId={}",
                request.getMessageId(), telegramMessageId);

            // Envia callback de READ status (simulando que o usuário leu a mensagem)
            sendReadStatusCallback(request.getMessageId(), telegramMessageId);

            return SendMessageResponse.builder()
                .messageId(request.getMessageId())
                .telegramMessageId(telegramMessageId)
                .status("SENT")
                .timestamp(Instant.now().toString())
                .build();

        } catch (TelegramApiClient.TelegramApiException e) {
            log.error("[Telegram] Failed to send message via Telegram API: messageId={}, error={}, statusCode={}",
                request.getMessageId(), e.getMessage(), e.getStatusCode());
            throw e; // Propaga o erro para que o router faça retry
        } catch (Exception e) {
            log.error("[Telegram] Unexpected error sending message: messageId={}", 
                request.getMessageId(), e);
            throw new RuntimeException("Failed to send Telegram message", e);
        }
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
