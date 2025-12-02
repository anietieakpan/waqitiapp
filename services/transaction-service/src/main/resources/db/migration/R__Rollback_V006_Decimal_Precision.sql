-- =====================================================================================
-- Rollback Migration: R__Rollback_V006_Decimal_Precision.sql
-- Purpose: Rollback V006__Fix_Decimal_Precision_Consistency.sql if needed
--
-- WARNING: This rollback will truncate decimal precision from 4 places to 2 places
-- Data loss will occur for any values using the 3rd and 4th decimal places
--
-- CRITICAL: Only run this rollback script manually if absolutely necessary
-- Flyway repeatable migrations (R__) are for manual execution only
--
-- Author: Waqiti Platform Team
-- Date: 2025-11-10
-- =====================================================================================

-- =====================================================================================
-- SECTION 1: Safety checks before rollback
-- =====================================================================================

DO $$
DECLARE
    v_affected_rows BIGINT;
BEGIN
    -- Check if any records would lose precision
    SELECT COUNT(*) INTO v_affected_rows
    FROM ledger_entries
    WHERE (amount * 100) != FLOOR(amount * 100);

    IF v_affected_rows > 0 THEN
        RAISE WARNING 'WARNING: % ledger_entry records will lose decimal precision!', v_affected_rows;
        RAISE WARNING 'Values with more than 2 decimal places will be truncated';
    ELSE
        RAISE NOTICE 'Safe to rollback: No precision loss detected';
    END IF;
END $$;

-- =====================================================================================
-- SECTION 2: Create backup before rollback
-- =====================================================================================

DROP TABLE IF EXISTS ledger_entries_rollback_backup;

CREATE TABLE ledger_entries_rollback_backup AS
SELECT * FROM ledger_entries;

COMMENT ON TABLE ledger_entries_rollback_backup IS
'Backup created before rolling back DECIMAL precision from (19,4) to (19,2)';

-- =====================================================================================
-- SECTION 3: Rollback precision changes
-- =====================================================================================

-- Rollback ledger_entries.amount
ALTER TABLE ledger_entries
ALTER COLUMN amount TYPE DECIMAL(19,2);

-- Rollback ledger_entries.debit
ALTER TABLE ledger_entries
ALTER COLUMN debit TYPE DECIMAL(19,2);

-- Rollback ledger_entries.credit
ALTER TABLE ledger_entries
ALTER COLUMN credit TYPE DECIMAL(19,2);

-- Rollback balance columns if they exist
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ledger_entries' AND column_name = 'running_balance'
    ) THEN
        ALTER TABLE ledger_entries ALTER COLUMN running_balance TYPE DECIMAL(19,2);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ledger_entries' AND column_name = 'balance'
    ) THEN
        ALTER TABLE ledger_entries ALTER COLUMN balance TYPE DECIMAL(19,2);
    END IF;
END $$;

-- Rollback transaction_fees if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transaction_fees') THEN
        ALTER TABLE transaction_fees ALTER COLUMN fee_amount TYPE DECIMAL(19,2);
        ALTER TABLE transaction_fees ALTER COLUMN tax_amount TYPE DECIMAL(19,2);
        ALTER TABLE transaction_fees ALTER COLUMN total_amount TYPE DECIMAL(19,2);
    END IF;
END $$;

-- Rollback transaction_limits if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transaction_limits') THEN
        ALTER TABLE transaction_limits ALTER COLUMN min_amount TYPE DECIMAL(19,2);
        ALTER TABLE transaction_limits ALTER COLUMN max_amount TYPE DECIMAL(19,2);
        ALTER TABLE transaction_limits ALTER COLUMN daily_limit TYPE DECIMAL(19,2);
        ALTER TABLE transaction_limits ALTER COLUMN monthly_limit TYPE DECIMAL(19,2);
    END IF;
END $$;

-- =====================================================================================
-- SECTION 4: Update comments
-- =====================================================================================

COMMENT ON COLUMN ledger_entries.amount IS
'Transaction amount in DECIMAL(19,2) - ROLLED BACK from (19,4)';

-- =====================================================================================
-- SECTION 5: Validation
-- =====================================================================================

DO $$
DECLARE
    v_amount_precision INTEGER;
    v_amount_scale INTEGER;
BEGIN
    SELECT numeric_precision, numeric_scale INTO v_amount_precision, v_amount_scale
    FROM information_schema.columns
    WHERE table_name = 'ledger_entries' AND column_name = 'amount';

    IF v_amount_precision = 19 AND v_amount_scale = 2 THEN
        RAISE NOTICE 'ROLLBACK SUCCESSFUL: ledger_entries.amount is now DECIMAL(19,2)';
    ELSE
        RAISE EXCEPTION 'ROLLBACK FAILED: ledger_entries.amount is DECIMAL(%,%)',
                        v_amount_precision, v_amount_scale;
    END IF;
END $$;

-- =====================================================================================
-- SECTION 6: Analyze tables
-- =====================================================================================

ANALYZE ledger_entries;

-- =====================================================================================
-- Rollback Complete
-- =====================================================================================

DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Rollback of V006 completed';
    RAISE NOTICE 'DECIMAL precision reverted to (19,2)';
    RAISE NOTICE 'Backup table: ledger_entries_rollback_backup';
    RAISE NOTICE '========================================';
END $$;
