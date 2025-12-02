-- Legal Service Initial Schema
-- Created: 2025-09-27
-- Description: Legal document management, contracts, and compliance schema

CREATE TABLE IF NOT EXISTS legal_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id VARCHAR(100) UNIQUE NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_category VARCHAR(100) NOT NULL,
    document_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version VARCHAR(50) NOT NULL DEFAULT '1.0',
    previous_version_id VARCHAR(100),
    jurisdiction VARCHAR(100) NOT NULL,
    applicable_law VARCHAR(255),
    document_language VARCHAR(10) DEFAULT 'en',
    effective_date DATE,
    expiration_date DATE,
    renewal_date DATE,
    auto_renewal BOOLEAN DEFAULT FALSE,
    file_path VARCHAR(1000) NOT NULL,
    file_size_bytes INTEGER,
    file_format VARCHAR(20) NOT NULL,
    checksum VARCHAR(64),
    encrypted BOOLEAN DEFAULT TRUE,
    encryption_key_id VARCHAR(255),
    original_document_path VARCHAR(1000),
    signed_document_path VARCHAR(1000),
    parties JSONB NOT NULL,
    terms_and_conditions TEXT,
    clauses JSONB,
    obligations JSONB,
    penalties JSONB,
    confidentiality_level VARCHAR(20) DEFAULT 'CONFIDENTIAL',
    retention_years INTEGER DEFAULT 7,
    destruction_date DATE,
    tags TEXT[],
    related_documents TEXT[],
    approval_workflow_id VARCHAR(100),
    requires_approval BOOLEAN DEFAULT TRUE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    rejection_reason TEXT,
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_contract (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id VARCHAR(100) UNIQUE NOT NULL,
    contract_name VARCHAR(255) NOT NULL,
    contract_type VARCHAR(50) NOT NULL,
    contract_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    contract_value DECIMAL(18, 2),
    currency_code VARCHAR(3) DEFAULT 'USD',
    parties JSONB NOT NULL,
    primary_party_id VARCHAR(100) NOT NULL,
    counterparty_id VARCHAR(100) NOT NULL,
    counterparty_name VARCHAR(255) NOT NULL,
    counterparty_type VARCHAR(50) NOT NULL,
    contract_start_date DATE NOT NULL,
    contract_end_date DATE,
    contract_term_months INTEGER,
    renewal_terms JSONB,
    termination_clause TEXT,
    early_termination_penalty DECIMAL(18, 2),
    notice_period_days INTEGER DEFAULT 30,
    payment_terms VARCHAR(100),
    payment_schedule JSONB,
    performance_obligations JSONB,
    deliverables JSONB,
    milestones JSONB,
    service_level_agreements JSONB,
    warranties TEXT,
    indemnification_clause TEXT,
    liability_cap DECIMAL(18, 2),
    dispute_resolution_method VARCHAR(100),
    governing_law VARCHAR(255),
    jurisdiction VARCHAR(100),
    confidentiality_clause TEXT,
    non_compete_clause TEXT,
    intellectual_property_rights TEXT,
    force_majeure_clause TEXT,
    amendment_history JSONB,
    document_ids TEXT[],
    signed BOOLEAN DEFAULT FALSE,
    signatures JSONB,
    executed_date DATE,
    notarized BOOLEAN DEFAULT FALSE,
    notary_details JSONB,
    compliance_status VARCHAR(20) DEFAULT 'PENDING',
    last_review_date DATE,
    next_review_date DATE,
    risk_rating VARCHAR(20),
    risk_factors TEXT[],
    contract_manager VARCHAR(100),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_signature (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signature_id VARCHAR(100) UNIQUE NOT NULL,
    document_id VARCHAR(100),
    contract_id VARCHAR(100),
    signer_id VARCHAR(100) NOT NULL,
    signer_name VARCHAR(255) NOT NULL,
    signer_role VARCHAR(100) NOT NULL,
    signer_email VARCHAR(255) NOT NULL,
    signature_type VARCHAR(50) NOT NULL,
    signature_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    signature_method VARCHAR(50) NOT NULL,
    signature_data VARCHAR(2000),
    digital_signature JSONB,
    certificate_id VARCHAR(100),
    biometric_data JSONB,
    ip_address VARCHAR(50),
    device_info JSONB,
    geolocation JSONB,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reminder_sent_count INTEGER DEFAULT 0,
    last_reminder_at TIMESTAMP,
    signed_at TIMESTAMP,
    declined_at TIMESTAMP,
    decline_reason TEXT,
    verification_code VARCHAR(20),
    verified BOOLEAN DEFAULT FALSE,
    verification_timestamp TIMESTAMP,
    witness_required BOOLEAN DEFAULT FALSE,
    witness_name VARCHAR(255),
    witness_signature VARCHAR(2000),
    notarization_required BOOLEAN DEFAULT FALSE,
    notarized_at TIMESTAMP,
    notary_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_legal_signature_document FOREIGN KEY (document_id) REFERENCES legal_document(document_id) ON DELETE CASCADE,
    CONSTRAINT fk_legal_signature_contract FOREIGN KEY (contract_id) REFERENCES legal_contract(contract_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS legal_compliance_requirement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requirement_id VARCHAR(100) UNIQUE NOT NULL,
    requirement_name VARCHAR(255) NOT NULL,
    requirement_type VARCHAR(50) NOT NULL,
    regulatory_framework VARCHAR(255) NOT NULL,
    jurisdiction VARCHAR(100) NOT NULL,
    requirement_description TEXT NOT NULL,
    compliance_criteria JSONB NOT NULL,
    mandatory BOOLEAN DEFAULT TRUE,
    effective_date DATE NOT NULL,
    revision_date DATE,
    next_review_date DATE,
    related_regulations TEXT[],
    penalties_for_non_compliance TEXT,
    monitoring_frequency VARCHAR(20) DEFAULT 'QUARTERLY',
    responsible_party VARCHAR(100),
    documentation_required TEXT[],
    reporting_requirements JSONB,
    audit_requirements JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_compliance_assessment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id VARCHAR(100) UNIQUE NOT NULL,
    requirement_id VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    assessment_status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    compliance_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assessment_date DATE NOT NULL,
    assessor VARCHAR(100) NOT NULL,
    assessment_method VARCHAR(50) NOT NULL,
    findings TEXT,
    evidence_collected TEXT[],
    compliance_score INTEGER,
    risk_level VARCHAR(20),
    gaps_identified TEXT[],
    corrective_actions JSONB,
    remediation_deadline DATE,
    remediation_status VARCHAR(20),
    follow_up_required BOOLEAN DEFAULT FALSE,
    follow_up_date DATE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    next_assessment_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_legal_compliance_assessment FOREIGN KEY (requirement_id) REFERENCES legal_compliance_requirement(requirement_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS legal_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id VARCHAR(100) UNIQUE NOT NULL,
    case_number VARCHAR(100) UNIQUE NOT NULL,
    case_name VARCHAR(255) NOT NULL,
    case_type VARCHAR(50) NOT NULL,
    case_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    case_priority VARCHAR(20) DEFAULT 'NORMAL',
    filed_date DATE NOT NULL,
    court_name VARCHAR(255),
    jurisdiction VARCHAR(100) NOT NULL,
    judge_name VARCHAR(255),
    plaintiff_name VARCHAR(255) NOT NULL,
    plaintiff_representation VARCHAR(255),
    defendant_name VARCHAR(255) NOT NULL,
    defendant_representation VARCHAR(255),
    case_description TEXT NOT NULL,
    legal_claims TEXT[],
    relief_sought TEXT,
    amount_in_dispute DECIMAL(18, 2),
    currency_code VARCHAR(3) DEFAULT 'USD',
    hearing_dates JSONB,
    next_hearing_date DATE,
    trial_date DATE,
    deadlines JSONB,
    case_milestones JSONB,
    case_documents TEXT[],
    evidence_submitted TEXT[],
    witness_list JSONB,
    expert_witnesses JSONB,
    settlement_discussions BOOLEAN DEFAULT FALSE,
    settlement_amount DECIMAL(18, 2),
    settlement_terms TEXT,
    outcome VARCHAR(100),
    judgment_date DATE,
    judgment_summary TEXT,
    appeal_filed BOOLEAN DEFAULT FALSE,
    appeal_deadline DATE,
    case_manager VARCHAR(100) NOT NULL,
    outside_counsel VARCHAR(255),
    legal_fees_budget DECIMAL(18, 2),
    legal_fees_incurred DECIMAL(18, 2),
    risk_assessment TEXT,
    internal_notes TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_opinion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    opinion_id VARCHAR(100) UNIQUE NOT NULL,
    opinion_title VARCHAR(255) NOT NULL,
    opinion_type VARCHAR(50) NOT NULL,
    matter_description TEXT NOT NULL,
    legal_questions TEXT[],
    jurisdiction VARCHAR(100) NOT NULL,
    applicable_laws TEXT[],
    case_law_references TEXT[],
    statutory_references TEXT[],
    legal_analysis TEXT NOT NULL,
    conclusions TEXT NOT NULL,
    recommendations TEXT[],
    risk_assessment TEXT,
    confidence_level VARCHAR(20),
    limitations TEXT,
    assumptions TEXT,
    author_name VARCHAR(255) NOT NULL,
    author_credentials VARCHAR(500),
    reviewed_by VARCHAR(100),
    approved_by VARCHAR(100),
    opinion_date DATE NOT NULL,
    expiry_date DATE,
    update_required BOOLEAN DEFAULT FALSE,
    confidentiality_level VARCHAR(20) DEFAULT 'CONFIDENTIAL',
    client_id VARCHAR(100),
    related_case_id VARCHAR(100),
    related_contract_id VARCHAR(100),
    document_references TEXT[],
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_obligation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    obligation_id VARCHAR(100) UNIQUE NOT NULL,
    obligation_name VARCHAR(255) NOT NULL,
    obligation_type VARCHAR(50) NOT NULL,
    contract_id VARCHAR(100),
    document_id VARCHAR(100),
    obligation_description TEXT NOT NULL,
    responsible_party VARCHAR(100) NOT NULL,
    counterparty VARCHAR(100),
    obligation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_date DATE NOT NULL,
    completion_date DATE,
    recurring BOOLEAN DEFAULT FALSE,
    recurrence_frequency VARCHAR(20),
    next_due_date DATE,
    deliverables TEXT[],
    acceptance_criteria TEXT,
    penalty_for_breach DECIMAL(18, 2),
    performance_bond_required BOOLEAN DEFAULT FALSE,
    performance_bond_amount DECIMAL(18, 2),
    monitoring_frequency VARCHAR(20),
    last_review_date DATE,
    compliance_status VARCHAR(20) DEFAULT 'PENDING',
    issues_identified TEXT[],
    remediation_actions TEXT[],
    notifications_sent INTEGER DEFAULT 0,
    escalation_level INTEGER DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_legal_obligation_contract FOREIGN KEY (contract_id) REFERENCES legal_contract(contract_id) ON DELETE CASCADE,
    CONSTRAINT fk_legal_obligation_document FOREIGN KEY (document_id) REFERENCES legal_document(document_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS legal_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id VARCHAR(100) UNIQUE NOT NULL,
    audit_name VARCHAR(255) NOT NULL,
    audit_type VARCHAR(50) NOT NULL,
    audit_scope TEXT NOT NULL,
    audit_status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    audit_start_date DATE NOT NULL,
    audit_end_date DATE,
    auditor VARCHAR(100) NOT NULL,
    audit_team JSONB,
    entities_audited TEXT[],
    documents_reviewed TEXT[],
    contracts_reviewed TEXT[],
    findings JSONB,
    recommendations JSONB,
    high_risk_findings INTEGER DEFAULT 0,
    medium_risk_findings INTEGER DEFAULT 0,
    low_risk_findings INTEGER DEFAULT 0,
    compliance_rate DECIMAL(5, 4),
    overall_assessment VARCHAR(20),
    action_items JSONB,
    follow_up_required BOOLEAN DEFAULT FALSE,
    follow_up_date DATE,
    audit_report_path VARCHAR(1000),
    management_response TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(100) NOT NULL,
    recipient_email VARCHAR(255),
    subject VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    notification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    related_entity_type VARCHAR(50),
    related_entity_id VARCHAR(100),
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    action_required BOOLEAN DEFAULT FALSE,
    action_deadline DATE,
    reminder_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_contracts INTEGER DEFAULT 0,
    active_contracts INTEGER DEFAULT 0,
    contracts_signed INTEGER DEFAULT 0,
    contracts_expired INTEGER DEFAULT 0,
    contract_value DECIMAL(18, 2) DEFAULT 0,
    total_cases INTEGER DEFAULT 0,
    open_cases INTEGER DEFAULT 0,
    closed_cases INTEGER DEFAULT 0,
    cases_won INTEGER DEFAULT 0,
    cases_lost INTEGER DEFAULT 0,
    legal_fees_spent DECIMAL(18, 2) DEFAULT 0,
    compliance_assessments INTEGER DEFAULT 0,
    compliance_violations INTEGER DEFAULT 0,
    audits_conducted INTEGER DEFAULT 0,
    by_contract_type JSONB,
    by_case_type JSONB,
    by_jurisdiction JSONB,
    risk_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legal_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_legal_documents INTEGER DEFAULT 0,
    active_contracts INTEGER DEFAULT 0,
    contract_value DECIMAL(18, 2) DEFAULT 0,
    contracts_expiring_soon INTEGER DEFAULT 0,
    open_cases INTEGER DEFAULT 0,
    case_win_rate DECIMAL(5, 4),
    compliance_score DECIMAL(5, 4),
    audit_findings INTEGER DEFAULT 0,
    high_risk_matters INTEGER DEFAULT 0,
    legal_spend DECIMAL(18, 2) DEFAULT 0,
    by_document_type JSONB,
    by_jurisdiction JSONB,
    contract_renewal_rate DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_legal_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_legal_document_type ON legal_document(document_type);
CREATE INDEX idx_legal_document_category ON legal_document(document_category);
CREATE INDEX idx_legal_document_status ON legal_document(document_status);
CREATE INDEX idx_legal_document_jurisdiction ON legal_document(jurisdiction);
CREATE INDEX idx_legal_document_effective ON legal_document(effective_date);

CREATE INDEX idx_legal_contract_type ON legal_contract(contract_type);
CREATE INDEX idx_legal_contract_status ON legal_contract(contract_status);
CREATE INDEX idx_legal_contract_primary_party ON legal_contract(primary_party_id);
CREATE INDEX idx_legal_contract_counterparty ON legal_contract(counterparty_id);
CREATE INDEX idx_legal_contract_start_date ON legal_contract(contract_start_date DESC);
CREATE INDEX idx_legal_contract_end_date ON legal_contract(contract_end_date);

CREATE INDEX idx_legal_signature_document ON legal_signature(document_id);
CREATE INDEX idx_legal_signature_contract ON legal_signature(contract_id);
CREATE INDEX idx_legal_signature_signer ON legal_signature(signer_id);
CREATE INDEX idx_legal_signature_status ON legal_signature(signature_status);

CREATE INDEX idx_legal_compliance_requirement_type ON legal_compliance_requirement(requirement_type);
CREATE INDEX idx_legal_compliance_requirement_framework ON legal_compliance_requirement(regulatory_framework);
CREATE INDEX idx_legal_compliance_requirement_jurisdiction ON legal_compliance_requirement(jurisdiction);
CREATE INDEX idx_legal_compliance_requirement_active ON legal_compliance_requirement(is_active) WHERE is_active = true;

CREATE INDEX idx_legal_compliance_assessment_requirement ON legal_compliance_assessment(requirement_id);
CREATE INDEX idx_legal_compliance_assessment_entity ON legal_compliance_assessment(entity_id);
CREATE INDEX idx_legal_compliance_assessment_status ON legal_compliance_assessment(compliance_status);
CREATE INDEX idx_legal_compliance_assessment_date ON legal_compliance_assessment(assessment_date DESC);

CREATE INDEX idx_legal_case_number ON legal_case(case_number);
CREATE INDEX idx_legal_case_type ON legal_case(case_type);
CREATE INDEX idx_legal_case_status ON legal_case(case_status);
CREATE INDEX idx_legal_case_filed_date ON legal_case(filed_date DESC);
CREATE INDEX idx_legal_case_manager ON legal_case(case_manager);

CREATE INDEX idx_legal_opinion_type ON legal_opinion(opinion_type);
CREATE INDEX idx_legal_opinion_jurisdiction ON legal_opinion(jurisdiction);
CREATE INDEX idx_legal_opinion_date ON legal_opinion(opinion_date DESC);
CREATE INDEX idx_legal_opinion_client ON legal_opinion(client_id);

CREATE INDEX idx_legal_obligation_type ON legal_obligation(obligation_type);
CREATE INDEX idx_legal_obligation_contract ON legal_obligation(contract_id);
CREATE INDEX idx_legal_obligation_status ON legal_obligation(obligation_status);
CREATE INDEX idx_legal_obligation_due_date ON legal_obligation(due_date);
CREATE INDEX idx_legal_obligation_responsible ON legal_obligation(responsible_party);

CREATE INDEX idx_legal_audit_type ON legal_audit(audit_type);
CREATE INDEX idx_legal_audit_status ON legal_audit(audit_status);
CREATE INDEX idx_legal_audit_start_date ON legal_audit(audit_start_date DESC);

CREATE INDEX idx_legal_notification_type ON legal_notification(notification_type);
CREATE INDEX idx_legal_notification_recipient ON legal_notification(recipient);
CREATE INDEX idx_legal_notification_status ON legal_notification(notification_status);

CREATE INDEX idx_legal_analytics_period ON legal_analytics(period_end DESC);
CREATE INDEX idx_legal_statistics_period ON legal_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_legal_document_updated_at BEFORE UPDATE ON legal_document
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_contract_updated_at BEFORE UPDATE ON legal_contract
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_signature_updated_at BEFORE UPDATE ON legal_signature
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_compliance_requirement_updated_at BEFORE UPDATE ON legal_compliance_requirement
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_compliance_assessment_updated_at BEFORE UPDATE ON legal_compliance_assessment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_case_updated_at BEFORE UPDATE ON legal_case
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_opinion_updated_at BEFORE UPDATE ON legal_opinion
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_obligation_updated_at BEFORE UPDATE ON legal_obligation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_legal_audit_updated_at BEFORE UPDATE ON legal_audit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- Additional tables for Subpoena and Bankruptcy
-- ==========================================

CREATE TABLE IF NOT EXISTS legal_subpoena (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subpoena_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    case_number VARCHAR(100) NOT NULL,
    issuing_court VARCHAR(255) NOT NULL,
    issuance_date DATE NOT NULL,
    response_deadline DATE NOT NULL,
    subpoena_type VARCHAR(50) NOT NULL,
    requested_records TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    completed BOOLEAN DEFAULT FALSE,
    customer_notified BOOLEAN DEFAULT FALSE,
    customer_notification_date TIMESTAMP,
    serving_party VARCHAR(255),
    court_jurisdiction VARCHAR(100),
    attorney_name VARCHAR(255),
    attorney_contact VARCHAR(255),
    case_title VARCHAR(500),
    total_records_produced INTEGER,
    bates_prefix VARCHAR(50),
    bates_range VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_subpoena_customer ON legal_subpoena(customer_id);
CREATE INDEX idx_subpoena_case ON legal_subpoena(case_number);
CREATE INDEX idx_subpoena_status ON legal_subpoena(status);
CREATE INDEX idx_subpoena_deadline ON legal_subpoena(response_deadline);

CREATE TRIGGER update_legal_subpoena_updated_at BEFORE UPDATE ON legal_subpoena
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS bankruptcy_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bankruptcy_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    case_number VARCHAR(100) UNIQUE NOT NULL,
    bankruptcy_chapter VARCHAR(50) NOT NULL,
    case_status VARCHAR(20) NOT NULL DEFAULT 'FILED',
    filing_date DATE NOT NULL,
    court_district VARCHAR(255) NOT NULL,
    trustee_name VARCHAR(255),
    trustee_email VARCHAR(255),
    trustee_phone VARCHAR(50),
    total_debt_amount DECIMAL(18, 2) NOT NULL,
    waqiti_claim_amount DECIMAL(18, 2),
    claim_classification VARCHAR(100),
    currency_code VARCHAR(3) DEFAULT 'USD',
    automatic_stay_active BOOLEAN DEFAULT TRUE,
    automatic_stay_date DATE,
    accounts_frozen BOOLEAN DEFAULT FALSE,
    pending_transactions_cancelled BOOLEAN DEFAULT FALSE,
    proof_of_claim_filed BOOLEAN DEFAULT FALSE,
    proof_of_claim_filing_date DATE,
    proof_of_claim_bar_date DATE,
    discharge_granted BOOLEAN DEFAULT FALSE,
    discharge_date DATE,
    dismissed BOOLEAN DEFAULT FALSE,
    dismissal_date DATE,
    dismissal_reason TEXT,
    expected_recovery_percentage DECIMAL(5, 2),
    credit_reporting_flagged BOOLEAN DEFAULT FALSE,
    credit_reporting_flag_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX idx_bankruptcy_customer ON bankruptcy_case(customer_id);
CREATE INDEX idx_bankruptcy_case_number ON bankruptcy_case(case_number);
CREATE INDEX idx_bankruptcy_chapter ON bankruptcy_case(bankruptcy_chapter);
CREATE INDEX idx_bankruptcy_status ON bankruptcy_case(case_status);
CREATE INDEX idx_bankruptcy_stay_active ON bankruptcy_case(automatic_stay_active);

CREATE TRIGGER update_bankruptcy_case_updated_at BEFORE UPDATE ON bankruptcy_case
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();