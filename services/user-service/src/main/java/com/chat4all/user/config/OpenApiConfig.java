package com.chat4all.user.config;

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
 * OpenAPI 3.0 Configuration for User Service
 * 
 * Generates interactive API documentation at:
 * - Swagger UI: http://localhost:8083/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8083/v3/api-docs
 * 
 * Task: T140 - Create API documentation using Springdoc OpenAPI
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:user-service}")
    private String applicationName;

    @Value("${server.port:8083}")
    private String serverPort;

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Chat4All v2 - User Service API")
                .description("""
                    User Service provides endpoints for:
                    - Creating and managing user profiles
                    - Linking external platform identities (WhatsApp, Telegram, Instagram)
                    - Identity verification and mapping
                    - User search and discovery
                    
                    **Architecture**: Spring MVC with PostgreSQL persistence
                    
                    **User Stories Supported**:
                    - US5: Identity mapping across platforms
                    
                    **Features**:
                    - Multi-platform identity linking (one user → many external IDs)
                    - Identity verification workflows
                    - Audit logging for all identity operations
                    - Automatic identity suggestion based on phone/email matching
                    
                    **Performance**:
                    - User creation: <100ms (p95)
                    - Identity lookup: <50ms (p95)
                    - Supports 1M+ users with billions of identity mappings
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
                    .url("http://user-service:8083")
                    .description("Docker Compose / Kubernetes")
            ));
    }
}
