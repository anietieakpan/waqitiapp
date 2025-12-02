-- Session Timeout Management Tables
-- For tracking and managing user sessions with timeout capabilities

-- User sessions table
CREATE TABLE IF NOT EXISTS user_sessions (
    session_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(255),
    ip_address INET NOT NULL,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    logout_at TIMESTAMP,
    logout_reason VARCHAR(50), -- MANUAL, TIMEOUT, FORCED, SECURITY
    session_data JSONB,
    
    CONSTRAINT chk_logout_reason CHECK (logout_reason IN (
        'MANUAL', 'TIMEOUT', 'FORCED', 'SECURITY', 'EXPIRED', 'CONCURRENT_LOGIN'
    ))
);

-- Session activity tracking
CREATE TABLE IF NOT EXISTS session_activities (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL REFERENCES user_sessions(session_id) ON DELETE CASCADE,
    activity_type VARCHAR(50) NOT NULL, -- PAGE_VIEW, API_CALL, TRANSACTION, etc.
    endpoint VARCHAR(500),
    ip_address INET,
    activity_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_time_ms INT,
    is_suspicious BOOLEAN DEFAULT FALSE
);

-- Session timeout configurations
CREATE TABLE IF NOT EXISTS session_timeout_configs (
    id BIGSERIAL PRIMARY KEY,
    user_type VARCHAR(50) NOT NULL UNIQUE, -- CUSTOMER, ADMIN, MERCHANT, SERVICE
    idle_timeout_minutes INT NOT NULL DEFAULT 15,
    absolute_timeout_minutes INT NOT NULL DEFAULT 480, -- 8 hours
    warning_before_timeout_minutes INT DEFAULT 2,
    max_concurrent_sessions INT DEFAULT 1,
    require_reauthentication_for VARCHAR(255)[], -- Array of sensitive operations
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_user_type CHECK (user_type IN (
        'CUSTOMER', 'ADMIN', 'MERCHANT', 'SERVICE', 'SUPPORT', 'DEFAULT'
    ))
);

-- Concurrent session tracking
CREATE TABLE IF NOT EXISTS concurrent_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    session_count INT NOT NULL DEFAULT 0,
    active_sessions VARCHAR(128)[], -- Array of active session IDs
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Session security events
CREATE TABLE IF NOT EXISTS session_security_events (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128),
    user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- SUSPICIOUS_ACTIVITY, TIMEOUT, HIJACK_ATTEMPT, etc.
    event_details JSONB,
    ip_address INET,
    severity VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_event_type CHECK (event_type IN (
        'SUSPICIOUS_ACTIVITY', 'TIMEOUT', 'HIJACK_ATTEMPT', 
        'IP_CHANGE', 'DEVICE_CHANGE', 'CONCURRENT_LOGIN_ATTEMPT',
        'BRUTE_FORCE', 'SESSION_FIXATION'
    )),
    CONSTRAINT chk_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Indexes for performance
CREATE INDEX idx_user_sessions_user ON user_sessions(user_id) WHERE is_active = TRUE;
CREATE INDEX idx_user_sessions_expires ON user_sessions(expires_at) WHERE is_active = TRUE;
CREATE INDEX idx_user_sessions_last_activity ON user_sessions(last_activity_at) WHERE is_active = TRUE;
CREATE INDEX idx_user_sessions_device ON user_sessions(device_id) WHERE is_active = TRUE;

CREATE INDEX idx_session_activities_session ON session_activities(session_id);
CREATE INDEX idx_session_activities_timestamp ON session_activities(activity_timestamp);
CREATE INDEX idx_session_activities_suspicious ON session_activities(session_id) WHERE is_suspicious = TRUE;

CREATE INDEX idx_concurrent_sessions_user ON concurrent_sessions(user_id);

CREATE INDEX idx_session_security_events_session ON session_security_events(session_id);
CREATE INDEX idx_session_security_events_user ON session_security_events(user_id);
CREATE INDEX idx_session_security_events_severity ON session_security_events(severity, occurred_at);

-- Function to check session timeout
CREATE OR REPLACE FUNCTION check_session_timeout(p_session_id VARCHAR(128))
RETURNS TABLE(is_timed_out BOOLEAN, timeout_type VARCHAR(20)) AS $$
DECLARE
    v_last_activity TIMESTAMP;
    v_created_at TIMESTAMP;
    v_user_id VARCHAR(255);
    v_idle_timeout INT;
    v_absolute_timeout INT;
