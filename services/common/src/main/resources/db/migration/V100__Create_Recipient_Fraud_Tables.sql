-- ============================================================================
-- Recipient Fraud Database Schema
-- Version: 1.0.0
-- Purpose: Track recipient fraud patterns and risk scores
-- Author: Waqiti Security Team
-- Date: 2025-10-18
-- ============================================================================

-- Recipient Fraud Scores Table
-- Maintains aggregated fraud metrics for each recipient
CREATE TABLE IF NOT EXISTS recipient_fraud_scores (
    recipient_id VARCHAR(255) PRIMARY KEY,

    -- Chargeback metrics
    total_chargebacks INTEGER DEFAULT 0 NOT NULL,
    chargeback_rate DECIMAL(5,2) DEFAULT 0.00 NOT NULL,
    chargebacks_last_30_days INTEGER DEFAULT 0 NOT NULL,
    chargebacks_last_90_days INTEGER DEFAULT 0 NOT NULL,

    -- Fraud reports
    fraud_reports INTEGER DEFAULT 0 NOT NULL,
    fraud_reports_last_30_days INTEGER DEFAULT 0 NOT NULL,

    -- Transaction metrics
    total_transactions_received INTEGER DEFAULT 0 NOT NULL,
    total_amount_received DECIMAL(19,4) DEFAULT 0.0000 NOT NULL,
    average_transaction_amount DECIMAL(19,4) DEFAULT 0.0000 NOT NULL,

    -- Risk scoring (0-100, higher = more risky)
    risk_score INTEGER DEFAULT 0 NOT NULL CHECK (risk_score >= 0 AND risk_score <= 100),
    risk_level VARCHAR(20) DEFAULT 'LOW' NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    -- Status
    is_blacklisted BOOLEAN DEFAULT FALSE NOT NULL,
    blacklist_reason VARCHAR(500),
    blacklisted_at TIMESTAMP,
    blacklisted_by VARCHAR(255),

    -- Metadata
    first_transaction_at TIMESTAMP,
    last_transaction_at TIMESTAMP,
    last_chargeback_at TIMESTAMP,
    last_fraud_report_at TIMESTAMP,
    last_risk_calculation_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Indexes for performance
    CONSTRAINT chk_chargeback_rate CHECK (chargeback_rate >= 0 AND chargeback_rate <= 100)
);

-- Indexes for efficient querying
CREATE INDEX idx_recipient_risk_score ON recipient_fraud_scores(risk_score DESC);
CREATE INDEX idx_recipient_risk_level ON recipient_fraud_scores(risk_level);
CREATE INDEX idx_recipient_blacklisted ON recipient_fraud_scores(is_blacklisted) WHERE is_blacklisted = TRUE;
CREATE INDEX idx_recipient_chargeback_rate ON recipient_fraud_scores(chargeback_rate DESC);
CREATE INDEX idx_recipient_last_updated ON recipient_fraud_scores(updated_at DESC);

-- ============================================================================

-- Recipient Transaction History Table
-- Detailed transaction history for pattern analysis
CREATE TABLE IF NOT EXISTS recipient_transaction_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Transaction details
    transaction_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,

    -- Amount details
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    -- Transaction status
    status VARCHAR(50) NOT NULL,
    is_chargeback BOOLEAN DEFAULT FALSE NOT NULL,
    chargeback_id VARCHAR(255),
    chargeback_reason VARCHAR(500),
    chargeback_date TIMESTAMP,

    -- Fraud indicators
    is_fraud_reported BOOLEAN DEFAULT FALSE NOT NULL,
    fraud_report_id VARCHAR(255),
    fraud_report_date TIMESTAMP,
    fraud_type VARCHAR(100),

    -- Transaction context
    payment_method VARCHAR(50),
    device_id VARCHAR(255),
    ip_address VARCHAR(45),
    country_code VARCHAR(2),

    -- Timestamps
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Foreign key
    CONSTRAINT fk_recipient_fraud_scores FOREIGN KEY (recipient_id)
        REFERENCES recipient_fraud_scores(recipient_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- Indexes for transaction history queries
CREATE INDEX idx_recipient_tx_recipient ON recipient_transaction_history(recipient_id, transaction_date DESC);
CREATE INDEX idx_recipient_tx_sender ON recipient_transaction_history(sender_id);
CREATE INDEX idx_recipient_tx_status ON recipient_transaction_history(status);
CREATE INDEX idx_recipient_tx_chargeback ON recipient_transaction_history(is_chargeback) WHERE is_chargeback = TRUE;
CREATE INDEX idx_recipient_tx_fraud ON recipient_transaction_history(is_fraud_reported) WHERE is_fraud_reported = TRUE;
CREATE INDEX idx_recipient_tx_date ON recipient_transaction_history(transaction_date DESC);
CREATE INDEX idx_recipient_tx_transaction_id ON recipient_transaction_history(transaction_id);

-- ============================================================================

-- Recipient Fraud Alerts Table
-- Real-time fraud alerts for high-risk recipients
CREATE TABLE IF NOT EXISTS recipient_fraud_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Alert details
    recipient_id VARCHAR(255) NOT NULL,
    alert_type VARCHAR(50) NOT NULL CHECK (alert_type IN ('HIGH_CHARGEBACK_RATE', 'BLACKLISTED', 'RISK_THRESHOLD', 'PATTERN_ANOMALY')),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    -- Alert message
    title VARCHAR(255) NOT NULL,
    description TEXT,
    risk_score INTEGER NOT NULL,

    -- Alert status
    status VARCHAR(20) DEFAULT 'OPEN' NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'FALSE_POSITIVE')),
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMP,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,

    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Foreign key
    CONSTRAINT fk_alert_recipient FOREIGN KEY (recipient_id)
        REFERENCES recipient_fraud_scores(recipient_id)
        ON DELETE CASCADE
);

