-- Data Subject Requests table
CREATE TABLE data_subject_requests (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    request_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    deadline TIMESTAMP NOT NULL,
    verification_token VARCHAR(500),
    verified_at TIMESTAMP,
    processed_by VARCHAR(255),
    export_format VARCHAR(20),
    export_url VARCHAR(500),
    export_expires_at TIMESTAMP,
    rejection_reason VARCHAR(1000),
    notes TEXT,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT chk_request_type CHECK (request_type IN ('ACCESS', 'PORTABILITY', 'RECTIFICATION', 'ERASURE', 'RESTRICTION', 'OBJECTION')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT chk_export_format CHECK (export_format IN ('JSON', 'CSV', 'PDF', 'EXCEL'))
);

CREATE INDEX idx_requests_user_id ON data_subject_requests(user_id);
CREATE INDEX idx_requests_status ON data_subject_requests(status);
CREATE INDEX idx_requests_deadline ON data_subject_requests(deadline);

-- Request data categories
CREATE TABLE request_data_categories (
    request_id VARCHAR(36) NOT NULL,
    category VARCHAR(100) NOT NULL,
    FOREIGN KEY (request_id) REFERENCES data_subject_requests(id) ON DELETE CASCADE
);

CREATE INDEX idx_request_categories_request_id ON request_data_categories(request_id);

-- Request audit logs
CREATE TABLE request_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    performed_by VARCHAR(255) NOT NULL,
    performed_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    version BIGINT DEFAULT 0,
    
    FOREIGN KEY (request_id) REFERENCES data_subject_requests(id) ON DELETE CASCADE
);

CREATE INDEX idx_audit_logs_request_id ON request_audit_logs(request_id);
CREATE INDEX idx_audit_logs_performed_at ON request_audit_logs(performed_at);

-- Consent records table
CREATE TABLE consent_records (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version VARCHAR(20) NOT NULL,
    granted_at TIMESTAMP,
    withdrawn_at TIMESTAMP,
    expires_at TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    collection_method VARCHAR(50) NOT NULL,
    consent_text TEXT,
    lawful_basis VARCHAR(50),
    third_parties VARCHAR(1000),
    data_retention_days INTEGER,
    is_minor BOOLEAN DEFAULT FALSE,
    parental_consent_id VARCHAR(36),
    version_number BIGINT DEFAULT 0,
    
    CONSTRAINT chk_consent_purpose CHECK (purpose IN ('ESSENTIAL_SERVICE', 'MARKETING_EMAILS', 'PROMOTIONAL_SMS', 
        'PUSH_NOTIFICATIONS', 'ANALYTICS', 'PERSONALIZATION', 'THIRD_PARTY_SHARING', 'PROFILING', 
        'AUTOMATED_DECISIONS', 'LOCATION_TRACKING', 'BIOMETRIC_DATA', 'CROSS_BORDER_TRANSFER')),
    CONSTRAINT chk_consent_status CHECK (status IN ('GRANTED', 'WITHDRAWN', 'EXPIRED', 'PENDING')),
    CONSTRAINT chk_collection_method CHECK (collection_method IN ('EXPLICIT_CHECKBOX', 'IMPLICIT_SIGNUP', 
        'EMAIL_CONFIRMATION', 'IN_APP_PROMPT', 'SETTINGS_PAGE', 'CUSTOMER_SUPPORT', 'IMPORTED')),
    CONSTRAINT chk_lawful_basis CHECK (lawful_basis IN ('CONSENT', 'CONTRACT', 'LEGAL_OBLIGATION', 
        'VITAL_INTERESTS', 'PUBLIC_TASK', 'LEGITIMATE_INTERESTS'))
);

CREATE INDEX idx_consent_user_id ON consent_records(user_id);
CREATE INDEX idx_consent_purpose ON consent_records(purpose);
CREATE INDEX idx_consent_status ON consent_records(status);
CREATE INDEX idx_consent_expires_at ON consent_records(expires_at);
CREATE UNIQUE INDEX idx_consent_user_purpose_active ON consent_records(user_id, purpose) 
    WHERE status = 'GRANTED';

-- Data processing activities
CREATE TABLE data_processing_activities (
    id VARCHAR(36) PRIMARY KEY,
    activity_name VARCHAR(255) NOT NULL,
    description TEXT,
    processing_purpose VARCHAR(500) NOT NULL,
    lawful_basis VARCHAR(50),
    data_controller VARCHAR(255),
    data_processor VARCHAR(255),
    retention_period VARCHAR(255),
    security_measures TEXT,
    third_country_transfers BOOLEAN DEFAULT FALSE,
    transfer_safeguards TEXT,
    is_high_risk BOOLEAN DEFAULT FALSE,
    dpia_required BOOLEAN DEFAULT FALSE,
    dpia_completed BOOLEAN DEFAULT FALSE,
    dpia_reference VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    next_review_date TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT chk_activity_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'DISCONTINUED'))
);

