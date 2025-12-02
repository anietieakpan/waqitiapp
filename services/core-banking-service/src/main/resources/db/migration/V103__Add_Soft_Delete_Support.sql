-- V103: Add Soft Delete Support for GDPR Compliance
-- Description: Adds soft delete columns to all entities for data retention and GDPR compliance
-- Author: Claude Code - GDPR Implementation
-- Date: 2025-11-19
--
-- GDPR Article 17 (Right to Erasure) + Financial Regulatory Compliance
-- - Soft delete preserves audit trail while marking data as deleted
-- - Allows for 7-year retention period per financial regulations
-- - Hard delete can be performed after retention period expires

-- ==========================================
-- 1. ADD SOFT DELETE COLUMNS TO ACCOUNTS
-- ==========================================

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

COMMENT ON COLUMN accounts.deleted_at IS
'Soft delete timestamp - when account was marked as deleted (GDPR compliance)';

COMMENT ON COLUMN accounts.deleted_by IS
'User/admin who requested deletion (GDPR audit trail)';

COMMENT ON COLUMN accounts.deletion_reason IS
'Reason for deletion (e.g., GDPR Article 17 - User requested erasure)';

-- Create index for soft delete queries (exclude deleted records)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_deleted_at
ON accounts(deleted_at)
WHERE deleted_at IS NOT NULL;

-- ==========================================
-- 2. ADD SOFT DELETE COLUMNS TO TRANSACTIONS
-- ==========================================

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pseudonymized BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pseudonymized_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pseudonymization_reason VARCHAR(500);

COMMENT ON COLUMN transactions.deleted_at IS
'Soft delete timestamp - transactions should NOT be deleted, only pseudonymized';

COMMENT ON COLUMN transactions.pseudonymized IS
'Whether transaction has been pseudonymized for GDPR compliance (financial data retained)';

COMMENT ON COLUMN transactions.pseudonymized_at IS
'When transaction was pseudonymized (anonymized PII while preserving financial data)';

COMMENT ON COLUMN transactions.pseudonymization_reason IS
'Reason for pseudonymization (typically GDPR Article 17)';

-- Create index for pseudonymized transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_pseudonymized
ON transactions(pseudonymized, pseudonymized_at)
WHERE pseudonymized = TRUE;

-- ==========================================
-- 3. ADD SOFT DELETE COLUMNS TO BANK_ACCOUNTS
-- ==========================================

ALTER TABLE bank_accounts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

COMMENT ON COLUMN bank_accounts.deleted_at IS
'Soft delete timestamp - bank account marked as deleted';

COMMENT ON COLUMN bank_accounts.deleted_by IS
'User/admin who requested deletion';

COMMENT ON COLUMN bank_accounts.deletion_reason IS
'Reason for deletion (GDPR compliance)';

-- Create index for soft delete queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_bank_accounts_deleted_at
ON bank_accounts(deleted_at)
WHERE deleted_at IS NOT NULL;

-- ==========================================
-- 4. ADD SOFT DELETE COLUMNS TO LEDGER_ENTRIES
-- ==========================================

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

COMMENT ON COLUMN ledger_entries.deleted_at IS
'Soft delete timestamp - ledger entries should RARELY be deleted (audit trail)';

-- Create index for soft delete queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_deleted_at
ON ledger_entries(deleted_at)
WHERE deleted_at IS NOT NULL;

-- ==========================================
-- 5. ADD SOFT DELETE COLUMNS TO FUND_RESERVATIONS
-- ==========================================

ALTER TABLE fund_reservations
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

COMMENT ON COLUMN fund_reservations.deleted_at IS
'Soft delete timestamp for fund reservations';

-- Create index for soft delete queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fund_reservations_deleted_at
ON fund_reservations(deleted_at)
WHERE deleted_at IS NOT NULL;

-- ==========================================
-- 6. ADD SOFT DELETE COLUMNS TO FEE_SCHEDULES
-- ==========================================

ALTER TABLE fee_schedules
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

COMMENT ON COLUMN fee_schedules.deleted_at IS
'Soft delete timestamp for fee schedules';

-- Create index for soft delete queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fee_schedules_deleted_at
ON fee_schedules(deleted_at)
WHERE deleted_at IS NOT NULL;

-- ==========================================
-- 7. ADD SOFT DELETE COLUMNS TO STATEMENT_JOBS
-- ==========================================

ALTER TABLE statement_jobs
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

COMMENT ON COLUMN statement_jobs.deleted_at IS
'Soft delete timestamp for statement jobs';

-- Create index for soft delete queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_statement_jobs_deleted_at
ON statement_jobs(deleted_at)
WHERE deleted_at IS NOT NULL;

-- ==========================================
-- 8. CREATE GDPR AUDIT TABLE
-- ==========================================

CREATE TABLE IF NOT EXISTS gdpr_erasure_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    erasure_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT NOT NULL,
    accounts_erased INTEGER NOT NULL DEFAULT 0,
    bank_accounts_erased INTEGER NOT NULL DEFAULT 0,
    transactions_pseudonymized INTEGER NOT NULL DEFAULT 0,
    ledger_entries_affected INTEGER NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE gdpr_erasure_audit IS
'Audit trail for GDPR data erasure requests - MUST be retained for 7 years minimum';

COMMENT ON COLUMN gdpr_erasure_audit.user_id IS
'User whose data was erased';

