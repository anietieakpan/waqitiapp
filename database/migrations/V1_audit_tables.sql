-- Comprehensive Audit Tables for Financial Compliance
-- Supports SOX, PCI-DSS, GDPR requirements

-- Main audit log table with immutable records
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- User context
    user_id VARCHAR(36),
    username VARCHAR(255),
    session_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    
    -- Entity information
    entity_type VARCHAR(100),
    entity_id VARCHAR(255),
    
    -- Financial data
    amount DECIMAL(19, 4),
    currency VARCHAR(3),
    
    -- Details as JSON
    details JSONB,
    
    -- Integrity
    integrity_hash VARCHAR(255) NOT NULL,
    previous_hash VARCHAR(255),
    
    -- Request tracing
    request_id VARCHAR(36),
    service_name VARCHAR(100),
    
    -- Compliance
    compliance_type VARCHAR(50),
    compliance_result VARCHAR(50),
    expires_at TIMESTAMP WITH TIME ZONE, -- For GDPR
    
    -- Performance
    execution_time_ms BIGINT,
    performance_metrics JSONB,
    
    -- Indexes for performance
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for query performance
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp DESC);
CREATE INDEX idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_log_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_severity ON audit_log(severity) WHERE severity IN ('CRITICAL', 'COMPLIANCE');
CREATE INDEX idx_audit_log_amount ON audit_log(amount) WHERE amount IS NOT NULL;
CREATE INDEX idx_audit_log_compliance ON audit_log(compliance_type) WHERE compliance_type IS NOT NULL;

-- Hash chain integrity table
CREATE TABLE IF NOT EXISTS audit_hash_chain (
    id BIGSERIAL PRIMARY KEY,
    timestamp VARCHAR(50) NOT NULL,
    hash_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(timestamp)
);

