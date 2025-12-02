-- Create Security Events table for comprehensive security monitoring
CREATE TABLE security_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    event_type VARCHAR(100) NOT NULL,
    event_category VARCHAR(50) NOT NULL CHECK (event_category IN ('AUTHENTICATION', 'AUTHORIZATION', 'TRANSACTION', 'SYSTEM', 'COMPLIANCE', 'FRAUD', 'DATA_ACCESS', 'CONFIGURATION')),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT NOT NULL,
    event_data JSONB,
    source_ip INET,
    user_agent TEXT,
    device_fingerprint VARCHAR(255),
    session_id VARCHAR(255),
    transaction_id UUID,
    alert_id UUID,
    location_country CHAR(2),
    location_city VARCHAR(100),
    risk_score INTEGER CHECK (risk_score BETWEEN 1 AND 100),
    automated_response VARCHAR(255),
    investigation_required BOOLEAN DEFAULT false,
    investigated_by UUID,
    investigated_at TIMESTAMP WITH TIME ZONE,
    investigation_notes TEXT,
    false_positive BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for efficient querying and security monitoring
CREATE INDEX idx_security_events_user_id ON security_events(user_id);
CREATE INDEX idx_security_events_type ON security_events(event_type);
CREATE INDEX idx_security_events_category ON security_events(event_category);
CREATE INDEX idx_security_events_severity ON security_events(severity);
CREATE INDEX idx_security_events_created_at ON security_events(created_at);
CREATE INDEX idx_security_events_transaction_id ON security_events(transaction_id);
CREATE INDEX idx_security_events_alert_id ON security_events(alert_id);
CREATE INDEX idx_security_events_source_ip ON security_events(source_ip);
CREATE INDEX idx_security_events_risk_score ON security_events(risk_score) WHERE risk_score > 70;
CREATE INDEX idx_security_events_investigation ON security_events(investigation_required) WHERE investigation_required = true;
CREATE INDEX idx_security_events_session ON security_events(session_id);
CREATE INDEX idx_security_events_device ON security_events(device_fingerprint);

-- Composite indexes for security analytics
CREATE INDEX idx_security_events_user_created ON security_events(user_id, created_at);
CREATE INDEX idx_security_events_type_severity ON security_events(event_type, severity);
CREATE INDEX idx_security_events_category_created ON security_events(event_category, created_at);
CREATE INDEX idx_security_events_ip_created ON security_events(source_ip, created_at);
CREATE INDEX idx_security_events_severity_created ON security_events(severity, created_at);

-- Partial indexes for high-priority events
CREATE INDEX idx_security_events_critical ON security_events(created_at, user_id) WHERE severity = 'CRITICAL';
CREATE INDEX idx_security_events_fraud ON security_events(created_at, risk_score) WHERE event_category = 'FRAUD';
CREATE INDEX idx_security_events_false_positive ON security_events(event_type, false_positive) WHERE false_positive IS NOT NULL;

-- JSONB indexes for event data queries
CREATE INDEX idx_security_events_data_gin ON security_events USING GIN (event_data);

-- Add comments for documentation
COMMENT ON TABLE security_events IS 'Comprehensive security events log for monitoring and investigation';
COMMENT ON COLUMN security_events.event_type IS 'Specific type of security event (e.g., LOGIN_FAILED, SUSPICIOUS_TRANSACTION)';
COMMENT ON COLUMN security_events.event_category IS 'High-level category for event classification';
COMMENT ON COLUMN security_events.event_data IS 'JSON data containing event-specific details and metadata';
COMMENT ON COLUMN security_events.risk_score IS 'Calculated risk score from 1-100 based on event analysis';
COMMENT ON COLUMN security_events.automated_response IS 'Automated action taken by the system in response to the event';
COMMENT ON COLUMN security_events.device_fingerprint IS 'Device identification for tracking and fraud detection';
COMMENT ON COLUMN security_events.false_positive IS 'Indicates if the event was determined to be a false positive after investigation';