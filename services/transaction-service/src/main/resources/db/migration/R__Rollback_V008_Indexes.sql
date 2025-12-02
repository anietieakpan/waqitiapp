-- =====================================================================================
-- Rollback Migration: R__Rollback_V008_Indexes.sql
-- Purpose: Rollback V008__Add_Missing_Audit_Column_Indexes.sql if needed
--
-- NOTE: This rollback simply drops all indexes created by V008
-- No data loss occurs when dropping indexes (only performance impact)
--
-- Author: Waqiti Platform Team
-- Date: 2025-11-10
-- =====================================================================================

-- =====================================================================================
-- SECTION 1: Drop view created by V008
-- =====================================================================================

DROP VIEW IF EXISTS index_usage_statistics CASCADE;

-- =====================================================================================
-- SECTION 2: Drop audit column indexes from transactions
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_created_by;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_updated_by;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_audit_trail;

-- =====================================================================================
-- SECTION 3: Drop indexes from transaction_events
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_transaction_events_created_by;
DROP INDEX CONCURRENTLY IF EXISTS idx_transaction_events_type_timestamp;

-- =====================================================================================
-- SECTION 4: Drop indexes from ledger_entries
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_ledger_entries_created_by;
DROP INDEX CONCURRENTLY IF EXISTS idx_ledger_entries_account_user;

-- =====================================================================================
-- SECTION 5: Drop scheduled_transactions indexes
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_scheduled_transactions_scheduling;
DROP INDEX CONCURRENTLY IF EXISTS idx_scheduled_transactions_user;

-- =====================================================================================
-- SECTION 6: Drop saga execution indexes
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_saga_executions_status_timestamp;
DROP INDEX CONCURRENTLY IF EXISTS idx_saga_executions_transaction;
DROP INDEX CONCURRENTLY IF EXISTS idx_saga_step_executions_covering;

-- =====================================================================================
-- SECTION 7: Drop idempotency indexes
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_idempotency_keys_active;
DROP INDEX CONCURRENTLY IF EXISTS idx_idempotency_keys_cleanup;

-- =====================================================================================
-- SECTION 8: Drop covering indexes
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_list_covering;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_user_history;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_pending_processing;

-- =====================================================================================
-- SECTION 9: Drop partial indexes for specific statuses
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_failed;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_stuck;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_high_value;

-- =====================================================================================
-- SECTION 10: Drop fraud detection indexes
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_fraud_score;
DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_velocity_check;

-- =====================================================================================
-- SECTION 11: Drop GIN indexes
-- =====================================================================================

DROP INDEX CONCURRENTLY IF EXISTS idx_transactions_metadata_gin;
DROP INDEX CONCURRENTLY IF EXISTS idx_transaction_events_details_gin;

-- =====================================================================================
-- Rollback Complete
-- =====================================================================================

DO $$
BEGIN
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Rollback of V008 completed';
    RAISE NOTICE 'All audit column indexes dropped';
    RAISE NOTICE 'WARNING: Query performance may degrade';
    RAISE NOTICE 'WARNING: Audit queries will be slower';
    RAISE NOTICE '========================================';
END $$;
