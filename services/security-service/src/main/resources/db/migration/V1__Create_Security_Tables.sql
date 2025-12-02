-- Security Service Database Schema
-- Creates comprehensive security, fraud detection, and compliance tables

-- =====================================================================
-- SECURITY EVENTS TABLE
-- =====================================================================

CREATE TABLE security_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID,
    event_type VARCHAR(50) NOT NULL CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'PASSWORD_CHANGE',
        'MFA_ENABLED', 'MFA_DISABLED', 'MFA_SUCCESS', 'MFA_FAILURE',
        'FRAUD_DETECTED', 'TRANSACTION_BLOCKED', 'ACCOUNT_LOCKED',
        'SUSPICIOUS_ACTIVITY', 'DEVICE_REGISTERED', 'LOCATION_ANOMALY',
        'VELOCITY_EXCEEDED', 'AML_ALERT', 'COMPLIANCE_VIOLATION'
    )),
    severity VARCHAR(20) NOT NULL CHECK (severity IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )) DEFAULT 'MEDIUM',
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    source_ip VARCHAR(45),
    user_agent TEXT,
    device_fingerprint VARCHAR(255),
    location_data JSONB,
    additional_data JSONB,
    session_id VARCHAR(255),
    transaction_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'OPEN', 'INVESTIGATING', 'RESOLVED', 'FALSE_POSITIVE', 'DISMISSED'
    )) DEFAULT 'OPEN',
    resolution_notes TEXT,
    assigned_to UUID,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- AML ALERTS TABLE
-- =====================================================================

CREATE TABLE aml_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(50) UNIQUE NOT NULL,
    transaction_id UUID,
    user_id UUID,
    alert_type VARCHAR(50) NOT NULL CHECK (alert_type IN (
        'THRESHOLD_EXCEEDED', 'WATCHLIST_MATCH', 'PEP_MATCH', 'ADVERSE_MEDIA',
        'STRUCTURING', 'RAPID_MOVEMENT', 'SUSPICIOUS_PATTERN', 'VELOCITY_ANOMALY',
        'GEOGRAPHIC_RISK', 'UNUSUAL_BEHAVIOR', 'SYSTEM_ERROR'
    )),
    severity VARCHAR(20) NOT NULL CHECK (severity IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    risk_score DECIMAL(5,2) CHECK (risk_score >= 0 AND risk_score <= 100),
    details JSONB,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'OPEN', 'INVESTIGATING', 'ESCALATED', 'RESOLVED', 'FALSE_POSITIVE'
    )) DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL CHECK (priority IN (
        'LOW', 'NORMAL', 'HIGH', 'URGENT'
    )) DEFAULT 'NORMAL',
    assigned_to UUID,
    investigation_notes TEXT,
    resolution_action VARCHAR(100),
    escalated_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    auto_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    sar_generated BOOLEAN NOT NULL DEFAULT FALSE,
    sar_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- SUSPICIOUS ACTIVITY REPORTS TABLE
-- =====================================================================

CREATE TABLE suspicious_activity_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sar_number VARCHAR(50) UNIQUE NOT NULL,
    transaction_id UUID,
    user_id UUID,
    report_type VARCHAR(50) NOT NULL CHECK (report_type IN (
        'UNUSUAL_TRANSACTION', 'IDENTITY_THEFT', 'MONEY_LAUNDERING',
        'TERRORIST_FINANCING', 'FRAUD', 'CYBER_EVENT', 'OTHER'
    )),
    suspicious_activity TEXT NOT NULL,
    narrative_description TEXT NOT NULL,
    reporting_institution VARCHAR(255) NOT NULL,
    filing_date TIMESTAMP WITH TIME ZONE NOT NULL,
    incident_date TIMESTAMP WITH TIME ZONE NOT NULL,
    total_amount DECIMAL(19,4),
    currency VARCHAR(3),
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'FILED', 'REJECTED'
    )) DEFAULT 'DRAFT',
    priority VARCHAR(20) NOT NULL CHECK (priority IN (
        'NORMAL', 'HIGH', 'URGENT'
    )) DEFAULT 'NORMAL',
    involved_parties JSONB,
    supporting_documents JSONB,
    regulatory_flags JSONB,
    filing_reference VARCHAR(100),
    filed_at TIMESTAMP WITH TIME ZONE,
    filed_by UUID,
    reviewed_by UUID,
    approved_by UUID,
    review_notes TEXT,
    approval_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- FRAUD DETECTION RESULTS TABLE
-- =====================================================================

