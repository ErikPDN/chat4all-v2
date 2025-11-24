package com.chat4all.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Router Service Application
 * 
 * Main application class for the Router Service microservice.
 * Responsible for consuming messages from Kafka chat-events topic,
 * routing them to appropriate connectors, and handling delivery status updates.
 * 
 * Architecture:
 * - Consumes MessageEvent from Kafka (chat-events topic)
 * - Performs deduplication checks using Redis + MongoDB
 * - Routes messages to external platform connectors (WhatsApp, Telegram, Instagram)
 * - Handles retries with exponential backoff (max 3 attempts)
 * - Publishes failed messages to DLQ (Dead Letter Queue)
 * - Publishes status updates back to Kafka (status-updates topic)
 * 
 * Key Components:
 * - MessageEventConsumer: Kafka consumer for chat-events
 * - DeduplicationHandler: Prevents duplicate message delivery
 * - RoutingHandler: Determines target connector based on channel
 * - ConnectorClient: HTTP client for connector communication
 * - RetryHandler: Resilience4j-based retry logic
 * - DLQHandler: Dead Letter Queue for failed messages
 * - StatusUpdateProducer: Publishes status changes to Kafka
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableKafka
public class RouterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouterServiceApplication.class, args);
    }
}
