-- CRITICAL: PCI DSS Compliant Tokenized Cards Table Creation
-- 
-- This migration creates the tokenized_cards table with strict PCI DSS compliance:
-- - NO Primary Account Number (PAN) storage
-- - NO Card Verification Value (CVV) storage
-- - NO Track Data storage
-- - Only safe card metadata and tokens
-- 
-- SECURITY FEATURES:
-- - Unique token constraints
-- - User-based data isolation
-- - Audit trail columns
-- - Performance indexes
-- - Row-level security preparation
-- 
-- Author: Waqiti Security Team
-- Version: 1.0.0
-- Date: 2024-12-01

-- ============================================================================
-- Create tokenized_cards table with PCI DSS compliance
-- ============================================================================

CREATE TABLE IF NOT EXISTS tokenized_cards (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- CRITICAL: Secure token value (replaces PAN)
    token VARCHAR(50) NOT NULL UNIQUE,
    
    -- Safe card display data (PCI DSS compliant)
    last_four_digits CHAR(4) NOT NULL CHECK (last_four_digits ~ '^[0-9]{4}$'),
    card_type VARCHAR(20) NOT NULL CHECK (card_type IN ('VISA', 'MASTERCARD', 'AMEX', 'DISCOVER', 'UNKNOWN')),
    
    -- Card expiry information (safe to store per PCI DSS)
    expiry_month INTEGER NOT NULL CHECK (expiry_month >= 1 AND expiry_month <= 12),
    expiry_year INTEGER NOT NULL CHECK (expiry_year >= 2024 AND expiry_year <= 2099),
    
    -- Cardholder information (safe to store per PCI DSS)
    cardholder_name VARCHAR(100),
    
    -- User identification and access control
    user_id UUID NOT NULL,
    
    -- CRITICAL: Vault reference for encrypted PAN storage
    -- This points to HashiCorp Vault location - NEVER expose in API
    vault_path VARCHAR(255) NOT NULL,
    
    -- Token lifecycle management
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Token status and revocation
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(100),
    
    -- Usage tracking and analytics
    last_used_at TIMESTAMP WITH TIME ZONE,
    usage_count INTEGER NOT NULL DEFAULT 0 CHECK (usage_count >= 0),
    max_usage_count INTEGER DEFAULT 0 CHECK (max_usage_count >= 0),
    
    -- Audit and correlation
    correlation_id VARCHAR(50),
    
    -- Security classification
    security_level VARCHAR(20) NOT NULL DEFAULT 'STANDARD' 
        CHECK (security_level IN ('STANDARD', 'HIGH', 'CRITICAL')),
    
    -- Optional banking information
    issuing_bank VARCHAR(50),
    issuing_country CHAR(3),
    
    -- Environment and versioning
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION' 
        CHECK (environment IN ('PRODUCTION', 'STAGING', 'SANDBOX')),
    tokenization_version VARCHAR(10) NOT NULL DEFAULT '1.0',
    
    -- Custom metadata for business use cases
    metadata TEXT,
    
    -- Compliance flags
    pci_compliant BOOLEAN NOT NULL DEFAULT TRUE,
    audit_all_usage BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Network tokenization support (Apple Pay, Google Pay, etc.)
    network_token_id VARCHAR(50),
    network_provider VARCHAR(20),
    network_token_status VARCHAR(20),
    network_token_provisioned_at TIMESTAMP WITH TIME ZONE,
    
    -- Risk assessment
    risk_score INTEGER CHECK (risk_score >= 0 AND risk_score <= 100),
    fraud_score INTEGER CHECK (fraud_score >= 0 AND fraud_score <= 100),
    risk_assessment_date TIMESTAMP WITH TIME ZONE,
    
    -- Geographic and device information
    tokenized_location VARCHAR(100),
    tokenized_ip_address INET,
    device_fingerprint VARCHAR(100),
    device_type VARCHAR(20),
    
    -- Constraints to ensure data integrity
    CONSTRAINT tokenized_cards_token_format_check 
        CHECK (token ~ '^tok_[A-Z0-9]{8,}$'),
    
    CONSTRAINT tokenized_cards_expiry_future_check 
        CHECK (expires_at > CURRENT_TIMESTAMP),
    
    CONSTRAINT tokenized_cards_revocation_logic_check 
        CHECK ((is_active = FALSE AND revoked_at IS NOT NULL AND revocation_reason IS NOT NULL) OR 
               (is_active = TRUE AND revoked_at IS NULL)),
    
    CONSTRAINT tokenized_cards_max_usage_logic_check 
        CHECK (max_usage_count = 0 OR usage_count <= max_usage_count),
    
    CONSTRAINT tokenized_cards_network_token_logic_check 
        CHECK ((network_token_id IS NOT NULL AND network_provider IS NOT NULL) OR 
               (network_token_id IS NULL AND network_provider IS NULL)),
    
    -- PCI DSS compliance constraint
    CONSTRAINT tokenized_cards_pci_compliance_check 
        CHECK (pci_compliant = TRUE)
);

