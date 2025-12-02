-- Create crypto fraud events table
CREATE TABLE crypto_fraud_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID REFERENCES crypto_transactions(id),
    user_id UUID NOT NULL,
    currency VARCHAR(20) NOT NULL,
    amount DECIMAL(36,18) NOT NULL,
    to_address VARCHAR(255),
    risk_score DECIMAL(5,2) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    recommended_action VARCHAR(30) NOT NULL,
    risk_factors TEXT,
    ip_address INET,
    device_fingerprint VARCHAR(255),
    fraud_type VARCHAR(50),
    confidence_level DECIMAL(3,2),
    investigation_status VARCHAR(20) DEFAULT 'OPEN',
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_crypto_fraud_events_currency CHECK (currency IN ('BITCOIN', 'ETHEREUM', 'LITECOIN', 'USDC', 'USDT')),
    CONSTRAINT chk_crypto_fraud_events_risk_level CHECK (risk_level IN ('MINIMAL', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_crypto_fraud_events_action CHECK (recommended_action IN ('ALLOW', 'MONITOR', 'ADDITIONAL_VERIFICATION', 'MANUAL_REVIEW', 'BLOCK')),
    CONSTRAINT chk_crypto_fraud_events_investigation CHECK (investigation_status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'FALSE_POSITIVE')),
    CONSTRAINT chk_crypto_fraud_events_risk_score CHECK (risk_score >= 0 AND risk_score <= 100),
    CONSTRAINT chk_crypto_fraud_events_confidence CHECK (confidence_level >= 0 AND confidence_level <= 1)
);

-- Create indexes
CREATE INDEX idx_crypto_fraud_events_transaction_id ON crypto_fraud_events(transaction_id);
CREATE INDEX idx_crypto_fraud_events_user_id ON crypto_fraud_events(user_id);
CREATE INDEX idx_crypto_fraud_events_risk_level ON crypto_fraud_events(risk_level);
CREATE INDEX idx_crypto_fraud_events_risk_score ON crypto_fraud_events(risk_score);
CREATE INDEX idx_crypto_fraud_events_investigation_status ON crypto_fraud_events(investigation_status);
CREATE INDEX idx_crypto_fraud_events_created_at ON crypto_fraud_events(created_at);
CREATE INDEX idx_crypto_fraud_events_ip_address ON crypto_fraud_events(ip_address);
CREATE INDEX idx_crypto_fraud_events_device_fingerprint ON crypto_fraud_events(device_fingerprint);