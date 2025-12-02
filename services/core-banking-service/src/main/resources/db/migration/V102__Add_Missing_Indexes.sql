-- V102: Add Missing Database Indexes
-- Description: Adds performance-critical indexes identified in forensic analysis
-- Author: Claude Code - Performance Optimization
-- Date: 2025-11-08

-- ==========================================
-- 1. TRANSACTION RECONCILIATION INDEX
-- ==========================================

-- Index for reconciliation queries using external_reference
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_external_reference
ON transactions(external_reference)
WHERE external_reference IS NOT NULL;

COMMENT ON INDEX idx_transactions_external_reference IS
'Optimizes reconciliation queries that lookup transactions by external reference';

-- ==========================================
-- 2. TRANSACTION DATE RANGE QUERIES
-- ==========================================

-- Composite index for common date range + status queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_created_status
ON transactions(created_at DESC, status)
INCLUDE (transaction_number, amount, currency);

COMMENT ON INDEX idx_transactions_created_status IS
'Optimizes date range queries with status filtering (covering index includes common fields)';

-- ==========================================
-- 3. LEDGER BALANCE CALCULATIONS
-- ==========================================

-- Index for ledger entry queries by date and account
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_account_date
ON ledger_entries(account_id, entry_date DESC)
INCLUDE (debit_amount, credit_amount, running_balance);

COMMENT ON INDEX idx_ledger_entries_account_date IS
'Optimizes balance calculation queries for account history';

-- ==========================================
-- 4. ACCOUNT LOOKUP OPTIMIZATIONS
-- ==========================================

-- Index for active account queries by user
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_user_status_active
ON accounts(user_id, status)
WHERE status = 'ACTIVE';

COMMENT ON INDEX idx_accounts_user_status_active IS
'Partial index for common query: active accounts by user';

-- Index for account currency queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_currency_status
ON accounts(currency, status)
WHERE status IN ('ACTIVE', 'PENDING');

COMMENT ON INDEX idx_accounts_currency_status IS
'Optimizes multi-currency account queries';

-- ==========================================
-- 5. FUND RESERVATION QUERIES
-- ==========================================

-- Index for finding reservations by transaction
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fund_reservations_transaction
ON fund_reservations(transaction_id, status)
WHERE status = 'ACTIVE';

COMMENT ON INDEX idx_fund_reservations_transaction IS
'Optimizes lookup of active reservations by transaction';

-- Index for expiration cleanup job
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fund_reservations_expires_status
ON fund_reservations(expires_at ASC)
WHERE status = 'ACTIVE';

COMMENT ON INDEX idx_fund_reservations_expires_status IS
'Optimizes scheduled cleanup job for expired reservations';

-- ==========================================
-- 6. TRANSACTION REVERSAL LOOKUPS
-- ==========================================

-- Index for finding original transactions that can be reversed
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reversal_lookup
ON transactions(status, completed_at DESC)
WHERE status = 'COMPLETED' AND reversal_transaction_id IS NULL;

COMMENT ON INDEX idx_transactions_reversal_lookup IS
'Optimizes queries for reversible transactions';

-- Index for reversal chain navigation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_original_txn
ON transactions(original_transaction_id)
WHERE original_transaction_id IS NOT NULL;

COMMENT ON INDEX idx_transactions_original_txn IS
'Optimizes reversal chain lookups';

-- ==========================================
-- 7. COMPLIANCE AND AUDIT QUERIES
-- ==========================================

-- Index for compliance hold transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_compliance
ON transactions(status, compliance_check_id)
WHERE status IN ('COMPLIANCE_HOLD', 'REQUIRES_APPROVAL');

COMMENT ON INDEX idx_transactions_compliance IS
'Optimizes compliance review queues';

-- Index for high-risk transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_risk_score
ON transactions(risk_score DESC, created_at DESC)
WHERE risk_score > 70;

COMMENT ON INDEX idx_transactions_risk_score IS
'Partial index for high-risk transaction monitoring';

-- ==========================================
-- 8. BATCH PROCESSING QUERIES
-- ==========================================

-- Index for batch transaction processing
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_batch
ON transactions(batch_id, status)
WHERE batch_id IS NOT NULL;

COMMENT ON INDEX idx_transactions_batch IS
'Optimizes batch processing status tracking';

-- ==========================================
-- 9. ACCOUNT ACTIVITY TRACKING
-- ==========================================

-- Index for recent account activity
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_last_activity
ON accounts(last_activity_at DESC NULLS LAST)
WHERE status = 'ACTIVE';

COMMENT ON INDEX idx_accounts_last_activity IS
'Optimizes dormant account detection and activity monitoring';

-- ==========================================
-- 10. IDEMPOTENCY KEY LOOKUPS
-- ==========================================

-- Index for idempotency key lookups (if not unique constraint already)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_idempotency_key
ON transactions(idempotency_key)
WHERE idempotency_key IS NOT NULL;

COMMENT ON INDEX idx_transactions_idempotency_key IS
'Optimizes duplicate transaction detection via idempotency keys';

-- ==========================================
-- VALIDATION AND STATISTICS
-- ==========================================

-- Analyze tables to update statistics after index creation
ANALYZE transactions;
ANALYZE accounts;
ANALYZE ledger_entries;
ANALYZE fund_reservations;

-- Report index sizes for monitoring
DO $$
DECLARE
    idx_record RECORD;
    total_size BIGINT := 0;
BEGIN
    RAISE NOTICE 'Index Size Report:';
    RAISE NOTICE '==================';

    FOR idx_record IN
        SELECT
            schemaname,
            tablename,
            indexname,
            pg_size_pretty(pg_relation_size(indexname::regclass)) as index_size,
            pg_relation_size(indexname::regclass) as size_bytes
        FROM pg_indexes
        WHERE schemaname = 'public'
        AND indexname LIKE 'idx_%'
        ORDER BY pg_relation_size(indexname::regclass) DESC
        LIMIT 20
    LOOP
        RAISE NOTICE '% on %.%: %',
            idx_record.indexname,
            idx_record.schemaname,
            idx_record.tablename,
            idx_record.index_size;
        total_size := total_size + idx_record.size_bytes;
    END LOOP;

    RAISE NOTICE '==================';
    RAISE NOTICE 'Total Index Size: %', pg_size_pretty(total_size);
END $$;

-- ==========================================
-- MONITORING QUERIES (for DBA reference)
-- ==========================================

-- Query to check index usage (run after production deployment)
/*
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched,
    pg_size_pretty(pg_relation_size(indexname::regclass)) as size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan ASC
LIMIT 20;
*/

-- Query to find unused indexes (candidates for removal)
/*
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexname::regclass)) as size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
AND idx_scan = 0
AND indexname NOT LIKE 'pg_%'
ORDER BY pg_relation_size(indexname::regclass) DESC;
*/

-- ==========================================
-- ROLLBACK SCRIPT (for reference)
-- ==========================================

-- To rollback this migration, run:
/*
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_external_reference;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_created_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_ledger_entries_account_date;
DROP INDEX CONCURRENTLY IF EXISTS idx_accounts_user_status_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_accounts_currency_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_fund_reservations_transaction;
DROP INDEX CONCURRENTLY IF EXISTS idx_fund_reservations_expires_status;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_reversal_lookup;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_original_txn;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_compliance;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_risk_score;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_batch;
DROP INDEX CONCURRENTLY IF EXISTS idx_accounts_last_activity;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_idempotency_key;
*/
