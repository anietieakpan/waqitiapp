-- Add optimistic locking version column to bnpl_transactions table
-- This prevents race conditions during refund calculations and status updates

ALTER TABLE bnpl_transactions 
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Create index for performance
CREATE INDEX idx_bnpl_transactions_version ON bnpl_transactions(version);

-- Update comment for documentation
COMMENT ON COLUMN bnpl_transactions.version IS 'Optimistic locking version to prevent race conditions during BNPL transaction refund calculations and status updates';