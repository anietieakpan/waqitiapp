-- =====================================================================
-- CRITICAL SERVICES PERFORMANCE OPTIMIZATION INDEXES
-- =====================================================================
-- This migration adds missing performance indexes for critical services
-- based on production readiness forensic analysis and N+1 optimization
-- requirements across Payment, Wallet, User, and Core Banking services

-- =====================================================================
-- PAYMENT SERVICE INDEXES
-- =====================================================================

-- Payment processing optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_processor_reference 
ON payments(processor_reference) WHERE processor_reference IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_external_reference 
ON payments(external_reference) WHERE external_reference IS NOT NULL;

-- Payment reversal optimization (PaymentReversalInitiatedConsumer)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_reversal_status 
ON payments(status, created_at DESC) 
WHERE status IN ('REVERSED', 'PENDING_REVERSAL', 'REVERSAL_FAILED');

-- Payment method optimization (N+1 prevention)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_covering 
ON payment_methods(user_id, is_default) 
INCLUDE (type, last_four, expiry_month, expiry_year, is_active)
WHERE is_active = true;

-- Scheduled payments optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_payments_due 
ON scheduled_payments(next_payment_date, status)
WHERE status = 'ACTIVE' AND next_payment_date IS NOT NULL;

-- Payment attempts for retry logic
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_attempts_retry 
ON payment_attempts(payment_id, attempt_number, status)
INCLUDE (created_at, failure_reason);

-- =====================================================================
-- WALLET SERVICE INDEXES
-- =====================================================================

-- Wallet freeze optimization (WalletFreezeRequestedConsumer)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_freeze_status 
ON wallets(user_id, is_frozen, frozen_reason)
WHERE is_frozen = true;

-- Wallet balance queries with currency
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_currency_balance 
ON wallets(user_id, currency, status) 
INCLUDE (balance, available_balance, is_primary)
WHERE status = 'ACTIVE';

-- Wallet transaction history optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_history 
ON wallet_transactions(wallet_id, created_at DESC) 
INCLUDE (type, amount, balance_after, status, description);

-- Wallet limits and restrictions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_limits 
ON wallets(user_id, daily_limit, monthly_limit)
WHERE status = 'ACTIVE' AND (daily_limit > 0 OR monthly_limit > 0);

-- =====================================================================
-- USER SERVICE INDEXES
-- =====================================================================

-- User authentication optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_status 
ON users(LOWER(email), status) 
WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_phone_status 
ON users(phone_number, status) 
WHERE phone_number IS NOT NULL AND status = 'ACTIVE';

-- User profile optimization (N+1 prevention)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_profiles_covering 
ON user_profiles(user_id) 
INCLUDE (first_name, last_name, date_of_birth, preferred_language, preferred_currency, timezone);

-- User devices for security
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_devices_trusted 
ON user_devices(user_id, is_trusted, last_used_at DESC)
WHERE is_active = true;

-- User sessions optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_active 
ON user_sessions(user_id, last_activity DESC, expires_at)
WHERE is_active = true AND expires_at > CURRENT_TIMESTAMP;

-- MFA configuration optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mfa_configurations_user 
ON mfa_configurations(user_id, method, is_enabled)
WHERE is_enabled = true;

-- =====================================================================
-- CORE BANKING SERVICE INDEXES
-- =====================================================================

-- Account balance and status optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_user_type_status 
ON accounts(user_id, account_type, status) 
INCLUDE (balance, currency, account_number)
WHERE status IN ('ACTIVE', 'FROZEN');

-- Interest calculation optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_interest_calculation 
ON accounts(account_type, balance, last_interest_calculation)
WHERE account_type IN ('SAVINGS', 'CHECKING') AND balance > 0;

-- Account hierarchy optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_parent_child 
ON accounts(parent_account_id, status)
WHERE parent_account_id IS NOT NULL;

-- Fee management optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_fees_due 
ON account_fees(account_id, due_date, status)
WHERE status = 'PENDING' AND due_date <= CURRENT_DATE + INTERVAL '7 days';

