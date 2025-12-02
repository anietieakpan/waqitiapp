-- Dead Letter Records Table
-- Stores failed Kafka messages for manual investigation and replay
-- Version: 1.0.0
-- Date: 2025-10-11

-- ========================================
-- DEAD_LETTER_RECORDS TABLE
-- ========================================
CREATE TABLE IF NOT EXISTS dead_letter_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Original message information
    original_topic VARCHAR(255) NOT NULL,
    original_partition INTEGER NOT NULL,
    original_offset BIGINT NOT NULL,
    original_key VARCHAR(255),
    original_value TEXT NOT NULL,
    original_timestamp TIMESTAMP NOT NULL,

    -- Consumer information
    consumer_group VARCHAR(255),

    -- Failure information
    failure_exception VARCHAR(500) NOT NULL,
    failure_message TEXT,
    failure_stack_trace TEXT,
    failure_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count INTEGER NOT NULL,

    -- Investigation tracking
    investigation_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    investigated_by VARCHAR(255),
    investigated_at TIMESTAMP,
    resolution_notes TEXT,

    -- Replay tracking
    replayed BOOLEAN NOT NULL DEFAULT FALSE,
    replayed_at TIMESTAMP,
    replayed_by VARCHAR(255),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Unique constraint to prevent duplicate DLT records
    CONSTRAINT uk_dlt_topic_partition_offset
        UNIQUE (original_topic, original_partition, original_offset)
);

-- ========================================
-- INDEXES FOR PERFORMANCE
-- ========================================

-- Index for finding DLT records by topic
CREATE INDEX IF NOT EXISTS idx_dlt_topic
    ON dead_letter_records(original_topic);

-- Index for finding DLT records by topic, partition, offset (fast lookup)
CREATE INDEX IF NOT EXISTS idx_dlt_topic_partition_offset
    ON dead_letter_records(original_topic, original_partition, original_offset);

-- Index for finding DLT records by failure timestamp (recent failures)
CREATE INDEX IF NOT EXISTS idx_dlt_failure_timestamp
    ON dead_letter_records(failure_timestamp DESC);

-- Index for finding DLT records by investigation status
CREATE INDEX IF NOT EXISTS idx_dlt_investigation_status
    ON dead_letter_records(investigation_status);

-- Index for finding unreplayed DLT records
CREATE INDEX IF NOT EXISTS idx_dlt_replayed
    ON dead_letter_records(replayed, investigation_status);

-- Index for finding DLT records by consumer group
CREATE INDEX IF NOT EXISTS idx_dlt_consumer_group
    ON dead_letter_records(consumer_group);

-- Index for finding DLT records by exception type (for pattern analysis)
CREATE INDEX IF NOT EXISTS idx_dlt_failure_exception
    ON dead_letter_records(failure_exception);

-- ========================================
-- TABLE AND COLUMN COMMENTS
-- ========================================
COMMENT ON TABLE dead_letter_records IS 'Stores failed Kafka messages that could not be processed after all retries. Enables manual investigation and message replay.';

COMMENT ON COLUMN dead_letter_records.id IS 'Primary key (UUID)';
COMMENT ON COLUMN dead_letter_records.original_topic IS 'Original Kafka topic name';
COMMENT ON COLUMN dead_letter_records.original_partition IS 'Original Kafka partition number';
COMMENT ON COLUMN dead_letter_records.original_offset IS 'Original Kafka offset';
COMMENT ON COLUMN dead_letter_records.original_key IS 'Original message key (as string)';
COMMENT ON COLUMN dead_letter_records.original_value IS 'Original message value (JSON serialized)';
COMMENT ON COLUMN dead_letter_records.original_timestamp IS 'Original message timestamp';
COMMENT ON COLUMN dead_letter_records.consumer_group IS 'Kafka consumer group that failed to process';
COMMENT ON COLUMN dead_letter_records.failure_exception IS 'Java exception class name';
COMMENT ON COLUMN dead_letter_records.failure_message IS 'Exception message';
COMMENT ON COLUMN dead_letter_records.failure_stack_trace IS 'Full stack trace';
COMMENT ON COLUMN dead_letter_records.failure_timestamp IS 'When the failure occurred';
COMMENT ON COLUMN dead_letter_records.retry_count IS 'Number of retry attempts before failure';
COMMENT ON COLUMN dead_letter_records.investigation_status IS 'Status: PENDING, IN_PROGRESS, RESOLVED, WONT_FIX, REPLAYED, DUPLICATE';
COMMENT ON COLUMN dead_letter_records.investigated_by IS 'Username of investigator';
COMMENT ON COLUMN dead_letter_records.investigated_at IS 'When investigation was completed';
COMMENT ON COLUMN dead_letter_records.resolution_notes IS 'Notes from investigation';
COMMENT ON COLUMN dead_letter_records.replayed IS 'Whether message was successfully replayed';
COMMENT ON COLUMN dead_letter_records.replayed_at IS 'When message was replayed';
COMMENT ON COLUMN dead_letter_records.replayed_by IS 'Username who replayed the message';
COMMENT ON COLUMN dead_letter_records.version IS 'Optimistic locking version';

-- ========================================
-- TRIGGER FOR updated_at
-- ========================================
CREATE OR REPLACE FUNCTION update_dead_letter_records_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trigger_update_dead_letter_records_updated_at
    BEFORE UPDATE ON dead_letter_records
    FOR EACH ROW
    EXECUTE FUNCTION update_dead_letter_records_updated_at();

-- ========================================
-- CHECK CONSTRAINTS
-- ========================================

-- Ensure investigation_status is valid
ALTER TABLE dead_letter_records
    ADD CONSTRAINT chk_investigation_status
    CHECK (investigation_status IN ('PENDING', 'IN_PROGRESS', 'RESOLVED', 'WONT_FIX', 'REPLAYED', 'DUPLICATE'));

-- Ensure retry_count is non-negative
ALTER TABLE dead_letter_records
    ADD CONSTRAINT chk_retry_count
    CHECK (retry_count >= 0);

-- Ensure if replayed = true, then replayed_at and replayed_by are set
ALTER TABLE dead_letter_records
    ADD CONSTRAINT chk_replay_fields
    CHECK (
        (replayed = FALSE) OR
        (replayed = TRUE AND replayed_at IS NOT NULL AND replayed_by IS NOT NULL)
    );

-- Ensure if investigation_status is RESOLVED, then investigated_by and investigated_at are set
ALTER TABLE dead_letter_records
    ADD CONSTRAINT chk_investigation_fields
    CHECK (
        (investigation_status != 'RESOLVED') OR
        (investigation_status = 'RESOLVED' AND investigated_by IS NOT NULL AND investigated_at IS NOT NULL)
    );
