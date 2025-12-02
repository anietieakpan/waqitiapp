-- Performance optimization indexes for wallet service high-traffic queries
-- This migration adds specialized indexes for wallet operations

-- =====================================================================
-- WALLET BALANCE AND TRANSACTION INDEXES
-- =====================================================================

-- Wallet balance queries (most critical)
CREATE INDEX IF NOT EXISTS idx_wallets_user_currency ON wallets(user_id, currency) 
    INCLUDE (id, balance, available_balance, status);

-- Active wallets only (most queries)
CREATE INDEX IF NOT EXISTS idx_wallets_active ON wallets(user_id, currency, status) 
    WHERE status = 'ACTIVE';

-- Wallet transaction history
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_date ON wallet_transactions(wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_user_date ON wallet_transactions(user_id, created_at DESC);

-- Transaction types and status
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_type_status ON wallet_transactions(transaction_type, status, created_at DESC);

-- =====================================================================
-- BALANCE AUDIT AND RECONCILIATION INDEXES
-- =====================================================================

-- Balance audit trail
CREATE INDEX IF NOT EXISTS idx_balance_audit_wallet_date ON balance_audit_log(wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_balance_audit_date_range ON balance_audit_log(created_at) 
    WHERE created_at >= NOW() - INTERVAL '30 days';

-- Reconciliation queries
CREATE INDEX IF NOT EXISTS idx_balance_audit_discrepancies ON balance_audit_log(wallet_id, created_at DESC) 
    WHERE old_balance != new_balance;

-- Daily reconciliation support
CREATE INDEX IF NOT EXISTS idx_balance_audit_daily ON balance_audit_log(
    DATE_TRUNC('day', created_at), 
    wallet_id
);

-- =====================================================================
-- FUND RESERVATION INDEXES
-- =====================================================================

-- Active reservations
CREATE INDEX IF NOT EXISTS idx_fund_reservations_active ON fund_reservations(wallet_id, created_at DESC) 
    WHERE status = 'ACTIVE';

-- Reservation expiry cleanup
CREATE INDEX IF NOT EXISTS idx_fund_reservations_expired ON fund_reservations(expires_at) 
    WHERE expires_at < NOW() AND status = 'ACTIVE';

-- User reservation queries
CREATE INDEX IF NOT EXISTS idx_fund_reservations_user ON fund_reservations(user_id, status, created_at DESC);

-- Reservation amount analysis
CREATE INDEX IF NOT EXISTS idx_fund_reservations_amount ON fund_reservations(wallet_id, amount DESC, created_at DESC) 
    WHERE status = 'ACTIVE';

-- =====================================================================
-- TRANSFER AND ATOMIC OPERATION INDEXES
-- =====================================================================

-- Atomic transfer tracking
CREATE INDEX IF NOT EXISTS idx_atomic_transfers_status ON atomic_transfers(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_atomic_transfers_wallets ON atomic_transfers(source_wallet_id, target_wallet_id, created_at DESC);

-- Failed transfers for retry
CREATE INDEX IF NOT EXISTS idx_atomic_transfers_failed ON atomic_transfers(created_at DESC) 
    WHERE status = 'FAILED';

-- Pending transfers monitoring
CREATE INDEX IF NOT EXISTS idx_atomic_transfers_pending ON atomic_transfers(created_at ASC) 
    WHERE status = 'PENDING';

-- =====================================================================
-- CURRENCY AND EXCHANGE RATE INDEXES
-- =====================================================================

-- Multi-currency wallet queries
CREATE INDEX IF NOT EXISTS idx_wallets_currency_balance ON wallets(currency, balance DESC) 
    WHERE status = 'ACTIVE';

-- Exchange rate history
CREATE INDEX IF NOT EXISTS idx_exchange_rates_currency_date ON exchange_rates(from_currency, to_currency, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_exchange_rates_latest ON exchange_rates(from_currency, to_currency, created_at DESC) 
    INCLUDE (rate, spread);

-- =====================================================================
-- WALLET LIMITS AND COMPLIANCE INDEXES
-- =====================================================================

-- Transaction limits
CREATE INDEX IF NOT EXISTS idx_wallet_limits_user ON wallet_limits(user_id, limit_type) 
    INCLUDE (daily_limit, monthly_limit, remaining_daily, remaining_monthly);

-- Limit usage tracking
CREATE INDEX IF NOT EXISTS idx_limit_usage_wallet_date ON limit_usage_log(wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_limit_usage_daily ON limit_usage_log(
    wallet_id, 
    DATE_TRUNC('day', created_at)
) WHERE created_at >= CURRENT_DATE - INTERVAL '30 days';

-- =====================================================================
-- FRAUD DETECTION AND SECURITY INDEXES
-- =====================================================================

-- Suspicious transaction patterns
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_suspicious ON wallet_transactions(
    user_id, 
    amount DESC, 
    created_at DESC
) WHERE amount > 5000.00;

-- Rapid transaction detection
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_rapid ON wallet_transactions(
    user_id, 
    created_at DESC
) WHERE created_at >= NOW() - INTERVAL '1 hour';

-- Cross-border transactions
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_cross_border ON wallet_transactions(
    user_id,
    created_at DESC
) WHERE from_country != to_country;

-- =====================================================================
-- WALLET STATUS AND LIFECYCLE INDEXES
-- =====================================================================

-- Wallet status changes
CREATE INDEX IF NOT EXISTS idx_wallets_status_updated ON wallets(status, updated_at DESC);

-- Frozen/Suspended wallets
CREATE INDEX IF NOT EXISTS idx_wallets_frozen ON wallets(user_id, updated_at DESC) 
    WHERE status IN ('FROZEN', 'SUSPENDED');

-- Inactive wallets for cleanup
CREATE INDEX IF NOT EXISTS idx_wallets_inactive ON wallets(last_activity_at) 
    WHERE last_activity_at < NOW() - INTERVAL '2 years' AND status = 'ACTIVE';

-- =====================================================================
-- ANALYTICS AND REPORTING INDEXES
-- =====================================================================

-- Daily transaction volume
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_daily_volume ON wallet_transactions(
    DATE_TRUNC('day', created_at),
    currency,
    amount
) WHERE status = 'COMPLETED';

-- Monthly wallet analytics
CREATE INDEX IF NOT EXISTS idx_wallets_monthly_stats ON wallet_transactions(
    DATE_TRUNC('month', created_at),
    user_id,
    currency
) WHERE status = 'COMPLETED';

-- Top users by transaction volume
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_volume_ranking ON wallet_transactions(
    user_id,
    amount DESC,
    created_at DESC
) WHERE status = 'COMPLETED' AND created_at >= NOW() - INTERVAL '30 days';

-- =====================================================================
-- COVERING INDEXES FOR API ENDPOINTS
-- =====================================================================

-- Wallet balance API
CREATE INDEX IF NOT EXISTS idx_wallets_balance_api ON wallets(user_id) 
    INCLUDE (id, currency, balance, available_balance, status, updated_at);

-- Transaction history API
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_history_api ON wallet_transactions(wallet_id, created_at DESC) 
    INCLUDE (id, transaction_type, amount, currency, description, status, reference_id);

-- User wallet summary API
CREATE INDEX IF NOT EXISTS idx_wallets_user_summary ON wallets(user_id, status) 
    INCLUDE (id, currency, balance, available_balance, created_at);

-- =====================================================================
-- PARTIAL INDEXES FOR PERFORMANCE
-- =====================================================================

-- Active transactions only
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_active ON wallet_transactions(created_at DESC) 
    WHERE status IN ('PENDING', 'PROCESSING', 'COMPLETED');

-- Recent transactions (90 days)
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_recent ON wallet_transactions(wallet_id, created_at DESC) 
    WHERE created_at >= NOW() - INTERVAL '90 days';

-- Large transactions
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_large ON wallet_transactions(amount DESC, created_at DESC) 
    WHERE amount > 1000.00;

-- =====================================================================
-- BTREE INDEXES FOR RANGE QUERIES
-- =====================================================================

-- Balance range queries
CREATE INDEX IF NOT EXISTS idx_wallets_balance_ranges ON wallets(balance) 
    WHERE balance > 0 AND status = 'ACTIVE';

-- Transaction amount ranges
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_amount_ranges ON wallet_transactions(amount, created_at DESC) 
    WHERE status = 'COMPLETED';

-- Date range optimization
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_date_range ON wallet_transactions(created_at) 
    WHERE created_at >= '2023-01-01';

-- =====================================================================
-- SPECIALIZED QUERY INDEXES
-- =====================================================================

-- Find wallets needing rebalancing
CREATE INDEX IF NOT EXISTS idx_wallets_rebalancing ON wallets(currency, balance) 
    WHERE ABS(balance - target_balance) > threshold_amount;

-- Dormant account detection
CREATE INDEX IF NOT EXISTS idx_wallets_dormant ON wallets(user_id, last_activity_at) 
    WHERE last_activity_at < NOW() - INTERVAL '1 year' AND balance > 0;

-- Multi-wallet users
CREATE INDEX IF NOT EXISTS idx_wallets_multi_currency_users ON wallets(user_id, currency) 
    WHERE status = 'ACTIVE';

-- =====================================================================
-- CONCURRENT ACCESS OPTIMIZATION
-- =====================================================================

-- Reduce lock contention on high-volume operations
CREATE INDEX IF NOT EXISTS idx_wallets_concurrent_updates ON wallets(id, version) 
    WHERE status = 'ACTIVE';

-- Balance update optimization
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_balance_calc ON wallet_transactions(wallet_id, created_at ASC) 
    INCLUDE (amount, transaction_type, status);

-- =====================================================================
-- MAINTENANCE AND CLEANUP INDEXES
-- =====================================================================

-- Old completed transactions for archival
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_archive ON wallet_transactions(created_at) 
    WHERE status = 'COMPLETED' AND created_at < NOW() - INTERVAL '7 years';

-- Failed transaction cleanup
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_cleanup ON wallet_transactions(created_at) 
    WHERE status = 'FAILED' AND created_at < NOW() - INTERVAL '30 days';

-- Expired reservations cleanup
CREATE INDEX IF NOT EXISTS idx_fund_reservations_cleanup ON fund_reservations(expires_at) 
    WHERE status = 'EXPIRED';

-- =====================================================================
-- STATISTICS OPTIMIZATION
-- =====================================================================

-- Update statistics for query planner
ANALYZE wallets;
ANALYZE wallet_transactions;
ANALYZE balance_audit_log;
ANALYZE fund_reservations;
ANALYZE atomic_transfers;
ANALYZE exchange_rates;
ANALYZE wallet_limits;

-- Set high statistics targets for critical columns
ALTER TABLE wallets ALTER COLUMN user_id SET STATISTICS 1000;
ALTER TABLE wallets ALTER COLUMN currency SET STATISTICS 1000;
ALTER TABLE wallets ALTER COLUMN status SET STATISTICS 1000;
ALTER TABLE wallets ALTER COLUMN balance SET STATISTICS 1000;

ALTER TABLE wallet_transactions ALTER COLUMN wallet_id SET STATISTICS 1000;
ALTER TABLE wallet_transactions ALTER COLUMN user_id SET STATISTICS 1000;
ALTER TABLE wallet_transactions ALTER COLUMN status SET STATISTICS 1000;
ALTER TABLE wallet_transactions ALTER COLUMN created_at SET STATISTICS 1000;

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON INDEX idx_wallets_user_currency IS 'Primary index for wallet lookups by user and currency';
COMMENT ON INDEX idx_wallet_transactions_wallet_date IS 'Optimizes wallet transaction history queries';
COMMENT ON INDEX idx_fund_reservations_active IS 'Tracks active fund reservations for balance calculations';
COMMENT ON INDEX idx_atomic_transfers_status IS 'Monitors atomic transfer operations';
COMMENT ON INDEX idx_wallets_balance_api IS 'Covering index for wallet balance API endpoint';
COMMENT ON INDEX idx_wallet_transactions_suspicious IS 'Supports fraud detection queries';
COMMENT ON INDEX idx_wallets_concurrent_updates IS 'Optimizes concurrent wallet updates';
COMMENT ON INDEX idx_wallet_transactions_daily_volume IS 'Supports daily analytics queries';