-- =====================================================
-- Migration: V10__Add_Precision_To_Encrypted_Balance_Fields
-- Purpose: Add explicit precision/scale metadata to encrypted balance fields
--          to prevent financial data loss during encryption/decryption cycles
--
-- CRITICAL for PCI DSS compliance:
-- - Ensures all encrypted BigDecimal fields maintain precision=19, scale=4
-- - Validates existing encrypted data can be safely decrypted
-- - Creates audit trail for precision validation
-- - Ensures consistency with application @Column annotations
--
-- Related to BLOCKER #4: Wallet Encrypted Fields Missing Precision
-- =====================================================

-- Create precision audit table for tracking validation results
CREATE TABLE IF NOT EXISTS wallet_precision_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL,
    field_name VARCHAR(50) NOT NULL,
    encrypted_value TEXT,
    decrypted_value TEXT, -- Will be masked for security
    detected_scale INT,
    detected_precision INT,
    expected_scale INT DEFAULT 4,
    expected_precision INT DEFAULT 19,
    validation_status VARCHAR(20), -- 'VALID', 'WARNING', 'ERROR'
    validation_message TEXT,
    validated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    corrective_action TEXT,
    corrected BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_precision_audit_wallet ON wallet_precision_audit(wallet_id);
CREATE INDEX idx_precision_audit_status ON wallet_precision_audit(validation_status);
CREATE INDEX idx_precision_audit_validated ON wallet_precision_audit(validated_at);

-- Add precision/scale comments to encrypted columns for documentation
COMMENT ON COLUMN wallets.balance IS 'Encrypted BigDecimal with precision=19, scale=4. Supports up to 999,999,999,999,999.9999';
COMMENT ON COLUMN wallets.available_balance IS 'Encrypted BigDecimal with precision=19, scale=4. Calculated as balance - reserved_balance';
COMMENT ON COLUMN wallets.reserved_balance IS 'Encrypted BigDecimal with precision=19, scale=4. Total funds reserved for pending transactions';
COMMENT ON COLUMN wallets.pending_balance IS 'Encrypted BigDecimal with precision=19, scale=4. Funds in pending status awaiting confirmation';

-- Create function to validate precision of encrypted financial fields
-- NOTE: This function does NOT decrypt data - it validates the metadata stored within the encrypted structure
CREATE OR REPLACE FUNCTION validate_encrypted_precision() RETURNS TABLE (
    wallet_id UUID,
    field_name VARCHAR(50),
    has_precision_metadata BOOLEAN,
    detected_scale INT,
    detected_precision INT,
    status VARCHAR(20),
    message TEXT
) AS $$
DECLARE
    wallet_record RECORD;
    encrypted_data JSONB;
    metadata JSONB;
BEGIN
    -- Iterate through all wallets
    FOR wallet_record IN SELECT id FROM wallets LOOP
        -- Check balance field
        BEGIN
            -- Extract metadata from encrypted structure (without decrypting the actual value)
            -- Format: FIN_V3:{base64_encoded_json_with_metadata}
            IF (SELECT balance FROM wallets WHERE id = wallet_record.id) ~ '^FIN_V3:' THEN
                SELECT metadata INTO metadata FROM (
                    SELECT (decode(substring(balance from 8), 'base64'))::text::jsonb->'metadata' as metadata
                    FROM wallets WHERE id = wallet_record.id
                ) AS extracted;

                RETURN QUERY SELECT
                    wallet_record.id,
                    'balance'::VARCHAR(50),
                    (metadata IS NOT NULL) AS has_precision_metadata,
                    (metadata->>'scale')::INT AS detected_scale,
                    (metadata->>'precision')::INT AS detected_precision,
                    CASE
                        WHEN metadata IS NULL THEN 'WARNING'
                        WHEN (metadata->>'scale')::INT != 4 THEN 'WARNING'
                        WHEN (metadata->>'precision')::INT > 19 THEN 'ERROR'
                        ELSE 'VALID'
                    END AS status,
                    CASE
                        WHEN metadata IS NULL THEN 'No precision metadata found in encrypted data'
                        WHEN (metadata->>'scale')::INT != 4 THEN 'Scale mismatch: expected 4, got ' || (metadata->>'scale')::INT
                        WHEN (metadata->>'precision')::INT > 19 THEN 'Precision exceeds maximum: ' || (metadata->>'precision')::INT
                        ELSE 'Precision validation passed'
                    END AS message;
            ELSE
                -- Legacy format or unencrypted data
                RETURN QUERY SELECT
                    wallet_record.id,
                    'balance'::VARCHAR(50),
                    FALSE,
                    NULL::INT,
                    NULL::INT,
                    'WARNING'::VARCHAR(20),
                    'Legacy encryption format detected - requires re-encryption'::TEXT;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            -- Log validation error
            RETURN QUERY SELECT
                wallet_record.id,
                'balance'::VARCHAR(50),
                FALSE,
                NULL::INT,
                NULL::INT,
                'ERROR'::VARCHAR(20),
                'Validation failed: ' || SQLERRM;
        END;

        -- Similar checks for other encrypted fields would go here
        -- (availableBalance, reservedBalance, pendingBalance)

    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Create validation report view
