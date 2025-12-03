package com.chat4all.connector.telegram.config;

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
 * OpenAPI 3.0 Configuration for Telegram Connector
 * 
 * Generates interactive API documentation at:
 * - Swagger UI: http://localhost:8086/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8086/v3/api-docs
 * 
 * Task: T140 - Create API documentation using Springdoc OpenAPI
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:telegram-connector}")
    private String applicationName;

    @Value("${server.port:8086}")
    private String serverPort;

    @Bean
    public OpenAPI telegramConnectorOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Chat4All v2 - Telegram Connector API")
                .description("""
                    Telegram Connector integrates with Telegram Bot API for:
                    - Sending text messages via Telegram bots
                    - Sending media messages (photos, documents, videos, audio)
                    - Receiving inbound messages via webhooks
                    - Receiving callback queries and inline queries
                    - Bot command handling
                    
                    **Architecture**: Spring MVC with Telegram Bot API integration
                    
                    **Integration**:
                    - Telegram Bot API endpoint: api.telegram.org/bot{token}
                    - Real-time webhook updates for instant message delivery
                    - Circuit breaker and retry logic with Resilience4j
                    - Support for both HTTP and HTTPS webhooks
                    
                    **Message Types Supported**:
                    - Text messages with Markdown/HTML formatting
                    - Media messages (photo, document, video, audio, voice, animation)
                    - Location and contact sharing
                    - Inline keyboard buttons and reply keyboards
                    
                    **Features**:
                    - Bot: @chat4all_erik_bot
                    - Real Telegram Bot API integration (not mock)
                    - Message delivery confirmation with message_id
                    - Support for group chats and channels
                    
                    **Performance**:
                    - Message send latency: <300ms (p95)
                    - Webhook processing: <50ms (p95)
                    - Throughput: 30 messages/second per bot (Telegram API limit)
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
                    .url("http://telegram-connector:8086")
                    .description("Docker Compose / Kubernetes")
            ));
    }
}
