-- Create Transaction Patterns table for pattern analysis
CREATE TABLE transaction_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    user_id UUID NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    recipient_wallet_id UUID,
    recipient_user_id UUID,
    status VARCHAR(50) NOT NULL,
    pattern_type VARCHAR(50) CHECK (pattern_type IN ('NORMAL', 'SUSPICIOUS', 'HIGH_VELOCITY', 'STRUCTURING', 'ROUND_AMOUNT', 'OFF_HOURS', 'CROSS_BORDER', 'HIGH_RISK_COUNTRY', 'UNUSUAL_MERCHANT', 'ACCOUNT_TESTING', 'CIRCULAR_FLOW')),
    currency_code CHAR(3),
    country_code CHAR(2),
    is_cross_border BOOLEAN,
    payment_method VARCHAR(100),
    device_fingerprint VARCHAR(255),
    ip_address INET,
    merchant_category VARCHAR(100),
    risk_score INTEGER CHECK (risk_score BETWEEN 1 AND 100),
    velocity_count INTEGER,
    cumulative_amount DECIMAL(19,4),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    analyzed_at TIMESTAMP WITH TIME ZONE,
    analysis_result TEXT
);

-- Create indexes for efficient querying and pattern analysis
CREATE INDEX idx_transaction_patterns_transaction_id ON transaction_patterns(transaction_id);
CREATE INDEX idx_transaction_patterns_user_id ON transaction_patterns(user_id);
CREATE INDEX idx_transaction_patterns_timestamp ON transaction_patterns(timestamp);
CREATE INDEX idx_transaction_patterns_pattern_type ON transaction_patterns(pattern_type);
CREATE INDEX idx_transaction_patterns_risk_score ON transaction_patterns(risk_score) WHERE risk_score > 70;
CREATE INDEX idx_transaction_patterns_amount ON transaction_patterns(amount);
CREATE INDEX idx_transaction_patterns_currency ON transaction_patterns(currency_code);
CREATE INDEX idx_transaction_patterns_country ON transaction_patterns(country_code);
CREATE INDEX idx_transaction_patterns_cross_border ON transaction_patterns(is_cross_border) WHERE is_cross_border = true;

-- Composite indexes for complex queries
CREATE INDEX idx_transaction_patterns_user_timestamp ON transaction_patterns(user_id, timestamp);
CREATE INDEX idx_transaction_patterns_user_amount ON transaction_patterns(user_id, amount);
CREATE INDEX idx_transaction_patterns_type_timestamp ON transaction_patterns(pattern_type, timestamp);
CREATE INDEX idx_transaction_patterns_user_type ON transaction_patterns(user_id, pattern_type);
CREATE INDEX idx_transaction_patterns_velocity ON transaction_patterns(user_id, velocity_count) WHERE velocity_count > 5;

-- Partial index for suspicious patterns
CREATE INDEX idx_transaction_patterns_suspicious ON transaction_patterns(user_id, timestamp, amount) 
WHERE pattern_type IN ('SUSPICIOUS', 'STRUCTURING', 'HIGH_VELOCITY');

-- Add comments for documentation
COMMENT ON TABLE transaction_patterns IS 'Transaction patterns used for AML analysis and suspicious activity detection';
COMMENT ON COLUMN transaction_patterns.pattern_type IS 'Classification of the transaction pattern based on analysis';
COMMENT ON COLUMN transaction_patterns.risk_score IS 'Calculated risk score from 1-100 based on multiple factors';
COMMENT ON COLUMN transaction_patterns.velocity_count IS 'Number of transactions within the analysis time window';
COMMENT ON COLUMN transaction_patterns.cumulative_amount IS 'Running total amount for the analysis period';
COMMENT ON COLUMN transaction_patterns.device_fingerprint IS 'Device identification for fraud detection';
COMMENT ON COLUMN transaction_patterns.analysis_result IS 'Detailed results from pattern analysis algorithms';