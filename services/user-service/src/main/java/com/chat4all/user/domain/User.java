package com.chat4all.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User Entity - Internal unified user profile
 * 
 * Represents a single user in the Chat4All system who may have multiple external
 * identities across different platforms (WhatsApp, Telegram, Instagram).
 * 
 * This is the canonical user representation that ties together all platform-specific
 * identities into a single profile for unified conversation history and analytics.
 * 
 * Schema: PostgreSQL
 * Table: users
 * 
 * Fields:
 * - id: UUID primary key (auto-generated)
 * - displayName: User's display name shown in UI
 * - email: Optional email for user contact
 * - userType: ENUM (AGENT, CUSTOMER, SYSTEM) - role in the platform
 * - metadata: JSON field for extensible custom attributes
 * - createdAt: Timestamp when user was created (immutable)
 * - updatedAt: Timestamp of last update (auto-updated)
 * 
 * Relationships:
 * - One-to-Many with ExternalIdentity: A user can have multiple platform identities
 * 
 * Examples:
 * 1. Customer with WhatsApp and Telegram identities
 * 2. Agent with internal identity only
 * 3. System user for automated messages
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    /**
     * JSON field for extensible metadata
     * Examples:
     * - Customer: {"company": "Acme Corp", "segment": "Enterprise"}
     * - Agent: {"department": "Support", "languages": ["en", "pt"]}
     * - System: {"automationRule": "after-hours"}
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * External identities linked to this user
     * Cascade: ALL - when user is deleted, all identities are deleted
     * Orphan removal: true - when identity is removed from list, it's deleted from DB
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExternalIdentity> externalIdentities = new ArrayList<>();

    /**
     * Helper method to add an external identity
     * Maintains bidirectional relationship consistency
     */
    public void addExternalIdentity(ExternalIdentity identity) {
        externalIdentities.add(identity);
        identity.setUser(this);
    }

    /**
     * Helper method to remove an external identity
     * Maintains bidirectional relationship consistency
     */
    public void removeExternalIdentity(ExternalIdentity identity) {
        externalIdentities.remove(identity);
        identity.setUser(null);
    }
}
