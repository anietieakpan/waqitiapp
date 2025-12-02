-- Production-ready Outbox Event Schema Migration
-- This migration enhances the outbox_events table with:
-- 1. Optimistic locking (@Version field)
-- 2. Proper status enum (replacing boolean)
-- 3. Audit fields for compliance
-- 4. Retry tracking for resilience
-- 5. Performance indexes for polling queries

-- Add version column for optimistic locking (CRITICAL for preventing lost updates)
ALTER TABLE outbox_events
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Replace boolean 'processed' with status enum
ALTER TABLE outbox_events
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Set status based on existing 'processed' flag
UPDATE outbox_events
SET status = CASE WHEN processed = true THEN 'PROCESSED' ELSE 'PENDING' END;

-- Drop old boolean column after migration
ALTER TABLE outbox_events
DROP COLUMN processed;

-- Add audit fields
ALTER TABLE outbox_events
ADD COLUMN created_by VARCHAR(100),
ADD COLUMN processed_at TIMESTAMP,
ADD COLUMN processed_by VARCHAR(100);

-- Set processed_at for already processed events
UPDATE outbox_events
SET processed_at = created_at
WHERE status = 'PROCESSED';

-- Add retry tracking fields
ALTER TABLE outbox_events
ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
ADD COLUMN next_retry_at TIMESTAMP,
ADD COLUMN error_message VARCHAR(1000),
ADD COLUMN error_details TEXT;

-- Add distributed tracing support
ALTER TABLE outbox_events
ADD COLUMN correlation_id VARCHAR(100);

-- Rename columns to snake_case for consistency
ALTER TABLE outbox_events
RENAME COLUMN aggregateType TO aggregate_type;

ALTER TABLE outbox_events
RENAME COLUMN aggregateId TO aggregate_id;

ALTER TABLE outbox_events
RENAME COLUMN eventType TO event_type;

ALTER TABLE outbox_events
RENAME COLUMN createdAt TO created_at;

-- Add NOT NULL constraints to critical fields
ALTER TABLE outbox_events
ALTER COLUMN aggregate_type SET NOT NULL,
ALTER COLUMN aggregate_id SET NOT NULL,
ALTER COLUMN event_type SET NOT NULL,
ALTER COLUMN payload SET NOT NULL,
ALTER TABLE outbox_events
ALTER COLUMN created_at SET NOT NULL;

-- Create performance indexes

-- Primary polling query: SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at LIMIT 100
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);

-- Aggregate lookup: SELECT * FROM outbox_events WHERE aggregate_type = 'Payment' AND aggregate_id = '123'
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- Event type filtering and analytics
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);

-- Failed event queries with retry logic
CREATE INDEX idx_outbox_status_retry ON outbox_events(status, retry_count, next_retry_at);

-- Cleanup query: DELETE FROM outbox_events WHERE status = 'PROCESSED' AND processed_at < NOW() - INTERVAL '30 days'
CREATE INDEX idx_outbox_processed_cleanup ON outbox_events(status, processed_at);

-- Distributed tracing lookup
CREATE INDEX idx_outbox_correlation_id ON outbox_events(correlation_id);

-- Add table comments for documentation
COMMENT ON TABLE outbox_events IS 'Transactional Outbox Pattern for reliable event publishing with exactly-once semantics';
COMMENT ON COLUMN outbox_events.version IS 'Optimistic locking version - prevents lost updates in distributed systems';
COMMENT ON COLUMN outbox_events.status IS 'Event processing status: PENDING, PROCESSING, PROCESSED, FAILED, DEAD_LETTER';
COMMENT ON COLUMN outbox_events.retry_count IS 'Number of failed processing attempts (triggers exponential backoff)';
COMMENT ON COLUMN outbox_events.next_retry_at IS 'Timestamp for next retry attempt (exponential backoff: 2^retry_count minutes)';
COMMENT ON COLUMN outbox_events.correlation_id IS 'Distributed tracing correlation ID for end-to-end request tracking';

-- Create cleanup function for old processed events (retention: 30 days)
CREATE OR REPLACE FUNCTION cleanup_processed_outbox_events()
RETURNS INTEGER AS $$
DECLARE
  deleted_count INTEGER;
BEGIN
  DELETE FROM outbox_events
  WHERE status = 'PROCESSED'
    AND processed_at < NOW() - INTERVAL '30 days';

  GET DIAGNOSTICS deleted_count = ROW_COUNT;

  RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_processed_outbox_events IS 'Cleanup function for processed events older than 30 days. Run daily via scheduled job.';

-- Create function to reset stuck PROCESSING events (prevents orphaned events)
CREATE OR REPLACE FUNCTION reset_stuck_processing_events()
RETURNS INTEGER AS $$
DECLARE
  reset_count INTEGER;
BEGIN
  -- Reset events stuck in PROCESSING for more than 5 minutes
  UPDATE outbox_events
  SET status = 'PENDING',
      processed_by = NULL
  WHERE status = 'PROCESSING'
    AND created_at < NOW() - INTERVAL '5 minutes';

  GET DIAGNOSTICS reset_count = ROW_COUNT;

  RETURN reset_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION reset_stuck_processing_events IS 'Resets events stuck in PROCESSING state for >5 minutes. Run every minute via scheduled job.';

-- Grant necessary permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON outbox_events TO waqiti_app_user;
GRANT EXECUTE ON FUNCTION cleanup_processed_outbox_events() TO waqiti_app_user;
GRANT EXECUTE ON FUNCTION reset_stuck_processing_events() TO waqiti_app_user;