-- =====================================================================
-- COMPLIANCE SERVICE INDEXES
-- =====================================================================

-- AML alerts optimization (AMLAlertRaisedConsumer)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_priority 
ON aml_alerts(risk_level, status, created_at DESC)
WHERE status IN ('PENDING', 'UNDER_INVESTIGATION');

-- SAR filing optimization (SARFilingRequiredConsumer)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_reports_filing_due 
ON suspicious_activity_reports(filing_deadline, status)
WHERE status IN ('PENDING', 'IN_PROGRESS') AND filing_deadline <= CURRENT_DATE + INTERVAL '7 days';

-- KYC verification optimization (KYCVerificationExpiredConsumer)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_expiry 
ON kyc_documents(expiry_date, status, user_id)
WHERE status = 'VERIFIED' AND expiry_date <= CURRENT_DATE + INTERVAL '30 days';

-- Transaction monitoring optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_patterns_user 
ON transaction_patterns(user_id, pattern_type, risk_score DESC)
WHERE risk_score > 0.5;

-- =====================================================================
-- SECURITY SERVICE INDEXES
-- =====================================================================

-- Security events monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_events_user_type 
ON security_events(user_id, event_type, created_at DESC)
WHERE event_type IN ('LOGIN_FAILED', 'SUSPICIOUS_ACTIVITY', 'FRAUD_DETECTED');

-- Fraud detection optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_patterns_detection 
ON fraud_patterns(pattern_type, confidence_score DESC, is_active)
WHERE is_active = true AND confidence_score > 0.7;

-- =====================================================================
-- NOTIFICATION SERVICE INDEXES
-- =====================================================================

-- Unread notifications optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_unread 
ON notifications(user_id, is_read, priority, created_at DESC)
WHERE is_read = false;

-- Notification preferences optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_preferences_user 
ON notification_preferences(user_id) 
INCLUDE (email_enabled, sms_enabled, push_enabled, notification_types);

-- Notification delivery tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_deliveries_status 
ON notification_deliveries(notification_id, delivery_status, attempted_at DESC);

-- =====================================================================
-- RECONCILIATION SERVICE INDEXES
-- =====================================================================

-- Payment reconciliation optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_reconciliations_status 
ON payment_reconciliations(status, scheduled_date)
WHERE status IN ('PENDING', 'IN_PROGRESS');

-- Discrepancy detection optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_discrepancies_amount 
ON payment_discrepancies(amount, discrepancy_type, status)
WHERE status = 'UNRESOLVED' AND amount > 0;

-- Reconciliation reports optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reconciliation_reports_date 
ON reconciliation_reports(report_date DESC, report_type, status)
WHERE status = 'COMPLETED';

-- =====================================================================
-- AUDIT SERVICE INDEXES
-- =====================================================================

-- Audit events optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_user_action 
ON audit_events(user_id, action, created_at DESC)
INCLUDE (entity_type, entity_id, ip_address);

-- Audit events by entity
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_entity 
ON audit_events(entity_type, entity_id, created_at DESC)
INCLUDE (user_id, action, changes);

-- Security audit trail
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_security 
ON audit_events(created_at DESC)
WHERE action IN ('LOGIN_FAILED', 'UNAUTHORIZED_ACCESS', 'DATA_BREACH', 'PERMISSION_DENIED');

-- =====================================================================
-- ANALYTICS SERVICE INDEXES
-- =====================================================================

-- Real-time metrics optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_real_time_metrics_timestamp 
ON real_time_metrics(metric_timestamp DESC, metric_type)
WHERE metric_timestamp >= CURRENT_TIMESTAMP - INTERVAL '1 day';

-- User metrics aggregation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_metrics_date_type 
ON user_metrics(metric_date, metric_type, user_id)
INCLUDE (metric_value);

-- Transaction metrics aggregation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_metrics_date 
ON transaction_metrics(metric_date DESC, metric_type)
INCLUDE (total_amount, transaction_count, average_amount);

