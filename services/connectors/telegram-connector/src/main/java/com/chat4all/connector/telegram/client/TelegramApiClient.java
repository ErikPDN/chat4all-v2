package com.chat4all.connector.telegram.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Telegram Bot API Client
 * 
 * Realiza chamadas reais para a API oficial do Telegram Bot.
 * Documentação: https://core.telegram.org/bots/api
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class TelegramApiClient {

    private final WebClient webClient;
    private final String botToken;

    public TelegramApiClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.telegram.api-url}") String apiUrl,
            @Value("${app.telegram.bot-token}") String botToken) {
        this.botToken = botToken;
        this.webClient = webClientBuilder
                .baseUrl(apiUrl)
                .build();
        
        log.info("TelegramApiClient initialized with API URL: {}", apiUrl);
    }

    /**
     * Envia mensagem de texto para um chat do Telegram
     * 
     * @param chatId ID do chat (pode ser número ou @username)
     * @param text Texto da mensagem
     * @return Resposta da API do Telegram
     * @throws TelegramApiException em caso de erro na API
     */
    public TelegramSendMessageResponse sendMessage(String chatId, String text) {
        log.info("Sending message to Telegram API: chatId={}, textLength={}", chatId, text.length());

        TelegramSendMessageRequest request = TelegramSendMessageRequest.builder()
                .chatId(chatId)
                .text(text)
                .build();

        try {
            TelegramSendMessageResponse response = webClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Telegram API 4xx error: {}", errorBody);
                                        return Mono.error(new TelegramApiException(
                                                "Telegram API client error: " + errorBody,
                                                clientResponse.statusCode().value()
                                        ));
                                    })
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            serverResponse -> serverResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("Telegram API 5xx error: {}", errorBody);
                                        return Mono.error(new TelegramApiException(
                                                "Telegram API server error: " + errorBody,
                                                serverResponse.statusCode().value()
                                        ));
                                    })
                    )
                    .bodyToMono(TelegramSendMessageResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && response.isOk()) {
                log.info("Message sent successfully to Telegram: chatId={}, messageId={}", 
                        chatId, response.getResult().getMessageId());
                return response;
            } else {
                throw new TelegramApiException("Telegram API returned ok=false", 500);
            }

        } catch (WebClientResponseException e) {
            log.error("Telegram API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new TelegramApiException(
                    "Failed to send message to Telegram: " + e.getMessage(),
                    e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling Telegram API", e);
            throw new TelegramApiException("Unexpected error: " + e.getMessage(), 500);
        }
    }

    /**
     * Request DTO para sendMessage
     */
    @Data
    @Builder
    public static class TelegramSendMessageRequest {
        @JsonProperty("chat_id")
        private String chatId;
        
        private String text;
    }

    /**
     * Response DTO da API do Telegram
     */
    @Data
    public static class TelegramSendMessageResponse {
        private boolean ok;
        private TelegramMessage result;
        private String description;
        
        @JsonProperty("error_code")
        private Integer errorCode;
    }

    /**
     * Message object retornado pela API
     */
    @Data
    public static class TelegramMessage {
        @JsonProperty("message_id")
        private Long messageId;
        
        private TelegramChat chat;
        private Long date;
        private String text;
    }

    /**
     * Chat object
     */
    @Data
    public static class TelegramChat {
        private Long id;
        private String type;
        
        @JsonProperty("first_name")
        private String firstName;
        
        @JsonProperty("last_name")
        private String lastName;
        
        private String username;
    }

    /**
     * Exception customizada para erros da API do Telegram
     */
    public static class TelegramApiException extends RuntimeException {
        private final int statusCode;

        public TelegramApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
