-- Migration V4: Add Optimistic Locking
-- Created: 2025-11-15
-- Description: Add version columns for optimistic locking on all financial entities
--              to prevent race conditions and lost updates in concurrent transactions
-- CRITICAL: Prevents balance corruption in high-concurrency scenarios

-- ============================================================================
-- ADD VERSION COLUMNS
-- ============================================================================

-- journal_entry table
ALTER TABLE journal_entry
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- journal_entry_line table
ALTER TABLE journal_entry_line
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- general_ledger table
ALTER TABLE general_ledger
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- account_balance table (CRITICAL - concurrent balance updates)
ALTER TABLE account_balance
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- fiscal_period table
ALTER TABLE fiscal_period
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- reconciliation table
ALTER TABLE reconciliation
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- budget table
ALTER TABLE budget
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- budget_line table
ALTER TABLE budget_line
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- transaction_fee table
ALTER TABLE transaction_fee
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- tax_calculation table
ALTER TABLE tax_calculation
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- chart_of_accounts table
ALTER TABLE chart_of_accounts
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Note: settlement_entry already has version column from V2 migration

-- ============================================================================
-- CREATE INDEXES ON VERSION COLUMNS (for optimistic locking queries)
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_journal_entry_version ON journal_entry(id, version);
CREATE INDEX IF NOT EXISTS idx_account_balance_version ON account_balance(id, version);
CREATE INDEX IF NOT EXISTS idx_general_ledger_version ON general_ledger(id, version);

-- ============================================================================
-- ADD CHECK CONSTRAINTS (ensure version is never negative)
-- ============================================================================

ALTER TABLE journal_entry
    ADD CONSTRAINT chk_journal_entry_version CHECK (version >= 0);

ALTER TABLE account_balance
    ADD CONSTRAINT chk_account_balance_version CHECK (version >= 0);

ALTER TABLE general_ledger
    ADD CONSTRAINT chk_general_ledger_version CHECK (version >= 0);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON COLUMN journal_entry.version IS 'Optimistic locking version - prevents concurrent modification conflicts';
COMMENT ON COLUMN account_balance.version IS 'CRITICAL: Optimistic locking for concurrent balance updates';
COMMENT ON COLUMN general_ledger.version IS 'Optimistic locking version for ledger entry modifications';

-- Migration completed successfully
-- All financial entities now have optimistic locking support
