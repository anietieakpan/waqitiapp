-- =====================================================================
-- TRANSACTION SERVICE PERFORMANCE OPTIMIZATION INDEXES
-- =====================================================================
-- This migration adds missing indexes for the Transaction Service to
-- support N+1 query optimizations and improve overall query performance
-- Based on OptimizedTransactionService.java and TransactionRepository.java

-- =====================================================================
-- CORE TRANSACTION TABLE INDEXES
-- =====================================================================

-- Index for batch operations (findByBatchId, findByBatchIdAndStatus)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_batch_id 
ON transactions(batch_id) WHERE batch_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_batch_status 
ON transactions(batch_id, status) WHERE batch_id IS NOT NULL;

-- Index for merchant-related queries (N+1 optimization support)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_merchant_id 
ON transactions(merchant_id) WHERE merchant_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_merchant_id_date 
ON transactions(merchant_id, created_at DESC) 
INCLUDE (amount, status, currency, from_user_id)
WHERE merchant_id IS NOT NULL;

-- Index for customer queries (N+1 optimization support)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_customer_id 
ON transactions(customer_id) WHERE customer_id IS NOT NULL;

-- Optimized user transaction queries with covering data
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_from_user_date_covering 
ON transactions(from_user_id, created_at DESC) 
INCLUDE (amount, status, merchant_id, currency, to_user_id)
WHERE from_user_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_to_user_date_covering 
ON transactions(to_user_id, created_at DESC) 
INCLUDE (amount, status, merchant_id, currency, from_user_id)
WHERE to_user_id IS NOT NULL;

-- Combined user index for OR queries (fromUserId OR toUserId)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_combined 
ON transactions(greatest(from_user_id::text, to_user_id::text), created_at DESC)
WHERE from_user_id IS NOT NULL OR to_user_id IS NOT NULL;

-- Index for external and processor reference lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_external_reference 
ON transactions(external_reference) WHERE external_reference IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_processor_reference 
ON transactions(processor_reference) WHERE processor_reference IS NOT NULL;

-- Index for suspension queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_suspended 
ON transactions(suspended_at, emergency_suspension) 
WHERE suspended_at IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_suspension_reason 
ON transactions(suspension_reason) WHERE suspension_reason IS NOT NULL;

-- Index for retry/recovery operations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_retry_eligible 
ON transactions(retry_count, next_retry_at, status) 
WHERE status IN ('FAILED', 'PROCESSING_ERROR') AND retry_count > 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_stale_processing 
ON transactions(status, created_at) 
WHERE status = 'PROCESSING';

-- =====================================================================
-- TRANSACTION ITEMS TABLE INDEXES (N+1 OPTIMIZATION)
-- =====================================================================

-- Core index for transaction items (prevents N+1 when loading items)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_items_transaction_id_covering 
ON transaction_items(transaction_id) 
INCLUDE (item_name, quantity, price, total, item_type, metadata);

-- Batch loading optimization for multiple transaction IDs
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_items_batch_lookup 
ON transaction_items(transaction_id, item_order) 
WHERE transaction_id IS NOT NULL;

-- =====================================================================
-- ACCOUNT TABLE INDEXES (FOR JOIN OPTIMIZATIONS)
-- =====================================================================

-- Source account optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_source_account_covering 
ON accounts(id) 
INCLUDE (account_number, account_type, status, currency, user_id)
WHERE status = 'ACTIVE';

-- Target account optimizations  
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_target_account_covering 
ON accounts(id) 
INCLUDE (account_number, account_type, status, currency, user_id)
WHERE status = 'ACTIVE';

-- =====================================================================
-- AGGREGATION AND PROJECTION QUERY INDEXES
-- =====================================================================

-- Index for transaction summaries by user (getTransactionSummariesByUserIds)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_summary_aggregation 
ON transactions(from_user_id, created_at::date, status) 
INCLUDE (amount)
WHERE from_user_id IS NOT NULL;

-- Index for daily transaction counts by merchant (getDailyTransactionCounts)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_daily_merchant_counts 
ON transactions(merchant_id, created_at::date) 
INCLUDE (status)
WHERE merchant_id IS NOT NULL;