COMMENT ON COLUMN gdpr_erasure_audit.requested_by IS
'Admin/user who requested the erasure';

COMMENT ON COLUMN gdpr_erasure_audit.erasure_date IS
'When erasure was performed';

-- Create indexes for GDPR audit queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_gdpr_audit_user_id
ON gdpr_erasure_audit(user_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_gdpr_audit_erasure_date
ON gdpr_erasure_audit(erasure_date DESC);

-- ==========================================
-- 9. CREATE FUNCTION FOR HARD DELETE AFTER RETENTION PERIOD
-- ==========================================

-- Function to permanently delete records after retention period (7 years)
CREATE OR REPLACE FUNCTION purge_expired_soft_deletes()
RETURNS TABLE(
    table_name TEXT,
    records_purged BIGINT
) AS $$
DECLARE
    retention_period INTERVAL := '7 years';
    cutoff_date TIMESTAMP := CURRENT_TIMESTAMP - retention_period;
BEGIN
    -- Purge accounts older than retention period
    DELETE FROM accounts
    WHERE deleted_at IS NOT NULL
      AND deleted_at < cutoff_date;

    GET DIAGNOSTICS records_purged = ROW_COUNT;
    table_name := 'accounts';
    RETURN NEXT;

    -- Purge bank accounts
    DELETE FROM bank_accounts
    WHERE deleted_at IS NOT NULL
      AND deleted_at < cutoff_date;

    GET DIAGNOSTICS records_purged = ROW_COUNT;
    table_name := 'bank_accounts';
    RETURN NEXT;

    -- Purge fund reservations
    DELETE FROM fund_reservations
    WHERE deleted_at IS NOT NULL
      AND deleted_at < cutoff_date;

    GET DIAGNOSTICS records_purged = ROW_COUNT;
    table_name := 'fund_reservations';
    RETURN NEXT;

    -- Purge statement jobs
    DELETE FROM statement_jobs
    WHERE deleted_at IS NOT NULL
      AND deleted_at < cutoff_date;

    GET DIAGNOSTICS records_purged = ROW_COUNT;
    table_name := 'statement_jobs';
    RETURN NEXT;

    RETURN;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION purge_expired_soft_deletes() IS
'Permanently delete soft-deleted records after 7-year retention period. ' ||
'Run monthly via scheduled job. Returns count of purged records per table.';

-- ==========================================
-- 10. VALIDATION QUERIES
-- ==========================================

-- Verify all soft delete columns exist
DO $$
DECLARE
    missing_columns TEXT;
BEGIN
    SELECT string_agg(table_name || '.' || column_name, ', ') INTO missing_columns
    FROM (
        SELECT 'accounts' as table_name, 'deleted_at' as column_name
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'accounts' AND column_name = 'deleted_at'
        )
        UNION ALL
        SELECT 'transactions', 'pseudonymized'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'transactions' AND column_name = 'pseudonymized'
        )
        UNION ALL
        SELECT 'bank_accounts', 'deleted_at'
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'bank_accounts' AND column_name = 'deleted_at'
        )
    ) AS missing;

    IF missing_columns IS NOT NULL THEN
        RAISE EXCEPTION 'Migration failed: Missing columns: %', missing_columns;
    END IF;

    RAISE NOTICE 'Soft delete migration V103 completed successfully';
    RAISE NOTICE 'All tables now support GDPR-compliant soft deletion';
    RAISE NOTICE 'Retention period: 7 years';
    RAISE NOTICE 'Run purge_expired_soft_deletes() monthly to clean up old records';
END $$;

-- ==========================================
-- ROLLBACK SCRIPT (for reference)
-- ==========================================

-- To rollback this migration, run:
/*
ALTER TABLE accounts DROP COLUMN IF EXISTS deleted_at, DROP COLUMN IF EXISTS deleted_by, DROP COLUMN IF EXISTS deletion_reason;
ALTER TABLE transactions DROP COLUMN IF EXISTS deleted_at, DROP COLUMN IF EXISTS deleted_by, DROP COLUMN IF EXISTS deletion_reason, DROP COLUMN IF EXISTS pseudonymized, DROP COLUMN IF EXISTS pseudonymized_at, DROP COLUMN IF EXISTS pseudonymization_reason;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS deleted_at, DROP COLUMN IF EXISTS deleted_by, DROP COLUMN IF EXISTS deletion_reason;
ALTER TABLE ledger_entries DROP COLUMN IF EXISTS deleted_at, DROP COLUMN IF EXISTS deleted_by, DROP COLUMN IF EXISTS deletion_reason;
ALTER TABLE fund_reservations DROP COLUMN IF EXISTS deleted_at, DROP COLUMN IF EXISTS deleted_by, DROP COLUMN IF EXISTS deletion_reason;
ALTER TABLE fee_schedules DROP COLUMN IF EXISTS deleted_at, DROP COLUMN IF EXISTS deleted_by, DROP COLUMN IF EXISTS deletion_reason;
ALTER TABLE statement_jobs DROP COLUMN IF EXISTS deleted_at, DROP COLUMN IF EXISTS deleted_by, DROP COLUMN IF EXISTS deletion_reason;
DROP TABLE IF EXISTS gdpr_erasure_audit;
DROP FUNCTION IF EXISTS purge_expired_soft_deletes();
*/
