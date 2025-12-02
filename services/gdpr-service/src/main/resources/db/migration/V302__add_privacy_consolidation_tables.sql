-- Privacy Service Consolidation Migration
-- Adds tables for comprehensive privacy management including:
-- - Data Breaches (Articles 33-34)
-- - Privacy Audit Events (Article 5(2), 24)
-- - Data Privacy Impact Assessments (Article 35)

-- =============================================================================
-- DATA BREACHES (GDPR Articles 33-34)
-- =============================================================================

CREATE TABLE data_breaches (
    id VARCHAR(36) PRIMARY KEY,
    breach_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    discovered_at TIMESTAMP NOT NULL,
    breach_occurred_at TIMESTAMP,
    reported_by VARCHAR(255) NOT NULL,
    affected_user_count INTEGER,
    status VARCHAR(30) NOT NULL,

    -- Risk Assessment (Embedded)
    risk_level VARCHAR(20),
    risk_score INTEGER,
    likelihood_score INTEGER,
    impact_score INTEGER,
    risk_factors TEXT,
    mitigating_factors TEXT,
    residual_risk_level VARCHAR(20),
    assessed_at TIMESTAMP,
    assessed_by VARCHAR(255),
    assessment_methodology VARCHAR(100),
    requires_dpia BOOLEAN,
    dpia_reference VARCHAR(255),
    review_date TIMESTAMP,

    likely_consequences TEXT,
    mitigation_measures TEXT,

    -- Regulatory Notification (Article 33)
    requires_regulatory_notification BOOLEAN DEFAULT TRUE,
    regulatory_notification_deadline TIMESTAMP,
    regulatory_notified_at TIMESTAMP,
    regulatory_notification_reference VARCHAR(255),
    regulatory_exemption_reason TEXT,

    -- User Notification (Article 34)
    requires_user_notification BOOLEAN DEFAULT FALSE,
    user_notification_deadline TIMESTAMP,
    users_notified_at TIMESTAMP,
    user_notification_count INTEGER,
    user_notification_method VARCHAR(100),
    user_notification_exemption_reason TEXT,

    -- Technical Details
    attack_vector VARCHAR(100),
    vulnerability_exploited TEXT,
    systems_affected TEXT,
    data_compromised TEXT,

    -- Containment and Recovery
    contained_at TIMESTAMP,
    containment_actions TEXT,
    recovery_completed_at TIMESTAMP,
    recovery_actions TEXT,

    -- Investigation
    investigation_status VARCHAR(50),
    root_cause TEXT,
    investigation_completed_at TIMESTAMP,
    investigation_report_url VARCHAR(500),

    -- Legal and Compliance
    dpo_notified_at TIMESTAMP,
    legal_team_notified_at TIMESTAMP,
    insurance_notified_at TIMESTAMP,
    law_enforcement_notified_at TIMESTAMP,
    law_enforcement_reference VARCHAR(255),

    -- Follow-up Actions
    lessons_learned TEXT,
    preventive_measures TEXT,
    policy_changes_required TEXT,

    -- Metadata
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    closed_at TIMESTAMP,
    version BIGINT DEFAULT 0,

    CONSTRAINT chk_breach_type CHECK (breach_type IN (
        'CONFIDENTIALITY_BREACH', 'AVAILABILITY_BREACH', 'INTEGRITY_BREACH',
        'RANSOMWARE', 'PHISHING', 'MALWARE', 'INSIDER_THREAT',
        'PHYSICAL_THEFT', 'PHYSICAL_LOSS', 'CODE_INJECTION',
        'DDOS_ATTACK', 'MITM_ATTACK', 'BRUTE_FORCE', 'CREDENTIAL_ATTACK',
        'ZERO_DAY_EXPLOIT', 'MISCONFIGURATION', 'SUPPLY_CHAIN_BREACH',
        'API_VULNERABILITY', 'CLOUD_MISCONFIGURATION', 'OTHER'
    )),
    CONSTRAINT chk_breach_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_breach_status CHECK (status IN (
        'REPORTED', 'INVESTIGATING', 'CONTAINED', 'RECOVERED', 'RESOLVED', 'FALSE_POSITIVE', 'CLOSED'
    )),
    CONSTRAINT chk_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Breach affected data categories (many-to-many)
CREATE TABLE breach_affected_data_categories (
    breach_id VARCHAR(36) NOT NULL,
    data_category VARCHAR(100) NOT NULL,
    FOREIGN KEY (breach_id) REFERENCES data_breaches(id) ON DELETE CASCADE
);

-- Indexes for data_breaches
CREATE INDEX idx_breach_discovered_at ON data_breaches(discovered_at);
CREATE INDEX idx_breach_status ON data_breaches(status);
CREATE INDEX idx_breach_severity ON data_breaches(severity);
CREATE INDEX idx_breach_regulatory_deadline ON data_breaches(regulatory_notification_deadline);
CREATE INDEX idx_breach_user_deadline ON data_breaches(user_notification_deadline);
CREATE INDEX idx_breach_risk_level ON data_breaches(risk_level);

-- =============================================================================
-- PRIVACY AUDIT EVENTS (GDPR Article 5(2), 24 - Accountability)
-- =============================================================================

CREATE TABLE privacy_audit_events (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    performed_by VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    action VARCHAR(50),
    description TEXT,
    correlation_id VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    session_id VARCHAR(255),
    result VARCHAR(20),
    error_message TEXT,
    processing_time_ms BIGINT,

    -- Data Subject Rights Tracking
    privacy_right VARCHAR(50),
    legal_basis VARCHAR(100),

    -- Compliance Tracking
    gdpr_article VARCHAR(50),
    retention_period_days INTEGER,
    expires_at TIMESTAMP,

    -- Metadata
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT chk_audit_action CHECK (action IN (
        'REQUEST_CREATED', 'REQUEST_VERIFIED', 'REQUEST_APPROVED', 'REQUEST_REJECTED',
        'REQUEST_COMPLETED', 'REQUEST_CANCELLED', 'EXPORT_INITIATED', 'EXPORT_DATA_COLLECTED',
        'EXPORT_ENCRYPTED', 'EXPORT_STORED', 'EXPORT_DOWNLOADED', 'EXPORT_EXPIRED',
        'EXPORT_DELETED', 'ERASURE_INITIATED', 'ERASURE_VERIFIED', 'ERASURE_EXECUTED',
        'ERASURE_COMPLETED', 'ERASURE_FAILED', 'CONSENT_GRANTED', 'CONSENT_WITHDRAWN',
        'CONSENT_UPDATED', 'CONSENT_EXPIRED', 'BREACH_REPORTED', 'BREACH_INVESTIGATED',
        'BREACH_CONTAINED', 'BREACH_RESOLVED', 'BREACH_NOTIFIED_REGULATORY',
        'BREACH_NOTIFIED_USERS', 'DPIA_INITIATED', 'DPIA_COMPLETED', 'DPIA_REVIEWED',
        'DPIA_UPDATED', 'DATA_ACCESSED', 'DATA_MODIFIED', 'DATA_DELETED', 'DATA_SHARED',
        'CONFIGURATION_CHANGED', 'POLICY_UPDATED', 'RETENTION_APPLIED', 'CLEANUP_EXECUTED',
        'ACCESS_GRANTED', 'ACCESS_DENIED', 'AUTHENTICATION_FAILED',
        'AUTHORIZATION_FAILED', 'UNKNOWN'
    )),
    CONSTRAINT chk_audit_result CHECK (result IN ('SUCCESS', 'FAILURE', 'PARTIAL', 'DENIED', 'PENDING')),
    CONSTRAINT chk_privacy_right CHECK (privacy_right IN (
        'ACCESS', 'PORTABILITY', 'RECTIFICATION', 'ERASURE', 'RESTRICTION',
        'OBJECTION', 'AUTOMATED_DECISION_OBJECTION', 'INFORMATION',
        'COMPLAINT', 'JUDICIAL_REMEDY'
    ))
);

-- Privacy audit details (key-value pairs)
CREATE TABLE privacy_audit_details (
    audit_event_id VARCHAR(36) NOT NULL,
    detail_key VARCHAR(100) NOT NULL,
    detail_value TEXT,
    FOREIGN KEY (audit_event_id) REFERENCES privacy_audit_events(id) ON DELETE CASCADE
);

-- Indexes for privacy_audit_events
CREATE INDEX idx_audit_event_type ON privacy_audit_events(event_type);
CREATE INDEX idx_audit_entity_type ON privacy_audit_events(entity_type);
CREATE INDEX idx_audit_entity_id ON privacy_audit_events(entity_id);
CREATE INDEX idx_audit_user_id ON privacy_audit_events(user_id);
CREATE INDEX idx_audit_timestamp ON privacy_audit_events(timestamp);
CREATE INDEX idx_audit_correlation_id ON privacy_audit_events(correlation_id);
CREATE INDEX idx_audit_action ON privacy_audit_events(action);
CREATE INDEX idx_audit_privacy_right ON privacy_audit_events(privacy_right);
CREATE INDEX idx_audit_expires_at ON privacy_audit_events(expires_at);

-- =============================================================================
-- DATA PRIVACY IMPACT ASSESSMENTS (GDPR Article 35)
-- =============================================================================

CREATE TABLE data_privacy_impact_assessments (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    processing_activity_id VARCHAR(36),
    status VARCHAR(30) NOT NULL,

    -- Processing Description
    processing_purpose TEXT,
    processing_description TEXT,
    estimated_subjects_count BIGINT,
    involves_special_category_data BOOLEAN,
    involves_automated_decisions BOOLEAN,
    involves_profiling BOOLEAN,
    large_scale_processing BOOLEAN,
    systematic_monitoring BOOLEAN,

    -- Necessity and Proportionality
    necessity_assessment TEXT,
    proportionality_assessment TEXT,
    legal_basis VARCHAR(100),
    legitimate_interests TEXT,

    -- Risk Assessment (Embedded)
    risk_level VARCHAR(20),
    risk_score INTEGER,
    likelihood_score INTEGER,
    impact_score INTEGER,
    risk_factors TEXT,
    mitigating_factors TEXT,
    residual_risk_level VARCHAR(20),
    assessed_at TIMESTAMP,
    assessed_by VARCHAR(255),
    assessment_methodology VARCHAR(100),
    requires_dpia BOOLEAN,
    dpia_reference VARCHAR(255),
    review_date TIMESTAMP,

    overall_risk_level VARCHAR(20),
    identified_risks TEXT,
    risk_mitigation_measures TEXT,
    residual_risks TEXT,

    -- Security Measures
    technical_measures TEXT,
    organizational_measures TEXT,
    data_minimization_applied BOOLEAN,
    pseudonymization_applied BOOLEAN,
    encryption_applied BOOLEAN,

    -- Consultation
    dpo_consulted BOOLEAN,
    dpo_consultation_date TIMESTAMP,
    dpo_opinion TEXT,
    subjects_consulted BOOLEAN,
    subject_consultation_summary TEXT,
    external_consultation_required BOOLEAN,
    supervisory_authority_consulted BOOLEAN,
    supervisory_authority_consultation_date TIMESTAMP,
    supervisory_authority_reference VARCHAR(255),

    -- Recommendations and Actions
    recommendations TEXT,
    required_actions TEXT,
    actions_implemented BOOLEAN,
    actions_implementation_date TIMESTAMP,

    -- Approval and Review
    prepared_by VARCHAR(255),
    preparation_date TIMESTAMP,
    reviewed_by VARCHAR(255),
    review_date_actual TIMESTAMP,
    approved_by VARCHAR(255),
    approval_date TIMESTAMP,
    next_review_date TIMESTAMP,
    review_frequency_months INTEGER,

    -- Documentation
    methodology_used VARCHAR(255),
    standards_applied TEXT,
    document_url VARCHAR(500),
    attachment_references TEXT,

    -- Conclusion
    conclusion VARCHAR(50),
    conclusion_notes TEXT,
    processing_may_proceed BOOLEAN,
    conditions_for_processing TEXT,

    -- Metadata
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    completed_at TIMESTAMP,
    version BIGINT DEFAULT 0,

    CONSTRAINT chk_dpia_status CHECK (status IN (
        'INITIATED', 'DRAFT', 'UNDER_REVIEW', 'DPO_CONSULTATION',
        'SUBJECT_CONSULTATION', 'AUTHORITY_CONSULTATION', 'COMPLETED',
        'APPROVED', 'REJECTED', 'UNDER_PERIODIC_REVIEW', 'SUPERSEDED', 'ARCHIVED'
    )),
    CONSTRAINT chk_dpia_risk_level CHECK (overall_risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_dpia_conclusion CHECK (conclusion IN (
        'PROCEED_WITHOUT_CONDITIONS', 'PROCEED_WITH_CONDITIONS',
        'REQUIRES_ADDITIONAL_SAFEGUARDS', 'REQUIRES_AUTHORITY_CONSULTATION',
        'DO_NOT_PROCEED', 'REQUIRES_REDESIGN', 'INCONCLUSIVE'
    ))
);

-- DPIA data categories (many-to-many)
CREATE TABLE dpia_data_categories (
    dpia_id VARCHAR(36) NOT NULL,
    data_category VARCHAR(100) NOT NULL,
    FOREIGN KEY (dpia_id) REFERENCES data_privacy_impact_assessments(id) ON DELETE CASCADE
);

-- DPIA data subjects (many-to-many)
CREATE TABLE dpia_data_subjects (
    dpia_id VARCHAR(36) NOT NULL,
    subject_type VARCHAR(100) NOT NULL,
    FOREIGN KEY (dpia_id) REFERENCES data_privacy_impact_assessments(id) ON DELETE CASCADE
);

-- Indexes for data_privacy_impact_assessments
CREATE INDEX idx_dpia_status ON data_privacy_impact_assessments(status);
CREATE INDEX idx_dpia_risk_level ON data_privacy_impact_assessments(overall_risk_level);
CREATE INDEX idx_dpia_processing_activity ON data_privacy_impact_assessments(processing_activity_id);
CREATE INDEX idx_dpia_review_date ON data_privacy_impact_assessments(next_review_date);
CREATE INDEX idx_dpia_conclusion ON data_privacy_impact_assessments(conclusion);

-- =============================================================================
-- COMMENTS
-- =============================================================================

-- Data Breaches
COMMENT ON TABLE data_breaches IS 'GDPR Articles 33-34: Data breach records for notification and tracking';
COMMENT ON COLUMN data_breaches.regulatory_notification_deadline IS 'Article 33: 72 hours from discovery';
COMMENT ON COLUMN data_breaches.user_notification_deadline IS 'Article 34: Without undue delay if high risk';

-- Privacy Audit Events
COMMENT ON TABLE privacy_audit_events IS 'GDPR Article 5(2) & 24: Accountability and audit trail for privacy operations';
COMMENT ON COLUMN privacy_audit_events.retention_period_days IS 'Default 7 years (2555 days) for GDPR compliance';

-- Data Privacy Impact Assessments
COMMENT ON TABLE data_privacy_impact_assessments IS 'GDPR Article 35: DPIAs required for high-risk processing';
COMMENT ON COLUMN data_privacy_impact_assessments.external_consultation_required IS 'Article 36: Consultation with supervisory authority if high risk cannot be mitigated';

-- =============================================================================
-- INITIAL DATA
-- =============================================================================

-- Insert sample DPIA methodology standards
INSERT INTO data_privacy_impact_assessments (
    id, title, description, status, created_at, updated_at
) VALUES (
    gen_random_uuid()::text,
    'DPIA Methodology Template',
    'Standard DPIA methodology template based on ICO, CNIL, and EDPB guidelines',
    'ARCHIVED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT DO NOTHING;
