-- Enhanced Idempotency Infrastructure
-- Version: 1.0.0
-- Date: 2025-10-09
-- Author: Waqiti Platform Engineering

-- =============================================================================
-- Enhance existing idempotency_records table
-- =============================================================================

-- Add indexes for performance if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_idempotency_key_unique') THEN
        CREATE UNIQUE INDEX idx_idempotency_key_unique ON idempotency_records(idempotency_key);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_idempotency_status') THEN
        CREATE INDEX idx_idempotency_status ON idempotency_records(status);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_idempotency_created_at') THEN
        CREATE INDEX idx_idempotency_created_at ON idempotency_records(created_at DESC);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_idempotency_expires_at') THEN
        CREATE INDEX idx_idempotency_expires_at ON idempotency_records(expires_at)
        WHERE expires_at IS NOT NULL;
    END IF;
END $$;

-- =============================================================================
-- Create Idempotency Statistics Table for Monitoring
-- =============================================================================

CREATE TABLE IF NOT EXISTS idempotency_statistics (
    id BIGSERIAL PRIMARY KEY,
    date DATE NOT NULL,
    total_requests BIGINT DEFAULT 0,
    cache_hits BIGINT DEFAULT 0,
    cache_misses BIGINT DEFAULT 0,
    db_hits BIGINT DEFAULT 0,
    duplicates_prevented BIGINT DEFAULT 0,
    new_operations BIGINT DEFAULT 0,
    failed_operations BIGINT DEFAULT 0,
    avg_execution_time_ms DECIMAL(10,2),
    cache_hit_rate DECIMAL(5,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(date)
);

CREATE INDEX idx_idempotency_stats_date ON idempotency_statistics(date DESC);

-- =============================================================================
-- Create Idempotency Cleanup Log Table
-- =============================================================================

CREATE TABLE IF NOT EXISTS idempotency_cleanup_log (
    id BIGSERIAL PRIMARY KEY,
    cleanup_date TIMESTAMP NOT NULL DEFAULT NOW(),
    records_deleted INTEGER DEFAULT 0,
    records_older_than INTERVAL NOT NULL,
    execution_time_ms INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', -- COMPLETED, FAILED
    error_message TEXT
);

CREATE INDEX idx_idempotency_cleanup_date ON idempotency_cleanup_log(cleanup_date DESC);

-- =============================================================================
-- Partition idempotency_records for high-volume systems (optional)
-- =============================================================================

-- Comment: For systems with very high transaction volumes, consider partitioning:
--
-- ALTER TABLE idempotency_records PARTITION BY RANGE (created_at);
--
-- CREATE TABLE idempotency_records_2025_10 PARTITION OF idempotency_records
--     FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
--
-- This allows efficient archival and better query performance

-- =============================================================================
-- Maintenance Functions
-- =============================================================================

-- Function to aggregate daily statistics
CREATE OR REPLACE FUNCTION aggregate_idempotency_stats(target_date DATE DEFAULT CURRENT_DATE)
RETURNS void AS $$
DECLARE
    total_count BIGINT;
    completed_count BIGINT;
    failed_count BIGINT;
BEGIN
    -- Count records for the day
    SELECT
        COUNT(*),
        COUNT(*) FILTER (WHERE status = 'COMPLETED'),
        COUNT(*) FILTER (WHERE status = 'FAILED')
    INTO
        total_count,
        completed_count,
        failed_count
    FROM idempotency_records
    WHERE DATE(created_at) = target_date;

    -- Insert or update statistics
    INSERT INTO idempotency_statistics (
        date,
        total_requests,
        new_operations,
        failed_operations
    ) VALUES (
        target_date,
        total_count,
        completed_count,
        failed_count
    )
    ON CONFLICT (date) DO UPDATE SET
        total_requests = EXCLUDED.total_requests,
        new_operations = EXCLUDED.new_operations,
        failed_operations = EXCLUDED.failed_operations,
        updated_at = NOW();
END;
$$ LANGUAGE plpgsql;

-- Function to cleanup old idempotency records
CREATE OR REPLACE FUNCTION cleanup_old_idempotency_records(
    retention_days INTEGER DEFAULT 90
)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    start_time TIMESTAMP;
    execution_time INTEGER;
BEGIN
    start_time := NOW();

    -- Delete old completed records
    DELETE FROM idempotency_records
    WHERE status = 'COMPLETED'
      AND created_at < NOW() - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    execution_time := EXTRACT(EPOCH FROM (NOW() - start_time)) * 1000;

    -- Log cleanup operation
    INSERT INTO idempotency_cleanup_log (
        cleanup_date,
        records_deleted,
        records_older_than,
        execution_time_ms,
        status
    ) VALUES (
        NOW(),
        deleted_count,
        (retention_days || ' days')::INTERVAL,
        execution_time,
        'COMPLETED'
    );

    RETURN deleted_count;
EXCEPTION
    WHEN OTHERS THEN
        -- Log failed cleanup
        INSERT INTO idempotency_cleanup_log (
            cleanup_date,
            records_deleted,
            records_older_than,
            execution_time_ms,
            status,
            error_message
        ) VALUES (
            NOW(),
            0,
            (retention_days || ' days')::INTERVAL,
            EXTRACT(EPOCH FROM (NOW() - start_time)) * 1000,
            'FAILED',
            SQLERRM
        );

        RAISE;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- Performance Optimization Views
-- =============================================================================

-- View for monitoring idempotency effectiveness
CREATE OR REPLACE VIEW v_idempotency_effectiveness AS
SELECT
    DATE(created_at) as date,
    COUNT(*) as total_operations,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') as successful,
    COUNT(*) FILTER (WHERE status = 'FAILED') as failed,
    COUNT(*) FILTER (WHERE status = 'IN_PROGRESS') as in_progress,
    ROUND(
        COUNT(*) FILTER (WHERE status = 'COMPLETED')::NUMERIC /
        NULLIF(COUNT(*), 0) * 100,
        2
    ) as success_rate
FROM idempotency_records
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY date DESC;

-- View for identifying stuck operations
CREATE OR REPLACE VIEW v_stuck_idempotency_operations AS
SELECT
    id,
    idempotency_key,
    status,
    created_at,
    EXTRACT(EPOCH FROM (NOW() - created_at)) / 60 as minutes_stuck
FROM idempotency_records
WHERE status = 'IN_PROGRESS'
  AND created_at < NOW() - INTERVAL '10 minutes'
ORDER BY created_at ASC;

-- =============================================================================
-- Automated Cleanup Trigger
-- =============================================================================

-- Trigger to prevent runaway growth
CREATE OR REPLACE FUNCTION prevent_idempotency_table_growth()
RETURNS TRIGGER AS $$
DECLARE
    record_count BIGINT;
    threshold BIGINT := 10000000; -- 10 million records
BEGIN
    SELECT COUNT(*) INTO record_count FROM idempotency_records;

    IF record_count > threshold THEN
        -- Log warning
        RAISE WARNING 'Idempotency table size exceeded threshold: % records', record_count;

        -- Trigger automatic cleanup (async)
        PERFORM cleanup_old_idempotency_records(30); -- Keep only 30 days
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Only create trigger if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_check_idempotency_growth'
    ) THEN
        CREATE TRIGGER trg_check_idempotency_growth
            AFTER INSERT ON idempotency_records
            FOR EACH STATEMENT
            EXECUTE FUNCTION prevent_idempotency_table_growth();
    END IF;
