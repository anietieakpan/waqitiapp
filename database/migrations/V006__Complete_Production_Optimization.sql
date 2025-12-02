-- ==================================================
-- Waqiti Production-Ready Database Optimization
-- Version: V006
-- Description: Complete database optimization for production readiness
-- Author: Waqiti Engineering Team
-- Date: 2025-01-01
-- ==================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "btree_gin";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- ==================================================
-- USER MANAGEMENT OPTIMIZATIONS
-- ==================================================

-- Users table optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_lower_unique 
    ON users (LOWER(email)) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_phone_hash 
    ON users USING HASH (phone_number) WHERE phone_number IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_status_created 
    ON users (status, created_at) WHERE status IN ('ACTIVE', 'PENDING_VERIFICATION');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_kyc_status 
    ON users (kyc_status, updated_at) WHERE kyc_status != 'NOT_STARTED';

-- User sessions optimization
CREATE TABLE IF NOT EXISTS user_sessions (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    device_id VARCHAR(255) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    ip_address INET NOT NULL,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_user_active 
    ON user_sessions (user_id, is_active, expires_at) WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_user_sessions_cleanup 
    ON user_sessions (expires_at, is_active) WHERE is_active = FALSE;

-- ==================================================
-- WALLET AND BALANCE OPTIMIZATIONS
-- ==================================================

-- Partitioned wallets table for high performance
CREATE TABLE IF NOT EXISTS wallets_partitioned (
    wallet_id UUID DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance DECIMAL(20,2) NOT NULL DEFAULT 0.00,
    available_balance DECIMAL(20,2) NOT NULL DEFAULT 0.00,
    pending_balance DECIMAL(20,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_number INTEGER NOT NULL DEFAULT 1, -- For optimistic locking
    CONSTRAINT pk_wallets_partitioned PRIMARY KEY (wallet_id, currency)
) PARTITION BY HASH (user_id);

-- Create partitions for wallets (16 partitions for better distribution)
DO $$
DECLARE
    i INTEGER;
BEGIN
    FOR i IN 0..15 LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS wallets_part_%s PARTITION OF wallets_partitioned 
                       FOR VALUES WITH (modulus 16, remainder %s)', i, i);
    END LOOP;
END $$;

-- Wallet balance history for audit trail
CREATE TABLE IF NOT EXISTS wallet_balance_history (
    history_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id UUID NOT NULL,
    transaction_id UUID,
    old_balance DECIMAL(20,2) NOT NULL,
    new_balance DECIMAL(20,2) NOT NULL,
    balance_change DECIMAL(20,2) NOT NULL,
    change_type VARCHAR(20) NOT NULL, -- CREDIT, DEBIT, ADJUSTMENT
    change_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID -- User or system ID that made the change
) PARTITION BY RANGE (created_at);

-- Create monthly partitions for balance history
DO $$
DECLARE
    start_date DATE := DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 year');
    end_date DATE := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '2 years');
    partition_date DATE := start_date;
BEGIN
    WHILE partition_date < end_date LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS wallet_balance_history_%s PARTITION OF wallet_balance_history 
             FOR VALUES FROM (%L) TO (%L)',
            TO_CHAR(partition_date, 'YYYY_MM'),
            partition_date,
            partition_date + INTERVAL '1 month'
        );
        partition_date := partition_date + INTERVAL '1 month';
    END LOOP;
END $$;

-- ==================================================
-- TRANSACTION OPTIMIZATIONS
-- ==================================================

