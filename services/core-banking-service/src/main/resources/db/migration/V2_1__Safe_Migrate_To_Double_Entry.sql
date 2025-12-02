-- CRITICAL PRODUCTION SAFETY FIX: Safe Migration from V1 to V2 Schema
-- This migration safely transforms V1 schema to V2 double-entry schema
-- WITHOUT data loss by preserving existing accounts and transactions

-- =====================================================================
-- PHASE 1: CREATE BACKUP TABLES
-- =====================================================================

-- Backup existing accounts before schema change
CREATE TABLE accounts_backup_v1 AS
SELECT * FROM accounts;

-- Backup existing transactions before schema change
CREATE TABLE transactions_backup_v1 AS
SELECT * FROM transactions;

COMMENT ON TABLE accounts_backup_v1 IS 'V1 schema backup - created during V2 migration for rollback safety';
COMMENT ON TABLE transactions_backup_v1 IS 'V1 schema backup - created during V2 migration for rollback safety';

-- Log migration start
DO $$
BEGIN
    RAISE NOTICE 'MIGRATION V2.1: Backed up % accounts and % transactions',
        (SELECT COUNT(*) FROM accounts_backup_v1),
        (SELECT COUNT(*) FROM transactions_backup_v1);
END $$;

-- =====================================================================
-- PHASE 2: CREATE NEW TABLES (V2 Schema) WITH DIFFERENT NAMES
-- =====================================================================

-- Create V2 accounts table with new name first
CREATE TABLE accounts_v2 (
    account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20) UNIQUE NOT NULL,
    user_id UUID,
    business_id UUID,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL CHECK (account_type IN (
        'USER_WALLET', 'USER_SAVINGS', 'USER_CREDIT',
        'BUSINESS_OPERATING', 'BUSINESS_ESCROW',
        'SYSTEM_ASSET', 'SYSTEM_LIABILITY', 'FEE_COLLECTION',
        'SUSPENSE', 'NOSTRO', 'MERCHANT', 'TRANSIT', 'RESERVE'
    )),
    account_category VARCHAR(20) NOT NULL CHECK (account_category IN (
        'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'
    )),
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PENDING', 'ACTIVE', 'SUSPENDED', 'FROZEN', 'DORMANT', 'CLOSED'
    )) DEFAULT 'PENDING',
    currency VARCHAR(3) NOT NULL,
    current_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    reserved_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    credit_limit DECIMAL(19,4),
    daily_transaction_limit DECIMAL(19,4),
    monthly_transaction_limit DECIMAL(19,4),
    parent_account_id UUID,
    account_code VARCHAR(20),
    minimum_balance DECIMAL(19,4),
    maximum_balance DECIMAL(19,4),
    interest_rate DECIMAL(8,6),
    fee_schedule_id UUID,
    compliance_level VARCHAR(20) NOT NULL CHECK (compliance_level IN (
        'STANDARD', 'ENHANCED', 'RESTRICTED', 'MONITORED', 'BLOCKED'
    )) DEFAULT 'STANDARD',
    freeze_reason TEXT,
    closure_reason TEXT,
    last_transaction_date TIMESTAMP WITH TIME ZONE,
    last_statement_date TIMESTAMP WITH TIME ZONE,
    opened_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_date TIMESTAMP WITH TIME ZONE,
    external_account_id VARCHAR(100),
    routing_number VARCHAR(20),
    iban VARCHAR(50),
    swift_code VARCHAR(20),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- Create V2 transactions table with new name first
