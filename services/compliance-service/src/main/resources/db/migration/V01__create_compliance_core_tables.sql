-- ============================================================================
-- V01: Create Core Compliance Tables
-- ============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- Customer Risk Profiles Table
-- ============================================================================
CREATE TABLE customer_risk_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id VARCHAR(255) NOT NULL UNIQUE,
    risk_rating VARCHAR(20) NOT NULL CHECK (risk_rating IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    risk_score INTEGER NOT NULL DEFAULT 0 CHECK (risk_score >= 0 AND risk_score <= 100),
    
    -- PEP and Sanctions
    is_pep BOOLEAN DEFAULT FALSE,
    pep_details JSONB,
    is_sanctioned BOOLEAN DEFAULT FALSE,
    sanctions_details JSONB,
    last_sanctions_check TIMESTAMP,
    
    -- Customer Information
    customer_type VARCHAR(50) NOT NULL,
    occupation VARCHAR(255),
    industry VARCHAR(255),
    country_of_residence VARCHAR(3),
    nationality VARCHAR(3),
    
    -- Financial Profile
    declared_income DECIMAL(20, 2),
    income_source VARCHAR(255),
    expected_monthly_volume DECIMAL(20, 2),
    expected_transaction_count INTEGER,
    
    -- Risk Factors
    risk_factors JSONB DEFAULT '[]'::jsonb,
    high_risk_jurisdiction BOOLEAN DEFAULT FALSE,
    adverse_media_hits INTEGER DEFAULT 0,
    
    -- Historical Data
    total_transactions INTEGER DEFAULT 0,
    total_volume DECIMAL(20, 2) DEFAULT 0,
    sar_count INTEGER DEFAULT 0,
    alert_count INTEGER DEFAULT 0,
    false_positive_count INTEGER DEFAULT 0,
    
    -- Review Information
    last_review_date TIMESTAMP,
    next_review_date TIMESTAMP,
    review_frequency_days INTEGER DEFAULT 365,
    reviewed_by VARCHAR(255),
    review_notes TEXT,
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT DEFAULT 1,
    
    CONSTRAINT valid_risk_score CHECK (risk_score >= 0 AND risk_score <= 100)
);

CREATE INDEX idx_customer_risk_profiles_customer_id ON customer_risk_profiles(customer_id);
CREATE INDEX idx_customer_risk_profiles_risk_rating ON customer_risk_profiles(risk_rating);
CREATE INDEX idx_customer_risk_profiles_is_pep ON customer_risk_profiles(is_pep) WHERE is_pep = TRUE;
CREATE INDEX idx_customer_risk_profiles_is_sanctioned ON customer_risk_profiles(is_sanctioned) WHERE is_sanctioned = TRUE;
CREATE INDEX idx_customer_risk_profiles_next_review ON customer_risk_profiles(next_review_date);

-- ============================================================================
-- Compliance Transactions Table (for screening results)
-- ============================================================================
CREATE TABLE compliance_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id VARCHAR(255) NOT NULL,
    account_id VARCHAR(255),
    
    -- Transaction Details
    amount DECIMAL(20, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    transaction_date TIMESTAMP NOT NULL,
    
    -- Screening Results
    risk_score INTEGER,
    risk_level VARCHAR(20),
    screening_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    screening_date TIMESTAMP,
    rules_fired INTEGER DEFAULT 0,
    
    -- Decisions
    decision VARCHAR(50), -- APPROVE, REVIEW, ESCALATE, BLOCK
    decision_reason TEXT,
    auto_decision BOOLEAN DEFAULT TRUE,
    
    -- Flags
    is_blocked BOOLEAN DEFAULT FALSE,
    requires_review BOOLEAN DEFAULT FALSE,
    requires_sar BOOLEAN DEFAULT FALSE,
    is_ctr_required BOOLEAN DEFAULT FALSE,
    
    -- Risk Indicators (from Drools)
    risk_indicators JSONB DEFAULT '[]'::jsonb,
    alerts JSONB DEFAULT '[]'::jsonb,
    
    -- Review Information
    reviewed_by VARCHAR(255),
    review_date TIMESTAMP,
    review_notes TEXT,
    review_decision VARCHAR(50),
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_time_ms BIGINT,
    
    CONSTRAINT valid_screening_status CHECK (screening_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'ERROR'))
);

