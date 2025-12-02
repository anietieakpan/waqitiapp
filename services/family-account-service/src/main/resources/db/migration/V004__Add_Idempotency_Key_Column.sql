-- V004: Add Idempotency Key Support
--
-- Purpose: Add idempotency key column to transaction_attempts table to prevent duplicate transactions
-- Author: Waqiti Family Account Team
-- Date: 2025-11-19
--
-- Idempotency ensures that retrying the same transaction request (due to network issues, timeouts, etc.)
-- will not result in duplicate charges. This is critical for financial operations.
--
-- Implementation:
-- - Add idempotency_key column (nullable for backward compatibility with existing records)
-- - Add unique index to enforce one transaction per idempotency key
-- - Add index for fast lookups during transaction authorization

-- Add idempotency key column
-- NULL allowed for backward compatibility with existing transaction_attempts records
ALTER TABLE transaction_attempts
ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

-- Add unique index to prevent duplicate transactions with same idempotency key
-- This is the core mechanism that prevents duplicate processing
CREATE UNIQUE INDEX IF NOT EXISTS idx_transaction_attempts_idempotency_key
ON transaction_attempts(idempotency_key)
WHERE idempotency_key IS NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN transaction_attempts.idempotency_key IS
'Unique key provided by client to ensure idempotent transaction processing. Prevents duplicate charges on retry.';

-- Validation query (optional - for testing migration)
DO $$
BEGIN
    -- Verify column was added
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'transaction_attempts'
        AND column_name = 'idempotency_key'
    ) THEN
        RAISE EXCEPTION 'Migration failed: idempotency_key column not added';
    END IF;

    -- Verify index was created
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'transaction_attempts'
        AND indexname = 'idx_transaction_attempts_idempotency_key'
    ) THEN
        RAISE EXCEPTION 'Migration failed: idempotency key index not created';
    END IF;

    RAISE NOTICE 'Migration V004 completed successfully';
END $$;

-- Rollback instructions (for reference - Flyway doesn't auto-rollback)
-- To manually rollback this migration:
-- DROP INDEX IF EXISTS idx_transaction_attempts_idempotency_key;
-- ALTER TABLE transaction_attempts DROP COLUMN IF EXISTS idempotency_key;
