-- Create Fraud Rule Definitions Table
-- Stores configurable fraud detection rules

CREATE TABLE IF NOT EXISTS fraud_rule_definitions (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(100) NOT NULL UNIQUE,
    rule_name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    severity VARCHAR(20) NOT NULL,
    risk_score DECIMAL(5, 2) NOT NULL,
    weight DECIMAL(5, 2),
    priority INTEGER NOT NULL DEFAULT 50,
    enabled BOOLEAN NOT NULL DEFAULT true,
    min_amount DECIMAL(19, 2),
    max_amount DECIMAL(19, 2),
    historical_accuracy DECIMAL(5, 2),
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    
    CONSTRAINT check_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT check_risk_score CHECK (risk_score >= 0 AND risk_score <= 1),
    CONSTRAINT check_weight CHECK (weight IS NULL OR (weight >= 0 AND weight <= 10)),
    CONSTRAINT check_accuracy CHECK (historical_accuracy IS NULL OR (historical_accuracy >= 0 AND historical_accuracy <= 1)),
    CONSTRAINT check_amount_range CHECK (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount)
);

CREATE INDEX idx_fraud_rule_code ON fraud_rule_definitions(rule_code);
CREATE INDEX idx_fraud_rule_enabled ON fraud_rule_definitions(enabled);
CREATE INDEX idx_fraud_rule_priority ON fraud_rule_definitions(priority);
CREATE INDEX idx_fraud_rule_category ON fraud_rule_definitions(category);
CREATE INDEX idx_fraud_rule_severity ON fraud_rule_definitions(severity);

CREATE TABLE IF NOT EXISTS fraud_rule_transaction_types (
    rule_id BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    
    CONSTRAINT fk_rule_transaction_types FOREIGN KEY (rule_id) REFERENCES fraud_rule_definitions(id) ON DELETE CASCADE,
    PRIMARY KEY (rule_id, transaction_type)
);

CREATE TABLE IF NOT EXISTS fraud_rule_parameters (
    rule_id BIGINT NOT NULL,
    param_key VARCHAR(100) NOT NULL,
    param_value TEXT,
    
    CONSTRAINT fk_rule_parameters FOREIGN KEY (rule_id) REFERENCES fraud_rule_definitions(id) ON DELETE CASCADE,
    PRIMARY KEY (rule_id, param_key)
);

COMMENT ON TABLE fraud_rule_definitions IS 'Configurable fraud detection rules for dynamic fraud prevention';
COMMENT ON COLUMN fraud_rule_definitions.rule_code IS 'Unique identifier for the rule (e.g., HIGH_VALUE_TRANSACTION)';
COMMENT ON COLUMN fraud_rule_definitions.severity IS 'Rule severity: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN fraud_rule_definitions.risk_score IS 'Risk score contribution (0.0 to 1.0)';
COMMENT ON COLUMN fraud_rule_definitions.weight IS 'Rule weight in final fraud score calculation';
COMMENT ON COLUMN fraud_rule_definitions.priority IS 'Execution priority (higher = evaluated first)';
COMMENT ON COLUMN fraud_rule_definitions.historical_accuracy IS 'Historical accuracy of the rule (0.0 to 1.0)';