CREATE TABLE transactions_v2 (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_number VARCHAR(50) UNIQUE NOT NULL,
    transaction_type VARCHAR(30) NOT NULL CHECK (transaction_type IN (
        'P2P_TRANSFER', 'P2P_REQUEST', 'DEPOSIT', 'WITHDRAWAL', 'INTERNAL_TRANSFER',
        'FEE_CHARGE', 'FEE_REFUND', 'INTEREST_CREDIT', 'INTEREST_DEBIT',
        'SYSTEM_ADJUSTMENT', 'RECONCILIATION', 'REVERSAL',
        'BANK_TRANSFER', 'CARD_PAYMENT', 'ACH_TRANSFER', 'WIRE_TRANSFER',
        'MERCHANT_PAYMENT', 'MERCHANT_REFUND', 'HOLD_PLACEMENT', 'HOLD_RELEASE'
    )),
    source_account_id UUID,
    target_account_id UUID,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    fee_amount DECIMAL(19,4),
    exchange_rate DECIMAL(19,8),
    converted_amount DECIMAL(19,4),
    converted_currency VARCHAR(3),
    description TEXT NOT NULL,
    reference VARCHAR(50),
    external_reference VARCHAR(100),
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'PENDING', 'AUTHORIZED', 'PROCESSING', 'COMPLETED', 'FAILED',
        'CANCELLED', 'REVERSED', 'PARTIALLY_COMPLETED', 'REQUIRES_APPROVAL', 'COMPLIANCE_HOLD'
    )) DEFAULT 'PENDING',
    initiated_by UUID,
    approved_by UUID,
    batch_id UUID,
    priority VARCHAR(20) NOT NULL CHECK (priority IN (
        'LOW', 'NORMAL', 'HIGH', 'URGENT', 'IMMEDIATE'
    )) DEFAULT 'NORMAL',
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    value_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settlement_date TIMESTAMP WITH TIME ZONE,
    authorized_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_attempts INTEGER NOT NULL DEFAULT 3,
    idempotency_key VARCHAR(255) UNIQUE,
    metadata TEXT,
    reversal_transaction_id UUID,
    original_transaction_id UUID,
    parent_transaction_id UUID,
    reconciliation_id UUID,
    compliance_check_id UUID,
    risk_score INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- PHASE 3: MIGRATE DATA FROM V1 TO V2 SCHEMA
-- =====================================================================

-- Migrate accounts with schema transformation
INSERT INTO accounts_v2 (
    account_id,
    account_number,
    user_id,
    account_name,
    account_type,
    account_category,
    status,
    currency,
    current_balance,
    available_balance,
    pending_balance,
    reserved_balance,
    credit_limit,
    daily_transaction_limit,
    monthly_transaction_limit,
    minimum_balance,
    compliance_level,
    metadata,
    created_at,
    updated_at,
    version
)
SELECT
    id,
    account_number,
    user_id,
    COALESCE('Account ' || account_number, 'Migrated Account'),  -- Generate account name
    -- Transform V1 account_type to V2 account_type
    CASE
        WHEN account_type = 'CHECKING' THEN 'USER_WALLET'
        WHEN account_type = 'SAVINGS' THEN 'USER_SAVINGS'
        WHEN account_type = 'BUSINESS' THEN 'BUSINESS_OPERATING'
        WHEN account_type = 'ESCROW' THEN 'BUSINESS_ESCROW'
        ELSE 'USER_WALLET'
    END,
    'ASSET',  -- Default category for user accounts
    -- Transform V1 account_status to V2 status
    CASE
        WHEN account_status = 'ACTIVE' THEN 'ACTIVE'
        WHEN account_status = 'INACTIVE' THEN 'SUSPENDED'
        WHEN account_status = 'SUSPENDED' THEN 'FROZEN'
        WHEN account_status = 'CLOSED' THEN 'CLOSED'
        ELSE 'PENDING'
    END,
    currency,
    balance,
    available_balance,
    0.0000,  -- pending_balance (not in V1)
    frozen_amount,  -- Map frozen_amount to reserved_balance
    overdraft_limit,  -- credit_limit
    daily_withdrawal_limit,
    monthly_withdrawal_limit,
    minimum_balance,
    -- Transform compliance_level
    CASE
        WHEN compliance_level = 'BASIC' THEN 'STANDARD'
        WHEN compliance_level = 'STANDARD' THEN 'STANDARD'
        WHEN compliance_level = 'ENHANCED' THEN 'ENHANCED'
        WHEN compliance_level = 'PREMIUM' THEN 'ENHANCED'
        ELSE 'STANDARD'
    END,
    metadata::TEXT,  -- Convert JSONB to TEXT
    created_at,
    updated_at,
    version
FROM accounts_backup_v1;