CREATE TABLE fraud_detection_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id UUID UNIQUE NOT NULL,
    transaction_id UUID NOT NULL,
    user_id UUID,
    risk_level VARCHAR(20) NOT NULL CHECK (risk_level IN (
        'VERY_LOW', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH', 'CRITICAL'
    )),
    risk_score DECIMAL(5,2) NOT NULL CHECK (risk_score >= 0 AND risk_score <= 100),
    decision VARCHAR(20) NOT NULL CHECK (decision IN (
        'APPROVE', 'DECLINE', 'REVIEW'
    )),
    indicators JSONB,
    features JSONB,
    scores JSONB,
    analysis_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    model_version VARCHAR(50),
    processing_time_ms BIGINT,
    requires_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    review_reason TEXT,
    recommendations JSONB,
    reviewer_id UUID,
    review_decision VARCHAR(20),
    review_notes TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- TRANSACTION PATTERNS TABLE
-- =====================================================================

CREATE TABLE transaction_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern_id VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    pattern_type VARCHAR(50) NOT NULL CHECK (pattern_type IN (
        'VELOCITY', 'AMOUNT', 'TIME', 'LOCATION', 'DEVICE', 'BEHAVIORAL'
    )),
    pattern_name VARCHAR(100) NOT NULL,
    pattern_description TEXT,
    detection_algorithm VARCHAR(50),
    baseline_data JSONB,
    current_data JSONB,
    deviation_score DECIMAL(5,2),
    confidence_score DECIMAL(5,2),
    risk_contribution DECIMAL(5,2),
    first_detected TIMESTAMP WITH TIME ZONE NOT NULL,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL,
    occurrence_count INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'ACTIVE', 'RESOLVED', 'IGNORED', 'INVESTIGATING'
    )) DEFAULT 'ACTIVE',
    investigation_notes TEXT,
    related_transactions JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- REGULATORY REPORTS TABLE
-- =====================================================================

CREATE TABLE regulatory_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id VARCHAR(50) UNIQUE NOT NULL,
    report_type VARCHAR(50) NOT NULL CHECK (report_type IN (
        'SAR', 'CTR', 'FBAR', 'BSA', 'OFAC', 'KYC_REVIEW', 'AUDIT'
    )),
    reporting_period_start DATE NOT NULL,
    reporting_period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SUBMITTED', 'ACKNOWLEDGED'
    )) DEFAULT 'DRAFT',
    report_data JSONB NOT NULL,
    submission_deadline DATE,
    submitted_at TIMESTAMP WITH TIME ZONE,
    submitted_by UUID,
    acknowledgment_reference VARCHAR(100),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    filing_errors JSONB,
    generated_by VARCHAR(100),
    approved_by UUID,
    approval_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- DEVICE FINGERPRINTS TABLE
-- =====================================================================

CREATE TABLE device_fingerprints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fingerprint_hash VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    device_type VARCHAR(50),
    browser_name VARCHAR(100),
    browser_version VARCHAR(50),
    operating_system VARCHAR(100),
    screen_resolution VARCHAR(20),
    timezone VARCHAR(50),
    language VARCHAR(10),
    plugins JSONB,
    canvas_fingerprint VARCHAR(255),
    webgl_fingerprint VARCHAR(255),
    audio_fingerprint VARCHAR(255),
    first_seen TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usage_count INTEGER NOT NULL DEFAULT 1,
    risk_score INTEGER CHECK (risk_score >= 0 AND risk_score <= 100),
    risk_factors JSONB,
    is_trusted BOOLEAN NOT NULL DEFAULT FALSE,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_reason TEXT,
    blocked_at TIMESTAMP WITH TIME ZONE,
    blocked_by UUID,
    location_history JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- USER BEHAVIORAL PROFILES TABLE
-- =====================================================================

CREATE TABLE user_behavioral_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    profile_data JSONB NOT NULL,
    typical_amount_range JSONB,
    typical_time_patterns JSONB,
    typical_locations JSONB,
    typical_devices JSONB,
    velocity_patterns JSONB,
    risk_baseline DECIMAL(5,2),
    last_transaction_analysis TIMESTAMP WITH TIME ZONE,
    baseline_period_start DATE,
    baseline_period_end DATE,
    profile_confidence DECIMAL(5,2),
    learning_mode BOOLEAN NOT NULL DEFAULT TRUE,
    anomaly_threshold DECIMAL(5,2) DEFAULT 2.5,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- WATCHLIST ENTRIES TABLE
-- =====================================================================

