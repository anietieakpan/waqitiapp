-- Migration V6: Create Traditional Loan Management Tables
-- Extends BNPL service to support traditional loan products

-- Create loan_applications table
CREATE TABLE loan_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    loan_number VARCHAR(50) UNIQUE NOT NULL,
    loan_type VARCHAR(50) NOT NULL CHECK (loan_type IN (
        'PERSONAL_LOAN', 'BUSINESS_LOAN', 'EDUCATION_LOAN', 'HOME_LOAN',
        'AUTO_LOAN', 'AGRICULTURE_LOAN', 'MICROFINANCE', 'EMERGENCY_LOAN',
        'PAYDAY_LOAN', 'CONSOLIDATION_LOAN'
    )),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'ACTIVE',
        'OVERDUE', 'DEFAULTED', 'COMPLETED', 'CANCELLED', 'WRITTEN_OFF'
    )),
    requested_amount DECIMAL(15,2) NOT NULL,
    approved_amount DECIMAL(15,2),
    disbursed_amount DECIMAL(15,2),
    outstanding_balance DECIMAL(15,2) DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    interest_rate DECIMAL(5,4) NOT NULL,
    interest_type VARCHAR(20) NOT NULL DEFAULT 'REDUCING_BALANCE' CHECK (interest_type IN (
        'SIMPLE', 'COMPOUND', 'FLAT_RATE', 'REDUCING_BALANCE', 'FIXED', 'VARIABLE'
    )),
    loan_term_months INTEGER NOT NULL,
    repayment_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY' CHECK (repayment_frequency IN (
        'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'SEMI_ANNUALLY', 'ANNUALLY'
    )),
    monthly_payment DECIMAL(15,2),
    total_interest DECIMAL(15,2),
    total_repayment DECIMAL(15,2),
    application_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_date TIMESTAMP,
    disbursement_date TIMESTAMP,
    first_payment_date DATE,
    maturity_date DATE,
    credit_score INTEGER,
    debt_to_income_ratio DECIMAL(5,4),
    annual_income DECIMAL(15,2),
    employment_status VARCHAR(50),
    employment_duration_months INTEGER,
    purpose TEXT,
    collateral JSONB,
    documents JSONB,
    risk_assessment JSONB,
    risk_grade VARCHAR(10),
    decision VARCHAR(20),
    decision_reason TEXT,
    decision_date TIMESTAMP,
    decision_by VARCHAR(100),
    loan_officer_id UUID,
    branch_id UUID,
    product_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create loan_installments table
CREATE TABLE loan_installments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_amount DECIMAL(15,2) NOT NULL,
    interest_amount DECIMAL(15,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    paid_amount DECIMAL(15,2) DEFAULT 0,
    outstanding_amount DECIMAL(15,2),
    penalty_amount DECIMAL(15,2) DEFAULT 0,
    late_fee DECIMAL(15,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'DUE', 'OVERDUE', 'PARTIALLY_PAID', 'PAID', 'WAIVED', 'WRITTEN_OFF'
    )),
    payment_date TIMESTAMP,
    payment_method VARCHAR(50),
    payment_reference VARCHAR(100),
    days_overdue INTEGER DEFAULT 0,
    grace_period_days INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(loan_application_id, installment_number)
);

-- Create loan_transactions table
CREATE TABLE loan_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_application_id UUID NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    transaction_reference VARCHAR(100) UNIQUE NOT NULL,
    transaction_type VARCHAR(30) NOT NULL CHECK (transaction_type IN (
        'DISBURSEMENT', 'REPAYMENT', 'PREPAYMENT', 'PENALTY', 'FEE',
        'INTEREST_ACCRUAL', 'WRITE_OFF', 'REVERSAL', 'ADJUSTMENT_CREDIT',
        'ADJUSTMENT_DEBIT', 'PARTIAL_PREPAYMENT', 'FULL_PREPAYMENT',
        'INTEREST_WAIVER', 'FEE_WAIVER'
    )),
    amount DECIMAL(15,2) NOT NULL,
    principal_amount DECIMAL(15,2) DEFAULT 0,
    interest_amount DECIMAL(15,2) DEFAULT 0,
    fee_amount DECIMAL(15,2) DEFAULT 0,
    penalty_amount DECIMAL(15,2) DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED', 'ON_HOLD'
    )),
    payment_method VARCHAR(50),
    payment_channel VARCHAR(50),
    external_reference VARCHAR(100),
    bank_reference VARCHAR(100),
    description TEXT,
    notes TEXT,
    metadata JSONB,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    value_date TIMESTAMP,
    processed_date TIMESTAMP,
    processed_by UUID,
    balance_before DECIMAL(15,2),
    balance_after DECIMAL(15,2),
    installment_id UUID,
    reversal_reference VARCHAR(100),
    is_reversed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_loan_applications_user_id ON loan_applications(user_id);