INSERT INTO fraud_rule_definitions (rule_code, rule_name, description, category, severity, risk_score, weight, priority, enabled) VALUES
('HIGH_VALUE_TRANSACTION', 'High Value Transaction', 'Transaction exceeds high value threshold', 'AMOUNT', 'HIGH', 0.80, 2.0, 90, true),
('UNUSUAL_TRANSACTION_TIME', 'Unusual Transaction Time', 'Transaction during unusual hours (2am-6am)', 'BEHAVIORAL', 'MEDIUM', 0.50, 1.0, 60, true),
('RAPID_TRANSACTION_VELOCITY', 'Rapid Transaction Velocity', 'High frequency of transactions in short period', 'VELOCITY', 'HIGH', 0.75, 2.0, 85, true),
('NEW_DEVICE_HIGH_VALUE', 'New Device High Value', 'High value transaction from new device', 'DEVICE', 'HIGH', 0.70, 1.5, 80, true),
('INTERNATIONAL_TRANSACTION', 'International Transaction', 'Cross-border transaction', 'GEOGRAPHIC', 'MEDIUM', 0.45, 1.0, 50, true),
('HIGH_RISK_COUNTRY', 'High Risk Country', 'Transaction involving high-risk jurisdiction', 'GEOGRAPHIC', 'CRITICAL', 0.90, 3.0, 95, true),
('SANCTIONED_COUNTRY', 'Sanctioned Country', 'Transaction involving sanctioned country', 'COMPLIANCE', 'CRITICAL', 1.00, 5.0, 100, true),
('ROUND_AMOUNT_PATTERN', 'Round Amount Pattern', 'Suspicious round number transaction', 'PATTERN', 'MEDIUM', 0.40, 0.8, 40, true),
('DUPLICATE_TRANSACTION', 'Duplicate Transaction', 'Potential duplicate transaction detected', 'PATTERN', 'HIGH', 0.85, 2.5, 88, true),
('ACCOUNT_AGE_HIGH_VALUE', 'New Account High Value', 'High value transaction from new account', 'ACCOUNT', 'HIGH', 0.75, 1.8, 82, true),
('SUSPICIOUS_RECIPIENT', 'Suspicious Recipient', 'Recipient has high fraud risk score', 'RECIPIENT', 'HIGH', 0.80, 2.0, 85, true),
('VELOCITY_SPIKE', 'Transaction Velocity Spike', 'Transaction amount significantly exceeds average', 'VELOCITY', 'HIGH', 0.70, 1.5, 75, true),
('GEOGRAPHIC_IMPOSSIBLE_TRAVEL', 'Impossible Travel', 'Geographically impossible transaction location', 'GEOGRAPHIC', 'CRITICAL', 0.95, 3.5, 98, true),
('UNUSUAL_TRANSACTION_PATTERN', 'Unusual Transaction Pattern', 'Behavioral anomaly detected', 'BEHAVIORAL', 'MEDIUM', 0.60, 1.2, 65, true),
('MULTIPLE_FAILED_ATTEMPTS', 'Multiple Failed Attempts', 'Multiple recent failed transaction attempts', 'BEHAVIORAL', 'HIGH', 0.75, 2.0, 80, true),
('CRYPTO_HIGH_VALUE', 'High Value Crypto Transaction', 'High value cryptocurrency transaction', 'CRYPTO', 'HIGH', 0.70, 1.5, 78, true),
('STRUCTURING_PATTERN', 'Transaction Structuring', 'Potential structuring to avoid reporting thresholds', 'AML', 'CRITICAL', 0.95, 4.0, 99, true),
('RAPID_RECIPIENT_CHANGE', 'Rapid Recipient Change', 'Unusual number of different recipients', 'PATTERN', 'MEDIUM', 0.55, 1.0, 55, true),
('DORMANT_ACCOUNT_ACTIVATION', 'Dormant Account Activation', 'Significant transaction from dormant account', 'ACCOUNT', 'HIGH', 0.80, 2.0, 84, true),
('SUSPICIOUS_DEVICE_CHARACTERISTICS', 'Suspicious Device', 'Device exhibits suspicious characteristics', 'DEVICE', 'MEDIUM', 0.60, 1.2, 60, true);

INSERT INTO fraud_rule_parameters (rule_id, param_key, param_value) VALUES
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'HIGH_VALUE_TRANSACTION'), 'threshold', '10000.00'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'RAPID_TRANSACTION_VELOCITY'), 'max_transactions_per_hour', '10'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'NEW_DEVICE_HIGH_VALUE'), 'amount_threshold', '2000.00'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'ACCOUNT_AGE_HIGH_VALUE'), 'min_account_age_days', '30'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'ACCOUNT_AGE_HIGH_VALUE'), 'high_value_threshold', '5000.00'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'VELOCITY_SPIKE'), 'spike_multiplier', '5.0'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'MULTIPLE_FAILED_ATTEMPTS'), 'max_failed_attempts', '3'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'CRYPTO_HIGH_VALUE'), 'crypto_threshold', '3000.00'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'STRUCTURING_PATTERN'), 'structuring_threshold', '9500.00'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'STRUCTURING_PATTERN'), 'lower_bound', '8000.00'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'RAPID_RECIPIENT_CHANGE'), 'max_unique_recipients_per_day', '5'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'DORMANT_ACCOUNT_ACTIVATION'), 'dormant_period_days', '90'),
((SELECT id FROM fraud_rule_definitions WHERE rule_code = 'DORMANT_ACCOUNT_ACTIVATION'), 'significant_amount', '1000.00');

COMMENT ON TABLE fraud_rule_parameters IS 'Configurable parameters for fraud detection rules';
COMMENT ON TABLE fraud_rule_transaction_types IS 'Transaction types applicable for each rule';