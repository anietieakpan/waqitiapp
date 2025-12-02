-- Crypto Transaction Patterns Table
-- Stores cryptocurrency transaction patterns for behavioral analysis and fraud detection
-- Supports crypto-specific fraud detection and risk assessment

CREATE TABLE IF NOT EXISTS crypto_transaction_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    transaction_type VARCHAR(20),
    
    -- Amount fields
    currency VARCHAR(10) NOT NULL,
    amount DECIMAL(30, 18) NOT NULL,
    amount_usd DECIMAL(19, 4),
    
    -- Address fields
    from_address VARCHAR(255),
    to_address VARCHAR(255),
    
    -- Temporal fields
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hour_of_day INTEGER,
    day_of_week INTEGER,
    is_weekend BOOLEAN DEFAULT FALSE,
    is_night_time BOOLEAN DEFAULT FALSE,
    
    -- Device and location fields
    device_fingerprint VARCHAR(255),
    device_type VARCHAR(50),
    ip_address VARCHAR(45),
    country_code VARCHAR(2),
    city VARCHAR(100),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    
    -- Pattern indicators
    is_international BOOLEAN DEFAULT FALSE,
    is_high_value BOOLEAN DEFAULT FALSE,
    is_round_amount BOOLEAN DEFAULT FALSE,
    
    -- Risk scores
    risk_score DECIMAL(5, 2),
    address_risk_score DECIMAL(5, 2),
    behavioral_risk_score DECIMAL(5, 2),
    velocity_risk_score DECIMAL(5, 2),
    pattern_risk_score DECIMAL(5, 2),
    amount_risk_score DECIMAL(5, 2),
    fraud_probability DECIMAL(5, 4),
    
    -- Address risk flags
    is_sanctioned_address BOOLEAN DEFAULT FALSE,
    is_mixer_address BOOLEAN DEFAULT FALSE,
    is_dark_market_address BOOLEAN DEFAULT FALSE,
    is_gambling_address BOOLEAN DEFAULT FALSE,
    is_ransomware_address BOOLEAN DEFAULT FALSE,
    is_new_address BOOLEAN DEFAULT FALSE,
    is_exchange_address BOOLEAN DEFAULT FALSE,
    has_risky_connections BOOLEAN DEFAULT FALSE,
    
    -- Assessment metadata
    network_risk_level VARCHAR(20),
    recommended_action VARCHAR(50),
    flagged_for_review BOOLEAN DEFAULT FALSE,
    reviewed BOOLEAN DEFAULT FALSE,
    reviewer_notes TEXT,
    analysis_timestamp TIMESTAMP,
    ml_model_version VARCHAR(50),
    
    -- Blockchain metadata
    blockchain_confirmations INTEGER,
    transaction_fee DECIMAL(30, 18),
    transaction_fee_usd DECIMAL(19, 4),
    gas_used BIGINT,
    gas_price DECIMAL(30, 18),
    block_number BIGINT,
    blockchain_timestamp TIMESTAMP,
    
    -- JSON metadata
    risk_factors JSONB DEFAULT '{}'::jsonb,
    extended_metadata JSONB DEFAULT '{}'::jsonb,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance optimization
CREATE INDEX idx_crypto_pattern_user_id ON crypto_transaction_patterns(user_id);
CREATE INDEX idx_crypto_pattern_timestamp ON crypto_transaction_patterns(timestamp DESC);
CREATE INDEX idx_crypto_pattern_user_timestamp ON crypto_transaction_patterns(user_id, timestamp DESC);
CREATE INDEX idx_crypto_pattern_to_address ON crypto_transaction_patterns(to_address);
CREATE INDEX idx_crypto_pattern_from_address ON crypto_transaction_patterns(from_address);
CREATE INDEX idx_crypto_pattern_device ON crypto_transaction_patterns(device_fingerprint);
CREATE INDEX idx_crypto_pattern_risk ON crypto_transaction_patterns(risk_score DESC);
CREATE INDEX idx_crypto_pattern_country ON crypto_transaction_patterns(country_code);
CREATE INDEX idx_crypto_pattern_flagged ON crypto_transaction_patterns(flagged_for_review) WHERE flagged_for_review = TRUE;
CREATE INDEX idx_crypto_pattern_high_risk ON crypto_transaction_patterns(risk_score) WHERE risk_score >= 60.0;
CREATE INDEX idx_crypto_pattern_currency ON crypto_transaction_patterns(currency);
CREATE INDEX idx_crypto_pattern_amount ON crypto_transaction_patterns(amount_usd DESC NULLS LAST);

