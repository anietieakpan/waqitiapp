-- V10__Add_Optimistic_Locking_Version.sql
--
-- Adds version column for optimistic locking to wallet tables
-- Prevents race conditions in concurrent wallet updates
--
-- Related to: P0-1 (Race Condition Prevention with Optimistic Locking)

-- Add version column to wallets table (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'wallets'
        AND column_name = 'version'
    ) THEN
        ALTER TABLE wallets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

        COMMENT ON COLUMN wallets.version IS 'Optimistic locking version - prevents race conditions';
    END IF;
END $$;

-- Add version column to wallet_balances table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'wallet_balances'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'wallet_balances'
            AND column_name = 'version'
        ) THEN
            ALTER TABLE wallet_balances ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

            COMMENT ON COLUMN wallet_balances.version IS 'Optimistic locking version - prevents race conditions';
        END IF;
    END IF;
END $$;

-- Add version column to wallet_transactions table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'wallet_transactions'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'wallet_transactions'
            AND column_name = 'version'
        ) THEN
            ALTER TABLE wallet_transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

            COMMENT ON COLUMN wallet_transactions.version IS 'Optimistic locking version - prevents concurrent modifications';
        END IF;
    END IF;
END $$;

-- Add version column to fund_reservations table (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'fund_reservations'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'fund_reservations'
            AND column_name = 'version'
        ) THEN
            ALTER TABLE fund_reservations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

            COMMENT ON COLUMN fund_reservations.version IS 'Optimistic locking version - prevents double-spending';
        END IF;
    END IF;
END $$;

-- Create index on version column for wallets (performance optimization)
CREATE INDEX IF NOT EXISTS idx_wallets_version ON wallets (version);

-- View to detect potential race condition issues (wallets with high version numbers)
CREATE OR REPLACE VIEW high_contention_wallets AS
SELECT
    id as wallet_id,
    user_id,
    balance,
    version,
    updated_at,
    CASE
        WHEN version > 1000 THEN 'CRITICAL'
        WHEN version > 500 THEN 'HIGH'
        WHEN version > 100 THEN 'MODERATE'
        ELSE 'NORMAL'
    END as contention_level
FROM wallets
WHERE version > 100
ORDER BY version DESC;

COMMENT ON VIEW high_contention_wallets IS 'Identifies wallets with unusually high version numbers (indicating high contention)';
