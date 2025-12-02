-- =====================================================================
-- WAQITI ADVANCED FINANCIAL INDEXES OPTIMIZATION
-- =====================================================================
-- Advanced database indexes for Waqiti fintech platform
-- Optimizes query performance for high-frequency financial operations
-- 
-- Author: Claude Code Database Optimization
-- Created: 2025-09-28
-- Version: V102
-- 
-- CRITICAL AREAS ADDRESSED:
-- 1. Financial transaction processing (payments, wallets, ledger)
-- 2. Compliance and audit queries (AML, KYC, SAR)
-- 3. High-frequency user operations 
-- 4. Real-time balance calculations
-- 5. Fraud detection patterns
-- =====================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =====================================================================
-- PAYMENT SERVICE ADVANCED INDEXES
-- =====================================================================

-- High-value transaction monitoring (anti-money laundering)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_high_value_aml
ON payment_processing (amount, currency, created_at DESC)
WHERE amount >= 10000 AND status = 'COMPLETED'
INCLUDE (user_id, processor_name, gateway_response);

-- Real-time payment status tracking for API responses
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_realtime_status
ON payment_processing (payment_id, status, updated_at DESC)
INCLUDE (amount, currency, failure_reason, processed_at);

-- Payment method fraud detection patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_fraud_detection
ON payment_methods (user_id, method_type, verification_status, created_at DESC)
WHERE status = 'ACTIVE' AND verification_status = 'VERIFIED';

-- Payment disputes time-sensitive operations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_disputes_deadline
ON payment_disputes (due_date, status, created_at)
WHERE status IN ('RECEIVED', 'UNDER_REVIEW') AND due_date IS NOT NULL;

-- Webhook processing optimization for payment notifications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_webhooks_processing
ON payment_webhooks (processing_status, created_at DESC, processing_attempts)
WHERE processing_status IN ('PENDING', 'FAILED') AND processing_attempts < 5;

-- Payment refund analysis and reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_refunds_analysis
ON payment_refunds (refund_type, status, amount, created_at DESC)
INCLUDE (original_payment_id, reason, initiated_by);

-- Payment gateway health monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_gateways_health
ON payment_gateways (health_status, last_health_check, priority DESC)
WHERE is_active = true;

-- =====================================================================
-- WALLET SERVICE ADVANCED INDEXES  
-- =====================================================================

-- Real-time balance queries with currency conversion
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_balance_realtime
ON wallets (user_id, currency, status)
INCLUDE (balance, available_balance, pending_balance, frozen_balance, updated_at)
WHERE status = 'ACTIVE';

-- Wallet holds for transaction processing
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_holds_active
ON wallet_holds (wallet_id, status, expires_at)
INCLUDE (amount, hold_type, reference_id, created_by)
WHERE status = 'ACTIVE' AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP);

-- Multi-currency balance aggregation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_balances_multicurrency
ON wallet_balances (wallet_id, currency, updated_at DESC)
INCLUDE (balance, available_balance, last_transaction_id, version);

-- Transaction history for account statements
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_statement_history
ON transactions (wallet_id, created_at DESC, status)
INCLUDE (transaction_type, amount, currency, balance_after, description, reference_id)
WHERE status = 'COMPLETED';

-- Wallet audit trail for compliance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_audit_compliance
ON wallet_audit (wallet_id, created_at DESC, action)
INCLUDE (performed_by, old_values, new_values, ip_address, reason);

-- High-frequency transaction processing optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_high_frequency
ON transactions (source_wallet_id, target_wallet_id, status, created_at DESC)
WHERE status IN ('PENDING', 'COMPLETED') AND amount > 0;

-- =====================================================================
-- USER SERVICE ADVANCED INDEXES
-- =====================================================================

-- Enhanced user lookup with status and verification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_enhanced_lookup
ON users (email, phone_number, status, kyc_status)
WHERE status = 'ACTIVE'
INCLUDE (username, external_id, created_at, updated_at);

