-- ============================================================================
-- PERFORMANCE FIX (P1-008): Implement Table Partitioning for High-Volume Tables
-- Migration: Monthly partitioning for massive tables to improve query performance
--
-- IMPACT: Dramatically improves query performance and maintenance operations
--
-- PROBLEM ANALYSIS:
-- - transactions table: 10M+ rows, queries timing out on date ranges
-- - audit_events table: 100M+ rows, retention queries scanning entire table
-- - notifications table: 50M+ rows, delivery queries causing full table scans
--
-- PARTITIONING STRATEGY:
-- - Range partitioning by created_at (monthly intervals)
-- - Automated partition creation via maintenance function
-- - Automatic partition pruning for date-range queries
-- - Easier maintenance: drop old partitions instead of DELETE queries
--
-- PERFORMANCE GAINS (Expected):
-- - Date-range queries: 60-300s → <5s (60x improvement)
-- - Retention cleanup: 600s → <1s (600x improvement)
-- - Index maintenance: 10x faster (smaller partition indexes)
-- - Vacuum operations: 20x faster (per-partition instead of full table)
--
-- MIGRATION APPROACH:
-- - Create partitioned tables with same schema
-- - Copy data from existing tables (if small enough)
-- - For large tables: create partitions and migrate in batches
-- - Rename old tables to _old, rename partitioned tables to original names
--
-- ============================================================================

-- Set statement timeout for this migration
SET statement_timeout = '30min';

-- ============================================================================
-- UTILITY FUNCTION: Create Monthly Partitions
-- ============================================================================

CREATE OR REPLACE FUNCTION create_monthly_partition(
    parent_table TEXT,
    partition_date DATE
) RETURNS TEXT AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    -- Calculate partition boundaries (first day of month to first day of next month)
    start_date := date_trunc('month', partition_date)::DATE;
    end_date := (date_trunc('month', partition_date) + INTERVAL '1 month')::DATE;

    -- Generate partition name (e.g., transactions_2025_01)
    partition_name := parent_table || '_' || to_char(partition_date, 'YYYY_MM');

    -- Check if partition already exists
    IF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = partition_name
        AND n.nspname = 'public'
    ) THEN
        RAISE NOTICE 'Partition % already exists, skipping', partition_name;
        RETURN partition_name;
    END IF;

    -- Create partition
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I
         FOR VALUES FROM (%L) TO (%L)',
        partition_name,
        parent_table,
        start_date,
        end_date
    );

    RAISE NOTICE '✓ Created partition: % for range [%, %)',
        partition_name, start_date, end_date;

    RETURN partition_name;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION create_monthly_partition IS
'Create a monthly partition for a partitioned table with automatic boundary calculation';

-- ============================================================================
-- UTILITY FUNCTION: Auto-create Future Partitions
-- ============================================================================

CREATE OR REPLACE FUNCTION ensure_future_partitions(
    parent_table TEXT,
    months_ahead INTEGER DEFAULT 6
) RETURNS INTEGER AS $$
DECLARE
    partition_date DATE;
    created_count INTEGER := 0;
    i INTEGER;
BEGIN
    -- Create partitions for current month + N months ahead
    FOR i IN 0..months_ahead LOOP
        partition_date := (date_trunc('month', CURRENT_DATE) + (i || ' months')::INTERVAL)::DATE;

        PERFORM create_monthly_partition(parent_table, partition_date);
        created_count := created_count + 1;
    END LOOP;

    RAISE NOTICE 'Ensured % partitions exist for % (current + % months ahead)',
        created_count, parent_table, months_ahead;

    RETURN created_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION ensure_future_partitions IS
'Ensure partitions exist for current month and N months into the future';

-- ============================================================================
-- PARTITION #1: transactions table
-- ============================================================================

DO $$
DECLARE
    table_exists BOOLEAN;
    row_count BIGINT;
    is_already_partitioned BOOLEAN;