-- Indexes for fraud alerts
CREATE INDEX idx_fraud_alerts_recipient ON recipient_fraud_alerts(recipient_id);
CREATE INDEX idx_fraud_alerts_status ON recipient_fraud_alerts(status) WHERE status = 'OPEN';
CREATE INDEX idx_fraud_alerts_severity ON recipient_fraud_alerts(severity);
CREATE INDEX idx_fraud_alerts_created ON recipient_fraud_alerts(created_at DESC);

-- ============================================================================

-- Automated Risk Score Update Function
-- Recalculates recipient risk score based on historical data
CREATE OR REPLACE FUNCTION calculate_recipient_risk_score(p_recipient_id VARCHAR(255))
RETURNS INTEGER AS $$
DECLARE
    v_risk_score INTEGER := 0;
    v_total_tx INTEGER;
    v_chargeback_count INTEGER;
    v_chargeback_rate DECIMAL(5,2);
    v_fraud_reports INTEGER;
BEGIN
    -- Get transaction counts
    SELECT
        COUNT(*),
        SUM(CASE WHEN is_chargeback THEN 1 ELSE 0 END),
        SUM(CASE WHEN is_fraud_reported THEN 1 ELSE 0 END)
    INTO
        v_total_tx,
        v_chargeback_count,
        v_fraud_reports
    FROM recipient_transaction_history
    WHERE recipient_id = p_recipient_id;

    -- Calculate chargeback rate
    IF v_total_tx > 0 THEN
        v_chargeback_rate := (v_chargeback_count::DECIMAL / v_total_tx::DECIMAL) * 100;
    ELSE
        v_chargeback_rate := 0;
    END IF;

    -- Calculate risk score (0-100)
    -- Formula: Weighted combination of metrics
    v_risk_score := 0;

    -- Chargeback rate contributes up to 40 points
    v_risk_score := v_risk_score + LEAST((v_chargeback_rate * 4)::INTEGER, 40);

    -- Fraud reports contribute up to 30 points
    v_risk_score := v_risk_score + LEAST(v_fraud_reports * 10, 30);

    -- Recent chargebacks (last 30 days) contribute up to 20 points
    SELECT COUNT(*) INTO v_chargeback_count
    FROM recipient_transaction_history
    WHERE recipient_id = p_recipient_id
      AND is_chargeback = TRUE
      AND chargeback_date >= NOW() - INTERVAL '30 days';

    v_risk_score := v_risk_score + LEAST(v_chargeback_count * 5, 20);

    -- High transaction volume with low history contributes up to 10 points
    IF v_total_tx > 50 AND (
        SELECT created_at FROM recipient_fraud_scores WHERE recipient_id = p_recipient_id
    ) > NOW() - INTERVAL '30 days' THEN
        v_risk_score := v_risk_score + 10;
    END IF;

    RETURN LEAST(v_risk_score, 100);
END;
$$ LANGUAGE plpgsql;

-- ============================================================================

