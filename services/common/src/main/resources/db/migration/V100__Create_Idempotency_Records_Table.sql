-- V100: Create Idempotency Records Table for Universal Idempotency Layer
--
-- CRITICAL: This table prevents duplicate transaction processing across ALL services
--
-- Author: Waqiti Platform Team
-- Date: 2025-10-01
-- Jira: HP-1 - Universal Idempotency Layer
-- Priority: P0 BLOCKER
--
-- Impact: Prevents $5K-15K/month losses from duplicate transactions
--
-- ============================================================================

-- ============================================================================
-- IDEMPOTENCY RECORDS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS idempotency_records (
    -- Primary identification
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    operation_id UUID NOT NULL,

    -- Service and operation classification
    service_name VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'IN_PROGRESS', 'COMPLETED', 'FAILED', 'RETRYABLE_FAILED',
        'EXPIRED', 'CANCELLED', 'PENDING_APPROVAL', 'REJECTED'
    )),

    -- Request and response data
    request_hash TEXT,
    result TEXT,
    error VARCHAR(2000),

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP NOT NULL,

    -- Optimistic locking
    version BIGINT NOT NULL DEFAULT 0,

    -- Audit and compliance fields
    correlation_id VARCHAR(128),
    user_id VARCHAR(64),
    session_id VARCHAR(64),
    client_ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    device_fingerprint VARCHAR(64),

    -- Retry tracking
    retry_count INTEGER DEFAULT 0,
    last_retry_at TIMESTAMP,

    -- Business context (for financial operations)
    amount DECIMAL(19,4),
    currency CHAR(3)
);

-- ============================================================================
-- PERFORMANCE INDEXES
-- ============================================================================

-- PRIMARY INDEX: Fast lookup by idempotency key (99% of queries)
CREATE UNIQUE INDEX idx_idempotency_key ON idempotency_records(idempotency_key);

-- SERVICE CLASSIFICATION: Find operations by service and type
CREATE INDEX idx_service_operation ON idempotency_records(service_name, operation_type);

-- EXPIRATION: Cleanup job queries
CREATE INDEX idx_expires_at ON idempotency_records(expires_at)
    WHERE status NOT IN ('EXPIRED', 'COMPLETED', 'FAILED');

-- USER AUDIT: Find all operations for a user
CREATE INDEX idx_user_operation ON idempotency_records(user_id, operation_type);

-- DISTRIBUTED TRACING: Link related operations
CREATE INDEX idx_correlation_id ON idempotency_records(correlation_id);

-- STATUS MONITORING: Dashboard queries
CREATE INDEX idx_status_created ON idempotency_records(status, created_at DESC);

-- STUCK OPERATIONS: Find long-running in-progress operations
CREATE INDEX idx_in_progress_created ON idempotency_records(created_at)
    WHERE status = 'IN_PROGRESS';

-- FRAUD DETECTION: Find duplicate request hashes
CREATE INDEX idx_request_hash ON idempotency_records(request_hash)
    WHERE request_hash IS NOT NULL AND status = 'COMPLETED';

-- RETRY TRACKING: Find failed operations eligible for retry
CREATE INDEX idx_retryable_failed ON idempotency_records(status, retry_count, created_at)
    WHERE status = 'RETRYABLE_FAILED';

-- SECURITY AUDIT: IP address tracking
CREATE INDEX idx_client_ip ON idempotency_records(client_ip_address, created_at DESC);

-- DEVICE FINGERPRINT: Fraud pattern detection
CREATE INDEX idx_device_fingerprint ON idempotency_records(device_fingerprint, created_at DESC);

-- OPERATION ID: Unique operation tracking
CREATE INDEX idx_operation_id ON idempotency_records(operation_id);

-- USER RATE LIMITING: Count operations by user in time window
CREATE INDEX idx_user_created ON idempotency_records(user_id, operation_type, created_at);

-- FINANCIAL AUDIT: Large transactions
CREATE INDEX idx_amount_created ON idempotency_records(amount DESC, created_at DESC)
    WHERE amount IS NOT NULL;

-- ============================================================================
-- PARTITION STRATEGY (for high-volume production environments)
-- ============================================================================

-- Note: Partitioning commented out by default. Enable for >1M records/day
--
-- CREATE TABLE idempotency_records_y2025m10 PARTITION OF idempotency_records
--     FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
--
-- Partitioning strategy:
-- - Monthly partitions by created_at
-- - Automatic partition creation via pg_partman
-- - Retention: 90 days (configurable)

