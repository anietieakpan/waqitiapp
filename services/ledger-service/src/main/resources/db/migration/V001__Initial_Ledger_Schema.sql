-- =============================================================================
-- Waqiti Ledger Service - Consolidated Initial Database Schema
-- =============================================================================
-- Version: 1.0.0
-- Date: 2025-11-08
-- Description: Complete double-entry ledger system with comprehensive features
-- Compliance: SOX, Basel III, IFRS, GAAP
-- =============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- CUSTOM TYPES
-- =============================================================================

CREATE TYPE account_type AS ENUM (
    'ASSET', 'CURRENT_ASSET', 'FIXED_ASSET', 'OTHER_ASSET',
    'LIABILITY', 'CURRENT_LIABILITY', 'LONG_TERM_LIABILITY',
    'EQUITY', 'RETAINED_EARNINGS', 'CAPITAL',
    'REVENUE', 'OPERATING_REVENUE', 'OTHER_REVENUE',
    'EXPENSE', 'OPERATING_EXPENSE', 'COST_OF_GOODS_SOLD', 'OTHER_EXPENSE',
    'BANK', 'CONTRA_ASSET', 'CONTRA_LIABILITY'
);

CREATE TYPE normal_balance AS ENUM ('DEBIT', 'CREDIT');

CREATE TYPE entry_type AS ENUM ('DEBIT', 'CREDIT', 'RESERVATION', 'RELEASE', 'PENDING');

CREATE TYPE entry_status AS ENUM ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'POSTED', 'REVERSED', 'CANCELLED');

CREATE TYPE transaction_type AS ENUM (
    'JOURNAL_ENTRY', 'INVOICE', 'PAYMENT', 'RECEIPT', 'TRANSFER',
    'ADJUSTMENT', 'OPENING_BALANCE', 'CLOSING_ENTRY', 'REVERSAL',
    'ACCRUAL', 'PREPAYMENT', 'DEPRECIATION', 'REVALUATION', 'CONSOLIDATION'
);

CREATE TYPE transaction_status AS ENUM (
    'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'POSTED', 'REVERSED', 'CANCELLED', 'ON_HOLD'
);

CREATE TYPE period_status AS ENUM ('OPEN', 'CLOSING', 'CLOSED', 'LOCKED');

CREATE TYPE reconciliation_status_enum AS ENUM (
    'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'RECONCILED', 'DISPUTED', 'CORRECTED', 'ARCHIVED'
);

-- =============================================================================
-- CHART OF ACCOUNTS
-- =============================================================================

