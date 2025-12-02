-- Specialized Indexes for Time-Series, Geospatial, and Advanced Query Patterns
-- This migration adds specialized indexes for complex query optimization

-- =====================================================
-- TIME-SERIES INDEXES WITH BRIN
-- =====================================================
-- BRIN indexes are perfect for time-series data with natural ordering

-- Transaction time-series
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_created_brin ON transactions USING brin(created_at) WITH (pages_per_range = 128);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_completed_brin ON transactions USING brin(completed_at) WITH (pages_per_range = 128) WHERE completed_at IS NOT NULL;

-- Payment time-series
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_created_brin ON payments USING brin(created_at) WITH (pages_per_range = 128);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processed_brin ON payments USING brin(processed_at) WITH (pages_per_range = 128) WHERE processed_at IS NOT NULL;

-- Crypto transaction time-series
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_txn_created_brin ON crypto_transactions USING brin(created_at) WITH (pages_per_range = 64);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_txn_confirmed_brin ON crypto_transactions USING brin(confirmed_at) WITH (pages_per_range = 64) WHERE confirmed_at IS NOT NULL;

-- Audit trail time-series
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_trail_brin ON audit_trails USING brin(created_at) WITH (pages_per_range = 256);

-- Notification time-series
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_created_brin ON notifications USING brin(created_at) WITH (pages_per_range = 128);

-- =====================================================
-- GEOSPATIAL INDEXES (if PostGIS is available)
-- =====================================================

-- ATM location indexes (assuming location columns exist)
DO $$ 
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'postgis') THEN
        -- ATM terminal locations
        IF EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'atm_terminals' AND column_name = 'location') THEN
            EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_atm_location_gist ON atm_terminals USING gist(location)';
        END IF;
        
        -- Merchant locations
        IF EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'merchants' AND column_name = 'location') THEN
            EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_location_gist ON merchants USING gist(location)';
        END IF;
        
        -- User last known locations for fraud detection
        IF EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'user_locations' AND column_name = 'location') THEN
            EXECUTE 'CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_location_gist ON user_locations USING gist(location)';
        END IF;
    END IF;
END $$;

-- =====================================================
-- JSONB INDEXES FOR METADATA AND CONFIGURATIONS
-- =====================================================

-- Transaction metadata
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_metadata_gin ON transactions USING gin(metadata jsonb_path_ops) 
    WHERE metadata IS NOT NULL;

-- Payment metadata
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_metadata_gin ON payments USING gin(metadata jsonb_path_ops) 
    WHERE metadata IS NOT NULL;

-- Specific metadata field indexes (commonly queried fields)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_metadata_merchant_id ON transactions ((metadata->>'merchant_id')) 
    WHERE metadata->>'merchant_id' IS NOT NULL;
    
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_metadata_device_id ON transactions ((metadata->>'device_id')) 
    WHERE metadata->>'device_id' IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_metadata_invoice_id ON payments ((metadata->>'invoice_id')) 
    WHERE metadata->>'invoice_id' IS NOT NULL;

-- User preferences JSONB
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_preferences_gin ON user_profiles USING gin(preferences jsonb_path_ops) 
    WHERE preferences IS NOT NULL;

-- Notification templates configuration
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_template_config ON notification_templates USING gin(config jsonb_path_ops) 
    WHERE config IS NOT NULL;

-- =====================================================
-- BLOOM FILTER INDEXES FOR EXISTENCE CHECKS
-- =====================================================

-- Create bloom extension if not exists
CREATE EXTENSION IF NOT EXISTS bloom;

-- Bloom filters for fast existence checks
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_bloom ON users USING bloom(username, email, phone_number) WITH (length=80, col1=2, col2=2, col3=2);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_bloom ON transactions USING bloom(reference, external_id) WITH (length=80, col1=3, col2=3);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_addr_bloom ON crypto_addresses USING bloom(address) WITH (length=64, col1=4);

-- =====================================================
-- HASH INDEXES FOR EXACT LOOKUPS
-- =====================================================

