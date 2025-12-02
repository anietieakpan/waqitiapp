-- Waqiti Production Database Performance Optimization
-- Execute with caution in production environment
-- Backup database before running these optimizations

-- =====================================================
-- 1. ANALYZE AND VACUUM OPERATIONS
-- =====================================================

-- Update statistics for query planner
ANALYZE;

-- Reclaim storage and update statistics (requires maintenance window)
-- VACUUM ANALYZE;

-- For critical tables with high write volume
VACUUM ANALYZE transactions;
VACUUM ANALYZE wallets;
VACUUM ANALYZE payments;
VACUUM ANALYZE ledger_entries;

-- =====================================================
-- 2. PERFORMANCE MONITORING VIEWS
-- =====================================================

-- View for slow queries
CREATE OR REPLACE VIEW v_slow_queries AS
SELECT 
    query,
    calls,
    total_exec_time,
    mean_exec_time,
    max_exec_time,
    stddev_exec_time,
    rows
FROM pg_stat_statements
WHERE mean_exec_time > 100
ORDER BY mean_exec_time DESC
LIMIT 50;

-- View for table bloat
CREATE OR REPLACE VIEW v_table_bloat AS
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) AS bloat_size,
    round(100 * (pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) / pg_total_relation_size(schemaname||'.'||tablename)::numeric, 2) AS bloat_percentage
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
LIMIT 20;

-- View for index usage
CREATE OR REPLACE VIEW v_index_usage AS
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    CASE 
        WHEN idx_scan = 0 THEN 'UNUSED'
        WHEN idx_scan < 100 THEN 'RARELY USED'
        ELSE 'ACTIVE'
    END AS usage_status
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC, pg_relation_size(indexrelid) DESC;

-- =====================================================
-- 3. OPTIMIZED INDEXES FOR CRITICAL QUERIES
-- =====================================================

-- Composite indexes for payment queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_user_status_created 
ON payments(user_id, status, created_at DESC) 
WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_merchant_date 
ON payments(merchant_id, created_at DESC) 
WHERE merchant_id IS NOT NULL;

-- Partial indexes for active records
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_active 
ON wallets(user_id, currency) 
WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_recent 
ON transactions(created_at DESC, status) 
WHERE created_at > CURRENT_DATE - INTERVAL '30 days';

-- BRIN indexes for time-series data
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_created_brin 
ON audit_logs USING BRIN(created_at) 
WITH (pages_per_range = 128);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_history_brin 
ON transaction_history USING BRIN(transaction_date) 
WITH (pages_per_range = 64);

-- GIN indexes for JSONB columns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_metadata_gin 
ON payments USING GIN(metadata jsonb_path_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_preferences_gin 
ON users USING GIN(preferences jsonb_path_ops);

-- =====================================================
-- 4. QUERY OPTIMIZATION WITH MATERIALIZED VIEWS
-- =====================================================

-- Daily transaction summary
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_transaction_summary AS
SELECT 
    DATE(created_at) as transaction_date,
    COUNT(*) as total_transactions,
    COUNT(DISTINCT user_id) as unique_users,
    SUM(amount) as total_volume,
    AVG(amount) as avg_amount,
    MAX(amount) as max_amount,
    MIN(amount) as min_amount,
    COUNT(*) FILTER (WHERE status = 'SUCCESS') as successful_transactions,
    COUNT(*) FILTER (WHERE status = 'FAILED') as failed_transactions,
    AVG(EXTRACT(EPOCH FROM (updated_at - created_at))) as avg_processing_time
FROM transactions
WHERE created_at > CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE(created_at)
WITH DATA;

CREATE UNIQUE INDEX ON mv_daily_transaction_summary(transaction_date);

-- User balance summary
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_user_balance_summary AS
SELECT 
    w.user_id,
    w.currency,
    w.balance,
    w.available_balance,
    COUNT(t.id) as transaction_count,
    MAX(t.created_at) as last_transaction_date,
    SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE 0 END) as total_credits,
    SUM(CASE WHEN t.type = 'DEBIT' THEN t.amount ELSE 0 END) as total_debits
FROM wallets w
LEFT JOIN transactions t ON w.id = t.wallet_id
WHERE w.status = 'ACTIVE'
GROUP BY w.user_id, w.currency, w.balance, w.available_balance
WITH DATA;

CREATE UNIQUE INDEX ON mv_user_balance_summary(user_id, currency);

