package com.chat4all.connector.whatsapp.api;

import com.chat4all.connector.whatsapp.dto.SendMessageRequest;
import com.chat4all.connector.whatsapp.dto.SendMessageResponse;
import com.chat4all.connector.whatsapp.service.WhatsAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * WhatsApp Connector Webhook Controller
 * 
 * Mock implementation of WhatsApp Business API endpoints.
 * 
 * Endpoints:
 * - POST /v1/messages - Send message via WhatsApp
 * - GET /health - Health check
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class WebhookController {

    private final WhatsAppService whatsAppService;

    /**
     * Send message via WhatsApp (mock)
     * 
     * Accepts message from Router Service and simulates WhatsApp delivery.
     * After 2 seconds, sends READ status callback to Message Service.
     * 
     * @param request Send message request
     * @return Send message response
     */
    @PostMapping("/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(
        @Valid @RequestBody SendMessageRequest request
    ) {
        log.info("[WhatsApp] Received message request: messageId={}, to={}, content='{}'",
            request.getMessageId(), request.getTo(), request.getContent());

        SendMessageResponse response = whatsAppService.sendMessage(request);

        log.info("[WhatsApp] Message sent successfully: messageId={}, whatsappId={}",
            response.getMessageId(), response.getWhatsappMessageId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Health check endpoint
     * 
     * @return OK status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("WhatsApp Connector is healthy");
    }
}
