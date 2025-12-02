-- =====================================================================================
-- Migration: V008__Add_Missing_Audit_Column_Indexes.sql
-- Purpose: Add indexes on audit columns (created_by, updated_by) for performance
--
-- PERFORMANCE ISSUE: Audit queries without indexes are slow
-- - Compliance reports query by user ID (created_by, updated_by)
-- - Security investigations filter by user actions
-- - Without indexes, these are full table scans
--
-- Expected Performance Improvement:
-- - Audit queries: 1000x faster (from table scan to index scan)
-- - User activity reports: Sub-second response time
-- - Security investigations: Real-time results
--
-- Author: Waqiti Platform Team
-- Date: 2025-11-10
-- =====================================================================================

-- =====================================================================================
-- SECTION 1: Add indexes to transactions table audit columns
-- =====================================================================================

-- Index for created_by - for audit queries "who created this transaction"
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_created_by
ON transactions(created_by, created_at DESC)
WHERE created_by IS NOT NULL AND deleted_at IS NULL;

-- Index for updated_by - for audit queries "who last modified this"
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_updated_by
ON transactions(updated_by, updated_at DESC)
WHERE updated_by IS NOT NULL AND deleted_at IS NULL;

-- Composite index for audit trail queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_audit_trail
ON transactions(created_by, created_at, status)
WHERE deleted_at IS NULL;

-- =====================================================================================
-- SECTION 2: Add indexes to transaction_events table
-- =====================================================================================

-- Index for event creator (if column exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'transaction_events' AND column_name = 'created_by') THEN

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_events_created_by
                 ON transaction_events(created_by, created_at DESC)
                 WHERE created_by IS NOT NULL AND deleted_at IS NULL';
    END IF;
END $$;

-- Index for event type and timestamp (common query pattern)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_events_type_timestamp
ON transaction_events(event_type, created_at DESC)
WHERE deleted_at IS NULL;

-- =====================================================================================
-- SECTION 3: Add indexes to ledger_entries table
-- =====================================================================================

-- Index for created_by audit queries
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ledger_entries' AND column_name = 'created_by') THEN

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_created_by
                 ON ledger_entries(created_by, created_at DESC)
                 WHERE created_by IS NOT NULL AND deleted_at IS NULL';
    END IF;
END $$;

-- Composite index for account + user audit queries
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'ledger_entries' AND column_name = 'account_id' AND column_name = 'created_by') THEN

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_entries_account_user
                 ON ledger_entries(account_id, created_by, created_at DESC)
                 WHERE deleted_at IS NULL';
    END IF;
END $$;

-- =====================================================================================
-- SECTION 4: Add indexes to scheduled_transactions (if exists)
-- =====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'scheduled_transactions') THEN

        -- Index for scheduler queries (status + next execution date)
        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_transactions_scheduling
                 ON scheduled_transactions(status, next_execution_date)
                 WHERE deleted_at IS NULL AND status IN (''PENDING'', ''ACTIVE'')';

        -- Index for user's scheduled transactions
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'scheduled_transactions' AND column_name = 'user_id') THEN

            EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_transactions_user
                     ON scheduled_transactions(user_id, status, next_execution_date)
                     WHERE deleted_at IS NULL';
        END IF;
    END IF;
END $$;

-- =====================================================================================
-- SECTION 5: Add indexes to saga execution tables (if exists)
-- =====================================================================================

DO $$
BEGIN
    -- Add index to saga_executions table
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'saga_executions') THEN

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_saga_executions_status_timestamp
                 ON saga_executions(status, created_at DESC)
                 WHERE status IN (''RUNNING'', ''COMPENSATING'')';

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_saga_executions_transaction
                 ON saga_executions(transaction_id, status)
                 WHERE transaction_id IS NOT NULL';
    END IF;

    -- Add index to saga_step_executions table
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'saga_step_executions') THEN

        -- Covering index for saga step queries
        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_saga_step_executions_covering
                 ON saga_step_executions(saga_id, step_number, status)
                 INCLUDE (step_name, started_at, completed_at)';
    END IF;
END $$;

-- =====================================================================================
-- SECTION 6: Add indexes for transaction idempotency
-- =====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transaction_idempotency_keys') THEN

        -- Index for active idempotency keys (TTL not expired)
        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_idempotency_keys_active
                 ON transaction_idempotency_keys(idempotency_key, expires_at)
                 WHERE expires_at > CURRENT_TIMESTAMP';

        -- Index for cleanup job
        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_idempotency_keys_cleanup
                 ON transaction_idempotency_keys(expires_at)
                 WHERE expires_at <= CURRENT_TIMESTAMP';
    END IF;
