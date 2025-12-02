-- Add optimistic locking version column to transaction_history table
-- This prevents race conditions during fraud score updates by ML models

ALTER TABLE transaction_history 
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Create index for performance
CREATE INDEX idx_transaction_history_version ON transaction_history(version);

-- Update comment for documentation
COMMENT ON COLUMN transaction_history.version IS 'Optimistic locking version to prevent race conditions during ML fraud score updates and fraud detection result modifications';