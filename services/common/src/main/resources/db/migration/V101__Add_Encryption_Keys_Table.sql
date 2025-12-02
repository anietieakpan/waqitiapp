-- V101__Add_Encryption_Keys_Table.sql
--
-- Creates encryption_keys table for key rotation and management
-- Supports AES-256-GCM field-level encryption with key versioning
--
-- Related to: P0-2 (PII/Financial Data Encryption at Rest)
-- Used by: EncryptedFinancialConverter.java, EncryptionService.java

CREATE TABLE IF NOT EXISTS encryption_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Key metadata
    key_id VARCHAR(100) NOT NULL UNIQUE,  -- e.g., "field-encryption-key-v1"
    key_type VARCHAR(50) NOT NULL,         -- e.g., "AES-256-GCM", "RSA-2048"
    key_purpose VARCHAR(100) NOT NULL,     -- e.g., "FIELD_ENCRYPTION", "JWT_SIGNING"

    -- Encrypted key material (encrypted with KMS master key)
    encrypted_key_material TEXT NOT NULL,
    kms_key_id VARCHAR(255),               -- AWS KMS Key ID used to encrypt the key

    -- Key versioning
    version INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    previous_key_id VARCHAR(100),          -- References previous version for rotation

    -- Key lifecycle
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP,
    deactivated_at TIMESTAMP,
    expires_at TIMESTAMP,
    rotated_at TIMESTAMP,

    -- Metadata
    created_by VARCHAR(100),
    rotation_schedule VARCHAR(50),         -- e.g., "MONTHLY", "QUARTERLY", "ANNUAL"
    algorithm_details JSONB,
    metadata JSONB,

    -- Audit trail
    last_used_at TIMESTAMP,
    usage_count BIGINT DEFAULT 0,

    CONSTRAINT chk_key_type CHECK (key_type IN ('AES-256-GCM', 'AES-128-GCM', 'RSA-2048', 'RSA-4096', 'HMAC-SHA256')),
    CONSTRAINT chk_key_purpose CHECK (key_purpose IN ('FIELD_ENCRYPTION', 'JWT_SIGNING', 'JWT_REFRESH', 'API_ENCRYPTION', 'BACKUP_ENCRYPTION'))
);

-- Index for active key lookup
CREATE INDEX IF NOT EXISTS idx_encryption_keys_active ON encryption_keys (key_purpose, is_active) WHERE is_active = true;

-- Index for key rotation queries
CREATE INDEX IF NOT EXISTS idx_encryption_keys_rotation ON encryption_keys (rotation_schedule, created_at);

-- Index for expiration monitoring
CREATE INDEX IF NOT EXISTS idx_encryption_keys_expires ON encryption_keys (expires_at) WHERE expires_at IS NOT NULL;

-- Index for version lookup
CREATE INDEX IF NOT EXISTS idx_encryption_keys_version ON encryption_keys (key_id, version);

-- Index for KMS key tracking
CREATE INDEX IF NOT EXISTS idx_encryption_keys_kms ON encryption_keys (kms_key_id) WHERE kms_key_id IS NOT NULL;

-- Trigger to update last_used_at and increment usage_count
-- This would be called from application code when a key is used
CREATE OR REPLACE FUNCTION update_key_usage()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_used_at = CURRENT_TIMESTAMP;
    NEW.usage_count = COALESCE(NEW.usage_count, 0) + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to ensure only one active key per purpose
CREATE OR REPLACE FUNCTION enforce_single_active_key()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_active = true THEN
        UPDATE encryption_keys
        SET is_active = false,
            deactivated_at = CURRENT_TIMESTAMP
        WHERE key_purpose = NEW.key_purpose
          AND is_active = true
          AND id != NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_enforce_single_active_key
    BEFORE INSERT OR UPDATE OF is_active ON encryption_keys
    FOR EACH ROW
    WHEN (NEW.is_active = true)
    EXECUTE FUNCTION enforce_single_active_key();