BEGIN
    -- Get session details
    SELECT last_activity_at, created_at, user_id
    INTO v_last_activity, v_created_at, v_user_id
    FROM user_sessions
    WHERE session_id = p_session_id AND is_active = TRUE;
    
    IF NOT FOUND THEN
        RETURN QUERY SELECT TRUE, 'NOT_FOUND'::VARCHAR(20);
        RETURN;
    END IF;
    
    -- Get timeout configuration
    SELECT idle_timeout_minutes, absolute_timeout_minutes
    INTO v_idle_timeout, v_absolute_timeout
    FROM session_timeout_configs stc
    JOIN user_sessions us ON us.session_id = p_session_id
    WHERE stc.user_type = COALESCE(
        (SELECT user_type FROM users WHERE user_id = v_user_id),
        'DEFAULT'
    ) AND stc.is_active = TRUE;
    
    IF NOT FOUND THEN
        -- Use default values
        v_idle_timeout := 15;
        v_absolute_timeout := 480;
    END IF;
    
    -- Check idle timeout
    IF v_last_activity + (v_idle_timeout || ' minutes')::INTERVAL < CURRENT_TIMESTAMP THEN
        RETURN QUERY SELECT TRUE, 'IDLE'::VARCHAR(20);
        RETURN;
    END IF;
    
    -- Check absolute timeout
    IF v_created_at + (v_absolute_timeout || ' minutes')::INTERVAL < CURRENT_TIMESTAMP THEN
        RETURN QUERY SELECT TRUE, 'ABSOLUTE'::VARCHAR(20);
        RETURN;
    END IF;
    
    RETURN QUERY SELECT FALSE, NULL::VARCHAR(20);
END;
$$ LANGUAGE plpgsql;

-- Function to update session activity
CREATE OR REPLACE FUNCTION update_session_activity(p_session_id VARCHAR(128))
RETURNS VOID AS $$
BEGIN
    UPDATE user_sessions
    SET last_activity_at = CURRENT_TIMESTAMP
    WHERE session_id = p_session_id AND is_active = TRUE;
END;
$$ LANGUAGE plpgsql;

-- Function to cleanup expired sessions
CREATE OR REPLACE FUNCTION cleanup_expired_sessions()
RETURNS INT AS $$
DECLARE
    v_count INT;
BEGIN
    UPDATE user_sessions
    SET is_active = FALSE,
        logout_at = CURRENT_TIMESTAMP,
        logout_reason = 'EXPIRED'
    WHERE is_active = TRUE
    AND (expires_at < CURRENT_TIMESTAMP
         OR last_activity_at + INTERVAL '24 hours' < CURRENT_TIMESTAMP);
    
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update concurrent sessions
CREATE OR REPLACE FUNCTION update_concurrent_sessions()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.is_active = TRUE THEN
        INSERT INTO concurrent_sessions (user_id, session_count, active_sessions)
        VALUES (NEW.user_id, 1, ARRAY[NEW.session_id])
        ON CONFLICT (user_id) DO UPDATE
        SET session_count = concurrent_sessions.session_count + 1,
            active_sessions = array_append(concurrent_sessions.active_sessions, NEW.session_id),
            last_updated = CURRENT_TIMESTAMP;
    ELSIF TG_OP = 'UPDATE' AND OLD.is_active = TRUE AND NEW.is_active = FALSE THEN
        UPDATE concurrent_sessions
        SET session_count = GREATEST(0, session_count - 1),
            active_sessions = array_remove(active_sessions, NEW.session_id),
            last_updated = CURRENT_TIMESTAMP
        WHERE user_id = NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_concurrent_sessions
    AFTER INSERT OR UPDATE ON user_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_concurrent_sessions();

-- Default timeout configurations
INSERT INTO session_timeout_configs (user_type, idle_timeout_minutes, absolute_timeout_minutes, warning_before_timeout_minutes, max_concurrent_sessions)
VALUES 
    ('CUSTOMER', 15, 480, 2, 3),
    ('ADMIN', 30, 240, 5, 1),
    ('MERCHANT', 20, 600, 3, 2),
    ('SERVICE', 60, 1440, 0, 10),
    ('SUPPORT', 30, 480, 5, 1),
    ('DEFAULT', 15, 480, 2, 1)
ON CONFLICT (user_type) DO NOTHING;

-- Comments for documentation
COMMENT ON TABLE user_sessions IS 'Tracks all user sessions with timeout management';
COMMENT ON TABLE session_activities IS 'Detailed activity log for each session';
COMMENT ON TABLE session_timeout_configs IS 'Configurable timeout rules per user type';
COMMENT ON TABLE concurrent_sessions IS 'Tracks concurrent session count per user';
COMMENT ON TABLE session_security_events IS 'Security events related to session management';
COMMENT ON FUNCTION check_session_timeout IS 'Checks if a session has timed out';
COMMENT ON FUNCTION update_session_activity IS 'Updates last activity timestamp for a session';
COMMENT ON FUNCTION cleanup_expired_sessions IS 'Cleans up expired sessions (should be run periodically)';