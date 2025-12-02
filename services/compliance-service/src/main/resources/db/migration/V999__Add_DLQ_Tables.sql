-- V999__Add_DLQ_Tables.sql
-- DLQ (Dead Letter Queue) infrastructure for compliance-service
-- Part of ADR-011: Consolidate DLQ Service into Common Module

-- ============================================================================
-- DLQ Records Table
-- ============================================================================
-- Stores failed Kafka messages for automatic recovery
-- Integrated with common module DLQProcessorService (runs every 2 minutes)
-- ============================================================================

CREATE TABLE IF NOT EXISTS dlq_records (
    -- Primary identification
    id                      UUID PRIMARY KEY,
    message_id              VARCHAR(255) NOT NULL UNIQUE,

    -- Kafka coordinates
    topic                   VARCHAR(255) NOT NULL,
    partition               INTEGER NOT NULL,
    offset                  BIGINT NOT NULL,
    message_key             TEXT,
    message_value           TEXT NOT NULL,
    headers                 TEXT,

    -- Status and retry management
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count             INTEGER NOT NULL DEFAULT 0,
    max_retries             INTEGER NOT NULL DEFAULT 10,
    next_retry_time         TIMESTAMP NOT NULL,

    -- Failure tracking
    last_failure_time       TIMESTAMP,
    last_failure_reason     TEXT,
    error_message           TEXT,
    stack_trace             TEXT,

    -- Reprocessing tracking
    reprocessed_at          TIMESTAMP,
    reprocessing_result     TEXT,

    -- Parking (permanent skip)
    parked_at               TIMESTAMP,
    parked_reason           TEXT,

    -- Service identification
    service_name            VARCHAR(100) NOT NULL DEFAULT 'compliance-service',

    -- Audit fields
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(100) DEFAULT 'SYSTEM',

    -- Constraints
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'REPROCESSED', 'PARKED', 'FAILED')),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_max_retries CHECK (max_retries > 0),
    CONSTRAINT unique_kafka_message UNIQUE (topic, partition, offset)
);

-- ============================================================================
-- Indexes for Performance
-- ============================================================================

-- Index for DLQProcessorService to find messages ready for retry
CREATE INDEX idx_dlq_pending_retry ON dlq_records(status, next_retry_time)
    WHERE status = 'PENDING';

-- Index for monitoring queries by topic
CREATE INDEX idx_dlq_topic_status ON dlq_records(topic, status, created_at DESC);

-- Index for finding stuck messages
CREATE INDEX idx_dlq_stuck_messages ON dlq_records(status, created_at)
    WHERE status = 'PENDING' AND retry_count >= 5;

-- Index for service-level queries
CREATE INDEX idx_dlq_service_status ON dlq_records(service_name, status, created_at DESC);

-- Index for message ID lookups (unique index already exists, this is for coverage)
CREATE INDEX idx_dlq_message_id ON dlq_records(message_id);

-- Index for reprocessing analysis
CREATE INDEX idx_dlq_reprocessed ON dlq_records(reprocessed_at DESC)
    WHERE status = 'REPROCESSED';

-- Index for parked messages
CREATE INDEX idx_dlq_parked ON dlq_records(parked_at DESC)
    WHERE status = 'PARKED';

-- ============================================================================
-- Triggers
-- ============================================================================

-- Automatically update updated_at timestamp on row modification
CREATE OR REPLACE FUNCTION update_dlq_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dlq_updated_at
    BEFORE UPDATE ON dlq_records
    FOR EACH ROW
    EXECUTE FUNCTION update_dlq_updated_at();

-- ============================================================================
-- Comments (Documentation)
-- ============================================================================

COMMENT ON TABLE dlq_records IS 'Dead Letter Queue records for failed Kafka messages. Automatically processed by DLQProcessorService every 2 minutes.';

