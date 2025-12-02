-- =====================================================
-- COMPENSATION TRANSACTION TABLES
-- Production-grade schema for SAGA pattern compensation
-- =====================================================

-- Create compensation_transactions table
CREATE TABLE IF NOT EXISTS compensation_transactions (
    compensation_id VARCHAR(64) PRIMARY KEY,
    original_transaction_id VARCHAR(64) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    target_service VARCHAR(100),
    compensation_action VARCHAR(255),
    compensation_data JSONB,
    reason TEXT,
    original_error TEXT,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    max_retries INTEGER NOT NULL DEFAULT 3,
    current_retry INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP,
    completed_at TIMESTAMP,
    initiated_by VARCHAR(100),
    correlation_id VARCHAR(64),
    metadata JSONB,
    CONSTRAINT chk_current_retry CHECK (current_retry >= 0),
    CONSTRAINT chk_max_retries CHECK (max_retries > 0),
    CONSTRAINT chk_retry_limit CHECK (current_retry <= max_retries)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_compensation_status
    ON compensation_transactions(status);

CREATE INDEX IF NOT EXISTS idx_compensation_original_tx
    ON compensation_transactions(original_transaction_id);

CREATE INDEX IF NOT EXISTS idx_compensation_created_at
    ON compensation_transactions(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_compensation_priority
    ON compensation_transactions(priority);

CREATE INDEX IF NOT EXISTS idx_compensation_type
    ON compensation_transactions(type);

-- Composite index for finding stuck compensations
CREATE INDEX IF NOT EXISTS idx_compensation_pending_created
    ON compensation_transactions(status, created_at)
    WHERE status = 'PENDING';

-- Composite index for retry queue
CREATE INDEX IF NOT EXISTS idx_compensation_retry_queue
    ON compensation_transactions(status, last_attempt_at)
    WHERE status = 'RETRYING';

-- Index for manual intervention queue
CREATE INDEX IF NOT EXISTS idx_compensation_manual_intervention
    ON compensation_transactions(status, created_at)
    WHERE status = 'REQUIRES_MANUAL_INTERVENTION';

-- Add comments for documentation
COMMENT ON TABLE compensation_transactions IS
    'Stores compensation transactions for distributed transaction rollback (SAGA pattern)';

COMMENT ON COLUMN compensation_transactions.compensation_id IS
    'Unique identifier for this compensation transaction';

COMMENT ON COLUMN compensation_transactions.original_transaction_id IS
    'ID of the original transaction being compensated';

COMMENT ON COLUMN compensation_transactions.type IS
    'Type of compensation action: REFUND_PAYMENT, REVERSE_WALLET_DEBIT, etc.';

COMMENT ON COLUMN compensation_transactions.status IS
    'Current status: PENDING, IN_PROGRESS, COMPLETED, FAILED, RETRYING, REQUIRES_MANUAL_INTERVENTION';

COMMENT ON COLUMN compensation_transactions.priority IS
    'Execution priority: CRITICAL, HIGH, NORMAL, LOW';

COMMENT ON COLUMN compensation_transactions.compensation_data IS
    'JSON data needed to execute the compensation';

COMMENT ON COLUMN compensation_transactions.metadata IS
    'Additional metadata for tracking and debugging';

-- Create audit trail table for compensation events
CREATE TABLE IF NOT EXISTS compensation_audit_log (
    id BIGSERIAL PRIMARY KEY,
    compensation_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_status VARCHAR(50),
    new_status VARCHAR(50),
    retry_attempt INTEGER,
    error_message TEXT,
    performed_by VARCHAR(100),
    details JSONB,
    CONSTRAINT fk_compensation
        FOREIGN KEY (compensation_id)
        REFERENCES compensation_transactions(compensation_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_audit_compensation_id
    ON compensation_audit_log(compensation_id);

CREATE INDEX IF NOT EXISTS idx_audit_timestamp
    ON compensation_audit_log(event_timestamp DESC);

COMMENT ON TABLE compensation_audit_log IS
    'Audit trail for all compensation transaction state changes';

-- Create materialized view for compensation metrics
CREATE MATERIALIZED VIEW IF NOT EXISTS compensation_metrics AS
SELECT
    DATE_TRUNC('hour', created_at) as hour,
    type,
    status,
    priority,
    COUNT(*) as count,
    AVG(current_retry) as avg_retries,
    MAX(current_retry) as max_retries,
    AVG(EXTRACT(EPOCH FROM (COALESCE(completed_at, CURRENT_TIMESTAMP) - created_at))) as avg_duration_seconds
FROM compensation_transactions
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY DATE_TRUNC('hour', created_at), type, status, priority;

CREATE UNIQUE INDEX IF NOT EXISTS idx_compensation_metrics_unique
    ON compensation_metrics(hour, type, status, priority);

COMMENT ON MATERIALIZED VIEW compensation_metrics IS
    'Aggregated metrics for compensation transactions (refreshed hourly)';

-- Create function to refresh metrics (called by cron job)
CREATE OR REPLACE FUNCTION refresh_compensation_metrics()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY compensation_metrics;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION refresh_compensation_metrics() IS
    'Refreshes the compensation_metrics materialized view';
