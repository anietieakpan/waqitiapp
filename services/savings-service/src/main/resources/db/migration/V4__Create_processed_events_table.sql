--
-- Migration V4: Create processed_events table for idempotency tracking
-- Author: Waqiti Development Team
-- Date: 2025-11-19
-- Purpose: Database-backed idempotency to prevent duplicate event processing
--

-- Create processed_events table
CREATE TABLE IF NOT EXISTS processed_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_result VARCHAR(50) DEFAULT 'SUCCESS',
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,

    -- Indexes for performance
    CONSTRAINT uk_processed_events_event_id UNIQUE (event_id)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_processed_events_event_id ON processed_events(event_id);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON processed_events(event_type);
CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(processed_at);
CREATE INDEX IF NOT EXISTS idx_processed_events_result ON processed_events(processing_result);

-- Add comments
COMMENT ON TABLE processed_events IS 'Tracks processed Kafka events for idempotency';
COMMENT ON COLUMN processed_events.event_id IS 'Unique identifier from event payload';
COMMENT ON COLUMN processed_events.event_type IS 'Type of event (e.g., savings.goal.achieved)';
COMMENT ON COLUMN processed_events.processing_result IS 'Result: SUCCESS, FAILURE, PARTIAL';
COMMENT ON COLUMN processed_events.retry_count IS 'Number of retry attempts';

-- Create cleanup function to remove old events (older than 90 days)
CREATE OR REPLACE FUNCTION cleanup_old_processed_events()
RETURNS void AS $$
BEGIN
    DELETE FROM processed_events
    WHERE processed_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
END;
$$ LANGUAGE plpgsql;

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE processed_events TO waqiti_savings;
