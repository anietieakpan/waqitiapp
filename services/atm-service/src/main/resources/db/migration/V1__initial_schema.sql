-- ATM Service Initial Schema
-- Created: 2025-09-27
-- Description: Core ATM transaction and device management schema

CREATE TABLE IF NOT EXISTS atm_device (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atm_id VARCHAR(50) UNIQUE NOT NULL,
    location VARCHAR(255) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(50),
    country_code VARCHAR(3) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    device_type VARCHAR(50) NOT NULL,
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    serial_number VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    cash_available DECIMAL(15, 2) DEFAULT 0,
    cash_capacity DECIMAL(15, 2) NOT NULL,
    last_maintenance_date TIMESTAMP,
    next_maintenance_date TIMESTAMP,
    is_tampered BOOLEAN DEFAULT FALSE,
    tamper_detected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS atm_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    atm_id VARCHAR(50) NOT NULL,
    card_number_encrypted VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    fee DECIMAL(10, 2) DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255),
    biometric_verified BOOLEAN DEFAULT FALSE,
    biometric_type VARCHAR(50),
    session_id VARCHAR(100),
    ip_address VARCHAR(45),
    country_code VARCHAR(3),
    risk_score DECIMAL(5, 4),
    fraud_flags TEXT[],
    receipt_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_atm_device FOREIGN KEY (atm_id) REFERENCES atm_device(atm_id)
);

CREATE TABLE IF NOT EXISTS atm_cash_cassette (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atm_id VARCHAR(50) NOT NULL,
    cassette_number INTEGER NOT NULL,
    denomination DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    current_count INTEGER NOT NULL DEFAULT 0,
    capacity INTEGER NOT NULL,
    last_replenished_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_atm_device_cassette FOREIGN KEY (atm_id) REFERENCES atm_device(atm_id),
    CONSTRAINT unique_atm_cassette UNIQUE (atm_id, cassette_number)
);

CREATE TABLE IF NOT EXISTS atm_maintenance_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atm_id VARCHAR(50) NOT NULL,
    maintenance_type VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    issues_found TEXT[],
    actions_taken TEXT,
    parts_replaced TEXT[],
    next_maintenance_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_atm_device_maintenance FOREIGN KEY (atm_id) REFERENCES atm_device(atm_id)
);

CREATE TABLE IF NOT EXISTS atm_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atm_id VARCHAR(50) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_atm_device_alert FOREIGN KEY (atm_id) REFERENCES atm_device(atm_id)
);

-- Indexes for performance
CREATE INDEX idx_atm_device_status ON atm_device(status);
CREATE INDEX idx_atm_device_location ON atm_device(country_code, city);
CREATE INDEX idx_atm_device_tampered ON atm_device(is_tampered) WHERE is_tampered = true;

CREATE INDEX idx_atm_transaction_atm ON atm_transaction(atm_id);
CREATE INDEX idx_atm_transaction_user ON atm_transaction(user_id);
CREATE INDEX idx_atm_transaction_status ON atm_transaction(status);
CREATE INDEX idx_atm_transaction_created ON atm_transaction(created_at DESC);
CREATE INDEX idx_atm_transaction_fraud ON atm_transaction(risk_score DESC) WHERE risk_score > 0.7;

CREATE INDEX idx_atm_cassette_atm ON atm_cash_cassette(atm_id);
CREATE INDEX idx_atm_cassette_status ON atm_cash_cassette(status);

CREATE INDEX idx_atm_maintenance_atm ON atm_maintenance_log(atm_id);
CREATE INDEX idx_atm_maintenance_status ON atm_maintenance_log(status);

CREATE INDEX idx_atm_alert_atm ON atm_alert(atm_id);
CREATE INDEX idx_atm_alert_unresolved ON atm_alert(is_resolved) WHERE is_resolved = false;
CREATE INDEX idx_atm_alert_severity ON atm_alert(severity);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_atm_device_updated_at BEFORE UPDATE ON atm_device
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_atm_cassette_updated_at BEFORE UPDATE ON atm_cash_cassette
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();