package com.chat4all.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * File Service Application
 * 
 * Responsibilities:
 * - File attachment management
 * - S3/MinIO storage integration
 * - Presigned URL generation for uploads/downloads
 * - File metadata persistence (MongoDB)
 * - Malware scanning integration (future)
 * - Thumbnail generation (future)
 * 
 * Architecture:
 * - Spring Boot WebFlux (reactive REST API)
 * - MongoDB (file metadata storage)
 * - MinIO/S3 (object storage)
 * - AWS SDK v2 (S3 client)
 * 
 * Endpoints:
 * - POST /api/files/initiate - Initiate file upload (returns presigned URL)
 * - GET /api/files/{id} - Get file metadata
 * - GET /api/files/{id}/download - Get presigned download URL
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableMongoRepositories
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