-- Compliance reports table
CREATE TABLE IF NOT EXISTS compliance_reports (
    report_id VARCHAR(36) PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_by VARCHAR(255) NOT NULL,
    report_data JSONB NOT NULL,
    report_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_compliance_reports_type ON compliance_reports(report_type);
CREATE INDEX idx_compliance_reports_dates ON compliance_reports(start_date, end_date);

-- Financial transaction audit (specialized table for high-volume financial audits)
CREATE TABLE IF NOT EXISTS financial_audit_log (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    source_account_id UUID,
    target_account_id UUID,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    
    -- Risk assessment
    risk_score INTEGER,
    fraud_check_result VARCHAR(50),
    aml_check_result VARCHAR(50),
    
    -- Metadata
    metadata JSONB,
    audit_id VARCHAR(36) REFERENCES audit_log(audit_id),
    
    -- Timestamps
    initiated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(transaction_id)
);

CREATE INDEX idx_financial_audit_transaction ON financial_audit_log(transaction_id);
CREATE INDEX idx_financial_audit_accounts ON financial_audit_log(source_account_id, target_account_id);
CREATE INDEX idx_financial_audit_amount ON financial_audit_log(amount) WHERE amount > 10000;
CREATE INDEX idx_financial_audit_status ON financial_audit_log(status);
CREATE INDEX idx_financial_audit_risk ON financial_audit_log(risk_score) WHERE risk_score > 50;

-- Security event audit (for security-specific events)
CREATE TABLE IF NOT EXISTS security_audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_name VARCHAR(100) NOT NULL,
    target_resource VARCHAR(255),
    success BOOLEAN NOT NULL,
    reason TEXT,
    
    -- Attack detection
    threat_level VARCHAR(20),
    attack_pattern VARCHAR(100),
    
    -- User context
    user_id VARCHAR(36),
    ip_address INET,
    geo_location JSONB,
    
    -- Context
    context JSONB,
    audit_id VARCHAR(36) REFERENCES audit_log(audit_id),
    
    -- Timestamps
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_audit_event ON security_audit_log(event_name);
CREATE INDEX idx_security_audit_user ON security_audit_log(user_id);
CREATE INDEX idx_security_audit_threat ON security_audit_log(threat_level) WHERE threat_level IN ('HIGH', 'CRITICAL');
CREATE INDEX idx_security_audit_failures ON security_audit_log(success) WHERE success = false;

-- Data access audit (for GDPR compliance)
CREATE TABLE IF NOT EXISTS data_access_audit_log (
    id BIGSERIAL PRIMARY KEY,
    access_type VARCHAR(50) NOT NULL, -- READ, WRITE, DELETE, EXPORT
    data_category VARCHAR(50) NOT NULL, -- PII, FINANCIAL, HEALTH, etc.
    data_subject_id VARCHAR(36), -- The person whose data is being accessed
    
    -- Access context
    purpose VARCHAR(255) NOT NULL,
    legal_basis VARCHAR(100), -- For GDPR
    
    -- User performing access
    accessor_id VARCHAR(36) NOT NULL,
    accessor_role VARCHAR(100),
    
    -- Data details
    fields_accessed TEXT[],
    records_count INTEGER,
    
    -- Audit link
    audit_id VARCHAR(36) REFERENCES audit_log(audit_id),
    
    -- Timestamps
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE, -- Data retention
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_data_access_subject ON data_access_audit_log(data_subject_id);
CREATE INDEX idx_data_access_accessor ON data_access_audit_log(accessor_id);
CREATE INDEX idx_data_access_category ON data_access_audit_log(data_category);
CREATE INDEX idx_data_access_type ON data_access_audit_log(access_type);

-- Audit retention policy table
CREATE TABLE IF NOT EXISTS audit_retention_policy (
    id SERIAL PRIMARY KEY,
    audit_type VARCHAR(100) NOT NULL,
    retention_days INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(audit_type)
);

-- Default retention policies
INSERT INTO audit_retention_policy (audit_type, retention_days) VALUES
    ('FINANCIAL', 2555), -- 7 years for financial records
    ('SECURITY', 365),   -- 1 year for security events
    ('COMPLIANCE', 2555), -- 7 years for compliance
    ('DATA_ACCESS', 1095), -- 3 years for GDPR
    ('GENERAL', 90)       -- 90 days for general logs
ON CONFLICT (audit_type) DO NOTHING;

-- Function to automatically set expires_at based on retention policy
CREATE OR REPLACE FUNCTION set_audit_expiry()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.expires_at IS NULL THEN
        NEW.expires_at := NEW.timestamp + INTERVAL '7 years';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_set_expiry
    BEFORE INSERT ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_expiry();

-- Function to clean up expired audit records (for GDPR compliance)
CREATE OR REPLACE FUNCTION cleanup_expired_audits()
RETURNS void AS $$
BEGIN
    -- Archive before deletion
    INSERT INTO archived_audit_log
    SELECT * FROM audit_log 
    WHERE expires_at < CURRENT_TIMESTAMP;
    
    -- Delete expired records
    DELETE FROM audit_log 
    WHERE expires_at < CURRENT_TIMESTAMP;
    
    -- Log cleanup operation
    INSERT INTO audit_log (
        audit_id, event_type, severity, timestamp,
        user_id, username, entity_type, details,
        integrity_hash
    ) VALUES (
        gen_random_uuid()::text,
        'AUDIT_CLEANUP',
        'INFO',
        CURRENT_TIMESTAMP,
        'SYSTEM',
        'SYSTEM',
        'AUDIT',
        jsonb_build_object('records_cleaned', ROW_COUNT()),
        'SYSTEM_GENERATED'
    );
END;
$$ LANGUAGE plpgsql;

-- Archive table for long-term storage
CREATE TABLE IF NOT EXISTS archived_audit_log (
    LIKE audit_log INCLUDING ALL
);

-- Partitioning for performance (partition by month)
-- This is for PostgreSQL 11+
CREATE TABLE IF NOT EXISTS audit_log_partitioned (
    LIKE audit_log INCLUDING ALL
) PARTITION BY RANGE (timestamp);

-- Create partitions for the next 12 months
DO $$
DECLARE
    start_date date := date_trunc('month', CURRENT_DATE);
    partition_date date;
    partition_name text;
BEGIN
    FOR i IN 0..11 LOOP
        partition_date := start_date + (i || ' months')::interval;
        partition_name := 'audit_log_' || to_char(partition_date, 'YYYY_MM');
        
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_log_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            partition_date,
            partition_date + interval '1 month'
        );
    END LOOP;
END $$;

-- Grant appropriate permissions
GRANT SELECT ON audit_log TO audit_reader;
GRANT INSERT ON audit_log TO audit_writer;
GRANT SELECT ON compliance_reports TO compliance_officer;

-- Comments for documentation
COMMENT ON TABLE audit_log IS 'Immutable audit log for all system events - SOX and PCI-DSS compliant';
COMMENT ON TABLE financial_audit_log IS 'Specialized audit log for financial transactions';
COMMENT ON TABLE security_audit_log IS 'Security event audit log for threat detection';
COMMENT ON TABLE data_access_audit_log IS 'GDPR-compliant data access audit log';
COMMENT ON TABLE compliance_reports IS 'Generated compliance reports with integrity verification';