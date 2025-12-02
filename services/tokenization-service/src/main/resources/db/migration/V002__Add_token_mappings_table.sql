-- Add Token Mappings Table
-- Date: 2025-10-18
-- Description: Bidirectional mapping between tokens and original data hashes for faster lookups
-- Critical Fix: Entity TokenMapping expects this table but it was missing from initial migration

-- ============================================================================
-- TOKEN MAPPINGS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS token_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id UUID NOT NULL,

    -- Token reference
    token VARCHAR(64) NOT NULL,

    -- Original data hash (for reverse lookup without decryption)
    original_data_hash VARCHAR(64) NOT NULL, -- SHA-256 hash of original sensitive data
    hash_algorithm VARCHAR(20) DEFAULT 'SHA-256' NOT NULL,

    -- Data classification
    data_type VARCHAR(50) NOT NULL, -- CARD_NUMBER, ROUTING_NUMBER, ACCOUNT_NUMBER, SSN, etc.
    data_format VARCHAR(20), -- Format of original data (e.g., PAN_16, ROUTING_9)

    -- Last 4 digits (for display purposes - PCI-DSS compliant)
    last_four VARCHAR(4),
    first_six VARCHAR(6), -- First 6 of PAN (BIN) for card type identification

    -- Token metadata
    token_family VARCHAR(20), -- PAYMENT, IDENTITY, TAX, BANKING
    token_scope VARCHAR(30), -- SINGLE_USE, MULTI_USE, SESSION
    max_usage_count INTEGER,
    current_usage_count INTEGER DEFAULT 0,

    -- Vault information
    vault_id VARCHAR(100), -- External vault ID if using third-party tokenization
    vault_provider VARCHAR(50), -- WAQITI, STRIPE, ADYEN, CYBERSOURCE

    -- PCI-DSS compliance tracking
    pci_scope BOOLEAN DEFAULT TRUE, -- Whether this token is in PCI scope
    encryption_standard VARCHAR(30) DEFAULT 'AES-256-GCM',
    key_rotation_date DATE,
    next_rotation_date DATE,

    -- Detokenization tracking
    detokenization_count INTEGER DEFAULT 0,
    last_detokenized_at TIMESTAMP,
    last_detokenized_by UUID,
    last_detokenized_ip VARCHAR(45),

    -- Lifecycle
    is_active BOOLEAN DEFAULT TRUE,
    deactivated_at TIMESTAMP,
    deactivated_reason VARCHAR(255),

    -- Audit trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP, -- Soft delete for audit
    version BIGINT DEFAULT 0,

    -- Foreign key to tokens table
    CONSTRAINT fk_token_mapping_token FOREIGN KEY (token_id)
        REFERENCES tokens(id) ON DELETE CASCADE,

    -- Unique constraints
    CONSTRAINT uq_token_mapping_token UNIQUE (token),
    CONSTRAINT uq_token_mapping_hash UNIQUE (original_data_hash, data_type),

    -- Check constraints
    CONSTRAINT chk_last_four_length CHECK (last_four IS NULL OR LENGTH(last_four) = 4),
    CONSTRAINT chk_first_six_length CHECK (first_six IS NULL OR LENGTH(first_six) = 6)
);

-- ============================================================================
-- TOKEN USAGE AUDIT TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS token_usage_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id UUID NOT NULL,
    token_mapping_id UUID NOT NULL,

    -- Usage details
    operation_type VARCHAR(30) NOT NULL, -- TOKENIZE, DETOKENIZE, VALIDATE, REFRESH
    operation_result VARCHAR(20) NOT NULL, -- SUCCESS, FAILURE, DENIED
    failure_reason TEXT,

    -- Context
    user_id UUID,
    service_name VARCHAR(100), -- Which service used the token
    api_endpoint VARCHAR(255), -- Which API endpoint
    request_id UUID, -- For correlation

    -- Security context
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_id VARCHAR(255),
    session_id VARCHAR(255),

    -- Geolocation
    country_code VARCHAR(2),
    city VARCHAR(100),
    is_vpn BOOLEAN DEFAULT FALSE,
    is_tor BOOLEAN DEFAULT FALSE,

    -- Timestamp
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_token_usage_token FOREIGN KEY (token_id)
        REFERENCES tokens(id) ON DELETE CASCADE,
    CONSTRAINT fk_token_usage_mapping FOREIGN KEY (token_mapping_id)
        REFERENCES token_mappings(id) ON DELETE CASCADE
);

