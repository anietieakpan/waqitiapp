-- =====================================================================
-- Database Performance Optimization: Audit Events Table Partitioning
-- =====================================================================
-- This migration creates a partitioned audit_events table optimized for
-- high-volume audit logging and compliance requirements.

-- Drop existing constraints that might conflict
ALTER TABLE IF EXISTS audit_events DROP CONSTRAINT IF EXISTS audit_events_pkey CASCADE;

-- Create partitioned audit events table
CREATE TABLE IF NOT EXISTS audit_events_partitioned (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    session_id VARCHAR(255),
    correlation_id VARCHAR(255),
    transaction_id VARCHAR(255),
    resource_id VARCHAR(255),
    resource_type VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    description TEXT,
    result VARCHAR(50) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    before_state TEXT,
    after_state TEXT,
    error_message TEXT,
    duration_ms BIGINT,
    severity VARCHAR(20) NOT NULL,
    compliance_tags VARCHAR(500),
    retention_date TIMESTAMP,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Include timestamp in primary key for partitioning
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Create weekly partitions for the last 12 weeks and next 8 weeks
-- Audit data has high volume, so weekly partitions provide better performance
DO $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
    week_start DATE;
BEGIN
    -- Start from 12 weeks ago (Monday of that week)
    week_start := DATE_TRUNC('week', CURRENT_DATE - INTERVAL '12 weeks');
    
    -- Create partitions for 20 weeks (12 past + 8 future)
    FOR i IN 0..19 LOOP
        start_date := week_start + (i || ' weeks')::INTERVAL;
        end_date := start_date + INTERVAL '1 week';
        partition_name := 'audit_events_w' || TO_CHAR(start_date, 'YYYY') || 'w' || TO_CHAR(start_date, 'WW');
        
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_events_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        
        -- Create optimized indexes for each partition
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (user_id, timestamp)', 
                      partition_name || '_user_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (event_type, timestamp)', 
                      partition_name || '_event_type_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (service_name, timestamp)', 
                      partition_name || '_service_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (result, severity, timestamp)', 
                      partition_name || '_result_severity_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (correlation_id) WHERE correlation_id IS NOT NULL', 
                      partition_name || '_correlation_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (transaction_id) WHERE transaction_id IS NOT NULL', 
                      partition_name || '_transaction_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (resource_id, resource_type) WHERE resource_id IS NOT NULL', 
                      partition_name || '_resource_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I USING GIN (compliance_tags) WHERE compliance_tags IS NOT NULL', 
                      partition_name || '_compliance_gin_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (archived, retention_date)', 
                      partition_name || '_archive_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (ip_address) WHERE ip_address IS NOT NULL', 
                      partition_name || '_ip_idx', partition_name);
    END LOOP;
END $$;

-- Create global indexes on the main table
CREATE INDEX IF NOT EXISTS audit_events_partitioned_timestamp_idx 
    ON audit_events_partitioned (timestamp);
CREATE INDEX IF NOT EXISTS audit_events_partitioned_severity_critical_idx 
    ON audit_events_partitioned (severity, timestamp) WHERE severity = 'CRITICAL';
CREATE INDEX IF NOT EXISTS audit_events_partitioned_error_idx 
    ON audit_events_partitioned (result, timestamp) WHERE result IN ('FAILURE', 'SYSTEM_ERROR', 'UNAUTHORIZED');
CREATE INDEX IF NOT EXISTS audit_events_partitioned_compliance_idx 
    ON audit_events_partitioned USING GIN (compliance_tags) WHERE compliance_tags IS NOT NULL;

