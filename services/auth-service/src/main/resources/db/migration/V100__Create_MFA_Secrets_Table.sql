-- MFA Secrets Table for TOTP Authentication
-- Version: V100
-- Date: 2025-11-10
-- Purpose: Store encrypted MFA secrets per user

CREATE TABLE IF NOT EXISTS mfa_secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    encrypted_secret TEXT NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    mfa_method VARCHAR(50) NOT NULL DEFAULT 'TOTP',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    encrypted_backup_codes TEXT,
    backup_codes_remaining INTEGER DEFAULT 10,
    failed_attempts INTEGER DEFAULT 0,
    last_verified_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    device_id VARCHAR(255),
    trusted_device BOOLEAN DEFAULT FALSE,
    trusted_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 0,

    CONSTRAINT fk_mfa_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_mfa_method CHECK (mfa_method IN ('TOTP', 'SMS', 'EMAIL', 'HARDWARE_KEY', 'BACKUP_CODE')),
    CONSTRAINT chk_failed_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT chk_backup_codes CHECK (backup_codes_remaining >= 0 AND backup_codes_remaining <= 10)
);

-- Indexes for performance
CREATE INDEX idx_mfa_user_id ON mfa_secrets(user_id);
CREATE INDEX idx_mfa_enabled ON mfa_secrets(enabled);
CREATE INDEX idx_mfa_expires_at ON mfa_secrets(expires_at);
CREATE INDEX idx_mfa_failed_attempts ON mfa_secrets(failed_attempts) WHERE failed_attempts >= 3;
CREATE INDEX idx_mfa_method ON mfa_secrets(mfa_method);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_mfa_secrets_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_mfa_secrets_updated_at
    BEFORE UPDATE ON mfa_secrets
    FOR EACH ROW
    EXECUTE FUNCTION update_mfa_secrets_updated_at();

-- Comments for documentation
COMMENT ON TABLE mfa_secrets IS 'Stores encrypted MFA secrets for user authentication';
COMMENT ON COLUMN mfa_secrets.encrypted_secret IS 'AES-256-GCM encrypted TOTP secret (Base32 encoded)';
COMMENT ON COLUMN mfa_secrets.key_version IS 'Encryption key version for key rotation support';
COMMENT ON COLUMN mfa_secrets.encrypted_backup_codes IS 'Encrypted comma-separated list of backup codes';
COMMENT ON COLUMN mfa_secrets.failed_attempts IS 'Counter for failed verification attempts (auto-disable at 5)';
COMMENT ON COLUMN mfa_secrets.trusted_device IS 'Whether this device is trusted (skip MFA for 30 days)';
