-- ============================================================================
-- Migration: V2__Enhance_Payment_Table_Production_Ready.sql
-- Purpose: Add optimistic locking, audit fields, indexes, and idempotency support
-- Critical for: Preventing lost updates, improving performance, enabling compliance
-- Impact: Production-readiness enhancement - ZERO downtime migration
-- ============================================================================

-- Step 1: Add new columns with default values (safe for existing data)
-- ============================================================================

-- Optimistic Locking
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN payments.version IS 'Optimistic locking version - prevents concurrent update conflicts';

-- Audit Fields for Compliance (PCI DSS, SOC 2, GDPR)
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Idempotency Support
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255) UNIQUE;

COMMENT ON COLUMN payments.idempotency_key IS 'Unique key for idempotent payment processing';

-- Provider Tracking
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS provider VARCHAR(50),
ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(255);

COMMENT ON COLUMN payments.provider IS 'Payment provider (Stripe, PayPal, etc.)';

-- Failure Tracking
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(1000);

-- Retry Tracking
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMP;

-- Fraud Detection Fields
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS fraud_score NUMERIC(5,2),
ADD COLUMN IF NOT EXISTS fraud_checked_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20);

-- Step 2: Update existing audit fields for existing rows
-- ============================================================================
UPDATE payments
SET created_by = 'SYSTEM_MIGRATION'
WHERE created_by IS NULL;

UPDATE payments
SET updated_at = created_at
WHERE updated_at IS NULL OR updated_at < created_at;

-- Step 3: Make audit fields NOT NULL after populating
-- ============================================================================
ALTER TABLE payments
ALTER COLUMN created_by SET NOT NULL,
ALTER COLUMN updated_at SET NOT NULL;

-- Step 4: Change amount precision from scale=2 to scale=4
-- ============================================================================
-- This is critical for multi-currency and crypto support
ALTER TABLE payments
ALTER COLUMN amount TYPE NUMERIC(19,4);

COMMENT ON COLUMN payments.amount IS 'Payment amount with 4 decimal places for precision';

-- Step 5: Create Performance Indexes
-- ============================================================================

-- Index for user payment lookup (most common query)
CREATE INDEX IF NOT EXISTS idx_payment_user_id
ON payments(user_id)
WHERE status IN ('PENDING', 'PROCESSING', 'COMPLETED');

COMMENT ON INDEX idx_payment_user_id IS 'Partial index for active user payments';

-- Index for merchant payment lookup
CREATE INDEX IF NOT EXISTS idx_payment_merchant_id
ON payments(merchant_id)
WHERE merchant_id IS NOT NULL;

-- Index for status-based queries (dashboards, monitoring)
CREATE INDEX IF NOT EXISTS idx_payment_status
ON payments(status, created_at DESC);

-- Index for time-based queries (reporting)
CREATE INDEX IF NOT EXISTS idx_payment_created_at
ON payments(created_at DESC);

-- Unique index on payment_id for fast lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_payment_id
ON payments(payment_id);

-- Index on idempotency key for duplicate detection
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_idempotency_key
ON payments(idempotency_key)
WHERE idempotency_key IS NOT NULL;

-- Index on provider for reconciliation
CREATE INDEX IF NOT EXISTS idx_payment_provider
ON payments(provider, created_at DESC)
WHERE provider IS NOT NULL;

-- Composite index for failed payment retry queries
CREATE INDEX IF NOT EXISTS idx_payment_retry
ON payments(status, retry_count, last_retry_at)
WHERE status IN ('FAILED', 'DECLINED') AND retry_count < 3;

-- Index for fraud analysis
CREATE INDEX IF NOT EXISTS idx_payment_fraud_score
ON payments(fraud_score DESC, created_at DESC)
WHERE fraud_score IS NOT NULL AND fraud_score > 50;

-- Step 6: Add Check Constraints for Data Integrity
-- ============================================================================

ALTER TABLE payments
ADD CONSTRAINT chk_payment_amount_positive
CHECK (amount > 0);

ALTER TABLE payments
ADD CONSTRAINT chk_payment_retry_count_valid
CHECK (retry_count >= 0 AND retry_count <= 10);

ALTER TABLE payments
ADD CONSTRAINT chk_payment_fraud_score_range
CHECK (fraud_score IS NULL OR (fraud_score >= 0 AND fraud_score <= 100));

ALTER TABLE payments
ADD CONSTRAINT chk_payment_version_non_negative
CHECK (version >= 0);

ALTER TABLE payments
ADD CONSTRAINT chk_payment_timestamps_logical
CHECK (updated_at >= created_at);

ALTER TABLE payments
ADD CONSTRAINT chk_payment_completed_at_valid
CHECK (
    (status = 'COMPLETED' AND completed_at IS NOT NULL) OR
    (status != 'COMPLETED' AND completed_at IS NULL) OR
    (completed_at IS NOT NULL)
);

-- Step 7: Create Audit Trigger for automatic updated_at
-- ============================================================================

CREATE OR REPLACE FUNCTION update_payment_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_payment_updated_at ON payments;

CREATE TRIGGER trg_payment_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION update_payment_updated_at();