-- Migrate transactions with schema transformation
INSERT INTO transactions_v2 (
    id,
    transaction_number,
    transaction_type,
    source_account_id,
    target_account_id,
    amount,
    currency,
    fee_amount,
    exchange_rate,
    description,
    reference,
    external_reference,
    status,
    priority,
    transaction_date,
    value_date,
    settlement_date,
    failure_reason,
    idempotency_key,
    metadata,
    original_transaction_id,
    risk_score,
    created_at,
    updated_at,
    processed_at,
    version
)
SELECT
    id,
    transaction_id,
    -- Transform V1 transaction_type to V2 transaction_type
    CASE
        WHEN transaction_type = 'TRANSFER' THEN 'P2P_TRANSFER'
        WHEN transaction_type = 'PAYMENT' THEN 'MERCHANT_PAYMENT'
        WHEN transaction_type = 'DEPOSIT' THEN 'DEPOSIT'
        WHEN transaction_type = 'WITHDRAWAL' THEN 'WITHDRAWAL'
        WHEN transaction_type = 'REVERSAL' THEN 'REVERSAL'
        ELSE 'P2P_TRANSFER'
    END,
    from_account_id,
    to_account_id,
    amount,
    currency,
    processing_fee,
    exchange_rate,
    COALESCE(description, 'Migrated transaction'),
    reference_number,
    external_reference,
    -- Transform V1 transaction_status to V2 status
    CASE
        WHEN transaction_status = 'PENDING' THEN 'PENDING'
        WHEN transaction_status = 'PROCESSING' THEN 'PROCESSING'
        WHEN transaction_status = 'COMPLETED' THEN 'COMPLETED'
        WHEN transaction_status = 'FAILED' THEN 'FAILED'
        WHEN transaction_status = 'CANCELLED' THEN 'CANCELLED'
        WHEN transaction_status = 'REVERSED' THEN 'REVERSED'
        ELSE 'PENDING'
    END,
    'NORMAL',  -- Default priority
    created_at,
    COALESCE(value_date::TIMESTAMP WITH TIME ZONE, created_at),  -- Convert DATE to TIMESTAMP
    settlement_date::TIMESTAMP WITH TIME ZONE,
    NULL,  -- failure_reason (can be added later if needed)
    NULL,  -- idempotency_key (generate if needed)
    metadata::TEXT,  -- Convert JSONB to TEXT
    original_transaction_id,
    risk_score,
    created_at,
    updated_at,
    processed_at,
    version
FROM transactions_backup_v1;

-- Log migration progress
DO $$
DECLARE
    v1_account_count INTEGER;
    v2_account_count INTEGER;
    v1_transaction_count INTEGER;
    v2_transaction_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v1_account_count FROM accounts_backup_v1;
    SELECT COUNT(*) INTO v2_account_count FROM accounts_v2;
    SELECT COUNT(*) INTO v1_transaction_count FROM transactions_backup_v1;
    SELECT COUNT(*) INTO v2_transaction_count FROM transactions_v2;

    RAISE NOTICE 'MIGRATION V2.1: Migrated % of % accounts (% success)',
        v2_account_count, v1_account_count,
        CASE WHEN v1_account_count > 0
            THEN ROUND(100.0 * v2_account_count / v1_account_count, 2)
            ELSE 100
        END || '%';

    RAISE NOTICE 'MIGRATION V2.1: Migrated % of % transactions (% success)',
        v2_transaction_count, v1_transaction_count,
        CASE WHEN v1_transaction_count > 0
            THEN ROUND(100.0 * v2_transaction_count / v1_transaction_count, 2)
            ELSE 100
        END || '%';

    -- Verification check
    IF v1_account_count != v2_account_count OR v1_transaction_count != v2_transaction_count THEN
        RAISE EXCEPTION 'MIGRATION V2.1 FAILED: Data count mismatch detected!';
    END IF;

    RAISE NOTICE 'MIGRATION V2.1: Data migration verified successfully';
END $$;

-- =====================================================================
-- PHASE 4: SWAP TABLES ATOMICALLY
-- =====================================================================

-- Drop V1 tables and rename V2 tables to primary names
BEGIN;
    -- Drop old tables
    DROP TABLE IF EXISTS accounts CASCADE;
    DROP TABLE IF EXISTS transactions CASCADE;

    -- Rename V2 tables to primary names
    ALTER TABLE accounts_v2 RENAME TO accounts;
    ALTER TABLE transactions_v2 RENAME TO transactions;

    -- Rename account_id to id for consistency (optional - may break foreign keys)
    -- ALTER TABLE accounts RENAME COLUMN account_id TO id;

COMMIT;

-- =====================================================================
-- PHASE 5: CREATE REMAINING V2 TABLES (Ledger, Chart of Accounts, etc.)
-- =====================================================================

-- Ledger Entries Table
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    entry_number BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(19,4) NOT NULL,
    running_balance DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT NOT NULL,
    reference VARCHAR(50),
    external_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PENDING', 'POSTED', 'REVERSED', 'FAILED', 'CANCELLED'
    )) DEFAULT 'PENDING',
    entry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    value_date TIMESTAMP WITH TIME ZONE NOT NULL,
    posting_date TIMESTAMP WITH TIME ZONE,
    reversal_entry_id UUID,
    original_entry_id UUID,
    reconciliation_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

