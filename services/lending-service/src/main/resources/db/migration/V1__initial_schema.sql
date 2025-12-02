-- Lending Service Initial Schema
-- Created: 2025-09-27
-- Description: Loan management, applications, and servicing schema

CREATE TABLE IF NOT EXISTS loan_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id VARCHAR(100) UNIQUE NOT NULL,
    borrower_id UUID NOT NULL,
    co_borrower_id UUID,
    loan_type VARCHAR(50) NOT NULL,
    requested_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    purpose VARCHAR(100) NOT NULL,
    requested_term_months INTEGER NOT NULL,
    employment_status VARCHAR(50),
    annual_income DECIMAL(15, 2),
    debt_to_income_ratio DECIMAL(5, 4),
    credit_score INTEGER,
    collateral_type VARCHAR(50),
    collateral_value DECIMAL(15, 2),
    application_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(100),
    decision VARCHAR(20),
    decision_reason TEXT,
    approved_amount DECIMAL(15, 2),
    approved_term_months INTEGER,
    approved_interest_rate DECIMAL(5, 4),
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS loan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id VARCHAR(100) UNIQUE NOT NULL,
    application_id VARCHAR(100),
    borrower_id UUID NOT NULL,
    co_borrower_id UUID,
    lender_id VARCHAR(100),
    loan_type VARCHAR(50) NOT NULL,
    principal_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    interest_rate DECIMAL(5, 4) NOT NULL,
    interest_type VARCHAR(20) NOT NULL,
    term_months INTEGER NOT NULL,
    monthly_payment DECIMAL(15, 2) NOT NULL,
    origination_fee DECIMAL(10, 2) DEFAULT 0,
    outstanding_balance DECIMAL(15, 2) NOT NULL,
    interest_accrued DECIMAL(15, 2) DEFAULT 0,
    total_paid DECIMAL(15, 2) DEFAULT 0,
    next_payment_due_date DATE,
    maturity_date DATE NOT NULL,
    loan_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    disbursed_at TIMESTAMP,
    disbursement_method VARCHAR(50),
    first_payment_date DATE,
    last_payment_date DATE,
    days_past_due INTEGER DEFAULT 0,
    default_date TIMESTAMP,
    charged_off_date TIMESTAMP,
    paid_off_date TIMESTAMP,
    collateral_id UUID,
    risk_rating VARCHAR(20),
    credit_score_at_origination INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_application FOREIGN KEY (application_id) REFERENCES loan_application(application_id)
);

CREATE TABLE IF NOT EXISTS loan_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id VARCHAR(100) UNIQUE NOT NULL,
    loan_id VARCHAR(100) NOT NULL,
    borrower_id UUID NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date DATE,
    payment_amount DECIMAL(15, 2) NOT NULL,
    principal_amount DECIMAL(15, 2) NOT NULL,
    interest_amount DECIMAL(15, 2) NOT NULL,
    late_fee DECIMAL(10, 2) DEFAULT 0,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    transaction_id VARCHAR(100),
    is_autopay BOOLEAN DEFAULT FALSE,
    is_extra_payment BOOLEAN DEFAULT FALSE,
    confirmation_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_payment FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS loan_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id VARCHAR(100) NOT NULL,
    payment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    scheduled_payment DECIMAL(15, 2) NOT NULL,
    principal_amount DECIMAL(15, 2) NOT NULL,
    interest_amount DECIMAL(15, 2) NOT NULL,
    remaining_balance DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    paid_date DATE,
    paid_amount DECIMAL(15, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_schedule FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE,
    CONSTRAINT unique_loan_payment_number UNIQUE (loan_id, payment_number)
);

CREATE TABLE IF NOT EXISTS collateral (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collateral_id VARCHAR(100) UNIQUE NOT NULL,
    loan_id VARCHAR(100),
    collateral_type VARCHAR(50) NOT NULL,
    description TEXT,
    estimated_value DECIMAL(15, 2) NOT NULL,
    appraised_value DECIMAL(15, 2),
    appraised_date DATE,
    appraiser VARCHAR(100),
    lien_position INTEGER DEFAULT 1,
    insurance_policy_number VARCHAR(100),
    insurance_company VARCHAR(100),
    insurance_expiry DATE,
    condition VARCHAR(20),
    location TEXT,
    ownership_documents TEXT[],
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS loan_modification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    modification_id VARCHAR(100) UNIQUE NOT NULL,
    loan_id VARCHAR(100) NOT NULL,
    modification_type VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    requested_by UUID NOT NULL,
    old_interest_rate DECIMAL(5, 4),
    new_interest_rate DECIMAL(5, 4),
    old_term_months INTEGER,
    new_term_months INTEGER,
    old_monthly_payment DECIMAL(15, 2),
    new_monthly_payment DECIMAL(15, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by VARCHAR(100),
    effective_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_modification FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS loan_default_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id VARCHAR(100) NOT NULL,
    default_type VARCHAR(50) NOT NULL,
    default_date DATE NOT NULL,
    days_past_due INTEGER NOT NULL,
    outstanding_amount DECIMAL(15, 2) NOT NULL,
    recovery_action VARCHAR(100),
    collection_stage VARCHAR(50),
    notes TEXT,
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_default FOREIGN KEY (loan_id) REFERENCES loan(loan_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_loan_application_borrower ON loan_application(borrower_id);
CREATE INDEX idx_loan_application_status ON loan_application(application_status);
CREATE INDEX idx_loan_application_submitted ON loan_application(submitted_at DESC);

CREATE INDEX idx_loan_borrower ON loan(borrower_id);
CREATE INDEX idx_loan_status ON loan(loan_status);
CREATE INDEX idx_loan_next_payment ON loan(next_payment_due_date);
CREATE INDEX idx_loan_maturity ON loan(maturity_date);
CREATE INDEX idx_loan_past_due ON loan(days_past_due DESC) WHERE days_past_due > 0;

CREATE INDEX idx_loan_payment_loan ON loan_payment(loan_id);
CREATE INDEX idx_loan_payment_borrower ON loan_payment(borrower_id);
CREATE INDEX idx_loan_payment_date ON loan_payment(payment_date DESC);

CREATE INDEX idx_loan_schedule_loan ON loan_schedule(loan_id);
CREATE INDEX idx_loan_schedule_due_date ON loan_schedule(due_date);
CREATE INDEX idx_loan_schedule_status ON loan_schedule(status);

CREATE INDEX idx_collateral_loan ON collateral(loan_id);
CREATE INDEX idx_collateral_type ON collateral(collateral_type);

CREATE INDEX idx_loan_modification_loan ON loan_modification(loan_id);
CREATE INDEX idx_loan_modification_status ON loan_modification(status);

CREATE INDEX idx_loan_default_loan ON loan_default_event(loan_id);
CREATE INDEX idx_loan_default_date ON loan_default_event(default_date DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_loan_application_updated_at BEFORE UPDATE ON loan_application
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_loan_updated_at BEFORE UPDATE ON loan
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_collateral_updated_at BEFORE UPDATE ON collateral
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();