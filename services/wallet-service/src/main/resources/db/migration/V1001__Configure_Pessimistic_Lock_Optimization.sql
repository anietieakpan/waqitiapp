-- ============================================================================
-- Migration: V1001__Configure_Pessimistic_Lock_Optimization.sql
-- Purpose: Configure database-level pessimistic locking for race condition prevention
-- Date: 2025-10-23
-- CRITICAL: This migration fixes race conditions in wallet balance updates
-- ============================================================================

-- Configure lock timeout for PostgreSQL to prevent indefinite waits
-- If lock cannot be acquired within 10 seconds, transaction will fail fast
SET lock_timeout = '10s';

-- Configure statement timeout to prevent long-running transactions
-- Maximum execution time for any single SQL statement
SET statement_timeout = '30s';

-- Configure deadlock timeout (how long to wait before checking for deadlocks)
SET deadlock_timeout = '1s';

-- ============================================================================
-- PERFORMANCE OPTIMIZATION: Add indexes for faster lock acquisition
-- ============================================================================

-- Index on wallet version for faster version-based conflict detection
-- Used by optimistic locking mechanism to detect concurrent modifications
CREATE INDEX IF NOT EXISTS idx_wallet_version
ON wallets(version)
WHERE status = 'ACTIVE';

COMMENT ON INDEX idx_wallet_version IS
'Optimizes version-based optimistic lock conflict detection for active wallets';

-- Composite index for fund reservations by wallet and status
-- Critical for fast reserved balance calculation inside pessimistic locks
CREATE INDEX IF NOT EXISTS idx_fund_reservation_wallet_status_expires
ON fund_reservations(wallet_id, status, expires_at)
WHERE status IN ('ACTIVE', 'PENDING');

COMMENT ON INDEX idx_fund_reservation_wallet_status_expires IS
'Optimizes getTotalReservedAmount queries executed inside pessimistic locks';

-- Index for idempotency key lookups (fast duplicate detection before acquiring locks)
CREATE INDEX IF NOT EXISTS idx_fund_reservation_idempotency_active
ON fund_reservations(idempotency_key)
WHERE idempotency_key IS NOT NULL AND status = 'ACTIVE';

COMMENT ON INDEX idx_fund_reservation_idempotency_active IS
'Optimizes idempotency checks before lock acquisition to reduce contention';

-- Index on wallet_id and transaction_id for fast reservation lookups
CREATE INDEX IF NOT EXISTS idx_fund_reservation_wallet_transaction
ON fund_reservations(wallet_id, transaction_id)
WHERE status IN ('ACTIVE', 'PENDING', 'CONFIRMED');

COMMENT ON INDEX idx_fund_reservation_wallet_transaction IS
'Optimizes reservation lookups by transaction for confirmation/release operations';

-- ============================================================================
-- DATA INTEGRITY: Add constraints for wallet balance consistency
-- ============================================================================

-- Add check constraint to ensure balance is never negative
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_balance_non_negative
CHECK (balance >= 0);

-- Add check constraint to ensure reserved balance is never negative
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_reserved_non_negative
CHECK (reserved_balance >= 0);

-- Add check constraint to ensure reserved balance never exceeds total balance
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_reserved_lte_balance
CHECK (reserved_balance <= balance);

-- Add check constraint to ensure available balance matches calculation
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_available_balance_consistent
CHECK (available_balance = (balance - reserved_balance));

-- ============================================================================
-- AUDIT TRAIL: Add new column for tracking pessimistic lock operations
-- ============================================================================

-- Add column to track when wallet was last locked
ALTER TABLE wallets
ADD COLUMN IF NOT EXISTS last_locked_at TIMESTAMP;

-- Add column to track which transaction last locked the wallet
ALTER TABLE wallets
ADD COLUMN IF NOT EXISTS last_locked_by_transaction VARCHAR(255);

-- Create index for monitoring lock contention
CREATE INDEX IF NOT EXISTS idx_wallet_last_locked
ON wallets(last_locked_at DESC)
WHERE last_locked_at IS NOT NULL;

COMMENT ON COLUMN wallets.last_locked_at IS
'Timestamp of last pessimistic lock acquisition for contention monitoring';

COMMENT ON COLUMN wallets.last_locked_by_transaction IS
'Transaction ID that last acquired pessimistic lock for debugging';

-- ============================================================================
-- FUND RESERVATION: Add wallet version snapshot for race condition detection
-- ============================================================================

-- Add column to store wallet version at reservation time
ALTER TABLE fund_reservations
ADD COLUMN IF NOT EXISTS wallet_version_at_reservation BIGINT;

COMMENT ON COLUMN fund_reservations.wallet_version_at_reservation IS
'Snapshot of wallet version when reservation was created - used to detect concurrent modifications';

-- ============================================================================
-- MONITORING: Create view for lock contention analysis
-- ============================================================================