CREATE INDEX idx_compliance_transactions_transaction_id ON compliance_transactions(transaction_id);
CREATE INDEX idx_compliance_transactions_customer_id ON compliance_transactions(customer_id);
CREATE INDEX idx_compliance_transactions_screening_status ON compliance_transactions(screening_status);
CREATE INDEX idx_compliance_transactions_risk_level ON compliance_transactions(risk_level);
CREATE INDEX idx_compliance_transactions_requires_review ON compliance_transactions(requires_review) WHERE requires_review = TRUE;
CREATE INDEX idx_compliance_transactions_is_blocked ON compliance_transactions(is_blocked) WHERE is_blocked = TRUE;
CREATE INDEX idx_compliance_transactions_date ON compliance_transactions(transaction_date);

-- ============================================================================
-- Compliance Alerts Table
-- ============================================================================
CREATE TABLE compliance_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id VARCHAR(255) NOT NULL UNIQUE,
    transaction_id VARCHAR(255),
    customer_id VARCHAR(255) NOT NULL,
    account_id VARCHAR(255),
    
    -- Alert Details
    alert_type VARCHAR(100) NOT NULL,
    alert_category VARCHAR(50) NOT NULL, -- AML, FRAUD, SANCTIONS, PEP, etc.
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    
    -- Alert Content
    title VARCHAR(500) NOT NULL,
    description TEXT,
    risk_indicators JSONB DEFAULT '[]'::jsonb,
    evidence JSONB DEFAULT '{}'::jsonb,
    
    -- Status Management
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    disposition VARCHAR(50), -- TRUE_POSITIVE, FALSE_POSITIVE, ESCALATED, etc.
    assigned_to VARCHAR(255),
    assigned_date TIMESTAMP,
    
    -- Investigation
    investigation_notes TEXT,
    investigation_findings JSONB DEFAULT '{}'::jsonb,
    remediation_actions JSONB DEFAULT '[]'::jsonb,
    
    -- Escalation
    is_escalated BOOLEAN DEFAULT FALSE,
    escalated_to VARCHAR(255),
    escalation_date TIMESTAMP,
    escalation_reason TEXT,
    
    -- Resolution
    resolved_by VARCHAR(255),
    resolution_date TIMESTAMP,
    resolution_notes TEXT,
    time_to_resolution_hours INTEGER,
    
    -- SAR/CTR Filing
    sar_filed BOOLEAN DEFAULT FALSE,
    sar_filing_date TIMESTAMP,
    sar_reference VARCHAR(255),
    ctr_filed BOOLEAN DEFAULT FALSE,
    ctr_filing_date TIMESTAMP,
    ctr_reference VARCHAR(255),
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date TIMESTAMP,
    sla_breach BOOLEAN DEFAULT FALSE,
    
    CONSTRAINT valid_alert_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'PENDING_REVIEW', 'ESCALATED', 'CLOSED', 'ARCHIVED'))
);

CREATE INDEX idx_compliance_alerts_customer_id ON compliance_alerts(customer_id);
CREATE INDEX idx_compliance_alerts_transaction_id ON compliance_alerts(transaction_id);
CREATE INDEX idx_compliance_alerts_status ON compliance_alerts(status);
CREATE INDEX idx_compliance_alerts_severity ON compliance_alerts(severity);
CREATE INDEX idx_compliance_alerts_assigned_to ON compliance_alerts(assigned_to);
CREATE INDEX idx_compliance_alerts_created_at ON compliance_alerts(created_at);
CREATE INDEX idx_compliance_alerts_sar_filed ON compliance_alerts(sar_filed) WHERE sar_filed = TRUE;

