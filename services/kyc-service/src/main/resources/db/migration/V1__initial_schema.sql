-- KYC Service Initial Schema
-- Created: 2025-09-27
-- Description: Know Your Customer verification and compliance schema

CREATE TABLE IF NOT EXISTS kyc_verification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    verification_type VARCHAR(50) NOT NULL,
    verification_level VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    risk_score DECIMAL(5, 4),
    submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_date TIMESTAMP,
    approved_date TIMESTAMP,
    rejected_date TIMESTAMP,
    rejection_reason TEXT,
    expiry_date DATE,
    renewal_required BOOLEAN DEFAULT FALSE,
    renewal_due_date DATE,
    reviewer_id VARCHAR(100),
    reviewer_notes TEXT,
    automated_checks_passed BOOLEAN,
    manual_review_required BOOLEAN DEFAULT FALSE,
    pep_check_status VARCHAR(20),
    sanctions_check_status VARCHAR(20),
    adverse_media_check_status VARCHAR(20),
    document_count INTEGER DEFAULT 0,
    verification_method VARCHAR(50),
    ip_address VARCHAR(50),
    user_agent TEXT,
    geolocation VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kyc_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id VARCHAR(100) UNIQUE NOT NULL,
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_subtype VARCHAR(50),
    document_number_encrypted VARCHAR(255),
    document_number_hash VARCHAR(128),
    issuing_country VARCHAR(3) NOT NULL,
    issuing_authority VARCHAR(255),
    issue_date DATE,
    expiry_date DATE,
    is_expired BOOLEAN DEFAULT FALSE,
    file_name VARCHAR(255),
    file_url TEXT NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMP,
    verified_by VARCHAR(100),
    verification_method VARCHAR(50),
    ocr_data JSONB,
    extracted_fields JSONB,
    quality_score DECIMAL(5, 4),
    authenticity_score DECIMAL(5, 4),
    tampering_detected BOOLEAN DEFAULT FALSE,
    rejection_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_document FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_identity_check (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id VARCHAR(100) UNIQUE NOT NULL,
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    check_provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result VARCHAR(20),
    confidence_score DECIMAL(5, 4),
    match_score DECIMAL(5, 4),
    check_data JSONB,
    response_data JSONB,
    warnings TEXT[],
    is_passed BOOLEAN,
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    cost_amount DECIMAL(10, 2),
    cost_currency VARCHAR(3) DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_identity_check FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_address_verification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address_verification_id VARCHAR(100) UNIQUE NOT NULL,
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50),
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    address_type VARCHAR(20) NOT NULL,
    verification_method VARCHAR(50) NOT NULL,
    verification_provider VARCHAR(50),
    provider_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    match_score DECIMAL(5, 4),
    standardized_address JSONB,
    verification_document_id VARCHAR(100),
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_address FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_pep_screening (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id VARCHAR(100) UNIQUE NOT NULL,
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    screening_provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(100),
    screening_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    is_pep BOOLEAN DEFAULT FALSE,
    pep_level VARCHAR(20),
    matches JSONB,
    match_count INTEGER DEFAULT 0,
    risk_rating VARCHAR(20),
    requires_enhanced_due_diligence BOOLEAN DEFAULT FALSE,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_decision VARCHAR(20),
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_pep FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_sanctions_screening (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id VARCHAR(100) UNIQUE NOT NULL,
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    screening_provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(100),
    screening_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    sanctions_lists_checked TEXT[],
    is_match BOOLEAN DEFAULT FALSE,
    matches JSONB,
    match_count INTEGER DEFAULT 0,
    risk_level VARCHAR(20),
    requires_review BOOLEAN DEFAULT FALSE,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_decision VARCHAR(20),
    review_notes TEXT,
    false_positive BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_sanctions FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_adverse_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id VARCHAR(100) UNIQUE NOT NULL,
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    screening_provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(100),
    screening_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    is_match BOOLEAN DEFAULT FALSE,
    matches JSONB,
    match_count INTEGER DEFAULT 0,
    categories TEXT[],
    severity VARCHAR(20),
    requires_review BOOLEAN DEFAULT FALSE,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_decision VARCHAR(20),
    review_notes TEXT,
    false_positive BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_adverse_media FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_enhanced_due_diligence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    edd_id VARCHAR(100) UNIQUE NOT NULL,
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    reason TEXT NOT NULL,
    required_actions TEXT[],
    assigned_to VARCHAR(100),
    assigned_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    findings TEXT,
    source_of_funds_verified BOOLEAN DEFAULT FALSE,
    source_of_wealth_verified BOOLEAN DEFAULT FALSE,
    business_activities_verified BOOLEAN DEFAULT FALSE,
    beneficial_owners_identified BOOLEAN DEFAULT FALSE,
    additional_documents_collected TEXT[],
    interviews_conducted INTEGER DEFAULT 0,
    risk_mitigation_measures TEXT[],
    completed_by VARCHAR(100),
    completed_at TIMESTAMP,
    approval_status VARCHAR(20),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_edd FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent TEXT,
    old_values JSONB,
    new_values JSONB,
    reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_kyc_audit FOREIGN KEY (verification_id) REFERENCES kyc_verification(verification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kyc_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_verifications INTEGER NOT NULL DEFAULT 0,
    approved_count INTEGER DEFAULT 0,
    rejected_count INTEGER DEFAULT 0,
    pending_count INTEGER DEFAULT 0,
    manual_review_count INTEGER DEFAULT 0,
    pep_matches INTEGER DEFAULT 0,
    sanctions_matches INTEGER DEFAULT 0,
    adverse_media_matches INTEGER DEFAULT 0,
    edd_required_count INTEGER DEFAULT 0,
    avg_processing_time_hours DECIMAL(10, 2),
    by_verification_level JSONB,
    by_country JSONB,
    by_rejection_reason JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_kyc_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_kyc_verification_customer ON kyc_verification(customer_id);
CREATE INDEX idx_kyc_verification_status ON kyc_verification(status);
CREATE INDEX idx_kyc_verification_level ON kyc_verification(verification_level);
CREATE INDEX idx_kyc_verification_risk ON kyc_verification(risk_level);
CREATE INDEX idx_kyc_verification_submission ON kyc_verification(submission_date DESC);
CREATE INDEX idx_kyc_verification_expiry ON kyc_verification(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_kyc_verification_pending ON kyc_verification(status) WHERE status = 'PENDING';

CREATE INDEX idx_kyc_document_verification ON kyc_document(verification_id);
CREATE INDEX idx_kyc_document_customer ON kyc_document(customer_id);
CREATE INDEX idx_kyc_document_type ON kyc_document(document_type);
CREATE INDEX idx_kyc_document_status ON kyc_document(verification_status);
CREATE INDEX idx_kyc_document_expiry ON kyc_document(expiry_date) WHERE is_expired = false;
CREATE INDEX idx_kyc_document_uploaded ON kyc_document(uploaded_at DESC);

CREATE INDEX idx_kyc_identity_check_verification ON kyc_identity_check(verification_id);
CREATE INDEX idx_kyc_identity_check_customer ON kyc_identity_check(customer_id);
CREATE INDEX idx_kyc_identity_check_type ON kyc_identity_check(check_type);
CREATE INDEX idx_kyc_identity_check_provider ON kyc_identity_check(check_provider);
CREATE INDEX idx_kyc_identity_check_status ON kyc_identity_check(status);

CREATE INDEX idx_kyc_address_verification ON kyc_address_verification(verification_id);
CREATE INDEX idx_kyc_address_customer ON kyc_address_verification(customer_id);
CREATE INDEX idx_kyc_address_status ON kyc_address_verification(status);
CREATE INDEX idx_kyc_address_verified ON kyc_address_verification(is_verified);

CREATE INDEX idx_kyc_pep_verification ON kyc_pep_screening(verification_id);
CREATE INDEX idx_kyc_pep_customer ON kyc_pep_screening(customer_id);
CREATE INDEX idx_kyc_pep_status ON kyc_pep_screening(status);
CREATE INDEX idx_kyc_pep_match ON kyc_pep_screening(is_pep) WHERE is_pep = true;
CREATE INDEX idx_kyc_pep_date ON kyc_pep_screening(screening_date DESC);

CREATE INDEX idx_kyc_sanctions_verification ON kyc_sanctions_screening(verification_id);
CREATE INDEX idx_kyc_sanctions_customer ON kyc_sanctions_screening(customer_id);
CREATE INDEX idx_kyc_sanctions_status ON kyc_sanctions_screening(status);
CREATE INDEX idx_kyc_sanctions_match ON kyc_sanctions_screening(is_match) WHERE is_match = true;
CREATE INDEX idx_kyc_sanctions_date ON kyc_sanctions_screening(screening_date DESC);

CREATE INDEX idx_kyc_adverse_media_verification ON kyc_adverse_media(verification_id);
CREATE INDEX idx_kyc_adverse_media_customer ON kyc_adverse_media(customer_id);
CREATE INDEX idx_kyc_adverse_media_match ON kyc_adverse_media(is_match) WHERE is_match = true;

CREATE INDEX idx_kyc_edd_verification ON kyc_enhanced_due_diligence(verification_id);
CREATE INDEX idx_kyc_edd_customer ON kyc_enhanced_due_diligence(customer_id);
CREATE INDEX idx_kyc_edd_status ON kyc_enhanced_due_diligence(status);
CREATE INDEX idx_kyc_edd_assigned ON kyc_enhanced_due_diligence(assigned_to);

CREATE INDEX idx_kyc_audit_verification ON kyc_audit_log(verification_id);
CREATE INDEX idx_kyc_audit_customer ON kyc_audit_log(customer_id);
CREATE INDEX idx_kyc_audit_action ON kyc_audit_log(action_type);
CREATE INDEX idx_kyc_audit_performed ON kyc_audit_log(performed_at DESC);

CREATE INDEX idx_kyc_stats_period ON kyc_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_kyc_verification_updated_at BEFORE UPDATE ON kyc_verification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kyc_document_updated_at BEFORE UPDATE ON kyc_document
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kyc_address_updated_at BEFORE UPDATE ON kyc_address_verification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kyc_edd_updated_at BEFORE UPDATE ON kyc_enhanced_due_diligence
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();