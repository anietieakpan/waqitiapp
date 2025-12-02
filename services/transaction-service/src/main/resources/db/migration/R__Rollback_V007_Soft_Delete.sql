-- =====================================================================================
-- Rollback Migration: R__Rollback_V007_Soft_Delete.sql
-- Purpose: Rollback V007__Add_Soft_Delete_Pattern.sql if needed
--
-- WARNING: This rollback removes soft delete columns and helper functions
-- Any soft-deleted records will be permanently visible again after rollback
--
-- CRITICAL: Only run this rollback script manually if absolutely necessary
-- Consider data retention compliance requirements before rolling back
--
-- Author: Waqiti Platform Team
-- Date: 2025-11-10
-- =====================================================================================

-- =====================================================================================
-- SECTION 1: Safety checks and warnings
-- =====================================================================================

DO $$
DECLARE
    v_soft_deleted_count BIGINT;
BEGIN
    -- Count soft-deleted records
    SELECT COUNT(*) INTO v_soft_deleted_count
    FROM transactions
    WHERE deleted_at IS NOT NULL;

    IF v_soft_deleted_count > 0 THEN
        RAISE WARNING 'WARNING: % soft-deleted transactions exist!', v_soft_deleted_count;
        RAISE WARNING 'These records will become active again after rollback!';
        RAISE WARNING 'Consider hard-deleting or migrating them first.';
    ELSE
        RAISE NOTICE 'Safe to rollback: No soft-deleted records found';
    END IF;
END $$;

-- =====================================================================================
-- SECTION 2: Drop views created by V007
-- =====================================================================================

DROP VIEW IF EXISTS soft_delete_statistics CASCADE;
DROP VIEW IF EXISTS deleted_transactions_audit CASCADE;
DROP VIEW IF EXISTS active_transactions CASCADE;

-- =====================================================================================
-- SECTION 3: Drop helper functions
-- =====================================================================================

DROP FUNCTION IF EXISTS cleanup_test_transactions(INTEGER);
DROP FUNCTION IF EXISTS restore_soft_deleted_transaction(UUID, VARCHAR, VARCHAR);
DROP FUNCTION IF EXISTS soft_delete_transaction(UUID, VARCHAR, VARCHAR);

-- =====================================================================================
-- SECTION 4: Drop indexes created for soft delete pattern
-- =====================================================================================

DROP INDEX IF EXISTS idx_transactions_deleted_at;
DROP INDEX IF EXISTS idx_transactions_deleted_by_date;
DROP INDEX IF EXISTS idx_transaction_events_active;
DROP INDEX IF EXISTS idx_ledger_entries_active;
DROP INDEX IF EXISTS idx_scheduled_transactions_active;
DROP INDEX IF EXISTS idx_recurring_transactions_active;
DROP INDEX IF EXISTS idx_transaction_disputes_active;
DROP INDEX IF EXISTS idx_receipts_active;

-- =====================================================================================
-- SECTION 5: Remove soft delete columns from transactions
-- =====================================================================================

ALTER TABLE transactions
DROP COLUMN IF EXISTS deleted_at,
DROP COLUMN IF EXISTS deleted_by,
DROP COLUMN IF EXISTS deletion_reason;

-- =====================================================================================
-- SECTION 6: Remove soft delete columns from transaction_events
-- =====================================================================================

ALTER TABLE transaction_events
DROP COLUMN IF EXISTS deleted_at,
DROP COLUMN IF EXISTS deleted_by,
DROP COLUMN IF EXISTS deletion_reason;

-- =====================================================================================
-- SECTION 7: Remove soft delete columns from ledger_entries
-- =====================================================================================

ALTER TABLE ledger_entries
DROP COLUMN IF EXISTS deleted_at,
DROP COLUMN IF EXISTS deleted_by,
DROP COLUMN IF EXISTS deletion_reason;

-- =====================================================================================
-- SECTION 8: Remove from optional tables
-- =====================================================================================

DO $$
BEGIN
    -- scheduled_transactions
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'scheduled_transactions') THEN
        ALTER TABLE scheduled_transactions
        DROP COLUMN IF EXISTS deleted_at,
        DROP COLUMN IF EXISTS deleted_by,
        DROP COLUMN IF EXISTS deletion_reason;
    END IF;

    -- recurring_transactions
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'recurring_transactions') THEN
        ALTER TABLE recurring_transactions
        DROP COLUMN IF EXISTS deleted_at,
        DROP COLUMN IF EXISTS deleted_by,
        DROP COLUMN IF EXISTS deletion_reason;
    END IF;

    -- transaction_disputes
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transaction_disputes') THEN
        ALTER TABLE transaction_disputes
        DROP COLUMN IF EXISTS deleted_at,
        DROP COLUMN IF EXISTS deleted_by,
        DROP COLUMN IF EXISTS deletion_reason;
    END IF;

    -- receipts
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'receipts') THEN
        ALTER TABLE receipts
        DROP COLUMN IF EXISTS deleted_at,
        DROP COLUMN IF EXISTS deleted_by,
        DROP COLUMN IF EXISTS deletion_reason;
    END IF;
END $$;

-- =====================================================================================
-- SECTION 9: Analyze tables
-- =====================================================================================

ANALYZE transactions;
ANALYZE transaction_events;
ANALYZE ledger_entries;

-- =====================================================================================
-- Rollback Complete
-- =====================================================================================

DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Rollback of V007 completed';
    RAISE NOTICE 'Soft delete pattern removed';
    RAISE NOTICE 'WARNING: Records are no longer soft-deletable';
    RAISE NOTICE 'WARNING: Hard deletes may violate compliance';
    RAISE NOTICE '========================================';
END $$;
