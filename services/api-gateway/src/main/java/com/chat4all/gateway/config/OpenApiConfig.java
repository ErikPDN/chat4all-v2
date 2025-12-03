package com.chat4all.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 configuration for Chat4All v2 API Gateway.
 * 
 * The API Gateway provides:
 * - OAuth2 authentication and authorization
 * - Rate limiting (100 req/min per user, 1000 req/min global)
 * - Request/response logging with correlation IDs
 * - Circuit breaker protection for downstream services
 * - Intelligent routing to backend microservices
 * 
 * Backend Services Routing:
 * - /api/messages/* → message-service (8081)
 * - /api/conversations/* → message-service (8081)
 * - /api/users/* → user-service (8083)
 * - /api/files/* → file-service (8084)
 * - /webhooks/* → connector services (8090, 8086, 8092)
 * 
 * Security:
 * - OAuth2 scopes: messages:read, messages:write, users:read, users:write, files:read, files:write
 * - JWT token validation with RS256
 * - CORS configuration for web clients
 * 
 * Performance:
 * - <50ms routing latency (P95)
 * - 10K+ concurrent requests support
 * - Redis-backed rate limiting
 * - Circuit breaker with 50% error threshold
 * 
 * Cross-Cutting Concerns:
 * - Correlation ID generation and propagation
 * - Structured JSON logging
 * - Prometheus metrics (/actuator/prometheus)
 * - Distributed tracing with OpenTelemetry
 * 
 * Constitutional Principles Alignment:
 * - Principle I: Horizontally scalable (stateless gateway, Redis for rate limiting)
 * - Principle V: Real-time performance (<50ms routing)
 * - Principle VI: Full observability (logs, metrics, tracing)
 * 
 * Interactive API Documentation:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 * 
 * Note: This gateway aggregates documentation from all backend services.
 * For service-specific API details, see individual service Swagger UIs.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiGatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chat4All v2 - API Gateway")
                        .description("""
                                Unified API Gateway for Chat4All v2 Messaging Platform.
                                
                                **Features:**
                                - OAuth2 authentication and authorization
                                - Rate limiting and throttling
                                - Circuit breaker protection
                                - Intelligent routing to microservices
                                - Request/response logging
                                - Correlation ID propagation
                                
                                **Routing Map:**
                                - Messages API: `/api/messages/*` → message-service
                                - Conversations API: `/api/conversations/*` → message-service
                                - Users API: `/api/users/*` → user-service
                                - Files API: `/api/files/*` → file-service
                                - Webhooks: `/webhooks/*` → connector services
                                
                                **Security:**
                                - OAuth2 scopes required for all endpoints
                                - JWT token validation
                                - CORS enabled for web clients
                                
                                **Rate Limits:**
                                - Authenticated users: 100 requests/minute
                                - Global limit: 1000 requests/minute
                                - Burst capacity: 200 requests
                                
                                **Performance:**
                                - Routing latency: <50ms (P95)
                                - Concurrent requests: 10K+
                                - Circuit breaker threshold: 50% errors
                                
                                **Documentation:**
                                For detailed API specifications, visit individual service Swagger UIs:
                                - Message Service: http://localhost:8081/swagger-ui.html
                                - User Service: http://localhost:8083/swagger-ui.html
                                - File Service: http://localhost:8084/swagger-ui.html
                                - WhatsApp Connector: http://localhost:8090/swagger-ui.html
                                - Telegram Connector: http://localhost:8086/swagger-ui.html
                                - Instagram Connector: http://localhost:8092/swagger-ui.html
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Chat4All Engineering Team")
                                .email("engineering@chat4all.com")
                                .url("https://github.com/ErikPDN/chat4all-v2"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development environment"),
                        new Server()
                                .url("http://api-gateway:8080")
                                .description("Docker Compose / Kubernetes environment"),
                        new Server()
                                .url("https://api.chat4all.com")
                                .description("Production environment (example)")
                ));
    }
}