CREATE TABLE watchlist_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id VARCHAR(50) UNIQUE NOT NULL,
    list_type VARCHAR(50) NOT NULL CHECK (list_type IN (
        'SANCTIONS', 'PEP', 'ADVERSE_MEDIA', 'INTERNAL_BLACKLIST', 'CUSTOM'
    )),
    list_name VARCHAR(100) NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) CHECK (entity_type IN (
        'INDIVIDUAL', 'ORGANIZATION', 'VESSEL', 'AIRCRAFT'
    )),
    aliases JSONB,
    identifiers JSONB,
    addresses JSONB,
    additional_info JSONB,
    risk_level VARCHAR(20) CHECK (risk_level IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    source VARCHAR(100),
    effective_date DATE,
    expiry_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_updated TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- COMPLIANCE VIOLATIONS TABLE
-- =====================================================================

CREATE TABLE compliance_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    violation_id VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID,
    transaction_id UUID,
    violation_type VARCHAR(50) NOT NULL CHECK (violation_type IN (
        'KYC_INCOMPLETE', 'TRANSACTION_LIMIT_EXCEEDED', 'SANCTIONS_VIOLATION',
        'AML_THRESHOLD_EXCEEDED', 'GEOGRAPHIC_RESTRICTION', 'AGE_RESTRICTION',
        'DOCUMENTATION_INSUFFICIENT', 'SUSPICIOUS_ACTIVITY'
    )),
    severity VARCHAR(20) NOT NULL CHECK (severity IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    description TEXT NOT NULL,
    regulation_reference VARCHAR(100),
    violation_details JSONB,
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'DETECTED', 'INVESTIGATING', 'REMEDIATED', 'ESCALATED', 'CLOSED'
    )) DEFAULT 'DETECTED',
    remediation_action VARCHAR(255),
    remediation_deadline DATE,
    assigned_to UUID,
    remediated_at TIMESTAMP WITH TIME ZONE,
    remediated_by UUID,
    escalated_to VARCHAR(100),
    escalated_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================================

-- Security events indexes
CREATE INDEX idx_security_events_user_id ON security_events(user_id);
CREATE INDEX idx_security_events_event_type ON security_events(event_type);
CREATE INDEX idx_security_events_severity ON security_events(severity);
CREATE INDEX idx_security_events_status ON security_events(status);
CREATE INDEX idx_security_events_created_at ON security_events(created_at);
CREATE INDEX idx_security_events_transaction_id ON security_events(transaction_id);

-- AML alerts indexes
CREATE INDEX idx_aml_alerts_transaction_id ON aml_alerts(transaction_id);
CREATE INDEX idx_aml_alerts_user_id ON aml_alerts(user_id);
CREATE INDEX idx_aml_alerts_alert_type ON aml_alerts(alert_type);
CREATE INDEX idx_aml_alerts_severity ON aml_alerts(severity);
CREATE INDEX idx_aml_alerts_status ON aml_alerts(status);
CREATE INDEX idx_aml_alerts_created_at ON aml_alerts(created_at);
CREATE INDEX idx_aml_alerts_risk_score ON aml_alerts(risk_score);

-- SAR indexes
CREATE INDEX idx_sar_transaction_id ON suspicious_activity_reports(transaction_id);
CREATE INDEX idx_sar_user_id ON suspicious_activity_reports(user_id);
CREATE INDEX idx_sar_report_type ON suspicious_activity_reports(report_type);
CREATE INDEX idx_sar_status ON suspicious_activity_reports(status);
CREATE INDEX idx_sar_filing_date ON suspicious_activity_reports(filing_date);
CREATE INDEX idx_sar_incident_date ON suspicious_activity_reports(incident_date);

-- Fraud detection indexes
CREATE INDEX idx_fraud_results_transaction_id ON fraud_detection_results(transaction_id);
CREATE INDEX idx_fraud_results_user_id ON fraud_detection_results(user_id);
CREATE INDEX idx_fraud_results_risk_level ON fraud_detection_results(risk_level);
CREATE INDEX idx_fraud_results_decision ON fraud_detection_results(decision);
CREATE INDEX idx_fraud_results_timestamp ON fraud_detection_results(analysis_timestamp);
CREATE INDEX idx_fraud_results_manual_review ON fraud_detection_results(requires_manual_review);

-- Transaction patterns indexes
CREATE INDEX idx_patterns_user_id ON transaction_patterns(user_id);
CREATE INDEX idx_patterns_type ON transaction_patterns(pattern_type);
CREATE INDEX idx_patterns_status ON transaction_patterns(status);
CREATE INDEX idx_patterns_first_detected ON transaction_patterns(first_detected);
CREATE INDEX idx_patterns_deviation_score ON transaction_patterns(deviation_score);

