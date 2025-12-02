-- DLQ Messages Table for Dead Letter Queue Recovery
-- Addresses BLOCKER-003: Unimplemented DLQ recovery logic

CREATE TABLE dlq_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Original message context
    original_topic VARCHAR(255) NOT NULL,
    partition_id INTEGER,
    offset_value BIGINT,
    message_key VARCHAR(500),
    message_payload TEXT NOT NULL,

    -- Retry management
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',

    -- Error tracking
    error_message TEXT,
    error_stacktrace TEXT,

    -- Timing
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    last_processed_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,

    -- Resolution
    resolved_by VARCHAR(255),
    review_notes TEXT,

    -- Priority and alerting
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    alert_sent BOOLEAN NOT NULL DEFAULT FALSE,

    -- Metadata
    metadata TEXT,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_dlq_status CHECK (status IN (
        'PENDING_REVIEW',
        'UNDER_INVESTIGATION',
        'RETRY_SCHEDULED',
        'RETRY_IN_PROGRESS',
        'RECOVERED',
        'PERMANENT_FAILURE',
        'ARCHIVED'
    )),
    CONSTRAINT chk_dlq_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Indexes for efficient querying
CREATE INDEX idx_dlq_status ON dlq_messages(status);
CREATE INDEX idx_dlq_topic ON dlq_messages(original_topic);
CREATE INDEX idx_dlq_received ON dlq_messages(received_at DESC);
CREATE INDEX idx_dlq_next_retry ON dlq_messages(next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_dlq_priority_status ON dlq_messages(priority, status);
CREATE INDEX idx_dlq_alert_sent ON dlq_messages(alert_sent) WHERE alert_sent = FALSE;

-- Index for stale message detection
CREATE INDEX idx_dlq_stale ON dlq_messages(status, received_at)
    WHERE status = 'PENDING_REVIEW';

-- Comments for documentation
COMMENT ON TABLE dlq_messages IS 'Dead Letter Queue messages for failed Kafka events requiring recovery';
COMMENT ON COLUMN dlq_messages.retry_count IS 'Number of automatic retry attempts made';
COMMENT ON COLUMN dlq_messages.status IS 'Current processing status of the DLQ message';
COMMENT ON COLUMN dlq_messages.next_retry_at IS 'Scheduled time for next automatic retry (with exponential backoff)';
COMMENT ON COLUMN dlq_messages.priority IS 'Priority level for manual review and alerting';
COMMENT ON COLUMN dlq_messages.alert_sent IS 'Whether operations team has been alerted about this message';
