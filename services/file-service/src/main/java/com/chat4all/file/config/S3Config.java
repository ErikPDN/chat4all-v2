package com.chat4all.file.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3/MinIO Configuration
 * 
 * Configures AWS SDK v2 S3 client to connect to MinIO (S3-compatible storage).
 * 
 * MinIO Configuration:
 * - Endpoint: http://localhost:9000 (or Docker service name)
 * - Access Key: minioadmin
 * - Secret Key: minioadmin
 * - Region: us-east-1 (MinIO default)
 * - Bucket: chat4all-files (auto-created if not exists)
 * 
 * Beans:
 * - S3Client: For direct S3 operations (create bucket, delete object)
 * - S3Presigner: For generating presigned URLs (upload/download)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
public class S3Config {

    @Value("${s3.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${s3.access-key:minioadmin}")
    private String accessKey;

    @Value("${s3.secret-key:minioadmin}")
    private String secretKey;

    @Value("${s3.region:us-east-1}")
    private String region;

    @Value("${s3.bucket-name:chat4all-files}")
    private String bucketName;

    /**
     * S3 Client Bean
     * 
     * Used for direct S3 operations:
     * - Create/delete buckets
     * - Upload/delete objects (sync)
     * - List objects
     * 
     * @return Configured S3Client
     */
    @Bean
    public S3Client s3Client() {
        log.info("Initializing S3 client with endpoint: {}, region: {}, bucket: {}", 
            endpoint, region, bucketName);

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        // Explicitly configure HTTP client to avoid "Multiple HTTP implementations" error
        SdkHttpClient httpClient = UrlConnectionHttpClient.builder().build();

        S3Client client = S3Client.builder()
            .httpClient(httpClient)
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .forcePathStyle(true) // Required for MinIO
            .build();

        // Create bucket if not exists
        ensureBucketExists(client);

        return client;
    }

    /**
     * S3 Presigner Bean
     * 
     * Used for generating presigned URLs:
     * - Upload URLs (PUT) - allows client to upload directly to S3
     * - Download URLs (GET) - temporary download links
     * 
     * @return Configured S3Presigner
     */
    @Bean
    public S3Presigner s3Presigner() {
        log.info("Initializing S3 presigner with endpoint: {}, region: {}", endpoint, region);

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        // Explicitly configure HTTP client to avoid "Multiple HTTP implementations" error
        SdkHttpClient httpClient = UrlConnectionHttpClient.builder().build();

        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .build();
    }

    /**
     * Ensure bucket exists (create if missing)
     * 
     * MinIO automatically creates buckets on first upload,
     * but we create explicitly for better error handling.
     * 
     * @param client S3Client instance
     */
    private void ensureBucketExists(S3Client client) {
        try {
            boolean bucketExists = client.listBuckets().buckets().stream()
                .anyMatch(bucket -> bucket.name().equals(bucketName));

            if (!bucketExists) {
                log.warn("Bucket '{}' does not exist, creating...", bucketName);
                client.createBucket(builder -> builder.bucket(bucketName));
                log.info("Bucket '{}' created successfully", bucketName);
            } else {
                log.info("Bucket '{}' already exists", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket exists: {}", bucketName, e);
            throw new RuntimeException("S3 bucket initialization failed", e);
        }
    }

    /**
     * Get configured bucket name
     * 
     * @return Bucket name
     */
    public String getBucketName() {
        return bucketName;
    }
}