-- Activity data categories
CREATE TABLE activity_data_categories (
    activity_id VARCHAR(36) NOT NULL,
    category VARCHAR(100) NOT NULL,
    FOREIGN KEY (activity_id) REFERENCES data_processing_activities(id) ON DELETE CASCADE
);

-- Activity data subjects
CREATE TABLE activity_data_subjects (
    activity_id VARCHAR(36) NOT NULL,
    subject_type VARCHAR(100) NOT NULL,
    FOREIGN KEY (activity_id) REFERENCES data_processing_activities(id) ON DELETE CASCADE
);

-- Activity recipients
CREATE TABLE activity_recipients (
    activity_id VARCHAR(36) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    FOREIGN KEY (activity_id) REFERENCES data_processing_activities(id) ON DELETE CASCADE
);

-- Consent versions table (for consent text versioning)
CREATE TABLE consent_versions (
    id VARCHAR(36) PRIMARY KEY,
    purpose VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    consent_text TEXT NOT NULL,
    data_categories TEXT,
    processing_purposes TEXT,
    third_parties TEXT,
    retention_period VARCHAR(255),
    user_rights TEXT,
    contact_info TEXT,
    effective_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    is_current BOOLEAN DEFAULT FALSE,
    
    CONSTRAINT chk_version_purpose CHECK (purpose IN ('ESSENTIAL_SERVICE', 'MARKETING_EMAILS', 'PROMOTIONAL_SMS', 
        'PUSH_NOTIFICATIONS', 'ANALYTICS', 'PERSONALIZATION', 'THIRD_PARTY_SHARING', 'PROFILING', 
        'AUTOMATED_DECISIONS', 'LOCATION_TRACKING', 'BIOMETRIC_DATA', 'CROSS_BORDER_TRANSFER'))
);

CREATE INDEX idx_consent_versions_purpose ON consent_versions(purpose);
CREATE INDEX idx_consent_versions_current ON consent_versions(is_current);

-- Privacy policies table
CREATE TABLE privacy_policies (
    id VARCHAR(36) PRIMARY KEY,
    version VARCHAR(20) NOT NULL,
    language VARCHAR(10) NOT NULL,
    effective_date TIMESTAMP NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    is_current BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_privacy_policies_language ON privacy_policies(language);
CREATE INDEX idx_privacy_policies_current ON privacy_policies(is_current);

-- Data breach records (for GDPR breach notification requirements)
CREATE TABLE data_breach_records (
    id VARCHAR(36) PRIMARY KEY,
    breach_date TIMESTAMP NOT NULL,
    discovered_date TIMESTAMP NOT NULL,
    description TEXT NOT NULL,
    data_categories_affected TEXT,
    number_of_records_affected INTEGER,
    potential_consequences TEXT,
    measures_taken TEXT,
    dpa_notified BOOLEAN DEFAULT FALSE,
    dpa_notification_date TIMESTAMP,
    users_notified BOOLEAN DEFAULT FALSE,
    user_notification_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    
    CONSTRAINT chk_breach_status CHECK (status IN ('INVESTIGATING', 'CONTAINED', 'RESOLVED', 'REPORTED'))
);

-- Scheduled jobs for GDPR compliance
CREATE TABLE gdpr_scheduled_jobs (
    id VARCHAR(36) PRIMARY KEY,
    job_name VARCHAR(255) NOT NULL,
    job_type VARCHAR(50) NOT NULL,
    schedule_expression VARCHAR(255) NOT NULL,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    is_enabled BOOLEAN DEFAULT TRUE,
    configuration TEXT
);

-- Create initial privacy policy
INSERT INTO privacy_policies (id, version, language, effective_date, content, created_at, created_by, is_current)
VALUES (
    gen_random_uuid()::text,
    '1.0',
    'en',
    CURRENT_TIMESTAMP,
    'Waqiti Privacy Policy - Please update this with your actual privacy policy content',
    CURRENT_TIMESTAMP,
    'system',
    true
);

-- Create initial consent versions for each purpose
INSERT INTO consent_versions (id, purpose, version, title, consent_text, effective_date, created_at, created_by, is_current)
VALUES 
    (gen_random_uuid()::text, 'ESSENTIAL_SERVICE', '1.0', 'Essential Service Terms', 
     'We process your data to provide core Waqiti services...', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', true),
    (gen_random_uuid()::text, 'MARKETING_EMAILS', '1.0', 'Marketing Communications', 
     'We would like to send you marketing emails about our products...', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', true),
    (gen_random_uuid()::text, 'ANALYTICS', '1.0', 'Usage Analytics', 
     'We collect analytics to improve our services...', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', true);