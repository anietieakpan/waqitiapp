-- Add CHECK constraints to prevent negative balances in accounts
-- CRITICAL: Production blocker - prevents negative balance bugs

-- Account balances table
ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_available_balance_non_negative
    CHECK (available_balance >= 0);

ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_current_balance_non_negative
    CHECK (current_balance >= 0);

ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_pending_balance_non_negative
    CHECK (pending_balance >= 0);

ALTER TABLE account_balances
    ADD CONSTRAINT chk_account_balances_held_balance_non_negative
    CHECK (held_balance >= 0);

-- Overdraft limits must be non-negative
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_overdraft_limit_non_negative
    CHECK (overdraft_limit >= 0);
