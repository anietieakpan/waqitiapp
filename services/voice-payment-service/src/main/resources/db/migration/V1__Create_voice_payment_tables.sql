-- Voice Payment Service Database Schema
-- Created for comprehensive voice-activated payment processing

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable JSONB operations
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- Voice Profiles Table
CREATE TABLE voice_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE,
    profile_name VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    is_enrolled BOOLEAN DEFAULT false,
    enrollment_status VARCHAR(20) DEFAULT 'NOT_STARTED',
    voice_samples JSONB,
    sample_count INTEGER DEFAULT 0,
    required_samples INTEGER DEFAULT 5,
    voice_signature JSONB,
    signature_version VARCHAR(10) DEFAULT '1.0',
    biometric_features JSONB,
    preferred_language VARCHAR(10) DEFAULT 'en-US',
    supported_languages JSONB,
    speech_pattern_id VARCHAR(50),
    accent_region VARCHAR(50),
    voice_quality_score DOUBLE PRECISION,
    recognition_accuracy DOUBLE PRECISION,
    training_iterations INTEGER DEFAULT 0,
    last_training_date TIMESTAMP,
    security_level VARCHAR(20) DEFAULT 'STANDARD',
    verification_threshold DOUBLE PRECISION DEFAULT 0.85,
    anti_spoofing_enabled BOOLEAN DEFAULT true,
    liveness_detection_enabled BOOLEAN DEFAULT true,
    device_profiles JSONB,
    total_commands_processed INTEGER DEFAULT 0,
    successful_commands INTEGER DEFAULT 0,
    failed_commands INTEGER DEFAULT 0,
    average_confidence_score DOUBLE PRECISION,
    false_positive_rate DOUBLE PRECISION,
    false_negative_rate DOUBLE PRECISION,
    last_successful_auth TIMESTAMP,
    last_failed_auth TIMESTAMP,
    consecutive_failures INTEGER DEFAULT 0,
    max_consecutive_failures INTEGER DEFAULT 3,
    is_locked BOOLEAN DEFAULT false,
    locked_until TIMESTAMP,
    preferences JSONB,
    analytics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Voice Sessions Table
CREATE TABLE voice_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    session_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    channel VARCHAR(30) DEFAULT 'VOICE',
    device_id VARCHAR(100),
    device_type VARCHAR(50),
    phone_number VARCHAR(20),
    caller_id VARCHAR(50),
    language VARCHAR(10) DEFAULT 'en-US',
    conversation_context JSONB,
    session_variables JSONB,
    current_intent VARCHAR(50),
    current_state VARCHAR(50),
    total_turns INTEGER DEFAULT 0,
    successful_turns INTEGER DEFAULT 0,
    failed_turns INTEGER DEFAULT 0,
    commands_history JSONB,
    authentication_status VARCHAR(20) DEFAULT 'PENDING',
    authentication_attempts INTEGER DEFAULT 0,
    max_authentication_attempts INTEGER DEFAULT 3,
    voice_signature_verified BOOLEAN DEFAULT false,
    biometric_score DOUBLE PRECISION,
    security_level VARCHAR(20) DEFAULT 'STANDARD',
    ambient_noise_level DOUBLE PRECISION,
    audio_quality_avg DOUBLE PRECISION,
    recognition_accuracy_avg DOUBLE PRECISION,
    performance_metrics JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP,
    ended_at TIMESTAMP,
    duration_seconds BIGINT,
    timeout_minutes INTEGER DEFAULT 5,
    is_recording_enabled BOOLEAN DEFAULT true,
    recording_consent_given BOOLEAN DEFAULT false,
    session_recordings JSONB,
    transcription_log JSONB,
    satisfaction_score INTEGER,
    user_feedback TEXT,
    escalated_to_human BOOLEAN DEFAULT false,
    human_agent_id VARCHAR(50),
    error_log JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Voice Commands Table
