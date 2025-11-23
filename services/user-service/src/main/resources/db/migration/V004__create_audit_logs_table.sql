-- V004__create_audit_logs_table.sql
-- Create immutable audit_logs table for compliance (7-year retention)

CREATE TABLE audit_logs (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(255),
    details JSONB DEFAULT '{}'::jsonb,
    ip_address INET,
    user_agent TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key (nullable, some actions may not be user-initiated)
    CONSTRAINT fk_audit_logs_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE SET NULL
);

-- Indexes
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_details ON audit_logs USING GIN (details jsonb_path_ops);

-- Immutability enforcement: Prevent UPDATE and DELETE operations
CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable - UPDATE and DELETE operations are not allowed';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_prevent_audit_log_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_modification();

CREATE TRIGGER trigger_prevent_audit_log_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_modification();

-- Comments
COMMENT ON TABLE audit_logs IS 'Immutable audit trail for compliance and security monitoring (7-year retention policy)';
COMMENT ON COLUMN audit_logs.action IS 'Action performed (e.g., USER_CREATED, MESSAGE_SENT, LOGIN_FAILED)';
COMMENT ON COLUMN audit_logs.resource_type IS 'Type of resource affected (e.g., USER, MESSAGE, CONVERSATION)';
COMMENT ON COLUMN audit_logs.resource_id IS 'Identifier of the affected resource';
COMMENT ON COLUMN audit_logs.details IS 'Additional context (request payload, error messages, etc.)';
COMMENT ON COLUMN audit_logs.ip_address IS 'Source IP address of the action';

-- Retention Policy Note:
-- Default retention: 7 years
-- Archival strategy: Move logs older than 1 year to S3 Glacier (implement via scheduled job)
-- Partitioning consideration: If volume exceeds 100M rows, consider time-based partitioning
