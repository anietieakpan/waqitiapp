-- Migration V5: Dead Letter Queue Recovery System
-- Created: 2025-11-15
-- Description: Create infrastructure for Kafka DLQ message recovery and retry
-- CRITICAL: Prevents message loss and provides audit trail for failed events

-- ============================================================================
-- DLQ MESSAGE TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS dlq_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id VARCHAR(255) UNIQUE NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255),
    message_payload JSONB NOT NULL,
    error_message TEXT NOT NULL,
    error_stack_trace TEXT,
    error_class VARCHAR(500),
    retry_count INTEGER DEFAULT 0 NOT NULL,
    max_retry_attempts INTEGER DEFAULT 5 NOT NULL,
    next_retry_at TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    consumer_group VARCHAR(255) NOT NULL,
    original_offset BIGINT,
    original_partition INTEGER,
    original_timestamp TIMESTAMP NOT NULL,
    first_failure_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0 NOT NULL,

    CONSTRAINT chk_dlq_status CHECK (status IN ('PENDING', 'RETRYING', 'RESOLVED', 'FAILED', 'MANUAL_REVIEW')),
    CONSTRAINT chk_dlq_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_dlq_max_retries CHECK (max_retry_attempts > 0)
);

-- ============================================================================
-- DLQ RETRY HISTORY TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS dlq_retry_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dlq_message_id UUID NOT NULL,
    retry_attempt INTEGER NOT NULL,
    retry_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_status VARCHAR(50) NOT NULL,
    error_message TEXT,
    processing_duration_ms BIGINT,
    metadata JSONB,

    CONSTRAINT fk_dlq_retry_message FOREIGN KEY (dlq_message_id) REFERENCES dlq_message(id) ON DELETE CASCADE,
    CONSTRAINT chk_retry_status CHECK (retry_status IN ('SUCCESS', 'FAILURE', 'TIMEOUT'))
);

-- ============================================================================
-- DLQ ALERT CONFIGURATION TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS dlq_alert_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic VARCHAR(255) NOT NULL,
    threshold_count INTEGER NOT NULL,
    threshold_window_minutes INTEGER NOT NULL,
    alert_channel VARCHAR(100) NOT NULL,
    is_enabled BOOLEAN DEFAULT TRUE,
    last_alert_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_alert_threshold CHECK (threshold_count > 0),
    CONSTRAINT chk_alert_window CHECK (threshold_window_minutes > 0),
    CONSTRAINT unique_topic_alert UNIQUE (topic, alert_channel)
);

-- ============================================================================
-- INDEXES FOR PERFORMANCE
-- ============================================================================

-- Primary query indexes
CREATE INDEX idx_dlq_status ON dlq_message(status) WHERE status IN ('PENDING', 'RETRYING');
CREATE INDEX idx_dlq_next_retry ON dlq_message(next_retry_at) WHERE status = 'PENDING' AND next_retry_at IS NOT NULL;
CREATE INDEX idx_dlq_topic ON dlq_message(topic, status);
CREATE INDEX idx_dlq_consumer_group ON dlq_message(consumer_group, status);
CREATE INDEX idx_dlq_created ON dlq_message(created_at DESC);
CREATE INDEX idx_dlq_retry_count ON dlq_message(retry_count, max_retry_attempts);

-- Monitoring and alerting indexes
CREATE INDEX idx_dlq_first_failure ON dlq_message(first_failure_at DESC);
CREATE INDEX idx_dlq_error_class ON dlq_message(error_class) WHERE status != 'RESOLVED';
CREATE INDEX idx_dlq_manual_review ON dlq_message(status, first_failure_at) WHERE status = 'MANUAL_REVIEW';

-- Retry history indexes
CREATE INDEX idx_dlq_retry_history_message ON dlq_retry_history(dlq_message_id, retry_timestamp DESC);
CREATE INDEX idx_dlq_retry_history_status ON dlq_retry_history(retry_status, retry_timestamp DESC);

-- Alert config index
CREATE INDEX idx_dlq_alert_topic ON dlq_alert_config(topic) WHERE is_enabled = TRUE;

-- GIN index for JSONB queries
CREATE INDEX idx_dlq_payload_gin ON dlq_message USING GIN (message_payload);
CREATE INDEX idx_dlq_metadata_gin ON dlq_message USING GIN (metadata);