CREATE TABLE voice_commands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    session_id VARCHAR(50) NOT NULL,
    original_audio_url VARCHAR(500),
    transcribed_text TEXT,
    confidence_score DOUBLE PRECISION,
    language VARCHAR(10) DEFAULT 'en-US',
    command_type VARCHAR(30) NOT NULL,
    intent VARCHAR(50),
    extracted_entities JSONB,
    recipient_name VARCHAR(100),
    recipient_id UUID,
    amount DECIMAL(19,2),
    currency VARCHAR(3) DEFAULT 'USD',
    purpose TEXT,
    confirmation_required BOOLEAN DEFAULT true,
    is_confirmed BOOLEAN DEFAULT false,
    processing_status VARCHAR(20) DEFAULT 'PENDING',
    payment_id UUID,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    context_data JSONB,
    device_info VARCHAR(500),
    location VARCHAR(200),
    ambient_noise_level DOUBLE PRECISION,
    audio_quality_score DOUBLE PRECISION,
    biometric_data JSONB,
    voice_signature_match BOOLEAN,
    security_score DOUBLE PRECISION,
    requires_2fa BOOLEAN DEFAULT false,
    is_2fa_verified BOOLEAN DEFAULT false,
    processing_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    expires_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Voice Transactions Table
CREATE TABLE voice_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    voice_command_id UUID NOT NULL,
    voice_session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    sender_id UUID,
    sender_account VARCHAR(100),
    recipient_id UUID,
    recipient_account VARCHAR(100),
    recipient_identifier VARCHAR(200),
    payee_name VARCHAR(100),
    payee_account VARCHAR(100),
    purpose TEXT,
    memo TEXT,
    status VARCHAR(20) DEFAULT 'INITIATED',
    voice_confirmation_received BOOLEAN DEFAULT false,
    confirmation_phrase TEXT,
    authentication_method VARCHAR(30),
    biometric_verified BOOLEAN DEFAULT false,
    biometric_score DOUBLE PRECISION,
    pin_verified BOOLEAN DEFAULT false,
    two_fa_verified BOOLEAN DEFAULT false,
    security_score DOUBLE PRECISION,
    fraud_score DOUBLE PRECISION,
    risk_level VARCHAR(20) DEFAULT 'LOW',
    fraud_indicators JSONB,
    compliance_data JSONB,
    payment_method VARCHAR(30),
    payment_provider VARCHAR(50),
    payment_reference VARCHAR(100),
    external_transaction_id VARCHAR(100),
    processing_fee DECIMAL(19,4),
    exchange_rate DOUBLE PRECISION,
    original_amount DECIMAL(19,2),
    original_currency VARCHAR(3),
    scheduled_date TIMESTAMP,
    recurring_schedule VARCHAR(20),
    parent_transaction_id UUID,
    voice_metadata JSONB,
    processing_metadata JSONB,
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_code VARCHAR(50),
    error_message TEXT,
    error_details JSONB,
    notification_sent BOOLEAN DEFAULT false,
    receipt_generated BOOLEAN DEFAULT false,
    receipt_url VARCHAR(500),
    audit_trail JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Voice Biometric Enrollments Table
CREATE TABLE voice_biometric_enrollments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    enrollment_session_id VARCHAR(50) NOT NULL,
    enrollment_type VARCHAR(30) DEFAULT 'STANDARD',
    status VARCHAR(20) DEFAULT 'IN_PROGRESS',
    samples_collected INTEGER DEFAULT 0,
    samples_required INTEGER DEFAULT 5,
    sample_urls JSONB,
    biometric_template JSONB,
    quality_metrics JSONB,
    language VARCHAR(10) DEFAULT 'en-US',
    phrases_used JSONB,
    enrollment_instructions JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Voice Authentication Sessions Table
CREATE TABLE voice_authentication_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    voice_profile_id UUID NOT NULL,
    authentication_type VARCHAR(30) DEFAULT 'BIOMETRIC',
    status VARCHAR(20) DEFAULT 'PENDING',
    voice_sample_url VARCHAR(500),
    biometric_match_score DOUBLE PRECISION,
    liveness_score DOUBLE PRECISION,
    anti_spoofing_score DOUBLE PRECISION,
    confidence_score DOUBLE PRECISION,
    security_level VARCHAR(20),
    fraud_indicators JSONB,
    device_context JSONB,
    environmental_context JSONB,
    authenticated_at TIMESTAMP,
    expires_at TIMESTAMP,
    attempts_count INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Voice Analytics Events Table
