package com.chat4all.connector.whatsapp.config;

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
 * OpenAPI 3.0 Configuration for WhatsApp Connector
 * 
 * Generates interactive API documentation at:
 * - Swagger UI: http://localhost:8090/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8090/v3/api-docs
 * 
 * Task: T140 - Create API documentation using Springdoc OpenAPI
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:whatsapp-connector}")
    private String applicationName;

    @Value("${server.port:8090}")
    private String serverPort;

    @Bean
    public OpenAPI whatsappConnectorOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Chat4All v2 - WhatsApp Connector API")
                .description("""
                    WhatsApp Connector integrates with WhatsApp Business API for:
                    - Sending text messages to WhatsApp users
                    - Sending media messages (images, documents, videos)
                    - Receiving inbound messages via webhooks
                    - Receiving delivery status updates
                    - Message template management
                    
                    **Architecture**: Spring MVC with WhatsApp Cloud API integration
                    
                    **Integration**:
                    - WhatsApp Cloud API endpoint: graph.facebook.com/v18.0
                    - Webhook signature validation for security
                    - Circuit breaker and retry logic with Resilience4j
                    - Rate limiting compliance with WhatsApp API limits
                    
                    **Message Types Supported**:
                    - Text messages
                    - Media messages (image, document, video, audio)
                    - Template messages for notifications
                    - Interactive messages (buttons, lists)
                    
                    **Performance**:
                    - Message send latency: <500ms (p95)
                    - Webhook processing: <100ms (p95)
                    - Throughput: 1000 messages/second (within API limits)
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
                    .url("http://whatsapp-connector:8090")
                    .description("Docker Compose / Kubernetes")
            ));
    }
}