-- ============================================================================
-- PARTITION BY STATUS (For better performance with large DLQ datasets)
-- ============================================================================

-- Note: PostgreSQL 10+ supports declarative partitioning
-- Uncomment if using PostgreSQL 10+ and expect high DLQ volumes

-- CREATE TABLE dlq_message_pending PARTITION OF dlq_message FOR VALUES IN ('PENDING');
-- CREATE TABLE dlq_message_retrying PARTITION OF dlq_message FOR VALUES IN ('RETRYING');
-- CREATE TABLE dlq_message_resolved PARTITION OF dlq_message FOR VALUES IN ('RESOLVED');
-- CREATE TABLE dlq_message_failed PARTITION OF dlq_message FOR VALUES IN ('FAILED');
-- CREATE TABLE dlq_message_manual PARTITION OF dlq_message FOR VALUES IN ('MANUAL_REVIEW');

-- ============================================================================
-- INSERT DEFAULT ALERT CONFIGURATIONS
-- ============================================================================

-- Alert when more than 10 messages fail in 15 minutes
INSERT INTO dlq_alert_config (topic, threshold_count, threshold_window_minutes, alert_channel, is_enabled)
VALUES
    ('accounting-events', 10, 15, 'SLACK', TRUE),
    ('payment-events', 5, 10, 'SLACK', TRUE),
    ('settlement-events', 10, 15, 'SLACK', TRUE),
    ('merchant-settlement-events', 10, 15, 'SLACK', TRUE),
    ('loan-events', 5, 10, 'SLACK', TRUE)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- FUNCTIONS FOR DLQ MANAGEMENT
-- ============================================================================

-- Function to update DLQ message timestamp
CREATE OR REPLACE FUNCTION update_dlq_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update updated_at
CREATE TRIGGER dlq_message_updated_at
    BEFORE UPDATE ON dlq_message
    FOR EACH ROW
    EXECUTE FUNCTION update_dlq_updated_at();

CREATE TRIGGER dlq_alert_config_updated_at
    BEFORE UPDATE ON dlq_alert_config
    FOR EACH ROW
    EXECUTE FUNCTION update_dlq_updated_at();

-- ============================================================================
-- VIEWS FOR MONITORING
-- ============================================================================

-- View for DLQ dashboard
CREATE OR REPLACE VIEW dlq_dashboard_summary AS
SELECT
    topic,
    status,
    COUNT(*) as message_count,
    AVG(retry_count) as avg_retry_count,
    MAX(retry_count) as max_retry_count,
    MIN(first_failure_at) as oldest_failure,
    MAX(first_failure_at) as newest_failure
FROM dlq_message
WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY topic, status;

-- View for pending retries
CREATE OR REPLACE VIEW dlq_pending_retries AS
SELECT
    id,
    message_id,
    topic,
    retry_count,
    max_retry_attempts,
    next_retry_at,
    first_failure_at,
    error_message,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - first_failure_at)) as age_seconds
FROM dlq_message
WHERE status = 'PENDING'
  AND next_retry_at <= CURRENT_TIMESTAMP
ORDER BY next_retry_at ASC;

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE dlq_message IS 'Dead Letter Queue messages for failed Kafka events with retry capability';
COMMENT ON TABLE dlq_retry_history IS 'Audit trail of all DLQ retry attempts';
COMMENT ON TABLE dlq_alert_config IS 'Configuration for DLQ alerting thresholds';

COMMENT ON COLUMN dlq_message.message_id IS 'Unique identifier for deduplication across retries';
COMMENT ON COLUMN dlq_message.retry_count IS 'Current number of retry attempts';
COMMENT ON COLUMN dlq_message.max_retry_attempts IS 'Maximum retries before moving to MANUAL_REVIEW';
COMMENT ON COLUMN dlq_message.next_retry_at IS 'Next scheduled retry time (with exponential backoff)';
COMMENT ON COLUMN dlq_message.status IS 'PENDING=awaiting retry, RETRYING=in progress, RESOLVED=fixed, FAILED=exhausted retries, MANUAL_REVIEW=needs human intervention';

-- Migration completed successfully
-- DLQ recovery system is now operational
