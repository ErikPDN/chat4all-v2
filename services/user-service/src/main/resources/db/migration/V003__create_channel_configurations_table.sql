-- V003: Create channel_configurations table
-- Purpose: External platform integration settings with encrypted credentials
-- Requirements: FR-011, FR-012

-- Enable pgcrypto extension for credential encryption
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Create channel_configurations table
CREATE TABLE channel_configurations (
    channel_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform platform_type_enum NOT NULL,
    name VARCHAR(255) NOT NULL,
    credentials JSONB NOT NULL,
    webhook_url VARCHAR(500),
    rate_limits JSONB,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ensure unique channel names per platform
    CONSTRAINT channel_configurations_name_unique UNIQUE (platform, name)
);

-- Create indexes for performance
CREATE INDEX idx_channel_configurations_platform ON channel_configurations(platform);
CREATE INDEX idx_channel_configurations_enabled ON channel_configurations(enabled) WHERE enabled = TRUE;

-- Create trigger to automatically update updated_at timestamp
CREATE TRIGGER update_channel_configurations_updated_at
    BEFORE UPDATE ON channel_configurations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Helper function to encrypt credentials (AES-256)
-- Usage: encrypt_credentials('{"api_key": "secret"}', 'encryption_key')
CREATE OR REPLACE FUNCTION encrypt_credentials(data JSONB, key TEXT)
RETURNS BYTEA AS $$
BEGIN
    RETURN pgp_sym_encrypt(data::TEXT, key);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Helper function to decrypt credentials
-- Usage: decrypt_credentials(encrypted_data, 'encryption_key')
CREATE OR REPLACE FUNCTION decrypt_credentials(encrypted BYTEA, key TEXT)
RETURNS JSONB AS $$
BEGIN
    RETURN pgp_sym_decrypt(encrypted, key)::JSONB;
EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'Failed to decrypt credentials: %', SQLERRM;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Add comments for documentation
COMMENT ON TABLE channel_configurations IS 'External platform integration settings';
COMMENT ON COLUMN channel_configurations.channel_id IS 'Unique identifier (UUID) for this channel configuration';
COMMENT ON COLUMN channel_configurations.platform IS 'External platform: WHATSAPP, TELEGRAM, or INSTAGRAM';
COMMENT ON COLUMN channel_configurations.name IS 'Human-readable name for this channel configuration';
COMMENT ON COLUMN channel_configurations.credentials IS 'Encrypted JSON containing API keys, tokens (use encrypt_credentials function)';
COMMENT ON COLUMN channel_configurations.webhook_url IS 'Callback URL for receiving messages from external platform';
COMMENT ON COLUMN channel_configurations.rate_limits IS 'Platform-specific rate limits (e.g., {"messages_per_second": 10, "daily_limit": 10000})';
COMMENT ON COLUMN channel_configurations.enabled IS 'Whether this channel configuration is active';

-- Example credential structures (stored encrypted):
-- WhatsApp: {"access_token": "...", "phone_number_id": "..."}
-- Telegram: {"bot_token": "..."}
-- Instagram: {"access_token": "...", "app_id": "..."}