BEGIN
    -- Check if transactions table exists
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'transactions'
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Table transactions does not exist, skipping partitioning';
        RETURN;
    END IF;

    -- Check if already partitioned
    SELECT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'transactions'
        AND c.relkind = 'p'  -- 'p' = partitioned table
    ) INTO is_already_partitioned;

    IF is_already_partitioned THEN
        RAISE NOTICE 'Table transactions is already partitioned, skipping';
        RETURN;
    END IF;

    -- Get current row count
    SELECT COUNT(*) INTO row_count FROM transactions;
    RAISE NOTICE 'Partitioning transactions table with % rows', row_count;

    -- Step 1: Rename existing table
    ALTER TABLE transactions RENAME TO transactions_old;
    RAISE NOTICE '✓ Renamed transactions to transactions_old';

    -- Step 2: Create partitioned table with same structure
    CREATE TABLE transactions (
        id UUID NOT NULL,
        user_id UUID NOT NULL,
        wallet_id UUID,
        transaction_type VARCHAR(50) NOT NULL,
        amount DECIMAL(19,4) NOT NULL,
        currency VARCHAR(3) NOT NULL,
        status VARCHAR(50) NOT NULL,
        description TEXT,
        metadata JSONB,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
        updated_at TIMESTAMP WITH TIME ZONE,
        completed_at TIMESTAMP WITH TIME ZONE,
        reference_id VARCHAR(255),
        external_reference VARCHAR(255),
        fee_amount DECIMAL(19,4),
        tax_amount DECIMAL(19,4),
        balance_before DECIMAL(19,4),
        balance_after DECIMAL(19,4),

        PRIMARY KEY (id, created_at)  -- Include partition key in PK
    ) PARTITION BY RANGE (created_at);

    RAISE NOTICE '✓ Created partitioned transactions table';

    -- Step 3: Create indexes on partitioned table (will be inherited by partitions)
    CREATE INDEX idx_transactions_user_date ON transactions(user_id, created_at DESC);
    CREATE INDEX idx_transactions_wallet ON transactions(wallet_id, created_at DESC);
    CREATE INDEX idx_transactions_status ON transactions(status, created_at DESC);
    CREATE INDEX idx_transactions_reference ON transactions(reference_id) WHERE reference_id IS NOT NULL;
    CREATE INDEX idx_transactions_type ON transactions(transaction_type, created_at DESC);

    RAISE NOTICE '✓ Created indexes on partitioned table';

    -- Step 4: Create partitions for last 24 months + next 6 months
    PERFORM ensure_future_partitions('transactions', 6);

    -- Create partitions for last 24 months (historical data)
    FOR i IN 1..24 LOOP
        PERFORM create_monthly_partition(
            'transactions',
            (date_trunc('month', CURRENT_DATE) - (i || ' months')::INTERVAL)::DATE
        );
    END LOOP;

    RAISE NOTICE '✓ Created historical partitions for last 24 months';

    -- Step 5: Migrate data if table is small enough (<1M rows)
    IF row_count < 1000000 THEN
        INSERT INTO transactions SELECT * FROM transactions_old;
        RAISE NOTICE '✓ Migrated % rows from transactions_old to partitioned table', row_count;

        -- Drop old table
        DROP TABLE transactions_old;
        RAISE NOTICE '✓ Dropped transactions_old table';
    ELSE
        RAISE NOTICE 'WARNING: transactions_old has % rows (>1M). Manual migration required.', row_count;
        RAISE NOTICE 'Run: INSERT INTO transactions SELECT * FROM transactions_old; -- in batches';
        RAISE NOTICE 'Then: DROP TABLE transactions_old;';
    END IF;

END $$;

-- ============================================================================
-- PARTITION #2: audit_events table
-- ============================================================================

DO $$
DECLARE
    table_exists BOOLEAN;
    row_count BIGINT;
    is_already_partitioned BOOLEAN;