-- Highly optimized transactions table with partitioning
CREATE TABLE IF NOT EXISTS transactions_optimized (
    transaction_id UUID DEFAULT uuid_generate_v4(),
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    sender_wallet_id UUID NOT NULL,
    recipient_wallet_id UUID,
    amount DECIMAL(20,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(10,6),
    fee_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00,
    net_amount DECIMAL(20,2) NOT NULL, -- amount - fee
    transaction_type VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    reference_id VARCHAR(255), -- External reference
    idempotency_key VARCHAR(255) NOT NULL,
    risk_score DECIMAL(3,2), -- Fraud detection score
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    metadata JSONB, -- Additional transaction data
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transactions_optimized PRIMARY KEY (transaction_id, created_at),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_net_amount_calc CHECK (net_amount = amount - fee_amount)
) PARTITION BY RANGE (created_at);

-- Create monthly partitions for transactions
DO $$
DECLARE
    start_date DATE := DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 year');
    end_date DATE := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '2 years');
    partition_date DATE := start_date;
BEGIN
    WHILE partition_date < end_date LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS transactions_%s PARTITION OF transactions_optimized 
             FOR VALUES FROM (%L) TO (%L)',
            TO_CHAR(partition_date, 'YYYY_MM'),
            partition_date,
            partition_date + INTERVAL '1 month'
        );
        partition_date := partition_date + INTERVAL '1 month';
    END LOOP;
END $$;

-- Critical indexes for transaction performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_sender_status_date 
    ON transactions_optimized (sender_id, status, created_at DESC) 
    WHERE status IN ('COMPLETED', 'PENDING');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_recipient_status_date 
    ON transactions_optimized (recipient_id, status, created_at DESC) 
    WHERE status IN ('COMPLETED', 'PENDING');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_idempotency 
    ON transactions_optimized (idempotency_key, sender_id) WHERE status != 'FAILED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reference 
    ON transactions_optimized (reference_id) WHERE reference_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_risk_score 
    ON transactions_optimized (risk_score, created_at) WHERE risk_score >= 0.7;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount_range 
    ON transactions_optimized (amount, currency, created_at) WHERE amount >= 1000;

-- GIN index for JSONB metadata queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_metadata_gin 
    ON transactions_optimized USING GIN (metadata) WHERE metadata IS NOT NULL;

-- ==================================================
-- FRAUD DETECTION OPTIMIZATIONS
-- ==================================================

-- Fraud incidents with time-based partitioning
CREATE TABLE IF NOT EXISTS fraud_incidents_partitioned (
    incident_id UUID DEFAULT uuid_generate_v4(),
    transaction_id UUID NOT NULL,
    user_id UUID NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    risk_score DECIMAL(3,2) NOT NULL,
    fraud_type VARCHAR(50) NOT NULL,
    detection_method VARCHAR(50) NOT NULL,
    is_confirmed_fraud BOOLEAN,
    false_positive BOOLEAN DEFAULT FALSE,
    incident_data JSONB NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_fraud_incidents PRIMARY KEY (incident_id, created_at),
    CONSTRAINT chk_risk_score CHECK (risk_score >= 0.0 AND risk_score <= 1.0)
) PARTITION BY RANGE (created_at);

-- Create monthly partitions for fraud incidents
DO $$
DECLARE
    start_date DATE := DATE_TRUNC('month', CURRENT_DATE - INTERVAL '2 years');
    end_date DATE := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 year');
    partition_date DATE := start_date;
BEGIN
    WHILE partition_date < end_date LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS fraud_incidents_%s PARTITION OF fraud_incidents_partitioned 
             FOR VALUES FROM (%L) TO (%L)',
            TO_CHAR(partition_date, 'YYYY_MM'),
            partition_date,
            partition_date + INTERVAL '1 month'
        );
        partition_date := partition_date + INTERVAL '1 month';
    END LOOP;
END $$;

