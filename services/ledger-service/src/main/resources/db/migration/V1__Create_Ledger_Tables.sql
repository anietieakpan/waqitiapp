-- Ledger Service Database Schema
-- Creates tables for double-entry bookkeeping ledger system

-- Chart of accounts
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

-- Ledger entries table
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id VARCHAR(50) UNIQUE NOT NULL,
    transaction_id VARCHAR(50) NOT NULL,
    account_id VARCHAR(50) NOT NULL,
    chart_account_id UUID NOT NULL REFERENCES chart_of_accounts(id),
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    balance_after DECIMAL(19,4) NOT NULL,
    description TEXT,
    reference_number VARCHAR(100),
    posting_date DATE NOT NULL,
    value_date DATE NOT NULL,
    journal_id UUID,
    reconciliation_status VARCHAR(20) NOT NULL CHECK (reconciliation_status IN ('PENDING', 'RECONCILED', 'DISPUTED')) DEFAULT 'PENDING',
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

-- Journal entries for grouping related ledger entries
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

-- Account balances summary
CREATE TABLE account_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(50) UNIQUE NOT NULL,
    chart_account_id UUID NOT NULL REFERENCES chart_of_accounts(id),
    current_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    pending_debits DECIMAL(19,4) NOT NULL DEFAULT 0,
    pending_credits DECIMAL(19,4) NOT NULL DEFAULT 0,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    last_entry_id UUID REFERENCES ledger_entries(id),
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

-- Reconciliation records
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

-- Audit trail for ledger changes
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

-- Indexes for performance
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_chart_account ON ledger_entries(chart_account_id);
CREATE INDEX idx_ledger_entries_posting_date ON ledger_entries(posting_date);
CREATE INDEX idx_ledger_entries_journal_id ON ledger_entries(journal_id);
CREATE INDEX idx_ledger_entries_entry_type ON ledger_entries(entry_type);

CREATE INDEX idx_journal_entries_transaction_id ON journal_entries(transaction_id);
CREATE INDEX idx_journal_entries_posting_date ON journal_entries(posting_date);
CREATE INDEX idx_journal_entries_status ON journal_entries(status);
CREATE INDEX idx_journal_entries_type ON journal_entries(journal_type);

CREATE INDEX idx_account_balances_account_id ON account_balances(account_id);
CREATE INDEX idx_account_balances_chart_account ON account_balances(chart_account_id);

CREATE INDEX idx_reconciliations_account_id ON reconciliations(account_id);
CREATE INDEX idx_reconciliations_date ON reconciliations(reconciliation_date);
CREATE INDEX idx_reconciliations_status ON reconciliations(status);

CREATE INDEX idx_chart_accounts_type ON chart_of_accounts(account_type);
CREATE INDEX idx_chart_accounts_parent ON chart_of_accounts(parent_account_id);
CREATE INDEX idx_chart_accounts_code ON chart_of_accounts(account_code);

CREATE INDEX idx_ledger_audit_table_record ON ledger_audit(table_name, record_id);
CREATE INDEX idx_ledger_audit_created_at ON ledger_audit(created_at);

-- Update journal entries when ledger entries are modified
CREATE OR REPLACE FUNCTION update_journal_totals()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        UPDATE journal_entries 
        SET total_debits = (
            SELECT COALESCE(SUM(amount), 0) 
            FROM ledger_entries 
            WHERE journal_id = NEW.journal_id AND entry_type = 'DEBIT'
        ),
        total_credits = (
            SELECT COALESCE(SUM(amount), 0) 
            FROM ledger_entries 
            WHERE journal_id = NEW.journal_id AND entry_type = 'CREDIT'
        )
        WHERE id = NEW.journal_id;
    END IF;
    
    IF TG_OP = 'DELETE' THEN
        UPDATE journal_entries 
        SET total_debits = (
            SELECT COALESCE(SUM(amount), 0) 
            FROM ledger_entries 
            WHERE journal_id = OLD.journal_id AND entry_type = 'DEBIT'
        ),
        total_credits = (
            SELECT COALESCE(SUM(amount), 0) 
            FROM ledger_entries 
            WHERE journal_id = OLD.journal_id AND entry_type = 'CREDIT'
        )
        WHERE id = OLD.journal_id;
        RETURN OLD;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Update account balances when ledger entries change
CREATE OR REPLACE FUNCTION update_account_balance()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO account_balances (account_id, chart_account_id, current_balance, last_entry_id)
        VALUES (NEW.account_id, NEW.chart_account_id, 
            CASE WHEN NEW.entry_type = 'DEBIT' THEN NEW.amount ELSE -NEW.amount END,
            NEW.id
        )
        ON CONFLICT (account_id) DO UPDATE SET
            current_balance = account_balances.current_balance + 
                CASE WHEN NEW.entry_type = 'DEBIT' THEN NEW.amount ELSE -NEW.amount END,
            last_entry_id = NEW.id,
            last_updated = CURRENT_TIMESTAMP,
            version = account_balances.version + 1;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers
CREATE TRIGGER trigger_update_journal_totals
    AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION update_journal_totals();

CREATE TRIGGER trigger_update_account_balance
    AFTER INSERT ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION update_account_balance();

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