CREATE TABLE chart_of_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code VARCHAR(20) UNIQUE NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    parent_account_id UUID REFERENCES chart_of_accounts(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chart_accounts_type ON chart_of_accounts(account_type);
CREATE INDEX idx_chart_accounts_parent ON chart_of_accounts(parent_account_id);
CREATE INDEX idx_chart_accounts_code ON chart_of_accounts(account_code);

COMMENT ON TABLE chart_of_accounts IS 'Hierarchical chart of accounts for financial categorization';

-- =============================================================================
-- ACCOUNTS TABLE
-- =============================================================================

CREATE TABLE accounts (
    account_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_code VARCHAR(50) NOT NULL UNIQUE,
    account_name VARCHAR(200) NOT NULL,
    account_type account_type NOT NULL,
    parent_account_id UUID REFERENCES accounts(account_id),
    chart_account_id UUID REFERENCES chart_of_accounts(id),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    allows_transactions BOOLEAN NOT NULL DEFAULT true,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    normal_balance normal_balance NOT NULL,
    opening_balance DECIMAL(19,4) DEFAULT 0.0000,
    current_balance DECIMAL(19,4) DEFAULT 0.0000,

    -- Organizational hierarchy
    company_id UUID,
    department_id UUID,
    cost_center_id UUID,

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0
);

-- Accounts indexes
CREATE INDEX idx_account_code ON accounts(account_code);
CREATE INDEX idx_account_type ON accounts(account_type);
CREATE INDEX idx_parent_account ON accounts(parent_account_id);
CREATE INDEX idx_account_active ON accounts(is_active);
CREATE INDEX idx_account_company ON accounts(company_id);
CREATE INDEX idx_account_department ON accounts(department_id);
CREATE INDEX idx_account_cost_center ON accounts(cost_center_id);

COMMENT ON TABLE accounts IS 'Individual general ledger accounts';

-- =============================================================================
-- TRANSACTIONS TABLE
-- =============================================================================

CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_number VARCHAR(50) NOT NULL UNIQUE,
    transaction_type transaction_type NOT NULL,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    value_date TIMESTAMP WITH TIME ZONE,
    description TEXT NOT NULL,
    reference_number VARCHAR(100),
    external_reference VARCHAR(100),
    total_debit_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    total_credit_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    exchange_rate DECIMAL(10,6) DEFAULT 1.000000,
    status transaction_status NOT NULL DEFAULT 'DRAFT',
    accounting_period VARCHAR(20),
    fiscal_year INTEGER,
    is_balanced BOOLEAN DEFAULT false,
    is_closing_transaction BOOLEAN DEFAULT false,
    is_adjusting_transaction BOOLEAN DEFAULT false,
    is_reversing_transaction BOOLEAN DEFAULT false,

    -- Reversal information
    reversal_of_transaction_id UUID REFERENCES transactions(transaction_id),
    reversed_by_transaction_id UUID REFERENCES transactions(transaction_id),
    reversal_date TIMESTAMP WITH TIME ZONE,
    reversal_reason TEXT,

    -- Organizational data
    company_id UUID,
    branch_id UUID,
    department_id UUID,
    cost_center_id UUID,
    project_id UUID,

    -- Source system information
    source_system VARCHAR(50),
    source_document_type VARCHAR(50),
    source_document_id UUID,

    -- Attachments and metadata
    attachment_urls TEXT,
    notes TEXT,
    tags TEXT,

    -- Approval workflow
    requires_approval BOOLEAN DEFAULT false,
    approval_level INTEGER,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    rejected_by VARCHAR(100),
    rejected_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT,

    -- Posting information
    posted_by VARCHAR(100),
    posted_at TIMESTAMP WITH TIME ZONE,

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_balanced_transaction CHECK (
        (status != 'POSTED') OR (total_debit_amount = total_credit_amount)
    )
);

-- Transactions indexes
CREATE UNIQUE INDEX idx_transaction_number ON transactions(transaction_number);
CREATE INDEX idx_transaction_date ON transactions(transaction_date);
CREATE INDEX idx_transaction_type ON transactions(transaction_type);
CREATE INDEX idx_transaction_status ON transactions(status);
CREATE INDEX idx_transaction_period ON transactions(accounting_period);
CREATE INDEX idx_transaction_company ON transactions(company_id);
CREATE INDEX idx_transaction_posted ON transactions(posted_at);
CREATE INDEX idx_transaction_approval ON transactions(requires_approval, status);
CREATE INDEX idx_transaction_reversal ON transactions(reversal_of_transaction_id);

COMMENT ON TABLE transactions IS 'Parent transaction records grouping related ledger entries';

-- =============================================================================
-- LEDGER ENTRIES TABLE (IMMUTABLE)
-- =============================================================================

CREATE TABLE ledger_entries (
    ledger_entry_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    transaction_id UUID NOT NULL REFERENCES transactions(transaction_id),
    entry_type entry_type NOT NULL,
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) DEFAULT 'USD',
    exchange_rate DECIMAL(10,6) DEFAULT 1.000000,
    base_amount DECIMAL(19,4),
    description TEXT,
    reference_number VARCHAR(100),
    external_reference VARCHAR(100),
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    value_date TIMESTAMP WITH TIME ZONE,
    accounting_period VARCHAR(20),
    status entry_status NOT NULL DEFAULT 'POSTED',
    posting_date TIMESTAMP WITH TIME ZONE,

    -- Reversal tracking
    reversal_of_entry_id UUID REFERENCES ledger_entries(ledger_entry_id),
    reversed_by_entry_id UUID REFERENCES ledger_entries(ledger_entry_id),
    is_closing_entry BOOLEAN DEFAULT false,
    is_adjusting_entry BOOLEAN DEFAULT false,
    is_reversing_entry BOOLEAN DEFAULT false,
    is_reversal BOOLEAN DEFAULT false,

    -- Reconciliation fields
    reconciled BOOLEAN DEFAULT false,
    reconciliation_id UUID,
    reconciled_date TIMESTAMP WITH TIME ZONE,
    reconciled_by VARCHAR(100),
    reconciliation_status VARCHAR(50) DEFAULT 'PENDING',

    -- Running balance (denormalized for performance)
    running_balance DECIMAL(19,4),
    balance_after DECIMAL(19,4),

    -- Document attachments
    attachment_urls TEXT,

    -- Source tracking
    source_system VARCHAR(100) NOT NULL DEFAULT 'WAQITI_CORE',
    batch_id UUID,

    -- Audit fields (IMMUTABLE - never updated after insert)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0,

    -- Organizational metadata
    cost_center_id UUID,
    project_id UUID,
    department_id UUID,
    tags TEXT,
    metadata JSONB,

    -- Constraints
    CONSTRAINT chk_reversal_has_original CHECK (
        (is_reversal = FALSE AND reversal_of_entry_id IS NULL) OR
        (is_reversal = TRUE AND reversal_of_entry_id IS NOT NULL)
    )
);