-- Fraud detection indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_user_risk_date 
    ON fraud_incidents_partitioned (user_id, risk_level, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_transaction_lookup 
    ON fraud_incidents_partitioned (transaction_id, risk_score);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_confirmed_cases 
    ON fraud_incidents_partitioned (is_confirmed_fraud, created_at) 
    WHERE is_confirmed_fraud = TRUE;

-- User velocity tracking for fraud detection
CREATE TABLE IF NOT EXISTS user_velocity_tracking (
    user_id UUID NOT NULL,
    time_window INTERVAL NOT NULL, -- '1 hour', '24 hours', etc.
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    total_amount DECIMAL(20,2) NOT NULL DEFAULT 0.00,
    unique_recipients INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_velocity_tracking PRIMARY KEY (user_id, time_window, window_start)
);

CREATE INDEX IF NOT EXISTS idx_velocity_window_cleanup 
    ON user_velocity_tracking (window_start, last_updated) 
    WHERE window_start < CURRENT_TIMESTAMP - INTERVAL '7 days';

-- ==================================================
-- AUDIT AND COMPLIANCE OPTIMIZATIONS
-- ==================================================

-- Comprehensive audit log
CREATE TABLE IF NOT EXISTS audit_events_partitioned (
    event_id UUID DEFAULT uuid_generate_v4(),
    user_id UUID,
    session_id UUID,
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(50) NOT NULL, -- AUTH, PAYMENT, ADMIN, etc.
    resource_type VARCHAR(50),
    resource_id VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    request_data JSONB,
    response_data JSONB,
    before_state JSONB,
    after_state JSONB,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    processing_time_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_events PRIMARY KEY (event_id, created_at)
) PARTITION BY RANGE (created_at);

-- Create daily partitions for audit events (high volume)
DO $$
DECLARE
    start_date DATE := CURRENT_DATE - INTERVAL '90 days';
    end_date DATE := CURRENT_DATE + INTERVAL '30 days';
    partition_date DATE := start_date;
BEGIN
    WHILE partition_date <= end_date LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS audit_events_%s PARTITION OF audit_events_partitioned 
             FOR VALUES FROM (%L) TO (%L)',
            TO_CHAR(partition_date, 'YYYY_MM_DD'),
            partition_date,
            partition_date + INTERVAL '1 day'
        );
        partition_date := partition_date + INTERVAL '1 day';
    END LOOP;
END $$;

-- Audit indexes for compliance reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_user_category_date 
    ON audit_events_partitioned (user_id, event_category, created_at DESC) 
    WHERE user_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_failed_events 
    ON audit_events_partitioned (success, event_type, created_at) 
    WHERE success = FALSE;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_admin_actions 
    ON audit_events_partitioned (event_category, action, created_at) 
    WHERE event_category = 'ADMIN';

-- ==================================================
-- PERFORMANCE MONITORING TABLES
-- ==================================================

-- Query performance monitoring
CREATE TABLE IF NOT EXISTS query_performance_log (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    query_hash CHAR(32) NOT NULL, -- MD5 hash of normalized query
    query_text TEXT NOT NULL,
    execution_time_ms INTEGER NOT NULL,
    rows_examined INTEGER,
    rows_returned INTEGER,
    database_name VARCHAR(100),
    table_names TEXT[], -- Array of tables accessed
    index_usage JSONB, -- Information about indexes used
    execution_plan JSONB,
    user_id UUID,
    application_name VARCHAR(100),
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (executed_at);

-- Create daily partitions for query performance
DO $$
DECLARE
    partition_date DATE := CURRENT_DATE - INTERVAL '7 days';
    end_date DATE := CURRENT_DATE + INTERVAL '7 days';
BEGIN
    WHILE partition_date <= end_date LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS query_performance_%s PARTITION OF query_performance_log 
             FOR VALUES FROM (%L) TO (%L)',
            TO_CHAR(partition_date, 'YYYY_MM_DD'),
            partition_date,
            partition_date + INTERVAL '1 day'
        );
        partition_date := partition_date + INTERVAL '1 day';
    END LOOP;
END $$;

-- ==================================================
-- MATERIALIZED VIEWS FOR ANALYTICS
-- ==================================================

-- Daily transaction summary for reporting
CREATE MATERIALIZED VIEW IF NOT EXISTS daily_transaction_summary AS
SELECT 
    DATE(created_at) as transaction_date,
    currency,
    status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    SUM(fee_amount) as total_fees,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount,
    COUNT(DISTINCT sender_id) as unique_senders,
    COUNT(DISTINCT recipient_id) as unique_recipients
