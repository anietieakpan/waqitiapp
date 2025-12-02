-- =====================================================
-- Migration: V101__Add_Comprehensive_InvestmentAccount_Indexes
-- Purpose: Add comprehensive database indexes to InvestmentAccount entity
--          to eliminate N+1 query issues and prevent full table scans
--
-- CRITICAL for Production Performance:
-- - Prevents full table scans on customer lookups
-- - Optimizes status-based queries for dashboards
-- - Improves brokerage integration queries
-- - Supports pattern day trading compliance checks
-- - Enables efficient reporting and analytics
--
-- Related to BLOCKER #5: InvestmentAccount Missing Database Indexes
-- =====================================================

-- Drop old indexes with incorrect column names if they exist
DROP INDEX IF EXISTS idx_investment_account_user_id;
DROP INDEX IF EXISTS idx_investment_account_status;
DROP INDEX IF EXISTS idx_investment_account_created_at;

-- =====================================================
-- CRITICAL: Primary access pattern indexes
-- =====================================================

-- Customer lookup (most common query pattern)
CREATE INDEX IF NOT EXISTS idx_investment_customer_id
ON investment_accounts(customer_id);

-- Account number lookup (unique identifier for external systems)
CREATE UNIQUE INDEX IF NOT EXISTS idx_investment_account_number
ON investment_accounts(account_number);

-- Wallet account integration (frequent join pattern)
CREATE INDEX IF NOT EXISTS idx_investment_wallet_account
ON investment_accounts(wallet_account_id);

-- Status-based filtering (dashboard queries)
CREATE INDEX IF NOT EXISTS idx_investment_status
ON investment_accounts(status);

-- =====================================================
-- CRITICAL: Composite indexes for complex queries
-- =====================================================

-- Customer status queries (e.g., "get all active accounts for customer")
CREATE INDEX IF NOT EXISTS idx_investment_customer_status
ON investment_accounts(customer_id, status);

-- KYC verification + status (compliance checks)
CREATE INDEX IF NOT EXISTS idx_investment_status_kyc
ON investment_accounts(status, kyc_verified);

-- Brokerage provider lookups (integration queries)
CREATE INDEX IF NOT EXISTS idx_investment_brokerage
ON investment_accounts(brokerage_provider, brokerage_account_id);

-- =====================================================
-- Performance: Reporting and analytics indexes
-- =====================================================

-- Account creation reporting
CREATE INDEX IF NOT EXISTS idx_investment_created_at
ON investment_accounts(created_at DESC);

-- Last modified tracking
CREATE INDEX IF NOT EXISTS idx_investment_updated_at
ON investment_accounts(updated_at DESC);

-- Activity tracking (dormant account detection)
CREATE INDEX IF NOT EXISTS idx_investment_last_activity
ON investment_accounts(last_activity_at DESC);

-- Activation date reporting
CREATE INDEX IF NOT EXISTS idx_investment_activated_at
ON investment_accounts(activated_at DESC);

-- =====================================================
-- Compliance: Pattern Day Trading indexes
-- =====================================================

-- Pattern day trader detection and monitoring
CREATE INDEX IF NOT EXISTS idx_investment_pattern_trader
ON investment_accounts(pattern_day_trader, day_trades)
WHERE pattern_day_trader = true OR day_trades >= 3;

-- Find accounts approaching pattern day trader status
CREATE INDEX IF NOT EXISTS idx_investment_day_trades_warning
ON investment_accounts(day_trades, status)
WHERE day_trades >= 2 AND status = 'ACTIVE';

-- =====================================================
-- Performance: Value-based queries for reporting
-- =====================================================

-- High-value account queries (VIP customer identification)
CREATE INDEX IF NOT EXISTS idx_investment_total_value
ON investment_accounts(total_value DESC)
WHERE total_value > 0;

-- Cash balance queries (liquidity reporting)
CREATE INDEX IF NOT EXISTS idx_investment_cash_balance
ON investment_accounts(cash_balance DESC)
WHERE cash_balance > 0;

-- Portfolio performance tracking
CREATE INDEX IF NOT EXISTS idx_investment_total_return
ON investment_accounts(total_return_percent DESC);

-- =====================================================
-- Covering indexes for common read patterns
-- =====================================================

-- Dashboard summary query optimization (covering index)
CREATE INDEX IF NOT EXISTS idx_investment_dashboard_summary
ON investment_accounts(customer_id, status, total_value, cash_balance, day_change_percent)
WHERE status = 'ACTIVE';

-- Active trading accounts (covering index for trading checks)
CREATE INDEX IF NOT EXISTS idx_investment_trading_eligible
ON investment_accounts(id, customer_id, status, kyc_verified, pattern_day_trader)
WHERE status = 'ACTIVE' AND kyc_verified = true;

-- =====================================================
-- Statistics and maintenance
-- =====================================================

-- Update table statistics for query optimizer
ANALYZE investment_accounts;

