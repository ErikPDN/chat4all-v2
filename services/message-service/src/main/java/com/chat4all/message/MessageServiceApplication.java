package com.chat4all.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Message Service Application
 * 
 * Core service for message acceptance, persistence, and status tracking.
 * 
 * Responsibilities:
 * 1. Accept outbound messages via REST API (POST /messages)
 * 2. Persist messages to MongoDB
 * 3. Publish message events to Kafka (chat-events topic)
 * 4. Track message delivery status
 * 5. Process inbound messages from webhooks
 * 
 * Technologies:
 * - Spring WebFlux (reactive REST API)
 * - Spring Data MongoDB Reactive (message persistence)
 * - Spring Kafka (event publishing)
 * - Redis (idempotency checks)
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = "com.chat4all.message.repository")
@EnableKafka
public class MessageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageServiceApplication.class, args);
    }
}
