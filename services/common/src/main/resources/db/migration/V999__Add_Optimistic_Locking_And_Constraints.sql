-- ============================================================================
-- WAQITI PLATFORM - CRITICAL DATABASE FIXES
-- Migration: V999 - Add Optimistic Locking and Financial Constraints
-- Date: 2025-09-27
-- Priority: P0 CRITICAL
-- ============================================================================

-- DESCRIPTION:
-- This migration adds critical financial constraints and optimistic locking support
-- to prevent data corruption, race conditions, and financial discrepancies.
--
-- Changes include:
-- 1. Add @Version columns for optimistic locking
-- 2. Add audit columns (created_at, updated_at, created_by, updated_by)
-- 3. Add financial business constraints (balance >= 0, amount > 0, etc.)
-- 4. Add performance indexes for critical queries
-- 5. Add soft delete support
-- 6. Add data integrity constraints

-- ============================================================================
-- PART 1: ADD OPTIMISTIC LOCKING COLUMNS TO ALL FINANCIAL ENTITIES
-- ============================================================================

-- Wallets Table
ALTER TABLE wallets 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- Transactions Table
ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- Payments Table
ALTER TABLE payments 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- Ledger Entries Table
ALTER TABLE ledger_entries 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- Accounts Table
ALTER TABLE accounts 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- Payment Methods Table
ALTER TABLE payment_methods 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- Merchant Accounts Table
ALTER TABLE merchant_accounts 
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

-- ============================================================================
-- PART 2: ADD CRITICAL FINANCIAL CONSTRAINTS
-- ============================================================================

-- Wallet Constraints
ALTER TABLE wallets 
ADD CONSTRAINT IF NOT EXISTS check_wallet_balance_non_negative 
    CHECK (balance >= 0),
ADD CONSTRAINT IF NOT EXISTS check_wallet_currency_valid 
    CHECK (currency IN ('USD', 'EUR', 'GBP', 'CAD', 'AUD', 'JPY', 'CNY', 'INR', 'BTC', 'ETH'));

-- Transaction Constraints
ALTER TABLE transactions 
ADD CONSTRAINT IF NOT EXISTS check_transaction_amount_positive 
    CHECK (amount > 0),
ADD CONSTRAINT IF NOT EXISTS check_transaction_status_valid 
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'));

-- Payment Constraints
ALTER TABLE payments 
ADD CONSTRAINT IF NOT EXISTS check_payment_amount_positive 
    CHECK (amount > 0),
ADD CONSTRAINT IF NOT EXISTS check_payment_status_valid 
    CHECK (status IN ('INITIATED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED', 'DISPUTED'));

-- Ledger Entry Constraints
ALTER TABLE ledger_entries 
ADD CONSTRAINT IF NOT EXISTS check_ledger_entry_type_valid 
    CHECK (entry_type IN ('DEBIT', 'CREDIT'));

-- Account Constraints
ALTER TABLE accounts 
ADD CONSTRAINT IF NOT EXISTS check_account_balance_non_negative 
    CHECK (balance >= 0),
ADD CONSTRAINT IF NOT EXISTS check_account_type_valid 
    CHECK (account_type IN ('CHECKING', 'SAVINGS', 'INVESTMENT', 'MERCHANT', 'ESCROW', 'RESERVE'));

-- Payment Method Constraints
ALTER TABLE payment_methods 
ADD CONSTRAINT IF NOT EXISTS check_payment_method_type_valid 
    CHECK (type IN ('CARD', 'BANK_ACCOUNT', 'CRYPTO_WALLET', 'PAYPAL', 'APPLE_PAY', 'GOOGLE_PAY'));

-- ============================================================================
-- PART 3: ADD UNIQUE CONSTRAINTS FOR DATA INTEGRITY
-- ============================================================================

-- Unique transaction reference numbers
ALTER TABLE transactions 
ADD CONSTRAINT IF NOT EXISTS unique_transaction_reference_number 
    UNIQUE (reference_number);

-- Unique payment provider transaction IDs
ALTER TABLE payments 
ADD CONSTRAINT IF NOT EXISTS unique_provider_transaction_id 
    UNIQUE (provider_transaction_id);

-- Unique ledger entry reference numbers
ALTER TABLE ledger_entries 
ADD CONSTRAINT IF NOT EXISTS unique_ledger_reference_number 
    UNIQUE (reference_number);

-- Unique wallet per user per currency
ALTER TABLE wallets 
ADD CONSTRAINT IF NOT EXISTS unique_wallet_user_currency 
    UNIQUE (user_id, currency, deleted) 
    WHERE deleted = FALSE;

-- ============================================================================
-- PART 4: ADD PERFORMANCE INDEXES FOR CRITICAL QUERIES
-- ============================================================================

