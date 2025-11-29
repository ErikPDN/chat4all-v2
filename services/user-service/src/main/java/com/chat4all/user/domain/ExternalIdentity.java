package com.chat4all.user.domain;

import com.chat4all.common.constant.Channel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * ExternalIdentity Entity - Links external platform identities to internal users
 * 
 * Represents a user's identity on an external platform (WhatsApp, Telegram, Instagram).
 * Multiple identities can be linked to a single User for unified conversation tracking.
 * 
 * Schema: PostgreSQL
 * Table: external_identities
 * 
 * Fields:
 * - id: UUID primary key (auto-generated)
 * - user: Many-to-One relationship with User (FK: user_id)
 * - platform: ENUM Channel (WHATSAPP, TELEGRAM, INSTAGRAM, INTERNAL)
 * - platformUserId: Platform-specific user identifier
 *   Examples:
 *   - WhatsApp: +5511999999999 (phone number in E.164 format)
 *   - Telegram: 123456789 (Telegram user ID)
 *   - Instagram: @username or numeric ID
 * - verified: Boolean flag for identity verification status
 * - linkedAt: Timestamp when identity was linked (immutable)
 * 
 * Unique Constraint:
 * - (platform, platformUserId) must be unique - prevents duplicate identities
 * 
 * Examples:
 * 1. User "John Doe" with:
 *    - WhatsApp: +5511999999999
 *    - Telegram: 123456789
 *    Both identities link to same User record
 * 
 * 2. Identity verification workflow:
 *    - Create ExternalIdentity with verified=false
 *    - Send verification code via platform
 *    - Update verified=true after confirmation
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Entity
@Table(
    name = "external_identities",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_external_identity_platform_user",
            columnNames = {"platform", "platform_user_id"}
        )
    },
    indexes = {
        @Index(name = "idx_external_identity_user", columnList = "user_id"),
        @Index(name = "idx_external_identity_platform_user", columnList = "platform, platform_user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Many external identities can belong to one user
     * FetchType.LAZY: User is loaded only when accessed
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_external_identity_user"))
    private User user;

    /**
     * External platform where this identity exists
     * Uses Channel enum from common-domain
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Channel platform;

    /**
     * Platform-specific user identifier
     * Examples:
     * - WhatsApp: +5511999999999
     * - Telegram: 123456789
     * - Instagram: @username
     * 
     * Length: 255 to support long identifiers
     */
    @Column(name = "platform_user_id", nullable = false, length = 255)
    private String platformUserId;

    /**
     * Verification status
     * - true: Identity verified via code/confirmation
     * - false: Pending verification
     * 
     * High-security channels may require verification before sending messages
     */
    @Column(name = "verified", nullable = false)
    @Builder.Default
    private boolean verified = false;

    /**
     * Timestamp when identity was linked to user
     * Immutable - used for audit trail
     */
    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    /**
     * Check if this identity matches a platform and platform user ID
     * Useful for finding identities in collections
     */
    public boolean matches(Channel platform, String platformUserId) {
        return this.platform == platform && 
               this.platformUserId != null && 
               this.platformUserId.equals(platformUserId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExternalIdentity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ExternalIdentity{" +
                "id=" + id +
                ", platform=" + platform +
                ", platformUserId='" + platformUserId + '\'' +
                ", verified=" + verified +
                ", linkedAt=" + linkedAt +
                '}';
    }
}
