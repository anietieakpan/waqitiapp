-- Standardize Wallet Service Database Schema
-- This migration standardizes all wallet-related tables to follow Waqiti patterns

-- =====================================================================
-- WALLETS TABLE STANDARDIZATION
-- =====================================================================

-- Ensure wallets table follows standard patterns
ALTER TABLE wallets 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are using TIMESTAMP WITH TIME ZONE
ALTER TABLE wallets 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure balance uses standard financial precision
ALTER TABLE wallets 
ALTER COLUMN balance TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_wallets_updated_at 
    BEFORE UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add business rule constraints
ALTER TABLE wallets 
ADD CONSTRAINT check_wallet_status 
CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED', 'SUSPENDED'));

ALTER TABLE wallets 
ADD CONSTRAINT check_wallet_type 
CHECK (wallet_type IN ('PERSONAL', 'BUSINESS', 'SAVINGS', 'CHECKING'));

ALTER TABLE wallets 
ADD CONSTRAINT check_account_type 
CHECK (account_type IN ('STANDARD', 'PREMIUM', 'CORPORATE'));

ALTER TABLE wallets 
ADD CONSTRAINT check_balance_non_negative 
CHECK (balance >= 0);

-- =====================================================================
-- WALLET BALANCES TABLE STANDARDIZATION
-- =====================================================================

-- Standardize wallet_balances table structure
ALTER TABLE wallet_balances 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE wallet_balances 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure all balance fields use standard precision
ALTER TABLE wallet_balances 
ALTER COLUMN current_balance TYPE DECIMAL(19,4),
ALTER COLUMN available_balance TYPE DECIMAL(19,4),
ALTER COLUMN pending_balance TYPE DECIMAL(19,4),
ALTER COLUMN frozen_balance TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_wallet_balances_updated_at 
    BEFORE UPDATE ON wallet_balances
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add balance consistency constraints
ALTER TABLE wallet_balances 
ADD CONSTRAINT check_balances_non_negative 
CHECK (current_balance >= 0 AND available_balance >= 0 AND pending_balance >= 0 AND frozen_balance >= 0);

ALTER TABLE wallet_balances 
ADD CONSTRAINT check_balance_consistency 
CHECK (current_balance = available_balance + pending_balance + frozen_balance);

-- =====================================================================
-- TRANSACTIONS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize transactions table
ALTER TABLE transactions 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE transactions 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN processed_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE transactions 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_transactions_updated_at 
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add business rule constraints
ALTER TABLE transactions 
ADD CONSTRAINT check_transaction_type 
CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REFUND', 'FEE', 'INTEREST', 'ADJUSTMENT'));

ALTER TABLE transactions 
ADD CONSTRAINT check_transaction_status 
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'));

ALTER TABLE transactions 
ADD CONSTRAINT check_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- WALLET HOLDS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize wallet_holds table
ALTER TABLE wallet_holds 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE wallet_holds 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN released_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE wallet_holds 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_wallet_holds_updated_at 
    BEFORE UPDATE ON wallet_holds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add business rule constraints
ALTER TABLE wallet_holds 
ADD CONSTRAINT check_hold_status 
CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'CANCELLED'));

ALTER TABLE wallet_holds 
ADD CONSTRAINT check_hold_type 
CHECK (hold_type IN ('PAYMENT_AUTHORIZATION', 'COMPLIANCE_HOLD', 'FRAUD_PREVENTION', 'MANUAL_HOLD'));

ALTER TABLE wallet_holds 
ADD CONSTRAINT check_hold_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- WALLET AUDIT TABLE STANDARDIZATION
-- =====================================================================

-- Standardize wallet_audit table
ALTER TABLE wallet_audit 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE wallet_audit 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision where applicable
ALTER TABLE wallet_audit 
ALTER COLUMN old_balance TYPE DECIMAL(19,4),
ALTER COLUMN new_balance TYPE DECIMAL(19,4),
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_wallet_audit_updated_at 
    BEFORE UPDATE ON wallet_audit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE wallet_audit 
ADD CONSTRAINT check_audit_operation 
CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE', 'FREEZE', 'UNFREEZE', 'CLOSE', 'BALANCE_UPDATE'));

-- =====================================================================
-- STANDARDIZED INDEXES FOR PERFORMANCE
-- =====================================================================

-- Wallets table indexes
CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);
CREATE INDEX IF NOT EXISTS idx_wallets_status ON wallets(status);
CREATE INDEX IF NOT EXISTS idx_wallets_currency ON wallets(currency);
CREATE INDEX IF NOT EXISTS idx_wallets_external_id ON wallets(external_id);
CREATE INDEX IF NOT EXISTS idx_wallets_created_at ON wallets(created_at);
CREATE INDEX IF NOT EXISTS idx_wallets_updated_at ON wallets(updated_at);
CREATE INDEX IF NOT EXISTS idx_wallets_user_currency ON wallets(user_id, currency);

