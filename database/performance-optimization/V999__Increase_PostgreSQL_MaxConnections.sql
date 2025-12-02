-- ============================================================================
-- CRITICAL FIX (P0-010): Database Connection Pool Capacity Planning
-- Migration: Increase PostgreSQL max_connections for microservices scale
--
-- PREVIOUS CONFIGURATION:
-- - PostgreSQL max_connections: 200
-- - Services: 80+ microservices
-- - Risk: Connection exhaustion under load
--
-- PROBLEM ANALYSIS:
-- - Payment service alone can scale to 50 pods
-- - Each pod has HikariCP pool: 20 connections
-- - 50 pods × 20 connections = 1000 connections needed (but only 200 available!)
-- - Other 70+ services also need connections
-- - Result: "FATAL: sorry, too many clients already" errors
--
-- FIX STRATEGY:
-- 1. Increase PostgreSQL max_connections from 200 to 1000
-- 2. Deploy PgBouncer for connection pooling (transaction mode)
-- 3. Reduce HikariCP pool sizes (services → PgBouncer → PostgreSQL)
-- 4. Monitor connection usage with alerts
--
-- ARCHITECTURE:
-- Before: Services (80 × 20 connections) → PostgreSQL (200 max) ❌
-- After:  Services (80 × 15 connections) → PgBouncer (100 pools) → PostgreSQL (1000 max) ✓
--
-- CONNECTION MATH:
-- - 80 services × 15 connections = 1200 client connections to PgBouncer
-- - PgBouncer transaction pooling: 1200 → 100-200 PostgreSQL connections
-- - PostgreSQL max_connections: 1000 (headroom for maintenance, monitoring)
--
-- MEMORY IMPACT:
-- - Each connection: ~10MB RAM
-- - 1000 connections × 10MB = 10GB RAM for connections
-- - Recommendation: PostgreSQL server with 32GB+ RAM
--
-- MONITORING:
-- - Alert on >700 connections (70% threshold)
-- - Dashboard: Active connections by database/service
-- - Track connection wait time in HikariCP
--
-- ============================================================================

-- NOTE: This ALTER SYSTEM requires PostgreSQL restart to take effect
-- For production, coordinate with DBAs for maintenance window

ALTER SYSTEM SET max_connections = 1000;

-- Increase shared_buffers proportionally (25% of RAM rule of thumb)
-- Assumes 32GB RAM server: 8GB shared_buffers
ALTER SYSTEM SET shared_buffers = '8GB';

-- Increase work_mem for better query performance with more connections
-- work_mem = Total RAM / max_connections / 4
-- 32GB / 1000 / 4 = 8MB per operation
ALTER SYSTEM SET work_mem = '8MB';

-- Increase maintenance_work_mem for VACUUM, CREATE INDEX
ALTER SYSTEM SET maintenance_work_mem = '1GB';

-- Adjust effective_cache_size (75% of RAM)
ALTER SYSTEM SET effective_cache_size = '24GB';

-- Connection pooling parameters
ALTER SYSTEM SET tcp_keepalives_idle = 60;
ALTER SYSTEM SET tcp_keepalives_interval = 10;
ALTER SYSTEM SET tcp_keepalives_count = 5;

-- Log slow queries (>100ms) to identify connection hogs
ALTER SYSTEM SET log_min_duration_statement = 100;

-- Log connections and disconnections for monitoring
ALTER SYSTEM SET log_connections = on;
ALTER SYSTEM SET log_disconnections = on;

-- Prevent runaway queries from hogging connections
ALTER SYSTEM SET statement_timeout = '30s';  -- 30 second max query time
ALTER SYSTEM SET idle_in_transaction_session_timeout = '5min';  -- Kill idle transactions

-- Increase max_prepared_transactions for distributed transactions
ALTER SYSTEM SET max_prepared_transactions = 200;

-- ============================================================================
-- CREATE MONITORING VIEW: Connection Usage by Database
-- ============================================================================

