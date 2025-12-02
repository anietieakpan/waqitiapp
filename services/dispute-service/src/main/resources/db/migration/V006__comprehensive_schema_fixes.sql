-- ============================================================================
-- Migration V006: Comprehensive Schema Fixes for Production Readiness
-- ============================================================================
-- Purpose: Address all schema mismatches identified in forensic audit
-- - Rename table: dispute → disputes
-- - Add 15+ missing columns from entity
-- - Fix amount precision: DECIMAL(15,2) → DECIMAL(19,4)
-- - Add version column for optimistic locking
-- - Create resolution_templates table
-- - Add comprehensive indexes
-- ============================================================================
-- Author: Waqiti Development Team
-- Date: 2025-11-22
-- JIRA: DISPUTE-001
-- Risk Level: HIGH (requires downtime)
-- Rollback: See V006__comprehensive_schema_fixes_rollback.sql
-- ============================================================================

-- ============================================================================
-- STEP 1: Rename main table (dispute → disputes)
-- ============================================================================

ALTER TABLE dispute RENAME TO disputes;

COMMENT ON TABLE disputes IS 'Main disputes table - stores all customer transaction disputes';

-- ============================================================================
-- STEP 2: Add missing columns
-- ============================================================================

-- Funds locking columns (Regulation E compliance)
ALTER TABLE disputes ADD COLUMN funds_locked BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE disputes ADD COLUMN funds_locked_at TIMESTAMP;
ALTER TABLE disputes ADD COLUMN funds_released_at TIMESTAMP;

COMMENT ON COLUMN disputes.funds_locked IS 'Whether transaction funds are locked pending investigation';
COMMENT ON COLUMN disputes.funds_locked_at IS 'Timestamp when funds were locked';
COMMENT ON COLUMN disputes.funds_released_at IS 'Timestamp when funds were released';

-- Auto-resolution ML columns
ALTER TABLE disputes ADD COLUMN auto_resolution_eligible BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE disputes ADD COLUMN auto_resolution_attempted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE disputes ADD COLUMN auto_resolution_score DOUBLE PRECISION;

COMMENT ON COLUMN disputes.auto_resolution_eligible IS 'Whether dispute qualifies for ML auto-resolution';
COMMENT ON COLUMN disputes.auto_resolution_attempted IS 'Whether auto-resolution was attempted';
COMMENT ON COLUMN disputes.auto_resolution_score IS 'ML confidence score for auto-resolution (0.0-1.0)';

-- Communication tracking columns
ALTER TABLE disputes ADD COLUMN customer_contacted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE disputes ADD COLUMN merchant_contacted BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE disputes ADD COLUMN last_communication_at TIMESTAMP;

COMMENT ON COLUMN disputes.customer_contacted IS 'Whether customer has been contacted about dispute';
COMMENT ON COLUMN disputes.merchant_contacted IS 'Whether merchant has been contacted';
COMMENT ON COLUMN disputes.last_communication_at IS 'Timestamp of most recent communication';

-- Audit trail columns
ALTER TABLE disputes ADD COLUMN created_by VARCHAR(255);
ALTER TABLE disputes ADD COLUMN resolved_by VARCHAR(255);

COMMENT ON COLUMN disputes.created_by IS 'User ID who created the dispute (system or customer)';
COMMENT ON COLUMN disputes.resolved_by IS 'User ID who resolved the dispute (agent or system)';

-- Optimistic locking column (CRITICAL for concurrency control)
ALTER TABLE disputes ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

COMMENT ON COLUMN disputes.version IS 'Optimistic locking version - incremented on each update';

-- Chargeback integration columns
ALTER TABLE disputes ADD COLUMN chargeback_id VARCHAR(255);
ALTER TABLE disputes ADD COLUMN chargeback_initiated_at TIMESTAMP;
ALTER TABLE disputes ADD COLUMN chargeback_status VARCHAR(50);

