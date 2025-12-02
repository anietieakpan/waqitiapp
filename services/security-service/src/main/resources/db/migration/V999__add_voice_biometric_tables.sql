-- Merged from biometric-service on $(date)
-- Biometric Service Initial Schema
-- Created: 2025-09-27
-- Description: Biometric authentication and enrollment management schema

CREATE TABLE IF NOT EXISTS biometric_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    biometric_type VARCHAR(50) NOT NULL,
    template_data_encrypted BYTEA NOT NULL,
    template_version VARCHAR(20) NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    quality_score DECIMAL(5, 4) NOT NULL,
    device_id VARCHAR(100),
    enrollment_location VARCHAR(100),
    is_primary BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,
    usage_count INTEGER DEFAULT 0,
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biometric_verification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_id VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    template_id VARCHAR(100) NOT NULL,
    biometric_type VARCHAR(50) NOT NULL,
    verification_result VARCHAR(20) NOT NULL,
    match_score DECIMAL(5, 4),
    confidence_score DECIMAL(5, 4),
    liveness_check_passed BOOLEAN DEFAULT FALSE,
    liveness_score DECIMAL(5, 4),
    device_id VARCHAR(100),
    ip_address VARCHAR(45),
    location VARCHAR(100),
    verification_method VARCHAR(50),
    attempt_number INTEGER DEFAULT 1,
    failure_reason TEXT,
    risk_score DECIMAL(5, 4),
    fraud_flags TEXT[],
    session_id VARCHAR(100),
    verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_biometric_template FOREIGN KEY (template_id) REFERENCES biometric_template(template_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS biometric_enrollment_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    biometric_type VARCHAR(50) NOT NULL,
    enrollment_status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    attempts_count INTEGER DEFAULT 0,
    quality_checks_passed INTEGER DEFAULT 0,
    quality_checks_failed INTEGER DEFAULT 0,
    device_id VARCHAR(100),
    ip_address VARCHAR(45),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    resulting_template_id VARCHAR(100),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biometric_device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) UNIQUE NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    supported_biometrics TEXT[] NOT NULL,
    firmware_version VARCHAR(50),
    certification_level VARCHAR(50),
    location VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_calibration_date TIMESTAMP,
    next_calibration_date TIMESTAMP,
    is_tampered BOOLEAN DEFAULT FALSE,
    tamper_detected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biometric_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    biometric_type VARCHAR(50),
    template_id VARCHAR(100),
    device_id VARCHAR(100),
    event_result VARCHAR(20) NOT NULL,
    event_details JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    risk_indicators TEXT[],
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biometric_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id VARCHAR(100) UNIQUE NOT NULL,
    policy_name VARCHAR(100) NOT NULL,
    biometric_type VARCHAR(50) NOT NULL,
    min_quality_score DECIMAL(5, 4) NOT NULL DEFAULT 0.8000,
    min_match_score DECIMAL(5, 4) NOT NULL DEFAULT 0.9500,
    require_liveness_check BOOLEAN DEFAULT TRUE,
    min_liveness_score DECIMAL(5, 4) DEFAULT 0.9000,
    max_attempts INTEGER DEFAULT 3,
    lockout_duration_minutes INTEGER DEFAULT 30,
    template_expiry_days INTEGER DEFAULT 365,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS biometric_failure_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    biometric_type VARCHAR(50) NOT NULL,
    failure_type VARCHAR(50) NOT NULL,
    failure_reason TEXT NOT NULL,
    device_id VARCHAR(100),
    ip_address VARCHAR(45),
    consecutive_failures INTEGER DEFAULT 1,
    account_locked BOOLEAN DEFAULT FALSE,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_biometric_preference (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    preferred_biometric_type VARCHAR(50),
    enabled_biometric_types TEXT[],
    require_for_login BOOLEAN DEFAULT FALSE,
    require_for_transactions BOOLEAN DEFAULT TRUE,
    high_value_transaction_threshold DECIMAL(15, 2) DEFAULT 1000.00,
    allow_fallback_to_password BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_biometric_template_user ON biometric_template(user_id);
CREATE INDEX idx_biometric_template_type ON biometric_template(biometric_type);
CREATE INDEX idx_biometric_template_active ON biometric_template(is_active) WHERE is_active = true;
CREATE INDEX idx_biometric_template_deleted ON biometric_template(deleted_at) WHERE deleted_at IS NULL;

CREATE INDEX idx_biometric_verification_user ON biometric_verification(user_id);
CREATE INDEX idx_biometric_verification_template ON biometric_verification(template_id);
CREATE INDEX idx_biometric_verification_result ON biometric_verification(verification_result);
CREATE INDEX idx_biometric_verification_date ON biometric_verification(verified_at DESC);
CREATE INDEX idx_biometric_verification_fraud ON biometric_verification(risk_score DESC) WHERE risk_score > 0.7;

CREATE INDEX idx_enrollment_session_user ON biometric_enrollment_session(user_id);
CREATE INDEX idx_enrollment_session_status ON biometric_enrollment_session(enrollment_status);
CREATE INDEX idx_enrollment_session_expires ON biometric_enrollment_session(expires_at);

CREATE INDEX idx_biometric_device_type ON biometric_device(device_type);
CREATE INDEX idx_biometric_device_status ON biometric_device(status);
CREATE INDEX idx_biometric_device_tampered ON biometric_device(is_tampered) WHERE is_tampered = true;

CREATE INDEX idx_biometric_audit_user ON biometric_audit_log(user_id);
CREATE INDEX idx_biometric_audit_event ON biometric_audit_log(event_type);
CREATE INDEX idx_biometric_audit_created ON biometric_audit_log(created_at DESC);

CREATE INDEX idx_biometric_policy_type ON biometric_policy(biometric_type);
CREATE INDEX idx_biometric_policy_active ON biometric_policy(is_active) WHERE is_active = true;

CREATE INDEX idx_biometric_failure_user ON biometric_failure_log(user_id);
CREATE INDEX idx_biometric_failure_locked ON biometric_failure_log(account_locked) WHERE account_locked = true;
CREATE INDEX idx_biometric_failure_created ON biometric_failure_log(created_at DESC);

CREATE INDEX idx_user_biometric_pref_user ON user_biometric_preference(user_id);

-- Insert default biometric policies
INSERT INTO biometric_policy (policy_id, policy_name, biometric_type, min_quality_score, min_match_score, require_liveness_check, min_liveness_score, max_attempts, lockout_duration_minutes) VALUES
    ('POL_FINGERPRINT_001', 'Standard Fingerprint Policy', 'FINGERPRINT', 0.8000, 0.9500, true, 0.9000, 3, 30),
    ('POL_FACIAL_001', 'Standard Facial Recognition Policy', 'FACIAL_RECOGNITION', 0.8500, 0.9600, true, 0.9200, 3, 30),
    ('POL_IRIS_001', 'Standard Iris Scan Policy', 'IRIS_SCAN', 0.9000, 0.9800, true, 0.9500, 3, 30)
ON CONFLICT (policy_id) DO NOTHING;

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_biometric_template_updated_at BEFORE UPDATE ON biometric_template
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_enrollment_session_updated_at BEFORE UPDATE ON biometric_enrollment_session
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_biometric_device_updated_at BEFORE UPDATE ON biometric_device
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_biometric_policy_updated_at BEFORE UPDATE ON biometric_policy
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_biometric_pref_updated_at BEFORE UPDATE ON user_biometric_preference
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();