-- ============================================================================
-- Compliance Rules Table (for Drools rule tracking)
-- ============================================================================
CREATE TABLE compliance_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rule_id VARCHAR(255) NOT NULL UNIQUE,
    rule_name VARCHAR(255) NOT NULL,
    rule_category VARCHAR(100) NOT NULL,
    rule_type VARCHAR(50) NOT NULL, -- DRL, DECISION_TABLE, etc.
    
    -- Rule Content
    description TEXT,
    rule_content TEXT, -- The actual DRL content
    risk_score INTEGER DEFAULT 0,
    
    -- Configuration
    is_enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    parameters JSONB DEFAULT '{}'::jsonb,
    thresholds JSONB DEFAULT '{}'::jsonb,
    
    -- Performance Metrics
    fire_count BIGINT DEFAULT 0,
    true_positive_count BIGINT DEFAULT 0,
    false_positive_count BIGINT DEFAULT 0,
    effectiveness_score DECIMAL(5, 2),
    avg_execution_time_ms BIGINT,
    
    -- Version Control
    version VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    previous_versions JSONB DEFAULT '[]'::jsonb,
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    last_fired_at TIMESTAMP,
    
    CONSTRAINT valid_effectiveness CHECK (effectiveness_score >= 0 AND effectiveness_score <= 100)
);

CREATE INDEX idx_compliance_rules_rule_id ON compliance_rules(rule_id);
CREATE INDEX idx_compliance_rules_category ON compliance_rules(rule_category);
CREATE INDEX idx_compliance_rules_enabled ON compliance_rules(is_enabled) WHERE is_enabled = TRUE;
CREATE INDEX idx_compliance_rules_fire_count ON compliance_rules(fire_count DESC);

-- ============================================================================
-- Suspicious Activity Reports (SAR) Table
-- ============================================================================
CREATE TABLE suspicious_activities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sar_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id VARCHAR(255) NOT NULL,
    
    -- Related Records
    alert_ids JSONB DEFAULT '[]'::jsonb,
    transaction_ids JSONB DEFAULT '[]'::jsonb,
    
    -- SAR Details
    suspicious_activity_type VARCHAR(100) NOT NULL,
    activity_date_range_start DATE NOT NULL,
    activity_date_range_end DATE NOT NULL,
    total_amount DECIMAL(20, 2),
    currency VARCHAR(3),
    
    -- Narrative
    narrative TEXT NOT NULL,
    red_flags JSONB DEFAULT '[]'::jsonb,
    supporting_documentation JSONB DEFAULT '[]'::jsonb,
    
    -- Filing Information
    filing_status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    filing_deadline DATE,
    filed_date TIMESTAMP,
    filing_reference VARCHAR(255),
    regulatory_body VARCHAR(100) DEFAULT 'FINCEN',
    
    -- Investigation
    investigator VARCHAR(255),
    investigation_start_date TIMESTAMP,
    investigation_end_date TIMESTAMP,
    investigation_findings TEXT,
    
    -- Approval Workflow
    prepared_by VARCHAR(255) NOT NULL,
    prepared_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(255),
    review_date TIMESTAMP,
    approved_by VARCHAR(255),
    approval_date TIMESTAMP,
    
    -- Quality Assurance
    qa_reviewed BOOLEAN DEFAULT FALSE,
    qa_reviewer VARCHAR(255),
    qa_review_date TIMESTAMP,
    qa_comments TEXT,
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 1,
    
    CONSTRAINT valid_filing_status CHECK (filing_status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'FILED', 'AMENDED', 'WITHDRAWN'))
);

CREATE INDEX idx_suspicious_activities_customer_id ON suspicious_activities(customer_id);
CREATE INDEX idx_suspicious_activities_filing_status ON suspicious_activities(filing_status);
CREATE INDEX idx_suspicious_activities_filing_deadline ON suspicious_activities(filing_deadline);
CREATE INDEX idx_suspicious_activities_filed_date ON suspicious_activities(filed_date);

-- ============================================================================
-- Compliance Audit Log Table
-- ============================================================================
CREATE TABLE compliance_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    audit_id VARCHAR(255) NOT NULL UNIQUE,
    
    -- Entity Information
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    entity_details JSONB,
    
    -- Action Details
    action VARCHAR(100) NOT NULL,
    action_category VARCHAR(50) NOT NULL,
    action_result VARCHAR(50),
    
    -- Change Tracking
    old_values JSONB,
    new_values JSONB,
    changed_fields JSONB DEFAULT '[]'::jsonb,
    
    -- User Information
    user_id VARCHAR(255) NOT NULL,
    user_name VARCHAR(255),
    user_role VARCHAR(100),
    ip_address INET,
    user_agent TEXT,
    session_id VARCHAR(255),
    
    -- Context
    reason TEXT,
    comments TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Compliance
    is_sensitive_action BOOLEAN DEFAULT FALSE,
    requires_dual_control BOOLEAN DEFAULT FALSE,
    approved_by VARCHAR(255),
    approval_timestamp TIMESTAMP
);

