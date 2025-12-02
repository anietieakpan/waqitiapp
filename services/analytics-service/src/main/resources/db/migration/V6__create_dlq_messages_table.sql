-- Dead Letter Queue Messages Table
-- Stores failed Kafka messages for recovery and audit trail

CREATE TABLE dlq_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Kafka Message Information
    original_topic VARCHAR(255) NOT NULL,
    dlq_topic VARCHAR(255) NOT NULL,
    partition_number INTEGER,
    offset_number BIGINT,
    message_key VARCHAR(500),
    message_value TEXT NOT NULL,
    headers JSONB,

    -- Failure Information
    failure_reason TEXT,
    stack_trace TEXT,
    exception_class VARCHAR(500),

    -- Retry Information
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_attempts INTEGER NOT NULL DEFAULT 3,
    last_retry_at TIMESTAMP WITH TIME ZONE,

    -- Status and Processing
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW'
        CHECK (status IN (
            'PENDING_REVIEW',
            'RETRY_IN_PROGRESS',
            'RECOVERED',
            'FAILED',
            'MANUAL_REVIEW_REQUIRED',
            'ARCHIVED'
        )),

    -- Tracking and Alerting
    correlation_id VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    assigned_to VARCHAR(255),
    review_notes TEXT,
    recovery_action TEXT,
    alerted BOOLEAN NOT NULL DEFAULT false,

    -- Timestamps
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Optimistic Locking
    version BIGINT NOT NULL DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_dlq_status ON dlq_messages(status);
CREATE INDEX idx_dlq_topic ON dlq_messages(original_topic);
CREATE INDEX idx_dlq_correlation_id ON dlq_messages(correlation_id);
CREATE INDEX idx_dlq_received_at ON dlq_messages(received_at);
CREATE INDEX idx_dlq_retry_eligible ON dlq_messages(status, retry_count, last_retry_at);

-- Composite index for stale message detection
CREATE INDEX idx_dlq_stale ON dlq_messages(status, received_at)
WHERE status = 'PENDING_REVIEW';

-- Index for failed messages needing alerts
CREATE INDEX idx_dlq_failed_not_alerted ON dlq_messages(status, alerted)
WHERE status = 'FAILED' AND alerted = false;

-- Index for manual review workflow
CREATE INDEX idx_dlq_manual_review ON dlq_messages(assigned_to, status)
WHERE status = 'MANUAL_REVIEW_REQUIRED';

-- Comments for documentation
COMMENT ON TABLE dlq_messages IS 'Stores failed Kafka messages for recovery, retry, and audit trail';
COMMENT ON COLUMN dlq_messages.original_topic IS 'Original Kafka topic where message was consumed from';
COMMENT ON COLUMN dlq_messages.dlq_topic IS 'DLQ topic where failed message was sent';
COMMENT ON COLUMN dlq_messages.message_value IS 'Full JSON payload of the failed message';
COMMENT ON COLUMN dlq_messages.retry_count IS 'Current number of retry attempts';
COMMENT ON COLUMN dlq_messages.max_retry_attempts IS 'Maximum retry attempts before marking as FAILED';
COMMENT ON COLUMN dlq_messages.status IS 'Current processing status of the DLQ message';
COMMENT ON COLUMN dlq_messages.correlation_id IS 'Correlation ID for distributed tracing';
COMMENT ON COLUMN dlq_messages.severity IS 'Severity level for prioritization and alerting';
COMMENT ON COLUMN dlq_messages.alerted IS 'Whether operations team has been alerted about this failure';