END $$;

-- =============================================================================
-- Comments and Documentation
-- =============================================================================

COMMENT ON TABLE idempotency_statistics IS 'Daily aggregated statistics for idempotency operations monitoring';
COMMENT ON TABLE idempotency_cleanup_log IS 'Audit log for idempotency record cleanup operations';
COMMENT ON VIEW v_idempotency_effectiveness IS 'Monitoring view showing idempotency success rates over time';
COMMENT ON VIEW v_stuck_idempotency_operations IS 'Identifies operations that are stuck in IN_PROGRESS state';

COMMENT ON FUNCTION cleanup_old_idempotency_records(INTEGER) IS 'Cleans up old idempotency records and logs the operation';
COMMENT ON FUNCTION aggregate_idempotency_stats(DATE) IS 'Aggregates daily statistics for monitoring and reporting';

-- =============================================================================
-- Grant Permissions (adjust based on your security model)
-- =============================================================================

-- Grant read access to monitoring views
-- GRANT SELECT ON v_idempotency_effectiveness TO monitoring_role;
-- GRANT SELECT ON v_stuck_idempotency_operations TO monitoring_role;

-- =============================================================================
-- Sample Queries for Monitoring
-- =============================================================================

-- Query to check current statistics:
-- SELECT * FROM idempotency_statistics ORDER BY date DESC LIMIT 30;

-- Query to find stuck operations:
-- SELECT * FROM v_stuck_idempotency_operations;

-- Query to see cleanup history:
-- SELECT * FROM idempotency_cleanup_log ORDER BY cleanup_date DESC LIMIT 10;

-- Query to check effectiveness:
-- SELECT * FROM v_idempotency_effectiveness;
