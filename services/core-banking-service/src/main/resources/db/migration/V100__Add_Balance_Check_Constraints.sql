-- Add CHECK constraints to prevent negative balances in core banking
-- CRITICAL: Production blocker - prevents negative balance bugs

-- Accounts table
ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_balance_non_negative
    CHECK (balance >= 0);

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_available_balance_non_negative
    CHECK (available_balance >= 0);

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_frozen_amount_non_negative
    CHECK (frozen_amount >= 0);

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_overdraft_limit_non_negative
    CHECK (overdraft_limit >= 0);

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_minimum_balance_non_negative
    CHECK (minimum_balance >= 0);

-- Balance snapshots table
ALTER TABLE balance_snapshots
    ADD CONSTRAINT chk_balance_snapshots_balance_non_negative
    CHECK (balance >= 0);

ALTER TABLE balance_snapshots
    ADD CONSTRAINT chk_balance_snapshots_available_balance_non_negative
    CHECK (available_balance >= 0);

ALTER TABLE balance_snapshots
    ADD CONSTRAINT chk_balance_snapshots_frozen_amount_non_negative
    CHECK (frozen_amount >= 0);

-- Transactions - amount must be positive
ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_amount_positive
    CHECK (amount > 0);

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_processing_fee_non_negative
    CHECK (processing_fee >= 0);
