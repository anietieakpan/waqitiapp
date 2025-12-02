-- Event Deduplication Table for Idempotent Event Processing
-- CRITICAL: Prevents duplicate event processing in distributed systems
-- 
-- This table tracks processed Kafka events to ensure idempotency
-- Essential for preventing duplicate wallet transactions and balance updates

CREATE TABLE processed_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_source VARCHAR(100) NOT NULL,
    event_topic VARCHAR(200),
    event_partition INTEGER,
    event_offset BIGINT,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_duration_ms BIGINT,
    related_wallet_id UUID,
    related_transaction_id UUID,
    processing_status VARCHAR(20) NOT NULL CHECK (processing_status IN ('SUCCESS', 'FAILED', 'SKIPPED')) DEFAULT 'SUCCESS',
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for fast duplicate detection
CREATE INDEX idx_processed_events_event_id ON processed_events(event_id);
CREATE INDEX idx_processed_events_event_type ON processed_events(event_type);
CREATE INDEX idx_processed_events_event_source ON processed_events(event_source);
CREATE INDEX idx_processed_events_wallet_id ON processed_events(related_wallet_id);
CREATE INDEX idx_processed_events_transaction_id ON processed_events(related_transaction_id);
CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);
CREATE INDEX idx_processed_events_topic_partition_offset ON processed_events(event_topic, event_partition, event_offset);

-- Composite index for common query pattern
CREATE INDEX idx_processed_events_type_timestamp ON processed_events(event_type, event_timestamp DESC);

-- TTL cleanup function (events older than 90 days)
CREATE OR REPLACE FUNCTION cleanup_old_processed_events()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM processed_events 
    WHERE processed_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Create a scheduled job placeholder (actual scheduling would be done by pg_cron or application scheduler)
COMMENT ON FUNCTION cleanup_old_processed_events() IS 
'Cleanup function for processed_events table. Should be scheduled to run weekly.
DELETE FROM processed_events WHERE processed_at < NOW() - INTERVAL ''90 days'';';

-- Statistics table for event processing metrics
CREATE TABLE event_processing_stats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    stat_date DATE NOT NULL,
    total_events INTEGER NOT NULL DEFAULT 0,
    successful_events INTEGER NOT NULL DEFAULT 0,
    failed_events INTEGER NOT NULL DEFAULT 0,
    skipped_events INTEGER NOT NULL DEFAULT 0,
    duplicate_events INTEGER NOT NULL DEFAULT 0,
    avg_processing_time_ms BIGDECIMAL(10,2),
    max_processing_time_ms BIGINT,
    min_processing_time_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(event_type, stat_date)
);

-- Index for event stats
CREATE INDEX idx_event_stats_type_date ON event_processing_stats(event_type, stat_date DESC);

-- Function to update event processing statistics
CREATE OR REPLACE FUNCTION update_event_processing_stats()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO event_processing_stats (
        event_type,
        stat_date,
        total_events,
        successful_events,
        failed_events,
        skipped_events,
        avg_processing_time_ms,
        max_processing_time_ms,
        min_processing_time_ms
    )
    VALUES (
        NEW.event_type,
        CURRENT_DATE,
        1,
        CASE WHEN NEW.processing_status = 'SUCCESS' THEN 1 ELSE 0 END,
        CASE WHEN NEW.processing_status = 'FAILED' THEN 1 ELSE 0 END,
        CASE WHEN NEW.processing_status = 'SKIPPED' THEN 1 ELSE 0 END,
        NEW.processing_duration_ms,
        NEW.processing_duration_ms,
        NEW.processing_duration_ms
    )
    ON CONFLICT (event_type, stat_date) DO UPDATE SET
        total_events = event_processing_stats.total_events + 1,
        successful_events = event_processing_stats.successful_events + 
            CASE WHEN NEW.processing_status = 'SUCCESS' THEN 1 ELSE 0 END,
        failed_events = event_processing_stats.failed_events + 
            CASE WHEN NEW.processing_status = 'FAILED' THEN 1 ELSE 0 END,
        skipped_events = event_processing_stats.skipped_events + 
            CASE WHEN NEW.processing_status = 'SKIPPED' THEN 1 ELSE 0 END,
        avg_processing_time_ms = (
            (event_processing_stats.avg_processing_time_ms * event_processing_stats.total_events + NEW.processing_duration_ms) /
            (event_processing_stats.total_events + 1)
        ),
        max_processing_time_ms = GREATEST(event_processing_stats.max_processing_time_ms, NEW.processing_duration_ms),
        min_processing_time_ms = LEAST(event_processing_stats.min_processing_time_ms, NEW.processing_duration_ms),
        updated_at = CURRENT_TIMESTAMP;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically update statistics
CREATE TRIGGER trigger_update_event_stats
    AFTER INSERT ON processed_events
    FOR EACH ROW EXECUTE FUNCTION update_event_processing_stats();

-- Add comment explaining the table's critical importance
COMMENT ON TABLE processed_events IS 
'CRITICAL TABLE: Ensures idempotent event processing in wallet service.
Prevents duplicate transactions from being processed when Kafka consumers retry or rebalance.
DO NOT DELETE OR MODIFY WITHOUT ARCHITECTURAL REVIEW.';

COMMENT ON COLUMN processed_events.event_id IS 
'Unique event identifier from the event payload. Used for duplicate detection.';

COMMENT ON COLUMN processed_events.event_offset IS 
'Kafka offset for this event. Useful for debugging and replay scenarios.';

COMMENT ON COLUMN processed_events.processing_duration_ms IS 
'Time taken to process the event in milliseconds. Used for performance monitoring.';

-- Grant appropriate permissions (adjust based on your security model)
-- GRANT SELECT, INSERT ON processed_events TO wallet_service_app;
-- GRANT SELECT ON event_processing_stats TO wallet_service_app;