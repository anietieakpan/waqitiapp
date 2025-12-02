-- Migration V1: Create SMS Banking Tables
-- Creates tables for SMS and USSD banking sessions

-- Create sms_sessions table
CREATE TABLE sms_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(20) NOT NULL,
    user_id UUID,
    session_id VARCHAR(100) UNIQUE NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('SMS', 'USSD')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN (
        'ACTIVE', 'COMPLETED', 'EXPIRED', 'TERMINATED', 'SUSPENDED'
    )),
    current_menu VARCHAR(50),
    current_step VARCHAR(50),
    session_data JSONB,
    transaction_context JSONB,
    language_preference VARCHAR(10) DEFAULT 'en',
    pin_attempts INTEGER DEFAULT 0,
    is_authenticated BOOLEAN DEFAULT FALSE,
    last_activity TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    timeout_minutes INTEGER DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_sms_sessions_phone_number ON sms_sessions(phone_number);
CREATE INDEX idx_sms_sessions_session_id ON sms_sessions(session_id);
CREATE INDEX idx_sms_sessions_user_id ON sms_sessions(user_id);
CREATE INDEX idx_sms_sessions_channel ON sms_sessions(channel);
CREATE INDEX idx_sms_sessions_status ON sms_sessions(status);
CREATE INDEX idx_sms_sessions_last_activity ON sms_sessions(last_activity);
CREATE INDEX idx_sms_sessions_created_at ON sms_sessions(created_at);

-- Create composite indexes for common queries
CREATE INDEX idx_sms_sessions_phone_status ON sms_sessions(phone_number, status);
CREATE INDEX idx_sms_sessions_channel_status ON sms_sessions(channel, status);
CREATE INDEX idx_sms_sessions_user_status ON sms_sessions(user_id, status);

-- Create function to update last_activity column
CREATE OR REPLACE FUNCTION update_last_activity_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_activity = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for last_activity
CREATE TRIGGER update_sms_sessions_last_activity 
    BEFORE UPDATE ON sms_sessions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_last_activity_column();

-- Add comments for documentation
COMMENT ON TABLE sms_sessions IS 'SMS and USSD banking sessions for tracking user interactions';

COMMENT ON COLUMN sms_sessions.phone_number IS 'User phone number in international format';
COMMENT ON COLUMN sms_sessions.session_id IS 'Unique session identifier from telecom gateway';
COMMENT ON COLUMN sms_sessions.channel IS 'Communication channel: SMS or USSD';
COMMENT ON COLUMN sms_sessions.status IS 'Current session status';
COMMENT ON COLUMN sms_sessions.current_menu IS 'Current menu position for USSD navigation';
COMMENT ON COLUMN sms_sessions.current_step IS 'Current step within a menu flow';
COMMENT ON COLUMN sms_sessions.session_data IS 'Session-specific data (recipient info, amounts, etc.)';
COMMENT ON COLUMN sms_sessions.transaction_context IS 'Transaction context for multi-step operations';
COMMENT ON COLUMN sms_sessions.language_preference IS 'User preferred language for responses';
COMMENT ON COLUMN sms_sessions.pin_attempts IS 'Number of PIN authentication attempts';
COMMENT ON COLUMN sms_sessions.is_authenticated IS 'Whether user has been authenticated with PIN';
COMMENT ON COLUMN sms_sessions.last_activity IS 'Timestamp of last user interaction';
COMMENT ON COLUMN sms_sessions.timeout_minutes IS 'Session timeout in minutes';
COMMENT ON COLUMN sms_sessions.ended_at IS 'When the session was ended';