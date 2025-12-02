-- =====================================================================
-- Database Performance Optimization: Comprehensive Index Strategy
-- =====================================================================
-- This migration creates optimized indexes for critical financial queries
-- across all high-volume tables in the Waqiti financial application.

-- =====================================================================
-- ACCOUNTS TABLE OPTIMIZATION
-- =====================================================================

-- Primary account lookup indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_user_id_status_idx 
    ON accounts (user_id, status) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_account_number_hash_idx 
    ON accounts USING HASH (account_number);

CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_account_type_currency_idx 
    ON accounts (account_type, currency, status);

-- Balance-based indexes for financial queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_available_balance_idx 
    ON accounts (available_balance) WHERE available_balance > 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_low_balance_idx 
    ON accounts (current_balance, minimum_balance) 
    WHERE current_balance < minimum_balance AND status = 'ACTIVE';

-- Activity-based indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_last_transaction_date_idx 
    ON accounts (last_transaction_date DESC) WHERE last_transaction_date IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_dormant_idx 
    ON accounts (last_transaction_date, status) 
    WHERE last_transaction_date < CURRENT_DATE - INTERVAL '90 days' AND status = 'ACTIVE';

-- Compliance and KYC indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_compliance_level_idx 
    ON accounts (compliance_level, last_kyc_update) 
    WHERE compliance_level IN ('RESTRICTED', 'MONITORED', 'BLOCKED');

CREATE INDEX CONCURRENTLY IF NOT EXISTS accounts_kyc_update_due_idx 
    ON accounts (last_kyc_update, compliance_level) 
    WHERE last_kyc_update < CURRENT_DATE - INTERVAL '1 year';

-- =====================================================================
-- PAYMENT TABLES OPTIMIZATION
-- =====================================================================

