package com.chat4all.connector.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WhatsApp Connector Application
 * 
 * Mock implementation of WhatsApp Business API connector.
 * 
 * Features:
 * - Receives messages from Router Service
 * - Simulates message delivery to WhatsApp
 * - Sends delivery/read status callbacks
 * - Implements connector health checks
 * 
 * Port: 8091
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@SpringBootApplication
public class WhatsAppConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatsAppConnectorApplication.class, args);
    }
}
