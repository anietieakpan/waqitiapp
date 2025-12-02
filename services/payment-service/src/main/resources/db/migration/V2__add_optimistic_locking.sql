-- CRITICAL FIX: Add version columns for optimistic locking
-- This prevents double-spending and race conditions in financial transactions
-- Author: Waqiti Engineering
-- Date: 2025-09-20

-- Add version column to transactions table
ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to payments table
ALTER TABLE payments 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to payment_methods table
ALTER TABLE payment_methods 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to payment_requests table
ALTER TABLE payment_requests 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to scheduled_payments table
ALTER TABLE scheduled_payments 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to instant_transfers table
ALTER TABLE instant_transfers 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add audit columns if missing
ALTER TABLE transactions
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

ALTER TABLE payments
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- Create indexes for better performance on version checks
CREATE INDEX IF NOT EXISTS idx_transactions_version ON transactions(version);
CREATE INDEX IF NOT EXISTS idx_payments_version ON payments(version);
CREATE INDEX IF NOT EXISTS idx_payment_methods_version ON payment_methods(version);

-- Update existing rows to have version = 1
UPDATE transactions SET version = 1 WHERE version = 0;
UPDATE payments SET version = 1 WHERE version = 0;
UPDATE payment_methods SET version = 1 WHERE version = 0;
UPDATE payment_requests SET version = 1 WHERE version = 0;
UPDATE scheduled_payments SET version = 1 WHERE version = 0;
UPDATE instant_transfers SET version = 1 WHERE version = 0;

-- Add comments for documentation
COMMENT ON COLUMN transactions.version IS 'Optimistic locking version to prevent concurrent updates';
COMMENT ON COLUMN payments.version IS 'Optimistic locking version to prevent concurrent updates';
COMMENT ON COLUMN payment_methods.version IS 'Optimistic locking version to prevent concurrent updates';