-- Create function to automatically create future audit partitions
CREATE OR REPLACE FUNCTION create_audit_partition_if_not_exists(partition_date TIMESTAMP)
RETURNS VOID AS $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
BEGIN
    start_date := DATE_TRUNC('week', partition_date::DATE);
    end_date := start_date + INTERVAL '1 week';
    partition_name := 'audit_events_w' || TO_CHAR(start_date, 'YYYY') || 'w' || TO_CHAR(start_date, 'WW');
    
    -- Check if partition exists
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c 
        JOIN pg_namespace n ON n.oid = c.relnamespace 
        WHERE c.relname = partition_name AND n.nspname = 'public'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I PARTITION OF audit_events_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        
        -- Create optimized indexes for the new partition
        EXECUTE format('CREATE INDEX %I ON %I (user_id, timestamp)', 
                      partition_name || '_user_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (event_type, timestamp)', 
                      partition_name || '_event_type_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (service_name, timestamp)', 
                      partition_name || '_service_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (result, severity, timestamp)', 
                      partition_name || '_result_severity_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (correlation_id) WHERE correlation_id IS NOT NULL', 
                      partition_name || '_correlation_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (transaction_id) WHERE transaction_id IS NOT NULL', 
                      partition_name || '_transaction_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (resource_id, resource_type) WHERE resource_id IS NOT NULL', 
                      partition_name || '_resource_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I USING GIN (compliance_tags) WHERE compliance_tags IS NOT NULL', 
                      partition_name || '_compliance_gin_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (archived, retention_date)', 
                      partition_name || '_archive_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (ip_address) WHERE ip_address IS NOT NULL', 
                      partition_name || '_ip_idx', partition_name);
                      
        RAISE NOTICE 'Created audit partition % for date range % to %', partition_name, start_date, end_date;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create trigger function to automatically create partitions
CREATE OR REPLACE FUNCTION audit_partition_trigger()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM create_audit_partition_if_not_exists(NEW.timestamp);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on the partitioned table
DROP TRIGGER IF EXISTS audit_auto_partition_trigger ON audit_events_partitioned;
CREATE TRIGGER audit_auto_partition_trigger
    BEFORE INSERT ON audit_events_partitioned
    FOR EACH ROW EXECUTE FUNCTION audit_partition_trigger();

-- Create audit event metadata table (for the ElementCollection)
CREATE TABLE IF NOT EXISTS audit_event_metadata_partitioned (
    audit_event_id UUID NOT NULL,
    audit_event_timestamp TIMESTAMP NOT NULL,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value TEXT,
    PRIMARY KEY (audit_event_id, audit_event_timestamp, metadata_key),
    FOREIGN KEY (audit_event_id, audit_event_timestamp) 
        REFERENCES audit_events_partitioned(id, timestamp) ON DELETE CASCADE
) PARTITION BY RANGE (audit_event_timestamp);

-- Create metadata partitions aligned with audit events partitions
DO $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
    week_start DATE;
BEGIN
    week_start := DATE_TRUNC('week', CURRENT_DATE - INTERVAL '12 weeks');
    
    FOR i IN 0..19 LOOP
        start_date := week_start + (i || ' weeks')::INTERVAL;
        end_date := start_date + INTERVAL '1 week';
        partition_name := 'audit_event_metadata_w' || TO_CHAR(start_date, 'YYYY') || 'w' || TO_CHAR(start_date, 'WW');
        
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_event_metadata_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (audit_event_id)', 
                      partition_name || '_event_id_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (metadata_key, metadata_value)', 
                      partition_name || '_key_value_idx', partition_name);
    END LOOP;
END $$;

-- Data migration from existing audit_events table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'audit_events' AND table_schema = 'public') THEN
        -- Migrate data in batches to avoid locking
        INSERT INTO audit_events_partitioned 
        SELECT id, event_type, service_name, timestamp, user_id, session_id, 
               correlation_id, transaction_id, resource_id, resource_type, 
               action, description, result, ip_address::INET, user_agent, 
               before_state, after_state, error_message, duration_ms, 
               severity, compliance_tags, retention_date, archived, 
               COALESCE(timestamp, CURRENT_TIMESTAMP)
        FROM audit_events 
        ON CONFLICT (id, timestamp) DO NOTHING;
        
        -- Migrate metadata if it exists
        IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'audit_event_metadata' AND table_schema = 'public') THEN
            INSERT INTO audit_event_metadata_partitioned 
            SELECT aem.audit_event_id, ae.timestamp, aem.metadata_key, aem.metadata_value
            FROM audit_event_metadata aem
            JOIN audit_events ae ON ae.id = aem.audit_event_id
            ON CONFLICT (audit_event_id, audit_event_timestamp, metadata_key) DO NOTHING;
        END IF;
        
        -- Rename old tables for backup
        ALTER TABLE audit_events RENAME TO audit_events_backup_pre_partition;
        IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'audit_event_metadata' AND table_schema = 'public') THEN
            ALTER TABLE audit_event_metadata RENAME TO audit_event_metadata_backup_pre_partition;
        END IF;
        
        RAISE NOTICE 'Migrated data from audit_events to audit_events_partitioned';
    END IF;