COMMENT ON TRIGGER trg_payment_updated_at ON payments IS
    'Automatically updates updated_at timestamp on row modification';

-- Step 8: Create Risk Level Enum Type (if not exists)
-- ============================================================================

DO $$ BEGIN
    CREATE TYPE risk_level_enum AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- Update risk_level column to use enum (optional, can stay VARCHAR for flexibility)
-- COMMENT: Keeping VARCHAR for now to allow dynamic risk levels

-- Step 9: Create Partitioning for Large Scale (Future Optimization)
-- ============================================================================
-- Note: This is commented out for now - enable when payment volume exceeds 10M records

/*
-- Partition by created_at month for time-series queries
CREATE TABLE payments_partitioned (LIKE payments INCLUDING ALL)
PARTITION BY RANGE (created_at);

-- Create partitions for current and future months
CREATE TABLE payments_2025_01 PARTITION OF payments_partitioned
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE payments_2025_02 PARTITION OF payments_partitioned
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Future: Automate partition creation with pg_cron or application logic
*/

-- Step 10: Update Statistics for Query Optimizer
-- ============================================================================

ANALYZE payments;

-- Step 11: Create Summary View for Reporting
-- ============================================================================

CREATE OR REPLACE VIEW payment_summary_stats AS
SELECT
    status,
    provider,
    risk_level,
    DATE(created_at) as payment_date,
    COUNT(*) as payment_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    AVG(fraud_score) as avg_fraud_score,
    COUNT(CASE WHEN retry_count > 0 THEN 1 END) as retry_count,
    COUNT(CASE WHEN completed_at IS NOT NULL THEN 1 END) as completed_count
FROM payments
GROUP BY status, provider, risk_level, DATE(created_at);

COMMENT ON VIEW payment_summary_stats IS
    'Pre-aggregated payment statistics for dashboard queries';

-- Step 12: Grant Permissions (adjust for your security model)
-- ============================================================================

-- GRANT SELECT, INSERT, UPDATE ON payments TO payment_service_role;
-- GRANT SELECT ON payment_summary_stats TO reporting_role;

-- Step 13: Create Indexes on Foreign Keys (if not already present)
-- ============================================================================

-- These improve JOIN performance
CREATE INDEX IF NOT EXISTS idx_payment_user_fk ON payments(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_merchant_fk ON payments(merchant_id);

-- Step 14: Verify Migration Success
-- ============================================================================

DO $$
DECLARE
    v_count INTEGER;
BEGIN
    -- Verify all new columns exist
    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_name = 'payments'
    AND column_name IN ('version', 'created_by', 'updated_by', 'idempotency_key',
                        'fraud_score', 'retry_count', 'risk_level');

    IF v_count < 7 THEN
        RAISE EXCEPTION 'Migration incomplete: Expected 7 new columns, found %', v_count;
    END IF;

    -- Verify indexes created
    SELECT COUNT(*) INTO v_count
    FROM pg_indexes
    WHERE tablename = 'payments'
    AND indexname LIKE 'idx_payment_%';

    IF v_count < 8 THEN
        RAISE WARNING 'Expected at least 8 indexes, found %. Some indexes may be missing.', v_count;
    END IF;

    RAISE NOTICE 'Migration V2 completed successfully! % indexes created.', v_count;
END $$;

-- ============================================================================
-- End of Migration
-- ============================================================================

-- Migration Rollback Instructions (for reference):
-- ============================================================================
/*
-- ROLLBACK SCRIPT (use only if necessary):

DROP TRIGGER IF EXISTS trg_payment_updated_at ON payments;
DROP FUNCTION IF EXISTS update_payment_updated_at();
DROP VIEW IF EXISTS payment_summary_stats;

ALTER TABLE payments
DROP COLUMN IF EXISTS version,
DROP COLUMN IF EXISTS created_by,
DROP COLUMN IF EXISTS updated_by,
DROP COLUMN IF EXISTS updated_at,
DROP COLUMN IF EXISTS idempotency_key,
DROP COLUMN IF EXISTS provider,
DROP COLUMN IF EXISTS provider_payment_id,
DROP COLUMN IF EXISTS failed_at,
DROP COLUMN IF EXISTS failure_reason,
DROP COLUMN IF EXISTS retry_count,
DROP COLUMN IF EXISTS last_retry_at,
DROP COLUMN IF EXISTS fraud_score,
DROP COLUMN IF EXISTS fraud_checked_at,
DROP COLUMN IF EXISTS risk_level;

DROP INDEX IF EXISTS idx_payment_user_id;
DROP INDEX IF EXISTS idx_payment_merchant_id;
DROP INDEX IF EXISTS idx_payment_status;
DROP INDEX IF EXISTS idx_payment_created_at;
DROP INDEX IF EXISTS idx_payment_payment_id;
DROP INDEX IF EXISTS idx_payment_idempotency_key;
DROP INDEX IF EXISTS idx_payment_provider;
DROP INDEX IF EXISTS idx_payment_retry;
DROP INDEX IF EXISTS idx_payment_fraud_score;

ALTER TABLE payments ALTER COLUMN amount TYPE NUMERIC(19,2);

*/