CREATE INDEX idx_loan_applications_status ON loan_applications(status);
CREATE INDEX idx_loan_applications_loan_type ON loan_applications(loan_type);
CREATE INDEX idx_loan_applications_application_date ON loan_applications(application_date);
CREATE INDEX idx_loan_applications_loan_number ON loan_applications(loan_number);
CREATE INDEX idx_loan_applications_risk_grade ON loan_applications(risk_grade);

CREATE INDEX idx_loan_installments_loan_id ON loan_installments(loan_application_id);
CREATE INDEX idx_loan_installments_due_date ON loan_installments(due_date);
CREATE INDEX idx_loan_installments_status ON loan_installments(status);
CREATE INDEX idx_loan_installments_payment_date ON loan_installments(payment_date);

CREATE INDEX idx_loan_transactions_loan_id ON loan_transactions(loan_application_id);
CREATE INDEX idx_loan_transactions_reference ON loan_transactions(transaction_reference);
CREATE INDEX idx_loan_transactions_type ON loan_transactions(transaction_type);
CREATE INDEX idx_loan_transactions_status ON loan_transactions(status);
CREATE INDEX idx_loan_transactions_date ON loan_transactions(transaction_date);
CREATE INDEX idx_loan_transactions_external_ref ON loan_transactions(external_reference);

-- Create function to update updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_loan_applications_updated_at BEFORE UPDATE ON loan_applications 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loan_installments_updated_at BEFORE UPDATE ON loan_installments 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loan_transactions_updated_at BEFORE UPDATE ON loan_transactions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert sample loan products configuration (can be used for product setup)
CREATE TABLE loan_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_name VARCHAR(100) NOT NULL,
    product_code VARCHAR(20) UNIQUE NOT NULL,
    loan_type VARCHAR(50) NOT NULL,
    min_amount DECIMAL(15,2) NOT NULL,
    max_amount DECIMAL(15,2) NOT NULL,
    min_term_months INTEGER NOT NULL,
    max_term_months INTEGER NOT NULL,
    interest_rate_min DECIMAL(5,4) NOT NULL,
    interest_rate_max DECIMAL(5,4) NOT NULL,
    interest_type VARCHAR(20) NOT NULL DEFAULT 'REDUCING_BALANCE',
    repayment_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    requires_collateral BOOLEAN DEFAULT FALSE,
    processing_fee_rate DECIMAL(5,4) DEFAULT 0,
    late_payment_penalty_rate DECIMAL(5,4) DEFAULT 0,
    grace_period_days INTEGER DEFAULT 0,
    eligibility_criteria JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default loan products
INSERT INTO loan_products (product_name, product_code, loan_type, min_amount, max_amount, min_term_months, max_term_months, interest_rate_min, interest_rate_max) VALUES
('Personal Loan Standard', 'PL001', 'PERSONAL_LOAN', 1000, 50000, 6, 60, 0.12, 0.24),
('Business Loan SME', 'BL001', 'BUSINESS_LOAN', 5000, 500000, 12, 120, 0.10, 0.20),
('Education Loan', 'EL001', 'EDUCATION_LOAN', 2000, 100000, 24, 120, 0.08, 0.15),
('Emergency Loan', 'EM001', 'EMERGENCY_LOAN', 500, 10000, 3, 24, 0.15, 0.30),
('Auto Loan', 'AL001', 'AUTO_LOAN', 5000, 200000, 12, 84, 0.09, 0.18);

-- Add comments for documentation
COMMENT ON TABLE loan_applications IS 'Traditional loan applications supporting various loan products';
COMMENT ON TABLE loan_installments IS 'Repayment schedule and payment tracking for loans';
COMMENT ON TABLE loan_transactions IS 'All financial transactions related to loans (disbursements, payments, etc.)';
COMMENT ON TABLE loan_products IS 'Loan product definitions and configurations';

COMMENT ON COLUMN loan_applications.loan_type IS 'Type of loan product';
COMMENT ON COLUMN loan_applications.interest_type IS 'Method of interest calculation';
COMMENT ON COLUMN loan_applications.outstanding_balance IS 'Current outstanding principal balance';
COMMENT ON COLUMN loan_applications.debt_to_income_ratio IS 'Borrower debt-to-income ratio for risk assessment';

COMMENT ON COLUMN loan_transactions.transaction_type IS 'Type of financial transaction';
COMMENT ON COLUMN loan_transactions.principal_amount IS 'Principal component of payment';
COMMENT ON COLUMN loan_transactions.interest_amount IS 'Interest component of payment';
COMMENT ON COLUMN loan_transactions.balance_before IS 'Loan balance before transaction';
COMMENT ON COLUMN loan_transactions.balance_after IS 'Loan balance after transaction';