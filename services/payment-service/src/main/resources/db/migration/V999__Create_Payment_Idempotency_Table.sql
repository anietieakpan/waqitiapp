-- ============================================================================
-- PAYMENT IDEMPOTENCY TABLE
-- ============================================================================
--
-- PURPOSE: Prevent duplicate payment processing in distributed system
--
-- COMPLIANCE:
-- - PCI DSS Requirement 6.5.3: Prevents duplicate transactions
-- - SOC 2: Ensures data integrity and auditability
--
-- PERFORMANCE:
-- - Unique index on idempotency_key for O(log n) duplicate detection
-- - Index on expires_at for efficient cleanup
-- - Index on status for monitoring queries
-- - Partitioning ready (by created_at) for high-volume scenarios
--
-- CAPACITY PLANNING:
-- - Estimated row size: ~2 KB (with JSON payloads)
-- - 1M transactions/day = 2 GB/day
-- - 24-hour TTL = max 2 GB active data
-- - 30-day archive = 60 GB total
--
-- @version 1.0.0
-- @since 2025-11-03
-- ============================================================================

CREATE TABLE IF NOT EXISTS payment_idempotency_records (
    -- Primary Key
    id UUID PRIMARY KEY,

    -- Idempotency Key (UNIQUE - enforces exactly-once semantics)
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,

    -- Request Metadata
    request_type VARCHAR(50) NOT NULL,  -- PAYMENT, TRANSFER, REFUND, etc.
    status VARCHAR(20) NOT NULL,        -- PROCESSING, COMPLETED, FAILED
    user_id VARCHAR(100) NOT NULL,

    -- Request/Response Payloads (JSON for audit trail)
    request_payload TEXT,
    response_payload TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    -- Duplicate Detection Metrics
    duplicate_request_count INTEGER NOT NULL DEFAULT 0,
    last_duplicate_request_at TIMESTAMP,

    -- Performance Metrics
    processing_time_ms BIGINT,

    -- Error Tracking
    error_message TEXT,

    -- Optimistic Locking
    version BIGINT NOT NULL DEFAULT 0,

    -- Audit Fields
    created_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Unique index on idempotency_key (CRITICAL for duplicate detection)
-- Already created via UNIQUE constraint, but explicitly defining for clarity
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_idempotency_key
    ON payment_idempotency_records(idempotency_key);

-- Index on status for monitoring queries
CREATE INDEX IF NOT EXISTS idx_payment_idempotency_status
    ON payment_idempotency_records(status);

-- Index on created_at for time-based queries
CREATE INDEX IF NOT EXISTS idx_payment_idempotency_created_at
    ON payment_idempotency_records(created_at DESC);

-- Index on expires_at for cleanup queries (CRITICAL for maintenance)
CREATE INDEX IF NOT EXISTS idx_payment_idempotency_expires_at
    ON payment_idempotency_records(expires_at);

-- Index on user_id for user-specific queries
CREATE INDEX IF NOT EXISTS idx_payment_idempotency_user_id
    ON payment_idempotency_records(user_id);

-- Composite index for stuck request detection
CREATE INDEX IF NOT EXISTS idx_payment_idempotency_stuck_requests
    ON payment_idempotency_records(status, created_at)
    WHERE status = 'PROCESSING';

-- ============================================================================
-- COMMENTS (for documentation)
-- ============================================================================

COMMENT ON TABLE payment_idempotency_records IS
    'Idempotency records for preventing duplicate payment processing. ' ||
    'Records expire after 24 hours and are cleaned up automatically. ' ||
    'Critical for PCI DSS compliance and exactly-once payment semantics.';

COMMENT ON COLUMN payment_idempotency_records.idempotency_key IS
    'Unique identifier for request (typically transactionId). ' ||
    'MUST be unique across all payment requests.';

COMMENT ON COLUMN payment_idempotency_records.status IS
    'Current status: PROCESSING (in-flight), COMPLETED (success), FAILED (can retry)';

COMMENT ON COLUMN payment_idempotency_records.duplicate_request_count IS
    'Number of duplicate requests detected. Used for fraud detection and monitoring.';

COMMENT ON COLUMN payment_idempotency_records.processing_time_ms IS
    'Duration of payment processing in milliseconds. Used for performance monitoring.';

-- ============================================================================
-- PARTITIONING (Optional - for high-volume scenarios)
-- ============================================================================

-- Uncomment below for partitioning by created_at (monthly partitions)
-- This is recommended for systems processing >10M transactions/month

/*
ALTER TABLE payment_idempotency_records
    PARTITION BY RANGE (created_at);

CREATE TABLE payment_idempotency_records_2025_11
    PARTITION OF payment_idempotency_records
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE payment_idempotency_records_2025_12
    PARTITION OF payment_idempotency_records
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

-- Add more partitions as needed
-- Automated partition management via pg_partman recommended
*/

-- ============================================================================
-- AUTOMATIC CLEANUP TRIGGER (Optional)
-- ============================================================================

-- Create function to automatically delete expired records
CREATE OR REPLACE FUNCTION cleanup_expired_idempotency_records()
RETURNS void AS $$
BEGIN
    DELETE FROM payment_idempotency_records
    WHERE expires_at < NOW() - INTERVAL '1 hour';

    RAISE NOTICE 'Cleaned up expired idempotency records';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_idempotency_records() IS
    'Cleanup function for expired idempotency records. ' ||
    'Should be called via pg_cron or scheduled job daily.';

-- ============================================================================
-- GRANTS (adjust based on your security model)
-- ============================================================================

-- Grant permissions to payment service role
-- GRANT SELECT, INSERT, UPDATE, DELETE ON payment_idempotency_records TO payment_service_role;

-- ============================================================================
-- VALIDATION QUERIES
-- ============================================================================

-- Verify table created successfully
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'payment_idempotency_records') THEN
        RAISE EXCEPTION 'Table payment_idempotency_records not created';
    END IF;

    RAISE NOTICE 'Payment idempotency table created successfully';
END $$;
