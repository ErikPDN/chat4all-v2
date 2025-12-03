package com.chat4all.connector.instagram.config;

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
 * OpenAPI 3.0 Configuration for Instagram Connector
 * 
 * Generates interactive API documentation at:
 * - Swagger UI: http://localhost:8092/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8092/v3/api-docs
 * 
 * Task: T140 - Create API documentation using Springdoc OpenAPI
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:instagram-connector}")
    private String applicationName;

    @Value("${server.port:8092}")
    private String serverPort;

    @Bean
    public OpenAPI instagramConnectorOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Chat4All v2 - Instagram Connector API")
                .description("""
                    Instagram Connector integrates with Instagram Messaging API for:
                    - Sending text messages via Instagram Direct
                    - Sending media messages (images, videos, stories)
                    - Receiving inbound messages via webhooks
                    - Receiving comment and mention notifications
                    - Story replies and reaction handling
                    
                    **Architecture**: Spring MVC with Instagram Graph API integration
                    
                    **Integration**:
                    - Instagram Graph API endpoint: graph.facebook.com/v18.0
                    - Webhook signature validation for security
                    - Circuit breaker and retry logic with Resilience4j
                    - OAuth 2.0 authentication flow
                    
                    **Message Types Supported**:
                    - Text messages (Direct Messages)
                    - Media messages (image, video)
                    - Story replies
                    - Generic templates with buttons
                    - Quick replies
                    
                    **Features**:
                    - Instagram Business Account integration
                    - Support for multiple Instagram pages
                    - Message tagging for compliance
                    - Read receipts and typing indicators
                    
                    **Performance**:
                    - Message send latency: <600ms (p95)
                    - Webhook processing: <150ms (p95)
                    - Throughput: 500 messages/second (within API limits)
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
                    .url("http://instagram-connector:8092")
                    .description("Docker Compose / Kubernetes")
            ));
    }
}