END $$;

-- Create views to maintain backward compatibility
CREATE OR REPLACE VIEW audit_events AS 
SELECT * FROM audit_events_partitioned;

CREATE OR REPLACE VIEW audit_event_metadata AS
SELECT audit_event_id, metadata_key, metadata_value 
FROM audit_event_metadata_partitioned;

-- Function for archiving old audit data
CREATE OR REPLACE FUNCTION archive_old_audit_events(archive_months INTEGER DEFAULT 3)
RETURNS INTEGER AS $$
DECLARE
    archived_count INTEGER := 0;
    cutoff_date TIMESTAMP;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - (archive_months || ' months')::INTERVAL;
    
    -- Mark events as archived but don't delete (compliance requirement)
    UPDATE audit_events_partitioned 
    SET archived = TRUE
    WHERE timestamp < cutoff_date 
    AND archived = FALSE;
    
    GET DIAGNOSTICS archived_count = ROW_COUNT;
    
    RAISE NOTICE 'Archived % audit events older than %', archived_count, cutoff_date;
    RETURN archived_count;
END;
$$ LANGUAGE plpgsql;

-- Function for compliance data purging (only after legal retention period)
CREATE OR REPLACE FUNCTION purge_old_audit_partitions(retention_years INTEGER DEFAULT 7)
RETURNS VOID AS $$
DECLARE
    partition_record RECORD;
    cutoff_date DATE;
BEGIN
    cutoff_date := DATE_TRUNC('week', CURRENT_DATE - (retention_years || ' years')::INTERVAL);
    
    FOR partition_record IN
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'audit_events_w%' 
        AND schemaname = 'public'
    LOOP
        DECLARE
            year_part INTEGER;
            week_part INTEGER;
            partition_date DATE;
        BEGIN
            year_part := SUBSTRING(partition_record.tablename FROM 'audit_events_w(\d{4})w')::INTEGER;
            week_part := SUBSTRING(partition_record.tablename FROM 'audit_events_w\d{4}w(\d{2})')::INTEGER;
            
            -- Calculate the Monday of that week
            partition_date := DATE_TRUNC('year', MAKE_DATE(year_part, 1, 1)) + 
                           ((week_part - 1) * 7 || ' days')::INTERVAL;
            
            IF partition_date < cutoff_date THEN
                -- Also drop corresponding metadata partition
                EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', 
                              'audit_event_metadata_w' || year_part || 'w' || LPAD(week_part::TEXT, 2, '0'));
                EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', partition_record.tablename);
                RAISE NOTICE 'Purged old audit partition: %', partition_record.tablename;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Could not process audit partition: %', partition_record.tablename;
        END;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Grant appropriate permissions
GRANT SELECT, INSERT, UPDATE ON audit_events_partitioned TO waqiti_user;
GRANT SELECT, INSERT, UPDATE ON audit_event_metadata_partitioned TO waqiti_user;
GRANT SELECT ON audit_events TO waqiti_user;
GRANT SELECT ON audit_event_metadata TO waqiti_user;
GRANT EXECUTE ON FUNCTION create_audit_partition_if_not_exists(TIMESTAMP) TO waqiti_user;
GRANT EXECUTE ON FUNCTION archive_old_audit_events(INTEGER) TO waqiti_user;

-- Create comments for documentation
COMMENT ON TABLE audit_events_partitioned IS 'Audit events table partitioned by timestamp for high-volume audit logging with compliance retention';
COMMENT ON TABLE audit_event_metadata_partitioned IS 'Metadata for audit events, partitioned to align with main audit table';
COMMENT ON FUNCTION archive_old_audit_events(INTEGER) IS 'Archives audit events older than specified months while maintaining compliance requirements';
COMMENT ON FUNCTION purge_old_audit_partitions(INTEGER) IS 'Purges audit partitions older than specified years (default 7 years for financial compliance)';