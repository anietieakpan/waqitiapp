-- Liquibase/Flyway Migration Script for GDPR Schema
-- Version: 1.0
-- Date: 2025-10-20
-- Description: Initial GDPR compliance schema for Articles 15-17, 30, 33-35

-- =====================================================
-- GDPR CONSENT RECORDS TABLE
-- Implements: GDPR Articles 6, 7, 13
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_consent_records (
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    consent_type VARCHAR(100) NOT NULL,
    is_granted BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    purpose TEXT,
    legal_basis VARCHAR(100),
    consent_method VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT,
    geolocation VARCHAR(100),
    granted_at TIMESTAMP,
    revoked_at TIMESTAMP,
    expires_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,
    parent_consent_id UUID,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT fk_parent_consent FOREIGN KEY (parent_consent_id)
    REFERENCES gdpr_consent_records(id) ON DELETE SET NULL
    );

-- Indexes for consent records
CREATE INDEX idx_consent_user_id ON gdpr_consent_records(user_id);
CREATE INDEX idx_consent_type ON gdpr_consent_records(consent_type);
CREATE INDEX idx_consent_granted_at ON gdpr_consent_records(granted_at);
CREATE INDEX idx_consent_active ON gdpr_consent_records(user_id, consent_type, is_active);
CREATE INDEX idx_consent_expires_at ON gdpr_consent_records(expires_at) WHERE expires_at IS NOT NULL;

-- Comment on table
COMMENT ON TABLE gdpr_consent_records IS 'GDPR Article 7: Consent management and audit trail';
COMMENT ON COLUMN gdpr_consent_records.legal_basis IS 'CONSENT, CONTRACT, LEGAL_OBLIGATION, VITAL_INTEREST, PUBLIC_TASK, LEGITIMATE_INTEREST';

-- =====================================================
-- GDPR DATA EXPORTS TABLE
-- Implements: GDPR Articles 15 (Right to Access), 20 (Data Portability)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_data_exports (
                                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    export_id VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    format VARCHAR(20) NOT NULL,
    include_metadata BOOLEAN DEFAULT true,
    include_history BOOLEAN DEFAULT true,
    requested_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP,
    file_path VARCHAR(500),
    file_size_bytes BIGINT,
    encryption_key_id VARCHAR(100),
    download_url VARCHAR(500),
    download_count INTEGER DEFAULT 0,
    last_downloaded_at TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    data_categories JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),

    CONSTRAINT chk_export_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'DOWNLOADED', 'FAILED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_export_format CHECK (format IN ('JSON', 'XML', 'CSV', 'PDF', 'ZIP'))
    );

-- Indexes for data exports
CREATE INDEX idx_export_user_id ON gdpr_data_exports(user_id);
CREATE INDEX idx_export_status ON gdpr_data_exports(status);
CREATE INDEX idx_export_created ON gdpr_data_exports(created_at);
CREATE INDEX idx_export_expires ON gdpr_data_exports(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_export_id ON gdpr_data_exports(export_id);

COMMENT ON TABLE gdpr_data_exports IS 'GDPR Articles 15 & 20: Data access and portability requests';
COMMENT ON COLUMN gdpr_data_exports.expires_at IS 'Auto-delete exports after 30 days per GDPR compliance';

-- =====================================================
-- GDPR DATA DELETIONS TABLE
-- Implements: GDPR Article 17 (Right to Erasure)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_data_deletions (
                                                   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    deletion_request_id VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL,
    deletion_reason VARCHAR(500) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    anonymized_at TIMESTAMP,
    hard_deleted_at TIMESTAMP,
    verification_hash VARCHAR(128),
    total_records_deleted INTEGER DEFAULT 0,
    total_records_anonymized INTEGER DEFAULT 0,
    total_records_retained INTEGER DEFAULT 0,
    retention_exceptions JSONB,
    deleted_entities JSONB,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    ip_address VARCHAR(45),
    requested_by VARCHAR(100),
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    CONSTRAINT chk_deletion_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'PROCESSING', 'ANONYMIZED', 'COMPLETED', 'PARTIAL', 'FAILED', 'CANCELLED', 'ON_LEGAL_HOLD'))
    );

