-- =====================================================================
-- Fraud Review System Database Migration
-- Version: 2025.10.16
-- Description: Creates tables for manual fraud review queue system
-- Author: Waqiti Platform Engineering
-- =====================================================================

-- Create fraud_review_cases table
CREATE TABLE IF NOT EXISTS fraud_review_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id VARCHAR(50) NOT NULL UNIQUE,
    payment_id UUID NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    risk_score DOUBLE PRECISION NOT NULL,
    risk_level VARCHAR(20),
    reason TEXT,
    priority INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    device_id VARCHAR(255),
    request_id VARCHAR(100),

    -- Queue management
    queued_at TIMESTAMP NOT NULL,
    sla_deadline TIMESTAMP NOT NULL,

    -- Review process
    assigned_analyst VARCHAR(100),
    reviewer_id VARCHAR(100),
    review_started_at TIMESTAMP,
    review_completed_at TIMESTAMP,
    review_duration_minutes BIGINT,

    -- Decision
    decision VARCHAR(30),
    decision_notes TEXT,
    rejection_reason TEXT,
    sla_violation BOOLEAN DEFAULT FALSE,

    -- Escalation
    escalated BOOLEAN DEFAULT FALSE,
    escalation_reason TEXT,
    escalated_by VARCHAR(100),
    escalated_at TIMESTAMP,

    -- Additional fields
    merchant_id VARCHAR(100),
    merchant_category VARCHAR(100),

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_priority CHECK (priority >= 0 AND priority <= 3),
    CONSTRAINT chk_risk_score CHECK (risk_score >= 0.0 AND risk_score <= 1.0)
);

-- Create fraud_review_triggered_rules table (for triggered rules list)
CREATE TABLE IF NOT EXISTS fraud_review_triggered_rules (
    fraud_review_case_id UUID NOT NULL REFERENCES fraud_review_cases(id) ON DELETE CASCADE,
    rule_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (fraud_review_case_id, rule_name)
);

-- =====================================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================================

-- Primary queue lookup index (most critical for performance)
CREATE INDEX idx_fraud_review_status_priority ON fraud_review_cases(status, priority, queued_at)
    WHERE status IN ('PENDING', 'IN_REVIEW');

-- Payment lookup index
CREATE INDEX idx_fraud_review_payment_id ON fraud_review_cases(payment_id);

-- User lookup index
CREATE INDEX idx_fraud_review_user_id ON fraud_review_cases(user_id);

-- Analyst assignment index
CREATE INDEX idx_fraud_review_assigned_analyst ON fraud_review_cases(assigned_analyst)
    WHERE assigned_analyst IS NOT NULL;

-- SLA monitoring index
CREATE INDEX idx_fraud_review_sla_deadline ON fraud_review_cases(sla_deadline, status)
    WHERE status = 'PENDING';

-- Escalation tracking index
CREATE INDEX idx_fraud_review_escalated ON fraud_review_cases(escalated, escalated_at)
    WHERE escalated = TRUE;

-- Analytics indexes
CREATE INDEX idx_fraud_review_created_at ON fraud_review_cases(created_at DESC);
CREATE INDEX idx_fraud_review_completed_at ON fraud_review_cases(review_completed_at DESC)
    WHERE review_completed_at IS NOT NULL;
CREATE INDEX idx_fraud_review_risk_score ON fraud_review_cases(risk_score DESC);

-- Device analytics index
CREATE INDEX idx_fraud_review_device_id ON fraud_review_cases(device_id)
    WHERE device_id IS NOT NULL;

-- Merchant analytics index
CREATE INDEX idx_fraud_review_merchant_id ON fraud_review_cases(merchant_id)
    WHERE merchant_id IS NOT NULL;

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON TABLE fraud_review_cases IS
'Manual fraud review queue - stores cases flagged for human analyst review';

COMMENT ON COLUMN fraud_review_cases.review_id IS
'Human-readable review ID (e.g., FRQ-ABC12345)';

COMMENT ON COLUMN fraud_review_cases.priority IS
'Review priority: 0=Critical (1hr SLA), 1=High (2hr), 2=Medium (4hr), 3=Low (8hr)';

COMMENT ON COLUMN fraud_review_cases.risk_score IS
'ML fraud risk score from 0.0 (safe) to 1.0 (fraud)';

COMMENT ON COLUMN fraud_review_cases.sla_deadline IS
'Deadline for completing review based on priority';

COMMENT ON COLUMN fraud_review_cases.sla_violation IS
'Whether review was completed past SLA deadline';

COMMENT ON COLUMN fraud_review_cases.escalated IS
'Whether case was escalated to senior analyst';

-- =====================================================================
-- TRIGGERS FOR AUTO-UPDATE
-- =====================================================================

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_fraud_review_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fraud_review_update_timestamp
    BEFORE UPDATE ON fraud_review_cases
    FOR EACH ROW
    EXECUTE FUNCTION update_fraud_review_updated_at();

-- =====================================================================
-- INITIAL DATA / REFERENCE DATA
-- =====================================================================

-- No initial data needed - queue starts empty

-- =====================================================================
-- GRANTS (adjust based on your security model)
-- =====================================================================

-- Grant permissions to payment service user
-- GRANT SELECT, INSERT, UPDATE ON fraud_review_cases TO payment_service_user;
-- GRANT SELECT, INSERT, DELETE ON fraud_review_triggered_rules TO payment_service_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO payment_service_user;

-- =====================================================================
-- MIGRATION VERIFICATION
-- =====================================================================

-- Verify tables were created
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'fraud_review_cases') THEN
        RAISE EXCEPTION 'Migration failed: fraud_review_cases table not created';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'fraud_review_triggered_rules') THEN
        RAISE EXCEPTION 'Migration failed: fraud_review_triggered_rules table not created';
    END IF;

    RAISE NOTICE 'Fraud review tables created successfully';
END $$;
