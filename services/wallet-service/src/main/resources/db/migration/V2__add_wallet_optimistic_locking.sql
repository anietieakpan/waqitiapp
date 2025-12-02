-- CRITICAL FIX: Add version columns to wallet tables for optimistic locking
-- This prevents double-spending and balance corruption in wallet operations
-- Author: Waqiti Engineering
-- Date: 2025-09-20

-- Add version column to wallets table
ALTER TABLE wallets 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to wallet_transactions table
ALTER TABLE wallet_transactions 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to wallet_limits table
ALTER TABLE wallet_limits 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add version column to wallet_holds table (for reserved funds)
ALTER TABLE wallet_holds 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add audit columns if missing
ALTER TABLE wallets
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE wallet_transactions
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_wallets_version ON wallets(version);
CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);
CREATE INDEX IF NOT EXISTS idx_wallets_status ON wallets(status);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_version ON wallet_transactions(version);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_id ON wallet_transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_created_at ON wallet_transactions(created_at);

-- Add composite index for concurrent balance checks
CREATE INDEX IF NOT EXISTS idx_wallets_balance_check ON wallets(id, version, balance, available_balance);

-- Update existing rows to have version = 1
UPDATE wallets SET version = 1 WHERE version = 0;
UPDATE wallet_transactions SET version = 1 WHERE version = 0;
UPDATE wallet_limits SET version = 1 WHERE version = 0;
UPDATE wallet_holds SET version = 1 WHERE version = 0;

-- Add check constraints for balance integrity
ALTER TABLE wallets
ADD CONSTRAINT check_balance_non_negative CHECK (balance >= 0),
ADD CONSTRAINT check_available_balance_non_negative CHECK (available_balance >= 0),
ADD CONSTRAINT check_pending_balance_non_negative CHECK (pending_balance >= 0);

-- Add comments for documentation
COMMENT ON COLUMN wallets.version IS 'Optimistic locking version - critical for preventing double-spending';
COMMENT ON COLUMN wallets.balance IS 'Total wallet balance including pending transactions';
COMMENT ON COLUMN wallets.available_balance IS 'Balance available for immediate use';
COMMENT ON COLUMN wallets.pending_balance IS 'Balance pending from incoming transactions';
COMMENT ON COLUMN wallet_transactions.version IS 'Optimistic locking version for transaction updates';