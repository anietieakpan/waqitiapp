-- Create audit table for instant transfers
CREATE TABLE IF NOT EXISTS instant_transfer_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES instant_transfers(id),
    action VARCHAR(50) NOT NULL,
    performed_by UUID,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create instant transfer limits table
CREATE TABLE IF NOT EXISTS instant_transfer_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transfer_method VARCHAR(50) NOT NULL,
    daily_limit DECIMAL(19, 2) NOT NULL DEFAULT 10000.00,
    daily_count_limit INTEGER NOT NULL DEFAULT 10,
    monthly_limit DECIMAL(19, 2) NOT NULL DEFAULT 100000.00,
    monthly_count_limit INTEGER NOT NULL DEFAULT 100,
    current_daily_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    current_daily_count INTEGER NOT NULL DEFAULT 0,
    current_monthly_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    current_monthly_count INTEGER NOT NULL DEFAULT 0,
    last_reset_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, transfer_method)
);

-- Create instant transfer velocity tracking table
CREATE TABLE IF NOT EXISTS instant_transfer_velocity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    window_type VARCHAR(50) NOT NULL, -- MINUTE, HOUR, DAY, WEEK, MONTH
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    total_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    unique_recipients INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create instant transfer network fees table
CREATE TABLE IF NOT EXISTS instant_transfer_fees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES instant_transfers(id),
    network_fee DECIMAL(10, 2),
    processing_fee DECIMAL(10, 2),
    total_fee DECIMAL(10, 2),
    fee_currency VARCHAR(3) DEFAULT 'USD',
    fee_paid_by VARCHAR(20), -- SENDER, RECIPIENT, SPLIT
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create instant transfer retry table
CREATE TABLE IF NOT EXISTS instant_transfer_retries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES instant_transfers(id),
    retry_attempt INTEGER NOT NULL,
    retry_reason VARCHAR(500),
    retry_status VARCHAR(50),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for audit and tracking tables
CREATE INDEX idx_instant_transfer_audit_transfer_id ON instant_transfer_audit(transfer_id);
CREATE INDEX idx_instant_transfer_audit_created_at ON instant_transfer_audit(created_at DESC);
CREATE INDEX idx_instant_transfer_audit_action ON instant_transfer_audit(action);

CREATE INDEX idx_instant_transfer_limits_user_id ON instant_transfer_limits(user_id);
CREATE INDEX idx_instant_transfer_limits_user_method ON instant_transfer_limits(user_id, transfer_method);

CREATE INDEX idx_instant_transfer_velocity_user_id ON instant_transfer_velocity(user_id);
CREATE INDEX idx_instant_transfer_velocity_window ON instant_transfer_velocity(window_type, window_start, window_end);

CREATE INDEX idx_instant_transfer_fees_transfer_id ON instant_transfer_fees(transfer_id);

CREATE INDEX idx_instant_transfer_retries_transfer_id ON instant_transfer_retries(transfer_id);
CREATE INDEX idx_instant_transfer_retries_created_at ON instant_transfer_retries(created_at DESC);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers to auto-update updated_at
CREATE TRIGGER update_instant_transfers_updated_at BEFORE UPDATE ON instant_transfers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_instant_transfer_limits_updated_at BEFORE UPDATE ON instant_transfer_limits
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_instant_transfer_velocity_updated_at BEFORE UPDATE ON instant_transfer_velocity
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add comments
COMMENT ON TABLE instant_transfer_audit IS 'Audit trail for instant transfer operations';
COMMENT ON TABLE instant_transfer_limits IS 'Transfer limits per user and method';
COMMENT ON TABLE instant_transfer_velocity IS 'Velocity tracking for fraud detection';
COMMENT ON TABLE instant_transfer_fees IS 'Fee tracking for instant transfers';
COMMENT ON TABLE instant_transfer_retries IS 'Retry history for failed transfers';