-- Chart of Accounts Table
CREATE TABLE chart_of_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code VARCHAR(20) UNIQUE NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    account_category VARCHAR(20) NOT NULL,
    parent_code VARCHAR(20),
    level INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- Account Reconciliation Table
CREATE TABLE account_reconciliations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    reconciliation_date TIMESTAMP WITH TIME ZONE NOT NULL,
    opening_balance DECIMAL(19,4) NOT NULL,
    closing_balance DECIMAL(19,4) NOT NULL,
    calculated_balance DECIMAL(19,4) NOT NULL,
    variance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PENDING', 'BALANCED', 'VARIANCE', 'INVESTIGATING', 'RESOLVED'
    )) DEFAULT 'PENDING',
    variance_reason TEXT,
    reconciled_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reconciliations_account FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

-- Transaction Batches Table
CREATE TABLE transaction_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_number VARCHAR(50) UNIQUE NOT NULL,
    batch_type VARCHAR(30) NOT NULL,
    total_transactions INTEGER NOT NULL DEFAULT 0,
    total_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'
    )) DEFAULT 'PENDING',
    created_by UUID NOT NULL,
    processed_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    metadata TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- PHASE 6: CREATE INDEXES
-- =====================================================================

-- Accounts indexes
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_type_status ON accounts(account_type, status);
CREATE INDEX idx_accounts_parent_account ON accounts(parent_account_id);
CREATE INDEX idx_accounts_currency ON accounts(currency);
CREATE INDEX idx_accounts_created_at ON accounts(created_at);
CREATE INDEX idx_accounts_updated_at ON accounts(updated_at);

-- Transactions indexes
CREATE INDEX idx_transactions_number ON transactions(transaction_number);
CREATE INDEX idx_transactions_source_account ON transactions(source_account_id);
CREATE INDEX idx_transactions_target_account ON transactions(target_account_id);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_external_ref ON transactions(external_reference);
CREATE INDEX idx_transactions_batch_id ON transactions(batch_id);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);

-- Ledger entries indexes
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_account ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_date ON ledger_entries(entry_date);
CREATE INDEX idx_ledger_entries_type ON ledger_entries(entry_type);
CREATE INDEX idx_ledger_entries_reference ON ledger_entries(reference);
CREATE INDEX idx_ledger_entries_account_date ON ledger_entries(account_id, entry_date);
CREATE INDEX idx_ledger_entries_status ON ledger_entries(status);

-- Chart of accounts indexes
CREATE INDEX idx_chart_accounts_code ON chart_of_accounts(account_code);
CREATE INDEX idx_chart_accounts_parent ON chart_of_accounts(parent_code);
CREATE INDEX idx_chart_accounts_type ON chart_of_accounts(account_type);
CREATE INDEX idx_chart_accounts_active ON chart_of_accounts(is_active);

-- Reconciliation indexes
CREATE INDEX idx_reconciliations_account ON account_reconciliations(account_id);
CREATE INDEX idx_reconciliations_date ON account_reconciliations(reconciliation_date);
CREATE INDEX idx_reconciliations_status ON account_reconciliations(status);

-- Batch indexes
CREATE INDEX idx_batches_number ON transaction_batches(batch_number);
CREATE INDEX idx_batches_type ON transaction_batches(batch_type);
CREATE INDEX idx_batches_status ON transaction_batches(status);
CREATE INDEX idx_batches_created_at ON transaction_batches(created_at);

-- =====================================================================
-- PHASE 7: CREATE UNIQUE CONSTRAINTS
-- =====================================================================

-- Ensure one primary account per user per currency
CREATE UNIQUE INDEX idx_accounts_user_currency_primary
ON accounts(user_id, currency)
WHERE is_primary = TRUE AND user_id IS NOT NULL;

-- Ensure unique transaction numbers
CREATE UNIQUE INDEX idx_transactions_number_unique ON transactions(transaction_number);

-- =====================================================================
-- PHASE 8: CREATE FOREIGN KEY CONSTRAINTS
-- =====================================================================

-- Add foreign key constraints for transactions
ALTER TABLE transactions
ADD CONSTRAINT fk_transactions_source_account
FOREIGN KEY (source_account_id) REFERENCES accounts(account_id);