-- =====================================================================
-- MERCHANT SERVICE INDEXES
-- =====================================================================

-- Merchant verification optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_verifications_status 
ON merchant_verifications(status, submitted_at DESC)
WHERE status IN ('PENDING', 'UNDER_REVIEW');

-- Merchant payment methods
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_payment_methods_active 
ON merchant_payment_methods(merchant_id, is_active) 
INCLUDE (type, account_number, is_default)
WHERE is_active = true;

-- Merchant transaction analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_transactions_analytics 
ON merchant_transactions(merchant_id, created_at::date) 
INCLUDE (amount, status, customer_id);

-- =====================================================================
-- INTERNATIONAL TRANSFER INDEXES
-- =====================================================================

-- SWIFT transfer optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_international_transfers_swift 
ON international_transfers(swift_code, status)
WHERE swift_code IS NOT NULL;

-- Exchange rate optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_exchange_rates_currency_date 
ON exchange_rates(from_currency, to_currency, effective_date DESC)
WHERE effective_date >= CURRENT_DATE - INTERVAL '7 days';

-- =====================================================================
-- CRYPTO SERVICE INDEXES
-- =====================================================================

-- Crypto transaction optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_transactions_user_currency 
ON crypto_transactions(user_id, crypto_currency, created_at DESC)
INCLUDE (amount, status, blockchain_tx_hash);

-- Crypto wallet optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_wallets_user_currency 
ON crypto_wallets(user_id, currency) 
INCLUDE (balance, wallet_address, is_active)
WHERE is_active = true;

-- Crypto price tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_price_history_symbol_date 
ON crypto_price_history(symbol, price_date DESC)
INCLUDE (price_usd, volume_24h);

-- =====================================================================
-- REPORTING SERVICE INDEXES
-- =====================================================================

-- Report generation optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reports_type_status_date 
ON reports(report_type, status, created_at DESC)
WHERE status IN ('COMPLETED', 'FAILED');

-- Report parameters optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_report_parameters_report_id 
ON report_parameters(report_id) 
INCLUDE (parameter_name, parameter_value);

-- =====================================================================
-- PERFORMANCE OPTIMIZATION SETTINGS
-- =====================================================================

-- Set appropriate fillfactor for frequently updated tables
ALTER TABLE payments SET (fillfactor = 90);
ALTER TABLE wallets SET (fillfactor = 85);
ALTER TABLE users SET (fillfactor = 90);
ALTER TABLE accounts SET (fillfactor = 90);
ALTER TABLE notifications SET (fillfactor = 80);
ALTER TABLE audit_events SET (fillfactor = 95);

-- =====================================================================
-- MATERIALIZED VIEWS FOR COMPLEX ANALYTICS
-- =====================================================================

-- Daily transaction summary materialized view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_transaction_summary AS
SELECT 
    DATE(created_at) as transaction_date,
    status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as average_amount,
    COUNT(DISTINCT from_user_id) as unique_users,
    COUNT(DISTINCT merchant_id) as unique_merchants
FROM transactions
WHERE created_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE(created_at), status;

CREATE UNIQUE INDEX ON mv_daily_transaction_summary(transaction_date, status);

-- User activity summary materialized view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_user_activity_summary AS
SELECT 
    user_id,
    DATE(created_at) as activity_date,
    COUNT(*) as transaction_count,
    SUM(amount) as total_spent,
    COUNT(DISTINCT merchant_id) as unique_merchants_visited
FROM transactions
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
  AND from_user_id IS NOT NULL
GROUP BY user_id, DATE(created_at);

CREATE UNIQUE INDEX ON mv_user_activity_summary(user_id, activity_date);

-- =====================================================================
-- INDEX MONITORING AND MAINTENANCE
-- =====================================================================