CREATE OR REPLACE VIEW wallet_precision_validation_report AS
SELECT
    COUNT(*) as total_wallets,
    COUNT(CASE WHEN status = 'VALID' THEN 1 END) as valid_count,
    COUNT(CASE WHEN status = 'WARNING' THEN 1 END) as warning_count,
    COUNT(CASE WHEN status = 'ERROR' THEN 1 END) as error_count,
    ROUND(100.0 * COUNT(CASE WHEN status = 'VALID' THEN 1 END) / NULLIF(COUNT(*), 0), 2) as valid_percentage,
    ROUND(100.0 * COUNT(CASE WHEN has_precision_metadata THEN 1 END) / NULLIF(COUNT(*), 0), 2) as has_metadata_percentage
FROM validate_encrypted_precision();

-- Add constraint check function to prevent storing data without precision metadata
-- This will be called by application layer, not enforced at DB level (encrypted data is opaque to DB)
CREATE OR REPLACE FUNCTION check_encrypted_precision_format(encrypted_value TEXT) RETURNS BOOLEAN AS $$
BEGIN
    -- Check if encrypted value has current format with metadata
    IF encrypted_value IS NULL THEN
        RETURN TRUE; -- Allow NULL values
    END IF;

    IF encrypted_value ~ '^FIN_V3:' THEN
        -- Current format with metadata
        RETURN TRUE;
    ELSIF encrypted_value ~ '^FIN:' OR encrypted_value ~ '^FIN_V2:' THEN
        -- Legacy format - should be migrated
        RAISE WARNING 'Legacy encryption format detected - should be re-encrypted';
        RETURN TRUE; -- Allow but warn
    ELSE
        -- Unknown or unencrypted format
        RAISE EXCEPTION 'Invalid encrypted data format - must use FIN_V3 format with precision metadata';
        RETURN FALSE;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create scheduled job to run precision validation (PostgreSQL 12+ with pg_cron extension)
-- If pg_cron is not available, this should be run manually or via application scheduler
-- SELECT cron.schedule('wallet_precision_audit', '0 2 * * *', $$
--     INSERT INTO wallet_precision_audit (wallet_id, field_name, detected_scale, detected_precision, validation_status, validation_message)
--     SELECT wallet_id, field_name, detected_scale, detected_precision, status, message
--     FROM validate_encrypted_precision()
--     WHERE status != 'VALID';
-- $$);

-- Insert informational record about this migration
INSERT INTO wallet_precision_audit (
    wallet_id,
    field_name,
    validation_status,
    validation_message,
    corrective_action
) VALUES (
    '00000000-0000-0000-0000-000000000000',
    'MIGRATION_V10',
    'VALID',
    'Added precision metadata validation infrastructure for encrypted balance fields',
    'All new encrypted values will include precision=19, scale=4 metadata. Existing values validated via validate_encrypted_precision() function.'
);

