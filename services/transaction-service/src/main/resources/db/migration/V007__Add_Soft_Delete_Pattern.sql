-- =====================================================================================
-- Migration: V007__Add_Soft_Delete_Pattern.sql
-- Purpose: Implement soft delete pattern across all critical tables
--
-- COMPLIANCE REQUIREMENT: Financial systems must retain all records for audit trails
-- - SOX Compliance: All financial transactions must be retained
-- - GDPR Compliance: Data deletion must be logged and reversible
-- - PCI-DSS: Transaction history must be maintained
--
-- Soft Delete Pattern:
-- - deleted_at: Timestamp when record was marked as deleted (NULL = not deleted)
-- - deleted_by: User ID who performed the deletion
-- - deletion_reason: Optional reason code for the deletion
--
-- Benefits:
-- - Preserves audit trail
-- - Enables data recovery
-- - Maintains referential integrity
-- - Supports compliance requirements
--
-- Author: Waqiti Platform Team
-- Date: 2025-11-10
-- =====================================================================================

-- =====================================================================================
-- SECTION 1: Add soft delete columns to transactions table
-- =====================================================================================

-- Add deleted_at column
ALTER TABLE transactions
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

-- Add deleted_by column
ALTER TABLE transactions
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

-- Add deletion_reason column
ALTER TABLE transactions
ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

-- Add index for efficient querying of non-deleted records
CREATE INDEX IF NOT EXISTS idx_transactions_deleted_at
ON transactions(deleted_at) WHERE deleted_at IS NULL;

-- Add index for deleted records audit queries
CREATE INDEX IF NOT EXISTS idx_transactions_deleted_by_date
ON transactions(deleted_by, deleted_at) WHERE deleted_at IS NOT NULL;

-- =====================================================================================
-- SECTION 2: Add soft delete columns to transaction_events table
-- =====================================================================================

ALTER TABLE transaction_events
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE transaction_events
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

ALTER TABLE transaction_events
ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

-- Partial index for active events only
CREATE INDEX IF NOT EXISTS idx_transaction_events_active
ON transaction_events(transaction_id, created_at) WHERE deleted_at IS NULL;

-- =====================================================================================
-- SECTION 3: Add soft delete columns to ledger_entries table
-- =====================================================================================

ALTER TABLE ledger_entries
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE ledger_entries
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

ALTER TABLE ledger_entries
ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

-- Index for active ledger entries
CREATE INDEX IF NOT EXISTS idx_ledger_entries_active
ON ledger_entries(account_id, created_at) WHERE deleted_at IS NULL;

-- =====================================================================================
-- SECTION 4: Add soft delete to scheduled_transactions if exists
-- =====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'scheduled_transactions') THEN
        ALTER TABLE scheduled_transactions
        ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

        ALTER TABLE scheduled_transactions
        ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

        ALTER TABLE scheduled_transactions
        ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

        CREATE INDEX IF NOT EXISTS idx_scheduled_transactions_active
        ON scheduled_transactions(status, scheduled_date) WHERE deleted_at IS NULL;
    END IF;
END $$;

-- =====================================================================================
-- SECTION 5: Add soft delete to recurring_transactions if exists
-- =====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'recurring_transactions') THEN
        ALTER TABLE recurring_transactions
        ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

        ALTER TABLE recurring_transactions
        ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

        ALTER TABLE recurring_transactions
        ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

        CREATE INDEX IF NOT EXISTS idx_recurring_transactions_active
        ON recurring_transactions(status, next_execution_date) WHERE deleted_at IS NULL;
    END IF;
END $$;

-- =====================================================================================
-- SECTION 6: Add soft delete to transaction_disputes if exists
-- =====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transaction_disputes') THEN
        ALTER TABLE transaction_disputes
        ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

        ALTER TABLE transaction_disputes
        ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

        ALTER TABLE transaction_disputes
        ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

        CREATE INDEX IF NOT EXISTS idx_transaction_disputes_active
        ON transaction_disputes(transaction_id, status) WHERE deleted_at IS NULL;
    END IF;