CREATE OR REPLACE VIEW connection_usage_by_database AS
SELECT
    datname AS database_name,
    COUNT(*) AS active_connections,
    COUNT(*) FILTER (WHERE state = 'active') AS executing_queries,
    COUNT(*) FILTER (WHERE state = 'idle') AS idle_connections,
    COUNT(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction,
    MAX(now() - state_change) AS longest_idle_time,
    MAX(now() - query_start) AS longest_query_time
FROM pg_stat_activity
WHERE datname IS NOT NULL
GROUP BY datname
ORDER BY active_connections DESC;

COMMENT ON VIEW connection_usage_by_database IS
'Monitoring view for database connection usage. Alert on >700 total connections.';

-- ============================================================================
-- CREATE MONITORING VIEW: Connection Usage by Application
-- ============================================================================

CREATE OR REPLACE VIEW connection_usage_by_application AS
SELECT
    application_name,
    COUNT(*) AS connection_count,
    COUNT(*) FILTER (WHERE state = 'active') AS active_queries,
    MAX(now() - state_change) AS max_idle_time
FROM pg_stat_activity
WHERE application_name IS NOT NULL
GROUP BY application_name
ORDER BY connection_count DESC;

COMMENT ON VIEW connection_usage_by_application IS
'Monitoring view for connection usage by microservice. Helps identify connection leaks.';

-- ============================================================================
-- CREATE FUNCTION: Alert on High Connection Usage
-- ============================================================================

CREATE OR REPLACE FUNCTION check_connection_threshold()
RETURNS TABLE(
    alert_level TEXT,
    current_connections INT,
    max_connections INT,
    usage_percent NUMERIC,
    message TEXT
) AS $$
DECLARE
    current_conn INT;
    max_conn INT;
    usage_pct NUMERIC;
BEGIN
    SELECT COUNT(*) INTO current_conn FROM pg_stat_activity;
    SELECT setting::INT INTO max_conn FROM pg_settings WHERE name = 'max_connections';

    usage_pct := (current_conn::NUMERIC / max_conn::NUMERIC) * 100;

    IF usage_pct >= 90 THEN
        RETURN QUERY SELECT
            'CRITICAL'::TEXT,
            current_conn,
            max_conn,
            ROUND(usage_pct, 2),
            'Connection usage critical: ' || current_conn || '/' || max_conn || ' (' || ROUND(usage_pct, 2) || '%)'::TEXT;
    ELSIF usage_pct >= 70 THEN
        RETURN QUERY SELECT
            'WARNING'::TEXT,
            current_conn,
            max_conn,
            ROUND(usage_pct, 2),
            'Connection usage high: ' || current_conn || '/' || max_conn || ' (' || ROUND(usage_pct, 2) || '%)'::TEXT;
    ELSE
        RETURN QUERY SELECT
            'OK'::TEXT,
            current_conn,
            max_conn,
            ROUND(usage_pct, 2),
            'Connection usage normal: ' || current_conn || '/' || max_conn || ' (' || ROUND(usage_pct, 2) || '%)'::TEXT;
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_connection_threshold() IS
'Function to check current connection usage and return alert level. Use in monitoring scripts.';

-- ============================================================================
-- RELOAD CONFIGURATION (if server supports it)
-- ============================================================================

-- Attempt to reload configuration without restart
-- This will apply some settings immediately, but max_connections requires restart
SELECT pg_reload_conf();

-- ============================================================================
-- POST-MIGRATION INSTRUCTIONS
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE 'PostgreSQL Connection Pool Capacity Fix Applied';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE '';
    RAISE NOTICE 'CRITICAL: max_connections change requires PostgreSQL restart!';
    RAISE NOTICE '';
    RAISE NOTICE 'To apply changes:';
    RAISE NOTICE '  1. Schedule maintenance window';
    RAISE NOTICE '  2. Restart PostgreSQL: sudo systemctl restart postgresql';
    RAISE NOTICE '  3. Verify: SELECT * FROM pg_settings WHERE name = ''max_connections'';';
    RAISE NOTICE '';
    RAISE NOTICE 'Monitoring:';
    RAISE NOTICE '  - Check usage: SELECT * FROM connection_usage_by_database;';
    RAISE NOTICE '  - Check alerts: SELECT * FROM check_connection_threshold();';
    RAISE NOTICE '  - Grafana dashboard: PostgreSQL Connections by Service';
    RAISE NOTICE '';
    RAISE NOTICE 'PgBouncer Deployment:';
    RAISE NOTICE '  - Apply: kubectl apply -f k8s/database/pgbouncer-deployment.yaml';
    RAISE NOTICE '  - Update services to use PgBouncer endpoint (port 6432)';
    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE '';
END $$;