COMMENT ON COLUMN dlq_records.id IS 'Primary key (UUID)';
COMMENT ON COLUMN dlq_records.message_id IS 'Unique message identifier from Kafka headers or generated';
COMMENT ON COLUMN dlq_records.topic IS 'Original Kafka topic (without -dlq suffix)';
COMMENT ON COLUMN dlq_records.partition IS 'Kafka partition number';
COMMENT ON COLUMN dlq_records.offset IS 'Kafka offset within partition';
COMMENT ON COLUMN dlq_records.message_key IS 'Kafka message key';
COMMENT ON COLUMN dlq_records.message_value IS 'Kafka message payload (JSON)';
COMMENT ON COLUMN dlq_records.headers IS 'Kafka headers as JSON';
COMMENT ON COLUMN dlq_records.status IS 'Current status: PENDING, REPROCESSED, PARKED, FAILED';
COMMENT ON COLUMN dlq_records.retry_count IS 'Number of retry attempts (0-based)';
COMMENT ON COLUMN dlq_records.max_retries IS 'Maximum retry attempts before escalation (default: 10)';
COMMENT ON COLUMN dlq_records.next_retry_time IS 'Timestamp when message is eligible for next retry';
COMMENT ON COLUMN dlq_records.last_failure_time IS 'Timestamp of most recent failure';
COMMENT ON COLUMN dlq_records.last_failure_reason IS 'Reason for most recent failure';
COMMENT ON COLUMN dlq_records.error_message IS 'Detailed error message from last failure';
COMMENT ON COLUMN dlq_records.stack_trace IS 'Stack trace from last failure (if available)';
COMMENT ON COLUMN dlq_records.reprocessed_at IS 'Timestamp when successfully reprocessed';
COMMENT ON COLUMN dlq_records.reprocessing_result IS 'Result message from successful reprocessing';
COMMENT ON COLUMN dlq_records.parked_at IS 'Timestamp when parked (permanently skipped)';
COMMENT ON COLUMN dlq_records.parked_reason IS 'Reason for parking (validation error, obsolete, etc.)';
COMMENT ON COLUMN dlq_records.service_name IS 'Service that owns this DLQ record (compliance-service)';
COMMENT ON COLUMN dlq_records.created_at IS 'Timestamp when DLQ record created';
COMMENT ON COLUMN dlq_records.updated_at IS 'Timestamp when DLQ record last updated (auto-updated by trigger)';
COMMENT ON COLUMN dlq_records.created_by IS 'User/system that created record (default: SYSTEM)';

-- ============================================================================
-- Usage Notes
-- ============================================================================
--
-- This table is part of the consolidated DLQ infrastructure (ADR-011).
--
-- Automatic Recovery:
-- - DLQProcessorService scans for records where next_retry_time < NOW()
-- - Uses RecoveryStrategyFactory to route to appropriate strategy:
--   * AutomaticRetryStrategy: Transient errors (network, timeout)
--   * SkipStrategy: Validation errors, obsolete events
--   * CompensatingTransactionStrategy: Financial operations (not used in compliance)
--   * ManualInterventionStrategy: Max retries exceeded, security incidents
--
-- Critical Compliance Topics:
-- - sar-filing: Suspicious Activity Reports
-- - ctr-filing: Currency Transaction Reports
-- - ofac-screening: OFAC sanctions screening
-- - aml-alert: Anti-Money Laundering alerts
-- - regulatory-reporting: Regulatory compliance reports
-- - fraud-detection: Fraud detection events
--
-- Monitoring:
-- - Query pending messages: SELECT * FROM dlq_records WHERE status = 'PENDING'
-- - Find stuck messages: SELECT * FROM dlq_records WHERE status = 'PENDING' AND retry_count >= 5
-- - Recovery success rate: SELECT COUNT(*) FILTER (WHERE status = 'REPROCESSED') / COUNT(*)::float FROM dlq_records
--
-- See: docs/operations/DLQ-Operational-Runbook.md
-- ============================================================================