-- Merchant analytics
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_merchant_analytics AS
SELECT 
    m.id as merchant_id,
    m.business_name,
    COUNT(p.id) as total_payments,
    SUM(p.amount) as total_revenue,
    AVG(p.amount) as avg_payment,
    COUNT(DISTINCT p.user_id) as unique_customers,
    COUNT(*) FILTER (WHERE p.created_at > CURRENT_DATE - INTERVAL '30 days') as payments_last_30_days,
    SUM(p.amount) FILTER (WHERE p.created_at > CURRENT_DATE - INTERVAL '30 days') as revenue_last_30_days
FROM merchants m
LEFT JOIN payments p ON m.id = p.merchant_id AND p.status = 'SUCCESS'
GROUP BY m.id, m.business_name
WITH DATA;

CREATE UNIQUE INDEX ON mv_merchant_analytics(merchant_id);

-- =====================================================
-- 5. PARTITION OPTIMIZATION
-- =====================================================

-- Ensure partitions are properly maintained
DO $$
DECLARE
    start_date DATE := CURRENT_DATE;
    end_date DATE := CURRENT_DATE + INTERVAL '3 months';
    partition_date DATE;
    partition_name TEXT;
BEGIN
    partition_date := start_date;
    WHILE partition_date < end_date LOOP
        partition_name := 'transactions_' || TO_CHAR(partition_date, 'YYYY_MM');
        
        -- Check if partition exists, create if not
        IF NOT EXISTS (
            SELECT 1 FROM pg_class WHERE relname = partition_name
        ) THEN
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS %I PARTITION OF transactions 
                FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                partition_date,
                partition_date + INTERVAL '1 month'
            );
            
            -- Create indexes on new partition
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS %I ON %I(user_id, created_at)',
                partition_name || '_user_idx',
                partition_name
            );
            
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS %I ON %I(status, created_at)',
                partition_name || '_status_idx',
                partition_name
            );
        END IF;
        
        partition_date := partition_date + INTERVAL '1 month';
    END LOOP;
END$$;

-- =====================================================
-- 6. CONNECTION POOLING OPTIMIZATION
-- =====================================================

-- Optimal PostgreSQL settings for high concurrency
-- Add these to postgresql.conf or set via ALTER SYSTEM

/*
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '8GB';
ALTER SYSTEM SET effective_cache_size = '24GB';
ALTER SYSTEM SET maintenance_work_mem = '2GB';
ALTER SYSTEM SET work_mem = '32MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '64MB';
ALTER SYSTEM SET default_statistics_target = 100;
ALTER SYSTEM SET random_page_cost = 1.1;
ALTER SYSTEM SET effective_io_concurrency = 200;
ALTER SYSTEM SET min_wal_size = '2GB';
ALTER SYSTEM SET max_wal_size = '8GB';
ALTER SYSTEM SET max_parallel_workers_per_gather = 4;
ALTER SYSTEM SET max_parallel_workers = 8;
ALTER SYSTEM SET max_parallel_maintenance_workers = 4;
*/

-- =====================================================
-- 7. STORED PROCEDURES FOR COMPLEX OPERATIONS
-- =====================================================