-- Create indexes for payment orchestration service
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payments' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS payments_user_id_status_idx 
            ON payments (user_id, status, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS payments_amount_currency_idx 
            ON payments (amount, currency) WHERE amount > 1000;
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS payments_status_processing_idx 
            ON payments (status, created_at) 
            WHERE status IN ('PENDING', 'PROCESSING', 'REQUIRES_APPROVAL');
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS payments_external_reference_idx 
            ON payments (external_reference) WHERE external_reference IS NOT NULL;
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS payments_merchant_id_idx 
            ON payments (merchant_id, status) WHERE merchant_id IS NOT NULL;
    END IF;
END $$;

-- Recurring payments optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'recurring_payments' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS recurring_payments_next_execution_idx 
            ON recurring_payments (next_execution_date, status) 
            WHERE status = 'ACTIVE' AND next_execution_date <= CURRENT_DATE + INTERVAL '7 days';
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS recurring_payments_user_status_idx 
            ON recurring_payments (user_id, status, created_at DESC);
    END IF;
END $$;

-- =====================================================================
-- INVESTMENT TABLES OPTIMIZATION
-- =====================================================================

-- Investment orders optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'investment_orders' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS investment_orders_user_status_idx 
            ON investment_orders (user_id, status, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS investment_orders_symbol_type_idx 
            ON investment_orders (symbol, order_type, status);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS investment_orders_pending_idx 
            ON investment_orders (status, created_at) 
            WHERE status IN ('PENDING', 'PARTIALLY_FILLED');
    END IF;
END $$;

-- Portfolio holdings optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'investment_holdings' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS investment_holdings_portfolio_symbol_idx 
            ON investment_holdings (portfolio_id, symbol);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS investment_holdings_user_updated_idx 
            ON investment_holdings (user_id, last_updated DESC);
    END IF;
END $$;

-- =====================================================================
-- CRYPTO TABLES OPTIMIZATION
-- =====================================================================

-- Crypto transactions optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'crypto_transactions' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS crypto_transactions_user_currency_idx 
            ON crypto_transactions (user_id, cryptocurrency, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS crypto_transactions_status_idx 
            ON crypto_transactions (status, created_at) 
            WHERE status IN ('PENDING', 'PROCESSING');
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS crypto_transactions_large_amount_idx 
            ON crypto_transactions (amount, cryptocurrency) WHERE amount > 1000;
    END IF;
END $$;

-- Crypto price history optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'crypto_price_history' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS crypto_price_history_symbol_timestamp_idx 
            ON crypto_price_history (symbol, timestamp DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS crypto_price_history_recent_idx 
            ON crypto_price_history (symbol, timestamp DESC) 
            WHERE timestamp > CURRENT_TIMESTAMP - INTERVAL '30 days';
    END IF;
END $$;

-- =====================================================================
-- COMPLIANCE AND SECURITY OPTIMIZATION
-- =====================================================================

-- Compliance alerts optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'compliance_alerts' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS compliance_alerts_severity_status_idx 
            ON compliance_alerts (severity, status, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS compliance_alerts_user_entity_idx 
            ON compliance_alerts (entity_id, entity_type, status);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS compliance_alerts_unresolved_idx 
            ON compliance_alerts (status, created_at) 
            WHERE status IN ('OPEN', 'IN_PROGRESS');
    END IF;
END $$;

-- Suspicious activity optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'suspicious_activities' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS suspicious_activities_risk_score_idx 
            ON suspicious_activities (risk_score DESC, detected_at DESC) 
            WHERE risk_score >= 70;
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS suspicious_activities_user_status_idx 
            ON suspicious_activities (user_id, status, detected_at DESC);
    END IF;
END $$;

-- =====================================================================
-- MESSAGING AND NOTIFICATIONS OPTIMIZATION
-- =====================================================================

-- Message optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'messages' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS messages_conversation_timestamp_idx 
            ON messages (conversation_id, timestamp DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS messages_sender_timestamp_idx 
            ON messages (sender_id, timestamp DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS messages_unread_idx 
            ON messages (conversation_id, is_read, timestamp DESC) WHERE is_read = FALSE;
    END IF;
END $$;

-- Notification optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'push_notification_logs' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS push_notifications_user_status_idx 
            ON push_notification_logs (user_id, status, sent_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS push_notifications_failed_idx 
            ON push_notification_logs (status, sent_at) 
            WHERE status = 'FAILED' AND sent_at > CURRENT_TIMESTAMP - INTERVAL '24 hours';
    END IF;
END $$;

-- =====================================================================
-- BUSINESS AND FAMILY ACCOUNTS OPTIMIZATION
-- =====================================================================

-- Business accounts optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'business_accounts' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS business_accounts_owner_status_idx 
            ON business_accounts (owner_user_id, status, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS business_accounts_business_type_idx 
            ON business_accounts (business_type, status);
    END IF;
END $$;

-- Family accounts optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'family_accounts' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS family_accounts_head_status_idx 
            ON family_accounts (head_user_id, status, created_at DESC);
    END IF;
    
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'family_members' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS family_members_user_family_idx 
            ON family_members (user_id, family_account_id, status);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS family_members_family_role_idx 
            ON family_members (family_account_id, role, status);
    END IF;
END $$;

-- =====================================================================
-- REPORTING AND ANALYTICS OPTIMIZATION
-- =====================================================================

-- Financial data optimization for reporting
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'financial_data' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS financial_data_user_period_idx 
            ON financial_data (user_id, reporting_period, data_type);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS financial_data_date_type_idx 
            ON financial_data (reporting_date, data_type, user_id);
    END IF;
END $$;

-- Report execution optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'report_executions' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS report_executions_status_date_idx 
            ON report_executions (status, execution_date DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS report_executions_user_report_idx 
            ON report_executions (user_id, report_definition_id, execution_date DESC);
    END IF;
END $$;

-- =====================================================================
-- EXCHANGE RATE AND CURRENCY OPTIMIZATION
-- =====================================================================

-- Exchange rate history optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'exchange_rate_history' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS exchange_rate_history_pair_date_idx 
            ON exchange_rate_history (base_currency, target_currency, rate_date DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS exchange_rate_history_recent_idx 
            ON exchange_rate_history (rate_date DESC, base_currency, target_currency) 
            WHERE rate_date > CURRENT_DATE - INTERVAL '7 days';
    END IF;
END $$;

-- =====================================================================
-- SOCIAL AND GAMIFICATION OPTIMIZATION
-- =====================================================================

-- Social connections optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'social_connections' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS social_connections_user_status_idx 
            ON social_connections (user_id, status, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS social_connections_friend_status_idx 
            ON social_connections (friend_user_id, status, created_at DESC);
    END IF;
END $$;

-- Gamification optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'user_points' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS user_points_user_total_idx 
            ON user_points (user_id, total_points DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS user_points_leaderboard_idx 
            ON user_points (total_points DESC, last_updated DESC) WHERE total_points > 0;
    END IF;
END $$;

-- =====================================================================
-- INTERNATIONAL TRANSFER OPTIMIZATION
-- =====================================================================

-- International transfers optimization
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'international_transfers' AND table_schema = 'public') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS international_transfers_user_status_idx 
            ON international_transfers (sender_user_id, status, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS international_transfers_recipient_idx 
            ON international_transfers (recipient_user_id, status, created_at DESC);
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS international_transfers_compliance_idx 
            ON international_transfers (compliance_status, created_at) 
            WHERE compliance_status IN ('PENDING_REVIEW', 'REQUIRES_DOCUMENTATION');
        
        CREATE INDEX CONCURRENTLY IF NOT EXISTS international_transfers_amount_idx 
            ON international_transfers (amount, source_currency) WHERE amount > 10000;
    END IF;
END $$;

-- =====================================================================
-- PERFORMANCE MONITORING INDEXES
-- =====================================================================

-- Create indexes for monitoring slow queries and performance
CREATE TABLE IF NOT EXISTS query_performance_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_hash VARCHAR(64) NOT NULL,
    query_text TEXT,
    execution_time_ms BIGINT NOT NULL,
    rows_examined BIGINT,
    rows_returned BIGINT,
    table_name VARCHAR(100),
    index_used VARCHAR(100),
    execution_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id UUID,
    connection_info JSONB
);

CREATE INDEX IF NOT EXISTS query_performance_log_hash_time_idx 
    ON query_performance_log (query_hash, execution_time_ms DESC);

CREATE INDEX IF NOT EXISTS query_performance_log_slow_queries_idx 
    ON query_performance_log (execution_time_ms DESC, execution_date DESC) 
    WHERE execution_time_ms > 5000; -- Queries slower than 5 seconds

CREATE INDEX IF NOT EXISTS query_performance_log_table_performance_idx 
    ON query_performance_log (table_name, execution_time_ms DESC) 
    WHERE table_name IS NOT NULL;

-- =====================================================================
-- MAINTENANCE FUNCTIONS
-- =====================================================================

-- Function to analyze index usage and suggest optimizations
CREATE OR REPLACE FUNCTION analyze_index_usage()
RETURNS TABLE (
    schemaname TEXT,
    tablename TEXT,
    indexname TEXT,
    index_size TEXT,
    index_scans BIGINT,
    tuples_read BIGINT,
    tuples_fetched BIGINT,
    usage_ratio DECIMAL(5,2),
    recommendation TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        s.schemaname::TEXT,
        s.tablename::TEXT,
        s.indexname::TEXT,
        pg_size_pretty(pg_total_relation_size(s.indexrelid))::TEXT as index_size,
        s.idx_scan as index_scans,
        s.idx_tup_read as tuples_read,
        s.idx_tup_fetch as tuples_fetched,
        CASE 
            WHEN s.idx_scan = 0 THEN 0 
            ELSE ROUND((s.idx_tup_fetch::DECIMAL / NULLIF(s.idx_scan, 0)) * 100, 2) 
        END as usage_ratio,
        CASE 
            WHEN s.idx_scan = 0 THEN 'Consider dropping - unused index'
            WHEN s.idx_scan < 100 AND pg_total_relation_size(s.indexrelid) > 1048576 THEN 'Low usage for large index - review necessity'
            WHEN s.idx_tup_read > s.idx_tup_fetch * 100 THEN 'High read/fetch ratio - may need optimization'
            ELSE 'Index usage appears normal'
        END::TEXT as recommendation
    FROM pg_stat_user_indexes s
    JOIN pg_index i ON s.indexrelid = i.indexrelid
    WHERE NOT i.indisunique  -- Exclude unique indexes (usually necessary)
    ORDER BY pg_total_relation_size(s.indexrelid) DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to identify missing indexes based on query patterns
CREATE OR REPLACE FUNCTION suggest_missing_indexes()
RETURNS TABLE (
    table_name TEXT,
    suggested_columns TEXT,
    reasoning TEXT,
    priority INTEGER
) AS $$
BEGIN
    RETURN QUERY
    WITH slow_queries AS (
        SELECT DISTINCT 
            qpl.table_name,
            qpl.query_text,
            AVG(qpl.execution_time_ms) as avg_time,
            COUNT(*) as frequency
        FROM query_performance_log qpl
        WHERE qpl.execution_date > CURRENT_TIMESTAMP - INTERVAL '7 days'
        AND qpl.execution_time_ms > 1000  -- Queries slower than 1 second
        AND qpl.table_name IS NOT NULL
        GROUP BY qpl.table_name, qpl.query_text
        HAVING COUNT(*) > 10  -- Frequent slow queries
    )
    SELECT 
        sq.table_name::TEXT,
        'Review query patterns for indexing opportunities'::TEXT as suggested_columns,
        ('Frequent slow query (avg: ' || ROUND(sq.avg_time) || 'ms, count: ' || sq.frequency || ')')::TEXT as reasoning,
        CASE 
            WHEN sq.avg_time > 10000 THEN 1  -- Very slow
            WHEN sq.avg_time > 5000 THEN 2   -- Slow
            ELSE 3  -- Moderate
        END as priority
    FROM slow_queries sq
    ORDER BY priority, sq.avg_time DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to update table statistics for better query planning
CREATE OR REPLACE FUNCTION update_table_statistics()
RETURNS VOID AS $$
DECLARE
    table_record RECORD;
BEGIN
    -- Update statistics for all user tables
    FOR table_record IN
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE schemaname = 'public'
        AND tablename NOT LIKE '%_backup%'
    LOOP
        EXECUTE format('ANALYZE %I.%I', table_record.schemaname, table_record.tablename);
        RAISE NOTICE 'Updated statistics for table: %.%', table_record.schemaname, table_record.tablename;
    END LOOP;
    
    RAISE NOTICE 'Table statistics update completed';
END;
$$ LANGUAGE plpgsql;

-- Grant permissions
GRANT EXECUTE ON FUNCTION analyze_index_usage() TO waqiti_user;
GRANT EXECUTE ON FUNCTION suggest_missing_indexes() TO waqiti_user;
GRANT EXECUTE ON FUNCTION update_table_statistics() TO waqiti_user;
GRANT SELECT, INSERT ON query_performance_log TO waqiti_user;

-- Create comments
COMMENT ON FUNCTION analyze_index_usage() IS 'Analyzes index usage patterns and provides optimization recommendations';
COMMENT ON FUNCTION suggest_missing_indexes() IS 'Suggests missing indexes based on slow query patterns';
COMMENT ON FUNCTION update_table_statistics() IS 'Updates table statistics for better query planning';
COMMENT ON TABLE query_performance_log IS 'Logs query performance metrics for analysis and optimization';

-- Final notice
SELECT 'Database index optimization completed. Use analyze_index_usage() and suggest_missing_indexes() for ongoing optimization.' as status;