CREATE INDEX idx_compliance_audit_log_entity ON compliance_audit_log(entity_type, entity_id);
CREATE INDEX idx_compliance_audit_log_user_id ON compliance_audit_log(user_id);
CREATE INDEX idx_compliance_audit_log_action ON compliance_audit_log(action);
CREATE INDEX idx_compliance_audit_log_created_at ON compliance_audit_log(created_at DESC);
CREATE INDEX idx_compliance_audit_log_sensitive ON compliance_audit_log(is_sensitive_action) WHERE is_sensitive_action = TRUE;

-- ============================================================================
-- Document Storage for KYC/Compliance Documents
-- ============================================================================
CREATE TABLE compliance_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id VARCHAR(255) NOT NULL UNIQUE,
    customer_id VARCHAR(255) NOT NULL,
    
    -- Document Information
    document_type VARCHAR(100) NOT NULL,
    document_category VARCHAR(50) NOT NULL, -- KYC, PROOF_OF_INCOME, etc.
    document_name VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    
    -- Storage Information
    storage_path TEXT, -- Encrypted path
    storage_type VARCHAR(50) DEFAULT 'S3', -- S3, BLOB, FILE_SYSTEM
    is_encrypted BOOLEAN DEFAULT TRUE,
    encryption_key_id VARCHAR(255),
    checksum VARCHAR(255), -- For integrity verification
    
    -- Document Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    verification_status VARCHAR(50),
    verified_by VARCHAR(255),
    verification_date TIMESTAMP,
    verification_notes TEXT,
    
    -- Expiry and Retention
    issue_date DATE,
    expiry_date DATE,
    retention_period_days INTEGER DEFAULT 2555, -- 7 years default
    scheduled_deletion_date DATE,
    
    -- Metadata
    uploaded_by VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP,
    access_count INTEGER DEFAULT 0,
    
    -- Compliance
    contains_pii BOOLEAN DEFAULT TRUE,
    redacted_version_id UUID,
    
    CONSTRAINT valid_document_status CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'EXPIRED', 'ARCHIVED', 'DELETED'))
);

CREATE INDEX idx_compliance_documents_customer_id ON compliance_documents(customer_id);
CREATE INDEX idx_compliance_documents_type ON compliance_documents(document_type);
CREATE INDEX idx_compliance_documents_status ON compliance_documents(status);
CREATE INDEX idx_compliance_documents_expiry ON compliance_documents(expiry_date);

-- ============================================================================
-- Triggers for automatic timestamp updates
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_customer_risk_profiles_updated_at BEFORE UPDATE ON customer_risk_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_transactions_updated_at BEFORE UPDATE ON compliance_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_alerts_updated_at BEFORE UPDATE ON compliance_alerts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_rules_updated_at BEFORE UPDATE ON compliance_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_suspicious_activities_updated_at BEFORE UPDATE ON suspicious_activities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Row Level Security Policies
-- ============================================================================
ALTER TABLE compliance_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE suspicious_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE compliance_audit_log ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- Initial Data
-- ============================================================================
INSERT INTO compliance_rules (rule_id, rule_name, rule_category, rule_type, description, risk_score, is_enabled)
VALUES 
    ('CTR_THRESHOLD', 'Currency Transaction Report Threshold', 'REGULATORY', 'DRL', 'Transactions over $10,000 require CTR', 20, true),
    ('STRUCTURING_DETECTION', 'Structuring Pattern Detection', 'AML', 'DRL', 'Multiple transactions below CTR threshold', 50, true),
    ('PEP_SCREENING', 'Politically Exposed Person Screening', 'SANCTIONS', 'DRL', 'Enhanced due diligence for PEPs', 35, true),
    ('HIGH_RISK_JURISDICTION', 'High Risk Country Detection', 'SANCTIONS', 'DRL', 'Transactions involving high-risk countries', 45, true),
    ('RAPID_MOVEMENT', 'Rapid Fund Movement', 'AML', 'DRL', 'Quick in-and-out transaction patterns', 30, true);