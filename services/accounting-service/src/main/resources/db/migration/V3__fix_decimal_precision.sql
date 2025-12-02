-- Migration V3: Fix Decimal Precision Mismatch
-- Created: 2025-11-15
-- Description: Update all monetary columns from DECIMAL(18,2) to DECIMAL(19,4)
--              to match application configuration (precision=4)
-- CRITICAL: This migration preserves data while increasing precision

-- ============================================================================
-- JOURNAL ENTRY TABLES
-- ============================================================================

-- Update journal_entry monetary columns
ALTER TABLE journal_entry
    ALTER COLUMN total_debit TYPE DECIMAL(19, 4),
    ALTER COLUMN total_credit TYPE DECIMAL(19, 4);

-- Update journal_entry_line monetary columns
ALTER TABLE journal_entry_line
    ALTER COLUMN debit_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN credit_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN functional_debit TYPE DECIMAL(19, 4),
    ALTER COLUMN functional_credit TYPE DECIMAL(19, 4);

-- ============================================================================
-- GENERAL LEDGER
-- ============================================================================

ALTER TABLE general_ledger
    ALTER COLUMN debit_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN credit_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN running_balance TYPE DECIMAL(19, 4);

-- ============================================================================
-- ACCOUNT BALANCES
-- ============================================================================

ALTER TABLE account_balance
    ALTER COLUMN opening_balance TYPE DECIMAL(19, 4),
    ALTER COLUMN period_debits TYPE DECIMAL(19, 4),
    ALTER COLUMN period_credits TYPE DECIMAL(19, 4),
    ALTER COLUMN closing_balance TYPE DECIMAL(19, 4);

-- ============================================================================
-- RECONCILIATION
-- ============================================================================

ALTER TABLE reconciliation
    ALTER COLUMN bank_statement_balance TYPE DECIMAL(19, 4),
    ALTER COLUMN book_balance TYPE DECIMAL(19, 4),
    ALTER COLUMN adjusted_balance TYPE DECIMAL(19, 4),
    ALTER COLUMN difference TYPE DECIMAL(19, 4);

-- ============================================================================
-- BUDGET TABLES
-- ============================================================================

ALTER TABLE budget_line
    ALTER COLUMN budgeted_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN actual_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN variance TYPE DECIMAL(19, 4);

-- Update variance_percentage to support higher precision
ALTER TABLE budget_line
    ALTER COLUMN variance_percentage TYPE DECIMAL(7, 4);

-- ============================================================================
-- SETTLEMENT AND FEE TABLES (from V2 migration)
-- ============================================================================

ALTER TABLE settlement_entry
    ALTER COLUMN gross_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN processing_fee TYPE DECIMAL(19, 4),
    ALTER COLUMN total_fees TYPE DECIMAL(19, 4),
    ALTER COLUMN taxes TYPE DECIMAL(19, 4),
    ALTER COLUMN net_amount TYPE DECIMAL(19, 4);

ALTER TABLE transaction_fee
    ALTER COLUMN amount TYPE DECIMAL(19, 4),
    ALTER COLUMN fixed_amount TYPE DECIMAL(19, 4);

-- Update fee_percentage to support higher precision
ALTER TABLE transaction_fee
    ALTER COLUMN fee_percentage TYPE DECIMAL(7, 4);

ALTER TABLE tax_calculation
    ALTER COLUMN tax_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN taxable_amount TYPE DECIMAL(19, 4);

-- Update tax_rate to support higher precision
ALTER TABLE tax_calculation
    ALTER COLUMN tax_rate TYPE DECIMAL(7, 4);

-- ============================================================================
-- ADD INDEXES FOR PERFORMANCE (if not already present)
-- ============================================================================

-- Ensure indexes exist on high-precision amount columns for range queries
CREATE INDEX IF NOT EXISTS idx_journal_entry_amounts ON journal_entry(total_debit, total_credit);
CREATE INDEX IF NOT EXISTS idx_gl_amounts ON general_ledger(debit_amount, credit_amount);
CREATE INDEX IF NOT EXISTS idx_settlement_amounts ON settlement_entry(gross_amount DESC, net_amount DESC);

-- ============================================================================
-- VALIDATION QUERIES (for testing - can be run separately)
-- ============================================================================

-- Verify no data loss occurred (run after migration)
-- SELECT
--     'journal_entry' as table_name,
--     COUNT(*) as row_count,
--     SUM(total_debit) as total_debits,
--     SUM(total_credit) as total_credits
-- FROM journal_entry
-- UNION ALL
-- SELECT
--     'general_ledger',
--     COUNT(*),
--     SUM(debit_amount),
--     SUM(credit_amount)
-- FROM general_ledger;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON COLUMN journal_entry.total_debit IS 'Total debit amount - DECIMAL(19,4) for multi-currency and crypto precision';
COMMENT ON COLUMN journal_entry.total_credit IS 'Total credit amount - DECIMAL(19,4) for multi-currency and crypto precision';
COMMENT ON COLUMN settlement_entry.gross_amount IS 'Gross amount with 4 decimal precision for accurate fee calculations';
COMMENT ON COLUMN settlement_entry.net_amount IS 'Net settlement amount with 4 decimal precision';

-- Migration completed successfully
-- All monetary columns now support 4 decimal places as per application configuration
