-- Add optimistic locking version column to instant_transfers table
-- This prevents race conditions during status transitions and concurrent processing

ALTER TABLE instant_transfers 
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Create index for performance
CREATE INDEX idx_instant_transfers_version ON instant_transfers(version);

-- Update comment for documentation
COMMENT ON COLUMN instant_transfers.version IS 'Optimistic locking version to prevent race conditions during instant transfer status transitions and concurrent processing';