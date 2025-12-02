-- Tokenization Service Database Schema
-- PCI-DSS compliant tokenized sensitive data storage
-- Version: 1.0.0
-- Date: 2025-10-11

-- Create tokens table
CREATE TABLE IF NOT EXISTS tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) UNIQUE NOT NULL,
    encrypted_data TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    kms_key_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    usage_count INTEGER NOT NULL DEFAULT 0,
    metadata TEXT,
    created_from_ip VARCHAR(45),
    created_user_agent VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Create indexes for performance
CREATE UNIQUE INDEX IF NOT EXISTS idx_tokens_token ON tokens(token);
CREATE INDEX IF NOT EXISTS idx_tokens_user_id ON tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_tokens_expires_at ON tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_tokens_status ON tokens(status);
CREATE INDEX IF NOT EXISTS idx_tokens_type ON tokens(type);
CREATE INDEX IF NOT EXISTS idx_tokens_created_at ON tokens(created_at);
CREATE INDEX IF NOT EXISTS idx_tokens_user_status ON tokens(user_id, status);

-- Add table and column comments
COMMENT ON TABLE tokens IS 'PCI-DSS compliant tokenized sensitive data storage';
COMMENT ON COLUMN tokens.id IS 'Primary key (UUID)';
COMMENT ON COLUMN tokens.token IS 'Unique token identifier (cryptographically random, format: TYPE_32chars)';
COMMENT ON COLUMN tokens.encrypted_data IS 'Original sensitive data encrypted with AWS KMS';
COMMENT ON COLUMN tokens.type IS 'Token type (CARD, BANK_ACCOUNT, SSN, TAX_ID, etc.)';
COMMENT ON COLUMN tokens.user_id IS 'User ID who owns this token';
COMMENT ON COLUMN tokens.kms_key_id IS 'AWS KMS Key ID used for encryption';
COMMENT ON COLUMN tokens.status IS 'Token status (ACTIVE, EXPIRED, REVOKED, SUSPENDED)';
COMMENT ON COLUMN tokens.expires_at IS 'Token expiration timestamp';
COMMENT ON COLUMN tokens.last_used_at IS 'Last time token was used (for audit)';
COMMENT ON COLUMN tokens.usage_count IS 'Number of times token has been used';
COMMENT ON COLUMN tokens.metadata IS 'Optional metadata in JSON format';
COMMENT ON COLUMN tokens.created_from_ip IS 'IP address from where token was created';
COMMENT ON COLUMN tokens.created_user_agent IS 'User agent from where token was created';
COMMENT ON COLUMN tokens.version IS 'Optimistic locking version';

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_tokens_updated_at BEFORE UPDATE ON tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
