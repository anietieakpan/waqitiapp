-- High-Volume Table Indexes for Performance Optimization
-- These indexes target the highest-traffic tables and most common query patterns

-- ============================================================================
-- TRANSACTIONS TABLE (Highest Volume)
-- ============================================================================

-- Primary query patterns: user lookups, merchant lookups, date ranges, status filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_id_status_date 
ON transactions (user_id, status, created_at DESC) 
INCLUDE (amount, currency, merchant_id, reference_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_merchant_id_status_date 
ON transactions (merchant_id, status, created_at DESC) 
INCLUDE (amount, currency, user_id, reference_id);

-- For transaction searches and filters
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reference_id 
ON transactions (reference_id) WHERE reference_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount_range_date 
ON transactions (created_at DESC, amount) WHERE status = 'COMPLETED';

-- For compliance and reporting queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_high_value 
ON transactions (amount, created_at DESC) WHERE amount >= 10000;

-- For analytics queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_daily_aggregation 
ON transactions (date_trunc('day', created_at), currency, status) 
INCLUDE (amount, merchant_id);

-- ============================================================================
-- USERS TABLE (High Volume)
-- ============================================================================

-- Login and authentication queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_status 
ON users (email, status) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_phone_status 
ON users (phone_number, status) WHERE status = 'ACTIVE' AND phone_number IS NOT NULL;

-- User management queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_created_at_status 
ON users (created_at DESC, status) INCLUDE (username, email, last_login_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_last_login 
ON users (last_login_at DESC) WHERE status = 'ACTIVE';

-- KYC status queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_kyc_status_date 
ON users (kyc_status, created_at) WHERE kyc_status IN ('PENDING', 'VERIFICATION_REQUIRED');

-- ============================================================================
-- MERCHANTS TABLE (High Volume)
-- ============================================================================

-- Merchant discovery and search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_status_category_city 
ON merchants (status, business_category, business_address_city) 
WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_verification_status_date 
ON merchants (verification_status, created_at DESC) 
INCLUDE (business_name, business_email, user_id);

-- High-volume merchant queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_total_volume_status 
ON merchants (total_volume DESC, status) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_last_transaction 
ON merchants (last_transaction_at DESC) WHERE status = 'ACTIVE';

-- ============================================================================
-- PAYMENTS TABLE (Very High Volume)
-- ============================================================================

-- Payment processing queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_status_created_at 
ON payments (status, created_at DESC) 
INCLUDE (user_id, amount, payment_method_id, transaction_id);

-- Payment method queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_payment_method_date 
ON payments (payment_method_id, created_at DESC) 
INCLUDE (amount, status, user_id);

-- Failed payment analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_failed_reason 
ON payments (failure_reason, created_at DESC) 
WHERE status = 'FAILED' AND failure_reason IS NOT NULL;

-- Retry and webhook queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_retry_count 
ON payments (retry_count, status, created_at) 
WHERE retry_count > 0;

-- ============================================================================
-- NOTIFICATIONS TABLE (Very High Volume)
-- ============================================================================

-- User notification queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_id_unread_date 
ON notifications (user_id, is_read, created_at DESC) 
INCLUDE (type, title, priority);

-- Notification delivery status
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_delivery_status 
ON notifications (delivery_status, created_at) 
WHERE delivery_status IN ('PENDING', 'FAILED');

-- Notification type analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_type_date 
ON notifications (type, created_at DESC) 
INCLUDE (user_id, delivery_status);

-- ============================================================================
-- AUDIT_EVENTS TABLE (Extremely High Volume)
-- ============================================================================

-- Audit queries by entity
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_entity_type_id_date 
ON audit_events (entity_type, entity_id, timestamp DESC) 
INCLUDE (event_type, user_id, details);

-- Audit queries by user
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_user_id_date 
ON audit_events (user_id, timestamp DESC) 
INCLUDE (entity_type, event_type, entity_id);

-- Security audit queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_security_events 
ON audit_events (event_type, timestamp DESC) 
WHERE event_type IN ('LOGIN_FAILED', 'SUSPICIOUS_ACTIVITY', 'FRAUD_DETECTED');

-- Compliance reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_compliance_date 
ON audit_events (date_trunc('day', timestamp), entity_type) 
INCLUDE (event_type, entity_id, user_id);

-- ============================================================================
-- WALLET_TRANSACTIONS TABLE (High Volume)
-- ============================================================================

-- Wallet balance calculations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_wallet_id_date 
ON wallet_transactions (wallet_id, created_at DESC) 
INCLUDE (type, amount, balance_after, status);

-- Transaction type analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_type_date 
ON wallet_transactions (type, created_at DESC) 
INCLUDE (wallet_id, amount, status);

-- Balance reconciliation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_status_date 
ON wallet_transactions (status, created_at) 
WHERE status IN ('PENDING', 'FAILED');

-- ============================================================================
-- MERCHANT_TRANSACTIONS TABLE (High Volume)
-- ============================================================================

-- Merchant settlement queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_transactions_settlement_date 
ON merchant_transactions (merchant_id, settlement_date) 
WHERE settlement_status = 'PENDING' AND settlement_date IS NOT NULL;

-- Merchant analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_transactions_merchant_date_amount 
ON merchant_transactions (merchant_id, created_at DESC) 
INCLUDE (amount, status, fee_amount, net_amount);

-- Chargeback and dispute queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_transactions_chargeback 
ON merchant_transactions (chargeback_status, created_at DESC) 
WHERE chargeback_status IS NOT NULL;

-- ============================================================================
-- SESSION_TOKENS TABLE (High Volume - Authentication)
-- ============================================================================

-- Active session lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_session_tokens_token_hash 
ON session_tokens (token_hash) WHERE is_revoked = false;

-- Session cleanup queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_session_tokens_expires_at 
ON session_tokens (expires_at) WHERE is_revoked = false;

-- User session management
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_session_tokens_user_id_active 
ON session_tokens (user_id, created_at DESC) 
WHERE is_revoked = false;

-- ============================================================================
-- FRAUD_EVENTS TABLE (High Volume - Security)
-- ============================================================================

-- Real-time fraud detection
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_events_user_id_date_score 
ON fraud_events (user_id, created_at DESC, risk_score) 
WHERE status = 'DETECTED';

-- Fraud pattern analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_events_type_date 
ON fraud_events (fraud_type, created_at DESC) 
INCLUDE (user_id, risk_score, transaction_id);

-- High-risk user identification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_events_high_risk 
ON fraud_events (risk_score DESC, created_at DESC) 
WHERE risk_score >= 75;

-- ============================================================================
-- RATE_LIMIT_TRACKING TABLE (Very High Volume)
-- ============================================================================

-- Rate limiting lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rate_limit_tracking_key_window 
ON rate_limit_tracking (limit_key, time_window) 
INCLUDE (request_count, last_request_at);

-- Rate limit cleanup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rate_limit_tracking_cleanup 
ON rate_limit_tracking (last_request_at) WHERE last_request_at < NOW() - INTERVAL '1 hour';

-- ============================================================================
-- API_LOGS TABLE (Extremely High Volume)
-- ============================================================================

-- API performance monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_api_logs_endpoint_date_response_time 
ON api_logs (endpoint, created_at DESC, response_time_ms) 
INCLUDE (status_code, user_id);

-- Error tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_api_logs_error_tracking 
ON api_logs (status_code, created_at DESC) 
WHERE status_code >= 400;

-- User activity tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_api_logs_user_id_date 
ON api_logs (user_id, created_at DESC) 
WHERE user_id IS NOT NULL;

-- ============================================================================
-- PUSH_NOTIFICATIONS TABLE (High Volume)
-- ============================================================================

-- Push notification delivery
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_push_notifications_device_token 
ON push_notifications (device_token, created_at DESC) 
INCLUDE (status, user_id, message_type);

-- Failed notification retry
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_push_notifications_failed_retry 
ON push_notifications (status, retry_count, created_at) 
WHERE status = 'FAILED' AND retry_count < 3;

-- ============================================================================
-- PERFORMANCE MONITORING AND MAINTENANCE
-- ============================================================================

-- Create function to monitor index usage
CREATE OR REPLACE FUNCTION get_unused_indexes() 
RETURNS TABLE(
    schemaname TEXT,
    tablename TEXT,
    indexname TEXT,
    index_size TEXT,
    index_scans BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        s.schemaname::TEXT,
        s.tablename::TEXT,
        s.indexname::TEXT,
        pg_size_pretty(pg_relation_size(s.indexname::regclass))::TEXT as index_size,
        s.idx_scan as index_scans
    FROM pg_stat_user_indexes s
    JOIN pg_index i ON s.indexrelid = i.indexrelid
    WHERE s.idx_scan < 100  -- Indexes used less than 100 times
    AND NOT i.indisunique   -- Exclude unique indexes
    AND s.indexname NOT LIKE '%_pkey'  -- Exclude primary keys
    ORDER BY s.idx_scan ASC, pg_relation_size(s.indexname::regclass) DESC;
END;
$$ LANGUAGE plpgsql;

-- Create function to identify slow queries
CREATE OR REPLACE FUNCTION get_slow_queries(min_duration_ms INTEGER DEFAULT 1000)
RETURNS TABLE(
    query TEXT,
    calls BIGINT,
    total_time_ms NUMERIC,
    mean_time_ms NUMERIC,
    rows_per_call NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        pg_stat_statements.query::TEXT,
        pg_stat_statements.calls,
        pg_stat_statements.total_exec_time::NUMERIC as total_time_ms,
        pg_stat_statements.mean_exec_time::NUMERIC as mean_time_ms,
        (pg_stat_statements.rows::NUMERIC / pg_stat_statements.calls) as rows_per_call
    FROM pg_stat_statements
    WHERE pg_stat_statements.mean_exec_time > min_duration_ms
    ORDER BY pg_stat_statements.mean_exec_time DESC
    LIMIT 20;
END;
$$ LANGUAGE plpgsql;

-- Comments for maintenance and monitoring
COMMENT ON INDEX idx_transactions_user_id_status_date IS 'Primary index for user transaction queries - monitor usage';
COMMENT ON INDEX idx_merchants_status_category_city IS 'Merchant search optimization - critical for discovery';
COMMENT ON INDEX idx_audit_events_entity_type_id_date IS 'Audit trail queries - compliance critical';
COMMENT ON INDEX idx_notifications_user_id_unread_date IS 'User notification center - high traffic';
COMMENT ON INDEX idx_payments_status_created_at IS 'Payment processing queries - transaction critical';

-- Final table analysis for query planner optimization
ANALYZE transactions;
ANALYZE users;
ANALYZE merchants;
ANALYZE payments;
ANALYZE notifications;
ANALYZE audit_events;
ANALYZE wallet_transactions;
ANALYZE merchant_transactions;
ANALYZE session_tokens;
ANALYZE fraud_events;
ANALYZE rate_limit_tracking;
ANALYZE api_logs;
ANALYZE push_notifications;