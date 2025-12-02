-- Dead Letter Events Table
-- Stores failed Kafka events for investigation, retry, and recovery
-- Version: 1.0
-- Author: Waqiti Platform Engineering Team
-- Date: 2025-10-11

CREATE TABLE IF NOT EXISTS dead_letter_events (
    id BIGSERIAL PRIMARY KEY,

    -- Event identification
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(500) NOT NULL,

    -- Service context
    service_name VARCHAR(100) NOT NULL,
    consumer_class VARCHAR(500),

    -- Kafka metadata
    topic VARCHAR(255) NOT NULL,
    partition INTEGER,
    offset BIGINT,

    -- Event data
    payload TEXT NOT NULL,

    -- Failure tracking
    failure_reason VARCHAR(2000) NOT NULL,
    stack_trace TEXT,

    -- Retry management
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,

    -- Status tracking
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    -- Categorization
    tags VARCHAR(500),

    -- Resolution tracking
    resolution_notes TEXT,
    resolved_by VARCHAR(100),

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP,
    resolved_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    expires_at TIMESTAMP,

    -- Optimistic locking
    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_status CHECK (status IN ('NEW', 'RETRYING', 'RESOLVED', 'FAILED', 'MANUALLY_RESOLVED', 'SKIPPED', 'INVESTIGATING')),
    CONSTRAINT chk_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_max_retries CHECK (max_retries > 0),
    CONSTRAINT chk_version CHECK (version >= 0)
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_dle_event_id ON dead_letter_events(event_id);
CREATE INDEX idx_dle_event_type ON dead_letter_events(event_type);
CREATE INDEX idx_dle_status ON dead_letter_events(status);
CREATE INDEX idx_dle_created_at ON dead_letter_events(created_at DESC);
CREATE INDEX idx_dle_service ON dead_letter_events(service_name);
CREATE INDEX idx_dle_topic ON dead_letter_events(topic);
CREATE INDEX idx_dle_retry ON dead_letter_events(retry_count);
CREATE INDEX idx_dle_severity ON dead_letter_events(severity);
CREATE INDEX idx_dle_next_retry ON dead_letter_events(next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_dle_expires ON dead_letter_events(expires_at) WHERE expires_at IS NOT NULL;

-- Composite indexes for common queries
CREATE INDEX idx_dle_status_severity ON dead_letter_events(status, severity);
CREATE INDEX idx_dle_service_status ON dead_letter_events(service_name, status);
CREATE INDEX idx_dle_created_status ON dead_letter_events(created_at DESC, status);

-- Comments for documentation
COMMENT ON TABLE dead_letter_events IS 'Stores failed Kafka events for investigation, retry, and recovery';
COMMENT ON COLUMN dead_letter_events.event_id IS 'Original event ID from failed event (unique)';
COMMENT ON COLUMN dead_letter_events.payload IS 'Full event payload in JSON format';
COMMENT ON COLUMN dead_letter_events.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN dead_letter_events.next_retry_at IS 'Scheduled time for next retry attempt';
COMMENT ON COLUMN dead_letter_events.expires_at IS 'Event expiration time (auto-cleanup after this)';
COMMENT ON COLUMN dead_letter_events.version IS 'Version for optimistic locking (prevents concurrent modifications)';

-- Grant permissions (adjust based on your database user)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON dead_letter_events TO waqiti_app_user;
-- GRANT USAGE, SELECT ON SEQUENCE dead_letter_events_id_seq TO waqiti_app_user;
