-- ==========================================================================
-- WAQITI COMPLETE DATABASE PERFORMANCE OPTIMIZATION
-- Comprehensive N+1 Query Fixes and Index Creation
-- ==========================================================================

-- Enable performance monitoring
SET log_statement = 'all';
SET log_min_duration_statement = 1000; -- Log queries taking more than 1 second

-- ==========================================================================
-- CRITICAL PERFORMANCE INDEXES
-- ==========================================================================

-- User Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_users_email_status ON users(email, status) WHERE deleted_at IS NULL;
CREATE INDEX CONCURRENT IF NOT EXISTS idx_users_phone_status ON users(phone, status) WHERE deleted_at IS NULL;
CREATE INDEX CONCURRENT IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_verification_tokens_token_type ON verification_tokens(token, token_type, expires_at);

-- Wallet Service Indexes  
CREATE INDEX CONCURRENT IF NOT EXISTS idx_wallets_user_id_status ON wallets(user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX CONCURRENT IF NOT EXISTS idx_wallets_balance_user_id ON wallets(user_id, balance) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENT IF NOT EXISTS idx_wallet_transactions_wallet_id_created ON wallet_transactions(wallet_id, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_wallet_transactions_type_status ON wallet_transactions(transaction_type, status);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_wallet_transactions_reference_id ON wallet_transactions(reference_id) WHERE reference_id IS NOT NULL;

-- Payment Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_payments_sender_receiver ON payments(sender_id, receiver_id, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_payments_status_created ON payments(status, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_payments_reference_number ON payments(reference_number) WHERE reference_number IS NOT NULL;
CREATE INDEX CONCURRENT IF NOT EXISTS idx_payment_methods_user_id_status ON payment_methods(user_id, status, is_default DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_payment_requests_requester_recipient ON payment_requests(requester_id, recipient_id, status);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_scheduled_payments_next_execution ON scheduled_payments(next_execution_date) WHERE status = 'ACTIVE';

-- Transaction Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_user_id_timestamp ON transactions(user_id, timestamp DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_type_status_timestamp ON transactions(transaction_type, status, timestamp DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_amount_range ON transactions(amount) WHERE status = 'COMPLETED';
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_sharded_user_month ON transactions(user_id, date_trunc('month', timestamp));

-- Ledger Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_ledger_entries_account_id_timestamp ON ledger_entries(account_id, transaction_timestamp DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_ledger_entries_reference_id ON ledger_entries(reference_id);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_account_balances_account_id_timestamp ON account_balances(account_id, balance_timestamp DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_journal_entries_batch_id ON journal_entries(batch_id);

-- Notification Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_notifications_user_id_created ON notifications(user_id, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_notifications_type_status ON notifications(notification_type, status);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_notifications_scheduled_for ON notifications(scheduled_for) WHERE status = 'PENDING';
CREATE INDEX CONCURRENT IF NOT EXISTS idx_push_notification_logs_device_token ON push_notification_logs(device_token);

-- Investment Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_investment_accounts_user_id ON investment_accounts(user_id);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_investment_orders_user_status ON investment_orders(user_id, status, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_investment_holdings_account_symbol ON investment_holdings(account_id, symbol);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_portfolio_user_id ON portfolios(user_id);

-- Compliance Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_compliance_alerts_user_id_priority ON compliance_alerts(user_id, priority, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_suspicious_activities_user_timestamp ON suspicious_activities(user_id, detected_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_customer_risk_profiles_user_score ON customer_risk_profiles(user_id, risk_score);

-- Messaging Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_messages_conversation_timestamp ON messages(conversation_id, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_conversations_participants ON conversation_participants(conversation_id, user_id);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_messages_sender_timestamp ON messages(sender_id, created_at DESC);

-- Rewards Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_rewards_accounts_user_id ON rewards_accounts(user_id);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_cashback_transactions_user_earned ON cashback_transactions(user_id, earned_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_points_transactions_user_processed ON points_transactions(user_id, processed_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_redemption_transactions_user_processed ON redemption_transactions(user_id, processed_at DESC);

-- Crypto Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_crypto_wallets_user_currency ON crypto_wallets(user_id, currency);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_crypto_transactions_wallet_timestamp ON crypto_transactions(wallet_id, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_crypto_balances_wallet_currency ON crypto_balances(wallet_id, currency);

-- BNPL Service Indexes
CREATE INDEX CONCURRENT IF NOT EXISTS idx_bnpl_applications_user_status ON bnpl_applications(user_id, status, created_at DESC);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_bnpl_plans_user_status ON bnpl_plans(user_id, status);
CREATE INDEX CONCURRENT IF NOT EXISTS idx_bnpl_installments_plan_due_date ON bnpl_installments(plan_id, due_date);

-- ==========================================================================
-- COMPOSITE INDEXES FOR COMPLEX QUERIES
-- ==========================================================================

-- User activity tracking
CREATE INDEX CONCURRENT IF NOT EXISTS idx_user_activity_composite ON user_activity_logs(user_id, activity_type, created_at DESC);

-- Payment analytics
CREATE INDEX CONCURRENT IF NOT EXISTS idx_payment_analytics ON payments(
    date_trunc('day', created_at), 
    status, 
    payment_method
) WHERE status IN ('COMPLETED', 'FAILED');

-- Transaction reporting
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transaction_reporting ON transactions(
    user_id, 
    transaction_type, 
    date_trunc('month', timestamp)
) WHERE status = 'COMPLETED';

-- Balance calculations
CREATE INDEX CONCURRENT IF NOT EXISTS idx_balance_calculations ON ledger_entries(
    account_id, 
    transaction_timestamp DESC, 
    entry_type
) WHERE status = 'POSTED';

-- Fraud detection
CREATE INDEX CONCURRENT IF NOT EXISTS idx_fraud_detection ON transactions(
    user_id, 
    amount, 
    timestamp DESC
) WHERE status = 'COMPLETED' AND amount > 1000;

-- ==========================================================================
-- PARTIAL INDEXES FOR SELECTIVE QUERIES
-- ==========================================================================

-- Active users only
CREATE INDEX CONCURRENT IF NOT EXISTS idx_active_users ON users(id, email) WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- Pending transactions
CREATE INDEX CONCURRENT IF NOT EXISTS idx_pending_transactions ON transactions(id, user_id, created_at DESC) WHERE status = 'PENDING';

-- Failed payments for retry
CREATE INDEX CONCURRENT IF NOT EXISTS idx_failed_payments_retry ON payments(id, created_at DESC) WHERE status = 'FAILED' AND retry_count < 3;

-- Unread notifications
CREATE INDEX CONCURRENT IF NOT EXISTS idx_unread_notifications ON notifications(user_id, created_at DESC) WHERE read_at IS NULL;

-- Expired sessions
CREATE INDEX CONCURRENT IF NOT EXISTS idx_expired_sessions ON user_sessions(expires_at) WHERE expires_at < NOW();

-- ==========================================================================
-- FULL-TEXT SEARCH INDEXES
-- ==========================================================================

-- User search by name and email
CREATE INDEX CONCURRENT IF NOT EXISTS idx_users_fulltext ON users USING gin(to_tsvector('english', COALESCE(first_name, '') || ' ' || COALESCE(last_name, '') || ' ' || COALESCE(email, '')));

-- Transaction descriptions
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_description_fulltext ON transactions USING gin(to_tsvector('english', COALESCE(description, '')));

-- Merchant search
CREATE INDEX CONCURRENT IF NOT EXISTS idx_merchants_search ON merchants USING gin(to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(description, '')));

-- ==========================================================================
-- JSONB INDEXES FOR METADATA QUERIES
-- ==========================================================================

-- Transaction metadata
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_metadata_gin ON transactions USING gin(metadata) WHERE metadata IS NOT NULL;

-- Payment method metadata
CREATE INDEX CONCURRENT IF NOT EXISTS idx_payment_methods_metadata ON payment_methods USING gin(metadata) WHERE metadata IS NOT NULL;

-- User preferences
CREATE INDEX CONCURRENT IF NOT EXISTS idx_user_preferences_gin ON user_profiles USING gin(preferences) WHERE preferences IS NOT NULL;

-- ==========================================================================
-- TIME-SERIES INDEXES FOR ANALYTICS
-- ==========================================================================

-- Daily transaction volume
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_daily_volume ON transactions(
    date_trunc('day', timestamp), 
    sum(amount) OVER (PARTITION BY date_trunc('day', timestamp))
) WHERE status = 'COMPLETED';

-- Monthly user growth
CREATE INDEX CONCURRENT IF NOT EXISTS idx_users_monthly_growth ON users(
    date_trunc('month', created_at), 
    count(*) OVER (PARTITION BY date_trunc('month', created_at))
);

-- ==========================================================================
-- UNIQUE CONSTRAINTS WITH INDEXES
-- ==========================================================================

-- Prevent duplicate payment requests
CREATE UNIQUE INDEX CONCURRENT IF NOT EXISTS idx_unique_payment_requests ON payment_requests(requester_id, recipient_id, amount, created_at) WHERE status = 'PENDING';

-- Prevent duplicate device tokens
CREATE UNIQUE INDEX CONCURRENT IF NOT EXISTS idx_unique_device_tokens ON device_tokens(token, platform) WHERE active = true;

-- Prevent duplicate verification codes
CREATE UNIQUE INDEX CONCURRENT IF NOT EXISTS idx_unique_verification_codes ON verification_tokens(user_id, token_type) WHERE used_at IS NULL AND expires_at > NOW();

-- ==========================================================================
-- COVERING INDEXES FOR READ-HEAVY QUERIES
-- ==========================================================================

-- User profile with most common fields
CREATE INDEX CONCURRENT IF NOT EXISTS idx_users_profile_covering ON users(id) INCLUDE (email, first_name, last_name, status, created_at);

-- Wallet balance with common fields
CREATE INDEX CONCURRENT IF NOT EXISTS idx_wallets_covering ON wallets(user_id) INCLUDE (balance, currency, status, updated_at);

-- Transaction history covering
CREATE INDEX CONCURRENT IF NOT EXISTS idx_transactions_history_covering ON transactions(user_id, timestamp DESC) INCLUDE (amount, transaction_type, status, description);

-- ==========================================================================
-- OPTIMIZED VIEWS FOR COMMON QUERIES
-- ==========================================================================

-- User balance summary view
CREATE OR REPLACE VIEW user_balance_summary AS
SELECT 
    u.id as user_id,
    u.email,
    u.first_name,
    u.last_name,
    COALESCE(w.balance, 0) as wallet_balance,
    COALESCE(r.cashback_balance, 0) as cashback_balance,
    COALESCE(r.points_balance, 0) as points_balance,
    u.status,
    u.created_at
FROM users u
LEFT JOIN wallets w ON u.id = w.user_id AND w.status = 'ACTIVE'
LEFT JOIN rewards_accounts r ON u.id = r.user_id AND r.status = 'ACTIVE'
WHERE u.status = 'ACTIVE' AND u.deleted_at IS NULL;

-- Transaction summary view
CREATE OR REPLACE VIEW transaction_summary AS
SELECT 
    t.id,
    t.user_id,
    t.transaction_type,
    t.amount,
    t.status,
    t.timestamp,
    t.description,
    u.email as user_email,
    u.first_name || ' ' || u.last_name as user_name
FROM transactions t
INNER JOIN users u ON t.user_id = u.id
WHERE t.status = 'COMPLETED'
ORDER BY t.timestamp DESC;

-- Payment analytics view
CREATE OR REPLACE VIEW payment_analytics AS
SELECT 
    date_trunc('day', created_at) as payment_date,
    status,
    payment_method,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as average_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount
FROM payments
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY date_trunc('day', created_at), status, payment_method;

-- ==========================================================================
-- MATERIALIZED VIEWS FOR HEAVY ANALYTICS
-- ==========================================================================

-- Daily transaction metrics
CREATE MATERIALIZED VIEW IF NOT EXISTS daily_transaction_metrics AS
SELECT 
    date_trunc('day', timestamp) as metric_date,
    COUNT(*) as total_transactions,
    COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_transactions,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) as failed_transactions,
    SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END) as total_volume,
    AVG(CASE WHEN status = 'COMPLETED' THEN amount END) as average_amount,
    COUNT(DISTINCT user_id) as unique_users
FROM transactions
WHERE timestamp >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY date_trunc('day', timestamp)
ORDER BY metric_date DESC;

CREATE UNIQUE INDEX ON daily_transaction_metrics(metric_date);

-- User activity metrics
CREATE MATERIALIZED VIEW IF NOT EXISTS user_activity_metrics AS
SELECT 
    user_id,
    COUNT(*) as total_transactions,
    SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END) as total_volume,
    MAX(timestamp) as last_transaction_date,
    date_trunc('month', MAX(timestamp)) as last_active_month
FROM transactions
WHERE timestamp >= CURRENT_DATE - INTERVAL '180 days'
GROUP BY user_id;

CREATE UNIQUE INDEX ON user_activity_metrics(user_id);

-- ==========================================================================
-- REFRESH PROCEDURES FOR MATERIALIZED VIEWS
-- ==========================================================================

CREATE OR REPLACE FUNCTION refresh_analytics_views()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY daily_transaction_metrics;
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_activity_metrics;
    
    -- Log refresh
    INSERT INTO system_logs(log_type, message, created_at)
    VALUES ('ANALYTICS_REFRESH', 'Materialized views refreshed successfully', NOW());
END;
$$ LANGUAGE plpgsql;

-- Schedule refresh every hour
SELECT cron.schedule('refresh-analytics', '0 * * * *', 'SELECT refresh_analytics_views();');

-- ==========================================================================
-- QUERY PERFORMANCE MONITORING
-- ==========================================================================

-- Table for tracking slow queries
CREATE TABLE IF NOT EXISTS slow_query_log (
    id BIGSERIAL PRIMARY KEY,
    query_text TEXT NOT NULL,
    execution_time_ms BIGINT NOT NULL,
    user_name TEXT,
    database_name TEXT,
    table_names TEXT[],
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX ON slow_query_log(execution_time_ms DESC, created_at DESC);

-- Function to analyze query performance
CREATE OR REPLACE FUNCTION analyze_query_performance()
RETURNS TABLE(
    table_name TEXT,
    total_queries BIGINT,
    avg_execution_time NUMERIC,
    max_execution_time BIGINT,
    slow_query_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        unnest(sql.table_names) as table_name,
        COUNT(*) as total_queries,
        ROUND(AVG(sql.execution_time_ms), 2) as avg_execution_time,
        MAX(sql.execution_time_ms) as max_execution_time,
        COUNT(CASE WHEN sql.execution_time_ms > 1000 THEN 1 END) as slow_query_count
    FROM slow_query_log sql
    WHERE sql.created_at >= NOW() - INTERVAL '24 hours'
    GROUP BY unnest(sql.table_names)
    ORDER BY avg_execution_time DESC;
END;
$$ LANGUAGE plpgsql;

-- ==========================================================================
-- DATABASE MAINTENANCE PROCEDURES
-- ==========================================================================

-- Vacuum and analyze procedure
CREATE OR REPLACE FUNCTION maintain_database_performance()
RETURNS void AS $$
DECLARE
    table_record RECORD;
BEGIN
    -- Analyze all tables for better statistics
    FOR table_record IN 
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ANALYZE %I.%I', table_record.schemaname, table_record.tablename);
    END LOOP;
    
    -- Update table statistics
    ANALYZE;
    
    -- Log maintenance
    INSERT INTO system_logs(log_type, message, created_at)
    VALUES ('DB_MAINTENANCE', 'Database performance maintenance completed', NOW());
END;
$$ LANGUAGE plpgsql;

-- Schedule maintenance daily at 2 AM
SELECT cron.schedule('db-maintenance', '0 2 * * *', 'SELECT maintain_database_performance();');

-- ==========================================================================
-- CONNECTION POOL OPTIMIZATION
-- ==========================================================================

-- Set optimal connection pool settings
ALTER SYSTEM SET max_connections = 200;
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = 0.9;
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = 100;

-- ==========================================================================
-- QUERY OPTIMIZATION HINTS
-- ==========================================================================

-- Create extension for better performance monitoring
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Enable query plan optimization
SET enable_hashjoin = on;
SET enable_mergejoin = on;
SET enable_nestloop = on;
SET enable_seqscan = on;
SET enable_indexscan = on;
SET enable_indexonlyscan = on;

-- ==========================================================================
-- PERFORMANCE TESTING FUNCTIONS
-- ==========================================================================

-- Function to test query performance
CREATE OR REPLACE FUNCTION test_query_performance(test_query TEXT)
RETURNS TABLE(
    execution_time_ms NUMERIC,
    rows_returned BIGINT,
    query_plan TEXT
) AS $$
DECLARE
    start_time TIMESTAMP;
    end_time TIMESTAMP;
    row_count BIGINT;
    plan_text TEXT;
BEGIN
    -- Get query plan
    EXECUTE format('EXPLAIN (FORMAT JSON) %s', test_query) INTO plan_text;
    
    -- Execute query and measure time
    start_time := clock_timestamp();
    EXECUTE format('SELECT COUNT(*) FROM (%s) AS subquery', test_query) INTO row_count;
    end_time := clock_timestamp();
    
    RETURN QUERY SELECT 
        EXTRACT(EPOCH FROM (end_time - start_time)) * 1000 as execution_time_ms,
        row_count as rows_returned,
        plan_text as query_plan;
END;
$$ LANGUAGE plpgsql;

-- ==========================================================================
-- PERFORMANCE ALERTS
-- ==========================================================================

-- Function to check for performance issues
CREATE OR REPLACE FUNCTION check_performance_alerts()
RETURNS void AS $$
DECLARE
    slow_queries_count INTEGER;
    large_table_count INTEGER;
    blocked_queries_count INTEGER;
BEGIN
    -- Check for slow queries
    SELECT COUNT(*) INTO slow_queries_count
    FROM slow_query_log
    WHERE created_at >= NOW() - INTERVAL '1 hour'
    AND execution_time_ms > 5000;
    
    -- Check for blocked queries
    SELECT COUNT(*) INTO blocked_queries_count
    FROM pg_stat_activity
    WHERE state = 'active' AND waiting = true;
    
    -- Check for large tables without recent analysis
    SELECT COUNT(*) INTO large_table_count
    FROM pg_stat_user_tables
    WHERE n_tup_ins + n_tup_upd + n_tup_del > 10000
    AND (last_analyze IS NULL OR last_analyze < NOW() - INTERVAL '1 day');
    
    -- Send alerts if thresholds exceeded
    IF slow_queries_count > 10 THEN
        INSERT INTO system_alerts(alert_type, message, severity, created_at)
        VALUES ('PERFORMANCE', format('High number of slow queries: %s', slow_queries_count), 'WARNING', NOW());
    END IF;
    
    IF blocked_queries_count > 5 THEN
        INSERT INTO system_alerts(alert_type, message, severity, created_at)
        VALUES ('PERFORMANCE', format('High number of blocked queries: %s', blocked_queries_count), 'CRITICAL', NOW());
    END IF;
    
    IF large_table_count > 0 THEN
        INSERT INTO system_alerts(alert_type, message, severity, created_at)
        VALUES ('MAINTENANCE', format('Tables need analysis: %s', large_table_count), 'INFO', NOW());
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Schedule performance checks every 15 minutes
SELECT cron.schedule('performance-check', '*/15 * * * *', 'SELECT check_performance_alerts();');

COMMIT;