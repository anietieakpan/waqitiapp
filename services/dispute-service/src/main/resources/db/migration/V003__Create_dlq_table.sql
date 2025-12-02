-- Create Dead Letter Queue table for failed Kafka messages
-- Provides persistent storage and recovery tracking

CREATE TABLE dlq_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) NOT NULL,
    source_topic VARCHAR(255) NOT NULL,
    event_json TEXT NOT NULL,
    error_message TEXT,
    error_stacktrace TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    recovery_strategy VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    alert_sent BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_id VARCHAR(50),
    version BIGINT NOT NULL DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_dlq_status ON dlq_entries(status);
CREATE INDEX idx_dlq_topic ON dlq_entries(source_topic);
CREATE INDEX idx_dlq_created ON dlq_entries(created_at);
CREATE INDEX idx_dlq_event_id ON dlq_entries(event_id);
CREATE INDEX idx_dlq_retry_status ON dlq_entries(status, retry_count);

-- Comments
COMMENT ON TABLE dlq_entries IS 'Dead Letter Queue entries for failed Kafka messages';
COMMENT ON COLUMN dlq_entries.event_id IS 'Original event identifier';
COMMENT ON COLUMN dlq_entries.source_topic IS 'Kafka topic where event originated';
COMMENT ON COLUMN dlq_entries.recovery_strategy IS 'Strategy for recovering from failure';
COMMENT ON COLUMN dlq_entries.ticket_id IS 'Reference to ticketing system for manual intervention';