-- Trigger to set activated_at when key becomes active
CREATE OR REPLACE FUNCTION set_key_activation_time()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_active = true AND (OLD IS NULL OR OLD.is_active = false) THEN
        NEW.activated_at = CURRENT_TIMESTAMP;
    END IF;

    IF NEW.is_active = false AND OLD.is_active = true THEN
        NEW.deactivated_at = CURRENT_TIMESTAMP;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_set_key_activation_time
    BEFORE UPDATE OF is_active ON encryption_keys
    FOR EACH ROW
    EXECUTE FUNCTION set_key_activation_time();

-- Audit table for key access (for compliance)
CREATE TABLE IF NOT EXISTS encryption_key_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id UUID NOT NULL REFERENCES encryption_keys(id),
    accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accessed_by VARCHAR(100) NOT NULL,
    service_name VARCHAR(100),
    operation_type VARCHAR(50), -- e.g., "ENCRYPT", "DECRYPT", "ROTATE"
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_key_access_log_key_id ON encryption_key_access_log (key_id, accessed_at DESC);
CREATE INDEX IF NOT EXISTS idx_key_access_log_time ON encryption_key_access_log (accessed_at DESC);
CREATE INDEX IF NOT EXISTS idx_key_access_log_service ON encryption_key_access_log (service_name, accessed_at DESC);
CREATE INDEX IF NOT EXISTS idx_key_access_log_failure ON encryption_key_access_log (success, accessed_at DESC) WHERE success = false;

-- View for active keys
CREATE OR REPLACE VIEW active_encryption_keys AS
SELECT
    key_id,
    key_type,
    key_purpose,
    version,
    created_at,
    activated_at,
    rotation_schedule,
    usage_count,
    last_used_at
FROM encryption_keys
WHERE is_active = true
ORDER BY key_purpose, version DESC;

-- View for key rotation monitoring
CREATE OR REPLACE VIEW keys_requiring_rotation AS
SELECT
    key_id,
    key_purpose,
    version,
    created_at,
    rotation_schedule,
    CASE
        WHEN rotation_schedule = 'MONTHLY' THEN created_at + INTERVAL '30 days'
        WHEN rotation_schedule = 'QUARTERLY' THEN created_at + INTERVAL '90 days'
        WHEN rotation_schedule = 'ANNUAL' THEN created_at + INTERVAL '365 days'
        ELSE created_at + INTERVAL '90 days'
    END AS next_rotation_date,
    CURRENT_TIMESTAMP AS current_time
FROM encryption_keys
WHERE is_active = true
  AND (
      (rotation_schedule = 'MONTHLY' AND created_at < CURRENT_TIMESTAMP - INTERVAL '30 days') OR
      (rotation_schedule = 'QUARTERLY' AND created_at < CURRENT_TIMESTAMP - INTERVAL '90 days') OR
      (rotation_schedule = 'ANNUAL' AND created_at < CURRENT_TIMESTAMP - INTERVAL '365 days')
  )
ORDER BY created_at ASC;

-- Comments
COMMENT ON TABLE encryption_keys IS 'Stores encrypted encryption keys with versioning and rotation support';
COMMENT ON COLUMN encryption_keys.encrypted_key_material IS 'Base64-encoded key material encrypted with KMS master key';
COMMENT ON COLUMN encryption_keys.is_active IS 'Only one key per purpose can be active at a time';
COMMENT ON COLUMN encryption_keys.rotation_schedule IS 'How frequently the key should be rotated (PCI DSS requires annual rotation)';
COMMENT ON TABLE encryption_key_access_log IS 'Audit log of all encryption key access (required for PCI DSS compliance)';

-- Grant permissions
-- GRANT SELECT, INSERT, UPDATE ON encryption_keys TO waqiti_app_user;
-- GRANT SELECT, INSERT ON encryption_key_access_log TO waqiti_app_user;
-- GRANT SELECT ON active_encryption_keys TO waqiti_app_user;
-- GRANT SELECT ON keys_requiring_rotation TO waqiti_app_user;
