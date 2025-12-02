-- ============================================================================
-- DLQ (Dead Letter Queue) Tables for Payment Service
-- ============================================================================
-- Author: Waqiti Platform Engineering
-- Version: 2.0.0
-- Date: 2025-11-19
--
-- Purpose: Provides DLQ message tracking and recovery infrastructure for
--          payment-service to handle failed Kafka messages from critical
--          payment topics.
--
-- Compliance: PCI-DSS, SOX, GDPR
-- Retention: 30+ days for audit trail
-- ============================================================================

-- DLQ Records Table
-- Stores all failed Kafka messages for recovery processing
CREATE TABLE IF NOT EXISTS dlq_records (
    id UUID PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    partition INTEGER NOT NULL,
    offset BIGINT NOT NULL,
    message_key VARCHAR(500),
    message_value TEXT NOT NULL,
    event_type VARCHAR(50),
    service_name VARCHAR(100) NOT NULL DEFAULT 'payment-service',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    first_failure_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_failure_time TIMESTAMP,
    last_failure_reason TEXT,
    next_retry_time TIMESTAMP,
    reprocessed_at TIMESTAMP,
    reprocessing_result TEXT,
    parked_at TIMESTAMP,
    parked_reason TEXT,
    headers JSONB,
    error_stack_trace TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_dlq_message_id ON dlq_records(message_id);
CREATE INDEX IF NOT EXISTS idx_dlq_status_event_type ON dlq_records(status, event_type);
CREATE INDEX IF NOT EXISTS idx_dlq_next_retry_time ON dlq_records(next_retry_time);
CREATE INDEX IF NOT EXISTS idx_dlq_created_at ON dlq_records(created_at);
CREATE INDEX IF NOT EXISTS idx_dlq_service_name ON dlq_records(service_name);
CREATE INDEX IF NOT EXISTS idx_dlq_topic_partition_offset ON dlq_records(topic, partition, offset);
CREATE INDEX IF NOT EXISTS idx_dlq_status ON dlq_records(status);

-- Check constraints
ALTER TABLE dlq_records ADD CONSTRAINT chk_retry_count CHECK (retry_count >= 0);
ALTER TABLE dlq_records ADD CONSTRAINT chk_status CHECK (
    status IN ('PENDING', 'REPROCESSED', 'PARKED')
);

-- Update trigger for updated_at
CREATE OR REPLACE FUNCTION update_dlq_records_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_dlq_records_updated_at
    BEFORE UPDATE ON dlq_records
    FOR EACH ROW
    EXECUTE FUNCTION update_dlq_records_updated_at();

-- Comments for documentation
COMMENT ON TABLE dlq_records IS 'Stores failed Kafka messages from payment topics for recovery processing';
COMMENT ON COLUMN dlq_records.message_id IS 'Unique identifier for the message (idempotency key)';
COMMENT ON COLUMN dlq_records.status IS 'Processing status: PENDING (awaiting retry), REPROCESSED (successfully recovered), PARKED (requires manual intervention)';
COMMENT ON COLUMN dlq_records.retry_count IS 'Number of retry attempts made (max 10)';
COMMENT ON COLUMN dlq_records.next_retry_time IS 'Scheduled time for next retry attempt (exponential backoff)';
COMMENT ON COLUMN dlq_records.version IS 'Optimistic locking version for concurrent updates';

-- Grant permissions (adjust as needed for your environment)
-- GRANT SELECT, INSERT, UPDATE ON dlq_records TO payment_service_app;
-- GRANT USAGE ON SEQUENCE dlq_records_id_seq TO payment_service_app;
