-- V004: Create audit_logs table
-- Purpose: Track identity mapping operations and configuration changes
-- Requirements: FR-035

-- Create immutable audit_logs table
CREATE TABLE audit_logs (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,
    performed_by UUID REFERENCES users(id),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changes JSONB,
    ip_address INET,
    user_agent TEXT,
    
    -- Ensure timestamp is always set
    CONSTRAINT audit_logs_timestamp_not_null CHECK (timestamp IS NOT NULL),
    CONSTRAINT audit_logs_action_check CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LINK', 'UNLINK'))
);

-- Create indexes for performance
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_performed_by ON audit_logs(performed_by);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);

-- Prevent UPDATE and DELETE operations on audit logs (immutability)
CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable - UPDATE and DELETE operations are not allowed';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_audit_log_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_modification();

CREATE TRIGGER prevent_audit_log_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_modification();

-- Function to clean up old audit logs (7-year retention policy)
-- This should be called by a scheduled job, not automatically
CREATE OR REPLACE FUNCTION cleanup_old_audit_logs(retention_years INTEGER DEFAULT 7)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM audit_logs
    WHERE timestamp < CURRENT_TIMESTAMP - (retention_years || ' years')::INTERVAL;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RAISE NOTICE 'Cleaned up % audit log records older than % years', deleted_count, retention_years;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Add comments for documentation
COMMENT ON TABLE audit_logs IS 'Immutable audit trail for identity mapping and configuration changes';
COMMENT ON COLUMN audit_logs.log_id IS 'Unique identifier (UUID) for this audit log entry';
COMMENT ON COLUMN audit_logs.entity_type IS 'Type of entity being audited (e.g., USER, EXTERNAL_IDENTITY, CHANNEL_CONFIGURATION)';
COMMENT ON COLUMN audit_logs.entity_id IS 'UUID of the entity being audited';
COMMENT ON COLUMN audit_logs.action IS 'Action performed: CREATE, UPDATE, DELETE, LINK, or UNLINK';
COMMENT ON COLUMN audit_logs.performed_by IS 'User who performed the action (null for system actions)';
COMMENT ON COLUMN audit_logs.timestamp IS 'When the action was performed';
COMMENT ON COLUMN audit_logs.changes IS 'JSON diff of before/after state for UPDATE actions';
COMMENT ON COLUMN audit_logs.ip_address IS 'IP address of the client that performed the action';
COMMENT ON COLUMN audit_logs.user_agent IS 'User agent of the client that performed the action';
COMMENT ON FUNCTION cleanup_old_audit_logs IS 'Removes audit logs older than retention period (default: 7 years). Should be called by scheduled job.';

-- Retention policy: 7 years (default), configurable per jurisdiction
-- Call cleanup_old_audit_logs() from application scheduler or cron job
