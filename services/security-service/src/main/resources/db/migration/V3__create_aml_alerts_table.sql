-- Create AML Alerts table for suspicious activity monitoring
CREATE TABLE aml_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_id UUID,
    alert_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('OPEN', 'UNDER_INVESTIGATION', 'RESOLVED', 'ESCALATED', 'CLOSED', 'REGULATORY_REPORTED')),
    requires_investigation BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    resolution TEXT,
    suspicious_activity BOOLEAN,
    escalated_at TIMESTAMP WITH TIME ZONE,
    escalated_to UUID,
    risk_score INTEGER CHECK (risk_score BETWEEN 1 AND 100),
    automated_action VARCHAR(255),
    investigation_notes TEXT,
    external_reference VARCHAR(255),
    jurisdiction_code CHAR(2),
    regulatory_reported BOOLEAN,
    reported_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for efficient querying
CREATE INDEX idx_aml_alerts_user_id ON aml_alerts(user_id);
CREATE INDEX idx_aml_alerts_transaction_id ON aml_alerts(transaction_id);
CREATE INDEX idx_aml_alerts_status ON aml_alerts(status);
CREATE INDEX idx_aml_alerts_severity ON aml_alerts(severity);
CREATE INDEX idx_aml_alerts_created_at ON aml_alerts(created_at);
CREATE INDEX idx_aml_alerts_alert_type ON aml_alerts(alert_type);
CREATE INDEX idx_aml_alerts_requires_investigation ON aml_alerts(requires_investigation) WHERE requires_investigation = true;
CREATE INDEX idx_aml_alerts_escalated_to ON aml_alerts(escalated_to) WHERE escalated_to IS NOT NULL;
CREATE INDEX idx_aml_alerts_regulatory_reported ON aml_alerts(regulatory_reported) WHERE regulatory_reported IS NOT NULL;

-- Create composite indexes for common query patterns
CREATE INDEX idx_aml_alerts_status_severity ON aml_alerts(status, severity);
CREATE INDEX idx_aml_alerts_user_created ON aml_alerts(user_id, created_at);
CREATE INDEX idx_aml_alerts_type_severity ON aml_alerts(alert_type, severity);

-- Add comments for documentation
COMMENT ON TABLE aml_alerts IS 'AML alerts for tracking suspicious activities and compliance monitoring';
COMMENT ON COLUMN aml_alerts.user_id IS 'User ID associated with the alert';
COMMENT ON COLUMN aml_alerts.transaction_id IS 'Transaction ID that triggered the alert (if applicable)';
COMMENT ON COLUMN aml_alerts.alert_type IS 'Type of AML alert (e.g., LARGE_CASH_TRANSACTION, STRUCTURING)';
COMMENT ON COLUMN aml_alerts.severity IS 'Alert severity level';
COMMENT ON COLUMN aml_alerts.risk_score IS 'Risk score from 1-100 based on pattern analysis';
COMMENT ON COLUMN aml_alerts.regulatory_reported IS 'Whether this alert has been reported to regulatory authorities';