-- Hash indexes for frequently looked up unique values
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_hash ON users USING hash(email);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_username_hash ON users USING hash(username);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_id_hash ON wallets USING hash(id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_ref_hash ON transactions USING hash(reference) WHERE reference IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_ref_hash ON payments USING hash(reference_number) WHERE reference_number IS NOT NULL;

-- =====================================================
-- COVERING INDEXES TO AVOID TABLE LOOKUPS
-- =====================================================

-- User authentication covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_auth_covering ON users(username) 
    INCLUDE (id, password_hash, status, account_locked) WHERE status = 'ACTIVE';

-- Wallet balance covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_balance_covering ON wallets(user_id, currency) 
    INCLUDE (balance, available_balance, status) WHERE status = 'ACTIVE';

-- Transaction summary covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_summary_covering ON transactions(user_id, created_at DESC) 
    INCLUDE (amount, currency, type, status, description);

-- Payment processing covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_covering ON payments(id) 
    INCLUDE (amount, currency, status, provider, external_reference) 
    WHERE status IN ('PENDING', 'PROCESSING');

-- =====================================================
-- EXPRESSION INDEXES FOR COMPUTED VALUES
-- =====================================================

-- Date-based expression indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_date_only ON transactions(DATE(created_at));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_month ON transactions(DATE_TRUNC('month', created_at));
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_year ON transactions(DATE_TRUNC('year', created_at));

-- Case-insensitive email lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_lower ON users(LOWER(email));

-- Amount range categorization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_amount_category ON transactions(
    CASE 
        WHEN amount < 100 THEN 'small'
        WHEN amount < 1000 THEN 'medium'
        WHEN amount < 10000 THEN 'large'
        ELSE 'very_large'
    END
);

-- Age calculation for users
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_age_bracket ON user_profiles(
    CASE 
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) < 18 THEN 'minor'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) < 25 THEN 'young_adult'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) < 40 THEN 'adult'
        WHEN EXTRACT(YEAR FROM AGE(date_of_birth)) < 60 THEN 'middle_aged'
        ELSE 'senior'
    END
) WHERE date_of_birth IS NOT NULL;

-- =====================================================
-- INDEXES FOR AGGREGATION QUERIES
-- =====================================================

-- Daily transaction aggregation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_daily_agg ON transactions(
    DATE(created_at), 
    user_id, 
    type, 
    status
) INCLUDE (amount, currency);

-- Monthly spending aggregation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_spending_monthly_agg ON transactions(
    user_id,
    DATE_TRUNC('month', created_at),
    type
) INCLUDE (amount) WHERE status = 'COMPLETED';

-- Merchant daily revenue
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_daily_revenue ON payments(
    merchant_id,
    DATE(created_at),
    status
) INCLUDE (amount, currency) WHERE merchant_id IS NOT NULL;

-- =====================================================
-- INDEXES FOR WINDOW FUNCTIONS
-- =====================================================

-- Running balance calculation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_running_balance ON transactions(
    wallet_id, 
    created_at, 
    id
) INCLUDE (amount, balance_after) WHERE status = 'COMPLETED';

-- User transaction ranking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_user_ranking ON transactions(
    user_id, 
    amount DESC, 
    created_at DESC
) WHERE status = 'COMPLETED';

-- =====================================================
-- MATERIALIZED VIEW INDEXES
-- =====================================================

-- Create materialized views for expensive aggregations
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_transaction_summary AS
SELECT 
    DATE(created_at) as transaction_date,
    user_id,
    type,
    status,
    currency,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MAX(amount) as max_amount,
    MIN(amount) as min_amount
FROM transactions
WHERE created_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE(created_at), user_id, type, status, currency;