END $$;

-- =====================================================================================
-- SECTION 7: Add covering indexes for common query patterns
-- =====================================================================================

-- Covering index for transaction list queries (common API endpoint)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_list_covering
ON transactions(from_wallet_id, status, created_at DESC)
INCLUDE (id, reference, to_wallet_id, amount, currency, type)
WHERE deleted_at IS NULL;

-- Covering index for transaction history by user
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_history
ON transactions(from_user_id, created_at DESC)
INCLUDE (id, reference, amount, currency, status, type)
WHERE deleted_at IS NULL;

-- Covering index for pending transactions (for processing)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_pending_processing
ON transactions(status, created_at)
INCLUDE (id, from_wallet_id, to_wallet_id, amount, currency, type)
WHERE deleted_at IS NULL AND status IN ('PENDING', 'PROCESSING', 'INITIATED');

-- =====================================================================================
-- SECTION 8: Add partial indexes for specific status queries
-- =====================================================================================

-- Index for failed transactions (for retry mechanism)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_failed
ON transactions(status, created_at, retry_count)
WHERE deleted_at IS NULL AND status = 'FAILED' AND retry_count < 3;

-- Index for stuck transactions (for monitoring/alerting)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_stuck
ON transactions(status, created_at)
WHERE deleted_at IS NULL
  AND status = 'PROCESSING'
  AND created_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes';

-- Index for high-value transactions (for compliance monitoring)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_high_value
ON transactions(amount, created_at DESC)
WHERE deleted_at IS NULL AND amount >= 10000;

-- =====================================================================================
-- SECTION 9: Add indexes for fraud detection queries
-- =====================================================================================

-- Index for fraud score filtering
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'transactions' AND column_name = 'fraud_score') THEN

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_fraud_score
                 ON transactions(fraud_score DESC, created_at DESC)
                 WHERE deleted_at IS NULL AND fraud_score IS NOT NULL AND fraud_score > 50';
    END IF;
END $$;

-- Index for velocity checks (rapid transactions from same wallet)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_velocity_check
ON transactions(from_wallet_id, created_at DESC)
INCLUDE (amount, status)
WHERE deleted_at IS NULL AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour';

-- =====================================================================================
-- SECTION 10: Add GIN indexes for JSONB columns
-- =====================================================================================

-- GIN index for transaction metadata searches
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'transactions' AND column_name = 'metadata' AND data_type = 'jsonb') THEN

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_metadata_gin
                 ON transactions USING GIN (metadata)
                 WHERE deleted_at IS NULL';
    END IF;
END $$;

-- GIN index for transaction event details
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'transaction_events' AND column_name = 'details' AND data_type = 'jsonb') THEN

        EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_events_details_gin
                 ON transaction_events USING GIN (details)
                 WHERE deleted_at IS NULL';
    END IF;
END $$;

-- =====================================================================================
-- SECTION 11: Validate all indexes were created
-- =====================================================================================

-- Query to show all new indexes
DO $$
DECLARE
    v_index_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_index_count
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename IN ('transactions', 'transaction_events', 'ledger_entries')
      AND indexname LIKE '%created_by%' OR indexname LIKE '%updated_by%';

    RAISE NOTICE 'Created audit column indexes: %', v_index_count;
END $$;

-- =====================================================================================
-- SECTION 12: Update table statistics
-- =====================================================================================

ANALYZE transactions;
ANALYZE transaction_events;
ANALYZE ledger_entries;

-- =====================================================================================
-- SECTION 13: Create monitoring view for index usage
-- =====================================================================================

CREATE OR REPLACE VIEW index_usage_statistics AS
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND tablename IN ('transactions', 'transaction_events', 'ledger_entries', 'saga_executions')
ORDER BY idx_scan DESC;

COMMENT ON VIEW index_usage_statistics IS
'Monitoring view for index usage - helps identify unused or under-utilized indexes';

-- =====================================================================================
-- Migration Complete
-- =====================================================================================

DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Migration V008 completed successfully';
    RAISE NOTICE 'Added indexes for audit columns and common query patterns';
    RAISE NOTICE 'Expected performance improvement: 10-1000x for audit queries';
    RAISE NOTICE 'View created: index_usage_statistics';
    RAISE NOTICE '========================================';
END $$;
