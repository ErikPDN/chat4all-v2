package com.chat4all.file.config;

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
 * OpenAPI 3.0 Configuration for File Service
 * 
 * Generates interactive API documentation at:
 * - Swagger UI: http://localhost:8084/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8084/v3/api-docs
 * 
 * Task: T140 - Create API documentation using Springdoc OpenAPI
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:file-service}")
    private String applicationName;

    @Value("${server.port:8084}")
    private String serverPort;

    @Bean
    public OpenAPI fileServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Chat4All v2 - File Service API")
                .description("""
                    File Service provides endpoints for:
                    - Initiating file uploads with presigned S3 URLs
                    - Managing file metadata and storage
                    - Malware scanning integration (ClamAV)
                    - Thumbnail generation for images
                    - File download with presigned URLs
                    
                    **Architecture**: Reactive WebFlux with MongoDB metadata and S3/MinIO storage
                    
                    **User Stories Supported**:
                    - US3: Send file attachments (images, documents, videos)
                    
                    **Features**:
                    - Multi-part upload for files >100MB
                    - File type validation (whitelist: jpg, png, pdf, docx, mp4, etc.)
                    - Automatic malware scanning before availability
                    - TTL-based file expiration (24h default)
                    - Max file size: 2GB per specification FR-024
                    
                    **Performance**:
                    - Presigned URL generation: <50ms (p95)
                    - File metadata retrieval: <100ms (p95)
                    - Supports 100K+ concurrent uploads
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
                    .url("http://file-service:8084")
                    .description("Docker Compose / Kubernetes")
            ));
    }
}