COMMENT ON COLUMN disputes.chargeback_id IS 'Provider chargeback case ID (Stripe, etc.)';
COMMENT ON COLUMN disputes.chargeback_initiated_at IS 'When chargeback was initiated with provider';
COMMENT ON COLUMN disputes.chargeback_status IS 'Status of chargeback: PENDING, WON_BY_MERCHANT, WON_BY_CUSTOMER, WITHDRAWN';

-- ============================================================================
-- STEP 3: Fix amount precision (CRITICAL for financial accuracy)
-- ============================================================================

-- Change from DECIMAL(15,2) to DECIMAL(19,4) to prevent precision loss
ALTER TABLE disputes ALTER COLUMN dispute_amount TYPE DECIMAL(19,4);

COMMENT ON COLUMN disputes.dispute_amount IS 'Disputed amount with 4 decimal precision for multi-currency support';

-- ============================================================================
-- STEP 4: Create resolution_templates table
-- ============================================================================

CREATE TABLE resolution_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    resolution_type VARCHAR(50) NOT NULL,
    refund_percentage INTEGER,
    requires_evidence BOOLEAN DEFAULT FALSE NOT NULL,
    auto_approve_threshold DECIMAL(19,4),
    priority INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE resolution_templates IS 'Pre-defined resolution templates for common dispute types';
COMMENT ON COLUMN resolution_templates.category IS 'Dispute category this template applies to';
COMMENT ON COLUMN resolution_templates.title IS 'Template title shown to agents';
COMMENT ON COLUMN resolution_templates.resolution_type IS 'Type: FULL_REFUND, PARTIAL_REFUND, MERCHANT_CREDIT, REJECT';
COMMENT ON COLUMN resolution_templates.refund_percentage IS 'Default refund percentage (0-100)';
COMMENT ON COLUMN resolution_templates.requires_evidence IS 'Whether evidence is required to use this template';
COMMENT ON COLUMN resolution_templates.auto_approve_threshold IS 'Max amount for auto-approval without manager review';
COMMENT ON COLUMN resolution_templates.priority IS 'Display priority (higher = shown first)';

-- ============================================================================
-- STEP 5: Create indexes for performance
-- ============================================================================

-- Index on funds_locked for fund lock queries
CREATE INDEX idx_disputes_funds_locked ON disputes(funds_locked) WHERE funds_locked = TRUE;

-- Index on auto_resolution_eligible for ML processing
CREATE INDEX idx_disputes_auto_resolution_eligible ON disputes(auto_resolution_eligible) WHERE auto_resolution_eligible = TRUE;

-- Index on chargeback_id for chargeback lookups
CREATE INDEX idx_disputes_chargeback_id ON disputes(chargeback_id) WHERE chargeback_id IS NOT NULL;

-- Composite index for communication tracking
CREATE INDEX idx_disputes_communication ON disputes(customer_contacted, merchant_contacted, last_communication_at);

-- Index on version for optimistic locking queries
CREATE INDEX idx_disputes_version ON disputes(version);

-- Index on created_by for audit queries
CREATE INDEX idx_disputes_created_by ON disputes(created_by);

-- Index on resolved_by for audit queries
CREATE INDEX idx_disputes_resolved_by ON disputes(resolved_by);

-- Index on resolution_templates category
CREATE INDEX idx_resolution_templates_category ON resolution_templates(category);

-- Composite index on resolution_templates for queries
CREATE INDEX idx_resolution_templates_lookup ON resolution_templates(category, priority DESC);

-- ============================================================================
-- STEP 6: Insert default resolution templates
-- ============================================================================

