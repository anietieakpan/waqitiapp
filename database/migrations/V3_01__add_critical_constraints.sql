-- =====================================================
-- WAQITI PLATFORM - CRITICAL DATABASE CONSTRAINTS
-- Migration V3.01 - Add Missing Constraints & Audit Columns
-- Priority: P0 - CRITICAL
-- Impact: Data integrity, prevent data corruption
-- =====================================================

-- =====================================================
-- PAYMENT SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 1: Payment Processing - Unique Transaction ID
-- CRITICAL: Prevents duplicate transaction processing
-- Impact: Eliminates risk of duplicate payments
ALTER TABLE payment_processing
ADD CONSTRAINT uk_payment_processing_transaction_id
UNIQUE (transaction_id);

-- Constraint 2: Payment Amounts - Must be positive
-- Impact: Prevents negative amount payments
ALTER TABLE payment_processing
ADD CONSTRAINT chk_payment_processing_amount_positive
CHECK (amount > 0);

-- Constraint 3: Payment Status - Valid values only
-- Impact: Ensures payment state machine integrity
ALTER TABLE payment_processing
ADD CONSTRAINT chk_payment_processing_status
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED'));

-- Add audit columns to split_payment_participants
-- Impact: Track who modified payment splits
ALTER TABLE split_payment_participants
ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add optimistic locking trigger for split_payment_participants
CREATE OR REPLACE FUNCTION increment_version_on_update()
RETURNS TRIGGER AS $$
BEGIN
    NEW.version = OLD.version + 1;
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER split_payment_participants_version_trigger
BEFORE UPDATE ON split_payment_participants
FOR EACH ROW
EXECUTE FUNCTION increment_version_on_update();

-- =====================================================
-- WALLET SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 4: Wallet Balances - Available balance cannot be negative
-- CRITICAL: Prevents overdraft
ALTER TABLE wallets
ADD CONSTRAINT chk_wallet_available_balance_positive
CHECK (available_balance >= 0);

-- Constraint 5: Wallet Holds - Expiry must be in future for active holds
-- Impact: Data quality for hold expiration
ALTER TABLE wallet_holds
ADD CONSTRAINT chk_hold_expiry
CHECK (status != 'ACTIVE' OR expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP);

-- Constraint 6: Wallet Holds - Amount must be positive
-- Impact: Prevents invalid hold amounts
ALTER TABLE wallet_holds
ADD CONSTRAINT chk_hold_amount_positive
CHECK (amount > 0);

-- =====================================================
-- LEDGER SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 7: Account Balances - Balance relationship validation
-- CRITICAL: Ensures current_balance = available + pending + reserved
ALTER TABLE account_balances
ADD CONSTRAINT chk_balance_relationship
CHECK (
    current_balance = (
        COALESCE(available_balance, 0) +
        COALESCE(pending_balance, 0) +
        COALESCE(reserved_balance, 0)
    )
);

-- Constraint 8: Account Balances - All balances must be non-negative
-- CRITICAL: Prevents negative balances
ALTER TABLE account_balances
ADD CONSTRAINT chk_balance_positive
CHECK (
    current_balance >= 0 AND
    available_balance >= 0 AND
    pending_balance >= 0 AND
    reserved_balance >= 0
);

-- Constraint 9: Ledger Entries - Debit amount must be non-negative
-- Impact: Accounting integrity
ALTER TABLE ledger_entries
ADD CONSTRAINT chk_ledger_debit_positive
CHECK (debit_amount >= 0);

-- Constraint 10: Ledger Entries - Credit amount must be non-negative
-- Impact: Accounting integrity
ALTER TABLE ledger_entries
ADD CONSTRAINT chk_ledger_credit_positive
CHECK (credit_amount >= 0);

-- Constraint 11: Ledger Entries - Exactly one of debit or credit must be non-zero
-- Impact: Double-entry bookkeeping integrity
ALTER TABLE ledger_entries
ADD CONSTRAINT chk_ledger_debit_or_credit
CHECK (
    (debit_amount > 0 AND credit_amount = 0) OR
    (credit_amount > 0 AND debit_amount = 0)
);

-- =====================================================
-- USER SERVICE CONSTRAINTS
-- =====================================================

