-- ATM Service Schema V6: Fix Table Name Mismatch
-- Created: 2025-11-15
-- Description: Rename atm_transaction (singular) to atm_transactions (plural) to match JPA entity

-- Rename table
ALTER TABLE IF EXISTS atm_transaction RENAME TO atm_transactions;

-- Recreate indexes with new table name (if they exist)
DROP INDEX IF EXISTS idx_atm_transaction_atm;
DROP INDEX IF EXISTS idx_atm_transaction_user;
DROP INDEX IF EXISTS idx_atm_transaction_status;
DROP INDEX IF EXISTS idx_atm_transaction_created;
DROP INDEX IF EXISTS idx_atm_transaction_fraud;

CREATE INDEX IF NOT EXISTS idx_atm_transactions_atm ON atm_transactions(atm_id);
CREATE INDEX IF NOT EXISTS idx_atm_transactions_user ON atm_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_atm_transactions_status ON atm_transactions(status);
CREATE INDEX IF NOT EXISTS idx_atm_transactions_created ON atm_transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_atm_transactions_fraud ON atm_transactions(risk_score DESC) WHERE risk_score > 0.7;

-- Update foreign key constraints (recreate if needed)
-- Note: Original V1 migration foreign key referenced atm_device(atm_id)
ALTER TABLE IF EXISTS atm_transactions DROP CONSTRAINT IF EXISTS fk_atm_device;
ALTER TABLE IF EXISTS atm_transactions ADD CONSTRAINT fk_atm_device
    FOREIGN KEY (atm_id) REFERENCES atm_device(atm_id) ON DELETE RESTRICT;

COMMENT ON TABLE atm_transactions IS 'ATM transaction records (renamed from atm_transaction for JPA entity compatibility)';