INSERT INTO resolution_templates (category, title, description, resolution_type, refund_percentage, requires_evidence, auto_approve_threshold, priority) VALUES
    ('UNAUTHORIZED_CHARGE', 'Full Refund - Unauthorized Transaction', 'Customer did not authorize this transaction', 'FULL_REFUND', 100, FALSE, 500.00, 100),
    ('FRAUD', 'Full Refund - Fraudulent Charge', 'Transaction identified as fraudulent', 'FULL_REFUND', 100, TRUE, 1000.00, 95),
    ('PRODUCT_NOT_RECEIVED', 'Full Refund - Non-Delivery', 'Product/service not received by customer', 'FULL_REFUND', 100, TRUE, 300.00, 90),
    ('PRODUCT_DEFECTIVE', 'Full Refund - Defective Product', 'Product received but defective/damaged', 'FULL_REFUND', 100, TRUE, 200.00, 85),
    ('PRODUCT_NOT_AS_DESCRIBED', 'Partial Refund - Description Mismatch', 'Product differs from description', 'PARTIAL_REFUND', 50, TRUE, 150.00, 80),
    ('DUPLICATE_CHARGE', 'Full Refund - Duplicate Transaction', 'Customer charged multiple times for single transaction', 'FULL_REFUND', 100, FALSE, 1000.00, 95),
    ('SUBSCRIPTION_CANCELLED', 'Full Refund - Cancelled Subscription', 'Charged after subscription cancellation', 'FULL_REFUND', 100, TRUE, 100.00, 75),
    ('MERCHANT_ERROR', 'Full Refund - Merchant Error', 'Merchant processing error', 'FULL_REFUND', 100, FALSE, 500.00, 70),
    ('SERVICE_NOT_RENDERED', 'Full Refund - Service Not Provided', 'Service paid for but not provided', 'FULL_REFUND', 100, TRUE, 300.00, 85),
    ('INCORRECT_AMOUNT', 'Partial Refund - Amount Difference', 'Charged incorrect amount', 'PARTIAL_REFUND', NULL, TRUE, 200.00, 75),
    ('GENERAL', 'Custom Resolution', 'Custom resolution requiring manager approval', 'PARTIAL_REFUND', NULL, TRUE, 50.00, 10);

-- ============================================================================
-- STEP 7: Add constraints
-- ============================================================================

-- Constraint: auto_resolution_score must be between 0 and 1
ALTER TABLE disputes ADD CONSTRAINT chk_auto_resolution_score
    CHECK (auto_resolution_score IS NULL OR (auto_resolution_score >= 0 AND auto_resolution_score <= 1));

-- Constraint: funds_locked_at must be set if funds_locked is true
ALTER TABLE disputes ADD CONSTRAINT chk_funds_locked_consistency
    CHECK (funds_locked = FALSE OR funds_locked_at IS NOT NULL);

-- Constraint: funds_released_at must be after funds_locked_at
ALTER TABLE disputes ADD CONSTRAINT chk_funds_release_after_lock
    CHECK (funds_released_at IS NULL OR funds_locked_at IS NULL OR funds_released_at >= funds_locked_at);

-- Constraint: refund_percentage must be between 0 and 100
ALTER TABLE resolution_templates ADD CONSTRAINT chk_refund_percentage
    CHECK (refund_percentage IS NULL OR (refund_percentage >= 0 AND refund_percentage <= 100));

-- Constraint: auto_approve_threshold must be positive
ALTER TABLE resolution_templates ADD CONSTRAINT chk_auto_approve_threshold
    CHECK (auto_approve_threshold IS NULL OR auto_approve_threshold > 0);

-- ============================================================================
-- STEP 8: Update existing data
-- ============================================================================

-- Set created_by for existing disputes
UPDATE disputes SET created_by = 'LEGACY_SYSTEM' WHERE created_by IS NULL;

-- Set version = 0 for existing disputes (already set by DEFAULT)
-- No update needed

-- ============================================================================
-- STEP 9: Add foreign key constraints (deferred from V001)
-- ============================================================================

-- Note: Foreign keys to user-service and transaction-service are not enforced
-- due to microservices architecture. Referential integrity maintained at application level.

-- ============================================================================
-- VERIFICATION QUERIES (commented out - for manual verification only)
-- ============================================================================

-- Verify table renamed:
-- SELECT COUNT(*) FROM disputes;

-- Verify all new columns exist:
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'disputes'
-- ORDER BY ordinal_position;

-- Verify resolution_templates populated:
-- SELECT COUNT(*) FROM resolution_templates;

-- Verify indexes created:
-- SELECT indexname FROM pg_indexes WHERE tablename = 'disputes';

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