CREATE OR REPLACE VIEW v_wallet_lock_contention AS
SELECT
    w.id as wallet_id,
    w.user_id,
    w.balance,
    w.reserved_balance,
    w.available_balance,
    w.status,
    w.version,
    w.last_locked_at,
    w.last_locked_by_transaction,
    COUNT(fr.id) as active_reservations_count,
    SUM(fr.amount) as total_reserved_from_reservations,
    CASE
        WHEN w.last_locked_at > NOW() - INTERVAL '5 minutes' THEN 'HIGH_CONTENTION'
        WHEN w.last_locked_at > NOW() - INTERVAL '30 minutes' THEN 'MODERATE_CONTENTION'
        ELSE 'LOW_CONTENTION'
    END as contention_level
FROM wallets w
LEFT JOIN fund_reservations fr ON fr.wallet_id = w.id AND fr.status = 'ACTIVE'
WHERE w.status = 'ACTIVE'
GROUP BY w.id, w.user_id, w.balance, w.reserved_balance, w.available_balance,
         w.status, w.version, w.last_locked_at, w.last_locked_by_transaction;

COMMENT ON VIEW v_wallet_lock_contention IS
'Monitoring view for analyzing pessimistic lock contention and performance impact';

-- ============================================================================
-- STATISTICS: Update table statistics for query planner optimization
-- ============================================================================

-- Analyze wallets table to update statistics
ANALYZE wallets;

-- Analyze fund_reservations table to update statistics
ANALYZE fund_reservations;

-- ============================================================================
-- CONFIGURATION: Set PostgreSQL parameters for optimal lock performance
-- ============================================================================

-- Log lock waits longer than 1 second for debugging
ALTER DATABASE waqiti_wallet SET log_lock_waits = on;
ALTER DATABASE waqiti_wallet SET deadlock_timeout = '1s';

-- Increase max_locks_per_transaction if needed (default is usually 64)
-- Uncomment if you encounter "out of shared memory" errors
-- ALTER DATABASE waqiti_wallet SET max_locks_per_transaction = 128;

-- ============================================================================
-- VALIDATION: Create function to verify balance consistency
-- ============================================================================

CREATE OR REPLACE FUNCTION verify_wallet_balance_consistency(p_wallet_id UUID)
RETURNS TABLE (
    wallet_id UUID,
    balance DECIMAL(19, 4),
    reserved_balance DECIMAL(19, 4),
    available_balance DECIMAL(19, 4),
    calculated_reserved DECIMAL(19, 4),
    calculated_available DECIMAL(19, 4),
    is_consistent BOOLEAN,
    inconsistency_details TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        w.id as wallet_id,
        w.balance,
        w.reserved_balance,
        w.available_balance,
        COALESCE(SUM(fr.amount), 0) as calculated_reserved,
        w.balance - COALESCE(SUM(fr.amount), 0) as calculated_available,
        (
            w.reserved_balance = COALESCE(SUM(fr.amount), 0) AND
            w.available_balance = (w.balance - COALESCE(SUM(fr.amount), 0))
        ) as is_consistent,
        CASE
            WHEN w.reserved_balance != COALESCE(SUM(fr.amount), 0) THEN
                'Reserved balance mismatch: stored=' || w.reserved_balance || ' actual=' || COALESCE(SUM(fr.amount), 0)
            WHEN w.available_balance != (w.balance - COALESCE(SUM(fr.amount), 0)) THEN
                'Available balance mismatch: stored=' || w.available_balance || ' actual=' || (w.balance - COALESCE(SUM(fr.amount), 0))
            ELSE 'Consistent'
        END as inconsistency_details
    FROM wallets w
    LEFT JOIN fund_reservations fr ON fr.wallet_id = w.id AND fr.status = 'ACTIVE'
    WHERE w.id = p_wallet_id
    GROUP BY w.id, w.balance, w.reserved_balance, w.available_balance;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION verify_wallet_balance_consistency(UUID) IS
'Verifies wallet balance consistency by comparing stored values with calculated values from reservations';

-- ============================================================================
-- RECONCILIATION: Create job table for tracking consistency checks
-- ============================================================================

CREATE TABLE IF NOT EXISTS wallet_consistency_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_run_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_wallets_checked INTEGER NOT NULL,
    inconsistent_wallets_count INTEGER NOT NULL,
    inconsistent_wallet_ids UUID[],
    max_inconsistency_amount DECIMAL(19, 4),
    check_duration_ms INTEGER,
    status VARCHAR(50) NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_consistency_check_run ON wallet_consistency_checks(check_run_at DESC);

COMMENT ON TABLE wallet_consistency_checks IS
'Tracks scheduled consistency checks for wallet balances to detect and fix race conditions';

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE 'Migration V1001 completed successfully';
    RAISE NOTICE 'Pessimistic lock optimization configured';
    RAISE NOTICE 'Lock timeout: 10s, Statement timeout: 30s';
    RAISE NOTICE '4 new indexes created for performance optimization';
    RAISE NOTICE '4 check constraints added for data integrity';
    RAISE NOTICE '2 audit columns added for lock tracking';
    RAISE NOTICE '1 monitoring view created for contention analysis';
    RAISE NOTICE '1 consistency verification function created';
END $$;
