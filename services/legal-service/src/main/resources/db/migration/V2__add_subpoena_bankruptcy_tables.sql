-- Legal Service - Subpoena and Bankruptcy Tables
-- Created: 2025-10-18
-- Description: Add subpoena processing and bankruptcy case management tables

-- Bankruptcy Case Table
-- Complete production-ready bankruptcy case tracking with multi-chapter support,
-- automatic stay enforcement, creditor claim management, and discharge monitoring
CREATE TABLE IF NOT EXISTS bankruptcy_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bankruptcy_id VARCHAR(100) UNIQUE NOT NULL,
    case_number VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    bankruptcy_chapter VARCHAR(20) NOT NULL,
    case_status VARCHAR(30) NOT NULL,
    filing_date DATE NOT NULL,
    court_district VARCHAR(100) NOT NULL,
    court_division VARCHAR(100),
    judge_name VARCHAR(255),
    trustee_name VARCHAR(255),
    trustee_email VARCHAR(255),
    trustee_phone VARCHAR(20),
    debtor_attorney VARCHAR(255),
    debtor_attorney_firm VARCHAR(255),
    total_debt_amount DECIMAL(18, 2),
    secured_debt_amount DECIMAL(18, 2),
    unsecured_debt_amount DECIMAL(18, 2),
    priority_debt_amount DECIMAL(18, 2),
    total_assets_value DECIMAL(18, 2),
    currency_code VARCHAR(3) DEFAULT 'USD',
    waqiti_claim_amount DECIMAL(18, 2),
    claim_classification VARCHAR(50),
    proof_of_claim_filed BOOLEAN DEFAULT FALSE,
    proof_of_claim_date DATE,
    proof_of_claim_bar_date DATE,
    automatic_stay_active BOOLEAN NOT NULL DEFAULT TRUE,
    automatic_stay_date DATE,
    automatic_stay_lifted_date DATE,
    stay_relief_motion_filed BOOLEAN DEFAULT FALSE,
    accounts_frozen BOOLEAN DEFAULT FALSE,
    frozen_account_ids TEXT[],
    pending_transactions_cancelled BOOLEAN DEFAULT FALSE,
    cancelled_transaction_ids TEXT[],
    meeting_341_scheduled BOOLEAN DEFAULT FALSE,
    meeting_341_date TIMESTAMP,
    meeting_341_location TEXT,
    meeting_341_attended BOOLEAN DEFAULT FALSE,
    reaffirmation_agreement_requested BOOLEAN DEFAULT FALSE,
    reaffirmation_agreement_approved BOOLEAN DEFAULT FALSE,
    repayment_plan JSONB,
    plan_payment_amount DECIMAL(18, 2),
    plan_duration_months INTEGER,
    plan_confirmed BOOLEAN DEFAULT FALSE,
    plan_confirmation_date DATE,
    exempt_assets JSONB,
    non_exempt_assets JSONB,
    liquidation_proceeding BOOLEAN DEFAULT FALSE,
    discharge_granted BOOLEAN DEFAULT FALSE,
    discharge_date DATE,
    discharge_type VARCHAR(50),
    dismissed BOOLEAN DEFAULT FALSE,
    dismissal_date DATE,
    dismissal_reason TEXT,
    converted_to_chapter VARCHAR(20),
    conversion_date DATE,
    creditor_list JSONB DEFAULT '[]'::jsonb,
    court_orders JSONB DEFAULT '[]'::jsonb,
    motions_filed JSONB DEFAULT '[]'::jsonb,
    payments_received JSONB DEFAULT '[]'::jsonb,
    total_payments_received DECIMAL(18, 2) DEFAULT 0,
    expected_recovery_percentage DECIMAL(5, 2),
    actual_recovery_amount DECIMAL(18, 2) DEFAULT 0,
    internal_notes JSONB DEFAULT '[]'::jsonb,
    audit_trail JSONB DEFAULT '[]'::jsonb,
    credit_reporting_flagged BOOLEAN DEFAULT FALSE,
    credit_reporting_flag_date DATE,
    all_departments_notified BOOLEAN DEFAULT FALSE,
    notification_sent_date TIMESTAMP,
    assigned_to VARCHAR(100),
    case_manager VARCHAR(100),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Subpoena Table