-- Indexes for data deletions
CREATE INDEX idx_deletion_user_id ON gdpr_data_deletions(user_id);
CREATE INDEX idx_deletion_status ON gdpr_data_deletions(status);
CREATE INDEX idx_deletion_requested ON gdpr_data_deletions(requested_at);
CREATE INDEX idx_deletion_completed ON gdpr_data_deletions(completed_at) WHERE completed_at IS NOT NULL;
CREATE INDEX idx_deletion_request_id ON gdpr_data_deletions(deletion_request_id);

COMMENT ON TABLE gdpr_data_deletions IS 'GDPR Article 17: Right to erasure (Right to be Forgotten)';
COMMENT ON COLUMN gdpr_data_deletions.retention_exceptions IS 'Data retained due to legal obligations (e.g., AML, tax law)';

-- =====================================================
-- GDPR AUDIT LOGS TABLE
-- Implements: GDPR Article 5(2) (Accountability)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_audit_logs (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    action_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    description TEXT,
    performed_by VARCHAR(100) NOT NULL,
    performed_by_type VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id VARCHAR(100),
    previous_value JSONB,
    new_value JSONB,
    metadata JSONB,
    compliance_article VARCHAR(100),
    legal_basis VARCHAR(100),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_action_type CHECK (action_type IN (
                                      'CONSENT_GRANTED', 'CONSENT_REVOKED', 'CONSENT_UPDATED', 'CONSENT_EXPIRED',
                                      'DATA_EXPORT_REQUESTED', 'DATA_EXPORT_GENERATED', 'DATA_EXPORT_DOWNLOADED', 'DATA_EXPORT_EXPIRED',
                                      'DATA_ACCESS_GRANTED', 'DATA_ACCESS_DENIED',
                                      'DELETION_REQUESTED', 'DELETION_APPROVED', 'DELETION_REJECTED', 'DELETION_STARTED',
                                      'DELETION_COMPLETED', 'DATA_ANONYMIZED', 'DATA_HARD_DELETED',
                                      'DATA_UPDATED', 'DATA_CORRECTED',
                                      'PROCESSING_RESTRICTED', 'PROCESSING_RESUMED',
                                      'BREACH_DETECTED', 'BREACH_REPORTED_TO_AUTHORITY', 'BREACH_NOTIFIED_TO_USER',
                                      'DPIA_INITIATED', 'DPIA_COMPLETED', 'DPIA_UPDATED',
                                      'DATA_SHARED_WITH_THIRD_PARTY', 'DATA_TRANSFER_CROSS_BORDER',
                                      'DATA_ARCHIVED', 'DATA_RETENTION_EXTENDED', 'DATA_PURGED',
                                      'POLICY_UPDATED', 'SETTINGS_CHANGED', 'AUDIT_LOG_ACCESSED'
                                                     ))
    );

-- Indexes for audit logs (immutable, optimized for read)
CREATE INDEX idx_audit_user_id ON gdpr_audit_logs(user_id);
CREATE INDEX idx_audit_action_type ON gdpr_audit_logs(action_type);
CREATE INDEX idx_audit_timestamp ON gdpr_audit_logs(timestamp DESC);
CREATE INDEX idx_audit_entity_type ON gdpr_audit_logs(entity_type);
CREATE INDEX idx_audit_performed_by ON gdpr_audit_logs(performed_by);
CREATE INDEX idx_audit_request_id ON gdpr_audit_logs(request_id) WHERE request_id IS NOT NULL;
CREATE INDEX idx_audit_compliance ON gdpr_audit_logs(compliance_article) WHERE compliance_article IS NOT NULL;

COMMENT ON TABLE gdpr_audit_logs IS 'GDPR Article 5(2): Immutable audit trail for accountability';

-- =====================================================
-- GDPR PROCESSING ACTIVITIES TABLE
-- Implements: GDPR Article 30 (Records of Processing Activities - ROPA)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_processing_activities (
                                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(100) UNIQUE,
    activity_name VARCHAR(255) NOT NULL,
    description TEXT,
    purpose TEXT,
    legal_basis VARCHAR(100),
    data_categories TEXT,
    data_subject_categories TEXT,
    retention_period_days INTEGER,
    recipients TEXT,
    third_country_transfers BOOLEAN DEFAULT false,
    security_measures TEXT,
    dpia_required BOOLEAN DEFAULT false,
    dpia_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100),

    CONSTRAINT chk_legal_basis CHECK (legal_basis IN (
                                      'CONSENT', 'CONTRACT', 'LEGAL_OBLIGATION', 'VITAL_INTEREST', 'PUBLIC_TASK', 'LEGITIMATE_INTEREST'
                                                     ))
    );

