-- Update processed_events table for distributed idempotency
-- Adds support for Redis-backed idempotency with database fallback

-- Add new columns
ALTER TABLE processed_events
ADD COLUMN IF NOT EXISTS event_key VARCHAR(255);

ALTER TABLE processed_events
ADD COLUMN IF NOT EXISTS operation_id VARCHAR(255);

ALTER TABLE processed_events
ADD COLUMN IF NOT EXISTS processed_at_local TIMESTAMP;

ALTER TABLE processed_events
ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

ALTER TABLE processed_events
ADD COLUMN IF NOT EXISTS result TEXT;

-- Create indexes for new columns
CREATE INDEX IF NOT EXISTS idx_event_key ON processed_events(event_key);
CREATE INDEX IF NOT EXISTS idx_expires_at ON processed_events(expires_at);
CREATE INDEX IF NOT EXISTS idx_operation_id ON processed_events(operation_id);

-- Update existing records with default values
UPDATE processed_events
SET event_key = COALESCE(event_key, event_id)
WHERE event_key IS NULL;

UPDATE processed_events
SET operation_id = COALESCE(operation_id, event_id)
WHERE operation_id IS NULL;

UPDATE processed_events
SET processed_at_local = COALESCE(processed_at_local, CURRENT_TIMESTAMP)
WHERE processed_at_local IS NULL;

UPDATE processed_events
SET expires_at = COALESCE(expires_at, CURRENT_TIMESTAMP + INTERVAL '7 days')
WHERE expires_at IS NULL;

-- Make event_key and operation_id NOT NULL after backfilling
ALTER TABLE processed_events
ALTER COLUMN event_key SET NOT NULL;

ALTER TABLE processed_events
ALTER COLUMN operation_id SET NOT NULL;

-- Add unique constraint on event_key
ALTER TABLE processed_events
ADD CONSTRAINT uk_event_key UNIQUE (event_key);

-- Comments
COMMENT ON COLUMN processed_events.event_key IS 'Unique key for idempotency checks';
COMMENT ON COLUMN processed_events.operation_id IS 'Operation identifier for tracking';
COMMENT ON COLUMN processed_events.expires_at IS 'Expiration time for cleanup (7 days TTL)';
COMMENT ON COLUMN processed_events.result IS 'Serialized processing result for caching';