-- Add security audit columns to user_roles
-- CRITICAL: Security compliance - track who granted/revoked roles
ALTER TABLE user_roles
ADD COLUMN IF NOT EXISTS granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS granted_by VARCHAR(100),
ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(100),
ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Constraint 12: User Roles - Revoked roles must have revoked_at timestamp
-- Impact: Audit trail integrity
ALTER TABLE user_roles
ADD CONSTRAINT chk_user_roles_revoked
CHECK (is_active = TRUE OR (is_active = FALSE AND revoked_at IS NOT NULL));

-- Constraint 13: Verification Tokens - Token must not be expired when used
-- Impact: Security - prevents use of expired tokens
ALTER TABLE verification_tokens
ADD CONSTRAINT chk_verification_token_expiry
CHECK (used = FALSE OR expiry_date > created_at);

-- =====================================================
-- TRANSACTION SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 14: Transactions - Amount must be positive
-- Impact: Prevents invalid transaction amounts
ALTER TABLE transactions
ADD CONSTRAINT chk_transaction_amount_positive
CHECK (amount > 0);

-- Constraint 15: Transactions - From and To accounts must be different
-- Impact: Prevents self-transfer loops
ALTER TABLE transactions
ADD CONSTRAINT chk_transaction_different_accounts
CHECK (from_account_id != to_account_id);

-- Constraint 16: Transactions - Status must be valid
-- Impact: State machine integrity
ALTER TABLE transactions
ADD CONSTRAINT chk_transaction_status
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'));

-- =====================================================
-- COMPLIANCE SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 17: Compliance Transactions - Risk score between 0 and 1000
-- Impact: Data quality for risk scoring
ALTER TABLE compliance_transactions
ADD CONSTRAINT chk_compliance_risk_score
CHECK (risk_score >= 0 AND risk_score <= 1000);

-- Constraint 18: SAR Filings - Filing deadline must be after creation
-- Impact: Regulatory compliance tracking
ALTER TABLE sar_filings
ADD CONSTRAINT chk_sar_filing_deadline
CHECK (filing_deadline > created_at);

-- =====================================================
-- FRAUD DETECTION SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 19: Fraud Alerts - Severity must be valid
-- Impact: Alert prioritization integrity
ALTER TABLE fraud_alerts
ADD CONSTRAINT chk_fraud_alert_severity
CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

-- Constraint 20: Fraud Scores - Score between 0 and 100
-- Impact: Data quality for fraud scoring
ALTER TABLE fraud_scores
ADD CONSTRAINT chk_fraud_score_range
CHECK (score >= 0 AND score <= 100);

-- =====================================================
-- CRYPTO SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 21: Crypto Transactions - Amount must be positive
-- Impact: Prevents invalid crypto transaction amounts
ALTER TABLE crypto_transactions
ADD CONSTRAINT chk_crypto_amount_positive
CHECK (amount > 0);

-- Constraint 22: Crypto Deposits - Confirmations must be non-negative
-- Impact: Data quality for blockchain confirmations
ALTER TABLE crypto_deposits
ADD CONSTRAINT chk_crypto_confirmations_positive
CHECK (confirmations >= 0);

-- =====================================================
-- INVESTMENT SERVICE CONSTRAINTS
-- =====================================================

-- Constraint 23: Margin Calls - Deficiency must be positive
-- Impact: Margin call data integrity
ALTER TABLE margin_calls
ADD CONSTRAINT chk_margin_deficiency_positive
CHECK (deficiency_amount > 0);

-- Constraint 24: Investment Orders - Quantity must be positive
-- Impact: Prevents invalid order quantities
ALTER TABLE investment_orders
ADD CONSTRAINT chk_investment_quantity_positive
CHECK (quantity > 0);

-- =====================================================
-- GENERAL AUDIT COLUMNS (Missing from multiple tables)
-- =====================================================

-- Add missing audit columns to scheduled_payment_executions
ALTER TABLE scheduled_payment_executions
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

-- Create trigger to auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER scheduled_payment_executions_updated_at_trigger
BEFORE UPDATE ON scheduled_payment_executions
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- CONSTRAINT VALIDATION
-- =====================================================

-- Validate all constraints after creation
-- Run these to check for existing data violations:

