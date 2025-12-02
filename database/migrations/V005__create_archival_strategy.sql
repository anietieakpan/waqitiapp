-- =====================================================================
-- Database Archival Strategy and Cleanup Procedures
-- =====================================================================
-- Comprehensive archival solution for financial data with compliance
-- requirements, automated cleanup, and data retention policies.

-- =====================================================================
-- ARCHIVAL INFRASTRUCTURE
-- =====================================================================

-- Create archival schema for segregating archived data
CREATE SCHEMA IF NOT EXISTS archive;

-- Create archival metadata table
CREATE TABLE IF NOT EXISTS archive.archival_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name VARCHAR(100) NOT NULL,
    partition_name VARCHAR(100),
    archive_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archive_method VARCHAR(50) NOT NULL, -- 'PARTITION_DROP', 'COLD_STORAGE', 'EXTERNAL_BACKUP'
    records_archived BIGINT NOT NULL DEFAULT 0,
    data_size_mb DECIMAL(12,2),
    retention_policy VARCHAR(100) NOT NULL,
    retention_expires_at TIMESTAMP NOT NULL,
    compliance_tags TEXT[],
    archive_location TEXT, -- S3 bucket, file path, etc.
    checksum VARCHAR(64),
    archived_by VARCHAR(100) NOT NULL DEFAULT 'system',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_archival_metadata_table_date 
    ON archive.archival_metadata (table_name, archive_date);
CREATE INDEX IF NOT EXISTS idx_archival_metadata_retention 
    ON archive.archival_metadata (retention_expires_at) WHERE retention_expires_at > CURRENT_TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_archival_metadata_compliance 
    ON archive.archival_metadata USING GIN (compliance_tags);

