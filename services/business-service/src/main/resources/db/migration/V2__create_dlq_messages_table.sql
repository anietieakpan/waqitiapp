-- DLQ Messages Table for Failed Kafka Message Recovery
-- CRITICAL: Financial service - ensures no data loss

CREATE TABLE IF NOT EXISTS dlq_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_name VARCHAR(100) NOT NULL,
    original_topic VARCHAR(200) NOT NULL,
    original_partition INTEGER,
    original_offset BIGINT,
    message_key VARCHAR(500),
    message_payload JSONB NOT NULL,
    headers JSONB,
    error_message TEXT,
    error_stack_trace TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    retry_after TIMESTAMP,
    last_retry_at TIMESTAMP,
    processing_notes TEXT,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_dlq_status CHECK (status IN (
        'PENDING',
        'RETRY_SCHEDULED',
        'RETRYING',
        'RECOVERED',
        'MANUAL_INTERVENTION_REQUIRED',
        'MAX_RETRIES_EXCEEDED',
        'PERMANENT_FAILURE',
        'ARCHIVED'
    )),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_max_retries CHECK (max_retries > 0)
);

-- Indexes for efficient queries
CREATE INDEX idx_dlq_messages_consumer ON dlq_messages(consumer_name);
CREATE INDEX idx_dlq_messages_status ON dlq_messages(status);
CREATE INDEX idx_dlq_messages_created ON dlq_messages(created_at);
CREATE INDEX idx_dlq_messages_retry_after ON dlq_messages(retry_after) WHERE retry_after IS NOT NULL;
CREATE INDEX idx_dlq_messages_topic ON dlq_messages(original_topic);
CREATE INDEX idx_dlq_messages_key ON dlq_messages(message_key) WHERE message_key IS NOT NULL;

-- Composite index for retry eligibility query
CREATE INDEX idx_dlq_retry_eligible ON dlq_messages(status, retry_count, retry_after)
    WHERE status IN ('RETRY_SCHEDULED', 'PENDING');

-- Index for manual intervention queries
CREATE INDEX idx_dlq_manual_intervention ON dlq_messages(status, created_at)
    WHERE status IN ('MANUAL_INTERVENTION_REQUIRED', 'MAX_RETRIES_EXCEEDED', 'PERMANENT_FAILURE');

-- Comment on table
COMMENT ON TABLE dlq_messages IS 'Stores failed Kafka messages for automated retry and manual recovery';
COMMENT ON COLUMN dlq_messages.message_payload IS 'JSON representation of the failed message';
COMMENT ON COLUMN dlq_messages.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN dlq_messages.retry_after IS 'Timestamp when next retry should be attempted (exponential backoff)';
COMMENT ON COLUMN dlq_messages.version IS 'Optimistic locking version for concurrent updates';
