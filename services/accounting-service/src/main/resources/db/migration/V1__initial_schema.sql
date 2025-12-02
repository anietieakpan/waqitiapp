-- Accounting Service Initial Schema
-- Created: 2025-09-27
-- Description: General ledger, journal entries, and financial reporting schema

CREATE TABLE IF NOT EXISTS chart_of_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code VARCHAR(50) UNIQUE NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    account_category VARCHAR(50) NOT NULL,
    parent_account_code VARCHAR(50),
    account_level INTEGER NOT NULL DEFAULT 1,
    is_leaf BOOLEAN DEFAULT TRUE,
    normal_balance VARCHAR(10) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    is_active BOOLEAN DEFAULT TRUE,
    is_system_account BOOLEAN DEFAULT FALSE,
    allow_manual_entries BOOLEAN DEFAULT TRUE,
    reconciliation_required BOOLEAN DEFAULT FALSE,
    description TEXT,
    tax_relevant BOOLEAN DEFAULT FALSE,
    tax_category VARCHAR(50),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at TIMESTAMP,
    CONSTRAINT fk_parent_account FOREIGN KEY (parent_account_code) REFERENCES chart_of_accounts(account_code)
);

CREATE TABLE IF NOT EXISTS journal_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id VARCHAR(100) UNIQUE NOT NULL,
    entry_date DATE NOT NULL,
    posting_date DATE NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    entry_source VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    description TEXT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    total_debit DECIMAL(18, 2) NOT NULL,
    total_credit DECIMAL(18, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    is_balanced BOOLEAN DEFAULT FALSE,
    is_posted BOOLEAN DEFAULT FALSE,
    posted_at TIMESTAMP,
    posted_by VARCHAR(100),
    is_reversed BOOLEAN DEFAULT FALSE,
    reversed_by_entry_id VARCHAR(100),
    reversal_reason TEXT,
    reversed_at TIMESTAMP,
    fiscal_year INTEGER NOT NULL,
    fiscal_period VARCHAR(20) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS journal_entry_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_id VARCHAR(100) UNIQUE NOT NULL,
    entry_id VARCHAR(100) NOT NULL,
    line_number INTEGER NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    debit_amount DECIMAL(18, 2) DEFAULT 0,
    credit_amount DECIMAL(18, 2) DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    exchange_rate DECIMAL(18, 8),
    functional_currency VARCHAR(3),
    functional_debit DECIMAL(18, 2),
    functional_credit DECIMAL(18, 2),
    description TEXT,
    cost_center VARCHAR(50),
    department VARCHAR(50),
    project_code VARCHAR(50),
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_journal_entry FOREIGN KEY (entry_id) REFERENCES journal_entry(entry_id) ON DELETE CASCADE,
    CONSTRAINT fk_account_code FOREIGN KEY (account_code) REFERENCES chart_of_accounts(account_code),
    CONSTRAINT unique_entry_line UNIQUE (entry_id, line_number)
);

CREATE TABLE IF NOT EXISTS general_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ledger_id VARCHAR(100) UNIQUE NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    entry_id VARCHAR(100) NOT NULL,
    line_id VARCHAR(100) NOT NULL,
    transaction_date DATE NOT NULL,
    posting_date DATE NOT NULL,
    fiscal_year INTEGER NOT NULL,
    fiscal_period VARCHAR(20) NOT NULL,
    debit_amount DECIMAL(18, 2) DEFAULT 0,
    credit_amount DECIMAL(18, 2) DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    running_balance DECIMAL(18, 2) NOT NULL,
    description TEXT,
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    cost_center VARCHAR(50),
    department VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gl_account FOREIGN KEY (account_code) REFERENCES chart_of_accounts(account_code),
    CONSTRAINT fk_gl_entry FOREIGN KEY (entry_id) REFERENCES journal_entry(entry_id) ON DELETE CASCADE,
    CONSTRAINT fk_gl_line FOREIGN KEY (line_id) REFERENCES journal_entry_line(line_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS account_balance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code VARCHAR(50) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    fiscal_period VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    opening_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    period_debits DECIMAL(18, 2) NOT NULL DEFAULT 0,
    period_credits DECIMAL(18, 2) NOT NULL DEFAULT 0,
    closing_balance DECIMAL(18, 2) NOT NULL,
    last_transaction_date DATE,
    is_closed BOOLEAN DEFAULT FALSE,
    closed_at TIMESTAMP,
    closed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_balance_account FOREIGN KEY (account_code) REFERENCES chart_of_accounts(account_code),
    CONSTRAINT unique_account_period UNIQUE (account_code, fiscal_year, fiscal_period)
);

CREATE TABLE IF NOT EXISTS fiscal_period (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_year INTEGER NOT NULL,
    period_number INTEGER NOT NULL,
    period_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    is_adjustment_period BOOLEAN DEFAULT FALSE,
    closed_at TIMESTAMP,
    closed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_fiscal_period UNIQUE (fiscal_year, period_number)
);