-- Wallet Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_id ON wallets(user_id) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_currency ON wallets(currency) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_balance ON wallets(balance DESC) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_updated_at ON wallets(updated_at DESC);

-- Transaction Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_id_date ON transactions(user_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_status ON transactions(status) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount ON transactions(amount DESC) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reference ON transactions(reference_number) WHERE deleted = FALSE;

-- Payment Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_user_id_date ON payments(user_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_status_date ON payments(status, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_provider_id ON payments(provider_transaction_id) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_amount ON payments(amount DESC) WHERE deleted = FALSE;

-- Ledger Entry Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_account_date ON ledger_entries(account_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_transaction_id ON ledger_entries(transaction_id) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_entry_type ON ledger_entries(entry_type) WHERE deleted = FALSE;

-- Account Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_user_id ON accounts(user_id) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_type ON accounts(account_type) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_balance ON accounts(balance DESC) WHERE deleted = FALSE;

-- Payment Method Indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_id ON payment_methods(user_id) WHERE deleted = FALSE;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_type ON payment_methods(type) WHERE deleted = FALSE;

-- ============================================================================
-- PART 5: ADD COMPOSITE INDEXES FOR COMPLEX QUERIES
-- ============================================================================

-- Wallet balance history queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_currency_balance 
    ON wallets(user_id, currency, balance DESC) 
    WHERE deleted = FALSE;

-- Transaction reconciliation queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_date_status_amount 
    ON transactions(created_at DESC, status, amount DESC) 
    WHERE deleted = FALSE;

-- Payment provider queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_provider_status_date 
    ON payments(provider, status, created_at DESC) 
    WHERE deleted = FALSE;

-- Ledger double-entry validation queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_transaction_type_amount 
    ON ledger_entries(transaction_id, entry_type, amount) 
    WHERE deleted = FALSE;

-- ============================================================================
-- PART 6: ADD TRIGGERS FOR AUTOMATIC TIMESTAMP UPDATES
-- ============================================================================

-- Create trigger function for updating updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to all financial tables
CREATE TRIGGER update_wallets_updated_at BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ledger_entries_updated_at BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_methods_updated_at BEFORE UPDATE ON payment_methods
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_accounts_updated_at BEFORE UPDATE ON merchant_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- PART 7: ADD VIEWS FOR FINANCIAL REPORTING
-- ============================================================================

-- Active Wallets View (excluding soft-deleted)
CREATE OR REPLACE VIEW v_active_wallets AS
SELECT * FROM wallets WHERE deleted = FALSE;

-- Active Transactions View
CREATE OR REPLACE VIEW v_active_transactions AS
SELECT * FROM transactions WHERE deleted = FALSE;

-- Active Payments View
CREATE OR REPLACE VIEW v_active_payments AS
SELECT * FROM payments WHERE deleted = FALSE;

-- Balanced Ledger Entries View (for reconciliation)
CREATE OR REPLACE VIEW v_balanced_ledger_entries AS
SELECT 
    transaction_id,
    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) as total_debits,
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) as total_credits,
    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) as net_balance
FROM ledger_entries
WHERE deleted = FALSE
GROUP BY transaction_id
HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) = 0;

-- ============================================================================
-- PART 8: ADD COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON COLUMN wallets.version IS 'Optimistic locking version - prevents race conditions';
COMMENT ON COLUMN wallets.created_at IS 'Entity creation timestamp - automatically set';
COMMENT ON COLUMN wallets.updated_at IS 'Last modification timestamp - automatically updated';
COMMENT ON COLUMN wallets.deleted IS 'Soft delete flag - preserves audit trail';

COMMENT ON CONSTRAINT check_wallet_balance_non_negative ON wallets 
    IS 'CRITICAL: Prevents negative wallet balances';
    
COMMENT ON CONSTRAINT unique_transaction_reference_number ON transactions 
    IS 'CRITICAL: Ensures transaction reference numbers are globally unique';

-- ============================================================================
-- VALIDATION QUERIES (Run these after migration to verify)
-- ============================================================================

-- Count records with version field
-- SELECT 'wallets' as table_name, COUNT(*) as total, 
--        COUNT(version) as with_version 
-- FROM wallets;

-- Verify all balances are non-negative
-- SELECT 'wallets' as table_name, COUNT(*) as negative_balances 
-- FROM wallets WHERE balance < 0;

-- Verify ledger entries balance for each transaction
-- SELECT transaction_id, COUNT(*) as entry_count,
--        SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) as balance
-- FROM ledger_entries
-- WHERE deleted = FALSE
-- GROUP BY transaction_id
-- HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END) != 0;

-- ============================================================================
-- END OF MIGRATION
-- ============================================================================

COMMIT;