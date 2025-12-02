-- Insurance Service Initial Schema
-- Created: 2025-09-27
-- Description: Insurance policy and claims management schema

CREATE TABLE IF NOT EXISTS insurance_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_number VARCHAR(100) UNIQUE NOT NULL,
    policy_holder_id UUID NOT NULL,
    policy_type VARCHAR(50) NOT NULL,
    coverage_type VARCHAR(50) NOT NULL,
    policy_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    coverage_amount DECIMAL(15, 2) NOT NULL,
    premium_amount DECIMAL(10, 2) NOT NULL,
    premium_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    deductible_amount DECIMAL(10, 2) DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    renewal_date DATE,
    is_auto_renew BOOLEAN DEFAULT TRUE,
    payment_method VARCHAR(50),
    beneficiaries JSONB,
    underwriter VARCHAR(100),
    agent_id VARCHAR(50),
    risk_class VARCHAR(20),
    exclusions TEXT[],
    riders JSONB,
    documents TEXT[],
    last_premium_paid_date DATE,
    next_premium_due_date DATE,
    total_premiums_paid DECIMAL(15, 2) DEFAULT 0,
    claims_count INTEGER DEFAULT 0,
    total_claims_paid DECIMAL(15, 2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT
);

CREATE TABLE IF NOT EXISTS insurance_claim (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_number VARCHAR(100) UNIQUE NOT NULL,
    policy_number VARCHAR(100) NOT NULL,
    policy_holder_id UUID NOT NULL,
    claim_type VARCHAR(50) NOT NULL,
    incident_date DATE NOT NULL,
    reported_date DATE NOT NULL DEFAULT CURRENT_DATE,
    claim_amount DECIMAL(15, 2) NOT NULL,
    approved_amount DECIMAL(15, 2),
    deductible_applied DECIMAL(10, 2),
    claim_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    priority VARCHAR(20) DEFAULT 'NORMAL',
    incident_description TEXT NOT NULL,
    incident_location TEXT,
    police_report_number VARCHAR(100),
    witness_information TEXT,
    supporting_documents TEXT[],
    adjuster_assigned VARCHAR(100),
    adjuster_notes TEXT,
    assessment_date DATE,
    approval_date DATE,
    approved_by VARCHAR(100),
    payment_date DATE,
    payment_method VARCHAR(50),
    payment_reference VARCHAR(100),
    denial_reason TEXT,
    fraud_flags TEXT[],
    fraud_review_required BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP,
    CONSTRAINT fk_insurance_policy FOREIGN KEY (policy_number) REFERENCES insurance_policy(policy_number) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS insurance_premium_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id VARCHAR(100) UNIQUE NOT NULL,
    policy_number VARCHAR(100) NOT NULL,
    policy_holder_id UUID NOT NULL,
    payment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date DATE NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    transaction_id VARCHAR(100),
    is_autopay BOOLEAN DEFAULT FALSE,
    late_fee DECIMAL(10, 2) DEFAULT 0,
    confirmation_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_premium_policy FOREIGN KEY (policy_number) REFERENCES insurance_policy(policy_number) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS insurance_quote (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_number VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    policy_type VARCHAR(50) NOT NULL,
    coverage_type VARCHAR(50) NOT NULL,
    requested_coverage_amount DECIMAL(15, 2) NOT NULL,
    quoted_premium DECIMAL(10, 2),
    premium_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    risk_factors JSONB,
    quote_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    valid_until TIMESTAMP,
    converted_to_policy_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS insurance_beneficiary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_number VARCHAR(100) NOT NULL,
    beneficiary_type VARCHAR(20) NOT NULL,
    relationship VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    contact_info JSONB,
    allocation_percentage DECIMAL(5, 2) NOT NULL,
    is_primary BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_beneficiary_policy FOREIGN KEY (policy_number) REFERENCES insurance_policy(policy_number) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS insurance_underwriting_decision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_number VARCHAR(100),
    quote_number VARCHAR(100),
    applicant_id UUID NOT NULL,
    decision VARCHAR(20) NOT NULL,
    risk_score DECIMAL(5, 4),
    risk_class VARCHAR(20),
    premium_adjustment_factor DECIMAL(5, 4) DEFAULT 1.0000,
    decision_reason TEXT,
    underwriter VARCHAR(100) NOT NULL,
    decision_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    conditions TEXT[],
    exclusions TEXT[],
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS insurance_policy_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_number VARCHAR(100) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_url TEXT NOT NULL,
    file_size_bytes BIGINT,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(100),
    is_signed BOOLEAN DEFAULT FALSE,
    signed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document_policy FOREIGN KEY (policy_number) REFERENCES insurance_policy(policy_number) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_insurance_policy_holder ON insurance_policy(policy_holder_id);
CREATE INDEX idx_insurance_policy_status ON insurance_policy(policy_status);
CREATE INDEX idx_insurance_policy_type ON insurance_policy(policy_type);
CREATE INDEX idx_insurance_policy_renewal ON insurance_policy(renewal_date);
CREATE INDEX idx_insurance_policy_end ON insurance_policy(end_date);

CREATE INDEX idx_insurance_claim_policy ON insurance_claim(policy_number);
CREATE INDEX idx_insurance_claim_holder ON insurance_claim(policy_holder_id);
CREATE INDEX idx_insurance_claim_status ON insurance_claim(claim_status);
CREATE INDEX idx_insurance_claim_reported ON insurance_claim(reported_date DESC);
CREATE INDEX idx_insurance_claim_fraud ON insurance_claim(fraud_review_required) WHERE fraud_review_required = true;

CREATE INDEX idx_premium_payment_policy ON insurance_premium_payment(policy_number);
CREATE INDEX idx_premium_payment_holder ON insurance_premium_payment(policy_holder_id);
CREATE INDEX idx_premium_payment_due ON insurance_premium_payment(due_date);

CREATE INDEX idx_insurance_quote_user ON insurance_quote(user_id);
CREATE INDEX idx_insurance_quote_status ON insurance_quote(quote_status);
CREATE INDEX idx_insurance_quote_valid ON insurance_quote(valid_until);

CREATE INDEX idx_beneficiary_policy ON insurance_beneficiary(policy_number);

CREATE INDEX idx_underwriting_policy ON insurance_underwriting_decision(policy_number);
CREATE INDEX idx_underwriting_quote ON insurance_underwriting_decision(quote_number);

CREATE INDEX idx_policy_document_policy ON insurance_policy_document(policy_number);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_insurance_policy_updated_at BEFORE UPDATE ON insurance_policy
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_insurance_claim_updated_at BEFORE UPDATE ON insurance_claim
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_insurance_quote_updated_at BEFORE UPDATE ON insurance_quote
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_beneficiary_updated_at BEFORE UPDATE ON insurance_beneficiary
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();