-- Index for status-based queries with date filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_status_date_range 
ON transactions(status, created_at) 
INCLUDE (amount, currency, from_user_id, to_user_id, merchant_id);

-- =====================================================================
-- BULK OPERATION INDEXES
-- =====================================================================

-- Index for bulk status updates (bulkUpdateStatus method)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_bulk_update 
ON transactions(id, status, updated_at) 
WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');

-- Index for bulk operations by customer/merchant
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_customer_bulk 
ON transactions(customer_id, status) 
WHERE customer_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_merchant_bulk 
ON transactions(merchant_id, status) 
WHERE merchant_id IS NOT NULL;

-- =====================================================================
-- WALLET-RELATED INDEXES
-- =====================================================================

-- Index for wallet transaction history
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_from_wallet_date 
ON transactions(from_wallet_id, created_at DESC) 
INCLUDE (amount, status, currency, to_wallet_id)
WHERE from_wallet_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_to_wallet_date 
ON transactions(to_wallet_id, created_at DESC) 
INCLUDE (amount, status, currency, from_wallet_id)
WHERE to_wallet_id IS NOT NULL;

-- Combined wallet index for date range queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_wallet_date_range 
ON transactions(from_wallet_id, created_at) 
WHERE from_wallet_id IS NOT NULL;

-- Alternative index for to_wallet queries in date range
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_to_wallet_date_range 
ON transactions(to_wallet_id, created_at) 
WHERE to_wallet_id IS NOT NULL;

-- =====================================================================
-- TIME-BASED PARTITIONING PREPARATION INDEXES
-- =====================================================================

-- Monthly partitioning preparation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_monthly_partition 
ON transactions(date_trunc('month', created_at), status);

-- Daily aggregation index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_daily_aggregation 
ON transactions(date_trunc('day', created_at), status, currency)
INCLUDE (amount);

-- =====================================================================
-- FOREIGN KEY PERFORMANCE INDEXES
-- =====================================================================

-- Ensure all foreign key relationships have proper indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_source_account_fk 
ON transactions(source_account_id) WHERE source_account_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_target_account_fk 
ON transactions(target_account_id) WHERE target_account_id IS NOT NULL;

-- =====================================================================
-- FULL-TEXT SEARCH INDEXES
-- =====================================================================

-- Full-text search on transaction descriptions and metadata
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_description_fts 
ON transactions USING GIN (to_tsvector('english', description))
WHERE description IS NOT NULL;

-- JSON metadata search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_metadata_gin 
ON transactions USING GIN (metadata)
WHERE metadata IS NOT NULL;

-- =====================================================================
-- PARTIAL INDEXES FOR ACTIVE/VALID RECORDS
-- =====================================================================

-- Index for non-cancelled transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_active 
ON transactions(created_at DESC) 
WHERE status != 'CANCELLED' AND status != 'REVERSED';

-- Index for completed transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_completed 
ON transactions(created_at DESC, amount DESC) 
WHERE status = 'COMPLETED';

-- Index for failed transactions for analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_failed_analysis 
ON transactions(created_at DESC, failure_reason) 
WHERE status = 'FAILED' AND failure_reason IS NOT NULL;

-- =====================================================================
-- COMPOSITE INDEXES FOR COMPLEX QUERIES
-- =====================================================================

-- User + status + date for filtered transaction listings
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_status_date 
ON transactions(from_user_id, status, created_at DESC)
WHERE from_user_id IS NOT NULL;

-- Alternative for to_user queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_to_user_status_date 
ON transactions(to_user_id, status, created_at DESC)
WHERE to_user_id IS NOT NULL;

-- Merchant + status + date for merchant dashboards
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_merchant_status_date 
ON transactions(merchant_id, status, created_at DESC)
WHERE merchant_id IS NOT NULL;

-- Amount range queries with date
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount_range_date 
ON transactions(amount, created_at DESC)
WHERE status = 'COMPLETED';

-- =====================================================================
-- TRANSACTION EVENTS TABLE INDEXES
-- =====================================================================

