-- V2__Create_Billing_Disputes_Table.sql
-- Billing Dispute Management Schema
-- Author: Waqiti Billing Team
-- Date: 2025-10-18
--
-- PURPOSE: Complete billing dispute lifecycle management with SLA tracking,
-- workflow automation, and compliance requirements.
--
-- BUSINESS IMPACT:
-- - Average dispute: $50-$500
-- - Monthly volume: 100-500 cases
-- - Resolution time: 5-30 days
-- - Win rate: 70% for merchant
--
-- SLA TARGETS:
-- - First response: 24 hours
-- - Simple cases: 5 business days
-- - Complex cases: 15 business days
-- - Escalated: 30 days max

-- ==================== CREATE TABLES ====================

CREATE TABLE billing_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    billing_cycle_id UUID NOT NULL,
    invoice_id UUID,
    invoice_number VARCHAR(50),

    -- Status and workflow
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'SUBMITTED',
        'UNDER_REVIEW',
        'PENDING_MERCHANT_RESPONSE',
        'MERCHANT_RESPONDED',
        'ESCALATED',
        'PENDING_REFUND',
        'RESOLVED',
        'CLOSED'
    )),

    -- Reason and resolution
    reason VARCHAR(100) NOT NULL CHECK (reason IN (
        'INCORRECT_AMOUNT',
        'SERVICE_NOT_RECEIVED',
        'POOR_SERVICE_QUALITY',
        'DUPLICATE_CHARGE',
        'CANCELLED_SUBSCRIPTION',
        'BILLING_ERROR',
        'UNAUTHORIZED_CHARGE',
        'PROMOTIONAL_DISCOUNT_NOT_APPLIED',
        'REFUND_NOT_RECEIVED',
        'OTHER'
    )),

    resolution_type VARCHAR(100) CHECK (resolution_type IN (
        'ACCEPTED_FULL_REFUND',
        'ACCEPTED_PARTIAL_REFUND',
        'ACCEPTED_CREDIT_NOTE',
        'REJECTED_NO_MERIT',
        'REJECTED_INSUFFICIENT_EVIDENCE',
        'REJECTED_OUTSIDE_POLICY',
        'WITHDRAWN_BY_CUSTOMER',
        'RESOLVED_GOODWILL_REFUND'
    )),

    -- Financial details
    disputed_amount DECIMAL(19, 4) NOT NULL,
    approved_refund_amount DECIMAL(19, 4),
    currency CHAR(3) NOT NULL,

    -- Dispute content
    customer_description TEXT NOT NULL,
    merchant_response TEXT,
    internal_notes TEXT,
    resolution_notes TEXT,

    -- Evidence tracking
    customer_evidence_url VARCHAR(500),
    merchant_evidence_url VARCHAR(500),

    -- Workflow tracking
    assigned_to UUID,
    priority VARCHAR(20) CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    escalated BOOLEAN DEFAULT FALSE,
    escalation_reason VARCHAR(500),

    -- Timeline tracking
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    merchant_response_deadline TIMESTAMP,
    merchant_responded_at TIMESTAMP,
    resolution_date TIMESTAMP,
    resolved_by UUID,

    -- SLA tracking
    sla_breach BOOLEAN DEFAULT FALSE,
    sla_deadline TIMESTAMP NOT NULL,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,

    -- Foreign key constraints
    CONSTRAINT fk_billing_disputes_billing_cycle
        FOREIGN KEY (billing_cycle_id)
        REFERENCES billing_cycles(id)
        ON DELETE RESTRICT
);

-- ==================== CREATE INDEXES ====================

-- Core query indexes
CREATE INDEX idx_billing_disputes_customer
    ON billing_disputes(customer_id);

CREATE INDEX idx_billing_disputes_billing_cycle
    ON billing_disputes(billing_cycle_id);

CREATE INDEX idx_billing_disputes_invoice
    ON billing_disputes(invoice_id)
    WHERE invoice_id IS NOT NULL;

-- Status and workflow indexes
CREATE INDEX idx_billing_disputes_status
    ON billing_disputes(status);

CREATE INDEX idx_billing_disputes_status_submitted
    ON billing_disputes(status, submitted_at DESC)
    WHERE status IN ('SUBMITTED', 'UNDER_REVIEW', 'PENDING_MERCHANT_RESPONSE', 'ESCALATED');

CREATE INDEX idx_billing_disputes_assigned_to
    ON billing_disputes(assigned_to)
    WHERE assigned_to IS NOT NULL;

-- Escalation indexes
CREATE INDEX idx_billing_disputes_escalated
    ON billing_disputes(escalated, status)
    WHERE escalated = TRUE;

-- SLA monitoring indexes (CRITICAL FOR PERFORMANCE)
CREATE INDEX idx_billing_disputes_sla_breach
    ON billing_disputes(sla_deadline, status)
    WHERE sla_breach = FALSE
      AND status IN ('SUBMITTED', 'UNDER_REVIEW', 'PENDING_MERCHANT_RESPONSE', 'ESCALATED');

CREATE INDEX idx_billing_disputes_sla_approaching
    ON billing_disputes(sla_deadline, status)
    WHERE sla_breach = FALSE
      AND status IN ('SUBMITTED', 'UNDER_REVIEW', 'PENDING_MERCHANT_RESPONSE', 'ESCALATED');

-- Priority indexes
CREATE INDEX idx_billing_disputes_priority
    ON billing_disputes(priority, status);

-- Timeline indexes
CREATE INDEX idx_billing_disputes_submitted_date
    ON billing_disputes(submitted_at DESC);

CREATE INDEX idx_billing_disputes_resolution_date
    ON billing_disputes(resolution_date DESC)
    WHERE resolution_date IS NOT NULL;