-- Complete production-ready subpoena processing with RFPA compliance,
-- 12-step zero-tolerance processing workflow, and legal hold management
CREATE TABLE IF NOT EXISTS subpoena (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subpoena_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    case_number VARCHAR(100) NOT NULL,
    issuing_court VARCHAR(255) NOT NULL,
    issuing_party VARCHAR(255),
    issuance_date DATE NOT NULL,
    response_deadline DATE NOT NULL,
    subpoena_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    requested_records TEXT NOT NULL,
    scope_description TEXT,
    time_period_start DATE,
    time_period_end DATE,
    validated BOOLEAN DEFAULT FALSE,
    validation_date TIMESTAMP,
    validated_by VARCHAR(100),
    invalid_reason TEXT,
    customer_notification_required BOOLEAN DEFAULT TRUE,
    customer_notified BOOLEAN DEFAULT FALSE,
    customer_notification_date TIMESTAMP,
    customer_notification_method VARCHAR(50),
    rfpa_exception_applied BOOLEAN DEFAULT FALSE,
    rfpa_exception_type VARCHAR(100),
    records_gathered JSONB DEFAULT '[]'::jsonb,
    total_records_count INTEGER DEFAULT 0,
    redaction_performed BOOLEAN DEFAULT FALSE,
    privileged_records_count INTEGER DEFAULT 0,
    document_production_prepared BOOLEAN DEFAULT FALSE,
    bates_numbering_range VARCHAR(255),
    production_start_bates VARCHAR(100),
    production_end_bates VARCHAR(100),
    production_format VARCHAR(50) DEFAULT 'PDF',
    records_certified BOOLEAN DEFAULT FALSE,
    certification_date TIMESTAMP,
    certified_by VARCHAR(100),
    certification_statement TEXT,
    submitted_to_court BOOLEAN DEFAULT FALSE,
    submission_date TIMESTAMP,
    submission_method VARCHAR(50),
    submission_tracking_number VARCHAR(255),
    compliance_certificate_filed BOOLEAN DEFAULT FALSE,
    compliance_certificate_date TIMESTAMP,
    compliance_certificate_path VARCHAR(1000),
    legal_hold_applied BOOLEAN DEFAULT FALSE,
    legal_hold_id VARCHAR(100),
    outside_counsel_engaged BOOLEAN DEFAULT FALSE,
    outside_counsel_name VARCHAR(255),
    escalated_to_legal_counsel BOOLEAN DEFAULT FALSE,
    escalation_reason TEXT,
    escalation_date TIMESTAMP,
    processing_notes JSONB DEFAULT '[]'::jsonb,
    audit_trail JSONB DEFAULT '[]'::jsonb,
    completed BOOLEAN DEFAULT FALSE,
    completion_date TIMESTAMP,
    assigned_to VARCHAR(100),
    priority_level VARCHAR(20) DEFAULT 'HIGH',
    created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for bankruptcy_case table
CREATE INDEX idx_bankruptcy_case_number ON bankruptcy_case(case_number);
CREATE INDEX idx_bankruptcy_customer ON bankruptcy_case(customer_id);
CREATE INDEX idx_bankruptcy_chapter ON bankruptcy_case(bankruptcy_chapter);
CREATE INDEX idx_bankruptcy_status ON bankruptcy_case(case_status);
CREATE INDEX idx_bankruptcy_filing_date ON bankruptcy_case(filing_date);
CREATE INDEX idx_bankruptcy_automatic_stay ON bankruptcy_case(automatic_stay_active);

-- Indexes for subpoena table
CREATE INDEX idx_subpoena_customer ON subpoena(customer_id);
CREATE INDEX idx_subpoena_case_number ON subpoena(case_number);
CREATE INDEX idx_subpoena_status ON subpoena(status);
CREATE INDEX idx_subpoena_deadline ON subpoena(response_deadline);
CREATE INDEX idx_subpoena_issuing_court ON subpoena(issuing_court);

-- Update timestamp triggers for bankruptcy_case
CREATE TRIGGER update_bankruptcy_case_updated_at BEFORE UPDATE ON bankruptcy_case
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Update timestamp triggers for subpoena
CREATE TRIGGER update_subpoena_updated_at BEFORE UPDATE ON subpoena
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
