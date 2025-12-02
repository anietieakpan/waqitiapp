-- Add CHECK constraints to prevent negative balances
-- CRITICAL: Production blocker - prevents negative balance bugs

-- Wallets table
ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_balance_non_negative
    CHECK (balance >= 0);

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_available_balance_non_negative
    CHECK (available_balance >= 0);

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_pending_balance_non_negative
    CHECK (pending_balance >= 0);

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_frozen_balance_non_negative
    CHECK (frozen_balance >= 0);

-- Wallet balances table
ALTER TABLE wallet_balances
    ADD CONSTRAINT chk_wallet_balances_balance_non_negative
    CHECK (balance >= 0);

ALTER TABLE wallet_balances
    ADD CONSTRAINT chk_wallet_balances_available_balance_non_negative
    CHECK (available_balance >= 0);

ALTER TABLE wallet_balances
    ADD CONSTRAINT chk_wallet_balances_pending_balance_non_negative
    CHECK (pending_balance >= 0);

ALTER TABLE wallet_balances
    ADD CONSTRAINT chk_wallet_balances_frozen_balance_non_negative
    CHECK (frozen_balance >= 0);

-- Wallet holds table
ALTER TABLE wallet_holds
    ADD CONSTRAINT chk_wallet_holds_amount_positive
    CHECK (amount > 0);
