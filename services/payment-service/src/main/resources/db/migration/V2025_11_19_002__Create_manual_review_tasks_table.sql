-- ============================================================================
-- PRODUCTION MIGRATION: Manual Review Task Tracking
-- ============================================================================
-- Version: V201
-- Date: November 18, 2025
-- Author: Waqiti Production Team
-- Purpose: Create table for manual review task tracking and workflow management
--
-- COMPLIANCE:
-- - SOX: Audit trail of manual interventions in financial operations
-- - PSD2: Strong Customer Authentication exception tracking
-- - BSA/AML: Suspicious activity review workflow
-- - PCI-DSS: Security incident investigation tracking
--
-- FEATURES:
-- - SLA tracking and breach detection
-- - Task assignment and workload management
-- - Priority-based task queuing
-- - Escalation tracking
-- - Resolution workflow
-- - Comprehensive audit trail
-- ============================================================================

-- Create manual_review_tasks table
CREATE TABLE IF NOT EXISTS payment.manual_review_tasks (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Task Classification
    review_type VARCHAR(50) NOT NULL CHECK (review_type IN (
        'SETTLEMENT_FAILURE', 'PAYMENT_FAILURE', 'FRAUD_ALERT',
        'COMPLIANCE_EXCEPTION', 'HIGH_VALUE_TRANSACTION', 'SUSPICIOUS_ACTIVITY',
        'KYC_VERIFICATION', 'CHARGEBACK', 'DISPUTE', 'REFUND_REQUEST',
        'ACCOUNT_ANOMALY', 'TRANSACTION_ANOMALY', 'OTHER'
    )),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED', 'CANCELLED', 'ON_HOLD'
    )),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN (
        'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'
    )),

    -- Entity Information
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    payment_id VARCHAR(50),
    settlement_id VARCHAR(50),
    batch_id VARCHAR(50),
    user_id VARCHAR(50),

    -- Financial Information
    amount DECIMAL(19, 4),
    currency VARCHAR(3),
    bank_code VARCHAR(50),

    -- Task Details
    title VARCHAR(200) NOT NULL,
    description TEXT,
    reason TEXT,

    -- Assignment and Workflow
    assigned_to VARCHAR(100),
    assigned_at TIMESTAMP,
    due_date TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- Resolution
    resolution_notes TEXT,
    resolution_action VARCHAR(100),
    resolved_by VARCHAR(50),

    -- Escalation Tracking
    escalation_count INTEGER DEFAULT 0,
    last_escalation_at TIMESTAMP,

    -- SLA Management
    sla_breached BOOLEAN DEFAULT false,
    sla_breached_at TIMESTAMP,

    -- Metadata
    tags TEXT,

    -- Audit Fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),

    -- Constraints
    CONSTRAINT check_due_after_creation CHECK (due_date IS NULL OR due_date > created_at),
    CONSTRAINT check_completed_after_started CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at),
    CONSTRAINT check_assigned_before_started CHECK (started_at IS NULL OR assigned_at IS NULL OR started_at >= assigned_at)
);

-- ============================================================================
-- INDEXES FOR OPTIMAL QUERY PERFORMANCE
-- ============================================================================

-- Primary workflow indexes
CREATE INDEX IF NOT EXISTS idx_review_status ON payment.manual_review_tasks(status) WHERE status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS');
CREATE INDEX IF NOT EXISTS idx_review_priority ON payment.manual_review_tasks(priority, created_at) WHERE status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS');
CREATE INDEX IF NOT EXISTS idx_review_type ON payment.manual_review_tasks(review_type, status);

-- Assignment and workload indexes
CREATE INDEX IF NOT EXISTS idx_review_assigned_to ON payment.manual_review_tasks(assigned_to, status) WHERE status IN ('ASSIGNED', 'IN_PROGRESS');
CREATE INDEX IF NOT EXISTS idx_review_unassigned ON payment.manual_review_tasks(status) WHERE assigned_to IS NULL AND status = 'PENDING';

