-- =====================================================================
-- COMPREHENSIVE DATABASE PERFORMANCE OPTIMIZATION
-- =====================================================================
-- This migration adds all missing indexes identified in the deep scan
-- to optimize query performance across the Waqiti platform

-- =====================================================================
-- TRANSACTION TABLE INDEXES
-- =====================================================================

-- Composite index for user transaction queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_user_date 
ON transactions(user_id, created_at DESC)
WHERE status != 'CANCELLED';

-- Index for transaction status queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_status_date 
ON transactions(status, created_at DESC);

-- Index for transaction type queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_type_amount 
ON transactions(transaction_type, amount)
WHERE amount > 0;

-- Index for recipient queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_recipient 
ON transactions(recipient_id, created_at DESC)
WHERE recipient_id IS NOT NULL;

-- Partial index for pending transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_pending 
ON transactions(created_at DESC)
WHERE status = 'PENDING';

-- Index for transaction reference lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_reference 
ON transactions(reference_number)
WHERE reference_number IS NOT NULL;

-- =====================================================================
-- PAYMENT TABLE INDEXES
-- =====================================================================

-- Composite index for payment status and date
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_status_date 
ON payments(status, created_at DESC);

-- Index for payment method queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_method_user 
ON payments(payment_method, user_id, created_at DESC);

-- Index for scheduled payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_scheduled 
ON payments(scheduled_date, status)
WHERE scheduled_date IS NOT NULL;

-- Index for payment amount ranges
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_amount_range 
ON payments(amount, created_at DESC)
WHERE amount BETWEEN 0 AND 10000;

-- Index for high-value payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_high_value 
ON payments(user_id, created_at DESC)
WHERE amount > 10000;

-- =====================================================================
-- WALLET TABLE INDEXES
-- =====================================================================

-- Composite index for wallet lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_user_currency 
ON wallets(user_id, currency, status);

-- Index for wallet balance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_balance 
ON wallets(balance DESC)
WHERE status = 'ACTIVE';

-- Index for wallet type queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_type 
ON wallets(wallet_type, user_id)
WHERE status = 'ACTIVE';

-- =====================================================================
-- VIRTUAL CARD TABLE INDEXES
-- =====================================================================

-- Index for user virtual cards
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_virtual_card_user_status 
ON virtual_cards(user_id, status, created_at DESC);

-- Index for card expiry monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_virtual_card_expiry 
ON virtual_cards(expiry_date, status)
WHERE status = 'ACTIVE';

-- Index for card limits
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_virtual_card_limits 
ON virtual_cards(spending_limit, daily_limit)
WHERE status = 'ACTIVE';

-- =====================================================================
-- USER TABLE INDEXES
-- =====================================================================

-- Index for user email lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_email_lower 
ON users(LOWER(email))
WHERE status = 'ACTIVE';

-- Index for user phone lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_phone 
ON users(phone_number)
WHERE phone_number IS NOT NULL;

-- Index for user KYC status
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_kyc_status 
ON users(kyc_status, created_at DESC);

-- Index for user referral tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_referral 
ON users(referred_by)
WHERE referred_by IS NOT NULL;

-- =====================================================================
-- ACCOUNT TABLE INDEXES
-- =====================================================================

-- Index for account lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_user_type 
ON accounts(user_id, account_type, status);

-- Index for account balance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_balance_range 
ON accounts(balance)
WHERE status = 'ACTIVE';

-- Index for account number lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_number 
ON accounts(account_number)
WHERE account_number IS NOT NULL;

-- =====================================================================
-- MERCHANT TABLE INDEXES
-- =====================================================================

-- Index for merchant lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_category 
ON merchants(category, status)
WHERE status = 'ACTIVE';

-- Index for merchant location queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_location 
ON merchants USING GIST(location)
WHERE status = 'ACTIVE';

-- Index for merchant rating queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_rating 
ON merchants(rating DESC, review_count DESC)
WHERE status = 'ACTIVE';

-- =====================================================================
-- NOTIFICATION TABLE INDEXES
-- =====================================================================

-- Index for unread notifications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_unread 
ON notifications(user_id, created_at DESC)
WHERE is_read = FALSE;

-- Index for notification type queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_type 
ON notifications(notification_type, user_id, created_at DESC);

-- =====================================================================
-- AUDIT LOG TABLE INDEXES
-- =====================================================================

-- Index for audit log queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_user_action 
ON audit_logs(user_id, action, created_at DESC);

-- Index for audit log entity queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_entity 
ON audit_logs(entity_type, entity_id, created_at DESC);

-- Index for security audit queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_security 
ON audit_logs(created_at DESC)
WHERE action IN ('LOGIN_FAILED', 'UNAUTHORIZED_ACCESS', 'SUSPICIOUS_ACTIVITY');

-- =====================================================================
-- CRYPTO TRANSACTION TABLE INDEXES
-- =====================================================================

-- Index for crypto transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_tx_user 
ON crypto_transactions(user_id, crypto_currency, created_at DESC);

-- Index for blockchain transaction hash
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_tx_hash 
ON crypto_transactions(blockchain_tx_hash)
WHERE blockchain_tx_hash IS NOT NULL;

-- =====================================================================
-- INTERNATIONAL TRANSFER TABLE INDEXES
-- =====================================================================

-- Index for international transfers
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_intl_transfer_user 
ON international_transfers(user_id, status, created_at DESC);