-- Composite indexes for common queries
CREATE INDEX idx_crypto_pattern_user_currency ON crypto_transaction_patterns(user_id, currency);
CREATE INDEX idx_crypto_pattern_user_risk ON crypto_transaction_patterns(user_id, risk_score DESC);
CREATE INDEX idx_crypto_pattern_time_range ON crypto_transaction_patterns(timestamp DESC, user_id);

-- GIN indexes for JSONB columns
CREATE INDEX idx_crypto_pattern_risk_factors ON crypto_transaction_patterns USING GIN (risk_factors);
CREATE INDEX idx_crypto_pattern_metadata ON crypto_transaction_patterns USING GIN (extended_metadata);

-- Partial indexes for specific queries
CREATE INDEX idx_crypto_pattern_sanctioned ON crypto_transaction_patterns(to_address) 
    WHERE is_sanctioned_address = TRUE;
CREATE INDEX idx_crypto_pattern_mixer ON crypto_transaction_patterns(to_address) 
    WHERE is_mixer_address = TRUE;
CREATE INDEX idx_crypto_pattern_dark_market ON crypto_transaction_patterns(to_address) 
    WHERE is_dark_market_address = TRUE;

-- Foreign key constraints
ALTER TABLE crypto_transaction_patterns
    ADD CONSTRAINT fk_crypto_pattern_transaction
    FOREIGN KEY (transaction_id) 
    REFERENCES crypto_transactions(transaction_hash)
    ON DELETE CASCADE
    ON UPDATE CASCADE;

-- Trigger for automatic updated_at timestamp
CREATE OR REPLACE FUNCTION update_crypto_pattern_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    
    -- Auto-populate derived fields
    IF NEW.timestamp IS NOT NULL THEN
        NEW.hour_of_day = EXTRACT(HOUR FROM NEW.timestamp);
        NEW.day_of_week = EXTRACT(DOW FROM NEW.timestamp);
        NEW.is_weekend = (NEW.day_of_week IN (0, 6));
        NEW.is_night_time = (NEW.hour_of_day >= 22 OR NEW.hour_of_day <= 6);
    END IF;
    
    -- Auto-populate risk flags based on amount
    IF NEW.amount_usd IS NOT NULL THEN
        NEW.is_high_value = (NEW.amount_usd > 10000);
        NEW.is_round_amount = (
            MOD(NEW.amount_usd::numeric, 1000) = 0 OR
            MOD(NEW.amount_usd::numeric, 500) = 0 OR
            MOD(NEW.amount_usd::numeric, 100) = 0
        );
    END IF;
    
    -- Set analysis timestamp if not provided
    IF NEW.analysis_timestamp IS NULL THEN
        NEW.analysis_timestamp = CURRENT_TIMESTAMP;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_crypto_pattern_timestamp
    BEFORE INSERT OR UPDATE ON crypto_transaction_patterns
    FOR EACH ROW
    EXECUTE FUNCTION update_crypto_pattern_timestamp();

-- Comments for documentation
COMMENT ON TABLE crypto_transaction_patterns IS 'Stores cryptocurrency transaction patterns for behavioral analysis and fraud detection';
COMMENT ON COLUMN crypto_transaction_patterns.risk_score IS 'Overall risk score (0-100)';
COMMENT ON COLUMN crypto_transaction_patterns.fraud_probability IS 'ML-calculated fraud probability (0-1)';
COMMENT ON COLUMN crypto_transaction_patterns.is_sanctioned_address IS 'TRUE if address is on sanctions list';
COMMENT ON COLUMN crypto_transaction_patterns.is_mixer_address IS 'TRUE if address is known mixer/tumbler service';
COMMENT ON COLUMN crypto_transaction_patterns.recommended_action IS 'System recommended action: ALLOW, MONITOR, ADDITIONAL_VERIFICATION, MANUAL_REVIEW, BLOCK';
COMMENT ON COLUMN crypto_transaction_patterns.risk_factors IS 'JSONB object containing specific risk factors identified';
COMMENT ON COLUMN crypto_transaction_patterns.extended_metadata IS 'JSONB object for additional transaction metadata';

-- Grant permissions (adjust based on your security model)
GRANT SELECT, INSERT, UPDATE ON crypto_transaction_patterns TO crypto_service_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO crypto_service_app;