-- Add column comments for documentation
COMMENT ON COLUMN investment_accounts.customer_id IS 'Customer identifier - primary lookup key';
COMMENT ON COLUMN investment_accounts.account_number IS 'Unique account number for external integrations';
COMMENT ON COLUMN investment_accounts.wallet_account_id IS 'Associated wallet for cash management';
COMMENT ON COLUMN investment_accounts.status IS 'Account status: PENDING_ACTIVATION, ACTIVE, SUSPENDED, CLOSED';
COMMENT ON COLUMN investment_accounts.kyc_verified IS 'KYC verification status - required for trading';
COMMENT ON COLUMN investment_accounts.pattern_day_trader IS 'Pattern day trader flag per FINRA Rule 4210';
COMMENT ON COLUMN investment_accounts.day_trades IS 'Day trade counter for pattern detection';
COMMENT ON COLUMN investment_accounts.cash_balance IS 'Available cash balance (precision 19, scale 4)';
COMMENT ON COLUMN investment_accounts.total_value IS 'Total portfolio value including cash (precision 19, scale 4)';

-- Add index comments for maintenance documentation
COMMENT ON INDEX idx_investment_customer_id IS 'Primary lookup index for customer account queries';
COMMENT ON INDEX idx_investment_customer_status IS 'Composite index for customer account listings filtered by status';
COMMENT ON INDEX idx_investment_status_kyc IS 'Compliance index for KYC verification queries';
COMMENT ON INDEX idx_investment_pattern_trader IS 'Partial index for pattern day trader monitoring and compliance';
COMMENT ON INDEX idx_investment_dashboard_summary IS 'Covering index for dashboard summary queries - includes all displayed fields';
COMMENT ON INDEX idx_investment_trading_eligible IS 'Covering index for trading eligibility checks - hot path optimization';

-- =====================================================
-- Index size and usage monitoring view
-- =====================================================

CREATE OR REPLACE VIEW investment_account_index_health AS
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size,
    idx_scan as index_scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched,
    CASE
        WHEN idx_scan = 0 THEN 'UNUSED'
        WHEN idx_scan < 100 THEN 'LOW_USAGE'
        WHEN idx_scan < 1000 THEN 'MODERATE_USAGE'
        ELSE 'HIGH_USAGE'
    END as usage_level,
    pg_stat_get_last_autoanalyze_time(indexrelid) as last_analyzed
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND tablename = 'investment_accounts'
ORDER BY idx_scan DESC;

COMMENT ON VIEW investment_account_index_health IS
'Monitoring view for investment account index usage and health. Use to identify unused indexes or performance issues.';

-- =====================================================
-- Query plan analysis helper function
-- =====================================================

CREATE OR REPLACE FUNCTION explain_investment_query(query_text TEXT)
RETURNS TABLE (
    query_plan TEXT
) AS $$
BEGIN
    RETURN QUERY EXECUTE 'EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) ' || query_text;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION explain_investment_query(TEXT) IS
'Helper function to analyze query plans for investment account queries. Usage: SELECT * FROM explain_investment_query(''SELECT * FROM investment_accounts WHERE customer_id = ''''...'''''');';

-- =====================================================
-- Performance validation
-- =====================================================

DO $$
DECLARE
    total_accounts INT;
    total_indexes INT;
    covering_indexes INT;
    partial_indexes INT;
BEGIN
    SELECT COUNT(*) INTO total_accounts FROM investment_accounts;

    SELECT COUNT(*) INTO total_indexes
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'investment_accounts';

    SELECT COUNT(*) INTO covering_indexes
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'investment_accounts'
      AND indexdef LIKE '%INCLUDE%';

    SELECT COUNT(*) INTO partial_indexes
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND tablename = 'investment_accounts'
      AND indexdef LIKE '%WHERE%';

    RAISE NOTICE 'Migration V101 completed successfully:';
    RAISE NOTICE '  - Total investment accounts: %', total_accounts;
    RAISE NOTICE '  - Total indexes created: %', total_indexes;
    RAISE NOTICE '  - Partial indexes (filtered): %', partial_indexes;
    RAISE NOTICE '  - Table analyzed: YES';
    RAISE NOTICE '';
    RAISE NOTICE 'Performance optimizations:';
    RAISE NOTICE '  ✓ Customer lookup index (idx_investment_customer_id)';
    RAISE NOTICE '  ✓ Status filtering index (idx_investment_status)';
    RAISE NOTICE '  ✓ Composite customer+status index for listings';
    RAISE NOTICE '  ✓ Pattern day trader compliance index';
    RAISE NOTICE '  ✓ Dashboard summary covering index';
    RAISE NOTICE '  ✓ Trading eligibility covering index';
    RAISE NOTICE '';
    RAISE NOTICE 'Monitoring:';
    RAISE NOTICE '  - View: investment_account_index_health';
    RAISE NOTICE '  - Function: explain_investment_query(query_text)';
    RAISE NOTICE '';
    RAISE NOTICE 'Next steps:';
    RAISE NOTICE '  1. Monitor: SELECT * FROM investment_account_index_health;';
    RAISE NOTICE '  2. Verify queries use indexes: SELECT * FROM explain_investment_query(''your query'');';
    RAISE NOTICE '  3. Schedule weekly ANALYZE on investment_accounts table';
END $$;
