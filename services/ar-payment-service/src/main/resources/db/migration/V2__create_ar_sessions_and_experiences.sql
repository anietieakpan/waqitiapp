-- AR Payment Service - Augmented Reality Payment Schema
-- Created: 2025-11-15
-- Description: AR sessions and AR payment experiences for spatial/gesture-based payments

-- Enable PostGIS extension for spatial queries
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- AR Sessions Table
-- =====================================================
CREATE TABLE IF NOT EXISTS ar_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_token VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    session_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    device_id VARCHAR(100),
    device_type VARCHAR(50),
    ar_platform VARCHAR(50),
    ar_platform_version VARCHAR(20),

    -- Device capabilities and spatial data (JSONB)
    device_capabilities JSONB,
    spatial_mapping_data JSONB,
    anchor_points JSONB,

    -- Location data
    current_location_lat DOUBLE PRECISION,
    current_location_lng DOUBLE PRECISION,
    location_accuracy DOUBLE PRECISION,
    indoor_location TEXT,

    -- Spatial geometry for PostGIS queries
    location_point GEOMETRY(Point, 4326),

    -- Detected surfaces and objects (JSONB)
    detected_surfaces JSONB,
    recognized_objects JSONB,
    active_overlays JSONB,

    -- Payment information
    payment_amount DECIMAL(19, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    recipient_id UUID,
    recipient_name VARCHAR(100),
    payment_id UUID,

    -- Interaction tracking
    interaction_count INTEGER DEFAULT 0,
    gesture_count INTEGER DEFAULT 0,
    interaction_history JSONB,

    -- AR quality metrics
    ar_quality_score DOUBLE PRECISION,
    tracking_quality VARCHAR(20),
    lighting_intensity DOUBLE PRECISION,
    frame_rate INTEGER,
    performance_metrics JSONB,

    -- Session metadata
    session_metadata JSONB,

    -- Timestamps
    started_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP,
    ended_at TIMESTAMP,
    duration_seconds BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Optimistic locking
    version BIGINT DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_ar_session_status CHECK (status IN ('INITIALIZING', 'ACTIVE', 'PAUSED', 'BACKGROUND', 'ENDED', 'ERROR', 'TIMEOUT')),
    CONSTRAINT chk_ar_session_type CHECK (session_type IN ('PAYMENT_SCAN', 'SPATIAL_PAYMENT', 'VIRTUAL_STOREFRONT', 'BILL_SPLIT_AR', 'CRYPTO_WALLET_AR', 'MERCHANT_DISCOVERY', 'SOCIAL_PAYMENT', 'GAMIFIED_PAYMENT', 'EDUCATIONAL', 'DEMO')),
    CONSTRAINT chk_ar_quality_score CHECK (ar_quality_score IS NULL OR (ar_quality_score >= 0 AND ar_quality_score <= 1)),
    CONSTRAINT chk_payment_amount_positive CHECK (payment_amount IS NULL OR payment_amount >= 0),
    CONSTRAINT chk_currency_code CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_interaction_counts CHECK (interaction_count >= 0 AND gesture_count >= 0)
);

