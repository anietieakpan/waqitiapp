-- Audit Service Initial Schema
-- Created: 2025-09-27
-- Description: Comprehensive audit logging and compliance tracking schema

CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id VARCHAR(100) UNIQUE NOT NULL,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL,
    actor_name VARCHAR(255),
    actor_email VARCHAR(255),
    actor_ip_address VARCHAR(50),
    session_id VARCHAR(100),
    correlation_id VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    before_state JSONB,
    after_state JSONB,
    changes JSONB,
    metadata JSONB,
    user_agent TEXT,
    geolocation VARCHAR(100),
    device_fingerprint VARCHAR(255),
    is_sensitive BOOLEAN DEFAULT FALSE,
    retention_period_days INTEGER DEFAULT 2555,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS security_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    security_audit_id VARCHAR(100) UNIQUE NOT NULL,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    security_event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    user_id VARCHAR(100),
    username VARCHAR(255),
    ip_address VARCHAR(50),
    source_system VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    resource_accessed VARCHAR(255),
    authentication_method VARCHAR(50),
    authorization_result VARCHAR(20),
    access_denied_reason TEXT,
    risk_score DECIMAL(5, 4),
    is_suspicious BOOLEAN DEFAULT FALSE,
    threat_indicators JSONB,
    session_id VARCHAR(100),
    device_id VARCHAR(100),
    device_fingerprint VARCHAR(255),
    user_agent TEXT,
    geolocation VARCHAR(100),
    country_code VARCHAR(3),
    investigation_required BOOLEAN DEFAULT FALSE,
    investigated BOOLEAN DEFAULT FALSE,
    investigated_by VARCHAR(100),
    investigated_at TIMESTAMP,
    investigation_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS compliance_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    compliance_audit_id VARCHAR(100) UNIQUE NOT NULL,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    compliance_type VARCHAR(50) NOT NULL,
    regulation VARCHAR(50) NOT NULL,
    rule_reference VARCHAR(100),
    control_id VARCHAR(100),
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    check_result VARCHAR(20) NOT NULL,
    is_compliant BOOLEAN NOT NULL,
    findings TEXT,
    violations TEXT[],
    risk_level VARCHAR(20),
    remediation_required BOOLEAN DEFAULT FALSE,
    remediation_deadline DATE,
    remediation_status VARCHAR(20),
    remediation_notes TEXT,
    auditor_id VARCHAR(100),
    audit_evidence JSONB,
    evidence_urls TEXT[],
    attestation_required BOOLEAN DEFAULT FALSE,
    attested BOOLEAN DEFAULT FALSE,
    attested_by VARCHAR(100),
    attested_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS data_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_id VARCHAR(100) UNIQUE NOT NULL,
    access_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(100) NOT NULL,
    username VARCHAR(255),
    data_type VARCHAR(50) NOT NULL,
    data_classification VARCHAR(20) NOT NULL,
    table_name VARCHAR(100),
    record_id VARCHAR(100),
    access_type VARCHAR(20) NOT NULL,
    columns_accessed TEXT[],
    query_text TEXT,
    result_count INTEGER,
    access_reason VARCHAR(100),
    business_justification TEXT,
    is_authorized BOOLEAN NOT NULL,
    authorization_source VARCHAR(50),
    ip_address VARCHAR(50),
    application_name VARCHAR(100),
    session_id VARCHAR(100),
    is_bulk_access BOOLEAN DEFAULT FALSE,
    is_sensitive_data BOOLEAN DEFAULT FALSE,
    data_retention_category VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transaction_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_audit_id VARCHAR(100) UNIQUE NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    transaction_timestamp TIMESTAMP NOT NULL,
    customer_id UUID,
    account_id VARCHAR(100),
    amount DECIMAL(15, 2),
    currency VARCHAR(3),
    transaction_status VARCHAR(20) NOT NULL,
    initiated_by VARCHAR(100),
    approved_by VARCHAR(100),
    approval_timestamp TIMESTAMP,
    rejection_reason TEXT,
    fraud_check_result VARCHAR(20),
    fraud_score DECIMAL(5, 4),
    aml_check_result VARCHAR(20),
    sanctions_check_result VARCHAR(20),
    risk_level VARCHAR(20),
    flags TEXT[],
    source_channel VARCHAR(50),
    ip_address VARCHAR(50),
    geolocation VARCHAR(100),
    device_fingerprint VARCHAR(255),
    counterparty_name VARCHAR(255),
    counterparty_account VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_audit_id VARCHAR(100) UNIQUE NOT NULL,
    request_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    api_endpoint VARCHAR(255) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    api_version VARCHAR(20),
    request_id VARCHAR(100),
    correlation_id VARCHAR(100),
    user_id VARCHAR(100),
    api_key_id VARCHAR(100),
    client_id VARCHAR(100),
    ip_address VARCHAR(50),
    request_headers JSONB,
    request_body JSONB,
    request_params JSONB,
    response_status_code INTEGER,
    response_timestamp TIMESTAMP,
    response_time_ms INTEGER,
    response_body JSONB,
    response_size_bytes INTEGER,
    is_successful BOOLEAN,
    error_code VARCHAR(50),
    error_message TEXT,
    rate_limit_remaining INTEGER,
    rate_limit_exceeded BOOLEAN DEFAULT FALSE,
    user_agent TEXT,
    geolocation VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_change (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_id VARCHAR(100) UNIQUE NOT NULL,
    change_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_type VARCHAR(50) NOT NULL,
    component_name VARCHAR(100) NOT NULL,
    component_type VARCHAR(50) NOT NULL,
    config_key VARCHAR(255) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    change_reason TEXT,
    changed_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    approval_timestamp TIMESTAMP,
    environment VARCHAR(20) NOT NULL,
    deployment_id VARCHAR(100),
    rollback_available BOOLEAN DEFAULT FALSE,
    rollback_performed BOOLEAN DEFAULT FALSE,
    rollback_timestamp TIMESTAMP,
    rollback_by VARCHAR(100),
    impact_assessment TEXT,
    validation_result VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS privileged_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_id VARCHAR(100) UNIQUE NOT NULL,
    access_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(100) NOT NULL,
    username VARCHAR(255) NOT NULL,
    privileged_role VARCHAR(100) NOT NULL,
    action_performed TEXT NOT NULL,
    target_system VARCHAR(100),
    target_resource VARCHAR(255),
    access_method VARCHAR(50),
    session_id VARCHAR(100),
    session_duration_seconds INTEGER,
    commands_executed TEXT[],
    files_accessed TEXT[],
    records_modified INTEGER,
    approval_required BOOLEAN DEFAULT TRUE,
    approval_ticket VARCHAR(100),
    approved_by VARCHAR(100),
    approval_timestamp TIMESTAMP,
    emergency_access BOOLEAN DEFAULT FALSE,
    emergency_justification TEXT,
    ip_address VARCHAR(50),
    is_recorded BOOLEAN DEFAULT FALSE,
    recording_url TEXT,
    reviewed BOOLEAN DEFAULT FALSE,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id VARCHAR(100) UNIQUE NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    report_period_start TIMESTAMP NOT NULL,
    report_period_end TIMESTAMP NOT NULL,
    generated_by VARCHAR(100) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    filters JSONB,
    total_records INTEGER NOT NULL,
    summary JSONB,
    findings JSONB,
    recommendations TEXT[],
    report_format VARCHAR(20) NOT NULL,
    file_url TEXT,
    file_size_bytes BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_retention_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id VARCHAR(100) UNIQUE NOT NULL,
    policy_name VARCHAR(255) NOT NULL,
    audit_type VARCHAR(50) NOT NULL,
    data_classification VARCHAR(20) NOT NULL,
    retention_period_days INTEGER NOT NULL,
    archive_after_days INTEGER,
    archive_location VARCHAR(100),
    delete_after_days INTEGER,
    compliance_requirement TEXT,
    regulation VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_audit_log_timestamp ON audit_log(event_timestamp DESC);
CREATE INDEX idx_audit_log_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_category ON audit_log(event_category);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_id);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_status ON audit_log(status);
CREATE INDEX idx_audit_log_severity ON audit_log(severity);
CREATE INDEX idx_audit_log_correlation ON audit_log(correlation_id);
CREATE INDEX idx_audit_log_sensitive ON audit_log(is_sensitive) WHERE is_sensitive = true;