FROM transactions_optimized
WHERE created_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE(created_at), currency, status;

CREATE UNIQUE INDEX IF NOT EXISTS idx_daily_tx_summary_unique 
    ON daily_transaction_summary (transaction_date, currency, status);

-- User activity summary
CREATE MATERIALIZED VIEW IF NOT EXISTS user_activity_summary AS
SELECT 
    u.user_id,
    u.created_at as user_since,
    COUNT(t.transaction_id) as total_transactions,
    SUM(CASE WHEN t.sender_id = u.user_id THEN t.amount ELSE 0 END) as total_sent,
    SUM(CASE WHEN t.recipient_id = u.user_id THEN t.amount ELSE 0 END) as total_received,
    MAX(t.created_at) as last_transaction_at,
    COUNT(DISTINCT CASE WHEN t.sender_id = u.user_id THEN t.recipient_id END) as unique_recipients_sent_to,
    COUNT(DISTINCT CASE WHEN t.recipient_id = u.user_id THEN t.sender_id END) as unique_senders_received_from,
    COALESCE(AVG(f.risk_score), 0) as avg_risk_score
FROM users u
LEFT JOIN transactions_optimized t ON (u.user_id = t.sender_id OR u.user_id = t.recipient_id)
LEFT JOIN fraud_incidents_partitioned f ON u.user_id = f.user_id AND f.created_at >= CURRENT_DATE - INTERVAL '90 days'
WHERE u.status = 'ACTIVE' AND (t.status = 'COMPLETED' OR t.status IS NULL)
GROUP BY u.user_id, u.created_at;

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_activity_summary_unique 
    ON user_activity_summary (user_id);

-- ==================================================
-- DATABASE FUNCTIONS FOR OPTIMIZATION
-- ==================================================

-- Function to refresh materialized views
CREATE OR REPLACE FUNCTION refresh_analytics_views()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_transaction_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_activity_summary;
    
    -- Log the refresh
    INSERT INTO audit_events_partitioned (
        event_type, event_category, action, success, processing_time_ms
    ) VALUES (
        'SYSTEM', 'MAINTENANCE', 'REFRESH_VIEWS', TRUE, 
        EXTRACT(EPOCH FROM clock_timestamp() - statement_timestamp()) * 1000
    );
END;
$$ LANGUAGE plpgsql;

-- Function to clean up old partitions
CREATE OR REPLACE FUNCTION cleanup_old_partitions()
RETURNS VOID AS $$
DECLARE
    partition_record RECORD;
    cutoff_date DATE := CURRENT_DATE - INTERVAL '2 years';
BEGIN
    -- Clean up audit events older than 2 years
    FOR partition_record IN
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'audit_events_%' 
        AND tablename < 'audit_events_' || TO_CHAR(cutoff_date, 'YYYY_MM_DD')
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE', 
                      partition_record.schemaname, partition_record.tablename);
    END LOOP;
    
    -- Clean up old balance history (keep 1 year)
    cutoff_date := CURRENT_DATE - INTERVAL '1 year';
    FOR partition_record IN
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'wallet_balance_history_%' 
        AND tablename < 'wallet_balance_history_' || TO_CHAR(cutoff_date, 'YYYY_MM')
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE', 
                      partition_record.schemaname, partition_record.tablename);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Function to update table statistics
CREATE OR REPLACE FUNCTION update_table_statistics()
RETURNS VOID AS $$
DECLARE
    table_name TEXT;