-- User profile completion tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_profiles_completion
ON user_profiles (user_id)
INCLUDE (first_name, last_name, date_of_birth, country, preferred_language, preferred_currency)
WHERE first_name IS NOT NULL AND last_name IS NOT NULL;

-- Verification token management with expiry
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_verification_tokens_active
ON verification_tokens (user_id, type, expiry_date, used)
WHERE used = false AND expiry_date > CURRENT_TIMESTAMP;

-- User roles and permissions lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_roles_lookup
ON user_roles (user_id, role)
INCLUDE (assigned_at)
WHERE role IS NOT NULL;

-- =====================================================================
-- LEDGER SERVICE ADVANCED INDEXES
-- =====================================================================

-- Double-entry bookkeeping integrity checks
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_integrity
ON ledger_entries (transaction_id, entry_type, posting_date)
INCLUDE (account_id, amount, balance_after, journal_id)
WHERE reconciliation_status = 'PENDING';

-- Chart of accounts hierarchy navigation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chart_accounts_hierarchy
ON chart_of_accounts (parent_account_id, account_type, is_active)
INCLUDE (account_code, account_name, description)
WHERE is_active = true;

-- Journal entry balancing verification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_journal_entries_balancing
ON journal_entries (status, posting_date, journal_type)
INCLUDE (total_debits, total_credits, transaction_id, description)
WHERE status = 'POSTED' AND ABS(total_debits - total_credits) < 0.01;

-- Account balance reconciliation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_balances_reconciliation
ON account_balances (account_id, last_updated DESC)
INCLUDE (current_balance, pending_debits, pending_credits, available_balance, version);

-- Reconciliation tracking for audit
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reconciliations_audit
ON reconciliations (account_id, reconciliation_date DESC, status)
INCLUDE (statement_balance, ledger_balance, difference, reconciled_by)
WHERE status = 'COMPLETED';

-- =====================================================================
-- COMPLIANCE AND AUDIT ADVANCED INDEXES
-- =====================================================================

-- Audit events for regulatory reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_regulatory
ON audit_events (entity_type, severity, timestamp DESC)
INCLUDE (entity_id, user_id, event_type, description, correlation_id)
WHERE severity IN ('HIGH', 'CRITICAL');

-- Suspicious activity reports management
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_suspicious_activity_reports_management
ON suspicious_activity_reports (status, priority, created_at DESC)
INCLUDE (subject_user_id, activity_type, risk_score, sar_number, filing_deadline)
WHERE status IN ('PENDING', 'UNDER_REVIEW');

-- Compliance reports deadline tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_reports_deadlines
ON compliance_reports (submission_deadline, status, report_type)
WHERE submission_deadline <= CURRENT_DATE + INTERVAL '30 days' AND status != 'SUBMITTED';

-- Audit verification integrity checks
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_verifications_integrity
ON audit_verifications (entity_type, entity_id, period_start, period_end)
INCLUDE (integrity_score, verified_events, failed_events, status)
WHERE integrity_score < 100.0;

-- =====================================================================
-- TRANSACTION ANALYSIS AND FRAUD DETECTION
-- =====================================================================

-- Transaction pattern analysis for fraud detection
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_fraud_patterns
ON transactions (source_wallet_id, amount, created_at)
WHERE status = 'COMPLETED' AND amount > 1000
INCLUDE (target_wallet_id, transaction_type, currency, metadata);

-- Velocity checking for transaction limits
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_velocity_check
ON transactions (source_wallet_id, created_at DESC)
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours' AND status = 'COMPLETED'
INCLUDE (amount, currency, transaction_type);

-- Geographic transaction analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_geographic
ON transactions (source_wallet_id, created_at DESC)
INCLUDE (metadata)
WHERE metadata ? 'location' AND status = 'COMPLETED';

-- =====================================================================
-- PAYMENT PROCESSING PERFORMANCE INDEXES
-- =====================================================================

-- Payment method selection optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_selection
ON payment_methods (user_id, is_default DESC, method_type, status)
WHERE status = 'ACTIVE' AND verification_status = 'VERIFIED'
INCLUDE (provider, display_name, masked_details, expires_at);

