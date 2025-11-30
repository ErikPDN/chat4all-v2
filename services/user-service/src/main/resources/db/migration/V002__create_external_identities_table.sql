-- V002: Create external_identities table
-- Purpose: Links internal users to platform-specific accounts (WhatsApp, Telegram, Instagram)
-- Requirements: FR-033, FR-034

-- Create external_identities table
CREATE TABLE external_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    platform_user_id VARCHAR(255) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    
    -- Prevent duplicate platform accounts
    CONSTRAINT unique_platform_identity UNIQUE (platform, platform_user_id),
    CONSTRAINT external_identities_platform_check CHECK (platform IN ('WHATSAPP', 'TELEGRAM', 'INSTAGRAM'))
);

-- Create indexes for performance
CREATE INDEX idx_external_identities_user_id ON external_identities(user_id);
CREATE INDEX idx_external_identities_platform ON external_identities(platform);
CREATE INDEX idx_external_identities_platform_user ON external_identities(platform, platform_user_id);

-- Add comments for documentation
COMMENT ON TABLE external_identities IS 'Links internal users to external platform accounts';
COMMENT ON COLUMN external_identities.id IS 'Unique identifier (UUID) for this identity mapping';
COMMENT ON COLUMN external_identities.user_id IS 'Reference to internal user profile';
COMMENT ON COLUMN external_identities.platform IS 'External platform: WHATSAPP, TELEGRAM, or INSTAGRAM';
COMMENT ON COLUMN external_identities.platform_user_id IS 'Platform-specific user identifier (e.g., +5511999999999 for WhatsApp, @username for Telegram)';
COMMENT ON COLUMN external_identities.verified IS 'Whether identity verification has been completed (for high-security channels)';
COMMENT ON COLUMN external_identities.linked_at IS 'Timestamp when identity was linked';
COMMENT ON COLUMN external_identities.metadata IS 'Additional platform-specific metadata';
COMMENT ON CONSTRAINT unique_platform_identity ON external_identities IS 'Ensures each platform account can only be linked once';