-- ============================================================================
-- Create indexes for performance and security
-- ============================================================================

-- Unique token index (already created by UNIQUE constraint, but explicit for clarity)
CREATE UNIQUE INDEX IF NOT EXISTS idx_tokenized_cards_token 
    ON tokenized_cards (token);

-- User-based access index (critical for data isolation)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_user_id 
    ON tokenized_cards (user_id);

-- User + token combination (for secure lookups)
CREATE UNIQUE INDEX IF NOT EXISTS idx_tokenized_cards_user_token 
    ON tokenized_cards (user_id, token);

-- Last 4 digits + user (for duplicate detection)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_last4_user 
    ON tokenized_cards (last_four_digits, user_id);

-- Active tokens index (for filtering)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_active 
    ON tokenized_cards (is_active) WHERE is_active = TRUE;

-- Token expiration index (for cleanup and monitoring)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_expires 
    ON tokenized_cards (expires_at);

-- Created date index (for analytics and reporting)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_created 
    ON tokenized_cards (created_at);

-- Last used index (for usage analytics)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_last_used 
    ON tokenized_cards (last_used_at) WHERE last_used_at IS NOT NULL;

-- Revoked tokens index (for audit and cleanup)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_revoked 
    ON tokenized_cards (revoked_at) WHERE revoked_at IS NOT NULL;

-- Security level index (for filtering by security classification)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_security_level 
    ON tokenized_cards (security_level);

-- Environment index (for environment-specific operations)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_environment 
    ON tokenized_cards (environment);

-- Network token index (for mobile payment lookups)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_network_token 
    ON tokenized_cards (network_token_id) WHERE network_token_id IS NOT NULL;

-- Risk score index (for high-risk token monitoring)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_risk_score 
    ON tokenized_cards (risk_score) WHERE risk_score IS NOT NULL;

-- Correlation ID index (for audit trail correlation)
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_correlation 
    ON tokenized_cards (correlation_id) WHERE correlation_id IS NOT NULL;

-- ============================================================================
-- Create updated_at trigger
-- ============================================================================

-- Function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_tokenized_cards_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update updated_at column
DROP TRIGGER IF EXISTS trigger_tokenized_cards_updated_at ON tokenized_cards;
CREATE TRIGGER trigger_tokenized_cards_updated_at
    BEFORE UPDATE ON tokenized_cards
    FOR EACH ROW
    EXECUTE FUNCTION update_tokenized_cards_updated_at();

-- ============================================================================
-- Create audit trigger for PCI DSS compliance
-- ============================================================================

-- Audit table for tokenized cards operations
CREATE TABLE IF NOT EXISTS tokenized_cards_audit (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name VARCHAR(50) NOT NULL DEFAULT 'tokenized_cards',
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    user_id UUID,
    token_id UUID,
    token_masked VARCHAR(50), -- Masked token for audit safety
    changed_by VARCHAR(100),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_values JSONB,
    new_values JSONB,
    correlation_id VARCHAR(50),
    client_ip INET,
    user_agent TEXT
);

-- Index for audit table
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_audit_changed_at 
    ON tokenized_cards_audit (changed_at);
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_audit_token_id 
    ON tokenized_cards_audit (token_id);