-- Ledger entries indexes
CREATE INDEX idx_ledger_account ON ledger_entries(account_id);
CREATE INDEX idx_ledger_transaction ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_date ON ledger_entries(transaction_date);
CREATE INDEX idx_ledger_period ON ledger_entries(accounting_period);
CREATE INDEX idx_ledger_status ON ledger_entries(status);
CREATE INDEX idx_ledger_type ON ledger_entries(entry_type);
CREATE INDEX idx_ledger_reference ON ledger_entries(reference_number);
CREATE INDEX idx_ledger_reconciled ON ledger_entries(reconciled);
CREATE INDEX idx_ledger_reconciliation ON ledger_entries(reconciliation_id);
CREATE INDEX idx_ledger_reversal ON ledger_entries(reversal_of_entry_id);

-- Composite indexes for common queries
CREATE INDEX idx_ledger_account_date ON ledger_entries(account_id, transaction_date);
CREATE INDEX idx_ledger_account_period ON ledger_entries(account_id, accounting_period);
CREATE INDEX idx_ledger_period_status ON ledger_entries(accounting_period, status);

COMMENT ON TABLE ledger_entries IS 'Immutable double-entry ledger entries - cannot be modified after creation';
COMMENT ON COLUMN ledger_entries.is_reversal IS 'Reversal entries must have same amount as original entry but opposite entry_type';

-- =============================================================================
-- JOURNAL ENTRIES TABLE
-- =============================================================================

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_number VARCHAR(50) UNIQUE NOT NULL,
    transaction_id VARCHAR(50) NOT NULL,
    journal_type VARCHAR(20) NOT NULL CHECK (journal_type IN ('TRANSFER', 'PAYMENT', 'ADJUSTMENT', 'REVERSAL', 'ACCRUAL')),
    description TEXT NOT NULL,
    total_debits DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_credits DECIMAL(19,4) NOT NULL DEFAULT 0,
    posting_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED')) DEFAULT 'DRAFT',
    posted_by UUID,
    reversed_by UUID,
    reversal_reason TEXT,
    reference_number VARCHAR(100),
    source_system VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMP WITH TIME ZONE,
    reversed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_balanced_journal CHECK (total_debits = total_credits OR status = 'DRAFT')
);

CREATE INDEX idx_journal_entries_transaction_id ON journal_entries(transaction_id);
CREATE INDEX idx_journal_entries_posting_date ON journal_entries(posting_date);
CREATE INDEX idx_journal_entries_status ON journal_entries(status);
CREATE INDEX idx_journal_entries_type ON journal_entries(journal_type);

-- =============================================================================
-- ACCOUNT BALANCES TABLE
-- =============================================================================

CREATE TABLE account_balances (
    balance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(255) NOT NULL UNIQUE,
    chart_account_id UUID REFERENCES chart_of_accounts(id),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Balance fields
    current_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    debit_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    credit_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    net_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    opening_balance DECIMAL(19,4) DEFAULT 0.0000,
    closing_balance DECIMAL(19,4),
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_debits DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_credits DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    reserved_balance DECIMAL(19,4) DEFAULT 0.0000,
    credit_limit DECIMAL(19,4),

    -- Tracking
    last_entry_id UUID REFERENCES ledger_entries(ledger_entry_id),
    last_transaction_id VARCHAR(255),
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_frozen BOOLEAN DEFAULT false,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Account balances indexes
CREATE INDEX idx_balance_account_id ON account_balances(account_id);
CREATE INDEX idx_balance_chart_account ON account_balances(chart_account_id);
CREATE INDEX idx_balance_currency ON account_balances(currency);
CREATE INDEX idx_balance_last_updated ON account_balances(last_updated);
CREATE INDEX idx_balance_frozen ON account_balances(is_frozen);

-- =============================================================================
-- ACCOUNTING PERIODS TABLE
-- =============================================================================

CREATE TABLE accounting_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status period_status NOT NULL DEFAULT 'OPEN',
    fiscal_year INTEGER NOT NULL,
    fiscal_month INTEGER NOT NULL,

    -- Closing information
    closed_at TIMESTAMP WITH TIME ZONE,
    closed_by UUID,
    closing_entries_id UUID,

    -- Locking information
    locked_at TIMESTAMP WITH TIME ZONE,
    locked_by UUID,
    lock_reason TEXT,

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID,

    CONSTRAINT uk_accounting_period_dates UNIQUE (start_date, end_date),
    CONSTRAINT chk_period_dates CHECK (start_date < end_date)
);