END $$;

-- =====================================================================================
-- SECTION 7: Add soft delete to receipts if exists
-- =====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'receipts') THEN
        ALTER TABLE receipts
        ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

        ALTER TABLE receipts
        ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255);

        ALTER TABLE receipts
        ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

        CREATE INDEX IF NOT EXISTS idx_receipts_active
        ON receipts(transaction_id) WHERE deleted_at IS NULL;
    END IF;
END $$;

-- =====================================================================================
-- SECTION 8: Create helper function for soft delete
-- =====================================================================================

CREATE OR REPLACE FUNCTION soft_delete_transaction(
    p_transaction_id UUID,
    p_deleted_by VARCHAR,
    p_deletion_reason VARCHAR DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
BEGIN
    -- Soft delete the transaction
    UPDATE transactions
    SET deleted_at = CURRENT_TIMESTAMP,
        deleted_by = p_deleted_by,
        deletion_reason = p_deletion_reason,
        updated_at = CURRENT_TIMESTAMP,
        updated_by = p_deleted_by
    WHERE id = p_transaction_id
      AND deleted_at IS NULL; -- Only delete if not already deleted

    -- Check if any row was updated
    IF FOUND THEN
        -- Log the deletion in transaction_events
        INSERT INTO transaction_events (
            id, transaction_id, event_type, description, details, created_at
        ) VALUES (
            gen_random_uuid(),
            p_transaction_id,
            'SOFT_DELETED',
            'Transaction marked as deleted',
            jsonb_build_object(
                'deleted_by', p_deleted_by,
                'deletion_reason', p_deletion_reason,
                'deleted_at', CURRENT_TIMESTAMP
            ),
            CURRENT_TIMESTAMP
        );

        RETURN TRUE;
    ELSE
        RETURN FALSE;
    END IF;
END;
$$;

-- =====================================================================================
-- SECTION 9: Create helper function for soft delete restoration
-- =====================================================================================

CREATE OR REPLACE FUNCTION restore_soft_deleted_transaction(
    p_transaction_id UUID,
    p_restored_by VARCHAR,
    p_restoration_reason VARCHAR DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
BEGIN
    -- Restore the transaction
    UPDATE transactions
    SET deleted_at = NULL,
        deleted_by = NULL,
        deletion_reason = NULL,
        updated_at = CURRENT_TIMESTAMP,
        updated_by = p_restored_by
    WHERE id = p_transaction_id
      AND deleted_at IS NOT NULL; -- Only restore if currently deleted

    -- Check if any row was updated
    IF FOUND THEN
        -- Log the restoration in transaction_events
        INSERT INTO transaction_events (
            id, transaction_id, event_type, description, details, created_at
        ) VALUES (
            gen_random_uuid(),
            p_transaction_id,
            'RESTORED',
            'Transaction restored from soft delete',
            jsonb_build_object(
                'restored_by', p_restored_by,
                'restoration_reason', p_restoration_reason,
                'restored_at', CURRENT_TIMESTAMP
            ),
            CURRENT_TIMESTAMP
        );

        RETURN TRUE;
    ELSE
        RETURN FALSE;
    END IF;
END;
$$;

-- =====================================================================================
-- SECTION 10: Create view for active (non-deleted) transactions
-- =====================================================================================

CREATE OR REPLACE VIEW active_transactions AS
SELECT *
FROM transactions
WHERE deleted_at IS NULL;

COMMENT ON VIEW active_transactions IS
'View of all non-deleted transactions - use this view for most queries to automatically filter deleted records';

-- =====================================================================================
-- SECTION 11: Create view for deleted transactions (audit)
-- =====================================================================================

CREATE OR REPLACE VIEW deleted_transactions_audit AS
SELECT
    id,
    reference,
    from_wallet_id,
    to_wallet_id,
    amount,
    currency,
    status,
    deleted_at,
    deleted_by,
    deletion_reason,
    created_at,
    updated_at
FROM transactions
WHERE deleted_at IS NOT NULL
ORDER BY deleted_at DESC;

COMMENT ON VIEW deleted_transactions_audit IS
'Audit view of all soft-deleted transactions - for compliance and recovery purposes';

-- =====================================================================================
-- SECTION 12: Add comments for documentation
-- =====================================================================================

COMMENT ON COLUMN transactions.deleted_at IS
'Soft delete timestamp - NULL indicates active record, non-NULL indicates deleted record. NEVER hard delete financial records.';

COMMENT ON COLUMN transactions.deleted_by IS
'User ID who performed the soft delete - for audit trail';

COMMENT ON COLUMN transactions.deletion_reason IS
'Reason code for deletion - examples: USER_REQUEST, DUPLICATE, FRAUD, TEST_DATA, GDPR_RIGHT_TO_ERASURE';

-- =====================================================================================
-- SECTION 13: Create cleanup policy function (for test data only)
-- =====================================================================================

CREATE OR REPLACE FUNCTION cleanup_test_transactions(
    p_days_old INTEGER DEFAULT 90
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_deleted_count INTEGER;
BEGIN
    -- SAFETY: Only delete test transactions, never production data
    UPDATE transactions
    SET deleted_at = CURRENT_TIMESTAMP,
        deleted_by = 'SYSTEM_CLEANUP',
        deletion_reason = 'TEST_DATA_CLEANUP'
    WHERE deleted_at IS NULL
      AND created_at < CURRENT_TIMESTAMP - (p_days_old || ' days')::INTERVAL
      AND (
          description LIKE '%TEST%' OR
          description LIKE '%DUMMY%' OR
          metadata->>'test_mode' = 'true'
      );

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;

    RETURN v_deleted_count;
END;
$$;

COMMENT ON FUNCTION cleanup_test_transactions IS
'Safely soft-deletes test transactions older than specified days. ONLY affects records marked as test data.';

-- =====================================================================================
-- SECTION 14: Create statistics for monitoring
-- =====================================================================================

CREATE OR REPLACE VIEW soft_delete_statistics AS
SELECT
    'transactions' as table_name,
    COUNT(*) FILTER (WHERE deleted_at IS NULL) as active_count,
    COUNT(*) FILTER (WHERE deleted_at IS NOT NULL) as deleted_count,
    COUNT(*) as total_count,
    ROUND(100.0 * COUNT(*) FILTER (WHERE deleted_at IS NOT NULL) / NULLIF(COUNT(*), 0), 2) as deleted_percentage
FROM transactions
UNION ALL
SELECT
    'ledger_entries' as table_name,
    COUNT(*) FILTER (WHERE deleted_at IS NULL) as active_count,
    COUNT(*) FILTER (WHERE deleted_at IS NOT NULL) as deleted_count,
    COUNT(*) as total_count,
    ROUND(100.0 * COUNT(*) FILTER (WHERE deleted_at IS NOT NULL) / NULLIF(COUNT(*), 0), 2) as deleted_percentage
FROM ledger_entries
ORDER BY table_name;

COMMENT ON VIEW soft_delete_statistics IS
'Statistics on soft-deleted records across tables - for monitoring and data retention compliance';

-- =====================================================================================
-- SECTION 15: Analyze tables for query planning
-- =====================================================================================

ANALYZE transactions;
ANALYZE transaction_events;
ANALYZE ledger_entries;

-- =====================================================================================
-- Migration Complete
-- =====================================================================================

DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Migration V007 completed successfully';
    RAISE NOTICE 'Soft delete pattern implemented';
    RAISE NOTICE 'Helper functions: soft_delete_transaction(), restore_soft_deleted_transaction()';
    RAISE NOTICE 'Views created: active_transactions, deleted_transactions_audit, soft_delete_statistics';
    RAISE NOTICE 'REMINDER: Update application code to use deleted_at checks in queries';
    RAISE NOTICE '========================================';
END $$;
