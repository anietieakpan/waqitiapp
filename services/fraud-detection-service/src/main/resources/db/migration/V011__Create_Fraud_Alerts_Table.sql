-- Create Fraud Alerts Table
-- Stores real-time fraud alerts for manual review and investigation

CREATE TABLE IF NOT EXISTS fraud_alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_id VARCHAR(50) NOT NULL UNIQUE,
    transaction_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    title VARCHAR(500) NOT NULL,
    description TEXT,
    risk_score DECIMAL(5, 2) NOT NULL,
    ml_score DECIMAL(5, 2),
    rule_score DECIMAL(5, 2),
    triggered_rules_count INTEGER,
    triggered_rules TEXT,
    is_blocked BOOLEAN NOT NULL DEFAULT false,
    requires_manual_review BOOLEAN NOT NULL DEFAULT false,
    confirmed_fraud BOOLEAN,
    notes TEXT,
    resolution TEXT,
    acknowledged_by VARCHAR(100),
    acknowledged_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_alert_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT check_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'UNDER_INVESTIGATION', 'RESOLVED', 'FALSE_POSITIVE')),
    CONSTRAINT check_alert_risk_score CHECK (risk_score >= 0 AND risk_score <= 1)
);

CREATE INDEX idx_fraud_alert_id ON fraud_alerts(alert_id);
CREATE INDEX idx_fraud_alert_transaction_id ON fraud_alerts(transaction_id);
CREATE INDEX idx_fraud_alert_user_id ON fraud_alerts(user_id);
CREATE INDEX idx_fraud_alert_severity ON fraud_alerts(severity);
CREATE INDEX idx_fraud_alert_status ON fraud_alerts(status);
CREATE INDEX idx_fraud_alert_created_at ON fraud_alerts(created_at);
CREATE INDEX idx_fraud_alert_severity_status ON fraud_alerts(severity, status);
CREATE INDEX idx_fraud_alert_user_created ON fraud_alerts(user_id, created_at);

CREATE TABLE IF NOT EXISTS fraud_alert_metadata (
    alert_id BIGINT NOT NULL,
    meta_key VARCHAR(100) NOT NULL,
    meta_value TEXT,
    
    CONSTRAINT fk_alert_metadata FOREIGN KEY (alert_id) REFERENCES fraud_alerts(id) ON DELETE CASCADE,
    PRIMARY KEY (alert_id, meta_key)
);

COMMENT ON TABLE fraud_alerts IS 'Real-time fraud alerts for high-risk transactions requiring manual review';
COMMENT ON COLUMN fraud_alerts.alert_id IS 'Unique alert identifier (e.g., FRAUD-A1B2C3D4)';
COMMENT ON COLUMN fraud_alerts.severity IS 'Alert severity: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN fraud_alerts.status IS 'Alert status: OPEN, ACKNOWLEDGED, UNDER_INVESTIGATION, RESOLVED, FALSE_POSITIVE';
COMMENT ON COLUMN fraud_alerts.risk_score IS 'Overall fraud risk score (0.0 to 1.0)';
COMMENT ON COLUMN fraud_alerts.is_blocked IS 'Whether transaction was automatically blocked';
COMMENT ON COLUMN fraud_alerts.confirmed_fraud IS 'Whether fraud was confirmed after investigation';
COMMENT ON COLUMN fraud_alerts.triggered_rules IS 'Comma-separated list of triggered fraud rule codes';

CREATE OR REPLACE FUNCTION update_fraud_alert_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_fraud_alert_updated_at
    BEFORE UPDATE ON fraud_alerts
    FOR EACH ROW
    EXECUTE FUNCTION update_fraud_alert_timestamp();

COMMENT ON TABLE fraud_alert_metadata IS 'Additional metadata for fraud alerts';