-- Index for event timeline queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_events_timeline 
ON transaction_events(transaction_id, created_at DESC)
INCLUDE (event_type, event_status, description);

-- Index for event type analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_events_type_date 
ON transaction_events(event_type, created_at DESC)
INCLUDE (transaction_id, event_status);

-- =====================================================================
-- PERFORMANCE OPTIMIZATION SETTINGS
-- =====================================================================

-- Set fillfactor for frequently updated tables
ALTER TABLE transactions SET (fillfactor = 90);
ALTER TABLE transaction_items SET (fillfactor = 95);
ALTER TABLE transaction_events SET (fillfactor = 85);

-- =====================================================================
-- INDEX STATISTICS AND COMMENTS
-- =====================================================================

-- Add comments for maintenance documentation
COMMENT ON INDEX idx_transaction_items_transaction_id_covering IS 'Prevents N+1 queries when loading transaction items in OptimizedTransactionService';
COMMENT ON INDEX idx_transactions_from_user_date_covering IS 'Optimizes user transaction listings with covering data';
COMMENT ON INDEX idx_transactions_merchant_id_date IS 'Optimizes merchant transaction queries with customer details';
COMMENT ON INDEX idx_transactions_summary_aggregation IS 'Optimizes transaction summary aggregations by user';
COMMENT ON INDEX idx_transactions_daily_merchant_counts IS 'Optimizes daily transaction count aggregations';
COMMENT ON INDEX idx_transactions_bulk_update IS 'Optimizes bulk status update operations';

-- Update table statistics for query planner optimization
ANALYZE transactions;
ANALYZE transaction_items;
ANALYZE transaction_events;
ANALYZE accounts;

-- =====================================================================
-- VALIDATION QUERIES FOR INDEX EFFECTIVENESS
-- =====================================================================

-- Function to check index usage for transaction queries
CREATE OR REPLACE FUNCTION check_transaction_index_usage()
RETURNS TABLE(
    table_name TEXT,
    index_name TEXT,
    index_size TEXT,
    scans BIGINT,
    tuples_read BIGINT,
    tuples_fetched BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        s.relname::TEXT as table_name,
        i.relname::TEXT as index_name,
        pg_size_pretty(pg_relation_size(i.oid))::TEXT as index_size,
        s.idx_scan as scans,
        s.idx_tup_read as tuples_read,
        s.idx_tup_fetch as tuples_fetched
    FROM pg_stat_user_indexes s
    JOIN pg_class i ON i.oid = s.indexrelid
    WHERE s.schemaname = 'public' 
    AND s.relname LIKE '%transaction%'
    ORDER BY s.idx_scan DESC;
END;
$$ LANGUAGE plpgsql;

-- Grant necessary permissions
GRANT EXECUTE ON FUNCTION check_transaction_index_usage() TO waqiti_app;

-- =====================================================================
-- MAINTENANCE RECOMMENDATIONS
-- =====================================================================

-- Create monitoring view for transaction performance
CREATE OR REPLACE VIEW v_transaction_performance_metrics AS
SELECT 
    DATE(created_at) as transaction_date,
    status,
    COUNT(*) as transaction_count,
    AVG(amount) as avg_amount,
    SUM(amount) as total_amount,
    COUNT(DISTINCT from_user_id) as unique_users,
    COUNT(DISTINCT merchant_id) as unique_merchants,
    AVG(EXTRACT(EPOCH FROM (updated_at - created_at))) as avg_processing_time_seconds
FROM transactions
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(created_at), status
ORDER BY transaction_date DESC, status;

COMMENT ON VIEW v_transaction_performance_metrics IS 'Daily transaction performance metrics for monitoring and optimization';

-- Final message
DO $$
BEGIN
    RAISE NOTICE 'Transaction Service Performance Optimization completed successfully';
    RAISE NOTICE 'Added % indexes for N+1 query optimization and general performance', 
        (SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE 'idx_transaction%');
    RAISE NOTICE 'Run check_transaction_index_usage() to monitor index effectiveness';
END $$;