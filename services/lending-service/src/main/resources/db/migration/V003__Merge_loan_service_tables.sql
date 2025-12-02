-- Merged from loan-service on 2025-11-08
-- Adds loan product catalog and enhanced servicing tables from loan-service

-- Loan Product Catalog (from loan-service)
CREATE TABLE IF NOT EXISTS loan_product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(100) UNIQUE NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_type VARCHAR(50) NOT NULL,
    product_category VARCHAR(50) NOT NULL,
    description TEXT,
    min_loan_amount DECIMAL(15, 2) NOT NULL,
    max_loan_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    min_term_months INTEGER NOT NULL,
    max_term_months INTEGER NOT NULL,
    interest_rate_type VARCHAR(20) NOT NULL,
    base_interest_rate DECIMAL(5, 4) NOT NULL,
    min_interest_rate DECIMAL(5, 4) NOT NULL,
    max_interest_rate DECIMAL(5, 4) NOT NULL,
    interest_calculation_method VARCHAR(50) NOT NULL,
    payment_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    requires_collateral BOOLEAN DEFAULT FALSE,
    min_credit_score INTEGER,
    origination_fee_percentage DECIMAL(5, 4) DEFAULT 0,
    prepayment_penalty DECIMAL(5, 4) DEFAULT 0,
    late_payment_fee DECIMAL(10, 2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Loan Restructuring (from loan-service)
CREATE TABLE IF NOT EXISTS loan_restructure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    restructure_id VARCHAR(100) UNIQUE NOT NULL,
    loan_id VARCHAR(100) NOT NULL,
    restructure_type VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_principal DECIMAL(15, 2),
    new_principal DECIMAL(15, 2),
    old_interest_rate DECIMAL(5, 4),
    new_interest_rate DECIMAL(5, 4),
    old_term_months INTEGER,
    new_term_months INTEGER,
    old_monthly_payment DECIMAL(15, 2),
    new_monthly_payment DECIMAL(15, 2),
    restructure_fee DECIMAL(10, 2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    effective_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_restructure FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE
);

-- Loan Delinquency Tracking (from loan-service)
CREATE TABLE IF NOT EXISTS loan_delinquency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delinquency_id VARCHAR(100) UNIQUE NOT NULL,
    loan_id VARCHAR(100) NOT NULL,
    delinquency_status VARCHAR(50) NOT NULL,
    days_past_due INTEGER NOT NULL,
    amount_past_due DECIMAL(15, 2) NOT NULL,
    missed_payments_count INTEGER DEFAULT 0,
    last_payment_date DATE,
    next_expected_payment_date DATE,
    collection_stage VARCHAR(50),
    collection_agency VARCHAR(255),
    assigned_to VARCHAR(100),
    contact_attempts INTEGER DEFAULT 0,
    last_contact_date TIMESTAMP,
    promise_to_pay_date DATE,
    promise_to_pay_amount DECIMAL(15, 2),
    legal_action_initiated BOOLEAN DEFAULT FALSE,
    legal_action_date TIMESTAMP,
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolution_type VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_delinquency FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE
);

-- Loan Disbursement Tracking (from loan-service)
CREATE TABLE IF NOT EXISTS loan_disbursement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disbursement_id VARCHAR(100) UNIQUE NOT NULL,
    loan_id VARCHAR(100) NOT NULL,
    disbursement_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    disbursement_method VARCHAR(50) NOT NULL,
    destination_account_id VARCHAR(100) NOT NULL,
    destination_account_name VARCHAR(255),
    destination_bank VARCHAR(255),
    scheduled_date DATE NOT NULL,
    disbursed_at TIMESTAMP,
    transaction_reference VARCHAR(100),
    disbursement_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    origination_fee_deducted DECIMAL(10, 2) DEFAULT 0,
    net_disbursement_amount DECIMAL(15, 2) NOT NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_disbursement FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE
);

-- Loan Statement Generation (from loan-service)
CREATE TABLE IF NOT EXISTS loan_statement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id VARCHAR(100) UNIQUE NOT NULL,
    loan_id VARCHAR(100) NOT NULL,
    borrower_id UUID NOT NULL,
    statement_period_start DATE NOT NULL,
    statement_period_end DATE NOT NULL,
    opening_balance DECIMAL(15, 2) NOT NULL,
    closing_balance DECIMAL(15, 2) NOT NULL,
    principal_paid DECIMAL(15, 2) DEFAULT 0,
    interest_paid DECIMAL(15, 2) DEFAULT 0,
    fees_paid DECIMAL(15, 2) DEFAULT 0,
    late_fees_charged DECIMAL(15, 2) DEFAULT 0,
    payments_count INTEGER DEFAULT 0,
    next_payment_due_date DATE,
    next_payment_amount DECIMAL(15, 2),
    statement_generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    statement_url TEXT,
    is_sent BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_statement FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE
);

-- Indexes for new tables
CREATE INDEX idx_loan_product_type ON loan_product(product_type);
CREATE INDEX idx_loan_product_category ON loan_product(product_category);
CREATE INDEX idx_loan_product_active ON loan_product(is_active) WHERE is_active = true;

CREATE INDEX idx_loan_restructure_loan ON loan_restructure(loan_id);
CREATE INDEX idx_loan_restructure_status ON loan_restructure(status);
CREATE INDEX idx_loan_restructure_requested ON loan_restructure(requested_at DESC);

CREATE INDEX idx_loan_delinquency_loan ON loan_delinquency(loan_id);
CREATE INDEX idx_loan_delinquency_status ON loan_delinquency(delinquency_status);
CREATE INDEX idx_loan_delinquency_days_past_due ON loan_delinquency(days_past_due DESC);
CREATE INDEX idx_loan_delinquency_unresolved ON loan_delinquency(is_resolved) WHERE is_resolved = false;

CREATE INDEX idx_loan_disbursement_loan ON loan_disbursement(loan_id);
CREATE INDEX idx_loan_disbursement_status ON loan_disbursement(disbursement_status);
CREATE INDEX idx_loan_disbursement_scheduled ON loan_disbursement(scheduled_date);

CREATE INDEX idx_loan_statement_loan ON loan_statement(loan_id);
CREATE INDEX idx_loan_statement_borrower ON loan_statement(borrower_id);
CREATE INDEX idx_loan_statement_period ON loan_statement(statement_period_end DESC);

-- Update triggers for new tables
CREATE TRIGGER update_loan_product_updated_at BEFORE UPDATE ON loan_product
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loan_restructure_updated_at BEFORE UPDATE ON loan_restructure
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loan_delinquency_updated_at BEFORE UPDATE ON loan_delinquency
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loan_disbursement_updated_at BEFORE UPDATE ON loan_disbursement
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