BEGIN
    -- Check if audit_events table exists
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'audit_events'
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Table audit_events does not exist, skipping partitioning';
        RETURN;
    END IF;

    -- Check if already partitioned
    SELECT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'audit_events'
        AND c.relkind = 'p'
    ) INTO is_already_partitioned;

    IF is_already_partitioned THEN
        RAISE NOTICE 'Table audit_events is already partitioned, skipping';
        RETURN;
    END IF;

    -- Get current row count
    SELECT COUNT(*) INTO row_count FROM audit_events;
    RAISE NOTICE 'Partitioning audit_events table with % rows', row_count;

    -- Step 1: Rename existing table
    ALTER TABLE audit_events RENAME TO audit_events_old;
    RAISE NOTICE '✓ Renamed audit_events to audit_events_old';

    -- Step 2: Create partitioned table
    CREATE TABLE audit_events (
        id UUID NOT NULL,
        event_type VARCHAR(100) NOT NULL,
        table_name VARCHAR(100) NOT NULL,
        operation VARCHAR(10) NOT NULL,
        entity_type VARCHAR(100),
        entity_id UUID,
        old_data JSONB,
        new_data JSONB,
        changed_fields TEXT[],
        user_id UUID,
        session_id VARCHAR(255),
        ip_address INET,
        user_agent TEXT,
        service_name VARCHAR(100),
        hostname VARCHAR(255),
        transaction_id UUID,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
        event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
        sensitivity_level VARCHAR(20) DEFAULT 'MEDIUM',
        retention_period_days INTEGER DEFAULT 2555,
        compliance_tags TEXT[],
        search_vector TSVECTOR,

        PRIMARY KEY (id, created_at)
    ) PARTITION BY RANGE (created_at);

    RAISE NOTICE '✓ Created partitioned audit_events table';

    -- Step 3: Create indexes
    CREATE INDEX idx_audit_events_table_entity ON audit_events(table_name, entity_id, created_at);
    CREATE INDEX idx_audit_events_user ON audit_events(user_id, created_at DESC);
    CREATE INDEX idx_audit_events_timestamp ON audit_events(created_at DESC);
    CREATE INDEX idx_audit_events_event_type ON audit_events(event_type, created_at DESC);
    CREATE INDEX idx_audit_events_sensitivity ON audit_events(sensitivity_level, created_at);
    CREATE INDEX idx_audit_events_compliance ON audit_events USING GIN(compliance_tags);
    CREATE INDEX idx_audit_events_search ON audit_events USING GIN(search_vector);

    RAISE NOTICE '✓ Created indexes on partitioned table';

    -- Step 4: Create partitions (last 84 months = 7 years for compliance)
    PERFORM ensure_future_partitions('audit_events', 6);

    FOR i IN 1..84 LOOP
        PERFORM create_monthly_partition(
            'audit_events',
            (date_trunc('month', CURRENT_DATE) - (i || ' months')::INTERVAL)::DATE
        );
    END LOOP;

    RAISE NOTICE '✓ Created historical partitions for last 7 years (compliance retention)';

    -- Step 5: Migrate data if small enough
    IF row_count < 1000000 THEN
        INSERT INTO audit_events SELECT * FROM audit_events_old;
        RAISE NOTICE '✓ Migrated % rows from audit_events_old', row_count;

        DROP TABLE audit_events_old;
        RAISE NOTICE '✓ Dropped audit_events_old table';
    ELSE
        RAISE NOTICE 'WARNING: audit_events_old has % rows. Manual migration required.', row_count;
        RAISE NOTICE 'Migrate in batches: INSERT INTO audit_events SELECT * FROM audit_events_old WHERE created_at >= ... AND created_at < ...';
    END IF;

END $$;

-- ============================================================================
-- PARTITION #3: notifications table
-- ============================================================================

DO $$
DECLARE
    table_exists BOOLEAN;
    row_count BIGINT;
    is_already_partitioned BOOLEAN;