-- ============================================================================
-- TOKEN VAULT KEYS TABLE (Key management)
-- ============================================================================
CREATE TABLE IF NOT EXISTS token_vault_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Key identification
    key_id VARCHAR(255) UNIQUE NOT NULL, -- KMS Key ID or key alias
    key_alias VARCHAR(100),
    key_version INTEGER DEFAULT 1,

    -- Key details
    key_type VARCHAR(30) NOT NULL, -- MASTER, DATA, WRAPPING
    encryption_algorithm VARCHAR(50) DEFAULT 'AES-256-GCM' NOT NULL,
    key_length_bits INTEGER DEFAULT 256,

    -- Key provider
    provider VARCHAR(50) NOT NULL, -- AWS_KMS, AZURE_KEY_VAULT, HASHICORP_VAULT, INTERNAL
    provider_region VARCHAR(50),
    provider_key_arn TEXT, -- Full ARN for cloud providers

    -- Key lifecycle
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE, ROTATING, ROTATED, DEPRECATED, REVOKED
    created_date DATE NOT NULL,
    activated_date DATE,
    rotation_date DATE,
    expiration_date DATE,

    -- Usage
    is_primary BOOLEAN DEFAULT FALSE, -- Primary key for new tokenizations
    is_backup BOOLEAN DEFAULT FALSE,
    tokens_encrypted_count INTEGER DEFAULT 0,

    -- Compliance
    fips_140_2_compliant BOOLEAN DEFAULT FALSE,
    pci_dss_compliant BOOLEAN DEFAULT TRUE,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,

    -- Only one primary key at a time
    CONSTRAINT uq_primary_key UNIQUE (is_primary) WHERE is_primary = TRUE
);

-- ============================================================================
-- INDEXES FOR PERFORMANCE
-- ============================================================================

-- Token mappings indexes
CREATE INDEX idx_token_mappings_token_id ON token_mappings(token_id);
CREATE INDEX idx_token_mappings_token ON token_mappings(token);
CREATE INDEX idx_token_mappings_hash ON token_mappings(original_data_hash);
CREATE INDEX idx_token_mappings_type ON token_mappings(data_type);
CREATE INDEX idx_token_mappings_active ON token_mappings(is_active, data_type) WHERE is_active = TRUE;
CREATE INDEX idx_token_mappings_last_four ON token_mappings(last_four, data_type) WHERE last_four IS NOT NULL;
CREATE INDEX idx_token_mappings_vault ON token_mappings(vault_provider, vault_id) WHERE vault_provider IS NOT NULL;
CREATE INDEX idx_token_mappings_rotation ON token_mappings(next_rotation_date)
    WHERE next_rotation_date IS NOT NULL AND is_active = TRUE;

-- Token usage audit indexes
CREATE INDEX idx_token_usage_token ON token_usage_audit(token_id, occurred_at DESC);
CREATE INDEX idx_token_usage_mapping ON token_usage_audit(token_mapping_id, occurred_at DESC);
CREATE INDEX idx_token_usage_operation ON token_usage_audit(operation_type, operation_result);
CREATE INDEX idx_token_usage_user ON token_usage_audit(user_id, occurred_at DESC);
CREATE INDEX idx_token_usage_service ON token_usage_audit(service_name, occurred_at DESC);
CREATE INDEX idx_token_usage_time ON token_usage_audit(occurred_at DESC);
CREATE INDEX idx_token_usage_failed ON token_usage_audit(operation_result, occurred_at DESC)
    WHERE operation_result = 'FAILURE';