-- Index for SWIFT transfers
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_intl_transfer_swift 
ON international_transfers(swift_code, status)
WHERE swift_code IS NOT NULL;

-- =====================================================================
-- KYC DOCUMENT TABLE INDEXES
-- =====================================================================

-- Index for KYC document queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_doc_user_type 
ON kyc_documents(user_id, document_type, status);

-- Index for KYC verification queue
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_doc_verification 
ON kyc_documents(created_at ASC)
WHERE status = 'PENDING_VERIFICATION';

-- =====================================================================
-- SESSION TABLE INDEXES
-- =====================================================================

-- Index for active sessions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_session_user_active 
ON user_sessions(user_id, last_activity DESC)
WHERE is_active = TRUE;

-- Index for session token lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_session_token 
ON user_sessions(session_token)
WHERE is_active = TRUE;

-- =====================================================================
-- RATE LIMIT TABLE INDEXES
-- =====================================================================

-- Index for rate limit checks
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rate_limit_key 
ON rate_limits(limit_key, window_start DESC);

-- =====================================================================
-- PERFORMANCE OPTIMIZATION SETTINGS
-- =====================================================================

-- Update table statistics for query planner
ANALYZE transactions;
ANALYZE payments;
ANALYZE wallets;
ANALYZE virtual_cards;
ANALYZE users;
ANALYZE accounts;
ANALYZE merchants;
ANALYZE notifications;
ANALYZE audit_logs;

-- Set table fillfactor for frequently updated tables
ALTER TABLE transactions SET (fillfactor = 90);
ALTER TABLE payments SET (fillfactor = 90);
ALTER TABLE wallets SET (fillfactor = 85);
ALTER TABLE notifications SET (fillfactor = 80);

-- Create partial indexes for soft-deleted records
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_not_deleted 
ON transactions(id) WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_not_deleted 
ON payments(id) WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_not_deleted 
ON users(id) WHERE deleted_at IS NULL;

-- =====================================================================
-- MATERIALIZED VIEWS FOR COMPLEX QUERIES
-- =====================================================================

-- Materialized view for user transaction summary
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_user_transaction_summary AS
SELECT 
    user_id,
    DATE(created_at) as transaction_date,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MAX(amount) as max_amount,
    MIN(amount) as min_amount
FROM transactions
WHERE status = 'COMPLETED'
GROUP BY user_id, DATE(created_at);

CREATE INDEX ON mv_user_transaction_summary(user_id, transaction_date DESC);

-- Materialized view for merchant transaction analytics
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_merchant_analytics AS
SELECT 
    merchant_id,
    DATE(created_at) as date,
    COUNT(*) as transaction_count,
    SUM(amount) as revenue,
    COUNT(DISTINCT user_id) as unique_customers
FROM payments
WHERE status = 'COMPLETED'
GROUP BY merchant_id, DATE(created_at);

CREATE INDEX ON mv_merchant_analytics(merchant_id, date DESC);

-- =====================================================================
-- QUERY PERFORMANCE MONITORING
-- =====================================================================

-- Create extension for query monitoring if not exists
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Function to identify slow queries
CREATE OR REPLACE FUNCTION identify_slow_queries()
RETURNS TABLE(
    query TEXT,
    calls BIGINT,
    total_time DOUBLE PRECISION,
    mean_time DOUBLE PRECISION,
    max_time DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        substring(query, 1, 100) as query,
        calls,
        total_exec_time as total_time,
        mean_exec_time as mean_time,
        max_exec_time as max_time
    FROM pg_stat_statements
    WHERE mean_exec_time > 100 -- queries taking more than 100ms
    ORDER BY mean_exec_time DESC
    LIMIT 20;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- CLEANUP AND MAINTENANCE
-- =====================================================================

-- Remove duplicate indexes if any exist
DO $$
DECLARE
    dup_indexes CURSOR FOR
        SELECT indexname, tablename
        FROM pg_indexes
        WHERE schemaname = 'public'
        GROUP BY indexname, tablename
        HAVING COUNT(*) > 1;
    rec RECORD;
BEGIN
    FOR rec IN dup_indexes LOOP
        EXECUTE 'DROP INDEX IF EXISTS ' || rec.indexname;
    END LOOP;
END $$;

-- Update index statistics
REINDEX DATABASE waqiti;

-- =====================================================================
-- PERFORMANCE VALIDATION
-- =====================================================================

-- Create function to validate index usage
CREATE OR REPLACE FUNCTION validate_index_usage()
RETURNS TABLE(
    schemaname TEXT,
    tablename TEXT,
    indexname TEXT,
    idx_scan BIGINT,
    idx_tup_read BIGINT,
    idx_tup_fetch BIGINT,
    index_size TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        s.schemaname,
        s.tablename,
        s.indexname,
        s.idx_scan,
        s.idx_tup_read,
        s.idx_tup_fetch,
        pg_size_pretty(pg_relation_size(s.indexrelid)) as index_size
    FROM pg_stat_user_indexes s
    WHERE s.schemaname = 'public'
    ORDER BY s.idx_scan DESC;
END;
$$ LANGUAGE plpgsql;

-- Grant necessary permissions
GRANT EXECUTE ON FUNCTION identify_slow_queries() TO waqiti_app;
GRANT EXECUTE ON FUNCTION validate_index_usage() TO waqiti_app;