-- Create table for tracking archival jobs
CREATE TABLE IF NOT EXISTS archive.archival_jobs (
    job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(200) NOT NULL,
    job_type VARCHAR(50) NOT NULL, -- 'ARCHIVE', 'CLEANUP', 'RESTORE'
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'
    table_name VARCHAR(100) NOT NULL,
    date_range_start TIMESTAMP,
    date_range_end TIMESTAMP,
    criteria JSONB, -- Additional archival criteria
    records_processed BIGINT DEFAULT 0,
    records_total BIGINT DEFAULT 0,
    progress_percentage DECIMAL(5,2) DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    job_config JSONB,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_archival_jobs_status 
    ON archive.archival_jobs (status, created_at);
CREATE INDEX IF NOT EXISTS idx_archival_jobs_table 
    ON archive.archival_jobs (table_name, job_type);

-- =====================================================================
-- RETENTION POLICY CONFIGURATION
-- =====================================================================

-- Create retention policies table
CREATE TABLE IF NOT EXISTS archive.retention_policies (
    policy_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_name VARCHAR(100) NOT NULL UNIQUE,
    table_pattern VARCHAR(100) NOT NULL, -- Pattern matching table names
    retention_period INTERVAL NOT NULL, -- How long to keep in main tables
    archive_period INTERVAL, -- How long to keep in archive before deletion
    compliance_requirements TEXT[],
    archive_method VARCHAR(50) NOT NULL DEFAULT 'COLD_STORAGE',
    compression_enabled BOOLEAN DEFAULT true,
    encryption_required BOOLEAN DEFAULT true,
    automated_cleanup BOOLEAN DEFAULT true,
    business_justification TEXT NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

-- Insert default retention policies for financial compliance
INSERT INTO archive.retention_policies (
    policy_name, 
    table_pattern, 
    retention_period, 
    archive_period,
    compliance_requirements,
    business_justification,
    created_by
) VALUES 
-- Transactions: 7 years active, 10 years archived (regulatory requirement)
('FINANCIAL_TRANSACTIONS_RETENTION', 
 'transactions%', 
 INTERVAL '7 years', 
 INTERVAL '10 years',
 ARRAY['SOX', 'PCI_DSS', 'GDPR'],
 'Financial transaction records must be retained for regulatory compliance and audit purposes',
 'system'),

-- Audit events: 3 years active, 7 years archived (compliance requirement)
('AUDIT_EVENTS_RETENTION', 
 'audit_events%', 
 INTERVAL '3 years', 
 INTERVAL '7 years',
 ARRAY['SOX', 'GDPR', 'COMPLIANCE'],
 'Audit trail must be maintained for compliance and security investigations',
 'system'),

-- Ledger entries: 10 years active (never archive)
('LEDGER_ENTRIES_RETENTION', 
 'ledger_entries%', 
 INTERVAL '10 years', 
 NULL,
 ARRAY['SOX', 'GAAP', 'IFRS'],
 'Double-entry ledger records must be permanently retained for accounting integrity',
 'system'),

-- User activity logs: 1 year active, 3 years archived
('USER_ACTIVITY_RETENTION', 
 '%activity%', 
 INTERVAL '1 year', 
 INTERVAL '3 years',
 ARRAY['GDPR', 'PRIVACY'],
 'User activity tracking for security and compliance monitoring',
 'system'),

-- Temporary/session data: 90 days active, no archive
('TEMPORARY_DATA_RETENTION', 
 '%session%', 
 INTERVAL '90 days', 
 INTERVAL '0 days',
 ARRAY['PRIVACY'],
 'Temporary data for operational purposes only',
 'system')

ON CONFLICT (policy_name) DO NOTHING;

-- =====================================================================
-- ARCHIVAL FUNCTIONS
-- =====================================================================

-- Function to archive old transaction partitions
CREATE OR REPLACE FUNCTION archive.archive_transaction_partitions(retention_date DATE DEFAULT CURRENT_DATE - INTERVAL '7 years')
RETURNS INTEGER AS $$
DECLARE
    partition_record RECORD;
    archived_count INTEGER := 0;
    archive_job_id UUID;
    total_records BIGINT;
    partition_size_mb DECIMAL(12,2);
BEGIN
    -- Create archival job record
    INSERT INTO archive.archival_jobs (job_name, job_type, table_name, date_range_end, criteria)
    VALUES ('Archive Old Transaction Partitions', 'ARCHIVE', 'transactions_partitioned', retention_date::TIMESTAMP, 
            jsonb_build_object('retention_date', retention_date, 'method', 'partition_archive'))
    RETURNING job_id INTO archive_job_id;
    
    -- Update job status
    UPDATE archive.archival_jobs 
    SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP 
    WHERE job_id = archive_job_id;
    
    -- Find partitions older than retention date
    FOR partition_record IN
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'transactions_y%m%' 
        AND schemaname = 'public'
    LOOP
        DECLARE
            year_part INTEGER;
            month_part INTEGER;
            partition_date DATE;
            archive_table_name TEXT;
        BEGIN
            -- Extract date from partition name
            year_part := SUBSTRING(partition_record.tablename FROM 'transactions_y(\d{4})m')::INTEGER;
            month_part := SUBSTRING(partition_record.tablename FROM 'transactions_y\d{4}m(\d{2})')::INTEGER;
            partition_date := MAKE_DATE(year_part, month_part, 1);
            
            IF partition_date < retention_date THEN
                -- Get partition statistics
                EXECUTE format('SELECT COUNT(*), pg_size_pretty(pg_total_relation_size(%L))::TEXT', 
                              partition_record.tablename) 
                INTO total_records, partition_size_mb;
                
                -- Create archive table name
                archive_table_name := 'archive.' || partition_record.tablename;
                
                -- Move partition to archive schema
                EXECUTE format('ALTER TABLE %I.%I SET SCHEMA archive', 
                              partition_record.schemaname, partition_record.tablename);
                
                -- Record archival metadata
                INSERT INTO archive.archival_metadata (
                    table_name, partition_name, archive_method, records_archived,
                    data_size_mb, retention_policy, retention_expires_at,
                    compliance_tags, notes
                ) VALUES (
                    'transactions_partitioned', partition_record.tablename, 'PARTITION_ARCHIVE',
                    total_records, partition_size_mb::DECIMAL, 'FINANCIAL_TRANSACTIONS_RETENTION',
                    (partition_date + INTERVAL '10 years')::TIMESTAMP,
                    ARRAY['SOX', 'PCI_DSS', 'GDPR'],
                    format('Archived transaction partition for period: %s', partition_date)
                );
                
                archived_count := archived_count + 1;
                
                -- Update job progress
                UPDATE archive.archival_jobs 
                SET records_processed = records_processed + total_records,
                    progress_percentage = LEAST(100, (records_processed + total_records) * 100.0 / NULLIF(records_total, 0))
                WHERE job_id = archive_job_id;
                
                RAISE NOTICE 'Archived partition % with % records', partition_record.tablename, total_records;
            END IF;
            
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Could not process partition %: %', partition_record.tablename, SQLERRM;
            -- Continue with other partitions
        END;
    END LOOP;
    
    -- Complete the job
    UPDATE archive.archival_jobs 
    SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, progress_percentage = 100
    WHERE job_id = archive_job_id;
    
    RAISE NOTICE 'Archived % transaction partitions', archived_count;
    RETURN archived_count;
    
EXCEPTION WHEN OTHERS THEN
    -- Mark job as failed
    UPDATE archive.archival_jobs 
    SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, error_message = SQLERRM
    WHERE job_id = archive_job_id;
    
    RAISE;
END;
$$ LANGUAGE plpgsql;

-- Function to archive old audit events
CREATE OR REPLACE FUNCTION archive.archive_audit_events(retention_date DATE DEFAULT CURRENT_DATE - INTERVAL '3 years')
RETURNS INTEGER AS $$
DECLARE
    partition_record RECORD;
    archived_count INTEGER := 0;
    archive_job_id UUID;
    total_records BIGINT;
BEGIN
    -- Create archival job record
    INSERT INTO archive.archival_jobs (job_name, job_type, table_name, date_range_end)
    VALUES ('Archive Old Audit Events', 'ARCHIVE', 'audit_events_partitioned', retention_date::TIMESTAMP)
    RETURNING job_id INTO archive_job_id;
    
    UPDATE archive.archival_jobs 
    SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP 
    WHERE job_id = archive_job_id;
    
    -- Find audit event partitions older than retention date
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
            
            IF partition_date < retention_date THEN
                -- Get record count
                EXECUTE format('SELECT COUNT(*) FROM %I.%I', 
                              partition_record.schemaname, partition_record.tablename) 
                INTO total_records;
                
                -- Move to archive schema
                EXECUTE format('ALTER TABLE %I.%I SET SCHEMA archive', 
                              partition_record.schemaname, partition_record.tablename);
                
                -- Also move corresponding metadata partition
                EXECUTE format('ALTER TABLE %I SET SCHEMA archive', 
                              'audit_event_metadata_w' || year_part || 'w' || LPAD(week_part::TEXT, 2, '0'));
                
                -- Record archival metadata
                INSERT INTO archive.archival_metadata (
                    table_name, partition_name, archive_method, records_archived,
                    retention_policy, retention_expires_at, compliance_tags
                ) VALUES (
                    'audit_events_partitioned', partition_record.tablename, 'PARTITION_ARCHIVE',
                    total_records, 'AUDIT_EVENTS_RETENTION',
                    (partition_date + INTERVAL '7 years')::TIMESTAMP,
                    ARRAY['SOX', 'GDPR', 'COMPLIANCE']
                );
                
                archived_count := archived_count + 1;
                
                UPDATE archive.archival_jobs 
                SET records_processed = records_processed + total_records
                WHERE job_id = archive_job_id;
                
                RAISE NOTICE 'Archived audit partition % with % records', partition_record.tablename, total_records;
            END IF;
            
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Could not process audit partition %: %', partition_record.tablename, SQLERRM;
        END;
    END LOOP;
    
    UPDATE archive.archival_jobs 
    SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, progress_percentage = 100
    WHERE job_id = archive_job_id;
    
    RETURN archived_count;
    
EXCEPTION WHEN OTHERS THEN
    UPDATE archive.archival_jobs 
    SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, error_message = SQLERRM
    WHERE job_id = archive_job_id;
    
    RAISE;
END;
$$ LANGUAGE plpgsql;

-- Function to clean up expired archived data
CREATE OR REPLACE FUNCTION archive.cleanup_expired_archives()
RETURNS INTEGER AS $$
DECLARE
    cleanup_record RECORD;
    cleaned_count INTEGER := 0;
    cleanup_job_id UUID;
BEGIN
    -- Create cleanup job record
    INSERT INTO archive.archival_jobs (job_name, job_type, table_name)
    VALUES ('Cleanup Expired Archives', 'CLEANUP', 'archive_cleanup')
    RETURNING job_id INTO cleanup_job_id;
    
    UPDATE archive.archival_jobs 
    SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP 
    WHERE job_id = cleanup_job_id;
    
    -- Find expired archived data
    FOR cleanup_record IN
        SELECT am.*, rp.policy_name
        FROM archive.archival_metadata am
        JOIN archive.retention_policies rp ON am.retention_policy = rp.policy_name
        WHERE am.retention_expires_at <= CURRENT_TIMESTAMP
        AND rp.automated_cleanup = true
        ORDER BY am.retention_expires_at
    LOOP
        BEGIN
            -- Drop the archived table/partition
            IF cleanup_record.partition_name IS NOT NULL THEN
                EXECUTE format('DROP TABLE IF EXISTS archive.%I CASCADE', cleanup_record.partition_name);
                RAISE NOTICE 'Dropped expired archive table: archive.%', cleanup_record.partition_name;
            END IF;
            
            -- Update metadata to mark as cleaned up
            UPDATE archive.archival_metadata 
            SET notes = COALESCE(notes, '') || ' [CLEANED UP: ' || CURRENT_TIMESTAMP || ']'
            WHERE id = cleanup_record.id;
            
            cleaned_count := cleaned_count + 1;
            
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Could not cleanup archive %: %', cleanup_record.partition_name, SQLERRM;
        END;
    END LOOP;
    
    UPDATE archive.archival_jobs 
    SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, 
        records_processed = cleaned_count, progress_percentage = 100
    WHERE job_id = cleanup_job_id;
    
    RETURN cleaned_count;
    
EXCEPTION WHEN OTHERS THEN
    UPDATE archive.archival_jobs 
    SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, error_message = SQLERRM
    WHERE job_id = cleanup_job_id;
    
    RAISE;
END;
$$ LANGUAGE plpgsql;

-- Function to restore archived data for compliance requests
CREATE OR REPLACE FUNCTION archive.restore_archived_data(
    table_pattern TEXT,
    restore_date_start DATE,
    restore_date_end DATE,
    restore_schema TEXT DEFAULT 'temp_restore'
)
RETURNS TABLE (
    restored_table TEXT,
    record_count BIGINT,
    restore_location TEXT
) AS $$
DECLARE
    archive_record RECORD;
    restore_job_id UUID;
    total_restored INTEGER := 0;
BEGIN
    -- Create restore job
    INSERT INTO archive.archival_jobs (job_name, job_type, table_name, date_range_start, date_range_end)
    VALUES ('Restore Archived Data', 'RESTORE', table_pattern, 
            restore_date_start::TIMESTAMP, restore_date_end::TIMESTAMP)
    RETURNING job_id INTO restore_job_id;
    
    UPDATE archive.archival_jobs 
    SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP 
    WHERE job_id = restore_job_id;
    
    -- Create restore schema if it doesn't exist
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', restore_schema);
    
    -- Find archived data in the specified date range
    FOR archive_record IN
        SELECT * FROM archive.archival_metadata am
        WHERE am.table_name LIKE table_pattern
        AND am.archive_date BETWEEN restore_date_start AND restore_date_end
        AND am.partition_name IS NOT NULL
        ORDER BY am.archive_date
    LOOP
        DECLARE
            restore_table_name TEXT;
            record_count_result BIGINT;
        BEGIN
            restore_table_name := restore_schema || '.' || archive_record.partition_name;
            
            -- Check if archived table exists
            IF EXISTS (SELECT 1 FROM information_schema.tables 
                      WHERE table_schema = 'archive' 
                      AND table_name = archive_record.partition_name) THEN
                
                -- Copy archived table to restore schema
                EXECUTE format('CREATE TABLE %s AS SELECT * FROM archive.%I', 
                              restore_table_name, archive_record.partition_name);
                
                -- Get record count
                EXECUTE format('SELECT COUNT(*) FROM %s', restore_table_name) 
                INTO record_count_result;
                
                total_restored := total_restored + 1;
                
                -- Return result
                restored_table := restore_table_name;
                record_count := record_count_result;
                restore_location := restore_schema;
                
                RETURN NEXT;
                
                RAISE NOTICE 'Restored % records from % to %', 
                           record_count_result, archive_record.partition_name, restore_table_name;
            ELSE
                RAISE NOTICE 'Archived table % not found', archive_record.partition_name;
            END IF;
            
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Could not restore %: %', archive_record.partition_name, SQLERRM;
        END;
    END LOOP;
    
    UPDATE archive.archival_jobs 
    SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP, 
        records_processed = total_restored, progress_percentage = 100
    WHERE job_id = restore_job_id;
    
EXCEPTION WHEN OTHERS THEN
    UPDATE archive.archival_jobs 
    SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, error_message = SQLERRM
    WHERE job_id = restore_job_id;
    
    RAISE;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- AUTOMATED MAINTENANCE SCHEDULING
-- =====================================================================

-- Function to run automated archival maintenance
CREATE OR REPLACE FUNCTION archive.run_automated_maintenance()
RETURNS TABLE (
    maintenance_task TEXT,
    items_processed INTEGER,
    execution_time_ms BIGINT,
    status TEXT
) AS $$
DECLARE
    start_time TIMESTAMP;
    end_time TIMESTAMP;
    task_result INTEGER;
BEGIN
    -- Archive old transactions
    start_time := CURRENT_TIMESTAMP;
    SELECT archive.archive_transaction_partitions() INTO task_result;
    end_time := CURRENT_TIMESTAMP;
    
    maintenance_task := 'Archive Transaction Partitions';
    items_processed := task_result;
    execution_time_ms := EXTRACT(EPOCH FROM (end_time - start_time)) * 1000;
    status := 'COMPLETED';
    RETURN NEXT;
    
    -- Archive old audit events
    start_time := CURRENT_TIMESTAMP;
    SELECT archive.archive_audit_events() INTO task_result;
    end_time := CURRENT_TIMESTAMP;
    
    maintenance_task := 'Archive Audit Events';
    items_processed := task_result;
    execution_time_ms := EXTRACT(EPOCH FROM (end_time - start_time)) * 1000;
    status := 'COMPLETED';
    RETURN NEXT;
    
    -- Cleanup expired archives
    start_time := CURRENT_TIMESTAMP;
    SELECT archive.cleanup_expired_archives() INTO task_result;
    end_time := CURRENT_TIMESTAMP;
    
    maintenance_task := 'Cleanup Expired Archives';
    items_processed := task_result;
    execution_time_ms := EXTRACT(EPOCH FROM (end_time - start_time)) * 1000;
    status := 'COMPLETED';
    RETURN NEXT;
    
EXCEPTION WHEN OTHERS THEN
    maintenance_task := 'MAINTENANCE ERROR';
    items_processed := 0;
    execution_time_ms := 0;
    status := 'FAILED: ' || SQLERRM;
    RETURN NEXT;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- COMPLIANCE AND REPORTING FUNCTIONS
-- =====================================================================

-- Function to generate archival compliance report
CREATE OR REPLACE FUNCTION archive.generate_compliance_report(
    report_start_date DATE DEFAULT CURRENT_DATE - INTERVAL '1 year',
    report_end_date DATE DEFAULT CURRENT_DATE
)
RETURNS TABLE (
    compliance_requirement TEXT,
    table_name TEXT,
    total_records_archived BIGINT,
    total_size_mb DECIMAL(12,2),
    oldest_archive_date TIMESTAMP,
    newest_archive_date TIMESTAMP,
    retention_status TEXT,
    compliance_status TEXT
) AS $$
BEGIN
    RETURN QUERY
    WITH archive_summary AS (
        SELECT 
            am.table_name,
            UNNEST(am.compliance_tags) as compliance_tag,
            COUNT(*) as archive_count,
            SUM(am.records_archived) as total_records,
            SUM(am.data_size_mb) as total_size,
            MIN(am.archive_date) as first_archive,
            MAX(am.archive_date) as last_archive,
            COUNT(*) FILTER (WHERE am.retention_expires_at > CURRENT_TIMESTAMP) as active_archives,
            COUNT(*) FILTER (WHERE am.retention_expires_at <= CURRENT_TIMESTAMP) as expired_archives
        FROM archive.archival_metadata am
        WHERE am.archive_date BETWEEN report_start_date AND report_end_date
        GROUP BY am.table_name, UNNEST(am.compliance_tags)
    )
    SELECT 
        arch.compliance_tag::TEXT,
        arch.table_name::TEXT,
        arch.total_records,
        arch.total_size,
        arch.first_archive,
        arch.last_archive,
        CASE 
            WHEN arch.active_archives > 0 AND arch.expired_archives = 0 THEN 'COMPLIANT'
            WHEN arch.active_archives > 0 AND arch.expired_archives > 0 THEN 'PARTIALLY_EXPIRED'
            WHEN arch.expired_archives > 0 AND arch.active_archives = 0 THEN 'EXPIRED'
            ELSE 'UNKNOWN'
        END::TEXT as retention_status,
        CASE 
            WHEN arch.total_records > 0 AND arch.first_archive IS NOT NULL THEN 'ARCHIVED'
            ELSE 'PENDING_ARCHIVE'
        END::TEXT as compliance_status
    FROM archive_summary arch
    ORDER BY arch.compliance_tag, arch.table_name;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- PERMISSIONS AND SECURITY
-- =====================================================================

-- Grant appropriate permissions
GRANT USAGE ON SCHEMA archive TO waqiti_user;
GRANT SELECT, INSERT, UPDATE ON archive.archival_metadata TO waqiti_user;
GRANT SELECT, INSERT, UPDATE ON archive.archival_jobs TO waqiti_user;
GRANT SELECT ON archive.retention_policies TO waqiti_user;

-- Grant execution permissions for archival functions
GRANT EXECUTE ON FUNCTION archive.archive_transaction_partitions(DATE) TO waqiti_user;
GRANT EXECUTE ON FUNCTION archive.archive_audit_events(DATE) TO waqiti_user;
GRANT EXECUTE ON FUNCTION archive.cleanup_expired_archives() TO waqiti_user;
GRANT EXECUTE ON FUNCTION archive.restore_archived_data(TEXT, DATE, DATE, TEXT) TO waqiti_user;
GRANT EXECUTE ON FUNCTION archive.run_automated_maintenance() TO waqiti_user;
GRANT EXECUTE ON FUNCTION archive.generate_compliance_report(DATE, DATE) TO waqiti_user;

-- Create audit trail for archival operations
CREATE OR REPLACE FUNCTION archive.audit_archival_operation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO audit_events_partitioned (
            event_type, service_name, action, description, result,
            resource_id, resource_type, severity, compliance_tags
        ) VALUES (
            'ARCHIVAL_OPERATION', 'database-archival', 'ARCHIVE_CREATED',
            'New archival record created for ' || NEW.table_name,
            'SUCCESS', NEW.id::TEXT, 'ARCHIVAL_METADATA', 'MEDIUM',
            NEW.compliance_tags::TEXT
        );
        RETURN NEW;
    END IF;
    
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for archival audit trail
CREATE TRIGGER trg_audit_archival_metadata
    AFTER INSERT ON archive.archival_metadata
    FOR EACH ROW EXECUTE FUNCTION archive.audit_archival_operation();

-- =====================================================================
-- SCHEDULED MAINTENANCE SETUP
-- =====================================================================

-- Create a procedure to set up automated archival maintenance
-- Note: In production, this would be scheduled via cron, pg_cron, or external scheduler

COMMENT ON SCHEMA archive IS 'Schema containing archived financial data and archival management functions';
COMMENT ON TABLE archive.archival_metadata IS 'Metadata tracking for all archived database objects and retention policies';
COMMENT ON TABLE archive.archival_jobs IS 'Job tracking for archival, cleanup, and restoration operations';
COMMENT ON TABLE archive.retention_policies IS 'Configurable retention policies for different data types and compliance requirements';

COMMENT ON FUNCTION archive.archive_transaction_partitions(DATE) IS 'Archives transaction partitions older than specified retention date';
COMMENT ON FUNCTION archive.archive_audit_events(DATE) IS 'Archives audit event partitions older than specified retention date';
COMMENT ON FUNCTION archive.cleanup_expired_archives() IS 'Removes archived data that has exceeded final retention period';
COMMENT ON FUNCTION archive.restore_archived_data(TEXT, DATE, DATE, TEXT) IS 'Restores archived data to temporary schema for compliance or investigation purposes';
COMMENT ON FUNCTION archive.run_automated_maintenance() IS 'Executes complete automated maintenance cycle including archival and cleanup';
COMMENT ON FUNCTION archive.generate_compliance_report(DATE, DATE) IS 'Generates compliance report showing archival status for regulatory requirements';

-- Final status message
SELECT 'Database archival strategy and cleanup procedures created successfully. Use archive.run_automated_maintenance() for scheduled maintenance.' as status;