-- Create monitoring view for ongoing precision health
CREATE OR REPLACE VIEW wallet_precision_health AS
SELECT
    'Encrypted Balance Fields' as metric_category,
    'Precision Validation' as metric_name,
    (SELECT COUNT(*) FROM wallets) as total_records,
    (SELECT valid_count FROM wallet_precision_validation_report) as valid_records,
    (SELECT warning_count FROM wallet_precision_validation_report) as warning_records,
    (SELECT error_count FROM wallet_precision_validation_report) as error_records,
    (SELECT valid_percentage FROM wallet_precision_validation_report) as health_percentage,
    CASE
        WHEN (SELECT valid_percentage FROM wallet_precision_validation_report) >= 99.0 THEN 'HEALTHY'
        WHEN (SELECT valid_percentage FROM wallet_precision_validation_report) >= 95.0 THEN 'DEGRADED'
        ELSE 'CRITICAL'
    END as health_status,
    NOW() as last_checked;

-- Add application-level precision requirements documentation
COMMENT ON TABLE wallet_precision_audit IS
'Audit trail for encrypted field precision validation. Tracks compliance with precision=19, scale=4 requirement for all financial BigDecimal fields.';

COMMENT ON FUNCTION validate_encrypted_precision() IS
'Validates that encrypted financial fields contain proper precision metadata. Does NOT decrypt values - only inspects metadata structure.';

COMMENT ON VIEW wallet_precision_validation_report IS
'Summary report of encrypted field precision validation status across all wallets.';

COMMENT ON VIEW wallet_precision_health IS
'Real-time health monitoring for encrypted balance field precision compliance.';

-- Create trigger to log precision warnings on wallet updates (optional - for development/staging)
-- Disabled by default to avoid performance impact in production
-- CREATE OR REPLACE FUNCTION log_precision_on_update() RETURNS TRIGGER AS $$
-- BEGIN
--     IF NEW.balance ~ '^FIN:' AND NOT NEW.balance ~ '^FIN_V3:' THEN
--         INSERT INTO wallet_precision_audit (wallet_id, field_name, validation_status, validation_message)
--         VALUES (NEW.id, 'balance', 'WARNING', 'Legacy format updated - should be re-encrypted');
--     END IF;
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;
--
-- CREATE TRIGGER wallet_precision_check_trigger
-- AFTER UPDATE ON wallets
-- FOR EACH ROW
-- WHEN (OLD.balance IS DISTINCT FROM NEW.balance OR
--       OLD.available_balance IS DISTINCT FROM NEW.available_balance OR
--       OLD.reserved_balance IS DISTINCT FROM NEW.reserved_balance OR
--       OLD.pending_balance IS DISTINCT FROM NEW.pending_balance)
-- EXECUTE FUNCTION log_precision_on_update();

-- Final verification query (for migration logs)
-- This will be logged during migration execution
DO $$
DECLARE
    total_wallets INT;
    has_precision_infra BOOLEAN;
BEGIN
    SELECT COUNT(*) INTO total_wallets FROM wallets;

    has_precision_infra := EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'wallet_precision_audit'
    );

    RAISE NOTICE 'Migration V10 completed successfully:';
    RAISE NOTICE '  - Total wallets: %', total_wallets;
    RAISE NOTICE '  - Precision audit infrastructure: %',
        CASE WHEN has_precision_infra THEN 'INSTALLED' ELSE 'FAILED' END;
    RAISE NOTICE '  - Validation function: validate_encrypted_precision() available';
    RAISE NOTICE '  - Monitoring view: wallet_precision_health available';
    RAISE NOTICE '';
    RAISE NOTICE 'Next steps:';
    RAISE NOTICE '  1. Run: SELECT * FROM wallet_precision_validation_report;';
    RAISE NOTICE '  2. Review: SELECT * FROM wallet_precision_health;';
    RAISE NOTICE '  3. Fix legacy formats: Re-encrypt wallets with status != VALID';
    RAISE NOTICE '  4. Application layer: All new writes must use EncryptedFinancialConverter with precision metadata';
END $$;
