-- Add CHECK constraints to prevent negative balances in ledger
-- CRITICAL: Production blocker - prevents negative balance bugs

-- Account balances table
ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_current_balance_non_negative
    CHECK (current_balance >= 0);

ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_pending_debits_non_negative
    CHECK (pending_debits >= 0);

ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_pending_credits_non_negative
    CHECK (pending_credits >= 0);

ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_available_balance_non_negative
    CHECK (available_balance >= 0);

-- Journal entries constraints
ALTER TABLE journal_entries
    ADD CONSTRAINT chk_journal_entries_total_debits_non_negative
    CHECK (total_debits >= 0);

ALTER TABLE journal_entries
    ADD CONSTRAINT chk_journal_entries_total_credits_non_negative
    CHECK (total_credits >= 0);
