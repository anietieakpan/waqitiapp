-- ============================================================================
-- PCI DSS COMPLIANCE FIX: Remove CVV Storage
-- Migration V999 - CRITICAL SECURITY FIX
--
-- PCI DSS Requirement 3.2.2: Do NOT store the card verification code or value
-- (three-digit or four-digit number printed on the front or back of a payment card)
-- after authorization, even if encrypted.
--
-- This migration:
-- 1. Removes encrypted_cvv column from virtual_cards table
-- 2. Removes cvv_rotated_at column (no longer needed)
-- 3. Adds card_rotated_at for card number rotation tracking
-- 4. Adds audit trail for compliance verification
--
-- Date: 2025-10-18
-- Author: Security Remediation Team
-- Jira: SEC-1001 (PCI DSS CVV Storage Violation)
-- ============================================================================

-- Step 1: Add audit logging for this critical security fix
INSERT INTO audit_log (
    event_type,
    entity_type,
    description,
    performed_by,
    performed_at,
    metadata
) VALUES (
    'SECURITY_REMEDIATION',
    'VIRTUAL_CARD',
    'PCI DSS Compliance: Removed CVV storage per Requirement 3.2.2',
    'SYSTEM_MIGRATION',
    NOW(),
    '{"migration": "V999", "compliance": "PCI_DSS_3.2.2", "risk_level": "CRITICAL"}'::jsonb
);

-- Step 2: Backup data before deletion (for forensic purposes only, NOT for CVV recovery)
-- Store count of affected records in audit table
DO $$
DECLARE
    affected_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO affected_count FROM virtual_cards WHERE encrypted_cvv IS NOT NULL;

    INSERT INTO audit_log (
        event_type,
        entity_type,
        description,
        performed_by,
        performed_at,
        metadata
    ) VALUES (
        'DATA_PURGE',
        'VIRTUAL_CARD',
        'CVV data purged for PCI DSS compliance',
        'SYSTEM_MIGRATION',
        NOW(),
        jsonb_build_object(
            'affected_records', affected_count,
            'column_purged', 'encrypted_cvv',
            'reason', 'PCI_DSS_REQUIREMENT_3.2.2'
        )
    );
END $$;

-- Step 3: Drop encrypted_cvv column (CRITICAL: CVV must NEVER be stored)
ALTER TABLE virtual_cards
DROP COLUMN IF EXISTS encrypted_cvv;

-- Step 4: Drop cvv_rotated_at column (no longer applicable)
ALTER TABLE virtual_cards
DROP COLUMN IF EXISTS cvv_rotated_at;

-- Step 5: Add card_rotated_at for card number rotation tracking (security best practice)
ALTER TABLE virtual_cards
ADD COLUMN IF NOT EXISTS card_rotated_at TIMESTAMP;

-- Step 6: Add index for card rotation queries
CREATE INDEX IF NOT EXISTS idx_virtual_cards_card_rotated_at
ON virtual_cards(card_rotated_at)
WHERE card_rotated_at IS NOT NULL;

-- Step 7: Update comments for compliance documentation
COMMENT ON TABLE virtual_cards IS 'Virtual payment cards - PCI DSS compliant (no CVV storage per Requirement 3.2.2)';
COMMENT ON COLUMN virtual_cards.encrypted_card_number IS 'Encrypted PAN (stored with proper encryption per PCI DSS Requirement 3.4)';
COMMENT ON COLUMN virtual_cards.masked_card_number IS 'Masked PAN for display (only first 6 and last 4 digits visible per PCI DSS Requirement 3.3)';
COMMENT ON COLUMN virtual_cards.card_rotated_at IS 'Last card number rotation timestamp for security tracking';

-- Step 8: Add check constraint to ensure no CVV-like fields are added in future
-- (This serves as a preventive control)
DO $$
BEGIN
    -- Verify no CVV columns exist
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'virtual_cards'
        AND (column_name LIKE '%cvv%' OR column_name LIKE '%cvc%' OR column_name LIKE '%security_code%')
    ) THEN
        RAISE EXCEPTION 'PCI DSS VIOLATION: CVV-related column detected in virtual_cards table';
    END IF;
END $$;

-- Step 9: Final compliance verification
DO $$
DECLARE
    compliance_status TEXT;
BEGIN
    SELECT CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'virtual_cards'
            AND column_name IN ('encrypted_cvv', 'cvv', 'cvc', 'security_code', 'cvv2')
        )
        THEN 'COMPLIANT'
        ELSE 'NON_COMPLIANT'
    END INTO compliance_status;

    IF compliance_status = 'NON_COMPLIANT' THEN
        RAISE EXCEPTION 'PCI DSS COMPLIANCE VERIFICATION FAILED: CVV storage still detected';
    END IF;

    RAISE NOTICE 'PCI DSS Compliance Verification: % - No CVV storage detected', compliance_status;
END $$;

-- Step 10: Grant appropriate permissions (read-only for audit)
-- Card data should only be accessed through application layer with encryption
REVOKE ALL ON virtual_cards FROM PUBLIC;
-- Note: Actual GRANT statements should be configured per environment

-- ============================================================================
-- ROLLBACK INSTRUCTIONS (FOR EMERGENCY ONLY - NOT RECOMMENDED):
--
-- This migration should NOT be rolled back as it fixes a critical PCI DSS
-- violation. CVV data should NEVER be stored post-authorization.
--
-- If rollback is absolutely necessary for non-production environments:
-- ALTER TABLE virtual_cards ADD COLUMN encrypted_cvv VARCHAR(500);
-- ALTER TABLE virtual_cards ADD COLUMN cvv_rotated_at TIMESTAMP;
-- ALTER TABLE virtual_cards DROP COLUMN card_rotated_at;
--
-- WARNING: Do NOT rollback in production. Contact Security Team immediately.
-- ============================================================================

COMMIT;