-- Index the materialized view
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mv_daily_summary_user ON mv_daily_transaction_summary(user_id, transaction_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mv_daily_summary_date ON mv_daily_transaction_summary(transaction_date DESC);

-- User activity materialized view
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_user_activity_summary AS
SELECT 
    u.id as user_id,
    u.username,
    u.status,
    COUNT(DISTINCT t.id) as total_transactions,
    SUM(t.amount) as total_transacted,
    MAX(t.created_at) as last_transaction_date,
    COUNT(DISTINCT DATE(t.created_at)) as active_days
FROM users u
LEFT JOIN transactions t ON u.id = t.user_id AND t.status = 'COMPLETED'
WHERE u.created_at >= CURRENT_DATE - INTERVAL '180 days'
GROUP BY u.id, u.username, u.status;

-- Index the user activity view
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mv_user_activity_id ON mv_user_activity_summary(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mv_user_activity_volume ON mv_user_activity_summary(total_transacted DESC);

-- =====================================================
-- CONDITIONAL INDEXES FOR BUSINESS RULES
-- =====================================================

-- Transactions requiring AML review
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_aml_review ON transactions(created_at DESC)
WHERE amount > 10000 
    AND status = 'COMPLETED' 
    AND monitoring_enabled = true;

-- Users requiring re-verification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_reverify ON users(updated_at)
WHERE status = 'ACTIVE' 
    AND updated_at < CURRENT_DATE - INTERVAL '365 days';

-- Expired cards needing renewal
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cards_expiring ON cards(user_id, expiry_date)
WHERE status = 'ACTIVE' 
    AND expiry_date < CURRENT_DATE + INTERVAL '30 days';

-- Dormant accounts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_dormant_accounts ON users(last_activity_at)
WHERE status = 'ACTIVE' 
    AND last_activity_at < CURRENT_DATE - INTERVAL '90 days';

-- =====================================================
-- INDEXES FOR JOIN OPTIMIZATION
-- =====================================================

-- Foreign key indexes for join performance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_fk_wallet ON transactions(wallet_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_fk_user ON transactions(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_fk_user ON wallets(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_fk_user ON payments(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_fk_merchant ON payments(merchant_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_card_fk_user ON cards(user_id);

-- =====================================================
-- PERFORMANCE MONITORING INDEXES
-- =====================================================

-- Slow query identification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_slow_queries ON pg_stat_statements(mean_exec_time DESC)
WHERE calls > 100;

-- Table bloat monitoring
CREATE OR REPLACE VIEW v_table_bloat AS
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) AS external_size
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- =====================================================
-- INDEX MAINTENANCE FUNCTIONS
-- =====================================================

-- Function to rebuild bloated indexes
CREATE OR REPLACE FUNCTION rebuild_bloated_indexes(bloat_threshold float DEFAULT 30.0)
RETURNS TABLE(index_name text, bloat_percent float, rebuild_command text) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        indexname::text,
        (100 * (1 - (index_bytes::float / nullif(index_bytes + bloat_bytes, 0))))::float as bloat_pct,
        'REINDEX INDEX CONCURRENTLY ' || indexname || ';'::text
    FROM (
        SELECT
            indexname,
            pg_relation_size(indexname::regclass) as index_bytes,
            pg_relation_size(indexname::regclass) - 
                (pg_relation_size(indexname::regclass) * 
                (100 - (pg_stat_user_indexes.idx_tup_read::float / 
                    nullif(pg_stat_user_indexes.idx_scan, 0) * 100)) / 100) as bloat_bytes
        FROM pg_stat_user_indexes
        JOIN pg_indexes ON pg_indexes.indexname = pg_stat_user_indexes.indexrelname
    ) bloat_calc
    WHERE (100 * (1 - (index_bytes::float / nullif(index_bytes + bloat_bytes, 0)))) > bloat_threshold;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- AUTOMATED INDEX RECOMMENDATIONS
-- =====================================================

-- View for missing indexes based on query patterns
CREATE OR REPLACE VIEW v_missing_indexes AS
SELECT 
    schemaname,
    tablename,
    attname,
    n_distinct,
    correlation
FROM pg_stats
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
    AND n_distinct > 100
    AND correlation < 0.1
ORDER BY n_distinct DESC;

-- =====================================================
-- PERFORMANCE COMMENTS
-- =====================================================

COMMENT ON INDEX idx_txn_created_brin IS 'BRIN index for time-series transaction queries';
COMMENT ON INDEX idx_users_bloom IS 'Bloom filter for fast user existence checks';
COMMENT ON INDEX idx_users_auth_covering IS 'Covering index to avoid table lookups during authentication';
COMMENT ON INDEX idx_txn_amount_category IS 'Expression index for transaction amount categorization';
COMMENT ON MATERIALIZED VIEW mv_daily_transaction_summary IS 'Pre-aggregated daily transaction statistics';
COMMENT ON FUNCTION rebuild_bloated_indexes IS 'Identifies and provides commands to rebuild bloated indexes';

-- End of specialized indexes migration