BEGIN
    -- Check if notifications table exists
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'notifications'
    ) INTO table_exists;

    IF NOT table_exists THEN
        RAISE NOTICE 'Table notifications does not exist, skipping partitioning';
        RETURN;
    END IF;

    -- Check if already partitioned
    SELECT EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'notifications'
        AND c.relkind = 'p'
    ) INTO is_already_partitioned;

    IF is_already_partitioned THEN
        RAISE NOTICE 'Table notifications is already partitioned, skipping';
        RETURN;
    END IF;

    -- Get current row count
    SELECT COUNT(*) INTO row_count FROM notifications;
    RAISE NOTICE 'Partitioning notifications table with % rows', row_count;

    -- Step 1: Rename existing table
    ALTER TABLE notifications RENAME TO notifications_old;
    RAISE NOTICE '✓ Renamed notifications to notifications_old';

    -- Step 2: Create partitioned table
    CREATE TABLE notifications (
        id UUID NOT NULL,
        user_id UUID NOT NULL,
        type VARCHAR(100) NOT NULL,
        channel VARCHAR(50) NOT NULL,
        status VARCHAR(50) NOT NULL,
        subject VARCHAR(500),
        message TEXT NOT NULL,
        metadata JSONB,
        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
        sent_at TIMESTAMP WITH TIME ZONE,
        delivered_at TIMESTAMP WITH TIME ZONE,
        failed_at TIMESTAMP WITH TIME ZONE,
        retry_count INTEGER DEFAULT 0,
        error_message TEXT,
        template_id VARCHAR(255),
        priority VARCHAR(20) DEFAULT 'NORMAL',

        PRIMARY KEY (id, created_at)
    ) PARTITION BY RANGE (created_at);

    RAISE NOTICE '✓ Created partitioned notifications table';

    -- Step 3: Create indexes
    CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
    CREATE INDEX idx_notifications_status ON notifications(status, created_at);
    CREATE INDEX idx_notifications_pending ON notifications(status, created_at)
        WHERE status IN ('PENDING', 'RETRY');
    CREATE INDEX idx_notifications_failed ON notifications(status, retry_count, created_at DESC)
        WHERE status = 'FAILED';
    CREATE INDEX idx_notifications_type ON notifications(type, created_at DESC);

    RAISE NOTICE '✓ Created indexes on partitioned table';

    -- Step 4: Create partitions (last 12 months + next 6 months)
    PERFORM ensure_future_partitions('notifications', 6);

    FOR i IN 1..12 LOOP
        PERFORM create_monthly_partition(
            'notifications',
            (date_trunc('month', CURRENT_DATE) - (i || ' months')::INTERVAL)::DATE
        );
    END LOOP;

    RAISE NOTICE '✓ Created historical partitions for last 12 months';

    -- Step 5: Migrate data if small enough
    IF row_count < 1000000 THEN
        INSERT INTO notifications SELECT * FROM notifications_old;
        RAISE NOTICE '✓ Migrated % rows from notifications_old', row_count;

        DROP TABLE notifications_old;
        RAISE NOTICE '✓ Dropped notifications_old table';
    ELSE
        RAISE NOTICE 'WARNING: notifications_old has % rows. Manual migration required.', row_count;
        RAISE NOTICE 'Migrate in batches by month';
    END IF;

END $$;

-- ============================================================================
-- AUTOMATED PARTITION MANAGEMENT: Monthly Maintenance Function
-- ============================================================================

CREATE OR REPLACE FUNCTION maintain_partitions()
RETURNS TABLE(
    table_name TEXT,
    action TEXT,
    partition_name TEXT,
    result TEXT
) AS $$
DECLARE
    partitioned_tables TEXT[] := ARRAY['transactions', 'audit_events', 'notifications'];
    tbl TEXT;
    created_count INTEGER;
BEGIN
    -- Ensure future partitions exist for all partitioned tables
    FOREACH tbl IN ARRAY partitioned_tables LOOP
        BEGIN
            created_count := ensure_future_partitions(tbl, 6);

            RETURN QUERY SELECT
                tbl::TEXT,
                'ENSURE_FUTURE'::TEXT,
                NULL::TEXT,
                format('Ensured %s partitions exist', created_count)::TEXT;

        EXCEPTION WHEN OTHERS THEN
            RETURN QUERY SELECT
                tbl::TEXT,
                'ERROR'::TEXT,
                NULL::TEXT,
                SQLERRM::TEXT;
        END;
    END LOOP;

    -- Drop old partitions (transactions: >2 years, audit_events: >7 years, notifications: >1 year)
    -- This is safer than DELETE and instant
    FOR tbl IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        AND (
            -- Transactions partitions older than 2 years
            (tablename LIKE 'transactions_%'
             AND to_date(substring(tablename from 14), 'YYYY_MM') < CURRENT_DATE - INTERVAL '2 years')
            OR
            -- Audit partitions older than 7 years (compliance retention)
            (tablename LIKE 'audit_events_%'
             AND to_date(substring(tablename from 14), 'YYYY_MM') < CURRENT_DATE - INTERVAL '7 years')
            OR
            -- Notification partitions older than 1 year
            (tablename LIKE 'notifications_%'
             AND to_date(substring(tablename from 15), 'YYYY_MM') < CURRENT_DATE - INTERVAL '1 year')
        )
    LOOP
        BEGIN
            EXECUTE format('DROP TABLE IF EXISTS %I', tbl);

            RETURN QUERY SELECT
                SPLIT_PART(tbl, '_', 1)::TEXT,
                'DROP_OLD'::TEXT,
                tbl::TEXT,
                'Dropped old partition'::TEXT;

        EXCEPTION WHEN OTHERS THEN
            RETURN QUERY SELECT
                tbl::TEXT,
                'ERROR'::TEXT,
                tbl::TEXT,
                SQLERRM::TEXT;
        END;
    END LOOP;

    RETURN;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION maintain_partitions IS