CREATE INDEX IF NOT EXISTS idx_tokenized_cards_audit_user_id 
    ON tokenized_cards_audit (user_id);

-- Function to create audit trail
CREATE OR REPLACE FUNCTION audit_tokenized_cards()
RETURNS TRIGGER AS $$
DECLARE
    old_values JSONB;
    new_values JSONB;
    token_masked VARCHAR(50);
BEGIN
    -- Create masked token for audit safety
    IF TG_OP = 'DELETE' THEN
        token_masked = CASE 
            WHEN OLD.token IS NOT NULL AND LENGTH(OLD.token) >= 8 THEN 
                SUBSTRING(OLD.token, 1, 4) || '****' || SUBSTRING(OLD.token, LENGTH(OLD.token) - 3)
            ELSE '****'
        END;
        old_values = row_to_json(OLD)::jsonb;
        new_values = NULL;
    ELSIF TG_OP = 'UPDATE' THEN
        token_masked = CASE 
            WHEN NEW.token IS NOT NULL AND LENGTH(NEW.token) >= 8 THEN 
                SUBSTRING(NEW.token, 1, 4) || '****' || SUBSTRING(NEW.token, LENGTH(NEW.token) - 3)
            ELSE '****'
        END;
        old_values = row_to_json(OLD)::jsonb;
        new_values = row_to_json(NEW)::jsonb;
    ELSE -- INSERT
        token_masked = CASE 
            WHEN NEW.token IS NOT NULL AND LENGTH(NEW.token) >= 8 THEN 
                SUBSTRING(NEW.token, 1, 4) || '****' || SUBSTRING(NEW.token, LENGTH(NEW.token) - 3)
            ELSE '****'
        END;
        old_values = NULL;
        new_values = row_to_json(NEW)::jsonb;
    END IF;

    -- Insert audit record
    INSERT INTO tokenized_cards_audit (
        operation,
        user_id,
        token_id,
        token_masked,
        changed_by,
        old_values,
        new_values,
        correlation_id
    ) VALUES (
        TG_OP,
        COALESCE(NEW.user_id, OLD.user_id),
        COALESCE(NEW.id, OLD.id),
        token_masked,
        current_user,
        old_values,
        new_values,
        COALESCE(NEW.correlation_id, OLD.correlation_id)
    );

    -- Return appropriate record
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create audit triggers
DROP TRIGGER IF EXISTS trigger_tokenized_cards_audit ON tokenized_cards;
CREATE TRIGGER trigger_tokenized_cards_audit
    AFTER INSERT OR UPDATE OR DELETE ON tokenized_cards
    FOR EACH ROW
    EXECUTE FUNCTION audit_tokenized_cards();

-- ============================================================================
-- Create views for safe data access
-- ============================================================================

-- View for safe token display (no vault paths or sensitive data)
CREATE OR REPLACE VIEW tokenized_cards_safe AS
SELECT 
    id,
    token,
    last_four_digits,
    card_type,
    expiry_month,
    expiry_year,
    cardholder_name,
    user_id,
    created_at,
    updated_at,
    expires_at,
    is_active,
    revoked_at,
    revocation_reason,
    last_used_at,
    usage_count,
    max_usage_count,
    security_level,
    issuing_bank,
    issuing_country,
    environment,
    tokenization_version,
    pci_compliant,
    network_token_id,
    network_provider,
    network_token_status,
    network_token_provisioned_at,
    risk_score,
    fraud_score,
    risk_assessment_date,
    device_type,
    -- Computed fields
    CASE 
        WHEN expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP THEN FALSE
        ELSE TRUE 
    END as is_valid,
    CASE 
        WHEN expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP + INTERVAL '30 days' THEN TRUE
        ELSE FALSE 
    END as is_expiring_soon,
    CASE 
        WHEN max_usage_count > 0 AND usage_count >= max_usage_count THEN TRUE
        ELSE FALSE 
    END as is_usage_limit_reached,
    card_type || ' ending in ' || last_four_digits as display_name
FROM tokenized_cards;