-- Wallet balances indexes
CREATE INDEX IF NOT EXISTS idx_wallet_balances_wallet_id ON wallet_balances(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_balances_currency ON wallet_balances(currency);
CREATE INDEX IF NOT EXISTS idx_wallet_balances_created_at ON wallet_balances(created_at);
CREATE INDEX IF NOT EXISTS idx_wallet_balances_updated_at ON wallet_balances(updated_at);

-- Transactions indexes
CREATE INDEX IF NOT EXISTS idx_transactions_source_wallet_id ON transactions(source_wallet_id);
CREATE INDEX IF NOT EXISTS idx_transactions_target_wallet_id ON transactions(target_wallet_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_external_id ON transactions(external_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_processed_at ON transactions(processed_at);
CREATE INDEX IF NOT EXISTS idx_transactions_amount ON transactions(amount);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_transactions_wallet_date ON transactions(source_wallet_id, created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status_type ON transactions(status, type);

-- Wallet holds indexes
CREATE INDEX IF NOT EXISTS idx_wallet_holds_wallet_id ON wallet_holds(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_holds_status ON wallet_holds(status);
CREATE INDEX IF NOT EXISTS idx_wallet_holds_type ON wallet_holds(hold_type);
CREATE INDEX IF NOT EXISTS idx_wallet_holds_expires_at ON wallet_holds(expires_at);
CREATE INDEX IF NOT EXISTS idx_wallet_holds_created_at ON wallet_holds(created_at);

-- Wallet audit indexes
CREATE INDEX IF NOT EXISTS idx_wallet_audit_wallet_id ON wallet_audit(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_audit_user_id ON wallet_audit(user_id);
CREATE INDEX IF NOT EXISTS idx_wallet_audit_operation ON wallet_audit(operation);
CREATE INDEX IF NOT EXISTS idx_wallet_audit_transaction_id ON wallet_audit(transaction_id);
CREATE INDEX IF NOT EXISTS idx_wallet_audit_created_at ON wallet_audit(created_at);

-- =====================================================================
-- UNIQUE CONSTRAINTS FOR DATA INTEGRITY
-- =====================================================================

-- Ensure one primary wallet per user per currency
CREATE UNIQUE INDEX IF NOT EXISTS idx_wallets_user_currency_primary 
ON wallets(user_id, currency) 
WHERE wallet_type = 'PERSONAL';

-- Ensure unique external transaction IDs
CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_external_id_unique 
ON transactions(external_id) 
WHERE external_id IS NOT NULL;

-- =====================================================================
-- FINANCIAL INTEGRITY CONSTRAINTS
-- =====================================================================

-- Add constraint to ensure transaction references valid wallets
-- Note: These are intra-service constraints only
ALTER TABLE transactions 
ADD CONSTRAINT fk_transactions_source_wallet 
FOREIGN KEY (source_wallet_id) REFERENCES wallets(id);

ALTER TABLE transactions 
ADD CONSTRAINT fk_transactions_target_wallet 
FOREIGN KEY (target_wallet_id) REFERENCES wallets(id);

-- Add constraint for wallet balances
ALTER TABLE wallet_balances 
ADD CONSTRAINT fk_wallet_balances_wallet 
FOREIGN KEY (wallet_id) REFERENCES wallets(id);

-- Add constraint for wallet holds
ALTER TABLE wallet_holds 
ADD CONSTRAINT fk_wallet_holds_wallet 
FOREIGN KEY (wallet_id) REFERENCES wallets(id);

-- Add constraint for wallet audit
ALTER TABLE wallet_audit 
ADD CONSTRAINT fk_wallet_audit_wallet 
FOREIGN KEY (wallet_id) REFERENCES wallets(id);

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON TABLE wallets IS 'User wallet accounts for holding funds';
COMMENT ON TABLE wallet_balances IS 'Detailed balance breakdown for wallets';
COMMENT ON TABLE transactions IS 'Financial transactions between wallets';
COMMENT ON TABLE wallet_holds IS 'Temporary holds on wallet funds';
COMMENT ON TABLE wallet_audit IS 'Audit trail for all wallet operations';

COMMENT ON COLUMN wallets.balance IS 'Current total balance with 4 decimal precision';
COMMENT ON COLUMN wallet_balances.current_balance IS 'Total current balance';
COMMENT ON COLUMN wallet_balances.available_balance IS 'Available balance for transactions';
COMMENT ON COLUMN wallet_balances.pending_balance IS 'Pending balance awaiting processing';
COMMENT ON COLUMN wallet_balances.frozen_balance IS 'Frozen balance due to holds or restrictions';

-- =====================================================================
-- CLEANUP DUPLICATE CONSTRAINTS/INDEXES
-- =====================================================================

-- Drop any duplicate or conflicting constraints that might exist
-- (This would be customized based on actual existing constraints)