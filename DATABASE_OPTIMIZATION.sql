-- =====================================================
-- DATABASE OPTIMIZATION FOR WAQITI P2P PAYMENT SYSTEM
-- =====================================================
-- This file contains comprehensive database optimizations including:
-- 1. Missing indexes for frequently queried columns
-- 2. Composite indexes for common query patterns
-- 3. Partial indexes for filtered queries
-- 4. Index hints for specific use cases
-- =====================================================

-- =====================================================
-- PAYMENT SERVICE OPTIMIZATIONS
-- =====================================================

-- Payments table - Critical for performance
CREATE INDEX CONCURRENTLY idx_payments_user_date ON payments(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY idx_payments_status_date ON payments(status, created_at DESC);
CREATE INDEX CONCURRENTLY idx_payments_recipient_status ON payments(recipient_id, status);
CREATE INDEX CONCURRENTLY idx_payments_amount_currency ON payments(amount, currency) WHERE status = 'COMPLETED';

-- Payment requests - Optimize pending request queries
CREATE INDEX CONCURRENTLY idx_payment_requests_pending ON payment_requests(recipient_id, status, created_at DESC) 
WHERE status IN ('PENDING', 'REQUESTED');

-- Payment methods - Frequent lookups
CREATE INDEX CONCURRENTLY idx_payment_methods_user_active ON payment_methods(user_id, is_active, is_default) 
WHERE is_active = true;

-- =====================================================
-- TRANSACTION SERVICE OPTIMIZATIONS
-- =====================================================

-- Transactions - Heavy read table
CREATE INDEX CONCURRENTLY idx_transactions_user_type_date ON transactions(user_id, transaction_type, created_at DESC);
CREATE INDEX CONCURRENTLY idx_transactions_reference ON transactions(reference_number) WHERE reference_number IS NOT NULL;
CREATE INDEX CONCURRENTLY idx_transactions_amount_range ON transactions(amount) 
WHERE amount > 1000 AND status = 'COMPLETED';

-- Transaction history - Analytics queries
CREATE INDEX CONCURRENTLY idx_transaction_history_monthly ON transaction_history(user_id, date_trunc('month', created_at));
CREATE INDEX CONCURRENTLY idx_transaction_history_category ON transaction_history(user_id, category, created_at DESC);

-- =====================================================
-- USER/ACCOUNT SERVICE OPTIMIZATIONS
-- =====================================================

-- Users table - Authentication and lookup
CREATE INDEX CONCURRENTLY idx_users_email_lower ON users(LOWER(email));
CREATE INDEX CONCURRENTLY idx_users_phone_country ON users(phone_number, country_code) WHERE phone_verified = true;
CREATE INDEX CONCURRENTLY idx_users_kyc_status ON users(kyc_status, created_at) WHERE kyc_status != 'VERIFIED';

-- Accounts table - Financial queries
CREATE INDEX CONCURRENTLY idx_accounts_balance_check ON accounts(user_id, currency, available_balance) 
WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY idx_accounts_type_balance ON accounts(account_type, balance) 
WHERE balance > 0;

-- =====================================================
-- SOCIAL SERVICE OPTIMIZATIONS
-- =====================================================

-- Social connections - Friend lookups
CREATE INDEX CONCURRENTLY idx_social_connections_bidirectional ON social_connections(
    LEAST(user_id, connected_user_id), 
    GREATEST(user_id, connected_user_id)
) WHERE status = 'ACTIVE';

-- Social feed - Timeline queries
CREATE INDEX CONCURRENTLY idx_social_feed_user_visibility ON social_feed(user_id, visibility, created_at DESC);
CREATE INDEX CONCURRENTLY idx_social_feed_public ON social_feed(created_at DESC) 
WHERE visibility = 'PUBLIC' AND is_deleted = false;

-- Payment reactions - Engagement metrics
CREATE INDEX CONCURRENTLY idx_payment_reactions_payment ON payment_reactions(payment_id, reaction_type);
CREATE INDEX CONCURRENTLY idx_payment_comments_recent ON payment_comments(payment_id, created_at DESC);

-- =====================================================
-- WALLET SERVICE OPTIMIZATIONS
-- =====================================================

-- Wallets - Balance checks
CREATE INDEX CONCURRENTLY idx_wallets_user_currency ON wallets(user_id, currency, status) 
WHERE status IN ('ACTIVE', 'VERIFIED');
CREATE INDEX CONCURRENTLY idx_wallets_low_balance ON wallets(user_id, balance) 
WHERE balance < 10 AND status = 'ACTIVE';

-- Wallet transactions
CREATE INDEX CONCURRENTLY idx_wallet_transactions_recent ON wallet_transactions(wallet_id, created_at DESC) 
WHERE created_at > CURRENT_DATE - INTERVAL '30 days';

-- =====================================================
-- RECONCILIATION SERVICE OPTIMIZATIONS
-- =====================================================

-- Reconciliation items - Matching queries
CREATE INDEX CONCURRENTLY idx_recon_items_unmatched ON reconciliation_items(reconciliation_id, status, amount) 
WHERE status = 'UNMATCHED';
CREATE INDEX CONCURRENTLY idx_recon_items_reference ON reconciliation_items(reference, date, amount);
CREATE INDEX CONCURRENTLY idx_recon_items_discrepancy ON reconciliation_items(reconciliation_id, discrepancy_amount) 
WHERE discrepancy_amount > 0;

-- =====================================================
-- NOTIFICATION SERVICE OPTIMIZATIONS
-- =====================================================

-- Notifications - Delivery queries
CREATE INDEX CONCURRENTLY idx_notifications_unread ON notifications(user_id, created_at DESC) 
WHERE is_read = false;
CREATE INDEX CONCURRENTLY idx_notifications_priority ON notifications(user_id, priority, created_at DESC) 
WHERE status = 'PENDING';

-- =====================================================
-- FRAUD DETECTION SERVICE OPTIMIZATIONS
-- =====================================================

-- Fraud alerts - Risk assessment
CREATE INDEX CONCURRENTLY idx_fraud_alerts_active ON fraud_alerts(user_id, risk_score DESC) 
WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY idx_fraud_patterns_matching ON fraud_patterns(pattern_type, confidence_score) 
WHERE is_active = true;

-- Transaction monitoring
CREATE INDEX CONCURRENTLY idx_transaction_monitoring_suspicious ON transaction_monitoring(user_id, risk_level, created_at DESC) 
WHERE risk_level IN ('HIGH', 'CRITICAL');

-- =====================================================
-- KYC SERVICE OPTIMIZATIONS
-- =====================================================

-- KYC verifications - Pending reviews
CREATE INDEX CONCURRENTLY idx_kyc_verifications_pending ON kyc_verifications(status, created_at) 
WHERE status IN ('PENDING', 'IN_REVIEW');
CREATE INDEX CONCURRENTLY idx_kyc_documents_user ON kyc_documents(user_id, document_type, status) 
WHERE status = 'VERIFIED';

-- =====================================================
-- DISPUTE SERVICE OPTIMIZATIONS
-- =====================================================

-- Disputes - Case management
CREATE INDEX CONCURRENTLY idx_disputes_status_priority ON disputes(status, priority DESC, created_at) 
WHERE status NOT IN ('CLOSED', 'RESOLVED');
CREATE INDEX CONCURRENTLY idx_dispute_evidence_type ON dispute_evidence(dispute_id, evidence_type, created_at DESC);

-- =====================================================
-- ANALYTICS OPTIMIZATIONS (MATERIALIZED VIEWS)
-- =====================================================

-- Daily transaction summary
CREATE MATERIALIZED VIEW mv_daily_transaction_summary AS
SELECT 
    user_id,
    DATE(created_at) as transaction_date,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MAX(amount) as max_amount,
    currency
FROM transactions
WHERE status = 'COMPLETED'
GROUP BY user_id, DATE(created_at), currency;

CREATE UNIQUE INDEX ON mv_daily_transaction_summary(user_id, transaction_date, currency);

-- User engagement metrics
CREATE MATERIALIZED VIEW mv_user_engagement AS
SELECT 
    u.id as user_id,
    COUNT(DISTINCT t.id) as total_transactions,
    COUNT(DISTINCT sp.id) as social_payments,
    COUNT(DISTINCT pr.id) as payment_reactions,
    COUNT(DISTINCT pc.id) as payment_comments,
    MAX(t.created_at) as last_transaction_date
FROM users u
LEFT JOIN transactions t ON u.id = t.user_id
LEFT JOIN social_payments sp ON u.id = sp.sender_id OR u.id = sp.recipient_id
LEFT JOIN payment_reactions pr ON u.id = pr.user_id
LEFT JOIN payment_comments pc ON u.id = pc.user_id
GROUP BY u.id;

CREATE UNIQUE INDEX ON mv_user_engagement(user_id);

-- Monthly revenue summary
CREATE MATERIALIZED VIEW mv_monthly_revenue AS
SELECT 
    DATE_TRUNC('month', created_at) as month,
    transaction_type,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    SUM(fee_amount) as total_fees,
    currency
FROM transactions
WHERE status = 'COMPLETED'
GROUP BY DATE_TRUNC('month', created_at), transaction_type, currency;

CREATE UNIQUE INDEX ON mv_monthly_revenue(month, transaction_type, currency);

-- =====================================================
-- REFRESH STRATEGIES FOR MATERIALIZED VIEWS
-- =====================================================

-- Schedule these refreshes based on business requirements
-- Daily refresh for transaction summary
CREATE OR REPLACE FUNCTION refresh_daily_transaction_summary()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_transaction_summary;
END;
$$ LANGUAGE plpgsql;

-- Hourly refresh for user engagement
CREATE OR REPLACE FUNCTION refresh_user_engagement()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_user_engagement;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- QUERY OPTIMIZATION HINTS
-- =====================================================

-- Enable query parallelism for large tables
ALTER TABLE transactions SET (parallel_workers = 4);
ALTER TABLE payments SET (parallel_workers = 4);
ALTER TABLE social_feed SET (parallel_workers = 2);

-- Update table statistics for better query planning
ANALYZE transactions;
ANALYZE payments;
ANALYZE users;
ANALYZE accounts;
ANALYZE wallets;
ANALYZE social_payments;
ANALYZE reconciliation_items;

-- =====================================================
-- PARTITIONING STRATEGY FOR LARGE TABLES
-- =====================================================

-- Partition transactions table by month for better performance
-- Note: This requires careful migration planning

-- Create partitioned table
CREATE TABLE transactions_partitioned (
    LIKE transactions INCLUDING ALL
) PARTITION BY RANGE (created_at);

-- Create monthly partitions
CREATE TABLE transactions_2024_01 PARTITION OF transactions_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
    
CREATE TABLE transactions_2024_02 PARTITION OF transactions_partitioned
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
    
-- Continue for all months...

-- Create index on each partition
CREATE INDEX idx_transactions_2024_01_user_id ON transactions_2024_01(user_id);
CREATE INDEX idx_transactions_2024_02_user_id ON transactions_2024_02(user_id);

-- =====================================================
-- VACUUM AND MAINTENANCE SETTINGS
-- =====================================================

-- Configure autovacuum for high-transaction tables
ALTER TABLE transactions SET (autovacuum_vacuum_scale_factor = 0.05);
ALTER TABLE payments SET (autovacuum_vacuum_scale_factor = 0.05);
ALTER TABLE wallet_transactions SET (autovacuum_vacuum_scale_factor = 0.05);

-- =====================================================
-- CONNECTION POOLING RECOMMENDATIONS
-- =====================================================

-- Recommended HikariCP settings for production
-- hikari.maximumPoolSize=50
-- hikari.minimumIdle=10
-- hikari.connectionTimeout=30000
-- hikari.idleTimeout=600000
-- hikari.maxLifetime=1800000

-- =====================================================
-- MONITORING QUERIES
-- =====================================================

-- Find missing indexes
SELECT schemaname, tablename, attname, n_distinct, correlation
FROM pg_stats
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
  AND n_distinct > 100
  AND correlation < 0.1
ORDER BY n_distinct DESC;

-- Find slow queries
SELECT query, calls, mean_exec_time, max_exec_time, total_exec_time
FROM pg_stat_statements
WHERE mean_exec_time > 100
ORDER BY mean_exec_time DESC
LIMIT 20;

-- Find tables with high sequential scans
SELECT schemaname, tablename, seq_scan, seq_tup_read, idx_scan, idx_tup_fetch
FROM pg_stat_user_tables
WHERE seq_scan > idx_scan
  AND schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY seq_tup_read DESC;

-- =====================================================
-- END OF DATABASE OPTIMIZATION SCRIPT
-- =====================================================