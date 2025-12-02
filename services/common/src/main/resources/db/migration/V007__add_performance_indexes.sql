-- Performance Optimization Database Indexes
-- Comprehensive index strategy to eliminate N+1 queries and improve performance

-- =====================================================================
-- QR CODE PAYMENTS INDEXES
-- =====================================================================

-- Primary lookup indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_qr_code_id 
    ON qr_code_payments(qr_code_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_user_id_status 
    ON qr_code_payments(user_id, status) 
    WHERE status IN ('ACTIVE', 'PROCESSING');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_merchant_id_status 
    ON qr_code_payments(merchant_id, status) 
    WHERE merchant_id IS NOT NULL;

-- Expiry and cleanup indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_expires_at_status 
    ON qr_code_payments(expires_at, status) 
    WHERE expires_at IS NOT NULL AND status = 'ACTIVE';

-- Analytics indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_completed_at_amount 
    ON qr_code_payments(completed_at, final_amount) 
    WHERE status = 'COMPLETED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_type_created_at 
    ON qr_code_payments(type, created_at);

-- Reference lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_reference 
    ON qr_code_payments(reference) 
    WHERE reference IS NOT NULL;

-- Transaction correlation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_code_payments_transaction_id 
    ON qr_code_payments(transaction_id) 
    WHERE transaction_id IS NOT NULL;

-- =====================================================================
-- USER REWARDS ACCOUNT INDEXES
-- =====================================================================

-- Primary user lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_user_id 
    ON user_rewards_account(user_id);

-- Tier-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_tier_id_status 
    ON user_rewards_account(tier_id, status);

-- Performance leaderboard
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_total_points_status 
    ON user_rewards_account(total_points DESC, status) 
    WHERE status = 'ACTIVE';

-- High-value users
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_lifetime_cashback 
    ON user_rewards_account(lifetime_cashback_earned DESC) 
    WHERE status = 'ACTIVE';

-- Activity tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_last_activity 
    ON user_rewards_account(last_activity_date, status) 
    WHERE status = 'ACTIVE';

-- Tier progression
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_tier_progress 
    ON user_rewards_account(tier_id, tier_progress_points);

-- Cashback range queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_cashback_balance 
    ON user_rewards_account(cashback_balance) 
    WHERE status = 'ACTIVE' AND cashback_balance > 0;

-- Enrollment cohort analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_rewards_account_enrollment_date 
    ON user_rewards_account(enrollment_date);

-- =====================================================================
-- CRYPTO TRADE PAIR INDEXES
-- =====================================================================

-- Symbol lookup (most common)
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_symbol_unique 
    ON trade_pairs(symbol) 
    WHERE active = true;

-- Active trading pairs
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_active_trading 
    ON trade_pairs(active, trading_enabled, priority DESC) 
    WHERE active = true AND trading_enabled = true;

-- Currency-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_trade_currency_active 
    ON trade_pairs(trade_currency_id, active) 
    WHERE active = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_base_currency_active 
    ON trade_pairs(base_currency_id, active) 
    WHERE active = true;

-- Volume and popularity
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_volume_24h 
    ON trade_pairs(volume_24h DESC) 
    WHERE active = true AND trading_enabled = true;

-- Price update tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_price_update 
    ON trade_pairs(last_price_update) 
    WHERE active = true AND trading_enabled = true;

-- Fee-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_maker_fee 
    ON trade_pairs(maker_fee, taker_fee) 
    WHERE active = true;

-- Price alerts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_trade_pairs_price_alerts 
    ON trade_pairs(has_price_alerts) 
    WHERE active = true AND has_price_alerts = true;

-- =====================================================================
-- ROLE-BASED ACCESS CONTROL INDEXES
-- =====================================================================

-- Role name lookup
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_roles_name_unique 
    ON roles(name) 
    WHERE active = true;

-- Hierarchy queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_roles_hierarchy_level_priority 
    ON roles(hierarchy_level, priority) 
    WHERE active = true;

-- Role type filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_roles_type_active 
    ON roles(type, active);

-- Assignable roles
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_roles_assignable_hierarchy 
    ON roles(assignable, hierarchy_level) 
    WHERE active = true AND assignable = true;

-- User role assignments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_user_id_status 
    ON user_roles(user_id, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_role_id_status 
    ON user_roles(role_id, status);

-- Effective date filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_effective_expires 
    ON user_roles(effective_from, expires_at, status) 
    WHERE status = 'ACTIVE';

-- Role hierarchy relationships
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_hierarchy_parent_child 
    ON role_hierarchy_relations(parent_role_id, child_role_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_hierarchy_child_parent 
    ON role_hierarchy_relations(child_role_id, parent_role_id);

-- Role permissions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_permissions_role_id 
    ON role_permissions(role_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_permissions_permission_id 
    ON role_permissions(permission_id);

-- =====================================================================
-- TRANSACTION AND PAYMENT INDEXES
-- =====================================================================

-- Transaction lookup by user
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_id_created_at 
    ON transactions(user_id, created_at DESC);

-- Transaction status queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_status_created_at 
    ON transactions(status, created_at DESC);

-- Amount-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount_currency 
    ON transactions(amount, currency) 
    WHERE status = 'COMPLETED';

-- Reference and external ID lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_external_reference 
    ON transactions(external_reference) 
    WHERE external_reference IS NOT NULL;

-- Payment method tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_payment_method 
    ON transactions(payment_method_id, created_at DESC) 
    WHERE payment_method_id IS NOT NULL;

-- =====================================================================
-- AUDIT AND COMPLIANCE INDEXES
-- =====================================================================

-- Audit events by entity
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_entity_type_id 
    ON audit_events(entity_type, entity_id, created_at DESC);

-- User activity audit
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_user_id_created_at 
    ON audit_events(user_id, created_at DESC) 
    WHERE user_id IS NOT NULL;

-- Event type filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_event_type_created_at 
    ON audit_events(event_type, created_at DESC);

-- Compliance checks
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_checks_entity_type_status 
    ON compliance_checks(entity_type, entity_id, status);

-- KYC status tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_applications_user_id_status 
    ON kyc_applications(user_id, status, created_at DESC);

-- =====================================================================
-- NOTIFICATION AND MESSAGING INDEXES
-- =====================================================================

-- User notifications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_id_status 
    ON notifications(user_id, status, created_at DESC);

-- Notification type filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_type_created_at 
    ON notifications(notification_type, created_at DESC);

-- Message conversations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_messages_conversation_id_created_at 
    ON messages(conversation_id, created_at DESC);

-- Unread message tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_messages_recipient_read_status 
    ON messages(recipient_id, read_at) 
    WHERE read_at IS NULL;

-- =====================================================================
-- PERFORMANCE MONITORING INDEXES
-- =====================================================================

-- API request tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_api_requests_endpoint_timestamp 
    ON api_requests(endpoint, request_timestamp DESC);

-- Response time analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_api_requests_response_time 
    ON api_requests(response_time_ms) 
    WHERE response_time_ms > 1000;

-- Error rate monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_api_requests_status_code_timestamp 
    ON api_requests(status_code, request_timestamp DESC) 
    WHERE status_code >= 400;

-- User session tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_sessions_user_id_created_at 
    ON user_sessions(user_id, created_at DESC);

-- =====================================================================
-- SCHEDULED TASK OPTIMIZATION
-- =====================================================================

-- Data cleanup queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expired_tokens_expires_at 
    ON tokens(expires_at) 
    WHERE expires_at <= CURRENT_TIMESTAMP;

-- Session cleanup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expired_sessions_expires_at 
    ON user_sessions(expires_at) 
    WHERE expires_at <= CURRENT_TIMESTAMP;

-- QR code cleanup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_cleanup_created_status 
    ON qr_code_payments(created_at, status) 
    WHERE status IN ('EXPIRED', 'CANCELLED') 
    AND created_at < CURRENT_TIMESTAMP - INTERVAL '90 days';

-- =====================================================================
-- ANALYTICS AND REPORTING INDEXES
-- =====================================================================

-- Daily aggregation queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_date_amount 
    ON transactions(DATE(created_at), amount, currency) 
    WHERE status = 'COMPLETED';

-- Monthly revenue reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_month_merchant 
    ON payments(DATE_TRUNC('month', created_at), merchant_id, amount) 
    WHERE status = 'COMPLETED';

-- User behavior analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_actions_user_date 
    ON user_actions(user_id, DATE(created_at), action_type);

-- Geographic analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_country_currency 
    ON transactions(country_code, currency, created_at DESC) 
    WHERE country_code IS NOT NULL;

-- =====================================================================
-- COMPOSITE INDEXES FOR COMPLEX QUERIES
-- =====================================================================

-- Multi-field transaction search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_composite_search 
    ON transactions(user_id, status, payment_method_id, created_at DESC);

-- Role permission resolution
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_role_permission_composite 
    ON user_roles(user_id, status, effective_from, expires_at) 
    WHERE status = 'ACTIVE';

-- QR code analytics composite
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_analytics_composite 
    ON qr_code_payments(type, status, merchant_id, completed_at) 
    WHERE status = 'COMPLETED';

-- Fraud detection composite
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_detection_composite 
    ON transactions(user_id, amount, created_at, status) 
    WHERE amount > 1000 AND status = 'COMPLETED';

-- =====================================================================
-- INDEX STATISTICS AND MONITORING
-- =====================================================================

-- Create a view to monitor index usage
CREATE OR REPLACE VIEW index_usage_stats AS
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes 
ORDER BY idx_scan DESC;

-- =====================================================================
-- INDEX MAINTENANCE NOTES
-- =====================================================================

/*
MAINTENANCE SCHEDULE:
1. REINDEX CONCURRENTLY weekly for high-write indexes
2. ANALYZE tables daily for query planner optimization
3. Monitor index usage with pg_stat_user_indexes
4. Drop unused indexes quarterly
5. Consider partial indexes for large tables with filtered queries

PERFORMANCE TESTING:
1. Measure query performance before/after index creation
2. Monitor index bloat with pg_stats
3. Use EXPLAIN ANALYZE for query optimization
4. Track index hit ratios in monitoring

SCALING CONSIDERATIONS:
1. Partition large tables by date for better performance
2. Use partial indexes for frequently filtered data
3. Consider covering indexes for read-heavy queries
4. Implement index-only scans where possible
*/