-- Regulatory reports indexes
CREATE INDEX idx_regulatory_reports_type ON regulatory_reports(report_type);
CREATE INDEX idx_regulatory_reports_status ON regulatory_reports(status);
CREATE INDEX idx_regulatory_reports_period ON regulatory_reports(reporting_period_start, reporting_period_end);
CREATE INDEX idx_regulatory_reports_deadline ON regulatory_reports(submission_deadline);

-- Device fingerprints indexes
CREATE INDEX idx_device_fingerprints_user_id ON device_fingerprints(user_id);
CREATE INDEX idx_device_fingerprints_hash ON device_fingerprints(fingerprint_hash);
CREATE INDEX idx_device_fingerprints_first_seen ON device_fingerprints(first_seen);
CREATE INDEX idx_device_fingerprints_last_seen ON device_fingerprints(last_seen);
CREATE INDEX idx_device_fingerprints_risk_score ON device_fingerprints(risk_score);
CREATE INDEX idx_device_fingerprints_trusted ON device_fingerprints(is_trusted);

-- Behavioral profiles indexes
CREATE INDEX idx_behavioral_profiles_user_id ON user_behavioral_profiles(user_id);
CREATE INDEX idx_behavioral_profiles_updated_at ON user_behavioral_profiles(updated_at);
CREATE INDEX idx_behavioral_profiles_learning_mode ON user_behavioral_profiles(learning_mode);

-- Watchlist indexes
CREATE INDEX idx_watchlist_entity_name ON watchlist_entries(entity_name);
CREATE INDEX idx_watchlist_list_type ON watchlist_entries(list_type);
CREATE INDEX idx_watchlist_is_active ON watchlist_entries(is_active);
CREATE INDEX idx_watchlist_risk_level ON watchlist_entries(risk_level);
CREATE INDEX idx_watchlist_effective_date ON watchlist_entries(effective_date);

-- Compliance violations indexes
CREATE INDEX idx_violations_user_id ON compliance_violations(user_id);
CREATE INDEX idx_violations_transaction_id ON compliance_violations(transaction_id);
CREATE INDEX idx_violations_type ON compliance_violations(violation_type);
CREATE INDEX idx_violations_severity ON compliance_violations(severity);
CREATE INDEX idx_violations_status ON compliance_violations(status);
CREATE INDEX idx_violations_created_at ON compliance_violations(created_at);

-- =====================================================================
-- TRIGGERS FOR AUTOMATIC UPDATES
-- =====================================================================

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to all relevant tables
CREATE TRIGGER update_security_events_updated_at 
    BEFORE UPDATE ON security_events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_aml_alerts_updated_at 
    BEFORE UPDATE ON aml_alerts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_sar_updated_at 
    BEFORE UPDATE ON suspicious_activity_reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_patterns_updated_at 
    BEFORE UPDATE ON transaction_patterns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_regulatory_reports_updated_at 
    BEFORE UPDATE ON regulatory_reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_fingerprints_updated_at 
    BEFORE UPDATE ON device_fingerprints
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_behavioral_profiles_updated_at 
    BEFORE UPDATE ON user_behavioral_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_violations_updated_at 
    BEFORE UPDATE ON compliance_violations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON TABLE security_events IS 'Security events and incidents tracking';
COMMENT ON TABLE aml_alerts IS 'Anti-money laundering alerts and monitoring';
COMMENT ON TABLE suspicious_activity_reports IS 'Suspicious Activity Reports for regulatory compliance';
COMMENT ON TABLE fraud_detection_results IS 'Fraud detection analysis results';
COMMENT ON TABLE transaction_patterns IS 'Detected transaction patterns and anomalies';
COMMENT ON TABLE regulatory_reports IS 'Regulatory reporting and compliance';
COMMENT ON TABLE device_fingerprints IS 'Device fingerprinting for security';
COMMENT ON TABLE user_behavioral_profiles IS 'User behavioral analysis profiles';
COMMENT ON TABLE watchlist_entries IS 'Sanctions and watchlist screening data';
COMMENT ON TABLE compliance_violations IS 'Compliance violations tracking';

-- =====================================================================
-- INITIAL DATA FOR WATCHLISTS
-- =====================================================================

-- Insert sample watchlist entries (in production, these would be loaded from regulatory sources)
INSERT INTO watchlist_entries (
    entry_id, list_type, list_name, entity_name, entity_type, risk_level, source, is_active
) VALUES 
('OFAC-001', 'SANCTIONS', 'OFAC SDN List', 'Sample Sanctioned Entity', 'ORGANIZATION', 'CRITICAL', 'OFAC', true),
('PEP-001', 'PEP', 'Politically Exposed Persons', 'Sample PEP Individual', 'INDIVIDUAL', 'HIGH', 'Internal', true);