BEGIN
    FOR table_name IN
        SELECT tablename FROM pg_tables 
        WHERE schemaname = 'public' 
        AND tablename IN ('transactions_optimized', 'wallets_partitioned', 'users', 'fraud_incidents_partitioned')
    LOOP
        EXECUTE format('ANALYZE %I', table_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- ==================================================
-- SCHEDULED MAINTENANCE JOBS
-- ==================================================

-- Create pg_cron jobs for maintenance (if pg_cron extension is available)
DO $$
BEGIN
    -- Refresh materialized views every hour
    PERFORM cron.schedule('refresh-analytics', '0 * * * *', 'SELECT refresh_analytics_views();');
    
    -- Update statistics daily at 2 AM
    PERFORM cron.schedule('update-stats', '0 2 * * *', 'SELECT update_table_statistics();');
    
    -- Clean up old partitions weekly
    PERFORM cron.schedule('cleanup-partitions', '0 3 * * 0', 'SELECT cleanup_old_partitions();');
    
    -- Clean up old sessions daily
    PERFORM cron.schedule('cleanup-sessions', '0 4 * * *', 
                         'DELETE FROM user_sessions WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL ''7 days'';');
    
EXCEPTION
    WHEN OTHERS THEN
        -- pg_cron not available, skip scheduling
        RAISE NOTICE 'pg_cron extension not available, skipping job scheduling';
END
$$;

-- ==================================================
-- DATABASE CONFIGURATION RECOMMENDATIONS
-- ==================================================

-- Set optimal configuration parameters (must be set in postgresql.conf)
/*
# Performance Settings
shared_buffers = 25% of RAM
effective_cache_size = 75% of RAM
work_mem = 256MB
maintenance_work_mem = 2GB
max_connections = 200
max_parallel_workers_per_gather = 4

# Checkpoint Settings
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 500

# Query Planner
random_page_cost = 1.1  # For SSD storage
effective_io_concurrency = 200

# Partitioning
enable_partition_pruning = on
enable_partitionwise_join = on
enable_partitionwise_aggregate = on

# Monitoring
log_min_duration_statement = 1000  # Log slow queries
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on
*/

-- ==================================================
-- FINAL OPTIMIZATIONS AND CLEANUP
-- ==================================================

-- Update all table statistics
SELECT update_table_statistics();

-- Create or update table comments for documentation
COMMENT ON TABLE transactions_optimized IS 'Optimized transactions table with partitioning for high performance';
COMMENT ON TABLE fraud_incidents_partitioned IS 'Fraud detection incidents with time-based partitioning';
COMMENT ON TABLE audit_events_partitioned IS 'Complete audit trail with daily partitioning';
COMMENT ON TABLE wallets_partitioned IS 'User wallets with hash partitioning for even distribution';

-- Create indexes for foreign key constraints to improve join performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fk_transactions_sender 
    ON transactions_optimized (sender_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fk_transactions_recipient 
    ON transactions_optimized (recipient_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fk_wallets_user 
    ON wallets_partitioned (user_id);

-- Grant appropriate permissions
GRANT SELECT ON daily_transaction_summary TO readonly_user;
GRANT SELECT ON user_activity_summary TO readonly_user;

-- Final commit and logging
INSERT INTO audit_events_partitioned (
    event_type, event_category, action, success, request_data
) VALUES (
    'SYSTEM', 'MIGRATION', 'V006_COMPLETE_OPTIMIZATION', TRUE,
    '{"description": "Complete production optimization migration completed", "version": "V006"}'::jsonb
);

-- Performance monitoring query to validate optimization
WITH performance_check AS (
    SELECT 
        'transactions_optimized' as table_name,
        pg_size_pretty(pg_total_relation_size('transactions_optimized')) as size,
        (SELECT COUNT(*) FROM transactions_optimized) as row_count
    UNION ALL
    SELECT 
        'fraud_incidents_partitioned' as table_name,
        pg_size_pretty(pg_total_relation_size('fraud_incidents_partitioned')) as size,
        (SELECT COUNT(*) FROM fraud_incidents_partitioned) as row_count
    UNION ALL
    SELECT 
        'audit_events_partitioned' as table_name,
        pg_size_pretty(pg_total_relation_size('audit_events_partitioned')) as size,
        (SELECT COUNT(*) FROM audit_events_partitioned) as row_count
)
SELECT * FROM performance_check;

COMMIT;