-- Time-based indexes
CREATE INDEX IF NOT EXISTS idx_review_created_at ON payment.manual_review_tasks(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_review_due_date ON payment.manual_review_tasks(due_date) WHERE status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS');
CREATE INDEX IF NOT EXISTS idx_review_completed_at ON payment.manual_review_tasks(completed_at DESC) WHERE completed_at IS NOT NULL;

-- Entity lookup indexes
CREATE INDEX IF NOT EXISTS idx_review_entity_id ON payment.manual_review_tasks(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_review_payment_id ON payment.manual_review_tasks(payment_id) WHERE payment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_review_settlement_id ON payment.manual_review_tasks(settlement_id) WHERE settlement_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_review_user_id ON payment.manual_review_tasks(user_id) WHERE user_id IS NOT NULL;

-- SLA monitoring indexes
CREATE INDEX IF NOT EXISTS idx_review_sla_breached ON payment.manual_review_tasks(sla_breached, status) WHERE sla_breached = true;
CREATE INDEX IF NOT EXISTS idx_review_approaching_sla ON payment.manual_review_tasks(due_date) WHERE due_date IS NOT NULL AND status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS');

-- Escalation indexes
CREATE INDEX IF NOT EXISTS idx_review_escalated ON payment.manual_review_tasks(status, last_escalation_at DESC) WHERE status = 'ESCALATED';
CREATE INDEX IF NOT EXISTS idx_review_escalation_count ON payment.manual_review_tasks(escalation_count DESC) WHERE escalation_count > 0;

-- Critical tasks index (compound for dashboard queries)
CREATE INDEX IF NOT EXISTS idx_review_critical_pending ON payment.manual_review_tasks(priority, status, created_at) WHERE priority = 'CRITICAL' AND status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS');

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE payment.manual_review_tasks IS 'Tracks manual review tasks for settlement failures, fraud alerts, compliance exceptions, and other issues requiring human intervention';

COMMENT ON COLUMN payment.manual_review_tasks.id IS 'Primary key - auto-generated';
COMMENT ON COLUMN payment.manual_review_tasks.review_type IS 'Type of review: SETTLEMENT_FAILURE, FRAUD_ALERT, etc.';
COMMENT ON COLUMN payment.manual_review_tasks.status IS 'Current status: PENDING, ASSIGNED, IN_PROGRESS, RESOLVED, ESCALATED, CANCELLED, ON_HOLD';
COMMENT ON COLUMN payment.manual_review_tasks.priority IS 'Priority level: CRITICAL (<2h), HIGH (<4h), MEDIUM (<24h), LOW (<72h)';
COMMENT ON COLUMN payment.manual_review_tasks.entity_type IS 'Type of entity being reviewed (PAYMENT, SETTLEMENT, TRANSACTION, USER)';
COMMENT ON COLUMN payment.manual_review_tasks.entity_id IS 'ID of entity being reviewed';
COMMENT ON COLUMN payment.manual_review_tasks.payment_id IS 'Related payment ID (if applicable)';
COMMENT ON COLUMN payment.manual_review_tasks.settlement_id IS 'Related settlement ID (if applicable)';
COMMENT ON COLUMN payment.manual_review_tasks.batch_id IS 'Related batch ID (if applicable)';
COMMENT ON COLUMN payment.manual_review_tasks.user_id IS 'User ID being reviewed (if applicable)';
COMMENT ON COLUMN payment.manual_review_tasks.amount IS 'Amount involved in review (for financial reviews)';
COMMENT ON COLUMN payment.manual_review_tasks.currency IS 'Currency code (USD, EUR, etc.)';
COMMENT ON COLUMN payment.manual_review_tasks.bank_code IS 'Bank or institution code (if applicable)';
COMMENT ON COLUMN payment.manual_review_tasks.title IS 'Task title/summary (max 200 chars)';
COMMENT ON COLUMN payment.manual_review_tasks.description IS 'Detailed description of issue requiring review';
COMMENT ON COLUMN payment.manual_review_tasks.reason IS 'Reason for review (failure reason, alert trigger, etc.)';
COMMENT ON COLUMN payment.manual_review_tasks.assigned_to IS 'User ID or team name assigned to review';
COMMENT ON COLUMN payment.manual_review_tasks.assigned_at IS 'When task was assigned';
COMMENT ON COLUMN payment.manual_review_tasks.due_date IS 'SLA deadline for completion';
COMMENT ON COLUMN payment.manual_review_tasks.started_at IS 'When reviewer started working on task';
COMMENT ON COLUMN payment.manual_review_tasks.completed_at IS 'When task was completed';
COMMENT ON COLUMN payment.manual_review_tasks.resolution_notes IS 'Notes from reviewer about resolution';
COMMENT ON COLUMN payment.manual_review_tasks.resolution_action IS 'Action taken to resolve (e.g., APPROVED, REJECTED, ESCALATED)';
COMMENT ON COLUMN payment.manual_review_tasks.resolved_by IS 'User ID who resolved the task';
COMMENT ON COLUMN payment.manual_review_tasks.escalation_count IS 'Number of times task has been escalated';
COMMENT ON COLUMN payment.manual_review_tasks.last_escalation_at IS 'Timestamp of most recent escalation';
COMMENT ON COLUMN payment.manual_review_tasks.sla_breached IS 'Whether SLA deadline was missed';
COMMENT ON COLUMN payment.manual_review_tasks.sla_breached_at IS 'When SLA was first breached';
COMMENT ON COLUMN payment.manual_review_tasks.tags IS 'Additional tags/metadata (free-form text)';
COMMENT ON COLUMN payment.manual_review_tasks.created_at IS 'Audit field - task creation timestamp';
COMMENT ON COLUMN payment.manual_review_tasks.updated_at IS 'Audit field - last update timestamp';
COMMENT ON COLUMN payment.manual_review_tasks.created_by IS 'Audit field - user/system who created task';
COMMENT ON COLUMN payment.manual_review_tasks.updated_by IS 'Audit field - user who last updated task';

-- ============================================================================
-- STATISTICS FOR QUERY OPTIMIZER
-- ============================================================================

ANALYZE payment.manual_review_tasks;

-- ============================================================================
-- GRANTS (Adjust based on your security model)
-- ============================================================================

-- Grant appropriate permissions to payment service role
-- GRANT SELECT, INSERT, UPDATE, DELETE ON payment.manual_review_tasks TO payment_service_role;
-- GRANT USAGE, SELECT ON SEQUENCE payment.manual_review_tasks_id_seq TO payment_service_role;

-- ============================================================================
-- VERIFICATION QUERIES (For testing migration)
-- ============================================================================

-- Verify table exists
-- SELECT table_name, table_type
-- FROM information_schema.tables
-- WHERE table_schema = 'payment'
--   AND table_name = 'manual_review_tasks';

-- Verify indexes
-- SELECT indexname, indexdef
-- FROM pg_indexes
-- WHERE schemaname = 'payment'
--   AND tablename = 'manual_review_tasks'
-- ORDER BY indexname;

-- Verify constraints
-- SELECT conname, contype, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid = 'payment.manual_review_tasks'::regclass
-- ORDER BY contype, conname;

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
-- This migration creates the manual_review_tasks table with:
-- ✅ Workflow management (PENDING → ASSIGNED → IN_PROGRESS → RESOLVED)
-- ✅ SLA tracking and breach detection
-- ✅ Priority-based task queuing
-- ✅ Assignment and workload management
-- ✅ Escalation tracking
-- ✅ Comprehensive audit trail (SOX compliance)
-- ✅ 15 optimized indexes for performance
-- ============================================================================
