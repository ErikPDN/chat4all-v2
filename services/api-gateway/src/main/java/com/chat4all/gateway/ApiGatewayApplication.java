package com.chat4all.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application for Chat4All v2.
 * 
 * This is the main entry point for the API Gateway service which provides:
 * - Request routing to downstream microservices
 * - OAuth2/JWT authentication and authorization
 * - Rate limiting with Redis backend
 * - Circuit breaker pattern with Resilience4j
 * - Aggregated OpenAPI/Swagger documentation
 * - CORS configuration for frontend applications
 * 
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * API Docs: http://localhost:8080/v3/api-docs
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
