-- Migration: Create Failed Event Tables
-- Description: Creates tables for tracking failed Kafka events for retry
-- Author: System
-- Date: 2025-10-05

-- Create failed_events table
CREATE TABLE IF NOT EXISTS failed_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    last_retry_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT failed_events_pkey PRIMARY KEY (id)
);

-- Create indexes for efficient querying
CREATE INDEX idx_failed_events_event_id ON failed_events(event_id);
CREATE INDEX idx_failed_events_topic ON failed_events(topic);
CREATE INDEX idx_failed_events_status ON failed_events(status);
CREATE INDEX idx_failed_events_next_retry_at ON failed_events(next_retry_at);
CREATE INDEX idx_failed_events_created_at ON failed_events(created_at);

-- Composite index for retry processing
CREATE INDEX idx_failed_events_retry_processing ON failed_events(status, next_retry_at)
    WHERE status = 'PENDING' AND next_retry_at IS NOT NULL;

-- Add comments
COMMENT ON TABLE failed_events IS 'Stores failed Kafka events for retry and manual intervention';
COMMENT ON COLUMN failed_events.event_id IS 'Original event ID from the failed message';
COMMENT ON COLUMN failed_events.topic IS 'Kafka topic where the event failed';
COMMENT ON COLUMN failed_events.payload IS 'JSON payload of the failed event';
COMMENT ON COLUMN failed_events.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN failed_events.status IS 'Status: PENDING, RETRYING, RESOLVED, FAILED, MANUAL_INTERVENTION_REQUIRED';
COMMENT ON COLUMN failed_events.next_retry_at IS 'Timestamp for next retry attempt';