-- View for analytics and reporting (aggregated data only)
CREATE OR REPLACE VIEW tokenized_cards_analytics AS
SELECT 
    user_id,
    card_type,
    security_level,
    environment,
    COUNT(*) as total_tokens,
    COUNT(*) FILTER (WHERE is_active = TRUE) as active_tokens,
    COUNT(*) FILTER (WHERE is_active = FALSE) as inactive_tokens,
    AVG(usage_count) as avg_usage_count,
    MAX(usage_count) as max_usage_count,
    MIN(created_at) as first_token_created,
    MAX(created_at) as last_token_created,
    COUNT(*) FILTER (WHERE expires_at <= CURRENT_TIMESTAMP + INTERVAL '30 days') as expiring_soon_count
FROM tokenized_cards
GROUP BY user_id, card_type, security_level, environment;

-- ============================================================================
-- Insert default configuration and reference data
-- ============================================================================

-- Create table for tokenization configuration
CREATE TABLE IF NOT EXISTS tokenization_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert default configuration
INSERT INTO tokenization_config (config_key, config_value, description) VALUES
('pci_version', '4.0', 'PCI DSS version compliance'),
('tokenization_version', '1.0', 'Current tokenization algorithm version'),
('max_tokens_per_user', '10', 'Maximum tokens allowed per user'),
('default_token_expiry_years', '3', 'Default token expiration in years'),
('audit_retention_days', '2555', 'Audit log retention period (7 years)'),
('enable_format_preserving', 'true', 'Enable format-preserving tokenization'),
('enable_luhn_compliance', 'true', 'Enable Luhn algorithm compliance'),
('vault_path_prefix', 'payment-cards', 'Vault storage path prefix'),
('encryption_key_rotation_days', '90', 'Encryption key rotation frequency'),
('security_level_required', 'STANDARD', 'Minimum required security level')
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================================
-- Create stored procedures for common operations
-- ============================================================================

-- Procedure to safely clean up expired tokens
CREATE OR REPLACE FUNCTION cleanup_expired_tokens(
    cleanup_before_days INTEGER DEFAULT 30
)
RETURNS TABLE(
    tokens_processed INTEGER,
    tokens_deactivated INTEGER,
    audit_records_created INTEGER
) AS $$
DECLARE
    cutoff_date TIMESTAMP WITH TIME ZONE;
    processed_count INTEGER := 0;
    deactivated_count INTEGER := 0;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - (cleanup_before_days || ' days')::INTERVAL;
    
    -- Get count of tokens to process
    SELECT COUNT(*) INTO processed_count
    FROM tokenized_cards
    WHERE expires_at <= cutoff_date AND is_active = TRUE;
    
    -- Deactivate expired tokens
    UPDATE tokenized_cards
    SET 
        is_active = FALSE,
        revoked_at = CURRENT_TIMESTAMP,
        revocation_reason = 'EXPIRED_CLEANUP',
        updated_at = CURRENT_TIMESTAMP
    WHERE expires_at <= cutoff_date AND is_active = TRUE;
    
    GET DIAGNOSTICS deactivated_count = ROW_COUNT;
    
    RETURN QUERY SELECT processed_count, deactivated_count, deactivated_count;
END;
$$ LANGUAGE plpgsql;

