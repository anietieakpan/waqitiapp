-- CRITICAL COMPLIANCE: Database schema for regulatory compliance reporting
-- Creates comprehensive tables for storing compliance reports and audit trails
-- Supports AML, KYC, SAR, CTR, and other regulatory reporting requirements

-- Compliance reports storage table
CREATE TABLE IF NOT EXISTS compliance_reports (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(255) NOT NULL UNIQUE,
    report_type VARCHAR(100) NOT NULL,
    encrypted_data TEXT NOT NULL, -- Encrypted JSON data of the report
    generated_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'GENERATED',
    retention_date TIMESTAMP NOT NULL,
    file_path VARCHAR(500), -- Path to generated report file
    file_hash VARCHAR(256), -- SHA-256 hash for integrity verification
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- CTR (Currency Transaction Report) specific table
CREATE TABLE IF NOT EXISTS ctr_reports (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(255) NOT NULL UNIQUE,
    transaction_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    transaction_date TIMESTAMP NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_type VARCHAR(100) NOT NULL,
    
    -- Customer information (encrypted)
    customer_name_encrypted TEXT NOT NULL,
    customer_ssn_encrypted TEXT,
    customer_address_encrypted TEXT,
    customer_phone_encrypted TEXT,
    customer_email_encrypted TEXT,
    customer_dob_encrypted TEXT,
    customer_occupation_encrypted TEXT,
    
    -- Business information (if applicable)
    business_name_encrypted TEXT,
    business_address_encrypted TEXT,
    business_ein_encrypted TEXT,
    
    -- Financial institution information
    account_number_encrypted TEXT NOT NULL,
    routing_number_encrypted TEXT NOT NULL,
    financial_institution VARCHAR(255) NOT NULL DEFAULT 'Waqiti Financial Services',
    
    -- Reporting officer information
    reporting_officer VARCHAR(255) NOT NULL,
    reporting_officer_title VARCHAR(255) NOT NULL,
    reporting_officer_phone VARCHAR(50),
    reporting_officer_email VARCHAR(255),
    
    -- Status and timestamps
    report_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    fincen_submission_id VARCHAR(255),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_ctr_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_ctr_customer FOREIGN KEY (customer_id) REFERENCES users(id)
);

-- SAR (Suspicious Activity Report) specific table
CREATE TABLE IF NOT EXISTS sar_reports (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(255) NOT NULL UNIQUE,
    transaction_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    transaction_date TIMESTAMP NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Suspicious activity details
    suspicious_activity_type VARCHAR(100) NOT NULL,
    suspicious_activity_description TEXT NOT NULL,
    risk_score DECIMAL(5,2) NOT NULL,
    ml_flags JSONB, -- Machine learning detection flags
    
    -- Customer information (encrypted)
    customer_information_encrypted TEXT NOT NULL,
    
    -- Narrative and documentation
    transaction_narrative TEXT NOT NULL,
    supporting_documents JSONB, -- Array of document references
    compliance_officer_comments TEXT,
    
    -- Institution and officer information
    reporting_institution VARCHAR(255) NOT NULL DEFAULT 'Waqiti Financial Services',
    reporting_officer VARCHAR(255) NOT NULL,
    reviewing_officer VARCHAR(255),
    
    -- Status and workflow
    report_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_date TIMESTAMP,
    approval_date TIMESTAMP,
    submission_date TIMESTAMP,
    fincen_submission_id VARCHAR(255),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_sar_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_sar_customer FOREIGN KEY (customer_id) REFERENCES users(id)
);

-- KYC compliance tracking table
CREATE TABLE IF NOT EXISTS kyc_compliance_reports (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(255) NOT NULL UNIQUE,
    report_period_start TIMESTAMP NOT NULL,
    report_period_end TIMESTAMP NOT NULL,
    
    -- KYC statistics
    total_customers INTEGER NOT NULL DEFAULT 0,
    new_customers_count INTEGER NOT NULL DEFAULT 0,
    complete_kyc_count INTEGER NOT NULL DEFAULT 0,
    incomplete_kyc_count INTEGER NOT NULL DEFAULT 0,
    expired_kyc_count INTEGER NOT NULL DEFAULT 0,
    pending_review_count INTEGER NOT NULL DEFAULT 0,
    
    -- Compliance metrics
    kyc_compliance_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    
    -- Risk assessment summary
    risk_assessment_summary JSONB,
    document_verification_stats JSONB,
    identity_verification_stats JSONB,
    compliance_issues JSONB,
    recommendations JSONB,
    
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Monthly compliance summary table
CREATE TABLE IF NOT EXISTS monthly_compliance_summaries (
    id BIGSERIAL PRIMARY KEY,
    report_id VARCHAR(255) NOT NULL UNIQUE,
    report_period_start TIMESTAMP NOT NULL,
    report_period_end TIMESTAMP NOT NULL,
    
    -- Transaction metrics
    total_transaction_volume DECIMAL(20,2) NOT NULL DEFAULT 0.00,
    total_transaction_count BIGINT NOT NULL DEFAULT 0,
    high_risk_transaction_count BIGINT NOT NULL DEFAULT 0,
    
    -- Reporting metrics
    ctr_reports_generated INTEGER NOT NULL DEFAULT 0,
    sar_reports_generated INTEGER NOT NULL DEFAULT 0,
    aml_alerts_generated INTEGER NOT NULL DEFAULT 0,
    aml_alerts_resolved INTEGER NOT NULL DEFAULT 0,
    
    -- Compliance rates
    kyc_compliance_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    training_completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    
    -- Risk distribution
    customer_risk_distribution JSONB,
    
    -- Examinations and violations
    regulatory_examinations INTEGER NOT NULL DEFAULT 0,
    compliance_violations INTEGER NOT NULL DEFAULT 0,
    corrective_actions_implemented INTEGER NOT NULL DEFAULT 0,
    
    -- Summaries and analysis
    executive_summary TEXT,
    key_risk_indicators JSONB,
    recommended_actions JSONB,
    
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- AML alerts and monitoring table
CREATE TABLE IF NOT EXISTS aml_alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_id VARCHAR(255) NOT NULL UNIQUE,
    alert_type VARCHAR(100) NOT NULL,
    alert_severity VARCHAR(50) NOT NULL,
    
    -- Related entities
    customer_id VARCHAR(255),
    transaction_id VARCHAR(255),
    
    -- Alert details
    description TEXT NOT NULL,
    detection_method VARCHAR(100) NOT NULL, -- 'RULE_BASED', 'ML_MODEL', 'MANUAL'
    risk_score DECIMAL(5,2),
    ml_model_version VARCHAR(50),
    
    -- Alert data
    alert_data JSONB NOT NULL,
    
    -- Workflow
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(255),
    reviewed_by VARCHAR(255),
    resolution TEXT,
    false_positive BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    resolved_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_aml_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_aml_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

-- Compliance violations tracking table
CREATE TABLE IF NOT EXISTS compliance_violations (
    id BIGSERIAL PRIMARY KEY,
    violation_id VARCHAR(255) NOT NULL UNIQUE,
    violation_type VARCHAR(100) NOT NULL,
    violation_category VARCHAR(100) NOT NULL, -- 'AML', 'KYC', 'BSA', 'OFAC', etc.
    severity VARCHAR(50) NOT NULL,
    
    -- Violation details
    description TEXT NOT NULL,
    regulation_reference VARCHAR(255),
    
    -- Related entities
    customer_id VARCHAR(255),
    transaction_id VARCHAR(255),
    employee_id VARCHAR(255),
    
    -- Detection and reporting
    detected_by VARCHAR(255),
    detection_method VARCHAR(100),
    reported_to_regulator BOOLEAN DEFAULT FALSE,
    regulator_reference VARCHAR(255),
    
    -- Resolution
    corrective_action TEXT,
    remediation_plan TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    resolution_date TIMESTAMP,
    
    -- Financial impact
    potential_fine DECIMAL(15,2),
    actual_fine DECIMAL(15,2),
    
    -- Timestamps
    violation_date TIMESTAMP NOT NULL,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reported_at TIMESTAMP,
    resolved_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_violation_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_violation_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

-- Compliance training tracking table
CREATE TABLE IF NOT EXISTS compliance_training (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(255) NOT NULL,
    training_module VARCHAR(255) NOT NULL,
    training_type VARCHAR(100) NOT NULL, -- 'AML', 'KYC', 'FRAUD', 'BSA', etc.
    
    -- Training details
    training_version VARCHAR(50),
    training_provider VARCHAR(255),
    required_completion_date TIMESTAMP,
    
    -- Completion tracking
    started_at TIMESTAMP,
    completion_date TIMESTAMP,
    score DECIMAL(5,2),
    passing_score DECIMAL(5,2) DEFAULT 80.00,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    
    -- Certification
    certificate_issued BOOLEAN DEFAULT FALSE,
    certificate_expires_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(employee_id, training_module, training_version)
);

-- Regulatory examination tracking table
CREATE TABLE IF NOT EXISTS regulatory_examinations (
    id BIGSERIAL PRIMARY KEY,
    examination_id VARCHAR(255) NOT NULL UNIQUE,
    regulator VARCHAR(255) NOT NULL, -- 'FDIC', 'OCC', 'FED', 'FINCEN', etc.
    examination_type VARCHAR(100) NOT NULL,
    
    -- Examination details
    examination_scope TEXT,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    lead_examiner VARCHAR(255),
    
    -- Results
    overall_rating VARCHAR(50),
    findings_count INTEGER DEFAULT 0,
    violations_count INTEGER DEFAULT 0,
    corrective_actions_required INTEGER DEFAULT 0,
    
    -- Follow-up
    response_due_date TIMESTAMP,
    response_submitted_date TIMESTAMP,
    final_report_received_date TIMESTAMP,
    
    -- Documentation
    examination_report_path VARCHAR(500),
    response_document_path VARCHAR(500),
    
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_reports_type_date 
    ON compliance_reports(report_type, generated_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_reports_status 
    ON compliance_reports(status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ctr_reports_date 
    ON ctr_reports(transaction_date);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ctr_reports_amount 
    ON ctr_reports(amount);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ctr_reports_status 
    ON ctr_reports(report_status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_reports_date 
    ON sar_reports(transaction_date);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_reports_risk_score 
    ON sar_reports(risk_score);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_reports_status 
    ON sar_reports(report_status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_status 
    ON aml_alerts(status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_customer 
    ON aml_alerts(customer_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_detected_at 
    ON aml_alerts(detected_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_violations_date 
    ON compliance_violations(violation_date);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_violations_type 
    ON compliance_violations(violation_type);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_training_employee 
    ON compliance_training(employee_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_training_status 
    ON compliance_training(status);

-- Row Level Security (RLS) for sensitive compliance data
ALTER TABLE compliance_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE ctr_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE sar_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE compliance_violations ENABLE ROW LEVEL SECURITY;

-- RLS policies (examples - adjust based on actual roles)
CREATE POLICY compliance_reports_policy ON compliance_reports
    FOR ALL
    TO compliance_officers
    USING (true);

CREATE POLICY ctr_reports_policy ON ctr_reports
    FOR ALL
    TO compliance_officers
    USING (true);

CREATE POLICY sar_reports_policy ON sar_reports
    FOR ALL
    TO compliance_officers
    USING (true);

-- Comments for documentation
COMMENT ON TABLE compliance_reports IS 'Master table for storing encrypted compliance reports with audit trail';
COMMENT ON TABLE ctr_reports IS 'Currency Transaction Reports for transactions >= $10,000';
COMMENT ON TABLE sar_reports IS 'Suspicious Activity Reports for potential money laundering';
COMMENT ON TABLE kyc_compliance_reports IS 'KYC compliance status and metrics reporting';
COMMENT ON TABLE monthly_compliance_summaries IS 'Monthly compliance summary reports for management';
COMMENT ON TABLE aml_alerts IS 'Anti-Money Laundering alerts and monitoring';
COMMENT ON TABLE compliance_violations IS 'Compliance violations tracking and remediation';
COMMENT ON TABLE compliance_training IS 'Employee compliance training tracking';
COMMENT ON TABLE regulatory_examinations IS 'Regulatory examination tracking and results';

-- Trigger for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to all compliance tables
CREATE TRIGGER update_compliance_reports_updated_at BEFORE UPDATE ON compliance_reports 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ctr_reports_updated_at BEFORE UPDATE ON ctr_reports 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_sar_reports_updated_at BEFORE UPDATE ON sar_reports 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kyc_compliance_reports_updated_at BEFORE UPDATE ON kyc_compliance_reports 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_monthly_compliance_summaries_updated_at BEFORE UPDATE ON monthly_compliance_summaries 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_aml_alerts_updated_at BEFORE UPDATE ON aml_alerts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_violations_updated_at BEFORE UPDATE ON compliance_violations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_training_updated_at BEFORE UPDATE ON compliance_training 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_regulatory_examinations_updated_at BEFORE UPDATE ON regulatory_examinations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();