ALTER TABLE transactions
ADD CONSTRAINT fk_transactions_target_account
FOREIGN KEY (target_account_id) REFERENCES accounts(account_id);

-- Add foreign key for account parent relationship
ALTER TABLE accounts
ADD CONSTRAINT fk_accounts_parent
FOREIGN KEY (parent_account_id) REFERENCES accounts(account_id);

-- Add foreign key for chart of accounts
ALTER TABLE chart_of_accounts
ADD CONSTRAINT fk_chart_accounts_parent
FOREIGN KEY (parent_code) REFERENCES chart_of_accounts(account_code);

-- =====================================================================
-- PHASE 9: CREATE TRIGGERS
-- =====================================================================

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ledger_entries_updated_at
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_chart_accounts_updated_at
    BEFORE UPDATE ON chart_of_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reconciliations_updated_at
    BEFORE UPDATE ON account_reconciliations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================================
-- PHASE 10: INSERT SYSTEM ACCOUNTS
-- =====================================================================

-- Insert system accounts required for double-entry bookkeeping
INSERT INTO accounts (
    account_number, account_name, account_type, account_category,
    status, currency, current_balance, available_balance,
    created_by
) VALUES
('SYS-CASH-001', 'System Cash Account', 'SYSTEM_ASSET', 'ASSET', 'ACTIVE', 'USD', 0.0000, 0.0000, 'SYSTEM'),
('SYS-LIAB-001', 'System Liability Account', 'SYSTEM_LIABILITY', 'LIABILITY', 'ACTIVE', 'USD', 0.0000, 0.0000, 'SYSTEM'),
('SYS-FEE-001', 'Fee Collection Account', 'FEE_COLLECTION', 'REVENUE', 'ACTIVE', 'USD', 0.0000, 0.0000, 'SYSTEM'),
('SYS-SUSP-001', 'Suspense Account', 'SUSPENSE', 'ASSET', 'ACTIVE', 'USD', 0.0000, 0.0000, 'SYSTEM'),
('SYS-RES-001', 'Reserve Account', 'RESERVE', 'ASSET', 'ACTIVE', 'USD', 0.0000, 0.0000, 'SYSTEM');

-- Insert basic chart of accounts
INSERT INTO chart_of_accounts (
    account_code, account_name, account_type, account_category, level, created_by
) VALUES
('1000', 'ASSETS', 'SYSTEM_ASSET', 'ASSET', 1, 'SYSTEM'),
('1100', 'Current Assets', 'SYSTEM_ASSET', 'ASSET', 2, 'SYSTEM'),
('1200', 'User Wallets', 'USER_WALLET', 'ASSET', 2, 'SYSTEM'),
('2000', 'LIABILITIES', 'SYSTEM_LIABILITY', 'LIABILITY', 1, 'SYSTEM'),
('2100', 'Current Liabilities', 'SYSTEM_LIABILITY', 'LIABILITY', 2, 'SYSTEM'),
('4000', 'REVENUE', 'FEE_COLLECTION', 'REVENUE', 1, 'SYSTEM'),
('4100', 'Fee Income', 'FEE_COLLECTION', 'REVENUE', 2, 'SYSTEM');

-- =====================================================================
-- PHASE 11: FINAL VERIFICATION AND LOGGING
-- =====================================================================

DO $$
DECLARE
    final_account_count INTEGER;
    final_transaction_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO final_account_count FROM accounts;
    SELECT COUNT(*) INTO final_transaction_count FROM transactions;

    RAISE NOTICE '============================================================';
    RAISE NOTICE 'MIGRATION V2.1 COMPLETED SUCCESSFULLY';
    RAISE NOTICE '============================================================';
    RAISE NOTICE 'Final accounts count: %', final_account_count;
    RAISE NOTICE 'Final transactions count: %', final_transaction_count;
    RAISE NOTICE 'Backup tables preserved: accounts_backup_v1, transactions_backup_v1';
    RAISE NOTICE 'Migration completed at: %', CURRENT_TIMESTAMP;
    RAISE NOTICE '============================================================';
END $$;

-- Add comment documenting the migration
COMMENT ON TABLE accounts_backup_v1 IS 'Backup of V1 accounts schema. Safe to drop after verifying V2 migration success for 30+ days.';
COMMENT ON TABLE transactions_backup_v1 IS 'Backup of V1 transactions schema. Safe to drop after verifying V2 migration success for 30+ days.';