-- Procedure to get user token statistics
CREATE OR REPLACE FUNCTION get_user_token_stats(input_user_id UUID)
RETURNS TABLE(
    total_tokens INTEGER,
    active_tokens INTEGER,
    expired_tokens INTEGER,
    revoked_tokens INTEGER,
    total_usage_count BIGINT,
    last_token_created TIMESTAMP WITH TIME ZONE,
    last_token_used TIMESTAMP WITH TIME ZONE
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COUNT(*)::INTEGER as total_tokens,
        COUNT(*) FILTER (WHERE is_active = TRUE)::INTEGER as active_tokens,
        COUNT(*) FILTER (WHERE expires_at <= CURRENT_TIMESTAMP)::INTEGER as expired_tokens,
        COUNT(*) FILTER (WHERE is_active = FALSE AND revoked_at IS NOT NULL)::INTEGER as revoked_tokens,
        COALESCE(SUM(usage_count), 0) as total_usage_count,
        MAX(created_at) as last_token_created,
        MAX(last_used_at) as last_token_used
    FROM tokenized_cards
    WHERE user_id = input_user_id;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- Grant appropriate permissions (adjust based on your security model)
-- ============================================================================

-- Grant permissions to payment service user (replace 'payment_service_user' with actual username)
-- GRANT SELECT, INSERT, UPDATE ON tokenized_cards TO payment_service_user;
-- GRANT SELECT ON tokenized_cards_safe TO payment_service_user;
-- GRANT SELECT ON tokenized_cards_analytics TO payment_service_user;
-- GRANT SELECT, INSERT ON tokenized_cards_audit TO payment_service_user;
-- GRANT SELECT, UPDATE ON tokenization_config TO payment_service_user;
-- GRANT EXECUTE ON FUNCTION cleanup_expired_tokens(INTEGER) TO payment_service_user;
-- GRANT EXECUTE ON FUNCTION get_user_token_stats(UUID) TO payment_service_user;

-- ============================================================================
-- Comments for documentation
-- ============================================================================

COMMENT ON TABLE tokenized_cards IS 'PCI DSS compliant storage for tokenized payment card data. Contains NO sensitive card information.';
COMMENT ON COLUMN tokenized_cards.token IS 'Secure token that replaces PAN for all operations';
COMMENT ON COLUMN tokenized_cards.vault_path IS 'CRITICAL: Reference to encrypted PAN storage in HashiCorp Vault';
COMMENT ON COLUMN tokenized_cards.last_four_digits IS 'Last 4 digits of PAN - safe to display per PCI DSS';
COMMENT ON COLUMN tokenized_cards.user_id IS 'User identification for access control and data isolation';
COMMENT ON COLUMN tokenized_cards.pci_compliant IS 'PCI DSS compliance flag - must always be TRUE';

COMMENT ON TABLE tokenized_cards_audit IS 'Audit trail for all tokenized_cards operations - required for PCI DSS compliance';
COMMENT ON VIEW tokenized_cards_safe IS 'Safe view of tokenized cards without vault paths or sensitive data';
COMMENT ON VIEW tokenized_cards_analytics IS 'Aggregated analytics view for reporting and monitoring';

-- ============================================================================
-- Verify table creation and constraints
-- ============================================================================

-- Verify table exists and has correct structure
DO $$
DECLARE
    table_exists BOOLEAN;
    constraint_count INTEGER;
    index_count INTEGER;
BEGIN
    -- Check if table exists
    SELECT EXISTS (
        SELECT FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'tokenized_cards'
    ) INTO table_exists;
    
    IF NOT table_exists THEN
        RAISE EXCEPTION 'CRITICAL: tokenized_cards table was not created successfully';
    END IF;
    
    -- Check constraints
    SELECT COUNT(*) INTO constraint_count
    FROM information_schema.table_constraints
    WHERE table_name = 'tokenized_cards' 
    AND constraint_type IN ('CHECK', 'UNIQUE', 'PRIMARY KEY');
    
    IF constraint_count < 8 THEN
        RAISE WARNING 'Some constraints may be missing from tokenized_cards table';
    END IF;
    
    -- Check indexes
    SELECT COUNT(*) INTO index_count
    FROM pg_indexes
    WHERE tablename = 'tokenized_cards';
    
    IF index_count < 10 THEN
        RAISE WARNING 'Some indexes may be missing from tokenized_cards table';
    END IF;
    
    RAISE NOTICE 'SUCCESS: Tokenized cards table created with % constraints and % indexes', 
        constraint_count, index_count;
END $$;

-- ============================================================================
-- Migration completion log
-- ============================================================================

INSERT INTO tokenization_config (config_key, config_value, description) VALUES
('migration_v2024_12_01_001', CURRENT_TIMESTAMP::TEXT, 'Tokenized cards table creation migration completed')
ON CONFLICT (config_key) DO UPDATE SET 
    config_value = CURRENT_TIMESTAMP::TEXT,
    updated_at = CURRENT_TIMESTAMP;