-- Gateway routing optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_gateways_routing
ON payment_gateways (is_active, priority DESC, health_status)
INCLUDE (gateway_name, supported_methods, supported_currencies, rate_limits)
WHERE is_active = true AND health_status = 'HEALTHY';

-- Payment processing retry logic
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_retry
ON payment_processing (status, created_at, processing_attempts)
WHERE status = 'FAILED' AND processing_attempts < 3
INCLUDE (payment_id, failure_reason, gateway_response);

-- =====================================================================
-- ANALYTICS AND REPORTING INDEXES
-- =====================================================================

-- Daily transaction volume analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_daily_analytics
ON transactions (DATE(created_at), status, transaction_type)
INCLUDE (amount, currency, source_wallet_id, target_wallet_id)
WHERE status = 'COMPLETED';

-- User activity patterns for personalization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_activity_patterns
ON transactions (source_wallet_id, DATE(created_at))
INCLUDE (amount, transaction_type, target_wallet_id)
WHERE status = 'COMPLETED' AND created_at >= CURRENT_DATE - INTERVAL '90 days';

-- Payment method usage analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_analytics
ON payment_processing (processor_name, method_type, DATE(created_at))
INCLUDE (amount, currency, status)
WHERE status = 'COMPLETED';

-- =====================================================================
-- SPECIALIZED FINANCIAL INDEXES
-- =====================================================================

-- Currency exchange rate lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_exchange_rates_lookup
ON exchange_rates (from_currency, to_currency, effective_date DESC)
WHERE effective_date <= CURRENT_DATE
INCLUDE (rate, inverse_rate, source, last_updated);

-- Fee calculation optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fee_schedules_calculation
ON fee_schedules (fee_type, account_type, effective_date, expiry_date)
WHERE effective_date <= CURRENT_DATE AND (expiry_date IS NULL OR expiry_date > CURRENT_DATE)
INCLUDE (fee_amount, fee_percentage, minimum_fee, maximum_fee);

-- Interest calculation for savings accounts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_interest_calculation
ON accounts (account_type, balance, last_interest_calculation)
WHERE account_type IN ('SAVINGS', 'TIME_DEPOSIT') AND balance > 0
INCLUDE (interest_rate, currency, status);

-- =====================================================================
-- FULL-TEXT SEARCH INDEXES
-- =====================================================================

-- Transaction search by description and reference
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_fulltext_search
ON transactions USING gin(to_tsvector('english', coalesce(description, '') || ' ' || coalesce(reference_id, '') || ' ' || coalesce(external_reference, '')))
WHERE status = 'COMPLETED';

-- User search optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_fulltext_search
ON users USING gin(to_tsvector('english', username || ' ' || email || ' ' || coalesce(external_id, '')))
WHERE status = 'ACTIVE';

-- Payment search by references
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_fulltext_search
ON payment_processing USING gin(to_tsvector('english', coalesce(payment_id, '') || ' ' || coalesce(processor_transaction_id, '')))
WHERE status = 'COMPLETED';

-- =====================================================================
-- GEOSPATIAL INDEXES FOR LOCATION-BASED FEATURES
-- =====================================================================

-- Geographic transaction monitoring (if location data exists)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_geospatial
ON transactions USING gist((metadata->>'location')::jsonb)
WHERE metadata ? 'location' AND status = 'COMPLETED';

-- =====================================================================
-- COVERING INDEXES FOR CRITICAL QUERIES
-- =====================================================================

-- User wallet summary covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_summary_covering
ON wallets (user_id, status)
INCLUDE (wallet_id, currency, balance, available_balance, wallet_type, created_at)
WHERE status = 'ACTIVE';

-- Payment history covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_history_covering
ON payment_processing (payment_id, created_at DESC)
INCLUDE (amount, currency, status, processor_name, processed_at, failure_reason);

-- Transaction history covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_history_covering
ON transactions (wallet_id, created_at DESC)
INCLUDE (transaction_id, transaction_type, amount, currency, balance_after, status, description);

