package com.chat4all.message.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 Configuration for Message Service
 * 
 * Generates interactive API documentation at:
 * - Swagger UI: http://localhost:8081/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8081/v3/api-docs
 * 
 * Task: T140 - Create API documentation using Springdoc OpenAPI
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:message-service}")
    private String applicationName;

    @Value("${server.port:8081}")
    private String serverPort;

    @Bean
    public OpenAPI messageServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Chat4All v2 - Message Service API")
                .description("""
                    Message Service provides endpoints for:
                    - Sending messages to external platforms (WhatsApp, Telegram, Instagram)
                    - Receiving inbound messages via webhooks
                    - Tracking message delivery status
                    - Managing conversations and message history
                    
                    **Architecture**: Reactive WebFlux with MongoDB persistence and Kafka event streaming
                    
                    **User Stories Supported**:
                    - US1: Send text messages to external platforms
                    - US2: Receive messages from external platforms
                    - US3: Send file attachments
                    - US4: Group conversation support
                    
                    **Performance**:
                    - Message acceptance: <200ms (p95)
                    - Conversation history retrieval: <2s (p95)
                    - Throughput: 10,000 messages/second
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Chat4All Team")
                    .email("support@chat4all.com")
                    .url("https://github.com/ErikPDN/chat4all-v2"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local Development"),
                new Server()
                    .url("http://message-service:8081")
                    .description("Docker Compose / Kubernetes")
            ));
    }
}