CREATE INDEX idx_security_audit_timestamp ON security_audit(event_timestamp DESC);
CREATE INDEX idx_security_audit_type ON security_audit(security_event_type);
CREATE INDEX idx_security_audit_user ON security_audit(user_id);
CREATE INDEX idx_security_audit_ip ON security_audit(ip_address);
CREATE INDEX idx_security_audit_severity ON security_audit(severity);
CREATE INDEX idx_security_audit_suspicious ON security_audit(is_suspicious) WHERE is_suspicious = true;
CREATE INDEX idx_security_audit_investigation ON security_audit(investigation_required) WHERE investigation_required = true;

CREATE INDEX idx_compliance_audit_timestamp ON compliance_audit(event_timestamp DESC);
CREATE INDEX idx_compliance_audit_type ON compliance_audit(compliance_type);
CREATE INDEX idx_compliance_audit_regulation ON compliance_audit(regulation);
CREATE INDEX idx_compliance_audit_entity ON compliance_audit(entity_type, entity_id);
CREATE INDEX idx_compliance_audit_result ON compliance_audit(is_compliant);
CREATE INDEX idx_compliance_audit_remediation ON compliance_audit(remediation_required) WHERE remediation_required = true;

CREATE INDEX idx_data_access_timestamp ON data_access_log(access_timestamp DESC);
CREATE INDEX idx_data_access_user ON data_access_log(user_id);
CREATE INDEX idx_data_access_type ON data_access_log(data_type);
CREATE INDEX idx_data_access_classification ON data_access_log(data_classification);
CREATE INDEX idx_data_access_table ON data_access_log(table_name);
CREATE INDEX idx_data_access_sensitive ON data_access_log(is_sensitive_data) WHERE is_sensitive_data = true;