'Monthly partition maintenance: create future partitions and drop old partitions per retention policy';

-- ============================================================================
-- MONITORING VIEW: Partition Statistics
-- ============================================================================

CREATE OR REPLACE VIEW partition_statistics AS
SELECT
    schemaname,
    tablename AS partition_name,
    SPLIT_PART(tablename, '_', 1) AS parent_table,
    to_date(substring(tablename from position('_2' in tablename) + 1), 'YYYY_MM') AS partition_month,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) AS index_size,
    (SELECT reltuples::BIGINT FROM pg_class WHERE relname = tablename) AS estimated_row_count
FROM pg_tables
WHERE schemaname = 'public'
AND (tablename LIKE 'transactions_%'
    OR tablename LIKE 'audit_events_%'
    OR tablename LIKE 'notifications_%')
ORDER BY parent_table, partition_month DESC;

COMMENT ON VIEW partition_statistics IS
'Statistics for all partitions including size and estimated row counts';

-- ============================================================================
-- POST-MIGRATION: Summary and Validation
-- ============================================================================

DO $$
DECLARE
    partition_count INT;
    total_partitioned_size TEXT;
BEGIN
    -- Count partitions created
    SELECT COUNT(*) INTO partition_count
    FROM pg_tables
    WHERE schemaname = 'public'
    AND (tablename LIKE 'transactions_%'
        OR tablename LIKE 'audit_events_%'
        OR tablename LIKE 'notifications_%');

    -- Calculate total size
    SELECT pg_size_pretty(SUM(pg_total_relation_size(schemaname||'.'||tablename))) INTO total_partitioned_size
    FROM pg_tables
    WHERE schemaname = 'public'
    AND (tablename LIKE 'transactions_%'
        OR tablename LIKE 'audit_events_%'
        OR tablename LIKE 'notifications_%');

    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE 'Table Partitioning Migration Complete (P1-008)';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE 'Total partitions created: %', partition_count;
    RAISE NOTICE 'Total partitioned data size: %', COALESCE(total_partitioned_size, '0 bytes');
    RAISE NOTICE '';
    RAISE NOTICE 'Partitioned Tables:';
    RAISE NOTICE '  ✓ transactions: Monthly partitions (2-year retention)';
    RAISE NOTICE '  ✓ audit_events: Monthly partitions (7-year retention for compliance)';
    RAISE NOTICE '  ✓ notifications: Monthly partitions (1-year retention)';
    RAISE NOTICE '';
    RAISE NOTICE 'Performance Improvements (Expected):';
    RAISE NOTICE '  • Date-range queries: 60-300s → <5s (60x improvement)';
    RAISE NOTICE '  • Retention cleanup: 600s → <1s (instant DROP TABLE)';
    RAISE NOTICE '  • Index maintenance: 10x faster (smaller partition indexes)';
    RAISE NOTICE '  • Vacuum operations: 20x faster (per-partition)';
    RAISE NOTICE '';
    RAISE NOTICE 'Automated Maintenance:';
    RAISE NOTICE '  • Future partitions: Auto-created 6 months ahead';
    RAISE NOTICE '  • Old partitions: Auto-dropped per retention policy';
    RAISE NOTICE '  • Run monthly: SELECT * FROM maintain_partitions();';
    RAISE NOTICE '';
    RAISE NOTICE 'Monitoring:';
    RAISE NOTICE '  • SELECT * FROM partition_statistics;';
    RAISE NOTICE '  • Check partition pruning: EXPLAIN SELECT * FROM transactions WHERE created_at >= ...';
    RAISE NOTICE '';
    RAISE NOTICE 'Benefits:';
    RAISE NOTICE '  ✓ Automatic partition pruning (PostgreSQL query optimizer)';
    RAISE NOTICE '  ✓ Faster maintenance operations (per-partition)';
    RAISE NOTICE '  ✓ Easier data archival (DROP old partitions)';
    RAISE NOTICE '  ✓ Improved query performance (smaller indexes)';
    RAISE NOTICE '  ✓ Better vacuum efficiency';
    RAISE NOTICE '';
    RAISE NOTICE 'NOTE: If tables had >1M rows, manual migration required.';
    RAISE NOTICE 'Check for *_old tables and migrate in batches.';
    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE '';
END $$;

-- Update table statistics
ANALYZE;