-- Optimized transfer procedure
CREATE OR REPLACE FUNCTION process_transfer(
    p_from_wallet_id UUID,
    p_to_wallet_id UUID,
    p_amount DECIMAL(19,4),
    p_currency VARCHAR(3),
    p_reference VARCHAR(255)
) RETURNS TABLE(success BOOLEAN, transaction_id UUID, message TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_from_balance DECIMAL(19,4);
    v_transaction_id UUID;
    v_timestamp TIMESTAMP = NOW();
BEGIN
    -- Lock wallets to prevent race conditions
    PERFORM 1 FROM wallets WHERE id = p_from_wallet_id FOR UPDATE;
    PERFORM 1 FROM wallets WHERE id = p_to_wallet_id FOR UPDATE;
    
    -- Check balance
    SELECT available_balance INTO v_from_balance 
    FROM wallets 
    WHERE id = p_from_wallet_id AND currency = p_currency AND status = 'ACTIVE';
    
    IF v_from_balance IS NULL OR v_from_balance < p_amount THEN
        RETURN QUERY SELECT FALSE, NULL::UUID, 'Insufficient funds'::TEXT;
        RETURN;
    END IF;
    
    v_transaction_id := gen_random_uuid();
    
    -- Debit from wallet
    UPDATE wallets 
    SET available_balance = available_balance - p_amount,
        balance = balance - p_amount,
        updated_at = v_timestamp
    WHERE id = p_from_wallet_id;
    
    -- Credit to wallet
    UPDATE wallets 
    SET available_balance = available_balance + p_amount,
        balance = balance + p_amount,
        updated_at = v_timestamp
    WHERE id = p_to_wallet_id;
    
    -- Create transaction records
    INSERT INTO transactions (id, wallet_id, type, amount, currency, status, reference, created_at)
    VALUES 
        (v_transaction_id, p_from_wallet_id, 'DEBIT', p_amount, p_currency, 'SUCCESS', p_reference, v_timestamp),
        (gen_random_uuid(), p_to_wallet_id, 'CREDIT', p_amount, p_currency, 'SUCCESS', p_reference, v_timestamp);
    
    -- Create ledger entries
    INSERT INTO ledger_entries (transaction_id, account_id, debit, credit, balance_after, created_at)
    SELECT 
        v_transaction_id,
        p_from_wallet_id,
        p_amount,
        0,
        balance,
        v_timestamp
    FROM wallets WHERE id = p_from_wallet_id;
    
    INSERT INTO ledger_entries (transaction_id, account_id, debit, credit, balance_after, created_at)
    SELECT 
        v_transaction_id,
        p_to_wallet_id,
        0,
        p_amount,
        balance,
        v_timestamp
    FROM wallets WHERE id = p_to_wallet_id;
    
    RETURN QUERY SELECT TRUE, v_transaction_id, 'Transfer successful'::TEXT;
END;
$$;

-- =====================================================
-- 8. MONITORING AND ALERTING FUNCTIONS
-- =====================================================

-- Function to check database health
CREATE OR REPLACE FUNCTION check_database_health()
RETURNS TABLE(
    metric_name TEXT,
    metric_value NUMERIC,
    status TEXT,
    recommendation TEXT
)
LANGUAGE plpgsql
AS $$
BEGIN
    -- Check cache hit ratio
    RETURN QUERY
    SELECT 
        'Cache Hit Ratio'::TEXT,
        ROUND(100.0 * sum(heap_blks_hit) / NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2),
        CASE 
            WHEN ROUND(100.0 * sum(heap_blks_hit) / NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2) > 90 THEN 'GOOD'
            WHEN ROUND(100.0 * sum(heap_blks_hit) / NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2) > 80 THEN 'WARNING'
            ELSE 'CRITICAL'
        END,
        CASE 
            WHEN ROUND(100.0 * sum(heap_blks_hit) / NULLIF(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2) < 90 
            THEN 'Consider increasing shared_buffers'
            ELSE 'Optimal'
        END
    FROM pg_statio_user_tables;
    
    -- Check index usage
    RETURN QUERY
    SELECT 
        'Index Usage Ratio'::TEXT,
        ROUND(100.0 * sum(idx_scan) / NULLIF(sum(seq_scan + idx_scan), 0), 2),
        CASE 
            WHEN ROUND(100.0 * sum(idx_scan) / NULLIF(sum(seq_scan + idx_scan), 0), 2) > 95 THEN 'GOOD'
            WHEN ROUND(100.0 * sum(idx_scan) / NULLIF(sum(seq_scan + idx_scan), 0), 2) > 80 THEN 'WARNING'
            ELSE 'CRITICAL'
        END,
        CASE 
            WHEN ROUND(100.0 * sum(idx_scan) / NULLIF(sum(seq_scan + idx_scan), 0), 2) < 95 
            THEN 'Review and create missing indexes'
            ELSE 'Optimal'
        END
    FROM pg_stat_user_tables;
    
    -- Check table bloat
    RETURN QUERY
    SELECT 
        'Table Bloat Percentage'::TEXT,
        ROUND(AVG(CASE 
            WHEN pg_total_relation_size(c.oid) > 0 
            THEN 100.0 * (pg_total_relation_size(c.oid) - pg_relation_size(c.oid)) / pg_total_relation_size(c.oid)
            ELSE 0 
        END), 2),
        CASE 
            WHEN AVG(CASE 
                WHEN pg_total_relation_size(c.oid) > 0 
                THEN 100.0 * (pg_total_relation_size(c.oid) - pg_relation_size(c.oid)) / pg_total_relation_size(c.oid)
                ELSE 0 
            END) > 40 THEN 'CRITICAL'
            WHEN AVG(CASE 
                WHEN pg_total_relation_size(c.oid) > 0 
                THEN 100.0 * (pg_total_relation_size(c.oid) - pg_relation_size(c.oid)) / pg_total_relation_size(c.oid)
                ELSE 0 
            END) > 20 THEN 'WARNING'
            ELSE 'GOOD'
        END,
        CASE 
            WHEN AVG(CASE 
                WHEN pg_total_relation_size(c.oid) > 0 
                THEN 100.0 * (pg_total_relation_size(c.oid) - pg_relation_size(c.oid)) / pg_total_relation_size(c.oid)
                ELSE 0 
            END) > 20 
            THEN 'Schedule VACUUM FULL for bloated tables'
            ELSE 'Acceptable'
        END
    FROM pg_class c
    LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
    AND c.relkind = 'r';
    
    -- Check connection usage
    RETURN QUERY
    SELECT 
        'Connection Usage'::TEXT,
        ROUND(100.0 * COUNT(*) / current_setting('max_connections')::NUMERIC, 2),
        CASE 
            WHEN 100.0 * COUNT(*) / current_setting('max_connections')::NUMERIC > 80 THEN 'CRITICAL'
            WHEN 100.0 * COUNT(*) / current_setting('max_connections')::NUMERIC > 60 THEN 'WARNING'
            ELSE 'GOOD'
        END,
        CASE 
            WHEN 100.0 * COUNT(*) / current_setting('max_connections')::NUMERIC > 60 
            THEN 'Consider connection pooling or increasing max_connections'
            ELSE 'Optimal'
        END
    FROM pg_stat_activity;
END;
$$;

-- =====================================================
-- 9. CLEANUP AND MAINTENANCE
-- =====================================================

-- Remove unused indexes
DO $$
DECLARE
    rec RECORD;
BEGIN
    FOR rec IN 
        SELECT schemaname, indexname 
        FROM pg_stat_user_indexes 
        WHERE idx_scan = 0 
        AND indexrelname NOT LIKE '%_pkey'
        AND schemaname = 'public'
    LOOP
        RAISE NOTICE 'Consider dropping unused index: %.%', rec.schemaname, rec.indexname;
        -- Uncomment to actually drop:
        -- EXECUTE format('DROP INDEX %I.%I', rec.schemaname, rec.indexname);
    END LOOP;
END$$;

-- =====================================================
-- 10. REFRESH MATERIALIZED VIEWS
-- =====================================================

-- Create a function to refresh all materialized views
CREATE OR REPLACE FUNCTION refresh_all_materialized_views()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    mat_view RECORD;
BEGIN
    FOR mat_view IN 
        SELECT schemaname, matviewname 
        FROM pg_matviews 
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('REFRESH MATERIALIZED VIEW CONCURRENTLY %I.%I', 
                      mat_view.schemaname, mat_view.matviewname);
        RAISE NOTICE 'Refreshed materialized view: %', mat_view.matviewname;
    END LOOP;
END;
$$;

-- Schedule regular refresh (use pg_cron or external scheduler)
-- SELECT cron.schedule('refresh-matviews', '*/15 * * * *', 'SELECT refresh_all_materialized_views()');

-- =====================================================
-- 11. GRANT APPROPRIATE PERMISSIONS
-- =====================================================

-- Grant permissions to application user
GRANT EXECUTE ON FUNCTION process_transfer TO waqiti_app;
GRANT EXECUTE ON FUNCTION check_database_health TO waqiti_monitor;
GRANT EXECUTE ON FUNCTION refresh_all_materialized_views TO waqiti_admin;

-- Grant read permissions on monitoring views
GRANT SELECT ON v_slow_queries TO waqiti_monitor;
GRANT SELECT ON v_table_bloat TO waqiti_monitor;
GRANT SELECT ON v_index_usage TO waqiti_monitor;

-- =====================================================
-- 12. FINAL RECOMMENDATIONS
-- =====================================================

/*
Post-optimization checklist:
1. Monitor query performance using pg_stat_statements
2. Review slow query log regularly
3. Update table statistics weekly: ANALYZE;
4. Check for table and index bloat monthly
5. Review and optimize connection pool settings
6. Implement automated backup and point-in-time recovery
7. Set up streaming replication for high availability
8. Configure automated failover with Patroni or similar
9. Implement query result caching with Redis
10. Consider read replicas for analytics queries
*/

-- End of optimization script