-- =====================================================================
-- PARTIAL INDEXES FOR FILTERED QUERIES
-- =====================================================================

-- Active payment methods only
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_active_only
ON payment_methods (user_id, method_type, is_default)
WHERE status = 'ACTIVE' AND verification_status = 'VERIFIED';

-- Failed transactions for retry processing
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_failed_retry
ON transactions (created_at DESC, retry_count)
WHERE status = 'FAILED' AND retry_count < 3;

-- Pending compliance reviews
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_pending_review
ON suspicious_activity_reports (created_at DESC, priority)
WHERE status = 'PENDING' AND priority IN ('HIGH', 'CRITICAL');

-- =====================================================================
-- COMPOSITE INDEXES FOR COMPLEX QUERIES
-- =====================================================================

-- Multi-factor transaction lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_multi_factor
ON transactions (source_wallet_id, target_wallet_id, transaction_type, status, created_at DESC)
INCLUDE (amount, currency, reference_id);

-- User financial profile
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_financial_profile
ON users (kyc_status, status, created_at)
INCLUDE (username, email, external_id)
WHERE status = 'ACTIVE' AND kyc_status IN ('VERIFIED', 'ENHANCED');

-- Payment processing pipeline
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_pipeline
ON payment_processing (status, processor_name, created_at)
INCLUDE (payment_id, amount, currency, gateway_response)
WHERE status IN ('PENDING', 'PROCESSING');

-- =====================================================================
-- FUNCTION-BASED INDEXES
-- =====================================================================

-- Case-insensitive email lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_case_insensitive
ON users (lower(email))
WHERE status = 'ACTIVE' AND email IS NOT NULL;

-- Date-based transaction grouping
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_date_grouping
ON transactions (date_trunc('day', created_at), status)
WHERE status = 'COMPLETED';

-- Amount range categorization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_amount_categories
ON payment_processing (
    CASE 
        WHEN amount < 100 THEN 'SMALL'
        WHEN amount < 1000 THEN 'MEDIUM'
        WHEN amount < 10000 THEN 'LARGE'
        ELSE 'VERY_LARGE'
    END,
    created_at DESC
)
WHERE status = 'COMPLETED';

-- =====================================================================
-- HASH INDEXES FOR EXACT LOOKUPS
-- =====================================================================

-- Wallet ID hash index for exact lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_id_hash
ON wallets USING hash(wallet_id);

-- Transaction ID hash index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_id_hash
ON transactions USING hash(transaction_id);

-- Payment ID hash index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_id_hash
ON payment_processing USING hash(payment_id);

-- =====================================================================
-- MAINTENANCE AND MONITORING
-- =====================================================================

-- Update table statistics for query planner
ANALYZE wallets;
ANALYZE transactions; 
ANALYZE payment_processing;
ANALYZE payment_methods;
ANALYZE users;
ANALYZE user_profiles;
ANALYZE ledger_entries;
ANALYZE journal_entries;
ANALYZE account_balances;
ANALYZE audit_events;
ANALYZE suspicious_activity_reports;

-- Set appropriate fillfactor for write-heavy tables
ALTER TABLE transactions SET (fillfactor = 85);
ALTER TABLE payment_processing SET (fillfactor = 85);
ALTER TABLE wallet_balances SET (fillfactor = 85);
ALTER TABLE audit_events SET (fillfactor = 90);

-- =====================================================================
-- PERFORMANCE MONITORING FUNCTIONS
-- =====================================================================

