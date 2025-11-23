-- V001__create_users_table.sql
-- Create users table with UUID primary key and JSONB metadata

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- For credential encryption in channel_configurations

CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    display_name VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT user_type_check CHECK (user_type IN ('CUSTOMER', 'AGENT', 'SYSTEM'))
);

-- Indexes
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_created_at ON users(created_at DESC);
CREATE INDEX idx_users_metadata ON users USING GIN (metadata jsonb_path_ops);

-- Comments
COMMENT ON TABLE users IS 'Master user registry for all platform users (customers, agents, system)';
COMMENT ON COLUMN users.user_id IS 'Primary key, UUIDv4 auto-generated';
COMMENT ON COLUMN users.display_name IS 'User display name shown in UI';
COMMENT ON COLUMN users.user_type IS 'User role type (CUSTOMER, AGENT, SYSTEM)';
COMMENT ON COLUMN users.metadata IS 'Extensible JSONB field for additional user attributes';