CREATE TABLE IF NOT EXISTS reconciliation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id VARCHAR(100) UNIQUE NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    reconciliation_date DATE NOT NULL,
    fiscal_year INTEGER NOT NULL,
    fiscal_period VARCHAR(20) NOT NULL,
    bank_statement_balance DECIMAL(18, 2),
    book_balance DECIMAL(18, 2) NOT NULL,
    adjusted_balance DECIMAL(18, 2) NOT NULL,
    difference DECIMAL(18, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    reconciled_by VARCHAR(100),
    reconciled_at TIMESTAMP,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    notes TEXT,
    adjustments JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reconciliation_account FOREIGN KEY (account_code) REFERENCES chart_of_accounts(account_code)
);

CREATE TABLE IF NOT EXISTS budget (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id VARCHAR(100) UNIQUE NOT NULL,
    budget_name VARCHAR(255) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    budget_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INTEGER DEFAULT 1,
    description TEXT,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS budget_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id VARCHAR(100) NOT NULL,
    account_code VARCHAR(50) NOT NULL,
    fiscal_period VARCHAR(20) NOT NULL,
    budgeted_amount DECIMAL(18, 2) NOT NULL,
    actual_amount DECIMAL(18, 2) DEFAULT 0,
    variance DECIMAL(18, 2) DEFAULT 0,
    variance_percentage DECIMAL(5, 4) DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_line_budget FOREIGN KEY (budget_id) REFERENCES budget(budget_id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_line_account FOREIGN KEY (account_code) REFERENCES chart_of_accounts(account_code),
    CONSTRAINT unique_budget_account_period UNIQUE (budget_id, account_code, fiscal_period)
);

CREATE TABLE IF NOT EXISTS financial_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id VARCHAR(100) UNIQUE NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    fiscal_period VARCHAR(20),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    report_data JSONB NOT NULL,
    metrics JSONB,
    format VARCHAR(20) NOT NULL,
    file_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    generated_by VARCHAR(100) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_coa_type ON chart_of_accounts(account_type);
CREATE INDEX idx_coa_category ON chart_of_accounts(account_category);
CREATE INDEX idx_coa_parent ON chart_of_accounts(parent_account_code);
CREATE INDEX idx_coa_active ON chart_of_accounts(is_active) WHERE is_active = true;

CREATE INDEX idx_journal_entry_date ON journal_entry(entry_date DESC);
CREATE INDEX idx_journal_entry_posting ON journal_entry(posting_date DESC);
CREATE INDEX idx_journal_entry_type ON journal_entry(entry_type);
CREATE INDEX idx_journal_entry_source ON journal_entry(entry_source);
CREATE INDEX idx_journal_entry_reference ON journal_entry(reference_type, reference_id);
CREATE INDEX idx_journal_entry_status ON journal_entry(status);
CREATE INDEX idx_journal_entry_fiscal ON journal_entry(fiscal_year, fiscal_period);
CREATE INDEX idx_journal_entry_posted ON journal_entry(is_posted) WHERE is_posted = false;

CREATE INDEX idx_journal_line_entry ON journal_entry_line(entry_id);
CREATE INDEX idx_journal_line_account ON journal_entry_line(account_code);
CREATE INDEX idx_journal_line_entity ON journal_entry_line(entity_type, entity_id);

CREATE INDEX idx_gl_account ON general_ledger(account_code);
CREATE INDEX idx_gl_entry ON general_ledger(entry_id);
CREATE INDEX idx_gl_transaction_date ON general_ledger(transaction_date DESC);
CREATE INDEX idx_gl_posting_date ON general_ledger(posting_date DESC);
CREATE INDEX idx_gl_fiscal ON general_ledger(fiscal_year, fiscal_period);
CREATE INDEX idx_gl_reference ON general_ledger(reference_type, reference_id);

CREATE INDEX idx_account_balance_account ON account_balance(account_code);
CREATE INDEX idx_account_balance_fiscal ON account_balance(fiscal_year, fiscal_period);
CREATE INDEX idx_account_balance_open ON account_balance(is_closed) WHERE is_closed = false;

CREATE INDEX idx_fiscal_period_year ON fiscal_period(fiscal_year);
CREATE INDEX idx_fiscal_period_status ON fiscal_period(status);

CREATE INDEX idx_reconciliation_account ON reconciliation(account_code);
CREATE INDEX idx_reconciliation_date ON reconciliation(reconciliation_date DESC);
CREATE INDEX idx_reconciliation_fiscal ON reconciliation(fiscal_year, fiscal_period);
CREATE INDEX idx_reconciliation_status ON reconciliation(status);

CREATE INDEX idx_budget_year ON budget(fiscal_year);
CREATE INDEX idx_budget_type ON budget(budget_type);
CREATE INDEX idx_budget_status ON budget(status);

CREATE INDEX idx_budget_line_budget ON budget_line(budget_id);
CREATE INDEX idx_budget_line_account ON budget_line(account_code);
CREATE INDEX idx_budget_line_period ON budget_line(fiscal_period);

CREATE INDEX idx_financial_report_type ON financial_report(report_type);
CREATE INDEX idx_financial_report_fiscal ON financial_report(fiscal_year, fiscal_period);
CREATE INDEX idx_financial_report_generated ON financial_report(generated_at DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_coa_updated_at BEFORE UPDATE ON chart_of_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_journal_entry_updated_at BEFORE UPDATE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_account_balance_updated_at BEFORE UPDATE ON account_balance
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reconciliation_updated_at BEFORE UPDATE ON reconciliation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_budget_updated_at BEFORE UPDATE ON budget
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_budget_line_updated_at BEFORE UPDATE ON budget_line
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();