-- Function to monitor financial index usage
CREATE OR REPLACE FUNCTION monitor_financial_index_usage()
RETURNS TABLE(
    table_name TEXT,
    index_name TEXT,
    index_scans BIGINT,
    tuples_read BIGINT,
    tuples_fetched BIGINT,
    size_mb NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        i.tablename::TEXT,
        i.indexname::TEXT,
        s.idx_scan,
        s.idx_tup_read,
        s.idx_tup_fetch,
        ROUND(pg_relation_size(s.indexrelid) / 1024.0 / 1024.0, 2) as size_mb
    FROM pg_indexes i
    JOIN pg_stat_user_indexes s ON i.indexname = s.indexname
    WHERE i.schemaname = 'public' 
    AND i.tablename IN ('transactions', 'payment_processing', 'wallets', 'users', 'ledger_entries')
    ORDER BY s.idx_scan DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to identify unused indexes
CREATE OR REPLACE FUNCTION identify_unused_financial_indexes()
RETURNS TABLE(
    table_name TEXT,
    index_name TEXT,
    size_mb NUMERIC,
    recommendation TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        i.tablename::TEXT,
        i.indexname::TEXT,
        ROUND(pg_relation_size(s.indexrelid) / 1024.0 / 1024.0, 2) as size_mb,
        CASE 
            WHEN s.idx_scan = 0 THEN 'Consider dropping - never used'
            WHEN s.idx_scan < 10 THEN 'Low usage - review necessity'
            ELSE 'Actively used'
        END::TEXT as recommendation
    FROM pg_indexes i
    JOIN pg_stat_user_indexes s ON i.indexname = s.indexname
    WHERE i.schemaname = 'public' 
    AND i.indexname LIKE 'idx_%'
    AND pg_relation_size(s.indexrelid) > 1024 * 1024 -- > 1MB
    ORDER BY s.idx_scan ASC, pg_relation_size(s.indexrelid) DESC;
END;
$$ LANGUAGE plpgsql;

-- Grant execution permissions
GRANT EXECUTE ON FUNCTION monitor_financial_index_usage() TO waqiti_app;
GRANT EXECUTE ON FUNCTION identify_unused_financial_indexes() TO waqiti_app;

-- =====================================================================
-- COMPLETION SUMMARY
-- =====================================================================

DO $$
DECLARE
    new_indexes INTEGER;
    total_indexes INTEGER;
BEGIN
    SELECT COUNT(*) INTO new_indexes 
    FROM pg_indexes 
    WHERE schemaname = 'public' 
    AND indexname LIKE 'idx_%'
    AND indexname SIMILAR TO '%_(advanced|realtime|fraud|compliance|analytics|covering|financial)_%';
    
    SELECT COUNT(*) INTO total_indexes 
    FROM pg_indexes 
    WHERE schemaname = 'public' 
    AND indexname LIKE 'idx_%';
    
    RAISE NOTICE '========================================================';
    RAISE NOTICE 'WAQITI ADVANCED FINANCIAL INDEXES OPTIMIZATION COMPLETE';
    RAISE NOTICE '========================================================';
    RAISE NOTICE 'Advanced financial indexes created: %', new_indexes;
    RAISE NOTICE 'Total database indexes: %', total_indexes;
    RAISE NOTICE 'Monitoring functions created: 2';
    RAISE NOTICE '';
    RAISE NOTICE 'OPTIMIZED AREAS:';
    RAISE NOTICE '✓ High-frequency payment processing';
    RAISE NOTICE '✓ Real-time balance calculations';
    RAISE NOTICE '✓ Multi-currency wallet operations';
    RAISE NOTICE '✓ Compliance and audit queries';
    RAISE NOTICE '✓ Fraud detection patterns';
    RAISE NOTICE '✓ Transaction history and reporting';
    RAISE NOTICE '✓ User financial profiles';
    RAISE NOTICE '✓ Ledger integrity and reconciliation';
    RAISE NOTICE '';
    RAISE NOTICE 'MAINTENANCE RECOMMENDATIONS:';
    RAISE NOTICE '• Run ANALYZE weekly on high-volume tables';
    RAISE NOTICE '• Monitor index usage monthly: SELECT * FROM monitor_financial_index_usage();';
    RAISE NOTICE '• Check for unused indexes: SELECT * FROM identify_unused_financial_indexes();';
    RAISE NOTICE '• Consider partitioning for audit_events and transactions tables';
    RAISE NOTICE '========================================================';
END $$;