package com.chat4all.connector.instagram.api;

import com.chat4all.connector.instagram.dto.SendMessageRequest;
import com.chat4all.connector.instagram.dto.SendMessageResponse;
import com.chat4all.connector.instagram.service.InstagramService;
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

    private final InstagramService instagramService;

    @PostMapping("/messages")
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

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Instagram Connector is healthy");
    }
}
