-- V001: Create users table
-- Purpose: Internal user profiles (agents and customers)
-- Requirements: FR-031, FR-032

-- Enable UUID generation extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    user_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata TEXT,
    
    CONSTRAINT users_display_name_min_length CHECK (LENGTH(display_name) >= 1),
    CONSTRAINT users_user_type_check CHECK (user_type IN ('AGENT', 'CUSTOMER'))
);

-- Create indexes for performance
CREATE INDEX idx_users_user_type ON users(user_type);
CREATE INDEX idx_users_metadata ON users USING GIN((metadata::jsonb));

-- Create trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add comments for documentation
COMMENT ON TABLE users IS 'Internal user profiles for agents and customers';
COMMENT ON COLUMN users.id IS 'Unique identifier (UUID) automatically generated';
COMMENT ON COLUMN users.display_name IS 'User display name (min 1 char, max 255 chars)';
COMMENT ON COLUMN users.email IS 'Optional email for user contact';
COMMENT ON COLUMN users.user_type IS 'User type: AGENT or CUSTOMER';
COMMENT ON COLUMN users.metadata IS 'Additional user metadata stored as JSON text';
