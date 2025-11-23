-- V003__create_channel_configurations_table.sql
-- Create channel_configurations table for connector credentials and rate limits

CREATE TABLE channel_configurations (
    config_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    channel VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    credentials JSONB NOT NULL, -- Encrypted using pgcrypto extension
    rate_limits JSONB DEFAULT '{
        "messages_per_second": 10,
        "daily_message_limit": 10000
    }'::jsonb,
    webhook_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT channel_check CHECK (channel IN ('WHATSAPP', 'TELEGRAM', 'INSTAGRAM')),
    CONSTRAINT unique_active_channel UNIQUE (channel, is_active)
);

-- Indexes
CREATE INDEX idx_channel_configurations_channel ON channel_configurations(channel);
CREATE INDEX idx_channel_configurations_is_active ON channel_configurations(is_active);
CREATE INDEX idx_channel_configurations_rate_limits ON channel_configurations USING GIN (rate_limits jsonb_path_ops);

-- Comments
COMMENT ON TABLE channel_configurations IS 'Stores connector configuration and credentials for each messaging platform';
COMMENT ON COLUMN channel_configurations.credentials IS 'ENCRYPTED JSONB field - use pgcrypto functions pgp_sym_encrypt/pgp_sym_decrypt for encryption at rest';
COMMENT ON COLUMN channel_configurations.rate_limits IS 'Platform-specific rate limiting configuration (messages per second, daily limits)';
COMMENT ON COLUMN channel_configurations.webhook_url IS 'Webhook endpoint for receiving messages from platform (e.g., WhatsApp Cloud API webhook)';
COMMENT ON CONSTRAINT unique_active_channel ON channel_configurations IS 'Only one active configuration per channel (prevents multiple WhatsApp configs being active simultaneously)';

-- Security Note: 
-- Credentials should be encrypted using pgcrypto:
--   INSERT: pgp_sym_encrypt('{"api_key": "secret"}', 'encryption_key')
--   SELECT: pgp_sym_decrypt(credentials::bytea, 'encryption_key')