-- ============================================================================
-- CONSTRAINTS
-- ============================================================================

-- Ensure expires_at is in the future when created
ALTER TABLE idempotency_records ADD CONSTRAINT chk_expires_at_future
    CHECK (expires_at > created_at);

-- Ensure completed_at is after created_at
ALTER TABLE idempotency_records ADD CONSTRAINT chk_completed_at_after_created
    CHECK (completed_at IS NULL OR completed_at >= created_at);

-- Ensure retry_count is non-negative
ALTER TABLE idempotency_records ADD CONSTRAINT chk_retry_count_positive
    CHECK (retry_count >= 0);

-- Ensure amount is positive if set
ALTER TABLE idempotency_records ADD CONSTRAINT chk_amount_positive
    CHECK (amount IS NULL OR amount > 0);

-- Ensure currency is set if amount is set
ALTER TABLE idempotency_records ADD CONSTRAINT chk_currency_with_amount
    CHECK ((amount IS NULL AND currency IS NULL) OR (amount IS NOT NULL AND currency IS NOT NULL));

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Trigger to auto-update version on every update (optimistic locking)
CREATE OR REPLACE FUNCTION update_idempotency_record_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_idempotency_version
    BEFORE UPDATE ON idempotency_records
    FOR EACH ROW
    EXECUTE FUNCTION update_idempotency_record_version();

-- Trigger to set completed_at when status changes to final state
CREATE OR REPLACE FUNCTION set_idempotency_completed_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('COMPLETED', 'FAILED', 'EXPIRED', 'CANCELLED', 'REJECTED')
       AND OLD.status NOT IN ('COMPLETED', 'FAILED', 'EXPIRED', 'CANCELLED', 'REJECTED')
       AND NEW.completed_at IS NULL THEN
        NEW.completed_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_set_idempotency_completed_at
    BEFORE UPDATE ON idempotency_records
    FOR EACH ROW
    EXECUTE FUNCTION set_idempotency_completed_at();

-- ============================================================================
-- CLEANUP FUNCTION (for scheduled job)
-- ============================================================================

CREATE OR REPLACE FUNCTION cleanup_expired_idempotency_records(retention_days INTEGER DEFAULT 90)
RETURNS TABLE (
    expired_count BIGINT,
    deleted_count BIGINT
) AS $$
DECLARE
    expired_count_val BIGINT;
    deleted_count_val BIGINT;
BEGIN
    -- Mark expired records
    UPDATE idempotency_records
    SET status = 'EXPIRED'
    WHERE expires_at < CURRENT_TIMESTAMP
      AND status NOT IN ('EXPIRED', 'COMPLETED', 'FAILED');

    GET DIAGNOSTICS expired_count_val = ROW_COUNT;

    -- Delete old records beyond retention period
    DELETE FROM idempotency_records
    WHERE created_at < CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL
      AND status IN ('COMPLETED', 'FAILED', 'EXPIRED');

    GET DIAGNOSTICS deleted_count_val = ROW_COUNT;

    RETURN QUERY SELECT expired_count_val, deleted_count_val;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- MONITORING VIEWS
-- ============================================================================

-- View: Real-time idempotency statistics
CREATE OR REPLACE VIEW idempotency_statistics AS
SELECT
    status,
    COUNT(*) AS count,
    MIN(created_at) AS oldest_record,
    MAX(created_at) AS newest_record,
    AVG(EXTRACT(EPOCH FROM (COALESCE(completed_at, CURRENT_TIMESTAMP) - created_at))) AS avg_duration_seconds
FROM idempotency_records
WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY status
ORDER BY count DESC;

-- View: Service-level statistics
CREATE OR REPLACE VIEW idempotency_service_stats AS
SELECT
    service_name,
    operation_type,
    COUNT(*) AS total_operations,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') AS successful,
    COUNT(*) FILTER (WHERE status IN ('FAILED', 'RETRYABLE_FAILED')) AS failed,
    COUNT(*) FILTER (WHERE status = 'IN_PROGRESS') AS in_progress,
    ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'COMPLETED') / NULLIF(COUNT(*), 0), 2) AS success_rate_pct
FROM idempotency_records
WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY service_name, operation_type
ORDER BY total_operations DESC;

-- View: Stuck operations (in progress for > 5 minutes)
CREATE OR REPLACE VIEW idempotency_stuck_operations AS
SELECT
    id,
    idempotency_key,
    service_name,
    operation_type,
    user_id,
    created_at,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - created_at)) AS age_seconds
