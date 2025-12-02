-- Enhanced User Service Database Schema
-- Additional tables for KYC, risk assessment, and device management

-- KYC documents table
CREATE TABLE kyc_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL CHECK (document_type IN ('PASSPORT', 'DRIVERS_LICENSE', 'NATIONAL_ID', 'UTILITY_BILL', 'BANK_STATEMENT')),
    document_status VARCHAR(20) NOT NULL CHECK (document_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')) DEFAULT 'PENDING',
    document_number VARCHAR(100),
    issuing_country VARCHAR(3),
    issuing_authority VARCHAR(100),
    issue_date DATE,
    expiry_date DATE,
    file_path VARCHAR(500),
    file_hash VARCHAR(64),
    verification_score DECIMAL(5,2),
    verification_notes TEXT,
    verified_by UUID,
    verified_at TIMESTAMP WITH TIME ZONE,
    rejection_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User risk assessments
CREATE TABLE user_risk_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    assessment_type VARCHAR(20) NOT NULL CHECK (assessment_type IN ('ONBOARDING', 'PERIODIC', 'TRANSACTION_TRIGGERED', 'MANUAL')),
    risk_score INTEGER NOT NULL CHECK (risk_score >= 0 AND risk_score <= 100),
    risk_level VARCHAR(20) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    assessment_factors JSONB NOT NULL,
    geographical_risk JSONB,
    transaction_patterns JSONB,
    external_data JSONB,
    assessment_notes TEXT,
    assessed_by UUID,
    next_assessment_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Device fingerprints and tracking
CREATE TABLE user_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_fingerprint VARCHAR(64) NOT NULL,
    device_type VARCHAR(20) NOT NULL CHECK (device_type IN ('MOBILE', 'DESKTOP', 'TABLET', 'UNKNOWN')),
    device_name VARCHAR(100),
    operating_system VARCHAR(50),
    browser VARCHAR(50),
    ip_address INET,
    location_data JSONB,
    is_trusted BOOLEAN NOT NULL DEFAULT FALSE,
    risk_score INTEGER CHECK (risk_score >= 0 AND risk_score <= 100),
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, device_fingerprint)
);

-- Location validation records
CREATE TABLE location_validations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    ip_address INET NOT NULL,
    country_code VARCHAR(3),
    city VARCHAR(100),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    is_vpn BOOLEAN DEFAULT FALSE,
    is_proxy BOOLEAN DEFAULT FALSE,
    is_tor BOOLEAN DEFAULT FALSE,
    risk_score INTEGER CHECK (risk_score >= 0 AND risk_score <= 100),
    validation_source VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- JWT token revocation
CREATE TABLE revoked_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    token_type VARCHAR(20) NOT NULL CHECK (token_type IN ('ACCESS', 'REFRESH', 'DEVICE')),
    revocation_reason VARCHAR(50) NOT NULL CHECK (revocation_reason IN ('LOGOUT', 'SECURITY_BREACH', 'SUSPICIOUS_ACTIVITY', 'EXPIRED', 'MANUAL')),
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- User sessions
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    device_id UUID REFERENCES user_devices(id),
    ip_address INET,
    user_agent TEXT,
    login_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_at TIMESTAMP WITH TIME ZONE,
    session_status VARCHAR(20) NOT NULL CHECK (session_status IN ('ACTIVE', 'EXPIRED', 'TERMINATED')) DEFAULT 'ACTIVE',
    location_data JSONB,
    metadata JSONB
);

-- Two-factor authentication
CREATE TABLE user_2fa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    method_type VARCHAR(20) NOT NULL CHECK (method_type IN ('TOTP', 'SMS', 'EMAIL', 'BACKUP_CODES')),
    secret_key VARCHAR(255),
    backup_codes JSONB,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, method_type)
);

-- User preferences and settings
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    language VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(50) DEFAULT 'UTC',
    currency VARCHAR(3) DEFAULT 'USD',
    notification_settings JSONB,
    privacy_settings JSONB,
    security_settings JSONB,
    ui_preferences JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User activity log
CREATE TABLE user_activity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    activity_description TEXT,
    ip_address INET,
    user_agent TEXT,
    device_id UUID REFERENCES user_devices(id),
    location_data JSONB,
    risk_score INTEGER CHECK (risk_score >= 0 AND risk_score <= 100),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_kyc_documents_user_id ON kyc_documents(user_id);
CREATE INDEX idx_kyc_documents_status ON kyc_documents(document_status);
CREATE INDEX idx_kyc_documents_type ON kyc_documents(document_type);
CREATE INDEX idx_kyc_documents_verified_at ON kyc_documents(verified_at);

CREATE INDEX idx_user_risk_assessments_user_id ON user_risk_assessments(user_id);
CREATE INDEX idx_user_risk_assessments_risk_level ON user_risk_assessments(risk_level);
CREATE INDEX idx_user_risk_assessments_type ON user_risk_assessments(assessment_type);
CREATE INDEX idx_user_risk_assessments_active ON user_risk_assessments(is_active);

CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX idx_user_devices_fingerprint ON user_devices(device_fingerprint);
CREATE INDEX idx_user_devices_trusted ON user_devices(is_trusted);
CREATE INDEX idx_user_devices_last_seen ON user_devices(last_seen_at);

CREATE INDEX idx_location_validations_user_id ON location_validations(user_id);
CREATE INDEX idx_location_validations_ip ON location_validations(ip_address);
CREATE INDEX idx_location_validations_country ON location_validations(country_code);

CREATE INDEX idx_revoked_tokens_token_id ON revoked_tokens(token_id);
CREATE INDEX idx_revoked_tokens_user_id ON revoked_tokens(user_id);
CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_session_id ON user_sessions(session_id);
CREATE INDEX idx_user_sessions_status ON user_sessions(session_status);
CREATE INDEX idx_user_sessions_last_activity ON user_sessions(last_activity_at);

CREATE INDEX idx_user_2fa_user_id ON user_2fa(user_id);
CREATE INDEX idx_user_2fa_enabled ON user_2fa(is_enabled);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);

CREATE INDEX idx_user_activity_log_user_id ON user_activity_log(user_id);
CREATE INDEX idx_user_activity_log_type ON user_activity_log(activity_type);
CREATE INDEX idx_user_activity_log_created_at ON user_activity_log(created_at);
CREATE INDEX idx_user_activity_log_success ON user_activity_log(success);

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_kyc_documents_updated_at 
    BEFORE UPDATE ON kyc_documents 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_devices_updated_at 
    BEFORE UPDATE ON user_devices 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_2fa_updated_at 
    BEFORE UPDATE ON user_2fa 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_preferences_updated_at 
    BEFORE UPDATE ON user_preferences 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Cleanup expired tokens
CREATE OR REPLACE FUNCTION cleanup_expired_tokens()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM revoked_tokens WHERE expires_at < CURRENT_TIMESTAMP;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;