CREATE INDEX idx_accounting_periods_status ON accounting_periods(status);
CREATE INDEX idx_accounting_periods_dates ON accounting_periods(start_date, end_date);
CREATE INDEX idx_accounting_periods_fiscal ON accounting_periods(fiscal_year, fiscal_month);

-- =============================================================================
-- RECONCILIATION TABLES
-- =============================================================================

CREATE TABLE reconciliations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id VARCHAR(50) UNIQUE NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    reconciliation_date DATE NOT NULL,
    statement_balance DECIMAL(19,4) NOT NULL,
    ledger_balance DECIMAL(19,4) NOT NULL,
    difference DECIMAL(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')) DEFAULT 'PENDING',
    reconciled_by UUID,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_reconciliations_account_id ON reconciliations(account_id);
CREATE INDEX idx_reconciliations_date ON reconciliations(reconciliation_date);
CREATE INDEX idx_reconciliations_status ON reconciliations(status);

-- =============================================================================
-- BANK RECONCILIATIONS TABLE
-- =============================================================================

CREATE TABLE bank_reconciliations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_code VARCHAR(20) NOT NULL,
    reconciliation_date DATE NOT NULL,

    -- Bank statement details
    statement_ending_balance DECIMAL(19,4) NOT NULL,
    statement_beginning_balance DECIMAL(19,4) NOT NULL,
    statement_date DATE NOT NULL,

    -- GL details
    gl_ending_balance DECIMAL(19,4) NOT NULL,
    gl_beginning_balance DECIMAL(19,4) NOT NULL,

    -- Reconciliation items
    deposits_in_transit DECIMAL(19,4) DEFAULT 0,
    outstanding_checks DECIMAL(19,4) DEFAULT 0,
    bank_errors DECIMAL(19,4) DEFAULT 0,
    gl_errors DECIMAL(19,4) DEFAULT 0,

    -- Adjusted balances
    adjusted_bank_balance DECIMAL(19,4) NOT NULL,
    adjusted_gl_balance DECIMAL(19,4) NOT NULL,
    variance DECIMAL(19,4) NOT NULL DEFAULT 0,

    -- Status
    status reconciliation_status_enum NOT NULL DEFAULT 'PENDING',
    reconciled_items_count INTEGER DEFAULT 0,
    unreconciled_items_count INTEGER DEFAULT 0,

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    reviewed_by UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_bank_reconciliation UNIQUE (bank_account_code, reconciliation_date)
);

CREATE INDEX idx_bank_reconciliations_account ON bank_reconciliations(bank_account_code);
CREATE INDEX idx_bank_reconciliations_date ON bank_reconciliations(reconciliation_date);
CREATE INDEX idx_bank_reconciliations_status ON bank_reconciliations(status);

-- =============================================================================
-- AUDIT TRAIL TABLE
-- =============================================================================

CREATE TABLE ledger_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name VARCHAR(50) NOT NULL,
    record_id UUID NOT NULL,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    old_values JSONB,
    new_values JSONB,
    changed_by UUID NOT NULL,
    change_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ledger_audit_table_record ON ledger_audit(table_name, record_id);
CREATE INDEX idx_ledger_audit_created_at ON ledger_audit(created_at);

-- =============================================================================
-- LEDGER ENTRY AUDIT LOG (Security monitoring)
-- =============================================================================