CREATE INDEX idx_token_usage_security ON token_usage_audit(is_vpn, is_tor, occurred_at DESC)
    WHERE is_vpn = TRUE OR is_tor = TRUE;

-- Token vault keys indexes
CREATE INDEX idx_vault_keys_status ON token_vault_keys(status);
CREATE INDEX idx_vault_keys_provider ON token_vault_keys(provider);
CREATE INDEX idx_vault_keys_primary ON token_vault_keys(is_primary) WHERE is_primary = TRUE;
CREATE INDEX idx_vault_keys_expiration ON token_vault_keys(expiration_date)
    WHERE expiration_date IS NOT NULL AND status = 'ACTIVE';

-- ============================================================================
-- UPDATE TIMESTAMP TRIGGERS
-- ============================================================================

CREATE TRIGGER update_token_mappings_updated_at BEFORE UPDATE ON token_mappings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_token_vault_keys_updated_at BEFORE UPDATE ON token_vault_keys
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- TRIGGERS FOR USAGE TRACKING
-- ============================================================================

-- Auto-increment usage count on token_mappings
CREATE OR REPLACE FUNCTION increment_token_usage()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.operation_type IN ('DETOKENIZE', 'VALIDATE') AND NEW.operation_result = 'SUCCESS' THEN
        UPDATE token_mappings
        SET current_usage_count = current_usage_count + 1,
            last_detokenized_at = NEW.occurred_at,
            last_detokenized_by = NEW.user_id,
            last_detokenized_ip = NEW.ip_address
        WHERE id = NEW.token_mapping_id;

        -- Also update the main tokens table
        UPDATE tokens
        SET usage_count = usage_count + 1,
            last_used_at = NEW.occurred_at
        WHERE id = NEW.token_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER track_token_usage AFTER INSERT ON token_usage_audit
    FOR EACH ROW EXECUTE FUNCTION increment_token_usage();

-- ============================================================================
-- PARTITIONING PREPARATION (for audit table)
-- ============================================================================

-- Token usage audit should be partitioned by month for performance
COMMENT ON TABLE token_usage_audit IS 'PCI-DSS audit trail for all token operations. Consider partitioning by month for optimal performance.';

-- ============================================================================
-- TABLE COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE token_mappings IS 'Bidirectional mapping between tokens and original data hashes. Enables fast lookups without decryption. Critical for TokenMapping entity.';
COMMENT ON TABLE token_vault_keys IS 'Master key management for tokenization vault. Supports key rotation and multi-provider encryption.';
COMMENT ON TABLE token_usage_audit IS 'Complete audit trail of all token operations for PCI-DSS and SOX compliance. Records every tokenization and detokenization event.';

COMMENT ON COLUMN token_mappings.original_data_hash IS 'SHA-256 hash of original sensitive data. Enables duplicate detection and reverse lookup without storing plaintext.';
COMMENT ON COLUMN token_mappings.last_four IS 'Last 4 digits of sensitive data (PCI-DSS compliant display).';
COMMENT ON COLUMN token_mappings.first_six IS 'First 6 digits (BIN) for card type identification without full PAN exposure.';
COMMENT ON COLUMN token_mappings.pci_scope IS 'Whether this token is within PCI-DSS scope (true for card data).';

-- ============================================================================
-- SEED DATA - Initialize primary vault key
-- ============================================================================

-- Note: In production, this would be created through secure key provisioning process
-- This is just a placeholder for the schema structure
INSERT INTO token_vault_keys (
    key_id,
    key_alias,
    key_type,
    encryption_algorithm,
    provider,
    status,
    created_date,
    is_primary,
    pci_dss_compliant
) VALUES (
    'waqiti-tokenization-master-key-v1',
    'tokenization-master',
    'MASTER',
    'AES-256-GCM',
    'AWS_KMS',
    'ACTIVE',
    CURRENT_DATE,
    TRUE,
    TRUE
) ON CONFLICT (key_id) DO NOTHING;