/*
-- Check for negative wallet balances
SELECT wallet_id, available_balance FROM wallets WHERE available_balance < 0;

-- Check for duplicate transaction IDs
SELECT transaction_id, COUNT(*) FROM payment_processing
GROUP BY transaction_id HAVING COUNT(*) > 1;

-- Check for invalid ledger entries (both debit and credit non-zero)
SELECT id FROM ledger_entries WHERE debit_amount > 0 AND credit_amount > 0;

-- Check for balance relationship violations
SELECT account_id FROM account_balances
WHERE current_balance != (available_balance + pending_balance + reserved_balance);
*/

-- =====================================================
-- ROLLBACK SCRIPT (if needed)
-- =====================================================

/*
-- Remove constraints
ALTER TABLE payment_processing DROP CONSTRAINT IF EXISTS uk_payment_processing_transaction_id;
ALTER TABLE payment_processing DROP CONSTRAINT IF EXISTS chk_payment_processing_amount_positive;
ALTER TABLE payment_processing DROP CONSTRAINT IF EXISTS chk_payment_processing_status;
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_available_balance_positive;
ALTER TABLE wallet_holds DROP CONSTRAINT IF EXISTS chk_hold_expiry;
ALTER TABLE wallet_holds DROP CONSTRAINT IF EXISTS chk_hold_amount_positive;
ALTER TABLE account_balances DROP CONSTRAINT IF EXISTS chk_balance_relationship;
ALTER TABLE account_balances DROP CONSTRAINT IF EXISTS chk_balance_positive;
ALTER TABLE ledger_entries DROP CONSTRAINT IF EXISTS chk_ledger_debit_positive;
ALTER TABLE ledger_entries DROP CONSTRAINT IF EXISTS chk_ledger_credit_positive;
ALTER TABLE ledger_entries DROP CONSTRAINT IF EXISTS chk_ledger_debit_or_credit;
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS chk_user_roles_revoked;
ALTER TABLE verification_tokens DROP CONSTRAINT IF EXISTS chk_verification_token_expiry;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_transaction_amount_positive;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_transaction_different_accounts;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_transaction_status;
ALTER TABLE compliance_transactions DROP CONSTRAINT IF EXISTS chk_compliance_risk_score;
ALTER TABLE sar_filings DROP CONSTRAINT IF EXISTS chk_sar_filing_deadline;
ALTER TABLE fraud_alerts DROP CONSTRAINT IF EXISTS chk_fraud_alert_severity;
ALTER TABLE fraud_scores DROP CONSTRAINT IF EXISTS chk_fraud_score_range;
ALTER TABLE crypto_transactions DROP CONSTRAINT IF EXISTS chk_crypto_amount_positive;
ALTER TABLE crypto_deposits DROP CONSTRAINT IF EXISTS chk_crypto_confirmations_positive;
ALTER TABLE margin_calls DROP CONSTRAINT IF EXISTS chk_margin_deficiency_positive;
ALTER TABLE investment_orders DROP CONSTRAINT IF EXISTS chk_investment_quantity_positive;

-- Remove added columns
ALTER TABLE split_payment_participants DROP COLUMN IF EXISTS created_by;
ALTER TABLE split_payment_participants DROP COLUMN IF EXISTS updated_by;
ALTER TABLE split_payment_participants DROP COLUMN IF EXISTS version;
ALTER TABLE user_roles DROP COLUMN IF EXISTS granted_at;
ALTER TABLE user_roles DROP COLUMN IF EXISTS granted_by;
ALTER TABLE user_roles DROP COLUMN IF EXISTS revoked_at;
ALTER TABLE user_roles DROP COLUMN IF EXISTS revoked_by;
ALTER TABLE user_roles DROP COLUMN IF EXISTS is_active;
ALTER TABLE scheduled_payment_executions DROP COLUMN IF EXISTS updated_at;
ALTER TABLE scheduled_payment_executions DROP COLUMN IF EXISTS updated_by;

-- Remove triggers
DROP TRIGGER IF EXISTS split_payment_participants_version_trigger ON split_payment_participants;
DROP TRIGGER IF EXISTS scheduled_payment_executions_updated_at_trigger ON scheduled_payment_executions;
DROP FUNCTION IF EXISTS increment_version_on_update();
DROP FUNCTION IF EXISTS update_updated_at_column();
*/
