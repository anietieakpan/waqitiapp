-- =====================================================================================
-- Migration: V006__Fix_Decimal_Precision_Consistency.sql
-- Purpose: Fix DECIMAL precision inconsistency across financial tables
--
-- CRITICAL: This migration ensures all monetary values use DECIMAL(19,4) precision
-- consistent with the transaction service standard. DECIMAL(19,4) supports:
-- - Values up to 999 trillion (999,999,999,999,999.9999)
-- - 4 decimal places for cryptocurrency and foreign exchange precision
-- - Full compatibility with BigDecimal in Java
--
-- Tables affected:
-- - ledger_entries: Change amount from DECIMAL(19,2) to DECIMAL(19,4)
-- - Any other tables with inconsistent precision
--
-- Author: Waqiti Platform Team
-- Date: 2025-11-10
-- =====================================================================================

-- =====================================================================================
-- SECTION 1: Backup existing data (safety measure)
-- =====================================================================================

-- Create backup table for ledger_entries
CREATE TABLE IF NOT EXISTS ledger_entries_backup_v006 AS
SELECT * FROM ledger_entries;

COMMENT ON TABLE ledger_entries_backup_v006 IS
'Backup of ledger_entries before DECIMAL precision migration V006 - created 2025-11-10';

-- =====================================================================================
-- SECTION 2: Fix ledger_entries table precision
-- =====================================================================================

-- Change amount column from DECIMAL(19,2) to DECIMAL(19,4)
ALTER TABLE ledger_entries
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Change debit column if exists
ALTER TABLE ledger_entries
ALTER COLUMN debit TYPE DECIMAL(19,4);

-- Change credit column if exists
ALTER TABLE ledger_entries
ALTER COLUMN credit TYPE DECIMAL(19,4);

-- Change balance columns if they exist
DO $$
BEGIN
    -- Check and alter running_balance if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ledger_entries' AND column_name = 'running_balance'
    ) THEN
        ALTER TABLE ledger_entries ALTER COLUMN running_balance TYPE DECIMAL(19,4);
    END IF;

    -- Check and alter balance if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ledger_entries' AND column_name = 'balance'
    ) THEN
        ALTER TABLE ledger_entries ALTER COLUMN balance TYPE DECIMAL(19,4);
    END IF;
END $$;

-- =====================================================================================
-- SECTION 3: Fix any other tables with DECIMAL(19,2) that should be DECIMAL(19,4)
-- =====================================================================================

-- Fix transaction_fees table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transaction_fees') THEN
        ALTER TABLE transaction_fees ALTER COLUMN fee_amount TYPE DECIMAL(19,4);
        ALTER TABLE transaction_fees ALTER COLUMN tax_amount TYPE DECIMAL(19,4);
        ALTER TABLE transaction_fees ALTER COLUMN total_amount TYPE DECIMAL(19,4);
    END IF;
END $$;

-- Fix transaction_limits table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transaction_limits') THEN
        ALTER TABLE transaction_limits ALTER COLUMN min_amount TYPE DECIMAL(19,4);
        ALTER TABLE transaction_limits ALTER COLUMN max_amount TYPE DECIMAL(19,4);
        ALTER TABLE transaction_limits ALTER COLUMN daily_limit TYPE DECIMAL(19,4);
        ALTER TABLE transaction_limits ALTER COLUMN monthly_limit TYPE DECIMAL(19,4);
    END IF;
END $$;

-- =====================================================================================
-- SECTION 4: Add comments for documentation
-- =====================================================================================

COMMENT ON COLUMN ledger_entries.amount IS
'Transaction amount in DECIMAL(19,4) - supports up to 999 trillion with 4 decimal places for crypto/forex';

COMMENT ON COLUMN ledger_entries.debit IS
'Debit amount in DECIMAL(19,4) - debit side of double-entry bookkeeping';

COMMENT ON COLUMN ledger_entries.credit IS
'Credit amount in DECIMAL(19,4) - credit side of double-entry bookkeeping';

-- =====================================================================================
-- SECTION 5: Validation checks
-- =====================================================================================

-- Verify the precision change was applied correctly
DO $$
DECLARE
    v_amount_precision INTEGER;
    v_amount_scale INTEGER;
BEGIN
    SELECT numeric_precision, numeric_scale INTO v_amount_precision, v_amount_scale
    FROM information_schema.columns
    WHERE table_name = 'ledger_entries' AND column_name = 'amount';

    IF v_amount_precision != 19 OR v_amount_scale != 4 THEN
        RAISE EXCEPTION 'Precision change failed: ledger_entries.amount is DECIMAL(%,%) instead of DECIMAL(19,4)',
                        v_amount_precision, v_amount_scale;
    END IF;

    RAISE NOTICE 'SUCCESS: ledger_entries.amount precision changed to DECIMAL(19,4)';
END $$;

-- =====================================================================================
-- SECTION 6: Performance optimization after ALTER
-- =====================================================================================

-- Analyze table to update statistics
ANALYZE ledger_entries;

-- =====================================================================================
-- SECTION 7: Verification query for manual inspection
-- =====================================================================================

-- Query to verify all monetary columns have consistent precision
SELECT
    table_name,
    column_name,
    data_type,
    numeric_precision,
    numeric_scale
FROM information_schema.columns
WHERE table_schema = 'public'
  AND data_type = 'numeric'
  AND table_name IN ('transactions', 'ledger_entries', 'transaction_fees', 'transaction_limits')
ORDER BY table_name, column_name;

-- =====================================================================================
-- Migration Complete
-- =====================================================================================

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Migration V006 completed successfully';
    RAISE NOTICE 'All monetary amounts now use DECIMAL(19,4)';
    RAISE NOTICE 'Backup table: ledger_entries_backup_v006';
    RAISE NOTICE '========================================';
END $$;
