package com.chat4all.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit Logging Service for identity operations (FR-035)
 * 
 * Provides immutable audit trail for all identity-related operations
 * including creation, verification, linking, unlinking, and deletion.
 * 
 * Audit logs are:
 * - Immutable (INSERT only, no UPDATE/DELETE)
 * - Retained for 7 years (compliance requirement)
 * - Stored in PostgreSQL audit_logs table
 * - Independent transaction (REQUIRES_NEW) to ensure logging even on rollback
 * 
 * @author Chat4All Team
 * @version 1.0.0
 */
@Service
public class AuditService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Logs an identity operation to the audit_logs table.
     * 
     * Uses REQUIRES_NEW propagation to ensure audit log is committed
     * even if the parent transaction rolls back.
     * 
     * @param entityType Type of entity (USER, EXTERNAL_IDENTITY, etc)
     * @param entityId Entity ID being operated on
     * @param action Action performed (CREATE, UPDATE, DELETE, LINK, UNLINK)
     * @param performedBy User who performed the action (null for system actions)
     * @param changes JSON diff of changes (for UPDATE actions)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIdentityOperation(String entityType, UUID entityId, String action, 
                                      UUID performedBy, String changes) {
        try {
            String sql = """
                INSERT INTO audit_logs (
                    log_id,
                    entity_type,
                    entity_id,
                    action,
                    performed_by,
                    timestamp,
                    changes,
                    ip_address,
                    user_agent
                ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::inet, ?)
                """;
            
            UUID logId = UUID.randomUUID();
            LocalDateTime now = LocalDateTime.now();
            
            // In a real implementation, IP and User-Agent would come from request context
            String ipAddress = "127.0.0.1"; // Placeholder
            String userAgent = "Chat4All/1.0"; // Placeholder
            
            jdbcTemplate.update(
                    sql,
                    logId,
                    entityType,
                    entityId,
                    action,
                    performedBy,
                    now,
                    changes,
                    ipAddress,
                    userAgent
            );
            
            log.debug("Audit log created: action={}, entityType={}, entityId={}, logId={}", 
                    action, entityType, entityId, logId);
                    
        } catch (Exception e) {
            // Log error but don't throw - audit logging should not break business logic
            log.error("Failed to create audit log: action={}, entityType={}, entityId={}, error={}", 
                    action, entityType, entityId, e.getMessage(), e);
        }
    }
    
    /**
     * Logs identity creation operation.
     * 
     * @param userId User ID who owns the identity
     * @param identityId External identity ID
     * @param platform Platform name (WHATSAPP, TELEGRAM, etc)
     * @param platformUserId Platform-specific user ID
     */
    public void logIdentityCreated(UUID userId, UUID identityId, String platform, String platformUserId) {
        String changes = String.format(
                "{\"platform\":\"%s\",\"platformUserId\":\"%s\"}",
                platform, platformUserId
        );
        logIdentityOperation("EXTERNAL_IDENTITY", identityId, "CREATE", userId, changes);
    }
    
    /**
     * Logs identity deletion operation.
     * 
     * @param userId User ID who performed the deletion
     * @param identityId External identity ID
     * @param platform Platform name
     */
    public void logIdentityDeleted(UUID userId, UUID identityId, String platform) {
        String changes = String.format("{\"platform\":\"%s\"}", platform);
        logIdentityOperation("EXTERNAL_IDENTITY", identityId, "DELETE", userId, changes);
    }
    
    /**
     * Logs identity linking operation.
     * 
     * @param userId User ID to which identity was linked
     * @param identityId External identity ID
     * @param platform Platform name
     */
    public void logIdentityLinked(UUID userId, UUID identityId, String platform) {
        String changes = String.format(
                "{\"platform\":\"%s\",\"linkedToUser\":\"%s\"}",
                platform, userId
        );
        logIdentityOperation("EXTERNAL_IDENTITY", identityId, "LINK", userId, changes);
    }
    
    /**
     * Logs identity unlinking operation.
     * 
     * @param userId User ID from which identity was unlinked
     * @param identityId External identity ID
     * @param platform Platform name
     */
    public void logIdentityUnlinked(UUID userId, UUID identityId, String platform) {
        String changes = String.format(
                "{\"platform\":\"%s\",\"unlinkedFromUser\":\"%s\"}",
                platform, userId
        );
        logIdentityOperation("EXTERNAL_IDENTITY", identityId, "UNLINK", userId, changes);
    }
    
    /**
     * Logs user creation operation.
     * 
     * @param userId User ID of created user
     * @param displayName User display name
     * @param userType User type (CUSTOMER, AGENT, ADMIN)
     */
    public void logUserCreated(UUID userId, String displayName, String userType) {
        String changes = String.format(
                "{\"displayName\":\"%s\",\"userType\":\"%s\"}",
                displayName, userType
        );
        logIdentityOperation("USER", userId, "CREATE", userId, changes);
    }
    
    /**
     * Logs user update operation.
     * 
     * @param userId User ID of updated user
     * @param performedBy User who performed the update
     * @param changes JSON describing the changes
     */
    public void logUserUpdated(UUID userId, UUID performedBy, String changes) {
        logIdentityOperation("USER", userId, "UPDATE", performedBy, changes);
    }
    
    /**
     * Logs identity verification operation.
     * 
     * @param identityId External identity ID
     * @param performedBy User who performed verification (null for automated)
     * @param verificationType Type of verification (OTP, EMAIL, MANUAL, etc)
     */
    public void logIdentityVerified(UUID identityId, UUID performedBy, String verificationType) {
        String changes = String.format(
                "{\"verified\":true,\"verificationType\":\"%s\"}",
                verificationType
        );
        logIdentityOperation("EXTERNAL_IDENTITY", identityId, "UPDATE", performedBy, changes);
    }
    
    /**
     * Logs identity verification revocation.
     * 
     * @param identityId External identity ID
     * @param performedBy User who revoked verification
     * @param reason Reason for revocation
     */
    public void logVerificationRevoked(UUID identityId, UUID performedBy, String reason) {
        String changes = String.format(
                "{\"verified\":false,\"reason\":\"%s\"}",
                reason
        );
        logIdentityOperation("EXTERNAL_IDENTITY", identityId, "UPDATE", performedBy, changes);
    }
    
    /**
     * Logs security event (failed verification, suspicious activity).
     * 
     * @param entityId Entity ID (user or identity)
     * @param eventType Type of security event
     * @param description Event description
     */
    public void logSecurityEvent(UUID entityId, String eventType, String description) {
        String changes = String.format(
                "{\"eventType\":\"%s\",\"description\":\"%s\"}",
                eventType, description
        );
        // System action (no user performed this)
        logIdentityOperation("SECURITY_EVENT", entityId, "CREATE", null, changes);
    }
}
