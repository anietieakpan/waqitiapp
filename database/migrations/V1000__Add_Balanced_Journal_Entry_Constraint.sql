-- ================================================================
-- PRODUCTION FIX: Add constraint for balanced journal entries
-- ================================================================
-- PURPOSE:
--   Enforce double-entry bookkeeping at database level
--   Prevents unbalanced journal entries from being persisted
--
-- FINANCIAL IMPACT:
--   Without this constraint: Unbalanced entries → trial balance doesn't balance
--   With this constraint: Database rejects unbalanced entries → data integrity guaranteed
--
-- REGULATORY:
--   GAAP (Generally Accepted Accounting Principles) requires balanced entries
--   SOX compliance requires preventive controls for financial data
--
-- Author: Waqiti Engineering Team
-- Date: 2025-11-03
-- ================================================================

-- Add CHECK constraint to ensure total_debits = total_credits
ALTER TABLE journal_entries
    ADD CONSTRAINT chk_balanced_journal_entry
    CHECK (total_debits = total_credits);

-- Add comment explaining the constraint
COMMENT ON CONSTRAINT chk_balanced_journal_entry ON journal_entries IS
    'Enforces double-entry bookkeeping: total debits must equal total credits. ' ||
    'Required for GAAP compliance and financial integrity.';

-- Create index on balance check columns for performance
CREATE INDEX idx_journal_entries_balance_check
    ON journal_entries (total_debits, total_credits)
    WHERE total_debits != total_credits;

COMMENT ON INDEX idx_journal_entries_balance_check IS
    'Performance optimization for identifying unbalanced entries (should be zero rows)';

-- ================================================================
-- VALIDATION QUERY
-- ================================================================
-- Run this query after deployment to verify no existing unbalanced entries
--
-- SELECT journal_entry_id, entry_number, total_debits, total_credits,
--        (total_debits - total_credits) AS imbalance
-- FROM journal_entries
-- WHERE total_debits != total_credits;
--
-- Expected result: 0 rows
-- If any rows found: Fix data before applying constraint
-- ================================================================

-- ================================================================
-- ROLLBACK SCRIPT
-- ================================================================
-- To rollback this migration (NOT recommended for production):
--
-- ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS chk_balanced_journal_entry;
-- DROP INDEX IF EXISTS idx_journal_entries_balance_check;
-- ================================================================