CREATE TABLE ledger_entry_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type VARCHAR(20) NOT NULL,
    entry_id UUID,
    transaction_id VARCHAR(255),
    account_id VARCHAR(255),
    attempted_by VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    action_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action_success BOOLEAN NOT NULL,
    failure_reason TEXT,
    old_values JSONB,
    new_values JSONB,
    metadata JSONB
);

CREATE INDEX idx_audit_entry_id ON ledger_entry_audit_log(entry_id);
CREATE INDEX idx_audit_timestamp ON ledger_entry_audit_log(action_timestamp);
CREATE INDEX idx_audit_account ON ledger_entry_audit_log(account_id);
CREATE INDEX idx_audit_attempted_by ON ledger_entry_audit_log(attempted_by);
CREATE INDEX idx_audit_action_type ON ledger_entry_audit_log(action_type);
CREATE INDEX idx_audit_success ON ledger_entry_audit_log(action_success) WHERE action_success = FALSE;

-- =============================================================================
-- WALLET ACCOUNT MAPPING TABLE
-- =============================================================================

CREATE TABLE wallet_account_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    mapping_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(wallet_id, currency)
);

CREATE INDEX idx_wallet_mapping_wallet ON wallet_account_mapping(wallet_id);
CREATE INDEX idx_wallet_mapping_account ON wallet_account_mapping(account_id);

-- =============================================================================
-- TRIGGERS AND FUNCTIONS
-- =============================================================================

-- Function to update updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply update triggers to appropriate tables
CREATE TRIGGER update_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_account_balances_updated_at
    BEFORE UPDATE ON account_balances
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_accounting_periods_updated_at
    BEFORE UPDATE ON accounting_periods
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_reconciliations_updated_at
    BEFORE UPDATE ON bank_reconciliations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_chart_of_accounts_updated_at
    BEFORE UPDATE ON chart_of_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- VIEWS FOR REPORTING
-- =============================================================================

-- Trial Balance View
CREATE OR REPLACE VIEW trial_balance AS
SELECT
    a.account_code,
    a.account_name,
    a.account_type,
    COALESCE(SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END), 0) as debit_balance,
    COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END), 0) as credit_balance,
    COALESCE(SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE -le.amount END), 0) as net_balance
FROM accounts a
LEFT JOIN ledger_entries le ON a.account_id = le.account_id
    AND le.status = 'POSTED'
WHERE a.is_active = true
GROUP BY a.account_id, a.account_code, a.account_name, a.account_type
ORDER BY a.account_code;

-- Account Ledger View
CREATE OR REPLACE VIEW account_ledger AS
SELECT
    le.ledger_entry_id,
    a.account_code,
    a.account_name,
    t.transaction_number,
    t.transaction_date,
    le.description,
    le.reference_number,
    le.entry_type,
    le.amount,
    le.running_balance,
    le.reconciled,
    t.status as transaction_status
FROM ledger_entries le
JOIN accounts a ON le.account_id = a.account_id
JOIN transactions t ON le.transaction_id = t.transaction_id
ORDER BY a.account_code, t.transaction_date, le.created_at;

-- Account Balance Verification View
CREATE OR REPLACE VIEW v_account_balance_verification AS
SELECT
    le.account_id,
    le.currency,
    SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END) as total_credits,
    SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) as total_debits,
    SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END) as calculated_balance,
    ab.current_balance as recorded_balance,
    ABS(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END) - COALESCE(ab.current_balance, 0)) as balance_discrepancy,
    CASE
        WHEN ABS(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END) - COALESCE(ab.current_balance, 0)) > 0.01
        THEN 'DISCREPANCY_FOUND'
        ELSE 'BALANCED'
    END as verification_status,
    COUNT(le.ledger_entry_id) as total_entries,
    MAX(le.created_at) as last_entry_date
FROM ledger_entries le
LEFT JOIN account_balances ab ON le.account_id = ab.account_id AND le.currency = ab.currency
WHERE le.is_reversal = FALSE
GROUP BY le.account_id, le.currency, ab.current_balance;

-- =============================================================================
-- INITIAL DATA
-- =============================================================================

