-- V002__create_external_identities_table.sql
-- Create external_identities table for linking platform accounts (WhatsApp, Telegram, Instagram) to users

CREATE TABLE external_identities (
    identity_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    platform VARCHAR(50) NOT NULL,
    platform_user_id VARCHAR(255) NOT NULL,
    credentials JSONB DEFAULT '{}'::jsonb,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key with CASCADE delete
    CONSTRAINT fk_external_identities_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(user_id) 
        ON DELETE CASCADE,
    
    -- Constraints
    CONSTRAINT platform_check CHECK (platform IN ('WHATSAPP', 'TELEGRAM', 'INSTAGRAM')),
    CONSTRAINT unique_platform_user UNIQUE (platform, platform_user_id)
);

-- Indexes
CREATE INDEX idx_external_identities_user_id ON external_identities(user_id);
CREATE INDEX idx_external_identities_platform ON external_identities(platform);
CREATE INDEX idx_external_identities_last_synced ON external_identities(last_synced_at DESC);

-- Comments
COMMENT ON TABLE external_identities IS 'Links users to their external platform accounts (1:N relationship)';
COMMENT ON COLUMN external_identities.platform_user_id IS 'External platform user identifier (e.g., WhatsApp phone number)';
COMMENT ON COLUMN external_identities.credentials IS 'Platform-specific credentials (OAuth tokens, API keys)';
COMMENT ON COLUMN external_identities.last_synced_at IS 'Last time credentials were validated/refreshed';
COMMENT ON CONSTRAINT unique_platform_user ON external_identities IS 'Prevents duplicate platform accounts (one Instagram account cannot belong to multiple users)';
