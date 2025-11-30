package com.chat4all.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * User Service - Spring Boot Application
 * 
 * Identity Mapping and User Management Service for Chat4All Platform
 * 
 * Purpose:
 * - Manage unified user profiles across multiple external platforms
 * - Link external identities (WhatsApp, Telegram, Instagram) to internal users
 * - Provide identity resolution for incoming messages
 * - Support identity verification workflows
 * - Enable user analytics and segmentation
 * 
 * Architecture:
 * - Database: PostgreSQL (relational data for user profiles and identities)
 * - ORM: Spring Data JPA with Hibernate
 * - Migrations: Flyway for schema versioning
 * - Port: 8083
 * 
 * Key Features:
 * 1. User Management:
 *    - Create, read, update users
 *    - Support for AGENT, CUSTOMER, SYSTEM user types
 *    - Extensible metadata (JSONB) for custom attributes
 * 
 * 2. Identity Mapping:
 *    - Link multiple external platform identities to a single user
 *    - Unique constraint on (platform, platformUserId) prevents duplicates
 *    - Fast lookup by platform and platform user ID for message routing
 * 
 * 3. Identity Verification:
 *    - Verification workflow for high-security channels
 *    - Audit logging for identity operations
 * 
 * 4. Identity Suggestion:
 *    - Algorithm to suggest potential identity matches
 *    - Phone number normalization and comparison
 *    - Email and name similarity scoring
 * 
 * Database Schema:
 * - users: Internal user profiles
 * - external_identities: Platform-specific identities linked to users
 * - audit_logs: Immutable audit trail (future)
 * - channel_configurations: Platform credentials and settings (future)
 * 
 * REST API Endpoints:
 * - POST   /users              - Create new user
 * - GET    /users/{id}         - Get user by ID
 * - GET    /users              - List/search users
 * - PUT    /users/{id}         - Update user
 * - POST   /users/{id}/identities              - Link external identity
 * - DELETE /users/{id}/identities/{identityId} - Unlink identity
 * - GET    /users/{id}/identities              - Get user's identities
 * 
 * Health Checks:
 * - GET /actuator/health      - Service health status
 * - GET /actuator/health/liveness  - Kubernetes liveness probe
 * - GET /actuator/health/readiness - Kubernetes readiness probe
 * 
 * Metrics:
 * - GET /actuator/metrics     - Micrometer metrics
 * - GET /actuator/prometheus  - Prometheus scrape endpoint
 * 
 * Example Usage Flow:
 * 
 * 1. Incoming WhatsApp Message:
 *    a. Message arrives from +5511999999999
 *    b. ExternalIdentityRepository.findByPlatformAndPlatformUserId(WHATSAPP, "+5511999999999")
 *    c. Returns ExternalIdentity → User
 *    d. Message routed to User's conversation
 * 
 * 2. Link Telegram Identity to Existing User:
 *    a. User exists with WhatsApp identity
 *    b. POST /users/{id}/identities with Telegram user ID
 *    c. New ExternalIdentity created and linked to User
 *    d. Messages from both platforms now map to same User
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaRepositories
@EnableTransactionManagement
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
