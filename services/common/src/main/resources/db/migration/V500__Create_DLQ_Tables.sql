-- DLQ (Dead Letter Queue) Infrastructure Tables
-- Version: 1.0.0
-- Date: 2025-10-09
-- Author: Waqiti Platform Engineering

-- =============================================================================
-- DLQ Messages Table
-- =============================================================================
-- Stores all failed Kafka messages for retry and manual intervention
CREATE TABLE IF NOT EXISTS dlq_messages (
    id VARCHAR(36) PRIMARY KEY,
    original_topic VARCHAR(255) NOT NULL,
    original_partition INTEGER NOT NULL,
    original_offset BIGINT NOT NULL,
    message_key TEXT,
    message_value TEXT NOT NULL,
    headers JSONB,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    failure_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    failure_reason TEXT NOT NULL,
    failure_stack_trace TEXT,
    failure_category VARCHAR(50) NOT NULL,
    exception_class VARCHAR(255) NOT NULL,
    retry_after TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for efficient querying
CREATE INDEX idx_dlq_messages_topic ON dlq_messages(original_topic);
CREATE INDEX idx_dlq_messages_timestamp ON dlq_messages(failure_timestamp DESC);
CREATE INDEX idx_dlq_messages_category ON dlq_messages(failure_category);
CREATE INDEX idx_dlq_messages_attempts ON dlq_messages(attempt_number);
CREATE INDEX idx_dlq_messages_resolved ON dlq_messages(resolved) WHERE resolved = FALSE;
CREATE INDEX idx_dlq_messages_retry ON dlq_messages(retry_after) WHERE retry_after IS NOT NULL AND resolved = FALSE;

-- Composite index for parking lot queries
CREATE INDEX idx_dlq_messages_parking ON dlq_messages(original_topic, attempt_number, failure_timestamp DESC)
    WHERE attempt_number > 3 AND resolved = FALSE;

-- Unique constraint on topic + partition + offset to prevent duplicates
CREATE UNIQUE INDEX idx_dlq_messages_unique_message ON dlq_messages(original_topic, original_partition, original_offset, attempt_number);

-- =============================================================================
-- DLQ Statistics Table
-- =============================================================================
-- Aggregated statistics for monitoring and reporting
CREATE TABLE IF NOT EXISTS dlq_statistics (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    total_failures INTEGER DEFAULT 0,
    transient_failures INTEGER DEFAULT 0,
    database_failures INTEGER DEFAULT 0,
    external_service_failures INTEGER DEFAULT 0,
    poison_pill_failures INTEGER DEFAULT 0,
    business_logic_failures INTEGER DEFAULT 0,
    security_failures INTEGER DEFAULT 0,
    unknown_failures INTEGER DEFAULT 0,
    retry_count INTEGER DEFAULT 0,
    parking_lot_count INTEGER DEFAULT 0,
    resolved_count INTEGER DEFAULT 0,
    avg_retry_attempts DECIMAL(5,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(topic, date)
);

-- Indexes for statistics queries
CREATE INDEX idx_dlq_stats_topic_date ON dlq_statistics(topic, date DESC);
CREATE INDEX idx_dlq_stats_date ON dlq_statistics(date DESC);

-- =============================================================================
-- DLQ Replay Log Table
-- =============================================================================
-- Tracks manual replay operations for audit purposes
CREATE TABLE IF NOT EXISTS dlq_replay_log (
    id BIGSERIAL PRIMARY KEY,
    replay_batch_id VARCHAR(36) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    message_ids TEXT[] NOT NULL,
    initiated_by VARCHAR(100) NOT NULL,
    initiated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, FAILED
    messages_replayed INTEGER DEFAULT 0,
    messages_failed INTEGER DEFAULT 0,
    completed_at TIMESTAMP,
    error_message TEXT,
    notes TEXT
);

-- Indexes for replay log
CREATE INDEX idx_dlq_replay_batch ON dlq_replay_log(replay_batch_id);
CREATE INDEX idx_dlq_replay_topic ON dlq_replay_log(topic);
CREATE INDEX idx_dlq_replay_status ON dlq_replay_log(status) WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_dlq_replay_timestamp ON dlq_replay_log(initiated_at DESC);

-- =============================================================================
-- Triggers
-- =============================================================================

-- Update timestamp on row update
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_dlq_messages_updated_at BEFORE UPDATE ON dlq_messages
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dlq_statistics_updated_at BEFORE UPDATE ON dlq_statistics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- Partitioning (for high-volume systems)
-- =============================================================================

-- Comment: For systems with very high DLQ volumes, consider partitioning by date:
--
-- CREATE TABLE dlq_messages_2025_10 PARTITION OF dlq_messages
--     FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
--
-- CREATE TABLE dlq_messages_2025_11 PARTITION OF dlq_messages
--     FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
--
-- This allows for efficient archival and retention management

-- =============================================================================
-- Maintenance Functions
-- =============================================================================

-- Function to clean up old resolved messages (run monthly)
CREATE OR REPLACE FUNCTION cleanup_old_dlq_messages(retention_days INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM dlq_messages
    WHERE resolved = TRUE
      AND resolved_at < NOW() - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    INSERT INTO audit_log (action, entity_type, details, created_at)
    VALUES (
        'DLQ_CLEANUP',
        'dlq_messages',
        jsonb_build_object('deleted_count', deleted_count, 'retention_days', retention_days),
        NOW()
    );

    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- Initial Data
-- =============================================================================

-- Insert initial statistics row for tracking
-- (This will be updated by the DLQ system)

COMMENT ON TABLE dlq_messages IS 'Stores failed Kafka messages for retry and manual intervention';
COMMENT ON TABLE dlq_statistics IS 'Aggregated DLQ statistics for monitoring and reporting';
COMMENT ON TABLE dlq_replay_log IS 'Audit log for DLQ message replay operations';

COMMENT ON COLUMN dlq_messages.failure_category IS 'TRANSIENT, DATABASE, EXTERNAL_SERVICE, POISON_PILL, BUSINESS_LOGIC, SECURITY, IRRECOVERABLE, UNKNOWN';
COMMENT ON COLUMN dlq_messages.attempt_number IS 'Number of processing attempts (0 = first attempt, 1+ = retries)';
COMMENT ON COLUMN dlq_messages.retry_after IS 'Timestamp after which message can be retried (NULL = immediate retry)';