CREATE INDEX idx_transaction_audit_transaction ON transaction_audit(transaction_id);
CREATE INDEX idx_transaction_audit_timestamp ON transaction_audit(transaction_timestamp DESC);
CREATE INDEX idx_transaction_audit_customer ON transaction_audit(customer_id);
CREATE INDEX idx_transaction_audit_account ON transaction_audit(account_id);
CREATE INDEX idx_transaction_audit_type ON transaction_audit(transaction_type);
CREATE INDEX idx_transaction_audit_status ON transaction_audit(transaction_status);

CREATE INDEX idx_api_audit_timestamp ON api_audit(request_timestamp DESC);
CREATE INDEX idx_api_audit_endpoint ON api_audit(api_endpoint);
CREATE INDEX idx_api_audit_user ON api_audit(user_id);
CREATE INDEX idx_api_audit_status ON api_audit(response_status_code);
CREATE INDEX idx_api_audit_client ON api_audit(client_id);
CREATE INDEX idx_api_audit_failed ON api_audit(is_successful) WHERE is_successful = false;

CREATE INDEX idx_config_change_timestamp ON configuration_change(change_timestamp DESC);
CREATE INDEX idx_config_change_component ON configuration_change(component_name);
CREATE INDEX idx_config_change_type ON configuration_change(change_type);
CREATE INDEX idx_config_change_environment ON configuration_change(environment);

CREATE INDEX idx_privileged_access_timestamp ON privileged_access_log(access_timestamp DESC);
CREATE INDEX idx_privileged_access_user ON privileged_access_log(user_id);
CREATE INDEX idx_privileged_access_role ON privileged_access_log(privileged_role);
CREATE INDEX idx_privileged_access_emergency ON privileged_access_log(emergency_access) WHERE emergency_access = true;
CREATE INDEX idx_privileged_access_unreviewed ON privileged_access_log(reviewed) WHERE reviewed = false;

CREATE INDEX idx_audit_report_type ON audit_report(report_type);
CREATE INDEX idx_audit_report_generated ON audit_report(generated_at DESC);
CREATE INDEX idx_audit_report_period ON audit_report(report_period_end DESC);

CREATE INDEX idx_audit_retention_type ON audit_retention_policy(audit_type);
CREATE INDEX idx_audit_retention_active ON audit_retention_policy(is_active) WHERE is_active = true;

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_audit_retention_policy_updated_at BEFORE UPDATE ON audit_retention_policy
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();