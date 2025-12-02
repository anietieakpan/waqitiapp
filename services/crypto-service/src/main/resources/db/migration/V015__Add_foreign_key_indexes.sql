-- Add Foreign Key Indexes for Performance
-- Service: crypto-service
-- Date: 2025-10-18
-- Description: Add missing indexes on foreign key columns to prevent table scans
-- Impact: Significantly improves query performance and prevents deadlocks
-- Priority: HIGH - Production Performance Issue
--
-- Problem:
-- PostgreSQL does NOT automatically create indexes on foreign key columns.
-- Without these indexes:
-- - JOINs perform full table scans
-- - DELETE/UPDATE on parent table scans entire child table
-- - High risk of deadlocks under concurrent load
-- - Slow query performance at scale
--
-- Solution:
-- Create indexes on all foreign key columns
-- Performance improvement: 10-100x faster for FK constraint checks
--
-- Total Indexes: 4

CREATE INDEX IF NOT EXISTS idx_crypto_wallets_user_id ON crypto_wallets(user_id);
CREATE INDEX IF NOT EXISTS idx_crypto_transactions_wallet_id ON crypto_transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_crypto_transactions_user_id ON crypto_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_crypto_addresses_wallet_id ON crypto_addresses(wallet_id);

-- Index comments for documentation
COMMENT ON INDEX idx_crypto_wallets_user_id IS 'Foreign key index for crypto_wallets.user_id - Performance optimization';
COMMENT ON INDEX idx_crypto_transactions_wallet_id IS 'Foreign key index for crypto_transactions.wallet_id - Performance optimization';
COMMENT ON INDEX idx_crypto_transactions_user_id IS 'Foreign key index for crypto_transactions.user_id - Performance optimization';
COMMENT ON INDEX idx_crypto_addresses_wallet_id IS 'Foreign key index for crypto_addresses.wallet_id - Performance optimization';