CREATE TABLE voice_analytics_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    session_id VARCHAR(50),
    transaction_id VARCHAR(50),
    command_type VARCHAR(30),
    language VARCHAR(10),
    device_type VARCHAR(50),
    success BOOLEAN,
    confidence_score DOUBLE PRECISION,
    processing_duration_ms BIGINT,
    error_code VARCHAR(50),
    error_category VARCHAR(30),
    biometric_score DOUBLE PRECISION,
    fraud_score DOUBLE PRECISION,
    ambient_noise_level DOUBLE PRECISION,
    audio_quality_score DOUBLE PRECISION,
    user_satisfaction_score INTEGER,
    geographic_location VARCHAR(100),
    network_quality VARCHAR(20),
    event_metadata JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Voice Conversation Flows Table
CREATE TABLE voice_conversation_flows (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    flow_name VARCHAR(100) NOT NULL,
    flow_version VARCHAR(10) DEFAULT '1.0',
    language VARCHAR(10) NOT NULL,
    flow_definition JSONB NOT NULL,
    triggers JSONB,
    responses JSONB,
    validation_rules JSONB,
    fallback_actions JSONB,
    is_active BOOLEAN DEFAULT true,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Voice Security Events Table
CREATE TABLE voice_security_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type VARCHAR(50) NOT NULL,
    severity_level VARCHAR(20) NOT NULL,
    user_id UUID,
    session_id VARCHAR(50),
    transaction_id VARCHAR(50),
    threat_type VARCHAR(30),
    threat_description TEXT,
    detection_method VARCHAR(30),
    confidence_score DOUBLE PRECISION,
    source_ip VARCHAR(45),
    user_agent TEXT,
    device_fingerprint VARCHAR(100),
    biometric_anomaly JSONB,
    behavioral_anomaly JSONB,
    mitigation_actions JSONB,
    investigation_status VARCHAR(20) DEFAULT 'PENDING',
    resolved_at TIMESTAMP,
    event_metadata JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Voice User Preferences Table
CREATE TABLE voice_user_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE,
    preferred_language VARCHAR(10) DEFAULT 'en-US',
    supported_languages JSONB,
    voice_speed DOUBLE PRECISION DEFAULT 1.0,
    voice_pitch DOUBLE PRECISION DEFAULT 1.0,
    volume_level DOUBLE PRECISION DEFAULT 0.8,
    confirmation_timeout INTEGER DEFAULT 30,
    max_retries INTEGER DEFAULT 3,
    enable_biometric_auth BOOLEAN DEFAULT true,
    enable_voice_confirmations BOOLEAN DEFAULT true,
    enable_push_notifications BOOLEAN DEFAULT true,
    enable_email_receipts BOOLEAN DEFAULT false,
    enable_sms_fallback BOOLEAN DEFAULT true,
    enable_noise_suppression BOOLEAN DEFAULT true,
    enable_echo_cancellation BOOLEAN DEFAULT true,
    accessibility_features JSONB,
    privacy_settings JSONB,
    security_preferences JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Foreign Key Constraints
ALTER TABLE voice_sessions ADD CONSTRAINT fk_voice_sessions_user_id 
    FOREIGN KEY (user_id) REFERENCES voice_profiles(user_id);

ALTER TABLE voice_commands ADD CONSTRAINT fk_voice_commands_user_id 
    FOREIGN KEY (user_id) REFERENCES voice_profiles(user_id);

ALTER TABLE voice_transactions ADD CONSTRAINT fk_voice_transactions_user_id 
    FOREIGN KEY (user_id) REFERENCES voice_profiles(user_id);

ALTER TABLE voice_transactions ADD CONSTRAINT fk_voice_transactions_command_id 
    FOREIGN KEY (voice_command_id) REFERENCES voice_commands(id);

ALTER TABLE voice_transactions ADD CONSTRAINT fk_voice_transactions_session_id 
    FOREIGN KEY (voice_session_id) REFERENCES voice_sessions(id);

ALTER TABLE voice_biometric_enrollments ADD CONSTRAINT fk_voice_enrollments_user_id 
    FOREIGN KEY (user_id) REFERENCES voice_profiles(user_id);

ALTER TABLE voice_authentication_sessions ADD CONSTRAINT fk_voice_auth_sessions_user_id 
    FOREIGN KEY (user_id) REFERENCES voice_profiles(user_id);

ALTER TABLE voice_authentication_sessions ADD CONSTRAINT fk_voice_auth_sessions_profile_id 
    FOREIGN KEY (voice_profile_id) REFERENCES voice_profiles(id);

ALTER TABLE voice_user_preferences ADD CONSTRAINT fk_voice_preferences_user_id 
    FOREIGN KEY (user_id) REFERENCES voice_profiles(user_id);

-- Indexes for Performance
CREATE INDEX idx_voice_profiles_user_id ON voice_profiles(user_id);
CREATE INDEX idx_voice_profiles_enrollment_status ON voice_profiles(enrollment_status);
CREATE INDEX idx_voice_profiles_is_active ON voice_profiles(is_active);
CREATE INDEX idx_voice_profiles_last_used_at ON voice_profiles(last_used_at);

CREATE INDEX idx_voice_sessions_session_id ON voice_sessions(session_id);
CREATE INDEX idx_voice_sessions_user_id ON voice_sessions(user_id);
CREATE INDEX idx_voice_sessions_status ON voice_sessions(status);
CREATE INDEX idx_voice_sessions_started_at ON voice_sessions(started_at);

CREATE INDEX idx_voice_commands_user_id ON voice_commands(user_id);
CREATE INDEX idx_voice_commands_session_id ON voice_commands(session_id);
CREATE INDEX idx_voice_commands_processing_status ON voice_commands(processing_status);
CREATE INDEX idx_voice_commands_command_type ON voice_commands(command_type);
CREATE INDEX idx_voice_commands_created_at ON voice_commands(created_at);

CREATE INDEX idx_voice_transactions_user_id ON voice_transactions(user_id);
CREATE INDEX idx_voice_transactions_transaction_id ON voice_transactions(transaction_id);
CREATE INDEX idx_voice_transactions_status ON voice_transactions(status);
CREATE INDEX idx_voice_transactions_transaction_type ON voice_transactions(transaction_type);
CREATE INDEX idx_voice_transactions_initiated_at ON voice_transactions(initiated_at);

CREATE INDEX idx_voice_analytics_events_user_id ON voice_analytics_events(user_id);
CREATE INDEX idx_voice_analytics_events_event_type ON voice_analytics_events(event_type);
CREATE INDEX idx_voice_analytics_events_occurred_at ON voice_analytics_events(occurred_at);

CREATE INDEX idx_voice_security_events_user_id ON voice_security_events(user_id);
CREATE INDEX idx_voice_security_events_event_type ON voice_security_events(event_type);
CREATE INDEX idx_voice_security_events_severity_level ON voice_security_events(severity_level);
CREATE INDEX idx_voice_security_events_occurred_at ON voice_security_events(occurred_at);

-- JSONB Indexes for better query performance
CREATE INDEX idx_voice_profiles_preferences_gin ON voice_profiles USING gin(preferences);
CREATE INDEX idx_voice_profiles_biometric_features_gin ON voice_profiles USING gin(biometric_features);
CREATE INDEX idx_voice_sessions_conversation_context_gin ON voice_sessions USING gin(conversation_context);
CREATE INDEX idx_voice_commands_extracted_entities_gin ON voice_commands USING gin(extracted_entities);
CREATE INDEX idx_voice_transactions_fraud_indicators_gin ON voice_transactions USING gin(fraud_indicators);

-- Partial Indexes for common queries
CREATE INDEX idx_voice_profiles_active_enrolled ON voice_profiles(user_id) 
    WHERE is_active = true AND is_enrolled = true;

CREATE INDEX idx_voice_sessions_active ON voice_sessions(user_id, session_id) 
    WHERE status IN ('ACTIVE', 'WAITING_FOR_INPUT', 'PROCESSING');

CREATE INDEX idx_voice_commands_pending ON voice_commands(user_id, session_id) 
    WHERE processing_status IN ('PENDING', 'TRANSCRIBING', 'PARSING', 'VALIDATING', 'CONFIRMING');

CREATE INDEX idx_voice_transactions_processing ON voice_transactions(user_id) 
    WHERE status IN ('INITIATED', 'PROCESSING', 'PENDING');

-- Functions for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_voice_profiles_updated_at BEFORE UPDATE ON voice_profiles 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_sessions_updated_at BEFORE UPDATE ON voice_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_commands_updated_at BEFORE UPDATE ON voice_commands 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_transactions_updated_at BEFORE UPDATE ON voice_transactions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_biometric_enrollments_updated_at BEFORE UPDATE ON voice_biometric_enrollments 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_authentication_sessions_updated_at BEFORE UPDATE ON voice_authentication_sessions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_conversation_flows_updated_at BEFORE UPDATE ON voice_conversation_flows 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_user_preferences_updated_at BEFORE UPDATE ON voice_user_preferences 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Views for common queries

-- Active Voice Sessions View
CREATE VIEW active_voice_sessions AS
SELECT 
    vs.*,
    vp.preferred_language,
    vp.security_level,
    vp.voice_quality_score
FROM voice_sessions vs
JOIN voice_profiles vp ON vs.user_id = vp.user_id
WHERE vs.status IN ('ACTIVE', 'WAITING_FOR_INPUT', 'PROCESSING', 'AUTHENTICATING')
AND vs.started_at > CURRENT_TIMESTAMP - INTERVAL '24 hours';

-- Voice Transaction Summary View
CREATE VIEW voice_transaction_summary AS
SELECT 
    vt.*,
    vc.transcribed_text,
    vc.confidence_score as command_confidence,
    vs.language,
    vs.device_type,
    vp.preferred_language,
    vp.security_level
FROM voice_transactions vt
JOIN voice_commands vc ON vt.voice_command_id = vc.id
JOIN voice_sessions vs ON vt.voice_session_id = vs.id
JOIN voice_profiles vp ON vt.user_id = vp.user_id;

-- Voice Analytics Summary View
CREATE VIEW voice_analytics_summary AS
SELECT 
    DATE(occurred_at) as analytics_date,
    event_type,
    language,
    device_type,
    COUNT(*) as event_count,
    AVG(confidence_score) as avg_confidence,
    AVG(processing_duration_ms) as avg_processing_time,
    SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN success = false THEN 1 ELSE 0 END) as failure_count
FROM voice_analytics_events
WHERE occurred_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(occurred_at), event_type, language, device_type;

-- Comments for documentation
COMMENT ON TABLE voice_profiles IS 'Stores user voice biometric profiles and enrollment data';
COMMENT ON TABLE voice_sessions IS 'Manages voice interaction sessions with conversation state';
COMMENT ON TABLE voice_commands IS 'Records individual voice commands and their processing status';
COMMENT ON TABLE voice_transactions IS 'Tracks voice-initiated financial transactions';
COMMENT ON TABLE voice_biometric_enrollments IS 'Manages voice biometric enrollment process';
COMMENT ON TABLE voice_authentication_sessions IS 'Tracks voice authentication attempts';
COMMENT ON TABLE voice_analytics_events IS 'Stores voice interaction analytics data';
COMMENT ON TABLE voice_conversation_flows IS 'Defines conversation flows and responses';
COMMENT ON TABLE voice_security_events IS 'Logs voice-related security events and threats';
COMMENT ON TABLE voice_user_preferences IS 'Stores user voice interaction preferences';

-- Grant permissions (adjust based on your application user)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO voice_payment_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO voice_payment_user;