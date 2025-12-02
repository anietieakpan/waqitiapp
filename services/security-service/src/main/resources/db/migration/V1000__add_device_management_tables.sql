-- Merged from device-service on $(date)
-- Device Service Initial Schema
-- Created: 2025-09-27
-- Description: Device management, registration, and security schema

CREATE TABLE IF NOT EXISTS device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    device_fingerprint VARCHAR(255) UNIQUE NOT NULL,
    device_name VARCHAR(255),
    device_type VARCHAR(50) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    os_name VARCHAR(50),
    os_version VARCHAR(50),
    browser_name VARCHAR(50),
    browser_version VARCHAR(50),
    device_model VARCHAR(100),
    device_manufacturer VARCHAR(100),
    screen_resolution VARCHAR(20),
    screen_size DECIMAL(5, 2),
    timezone VARCHAR(50),
    language VARCHAR(10),
    user_agent TEXT,
    ip_address VARCHAR(50),
    registration_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_trusted BOOLEAN DEFAULT FALSE,
    trust_score DECIMAL(5, 4) DEFAULT 0.5000,
    trusted_at TIMESTAMP,
    is_primary BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_jailbroken BOOLEAN DEFAULT FALSE,
    is_rooted BOOLEAN DEFAULT FALSE,
    is_emulator BOOLEAN DEFAULT FALSE,
    push_token TEXT,
    push_enabled BOOLEAN DEFAULT FALSE,
    biometric_enabled BOOLEAN DEFAULT FALSE,
    location_enabled BOOLEAN DEFAULT FALSE,
    last_location JSONB,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(100) UNIQUE NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    session_token_hash VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(255),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(50),
    location JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    logout_reason VARCHAR(50),
    ended_at TIMESTAMP,
    activity_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_session FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_verification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_id VARCHAR(100) UNIQUE NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    verification_method VARCHAR(50) NOT NULL,
    verification_code_hash VARCHAR(255),
    sent_to VARCHAR(255) NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    attempts_count INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ip_address VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_verification FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_security_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    risk_score DECIMAL(5, 4),
    threat_indicators TEXT[],
    ip_address VARCHAR(50),
    location JSONB,
    user_agent TEXT,
    action_taken VARCHAR(50),
    is_blocked BOOLEAN DEFAULT FALSE,
    requires_review BOOLEAN DEFAULT FALSE,
    reviewed BOOLEAN DEFAULT FALSE,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    false_positive BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_security_event FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_location_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    accuracy DECIMAL(10, 2),
    altitude DECIMAL(10, 2),
    speed DECIMAL(10, 2),
    heading DECIMAL(5, 2),
    city VARCHAR(100),
    state VARCHAR(50),
    country_code VARCHAR(3),
    ip_address VARCHAR(50),
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_location_history FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_binding (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    binding_id VARCHAR(100) UNIQUE NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    binding_type VARCHAR(50) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    service_identifier VARCHAR(255),
    public_key TEXT,
    attestation_data JSONB,
    bound_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_validated_at TIMESTAMP,
    validation_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    revoked_at TIMESTAMP,
    revocation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_binding FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_certificate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    certificate_id VARCHAR(100) UNIQUE NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    certificate_type VARCHAR(50) NOT NULL,
    certificate_data TEXT NOT NULL,
    public_key TEXT NOT NULL,
    thumbprint VARCHAR(128) NOT NULL,
    issuer VARCHAR(255),
    subject VARCHAR(255),
    serial_number VARCHAR(100),
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_expired BOOLEAN DEFAULT FALSE,
    is_revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP,
    revocation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_certificate FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) NOT NULL,
    app_name VARCHAR(255) NOT NULL,
    app_version VARCHAR(50) NOT NULL,
    app_bundle_id VARCHAR(255),
    app_build_number VARCHAR(50),
    installed_at TIMESTAMP,
    last_updated_at TIMESTAMP,
    is_official BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_application FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_blocklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id VARCHAR(100) UNIQUE NOT NULL,
    block_type VARCHAR(50) NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    identifier_hash VARCHAR(128) NOT NULL,
    reason TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    added_by VARCHAR(100) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    times_blocked INTEGER DEFAULT 0,
    last_blocked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS device_risk_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) UNIQUE NOT NULL,
    risk_score DECIMAL(5, 4) NOT NULL DEFAULT 0.5000,
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    threat_level VARCHAR(20),
    is_compromised BOOLEAN DEFAULT FALSE,
    compromise_indicators TEXT[],
    security_events_count INTEGER DEFAULT 0,
    last_security_event_at TIMESTAMP,
    failed_login_attempts INTEGER DEFAULT 0,
    suspicious_activity_count INTEGER DEFAULT 0,
    location_anomalies INTEGER DEFAULT 0,
    velocity_violations INTEGER DEFAULT 0,
    behavioral_score DECIMAL(5, 4),
    device_integrity_score DECIMAL(5, 4),
    network_reputation_score DECIMAL(5, 4),
    last_assessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_risk_profile FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_notification_preference (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) UNIQUE NOT NULL,
    push_enabled BOOLEAN DEFAULT TRUE,
    transaction_alerts BOOLEAN DEFAULT TRUE,
    security_alerts BOOLEAN DEFAULT TRUE,
    marketing_notifications BOOLEAN DEFAULT FALSE,
    balance_alerts BOOLEAN DEFAULT TRUE,
    payment_reminders BOOLEAN DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    sound_enabled BOOLEAN DEFAULT TRUE,
    vibration_enabled BOOLEAN DEFAULT TRUE,
    badge_enabled BOOLEAN DEFAULT TRUE,
    preferences JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_notification_preference FOREIGN KEY (device_id) REFERENCES device(device_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS device_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_devices INTEGER NOT NULL DEFAULT 0,
    active_devices INTEGER DEFAULT 0,
    new_devices INTEGER DEFAULT 0,
    trusted_devices INTEGER DEFAULT 0,
    blocked_devices INTEGER DEFAULT 0,
    compromised_devices INTEGER DEFAULT 0,
    by_platform JSONB,
    by_device_type JSONB,
    by_os JSONB,
    security_events_count INTEGER DEFAULT 0,
    avg_risk_score DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_device_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_device_customer ON device(customer_id);
CREATE INDEX idx_device_fingerprint ON device(device_fingerprint);
CREATE INDEX idx_device_status ON device(status);
CREATE INDEX idx_device_platform ON device(platform);
CREATE INDEX idx_device_type ON device(device_type);
CREATE INDEX idx_device_trusted ON device(is_trusted);
CREATE INDEX idx_device_last_seen ON device(last_seen_at DESC);
CREATE INDEX idx_device_ip ON device(ip_address);

CREATE INDEX idx_device_session_device ON device_session(device_id);
CREATE INDEX idx_device_session_customer ON device_session(customer_id);
CREATE INDEX idx_device_session_active ON device_session(is_active) WHERE is_active = true;
CREATE INDEX idx_device_session_expires ON device_session(expires_at);
CREATE INDEX idx_device_session_activity ON device_session(last_activity_at DESC);

CREATE INDEX idx_device_verification_device ON device_verification(device_id);
CREATE INDEX idx_device_verification_customer ON device_verification(customer_id);
CREATE INDEX idx_device_verification_status ON device_verification(status);
CREATE INDEX idx_device_verification_expires ON device_verification(expires_at);

CREATE INDEX idx_device_security_event_device ON device_security_event(device_id);
CREATE INDEX idx_device_security_event_customer ON device_security_event(customer_id);
CREATE INDEX idx_device_security_event_type ON device_security_event(event_type);
CREATE INDEX idx_device_security_event_severity ON device_security_event(severity);
CREATE INDEX idx_device_security_event_detected ON device_security_event(detected_at DESC);
CREATE INDEX idx_device_security_event_review ON device_security_event(requires_review) WHERE requires_review = true;

CREATE INDEX idx_device_location_history_device ON device_location_history(device_id);
CREATE INDEX idx_device_location_history_customer ON device_location_history(customer_id);
CREATE INDEX idx_device_location_history_recorded ON device_location_history(recorded_at DESC);
CREATE INDEX idx_device_location_history_country ON device_location_history(country_code);

CREATE INDEX idx_device_binding_device ON device_binding(device_id);
CREATE INDEX idx_device_binding_customer ON device_binding(customer_id);
CREATE INDEX idx_device_binding_type ON device_binding(binding_type);
CREATE INDEX idx_device_binding_active ON device_binding(is_active) WHERE is_active = true;

CREATE INDEX idx_device_certificate_device ON device_certificate(device_id);
CREATE INDEX idx_device_certificate_expires ON device_certificate(expires_at);
CREATE INDEX idx_device_certificate_revoked ON device_certificate(is_revoked);

CREATE INDEX idx_device_application_device ON device_application(device_id);
CREATE INDEX idx_device_application_name ON device_application(app_name);

CREATE INDEX idx_device_blocklist_type ON device_blocklist(block_type);
CREATE INDEX idx_device_blocklist_hash ON device_blocklist(identifier_hash);
CREATE INDEX idx_device_blocklist_active ON device_blocklist(is_active) WHERE is_active = true;

CREATE INDEX idx_device_risk_profile_device ON device_risk_profile(device_id);
CREATE INDEX idx_device_risk_profile_score ON device_risk_profile(risk_score DESC);
CREATE INDEX idx_device_risk_profile_level ON device_risk_profile(risk_level);
CREATE INDEX idx_device_risk_profile_compromised ON device_risk_profile(is_compromised) WHERE is_compromised = true;

CREATE INDEX idx_device_notification_preference_device ON device_notification_preference(device_id);

CREATE INDEX idx_device_statistics_period ON device_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_device_updated_at BEFORE UPDATE ON device
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_session_updated_at BEFORE UPDATE ON device_session
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_binding_updated_at BEFORE UPDATE ON device_binding
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_application_updated_at BEFORE UPDATE ON device_application
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_blocklist_updated_at BEFORE UPDATE ON device_blocklist
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_risk_profile_updated_at BEFORE UPDATE ON device_risk_profile
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_device_notification_preference_updated_at BEFORE UPDATE ON device_notification_preference
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();