FROM idempotency_records
WHERE status = 'IN_PROGRESS'
  AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
ORDER BY created_at ASC;

-- View: High retry operations (potential issues)
CREATE OR REPLACE VIEW idempotency_high_retry_operations AS
SELECT
    idempotency_key,
    service_name,
    operation_type,
    status,
    retry_count,
    error,
    created_at,
    last_retry_at
FROM idempotency_records
WHERE retry_count >= 3
ORDER BY retry_count DESC, created_at DESC
LIMIT 100;

-- ============================================================================
-- GRANT PERMISSIONS
-- ============================================================================

-- Grant SELECT to read-only dashboard users
GRANT SELECT ON idempotency_records TO waqiti_dashboard_user;
GRANT SELECT ON idempotency_statistics TO waqiti_dashboard_user;
GRANT SELECT ON idempotency_service_stats TO waqiti_dashboard_user;
GRANT SELECT ON idempotency_stuck_operations TO waqiti_dashboard_user;
GRANT SELECT ON idempotency_high_retry_operations TO waqiti_dashboard_user;

-- Grant full access to application users
GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_records TO waqiti_app_user;

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE idempotency_records IS
    'Universal idempotency layer for preventing duplicate transaction processing across all services. Prevents $5K-15K/month losses from duplicates.';

COMMENT ON COLUMN idempotency_records.idempotency_key IS
    'Unique key for idempotency check. Format: service:operation:identifier';

COMMENT ON COLUMN idempotency_records.request_hash IS
    'SHA-256 hash of request payload for duplicate detection and fraud prevention';

COMMENT ON COLUMN idempotency_records.correlation_id IS
    'Distributed trace ID linking related operations across services';

COMMENT ON COLUMN idempotency_records.device_fingerprint IS
    'Device fingerprint for fraud detection and velocity checking';

COMMENT ON INDEX idx_idempotency_key IS
    'PRIMARY INDEX: Optimized for <5ms lookups by idempotency key (99% of queries)';

COMMENT ON FUNCTION cleanup_expired_idempotency_records IS
    'Scheduled cleanup function: Run daily at 2 AM to mark expired records and delete old data. Retention: 90 days default.';

-- ============================================================================
-- VERIFICATION QUERIES (for manual testing)
-- ============================================================================

-- Test queries (commented out - use for verification after migration):

-- 1. Check table created successfully:
-- SELECT COUNT(*) FROM idempotency_records;

-- 2. Verify indexes:
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'idempotency_records';

-- 3. Test insert:
-- INSERT INTO idempotency_records (idempotency_key, operation_id, service_name, operation_type, status, expires_at)
-- VALUES ('test:operation:123', gen_random_uuid(), 'payment-service', 'TEST_OPERATION', 'COMPLETED', CURRENT_TIMESTAMP + INTERVAL '24 hours');

-- 4. Test unique constraint:
-- Should fail with duplicate key error:
-- INSERT INTO idempotency_records (idempotency_key, operation_id, service_name, operation_type, status, expires_at)
-- VALUES ('test:operation:123', gen_random_uuid(), 'payment-service', 'TEST_OPERATION', 'COMPLETED', CURRENT_TIMESTAMP + INTERVAL '24 hours');

-- 5. View statistics:
-- SELECT * FROM idempotency_statistics;
-- SELECT * FROM idempotency_service_stats;

-- ============================================================================
-- MIGRATION VALIDATION
-- ============================================================================

DO $$
DECLARE
    index_count INTEGER;
    trigger_count INTEGER;
BEGIN
    -- Verify indexes created
    SELECT COUNT(*) INTO index_count
    FROM pg_indexes
    WHERE tablename = 'idempotency_records';

    IF index_count < 14 THEN
        RAISE EXCEPTION 'Migration V100 validation failed: Expected at least 14 indexes, found %', index_count;
    END IF;

    -- Verify triggers created
    SELECT COUNT(*) INTO trigger_count
    FROM pg_trigger
    WHERE tgrelid = 'idempotency_records'::regclass;

    IF trigger_count < 2 THEN
        RAISE EXCEPTION 'Migration V100 validation failed: Expected 2 triggers, found %', trigger_count;
    END IF;

    RAISE NOTICE 'Migration V100 validation successful: % indexes, % triggers', index_count, trigger_count;
END $$;