-- =====================================================
-- AR Payment Experiences Table
-- =====================================================
CREATE TABLE IF NOT EXISTS ar_payment_experiences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experience_id VARCHAR(50) UNIQUE NOT NULL,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    experience_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    payment_method VARCHAR(30),

    -- Payment details
    amount DECIMAL(19, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    recipient_id UUID,
    recipient_identifier VARCHAR(200),
    merchant_id UUID,
    product_ids TEXT,

    -- AR visualization data (JSONB)
    ar_visualization_data JSONB,
    interaction_points JSONB,
    gesture_sequence JSONB,

    -- AR markers and codes
    qr_code_data TEXT,
    ar_marker_id VARCHAR(100),

    -- Spatial payment data (JSONB)
    spatial_payment_data JSONB,
    visualization_effects JSONB,

    -- Confirmation details
    confirmation_method VARCHAR(30),
    confirmation_timestamp TIMESTAMP,

    -- Security and biometrics
    security_score DOUBLE PRECISION,
    biometric_data JSONB,
    face_id_verified BOOLEAN,
    gesture_accuracy DOUBLE PRECISION,
    environment_scan_quality DOUBLE PRECISION,

    -- Gamification (JSONB)
    gamification_elements JSONB,
    points_earned INTEGER DEFAULT 0,
    achievement_unlocked VARCHAR(100),

    -- Social sharing
    social_sharing_data JSONB,
    is_shared_to_feed BOOLEAN DEFAULT FALSE,
    ar_screenshot_url VARCHAR(500),
    ar_video_url VARCHAR(500),

    -- Analytics and performance
    analytics_data JSONB,
    interaction_duration_seconds BIGINT,
    gesture_count INTEGER DEFAULT 0,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,

    -- Payment transaction references
    payment_id UUID,
    transaction_id VARCHAR(100),

    -- Timestamps
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Optimistic locking
    version BIGINT DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_ar_experience_status CHECK (status IN ('INITIATED', 'SCANNING', 'PROCESSING', 'CONFIRMING', 'AUTHORIZED', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT')),
    CONSTRAINT chk_ar_experience_type CHECK (experience_type IN ('QR_SCAN_TO_PAY', 'AR_MARKER_PAYMENT', 'SPATIAL_DROP', 'GESTURE_PAYMENT', 'OBJECT_RECOGNITION', 'VIRTUAL_SHOPPING', 'HOLOGRAPHIC_WALLET', 'SOCIAL_AR_PAYMENT', 'CRYPTO_AR_TRADING', 'BILL_SPLIT_VISUAL', 'AR_INVOICE_VIEW', 'LOCATION_BASED', 'GAME_REWARD', 'EDUCATIONAL_AR')),
    CONSTRAINT chk_ar_payment_method CHECK (payment_method IS NULL OR payment_method IN ('QR_CODE', 'AR_MARKER', 'GESTURE', 'OBJECT_SCAN', 'SPATIAL_TAP', 'VOICE_COMMAND', 'GAZE_SELECTION', 'HAND_TRACKING', 'FACIAL_RECOGNITION')),
    CONSTRAINT chk_experience_amount_positive CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT chk_experience_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_security_score CHECK (security_score IS NULL OR (security_score >= 0 AND security_score <= 1)),
    CONSTRAINT chk_gesture_accuracy CHECK (gesture_accuracy IS NULL OR (gesture_accuracy >= 0 AND gesture_accuracy <= 1)),
    CONSTRAINT chk_scan_quality CHECK (environment_scan_quality IS NULL OR (environment_scan_quality >= 0 AND environment_scan_quality <= 1)),
    CONSTRAINT chk_experience_counts CHECK (points_earned >= 0 AND gesture_count >= 0 AND retry_count >= 0),
    CONSTRAINT fk_ar_experience_session FOREIGN KEY (session_id) REFERENCES ar_sessions(id) ON DELETE CASCADE
);

-- =====================================================
-- Indexes for Performance
-- =====================================================

-- AR Sessions Indexes
CREATE INDEX IF NOT EXISTS idx_ar_sessions_user_id ON ar_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_session_token ON ar_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_status ON ar_sessions(status);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_session_type ON ar_sessions(session_type);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_device_id ON ar_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_payment_id ON ar_sessions(payment_id);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_started_at ON ar_sessions(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_created_at ON ar_sessions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_user_status ON ar_sessions(user_id, status);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_active ON ar_sessions(status, last_active_at DESC) WHERE status IN ('ACTIVE', 'PAUSED');

-- PostGIS spatial index for location queries
CREATE INDEX IF NOT EXISTS idx_ar_sessions_location ON ar_sessions USING GIST(location_point);

-- JSONB indexes for frequently queried fields
CREATE INDEX IF NOT EXISTS idx_ar_sessions_device_capabilities ON ar_sessions USING GIN(device_capabilities);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_performance_metrics ON ar_sessions USING GIN(performance_metrics);
CREATE INDEX IF NOT EXISTS idx_ar_sessions_session_metadata ON ar_sessions USING GIN(session_metadata);

-- AR Payment Experiences Indexes
CREATE INDEX IF NOT EXISTS idx_ar_experiences_experience_id ON ar_payment_experiences(experience_id);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_session_id ON ar_payment_experiences(session_id);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_user_id ON ar_payment_experiences(user_id);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_status ON ar_payment_experiences(status);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_experience_type ON ar_payment_experiences(experience_type);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_recipient_id ON ar_payment_experiences(recipient_id);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_merchant_id ON ar_payment_experiences(merchant_id);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_payment_id ON ar_payment_experiences(payment_id);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_transaction_id ON ar_payment_experiences(transaction_id);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_started_at ON ar_payment_experiences(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_created_at ON ar_payment_experiences(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_user_status ON ar_payment_experiences(user_id, status);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_session_status ON ar_payment_experiences(session_id, status);

-- JSONB indexes for complex queries
CREATE INDEX IF NOT EXISTS idx_ar_experiences_analytics ON ar_payment_experiences USING GIN(analytics_data);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_gamification ON ar_payment_experiences USING GIN(gamification_elements);
CREATE INDEX IF NOT EXISTS idx_ar_experiences_visualization ON ar_payment_experiences USING GIN(ar_visualization_data);

-- =====================================================
-- Functions and Triggers
-- =====================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_ar_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to automatically set location_point from lat/lng
CREATE OR REPLACE FUNCTION update_ar_session_location_point()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.current_location_lat IS NOT NULL AND NEW.current_location_lng IS NOT NULL THEN
        NEW.location_point = ST_SetSRID(ST_MakePoint(NEW.current_location_lng, NEW.current_location_lat), 4326);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to calculate session duration on end
CREATE OR REPLACE FUNCTION calculate_ar_session_duration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('ENDED', 'ERROR', 'TIMEOUT') AND NEW.ended_at IS NOT NULL AND NEW.started_at IS NOT NULL THEN
        NEW.duration_seconds = EXTRACT(EPOCH FROM (NEW.ended_at - NEW.started_at))::BIGINT;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to calculate experience interaction duration
CREATE OR REPLACE FUNCTION calculate_ar_experience_duration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT')
       AND NEW.completed_at IS NOT NULL
       AND NEW.started_at IS NOT NULL THEN
        NEW.interaction_duration_seconds = EXTRACT(EPOCH FROM (NEW.completed_at - NEW.started_at))::BIGINT;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for ar_sessions
CREATE TRIGGER trigger_ar_sessions_updated_at
    BEFORE UPDATE ON ar_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_ar_updated_at_column();

CREATE TRIGGER trigger_ar_sessions_location_point
    BEFORE INSERT OR UPDATE OF current_location_lat, current_location_lng ON ar_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_ar_session_location_point();

CREATE TRIGGER trigger_ar_sessions_duration
    BEFORE UPDATE OF status, ended_at ON ar_sessions
    FOR EACH ROW
    EXECUTE FUNCTION calculate_ar_session_duration();

-- Triggers for ar_payment_experiences
CREATE TRIGGER trigger_ar_experiences_updated_at
    BEFORE UPDATE ON ar_payment_experiences
    FOR EACH ROW
    EXECUTE FUNCTION update_ar_updated_at_column();

CREATE TRIGGER trigger_ar_experiences_duration
    BEFORE UPDATE OF status, completed_at ON ar_payment_experiences
    FOR EACH ROW
    EXECUTE FUNCTION calculate_ar_experience_duration();

-- =====================================================
-- Comments for Documentation
-- =====================================================

COMMENT ON TABLE ar_sessions IS 'Augmented Reality payment sessions tracking AR device state, spatial mapping, and user interactions';
COMMENT ON TABLE ar_payment_experiences IS 'Individual AR payment experiences within sessions, tracking gestures, visualizations, and payment flow';

COMMENT ON COLUMN ar_sessions.session_token IS 'Unique session identifier for AR session management';
COMMENT ON COLUMN ar_sessions.device_capabilities IS 'JSONB: AR device capabilities (tracking, depth sensing, hand tracking, etc.)';
COMMENT ON COLUMN ar_sessions.spatial_mapping_data IS 'JSONB: 3D spatial mapping data from AR device';
COMMENT ON COLUMN ar_sessions.anchor_points IS 'JSONB: AR anchor points for spatial reference';
COMMENT ON COLUMN ar_sessions.location_point IS 'PostGIS geometry point for spatial queries';
COMMENT ON COLUMN ar_sessions.ar_quality_score IS 'Overall AR tracking quality score (0-1)';

COMMENT ON COLUMN ar_payment_experiences.experience_id IS 'Unique identifier for this AR payment experience';
COMMENT ON COLUMN ar_payment_experiences.gesture_sequence IS 'JSONB: Sequence of hand gestures performed during payment';
COMMENT ON COLUMN ar_payment_experiences.spatial_payment_data IS 'JSONB: 3D spatial payment data (drop location, coordinates, animations)';
COMMENT ON COLUMN ar_payment_experiences.biometric_data IS 'JSONB: Biometric verification data (hashed/encrypted)';
COMMENT ON COLUMN ar_payment_experiences.gamification_elements IS 'JSONB: AR gamification achievements, badges, and rewards';