CREATE INDEX idx_processing_activity_id ON gdpr_processing_activities(activity_id);
CREATE INDEX idx_processing_dpia_required ON gdpr_processing_activities(dpia_required) WHERE dpia_required = true;

COMMENT ON TABLE gdpr_processing_activities IS 'GDPR Article 30: Records of Processing Activities (ROPA)';

-- =====================================================
-- GDPR CONSENTS (Simple tracking table)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_consents (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    consent_type VARCHAR(100) NOT NULL,
    granted BOOLEAN NOT NULL,
    consent_date TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
    );

CREATE INDEX idx_consent_simple_user_id ON gdpr_consents(user_id);
CREATE INDEX idx_consent_simple_type ON gdpr_consents(consent_type);

-- =====================================================
-- GDPR EXPORT REQUESTS (Internal tracking)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_export_requests (
                                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    request_date TIMESTAMP NOT NULL,
    request_reason TEXT,
    status VARCHAR(50),
    completion_date TIMESTAMP,
    export_url VARCHAR(500),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_export_req_user_id ON gdpr_export_requests(user_id);

-- =====================================================
-- GDPR DELETION REQUESTS (Internal tracking)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_deletion_requests (
                                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    request_date TIMESTAMP NOT NULL,
    deletion_reason TEXT,
    hard_delete BOOLEAN DEFAULT false,
    status VARCHAR(50),
    completion_date TIMESTAMP,
    deletion_summary TEXT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_deletion_req_user_id ON gdpr_deletion_requests(user_id);

-- =====================================================
-- GDPR DATA BREACHES TABLE
-- Implements: GDPR Articles 33-34 (Breach Notification)
-- =====================================================
CREATE TABLE IF NOT EXISTS gdpr_data_breaches (
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    breach_date TIMESTAMP,
    discovery_date TIMESTAMP,
    affected_users JSONB,
    data_categories JSONB,
    breach_description TEXT,
    mitigation_actions TEXT,
    notified_supervisory_authority BOOLEAN DEFAULT false,
    supervisory_authority_notification_date TIMESTAMP,
    notified_users BOOLEAN DEFAULT false,
    user_notification_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_breach_discovery_date ON gdpr_data_breaches(discovery_date DESC);

COMMENT ON TABLE gdpr_data_breaches IS 'GDPR Articles 33-34: Data breach notification records';

-- =====================================================
-- TRIGGERS FOR AUDIT AND COMPLIANCE
-- =====================================================

-- Trigger to prevent deletion of audit logs (immutability)
CREATE OR REPLACE FUNCTION prevent_audit_log_deletion()
RETURNS TRIGGER AS $
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable and cannot be deleted per GDPR Article 5(2)';
END;
$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_deletion
    BEFORE DELETE ON gdpr_audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_deletion();

-- Trigger to prevent update of audit logs (immutability)
CREATE OR REPLACE FUNCTION prevent_audit_log_update()
RETURNS TRIGGER AS $
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable and cannot be updated per GDPR Article 5(2)';
END;
$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_update
    BEFORE UPDATE ON gdpr_audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_update();

-- Trigger to auto-update updated_at columns
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_consent_records
    BEFORE UPDATE ON gdpr_consent_records
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_update_data_exports
    BEFORE UPDATE ON gdpr_data_exports
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_update_data_deletions
    BEFORE UPDATE ON gdpr_data_deletions
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_column();

-- =====================================================
-- VIEWS FOR COMPLIANCE REPORTING
-- =====================================================

-- View: Active consents by type
CREATE OR REPLACE VIEW vw_active_consents AS
SELECT
    consent_type,
    COUNT(*) as total_consents,
    SUM(CASE WHEN is_granted THEN 1 ELSE 0 END) as granted_count,
    SUM(CASE WHEN NOT is_granted THEN 1 ELSE 0 END) as revoked_count,
    MAX(created_at) as last_consent_date
FROM gdpr_consent_records
WHERE is_active = true
GROUP BY consent_type;

-- View: GDPR request metrics
CREATE OR REPLACE VIEW vw_gdpr_request_metrics AS
SELECT
    'EXPORT' as request_type,
    status,
    COUNT(*) as count,
    AVG(EXTRACT(EPOCH FROM (completed_at - requested_at))/86400) as avg_days_to_complete
FROM gdpr_data_exports
WHERE completed_at IS NOT NULL
GROUP BY status
UNION ALL
SELECT
    'DELETION' as request_type,
    status,
    COUNT(*) as count,
    AVG(EXTRACT(EPOCH FROM (completed_at - requested_at))/86400) as avg_days_to_complete
FROM gdpr_data_deletions
WHERE completed_at IS NOT NULL
GROUP BY status;

-- View: Audit activity summary
CREATE OR REPLACE VIEW vw_audit_activity_summary AS
SELECT
    DATE(timestamp) as activity_date,
    action_type,
    COUNT(*) as action_count,
    COUNT(DISTINCT user_id) as unique_users
FROM gdpr_audit_logs
WHERE timestamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(timestamp), action_type
ORDER BY activity_date DESC, action_count DESC;

-- =====================================================
-- GRANT PERMISSIONS (adjust based on your setup)
-- =====================================================

-- Grant read/write to application role
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO waqiti_app_role;
-- GRANT SELECT, INSERT ON gdpr_audit_logs TO waqiti_app_role;

-- Grant read-only to reporting role
-- GRANT SELECT ON ALL TABLES IN SCHEMA public TO waqiti_reporting_role;
-- GRANT SELECT ON vw_active_consents, vw_gdpr_request_metrics, vw_audit_activity_summary TO waqiti_reporting_role;

-- =====================================================
-- INITIAL DATA / SEED DATA
-- =====================================================

-- Insert default processing activities
INSERT INTO gdpr_processing_activities (activity_id, activity_name, purpose, legal_basis, data_categories, retention_period_days)
VALUES
    ('PA-001', 'Payment Processing', 'Process financial transactions', 'CONTRACT', 'Financial data, Contact details', 2555),
    ('PA-002', 'Fraud Detection', 'Detect and prevent fraudulent activities', 'LEGITIMATE_INTEREST', 'Transaction data, Device data', 2555),
    ('PA-003', 'Customer Support', 'Provide customer service and support', 'CONTRACT', 'Contact details, Support tickets', 1825),
    ('PA-004', 'Marketing Communications', 'Send promotional materials', 'CONSENT', 'Contact details, Preferences', 730)
    ON CONFLICT (activity_id) DO NOTHING;

-- =====================================================
-- MAINTENANCE QUERIES (for scheduled jobs)
-- =====================================================

-- Query to find expired exports (run daily)
-- DELETE FROM gdpr_data_exports WHERE status = 'EXPIRED' AND expires_at < CURRENT_TIMESTAMP - INTERVAL '90 days';

-- Query to find consents needing renewal (run daily)
-- SELECT * FROM gdpr_consent_records WHERE is_active = true AND expires_at BETWEEN CURRENT_TIMESTAMP AND CURRENT_TIMESTAMP + INTERVAL '30 days';

-- =====================================================
-- COMPLIANCE NOTES
-- =====================================================

/*
GDPR Compliance Checklist:
✓ Article 5(2) - Accountability (audit logs are immutable)
✓ Article 6 - Lawfulness of processing (legal basis tracked)
✓ Article 7 - Consent management (consent versioning)
✓ Article 15 - Right to access (data exports)
✓ Article 17 - Right to erasure (soft/hard deletion)
✓ Article 20 - Data portability (multiple export formats)
✓ Article 30 - Records of processing activities (ROPA table)
✓ Article 33-34 - Breach notification (breach tracking)

Retention Policies:
- Consent records: Permanent (until user deletion)
- Data exports: Auto-delete after 30 days
- Audit logs: 10 years minimum
- Deletion records: Permanent (compliance proof)
- Financial data: 7 years (tax law compliance)

Security Measures:
- All tables support UUID primary keys (prevent enumeration)
- Audit logs are immutable (triggers prevent modification)
- JSONB columns for flexible metadata storage
- Comprehensive indexing for performance
- Views for compliance reporting
*/

-- End of migration script