-- Analytics indexes
CREATE INDEX idx_billing_disputes_customer_active
    ON billing_disputes(customer_id, status)
    WHERE status IN ('SUBMITTED', 'UNDER_REVIEW', 'PENDING_MERCHANT_RESPONSE', 'ESCALATED');

-- ==================== CREATE TRIGGERS ====================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_billing_disputes_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_billing_disputes_timestamp
    BEFORE UPDATE ON billing_disputes
    FOR EACH ROW
    EXECUTE FUNCTION update_billing_disputes_updated_at();

-- Auto-detect SLA breach on status change
CREATE OR REPLACE FUNCTION check_billing_dispute_sla_breach()
RETURNS TRIGGER AS $$
BEGIN
    -- Only check SLA on status transitions
    IF NEW.status != OLD.status AND NEW.resolution_date IS NULL THEN
        IF NEW.sla_deadline < NOW() THEN
            NEW.sla_breach = TRUE;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_check_sla_breach
    BEFORE UPDATE ON billing_disputes
    FOR EACH ROW
    EXECUTE FUNCTION check_billing_dispute_sla_breach();

-- ==================== ANALYTICS VIEWS ====================

-- Dispute summary by status
CREATE OR REPLACE VIEW v_billing_dispute_summary AS
SELECT
    status,
    COUNT(*) AS dispute_count,
    SUM(disputed_amount) AS total_disputed_amount,
    AVG(disputed_amount) AS avg_disputed_amount,
    COUNT(CASE WHEN sla_breach THEN 1 END) AS sla_breaches,
    COUNT(CASE WHEN escalated THEN 1 END) AS escalated_count
FROM billing_disputes
GROUP BY status;

-- SLA performance metrics
CREATE OR REPLACE VIEW v_billing_dispute_sla_metrics AS
SELECT
    DATE_TRUNC('day', submitted_at) AS dispute_date,
    COUNT(*) AS total_disputes,
    COUNT(CASE WHEN sla_breach THEN 1 END) AS sla_breached,
    ROUND(
        (COUNT(CASE WHEN sla_breach THEN 1 END)::DECIMAL / NULLIF(COUNT(*), 0)) * 100,
        2
    ) AS sla_breach_percentage,
    AVG(
        CASE WHEN resolution_date IS NOT NULL
        THEN EXTRACT(EPOCH FROM (resolution_date - submitted_at)) / 3600
        END
    ) AS avg_resolution_time_hours
FROM billing_disputes
WHERE submitted_at >= NOW() - INTERVAL '90 days'
GROUP BY DATE_TRUNC('day', submitted_at)
ORDER BY dispute_date DESC;

-- Active disputes by priority
CREATE OR REPLACE VIEW v_active_disputes_by_priority AS
SELECT
    priority,
    status,
    COUNT(*) AS dispute_count,
    SUM(disputed_amount) AS total_amount,
    COUNT(CASE WHEN sla_deadline < NOW() THEN 1 END) AS overdue_count
FROM billing_disputes
WHERE status IN ('SUBMITTED', 'UNDER_REVIEW', 'PENDING_MERCHANT_RESPONSE', 'ESCALATED')
GROUP BY priority, status
ORDER BY
    CASE priority
        WHEN 'URGENT' THEN 1
        WHEN 'HIGH' THEN 2
        WHEN 'MEDIUM' THEN 3
        WHEN 'LOW' THEN 4
    END,
    dispute_count DESC;

-- Dispute resolution statistics
CREATE OR REPLACE VIEW v_dispute_resolution_stats AS
SELECT
    resolution_type,
    COUNT(*) AS resolution_count,
    SUM(approved_refund_amount) AS total_refunded,
    AVG(approved_refund_amount) AS avg_refund_amount,
    ROUND(
        AVG(EXTRACT(EPOCH FROM (resolution_date - submitted_at)) / 86400),
        2
    ) AS avg_resolution_days
FROM billing_disputes
WHERE resolution_date IS NOT NULL
GROUP BY resolution_type
ORDER BY resolution_count DESC;

-- ==================== COMMENTS ====================

COMMENT ON TABLE billing_disputes IS 'Customer billing disputes with SLA tracking and workflow management';
COMMENT ON COLUMN billing_disputes.sla_deadline IS 'SLA deadline calculated based on priority: LOW=30d, MEDIUM=15d, HIGH=10d, URGENT=5d';
COMMENT ON COLUMN billing_disputes.priority IS 'Auto-calculated: URGENT>$1000, HIGH>$500, MEDIUM>$50, LOW<$50';
COMMENT ON COLUMN billing_disputes.escalated IS 'TRUE when dispute requires senior billing team review';
COMMENT ON COLUMN billing_disputes.sla_breach IS 'TRUE when dispute resolution exceeds SLA deadline';
COMMENT ON VIEW v_billing_dispute_sla_metrics IS 'SLA performance tracking for last 90 days';
COMMENT ON VIEW v_active_disputes_by_priority IS 'Real-time active disputes grouped by priority';

-- ==================== SAMPLE DATA (OPTIONAL - FOR TESTING) ====================

-- Uncomment below to insert sample disputes for testing
/*
INSERT INTO billing_disputes (
    customer_id,
    billing_cycle_id,
    status,
    reason,
    disputed_amount,
    currency,
    customer_description,
    priority,
    sla_deadline
) VALUES
(
    gen_random_uuid(),
    (SELECT id FROM billing_cycles LIMIT 1),
    'SUBMITTED',
    'INCORRECT_AMOUNT',
    250.00,
    'USD',
    'I was charged $250 but the agreed amount was $200. Please review my subscription plan.',
    'MEDIUM',
    NOW() + INTERVAL '15 days'
);
*/
