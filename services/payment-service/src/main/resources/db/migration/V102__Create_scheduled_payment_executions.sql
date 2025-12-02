-- ===============================================
-- Migration: V102__Create_scheduled_payment_executions.sql
-- Description: Create scheduled_payment_executions table with comprehensive audit support
-- Author: Claude Code - Production Remediation
-- Date: 2025-10-07
-- JIRA: PROD-P0-001
-- ===============================================

-- Create scheduled_payment_executions table
CREATE TABLE IF NOT EXISTS scheduled_payment_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheduled_payment_id UUID NOT NULL,
    transaction_id UUID,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED', 'PENDING', 'CANCELLED')),
    error_message VARCHAR(1000),
    error_code VARCHAR(50),
    execution_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count INTEGER DEFAULT 0 CHECK (retry_count >= 0 AND retry_count <= 10),
    processing_duration_ms BIGINT CHECK (processing_duration_ms >= 0),
    idempotency_key VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,

    -- Foreign key constraint
    CONSTRAINT fk_spe_scheduled_payment
        FOREIGN KEY (scheduled_payment_id)
        REFERENCES scheduled_payments(id)
        ON DELETE CASCADE,

    -- Unique constraint for idempotency
    CONSTRAINT uk_spe_idempotency_key
        UNIQUE (idempotency_key)
);

-- Create indexes for query optimization
CREATE INDEX idx_spe_scheduled_payment_id
    ON scheduled_payment_executions(scheduled_payment_id);

CREATE INDEX idx_spe_transaction_id
    ON scheduled_payment_executions(transaction_id);

CREATE INDEX idx_spe_status
    ON scheduled_payment_executions(status);

CREATE INDEX idx_spe_execution_date
    ON scheduled_payment_executions(execution_date DESC);

CREATE INDEX idx_spe_status_execution_date
    ON scheduled_payment_executions(status, execution_date DESC);

-- Index for cleanup queries
CREATE INDEX idx_spe_failed_retryable
    ON scheduled_payment_executions(status, retry_count, execution_date)
    WHERE status = 'FAILED' AND retry_count < 3;

-- Index for performance monitoring
CREATE INDEX idx_spe_processing_duration
    ON scheduled_payment_executions(processing_duration_ms DESC)
    WHERE processing_duration_ms IS NOT NULL;

-- Add comments for documentation
COMMENT ON TABLE scheduled_payment_executions IS
    'Records each execution attempt of scheduled payments with idempotency support. '
    'Provides comprehensive audit trail and execution history for compliance and debugging.';

COMMENT ON COLUMN scheduled_payment_executions.id IS
    'Unique identifier for the execution record';

COMMENT ON COLUMN scheduled_payment_executions.scheduled_payment_id IS
    'Reference to the parent scheduled payment';

COMMENT ON COLUMN scheduled_payment_executions.transaction_id IS
    'Reference to the actual payment transaction if execution was successful';

COMMENT ON COLUMN scheduled_payment_executions.idempotency_key IS
    'Unique key to prevent duplicate executions of the same scheduled payment';

COMMENT ON COLUMN scheduled_payment_executions.processing_duration_ms IS
    'Execution time in milliseconds for performance monitoring';

COMMENT ON COLUMN scheduled_payment_executions.version IS
    'Optimistic locking version for concurrent update protection';

-- Grant permissions (adjust based on your user setup)
-- GRANT SELECT, INSERT, UPDATE ON scheduled_payment_executions TO payment_service_app;

-- Create trigger to update version on updates (if not handled by JPA)
CREATE OR REPLACE FUNCTION update_scheduled_payment_execution_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_spe_version
    BEFORE UPDATE ON scheduled_payment_executions
    FOR EACH ROW
    EXECUTE FUNCTION update_scheduled_payment_execution_version();

-- ===============================================
-- Verification Queries (for manual testing)
-- ===============================================

-- Count scheduled payment executions
-- SELECT COUNT(*) as total_executions FROM scheduled_payment_executions;

-- Get execution statistics by status
-- SELECT status, COUNT(*) as count, AVG(processing_duration_ms) as avg_duration_ms
-- FROM scheduled_payment_executions
-- GROUP BY status;

-- Find failed executions that can be retried
-- SELECT id, scheduled_payment_id, error_message, retry_count
-- FROM scheduled_payment_executions
-- WHERE status = 'FAILED' AND retry_count < 3
-- ORDER BY execution_date DESC;

-- ===============================================
-- Rollback Script (if needed)
-- ===============================================

-- DROP TRIGGER IF EXISTS trg_update_spe_version ON scheduled_payment_executions;
-- DROP FUNCTION IF EXISTS update_scheduled_payment_execution_version();
-- DROP TABLE IF EXISTS scheduled_payment_executions CASCADE;
