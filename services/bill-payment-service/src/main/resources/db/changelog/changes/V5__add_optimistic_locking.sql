-- Migration V5: Add optimistic locking (version columns)
-- Created: 2025-11-18
-- Description: Add version columns to financial entities for optimistic locking and concurrency control

-- Add version column to bill_payments table
ALTER TABLE bill_payments
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add version column to bills table
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add version column to auto_pay_configs table
ALTER TABLE auto_pay_configs
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add comments explaining the version column purpose
COMMENT ON COLUMN bill_payments.version IS 'Optimistic locking version - prevents concurrent modification conflicts';
COMMENT ON COLUMN bills.version IS 'Optimistic locking version - prevents simultaneous bill updates';
COMMENT ON COLUMN auto_pay_configs.version IS 'Optimistic locking version - prevents concurrent auto-pay config changes';

-- Note: JPA will automatically increment these version numbers on each update
-- If a concurrent update occurs, JPA will throw OptimisticLockException
