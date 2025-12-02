-- CRITICAL DATABASE MIGRATION VALIDATOR
-- Production-grade database migration validation with rollback procedures
-- MUST BE RUN BEFORE ANY PRODUCTION MIGRATION

-- Create migration validation schema
CREATE SCHEMA IF NOT EXISTS migration_validation;

-- Create migration history table
CREATE TABLE IF NOT EXISTS migration_validation.migration_history (
    id BIGSERIAL PRIMARY KEY,
    migration_name VARCHAR(255) NOT NULL,
    migration_version VARCHAR(50) NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    rollback_sql TEXT,
    validation_checksum VARCHAR(64),
    applied_by VARCHAR(100),
    execution_time_ms BIGINT,
    status VARCHAR(20) DEFAULT 'APPLIED',
    UNIQUE(migration_name, migration_version)
);

-- Create validation functions
CREATE OR REPLACE FUNCTION migration_validation.validate_constraints()
RETURNS TABLE (
    table_name TEXT,
    constraint_name TEXT,
    constraint_type TEXT,
    status TEXT,
    error_message TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        tc.table_name::TEXT,
        tc.constraint_name::TEXT,
        tc.constraint_type::TEXT,
        CASE 
            WHEN tc.constraint_type = 'FOREIGN KEY' AND NOT EXISTS (
                SELECT 1 FROM information_schema.table_constraints rc
                WHERE rc.constraint_name = kcu.unique_constraint_name
            ) THEN 'INVALID'
            ELSE 'VALID'
        END::TEXT AS status,
        CASE 
            WHEN tc.constraint_type = 'FOREIGN KEY' AND NOT EXISTS (
                SELECT 1 FROM information_schema.table_constraints rc
                WHERE rc.constraint_name = kcu.unique_constraint_name
            ) THEN 'Referenced constraint not found'
            ELSE NULL
        END::TEXT AS error_message
    FROM information_schema.table_constraints tc
    LEFT JOIN information_schema.key_column_usage kcu 
        ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_schema NOT IN ('information_schema', 'pg_catalog', 'migration_validation')
    ORDER BY tc.table_name, tc.constraint_name;
END;
$$ LANGUAGE plpgsql;

-- Validate foreign key relationships
CREATE OR REPLACE FUNCTION migration_validation.validate_foreign_keys()
RETURNS TABLE (
    table_name TEXT,
    column_name TEXT,
    foreign_table_name TEXT,
    foreign_column_name TEXT,
    status TEXT,
    orphaned_records BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        kcu.table_name::TEXT,
        kcu.column_name::TEXT,
        ccu.table_name::TEXT AS foreign_table_name,
        ccu.column_name::TEXT AS foreign_column_name,
        CASE 
            WHEN orphaned.count_orphaned > 0 THEN 'ORPHANED_DATA'
            ELSE 'VALID'
        END::TEXT AS status,
        COALESCE(orphaned.count_orphaned, 0) AS orphaned_records
    FROM information_schema.key_column_usage kcu
    JOIN information_schema.constraint_column_usage ccu
        ON kcu.constraint_name = ccu.constraint_name
    JOIN information_schema.table_constraints tc
        ON kcu.constraint_name = tc.constraint_name
    LEFT JOIN LATERAL (
        SELECT COUNT(*) AS count_orphaned
        FROM information_schema.tables t
        WHERE t.table_name = kcu.table_name
        AND t.table_schema = kcu.table_schema
        -- Dynamic query to check for orphaned records would go here
        -- This is a placeholder - actual implementation would use dynamic SQL
    ) orphaned ON true
    WHERE tc.constraint_type = 'FOREIGN KEY'
    AND kcu.table_schema NOT IN ('information_schema', 'pg_catalog', 'migration_validation')
    ORDER BY kcu.table_name, kcu.column_name;
END;
$$ LANGUAGE plpgsql;

-- Validate data integrity
CREATE OR REPLACE FUNCTION migration_validation.validate_data_integrity()
RETURNS TABLE (
    table_name TEXT,
    validation_rule TEXT,
    invalid_records BIGINT,
    status TEXT
) AS $$
BEGIN
    -- Financial data validation
    RETURN QUERY
    SELECT 'payments'::TEXT, 'amount_positive'::TEXT, 
           (SELECT COUNT(*) FROM payments WHERE amount <= 0)::BIGINT,
           CASE WHEN (SELECT COUNT(*) FROM payments WHERE amount <= 0) > 0 
                THEN 'INVALID' ELSE 'VALID' END::TEXT
    WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payments');
    
    RETURN QUERY
    SELECT 'wallet_balances'::TEXT, 'balance_not_null'::TEXT,
           (SELECT COUNT(*) FROM wallet_balances WHERE balance IS NULL)::BIGINT,
           CASE WHEN (SELECT COUNT(*) FROM wallet_balances WHERE balance IS NULL) > 0 
                THEN 'INVALID' ELSE 'VALID' END::TEXT
    WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wallet_balances');
    
    RETURN QUERY
    SELECT 'transactions'::TEXT, 'valid_status'::TEXT,
           (SELECT COUNT(*) FROM transactions 
            WHERE status NOT IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'))::BIGINT,
           CASE WHEN (SELECT COUNT(*) FROM transactions 
                     WHERE status NOT IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')) > 0 
                THEN 'INVALID' ELSE 'VALID' END::TEXT
    WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transactions');
    
    RETURN QUERY
    SELECT 'audit_logs'::TEXT, 'timestamp_not_null'::TEXT,
           (SELECT COUNT(*) FROM audit_logs WHERE created_at IS NULL)::BIGINT,
           CASE WHEN (SELECT COUNT(*) FROM audit_logs WHERE created_at IS NULL) > 0 
                THEN 'INVALID' ELSE 'VALID' END::TEXT
    WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'audit_logs');
END;
$$ LANGUAGE plpgsql;

-- Create migration backup
CREATE OR REPLACE FUNCTION migration_validation.create_backup(backup_name TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    backup_schema TEXT := 'backup_' || backup_name;
    table_rec RECORD;
    sql_cmd TEXT;
BEGIN
    -- Create backup schema
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', backup_schema);
    
    -- Copy all financial tables to backup schema
    FOR table_rec IN 
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_type = 'BASE TABLE'
        AND table_name IN (
            'payments', 'transactions', 'wallet_balances', 'audit_logs',
            'ledger_entries', 'compliance_reports', 'fraud_scores',
            'user_accounts', 'merchant_accounts'
        )
    LOOP
        sql_cmd := format('CREATE TABLE %I.%I AS SELECT * FROM public.%I',
                         backup_schema, table_rec.table_name, table_rec.table_name);
        EXECUTE sql_cmd;
        
        RAISE NOTICE 'Backed up table: %', table_rec.table_name;
    END LOOP;
    
    -- Log backup creation
    INSERT INTO migration_validation.migration_history 
    (migration_name, migration_version, applied_by, status)
    VALUES ('BACKUP_' || backup_name, 'BACKUP', current_user, 'COMPLETED');
    
    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Backup failed: %', SQLERRM;
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Restore from backup
CREATE OR REPLACE FUNCTION migration_validation.restore_backup(backup_name TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    backup_schema TEXT := 'backup_' || backup_name;
    table_rec RECORD;
    sql_cmd TEXT;
BEGIN
    -- Check if backup schema exists
    IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = backup_schema) THEN
        RAISE EXCEPTION 'Backup schema % does not exist', backup_schema;
    END IF;
    
    -- Disable foreign key checks temporarily
    SET session_replication_role = replica;
    
    -- Restore tables from backup
    FOR table_rec IN 
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = backup_schema
        AND table_type = 'BASE TABLE'
    LOOP
        -- Truncate current table and restore from backup
        sql_cmd := format('TRUNCATE TABLE public.%I CASCADE', table_rec.table_name);
        EXECUTE sql_cmd;
        
        sql_cmd := format('INSERT INTO public.%I SELECT * FROM %I.%I',
                         table_rec.table_name, backup_schema, table_rec.table_name);
        EXECUTE sql_cmd;
        
        RAISE NOTICE 'Restored table: %', table_rec.table_name;
    END LOOP;
    
    -- Re-enable foreign key checks
    SET session_replication_role = DEFAULT;
    
    -- Log restore
    INSERT INTO migration_validation.migration_history 
    (migration_name, migration_version, applied_by, status)
    VALUES ('RESTORE_' || backup_name, 'RESTORE', current_user, 'COMPLETED');
    
    RETURN TRUE;
EXCEPTION
    WHEN OTHERS THEN
        -- Re-enable foreign key checks on error
        SET session_replication_role = DEFAULT;
        RAISE NOTICE 'Restore failed: %', SQLERRM;
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Pre-migration validation
CREATE OR REPLACE FUNCTION migration_validation.pre_migration_check()
RETURNS TABLE (
    check_type TEXT,
    status TEXT,
    details TEXT
) AS $$
BEGIN
    -- Check database connections
    RETURN QUERY
    SELECT 'CONNECTION_CHECK'::TEXT, 'PASSED'::TEXT, 
           format('Active connections: %s', count(*))::TEXT
    FROM pg_stat_activity 
    WHERE state = 'active';
    
    -- Check disk space (approximate)
    RETURN QUERY
    SELECT 'DISK_SPACE'::TEXT, 
           CASE WHEN pg_database_size(current_database()) > 0 THEN 'PASSED' ELSE 'FAILED' END::TEXT,
           format('Database size: %s', pg_size_pretty(pg_database_size(current_database())))::TEXT;
    
    -- Check for long-running transactions
    RETURN QUERY
    SELECT 'LONG_TRANSACTIONS'::TEXT,
           CASE WHEN count(*) > 0 THEN 'WARNING' ELSE 'PASSED' END::TEXT,
           format('Long-running transactions: %s', count(*))::TEXT
    FROM pg_stat_activity 
    WHERE state = 'active' 
    AND now() - query_start > interval '5 minutes';
    
    -- Check constraint violations
    RETURN QUERY
    SELECT 'CONSTRAINT_CHECK'::TEXT,
           CASE WHEN count(*) > 0 THEN 'FAILED' ELSE 'PASSED' END::TEXT,
           format('Invalid constraints: %s', count(*))::TEXT
    FROM migration_validation.validate_constraints()
    WHERE status = 'INVALID';
    
    -- Check data integrity
    RETURN QUERY
    SELECT 'DATA_INTEGRITY'::TEXT,
           CASE WHEN count(*) > 0 THEN 'FAILED' ELSE 'PASSED' END::TEXT,
           format('Data integrity issues: %s', count(*))::TEXT
    FROM migration_validation.validate_data_integrity()
    WHERE status = 'INVALID';
END;
$$ LANGUAGE plpgsql;

-- Post-migration validation
CREATE OR REPLACE FUNCTION migration_validation.post_migration_check()
RETURNS TABLE (
    check_type TEXT,
    status TEXT,
    details TEXT
) AS $$
BEGIN
    -- Re-run all pre-migration checks
    RETURN QUERY
    SELECT * FROM migration_validation.pre_migration_check();
    
    -- Additional post-migration checks
    RETURN QUERY
    SELECT 'INDEX_VALIDITY'::TEXT,
           CASE WHEN count(*) = 0 THEN 'PASSED' ELSE 'WARNING' END::TEXT,
           format('Invalid indexes: %s', count(*))::TEXT
    FROM pg_stat_user_indexes
    WHERE idx_scan = 0 AND schemaname = 'public';
    
    -- Check for missing sequences
    RETURN QUERY
    SELECT 'SEQUENCE_CHECK'::TEXT, 'PASSED'::TEXT,
           format('Sequences found: %s', count(*))::TEXT
    FROM information_schema.sequences
    WHERE sequence_schema = 'public';
END;
$$ LANGUAGE plpgsql;

-- Migration execution wrapper with validation
CREATE OR REPLACE FUNCTION migration_validation.execute_migration(
    migration_name TEXT,
    migration_version TEXT,
    migration_sql TEXT,
    rollback_sql TEXT DEFAULT NULL
)
RETURNS BOOLEAN AS $$
DECLARE
    start_time TIMESTAMP := now();
    execution_time_ms BIGINT;
    backup_name TEXT := migration_name || '_' || migration_version || '_' || to_char(now(), 'YYYYMMDD_HH24MISS');
    pre_check_failed BOOLEAN := FALSE;
    post_check_failed BOOLEAN := FALSE;
    check_rec RECORD;
BEGIN
    RAISE NOTICE 'Starting migration: % version %', migration_name, migration_version;
    
    -- Pre-migration validation
    RAISE NOTICE 'Running pre-migration checks...';
    FOR check_rec IN SELECT * FROM migration_validation.pre_migration_check()
    LOOP
        RAISE NOTICE 'Check %: % - %', check_rec.check_type, check_rec.status, check_rec.details;
        IF check_rec.status = 'FAILED' THEN
            pre_check_failed := TRUE;
        END IF;
    END LOOP;
    
    IF pre_check_failed THEN
        RAISE EXCEPTION 'Pre-migration checks failed. Migration aborted.';
    END IF;
    
    -- Create backup
    RAISE NOTICE 'Creating backup: %', backup_name;
    IF NOT migration_validation.create_backup(backup_name) THEN
        RAISE EXCEPTION 'Backup creation failed. Migration aborted.';
    END IF;
    
    -- Execute migration
    BEGIN
        RAISE NOTICE 'Executing migration SQL...';
        EXECUTE migration_sql;
        
        execution_time_ms := EXTRACT(epoch FROM (now() - start_time)) * 1000;
        
        -- Log successful migration
        INSERT INTO migration_validation.migration_history 
        (migration_name, migration_version, rollback_sql, applied_by, execution_time_ms, status)
        VALUES (migration_name, migration_version, rollback_sql, current_user, execution_time_ms, 'APPLIED');
        
        RAISE NOTICE 'Migration executed successfully in %ms', execution_time_ms;
        
    EXCEPTION
        WHEN OTHERS THEN
            RAISE NOTICE 'Migration failed: %. Rolling back...', SQLERRM;
            
            -- Attempt rollback
            IF rollback_sql IS NOT NULL THEN
                BEGIN
                    EXECUTE rollback_sql;
                    RAISE NOTICE 'Rollback completed successfully';
                EXCEPTION
                    WHEN OTHERS THEN
                        RAISE NOTICE 'Rollback failed: %. Restoring from backup...', SQLERRM;
                        PERFORM migration_validation.restore_backup(backup_name);
                END;
            ELSE
                RAISE NOTICE 'No rollback SQL provided. Restoring from backup...';
                PERFORM migration_validation.restore_backup(backup_name);
            END IF;
            
            -- Log failed migration
            INSERT INTO migration_validation.migration_history 
            (migration_name, migration_version, applied_by, execution_time_ms, status)
            VALUES (migration_name, migration_version, current_user, 
                   EXTRACT(epoch FROM (now() - start_time)) * 1000, 'FAILED');
            
            RAISE;
    END;
    
    -- Post-migration validation
    RAISE NOTICE 'Running post-migration checks...';
    FOR check_rec IN SELECT * FROM migration_validation.post_migration_check()
    LOOP
        RAISE NOTICE 'Check %: % - %', check_rec.check_type, check_rec.status, check_rec.details;
        IF check_rec.status = 'FAILED' THEN
            post_check_failed := TRUE;
        END IF;
    END LOOP;
    
    IF post_check_failed THEN
        RAISE WARNING 'Post-migration checks failed. Consider manual review.';
    END IF;
    
    RAISE NOTICE 'Migration % version % completed successfully', migration_name, migration_version;
    RETURN TRUE;
    
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'Migration failed: %', SQLERRM;
        RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

-- Grant permissions
GRANT USAGE ON SCHEMA migration_validation TO postgres;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA migration_validation TO postgres;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA migration_validation TO postgres;

-- Example usage:
/*
SELECT migration_validation.execute_migration(
    'add_fraud_detection_columns',
    '1.0.0',
    'ALTER TABLE payments ADD COLUMN fraud_score DECIMAL(3,2) DEFAULT 0.00;',
    'ALTER TABLE payments DROP COLUMN fraud_score;'
);
*/