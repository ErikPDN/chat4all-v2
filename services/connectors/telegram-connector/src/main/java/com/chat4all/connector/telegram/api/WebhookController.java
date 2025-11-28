package com.chat4all.connector.telegram.api;

import com.chat4all.connector.telegram.dto.SendMessageRequest;
import com.chat4all.connector.telegram.dto.SendMessageResponse;
import com.chat4all.connector.telegram.service.TelegramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class WebhookController {

    private final TelegramService telegramService;

    @PostMapping("/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(
        @Valid @RequestBody SendMessageRequest request
    ) {
        log.info("[Telegram] Received message request: messageId={}, chatId={}, content='{}'",
            request.getMessageId(), request.getChatId(), request.getContent());

        SendMessageResponse response = telegramService.sendMessage(request);

        log.info("[Telegram] Message sent successfully: messageId={}, telegramId={}",
            response.getMessageId(), response.getTelegramMessageId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Telegram Connector is healthy");
    }
}