-- Trigger to update recipient_fraud_scores after transaction changes
CREATE OR REPLACE FUNCTION update_recipient_fraud_metrics()
RETURNS TRIGGER AS $$
DECLARE
    v_risk_score INTEGER;
    v_risk_level VARCHAR(20);
    v_chargeback_rate DECIMAL(5,2);
    v_total_tx INTEGER;
    v_chargeback_count INTEGER;
BEGIN
    -- Count transactions
    SELECT COUNT(*) INTO v_total_tx
    FROM recipient_transaction_history
    WHERE recipient_id = NEW.recipient_id;

    -- Count chargebacks
    SELECT COUNT(*) INTO v_chargeback_count
    FROM recipient_transaction_history
    WHERE recipient_id = NEW.recipient_id AND is_chargeback = TRUE;

    -- Calculate chargeback rate
    IF v_total_tx > 0 THEN
        v_chargeback_rate := (v_chargeback_count::DECIMAL / v_total_tx::DECIMAL) * 100;
    ELSE
        v_chargeback_rate := 0;
    END IF;

    -- Calculate risk score
    v_risk_score := calculate_recipient_risk_score(NEW.recipient_id);

    -- Determine risk level
    IF v_risk_score >= 75 THEN
        v_risk_level := 'CRITICAL';
    ELSIF v_risk_score >= 50 THEN
        v_risk_level := 'HIGH';
    ELSIF v_risk_score >= 25 THEN
        v_risk_level := 'MEDIUM';
    ELSE
        v_risk_level := 'LOW';
    END IF;

    -- Update recipient_fraud_scores
    UPDATE recipient_fraud_scores
    SET
        total_transactions_received = v_total_tx,
        total_chargebacks = v_chargeback_count,
        chargeback_rate = v_chargeback_rate,
        risk_score = v_risk_score,
        risk_level = v_risk_level,
        last_transaction_at = NEW.transaction_date,
        last_risk_calculation_at = NOW(),
        updated_at = NOW()
    WHERE recipient_id = NEW.recipient_id;

    -- Auto-blacklist if risk score is critical
    IF v_risk_score >= 90 AND NOT (SELECT is_blacklisted FROM recipient_fraud_scores WHERE recipient_id = NEW.recipient_id) THEN
        UPDATE recipient_fraud_scores
        SET
            is_blacklisted = TRUE,
            blacklist_reason = 'Automatic blacklist: Risk score >= 90',
            blacklisted_at = NOW(),
            blacklisted_by = 'SYSTEM_AUTO'
        WHERE recipient_id = NEW.recipient_id;

        -- Create fraud alert
        INSERT INTO recipient_fraud_alerts (recipient_id, alert_type, severity, title, description, risk_score)
        VALUES (
            NEW.recipient_id,
            'RISK_THRESHOLD',
            'CRITICAL',
            'Recipient automatically blacklisted',
            'Risk score reached 90+. Recipient automatically blacklisted.',
            v_risk_score
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger
DROP TRIGGER IF EXISTS trg_update_recipient_fraud_metrics ON recipient_transaction_history;
CREATE TRIGGER trg_update_recipient_fraud_metrics
    AFTER INSERT OR UPDATE ON recipient_transaction_history
    FOR EACH ROW
    EXECUTE FUNCTION update_recipient_fraud_metrics();

-- ============================================================================

-- Initial data: Create default entry for unknown recipients
INSERT INTO recipient_fraud_scores (recipient_id, risk_score, risk_level)
VALUES ('UNKNOWN', 50, 'MEDIUM')
ON CONFLICT (recipient_id) DO NOTHING;

-- ============================================================================
-- Comments for documentation
-- ============================================================================

COMMENT ON TABLE recipient_fraud_scores IS 'Aggregated fraud metrics and risk scores for payment recipients';
COMMENT ON TABLE recipient_transaction_history IS 'Detailed transaction history for recipient fraud pattern analysis';
COMMENT ON TABLE recipient_fraud_alerts IS 'Real-time fraud alerts for high-risk recipients';

COMMENT ON COLUMN recipient_fraud_scores.risk_score IS 'Risk score 0-100 (higher = more risky)';
COMMENT ON COLUMN recipient_fraud_scores.chargeback_rate IS 'Percentage of transactions resulting in chargebacks';
COMMENT ON COLUMN recipient_fraud_scores.is_blacklisted IS 'Whether recipient is blacklisted from receiving payments';

-- ============================================================================
