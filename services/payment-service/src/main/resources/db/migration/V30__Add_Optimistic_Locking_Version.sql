-- V30__Add_Optimistic_Locking_Version.sql
--
-- Adds version column for optimistic locking to payment tables
-- Prevents race conditions in payment processing
--
-- Related to: P0-1 (Race Condition Prevention with Optimistic Locking)

-- Add version column to payments table (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'payments'
        AND column_name = 'version'
    ) THEN
        ALTER TABLE payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

        COMMENT ON COLUMN payments.version IS 'Optimistic locking version - prevents race conditions in payment state changes';
    END IF;
END $$;

-- Add version column to payment_transactions table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'payment_transactions'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'payment_transactions'
            AND column_name = 'version'
        ) THEN
            ALTER TABLE payment_transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

            COMMENT ON COLUMN payment_transactions.version IS 'Optimistic locking version - prevents concurrent modifications';
        END IF;
    END IF;
END $$;

-- Add version column to payment_methods table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'payment_methods'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'payment_methods'
            AND column_name = 'version'
        ) THEN
            ALTER TABLE payment_methods ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

            COMMENT ON COLUMN payment_methods.version IS 'Optimistic locking version - prevents concurrent payment method updates';
        END IF;
    END IF;
END $$;

-- Add version column to payment_refunds table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'payment_refunds'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'payment_refunds'
            AND column_name = 'version'
        ) THEN
            ALTER TABLE payment_refunds ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

            COMMENT ON COLUMN payment_refunds.version IS 'Optimistic locking version - prevents double refunds';
        END IF;
    END IF;
END $$;

-- Add version column to scheduled_payments table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'scheduled_payments'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'scheduled_payments'
            AND column_name = 'version'
        ) THEN
            ALTER TABLE scheduled_payments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

            COMMENT ON COLUMN scheduled_payments.version IS 'Optimistic locking version - prevents concurrent schedule modifications';
        END IF;
    END IF;
END $$;

-- Create indexes on version columns for performance
CREATE INDEX IF NOT EXISTS idx_payments_version ON payments (version);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_version ON payment_transactions (version) WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_transactions');

-- View to detect payments with high contention
CREATE OR REPLACE VIEW high_contention_payments AS
SELECT
    id as payment_id,
    user_id,
    amount,
    status,
    version,
    updated_at,
    CASE
        WHEN version > 50 THEN 'CRITICAL'
        WHEN version > 20 THEN 'HIGH'
        WHEN version > 10 THEN 'MODERATE'
        ELSE 'NORMAL'
    END as contention_level
FROM payments
WHERE version > 10
ORDER BY version DESC;

COMMENT ON VIEW high_contention_payments IS 'Identifies payments with unusually high version numbers (may indicate retry storms or race conditions)';