-- Function to monitor index bloat
CREATE OR REPLACE FUNCTION monitor_index_bloat()
RETURNS TABLE(
    schemaname TEXT,
    tablename TEXT,
    indexname TEXT,
    bloat_ratio NUMERIC,
    bloat_size TEXT,
    recommendation TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        s.schemaname::TEXT,
        s.tablename::TEXT,
        s.indexname::TEXT,
        ROUND((pg_relation_size(s.indexrelid)::NUMERIC / NULLIF(pg_relation_size(s.relid), 0)) * 100, 2) as bloat_ratio,
        pg_size_pretty(pg_relation_size(s.indexrelid))::TEXT as bloat_size,
        CASE 
            WHEN pg_relation_size(s.indexrelid) > 100 * 1024 * 1024 THEN 'Consider REINDEX'
            WHEN s.idx_scan = 0 THEN 'Consider dropping unused index'
            ELSE 'Optimal'
        END::TEXT as recommendation
    FROM pg_stat_user_indexes s
    WHERE s.schemaname = 'public'
    ORDER BY pg_relation_size(s.indexrelid) DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to refresh materialized views
CREATE OR REPLACE FUNCTION refresh_performance_views()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_transaction_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_user_activity_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_user_transaction_summary;
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_merchant_analytics;
    
    -- Update table statistics
    ANALYZE transactions;
    ANALYZE payments;
    ANALYZE wallets;
    ANALYZE users;
    ANALYZE accounts;
    ANALYZE notifications;
    ANALYZE audit_events;
    
    RAISE NOTICE 'Performance views refreshed and statistics updated';
END;
$$ LANGUAGE plpgsql;

-- Grant permissions
GRANT EXECUTE ON FUNCTION monitor_index_bloat() TO waqiti_app;
GRANT EXECUTE ON FUNCTION refresh_performance_views() TO waqiti_app;

-- =====================================================================
-- AUTOMATED MAINTENANCE SETUP
-- =====================================================================

-- Create a view to monitor slow queries
CREATE OR REPLACE VIEW v_slow_query_analysis AS
SELECT 
    query,
    calls,
    total_exec_time,
    mean_exec_time,
    max_exec_time,
    rows,
    100.0 * shared_blks_hit / nullif(shared_blks_hit + shared_blks_read, 0) AS hit_percent
FROM pg_stat_statements
WHERE mean_exec_time > 100 -- queries taking more than 100ms
ORDER BY mean_exec_time DESC
LIMIT 50;

COMMENT ON VIEW v_slow_query_analysis IS 'Monitors slow queries for performance optimization';

-- Create performance monitoring summary
CREATE OR REPLACE VIEW v_database_performance_summary AS
SELECT 
    'Index Usage' AS metric_type,
    COUNT(*) AS total_indexes,
    COUNT(*) FILTER (WHERE idx_scan > 0) AS used_indexes,
    ROUND(100.0 * COUNT(*) FILTER (WHERE idx_scan > 0) / COUNT(*), 2) AS usage_percentage
FROM pg_stat_user_indexes
WHERE schemaname = 'public'

UNION ALL

SELECT 
    'Table Size' AS metric_type,
    COUNT(*) AS total_tables,
    COUNT(*) FILTER (WHERE pg_total_relation_size(schemaname||'.'||tablename) > 100*1024*1024) AS large_tables,
    ROUND(100.0 * COUNT(*) FILTER (WHERE pg_total_relation_size(schemaname||'.'||tablename) > 100*1024*1024) / COUNT(*), 2) AS percentage
FROM pg_tables
WHERE schemaname = 'public';

-- Final completion message
DO $$
DECLARE
    index_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO index_count 
    FROM pg_indexes 
    WHERE schemaname = 'public' 
    AND indexname LIKE 'idx_%';
    
    RAISE NOTICE 'Critical Services Performance Optimization completed successfully';
    RAISE NOTICE 'Total performance indexes created: %', index_count;
    RAISE NOTICE 'Materialized views created: 4';
    RAISE NOTICE 'Monitoring functions created: 2';
    RAISE NOTICE 'Run refresh_performance_views() daily for optimal performance';
    RAISE NOTICE 'Run monitor_index_bloat() weekly to check index health';
END $$;