-- Insert default chart of accounts
INSERT INTO chart_of_accounts (account_code, account_name, account_type, description) VALUES
('1000', 'Assets', 'ASSET', 'Root asset account'),
('1100', 'Current Assets', 'ASSET', 'Liquid assets'),
('1110', 'Customer Accounts', 'ASSET', 'Customer account balances'),
('1120', 'Cash and Cash Equivalents', 'ASSET', 'Bank cash reserves'),
('1200', 'Fixed Assets', 'ASSET', 'Long-term assets'),
('2000', 'Liabilities', 'LIABILITY', 'Root liability account'),
('2100', 'Current Liabilities', 'LIABILITY', 'Short-term obligations'),
('2110', 'Customer Deposits', 'LIABILITY', 'Money owed to customers'),
('2120', 'Accrued Expenses', 'LIABILITY', 'Expenses incurred but not paid'),
('3000', 'Equity', 'EQUITY', 'Owner equity'),
('3100', 'Paid-in Capital', 'EQUITY', 'Capital contributions'),
('3200', 'Retained Earnings', 'EQUITY', 'Accumulated profits'),
('4000', 'Revenue', 'REVENUE', 'Income accounts'),
('4100', 'Service Revenue', 'REVENUE', 'Revenue from services'),
('4110', 'Transaction Fees', 'REVENUE', 'Fees from transactions'),
('4120', 'Interest Income', 'REVENUE', 'Interest earned'),
('5000', 'Expenses', 'EXPENSE', 'Operating expenses'),
('5100', 'Operating Expenses', 'EXPENSE', 'Day-to-day expenses'),
('5110', 'Processing Costs', 'EXPENSE', 'Transaction processing costs'),
('5120', 'Compliance Costs', 'EXPENSE', 'Regulatory compliance expenses');

-- Insert basic account structure
INSERT INTO accounts (account_code, account_name, account_type, normal_balance, created_by) VALUES
('1000', 'Cash', 'CURRENT_ASSET', 'DEBIT', 'system'),
('1100', 'Accounts Receivable', 'CURRENT_ASSET', 'DEBIT', 'system'),
('1200', 'Inventory', 'CURRENT_ASSET', 'DEBIT', 'system'),
('1500', 'Equipment', 'FIXED_ASSET', 'DEBIT', 'system'),
('1510', 'Accumulated Depreciation - Equipment', 'CONTRA_ASSET', 'CREDIT', 'system'),
('2000', 'Accounts Payable', 'CURRENT_LIABILITY', 'CREDIT', 'system'),
('2100', 'Notes Payable', 'LONG_TERM_LIABILITY', 'CREDIT', 'system'),
('3000', 'Owner''s Equity', 'EQUITY', 'CREDIT', 'system'),
('3100', 'Retained Earnings', 'RETAINED_EARNINGS', 'CREDIT', 'system'),
('4000', 'Sales Revenue', 'REVENUE', 'CREDIT', 'system'),
('5000', 'Cost of Goods Sold', 'COST_OF_GOODS_SOLD', 'DEBIT', 'system'),
('6000', 'Operating Expenses', 'OPERATING_EXPENSE', 'DEBIT', 'system'),
('6100', 'Rent Expense', 'OPERATING_EXPENSE', 'DEBIT', 'system'),
('6200', 'Salaries Expense', 'OPERATING_EXPENSE', 'DEBIT', 'system'),
('6300', 'Utilities Expense', 'OPERATING_EXPENSE', 'DEBIT', 'system');

-- Insert default accounting periods
INSERT INTO accounting_periods (id, period_name, start_date, end_date, status, fiscal_year, fiscal_month, created_by)
VALUES
    (gen_random_uuid(), 'January 2025', '2025-01-01', '2025-01-31', 'OPEN', 2025, 1, '00000000-0000-0000-0000-000000000000'),
    (gen_random_uuid(), 'February 2025', '2025-02-01', '2025-02-28', 'OPEN', 2025, 2, '00000000-0000-0000-0000-000000000000'),
    (gen_random_uuid(), 'March 2025', '2025-03-01', '2025-03-31', 'OPEN', 2025, 3, '00000000-0000-0000-0000-000000000000');

-- =============================================================================
-- COMMENTS FOR DOCUMENTATION
-- =============================================================================

COMMENT ON TABLE ledger_entries IS 'Immutable double-entry ledger entries - cannot be modified after creation (enforced by trigger)';
COMMENT ON TABLE ledger_entry_audit_log IS 'Audit trail of all attempted operations on ledger entries, including blocked modifications';
COMMENT ON VIEW v_account_balance_verification IS 'Verification view to detect discrepancies between ledger entries and account balances';

-- =============================================================================
-- END OF INITIAL SCHEMA
-- =============================================================================
