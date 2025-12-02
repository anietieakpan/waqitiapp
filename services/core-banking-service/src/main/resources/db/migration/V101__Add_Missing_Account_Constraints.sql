-- V101: Add Missing Account Constraints
-- Description: Adds critical database constraints to prevent invalid account states
-- Author: Claude Code - Forensic Analysis Implementation
-- Date: 2025-11-08

-- ==========================================
-- 1. BALANCE INTEGRITY CONSTRAINTS
-- ==========================================

-- Ensure balance cannot go negative unless overdraft is allowed
ALTER TABLE accounts
ADD CONSTRAINT chk_balance_non_negative
CHECK (
    balance >= COALESCE(-credit_limit, 0)
)
NOT VALID;

-- Validate the constraint on existing data
ALTER TABLE accounts VALIDATE CONSTRAINT chk_balance_non_negative;

COMMENT ON CONSTRAINT chk_balance_non_negative ON accounts IS
'Prevents negative balances unless overdraft (credit_limit) is configured';

-- ==========================================
-- 2. AVAILABLE BALANCE CONSISTENCY
-- ==========================================

-- Available balance should never exceed current balance
ALTER TABLE accounts
ADD CONSTRAINT chk_available_balance_consistency
CHECK (
    available_balance <= balance
)
NOT VALID;

ALTER TABLE accounts VALIDATE CONSTRAINT chk_available_balance_consistency;

COMMENT ON CONSTRAINT chk_available_balance_consistency ON accounts IS
'Ensures available balance never exceeds actual balance';

-- ==========================================
-- 3. RESERVED BALANCE VALIDATION
-- ==========================================

-- Reserved balance must be non-negative and not exceed current balance
ALTER TABLE accounts
ADD CONSTRAINT chk_reserved_balance_valid
CHECK (
    reserved_balance >= 0
    AND reserved_balance <= balance
)
NOT VALID;

ALTER TABLE accounts VALIDATE CONSTRAINT chk_reserved_balance_valid;

COMMENT ON CONSTRAINT chk_reserved_balance_valid ON accounts IS
'Ensures reserved funds are within account balance';

-- ==========================================
-- 4. PENDING BALANCE VALIDATION
-- ==========================================

-- Pending balance must be non-negative
ALTER TABLE accounts
ADD CONSTRAINT chk_pending_balance_non_negative
CHECK (
    pending_balance >= 0
)
NOT VALID;

ALTER TABLE accounts VALIDATE CONSTRAINT chk_pending_balance_non_negative;

COMMENT ON CONSTRAINT chk_pending_balance_non_negative ON accounts IS
'Prevents negative pending balances';

-- ==========================================
-- 5. TRANSACTION AMOUNT VALIDATION
-- ==========================================

-- Transaction amount must be positive
ALTER TABLE transactions
ADD CONSTRAINT chk_transaction_amount_positive
CHECK (
    amount > 0
)
NOT VALID;

ALTER TABLE transactions VALIDATE CONSTRAINT chk_transaction_amount_positive;

COMMENT ON CONSTRAINT chk_transaction_amount_positive ON transactions IS
'Ensures all transaction amounts are positive';

-- ==========================================
-- 6. FEE AMOUNT VALIDATION
-- ==========================================

-- Fee amount must be non-negative if present
ALTER TABLE transactions
ADD CONSTRAINT chk_fee_amount_non_negative
CHECK (
    fee_amount IS NULL OR fee_amount >= 0
)
NOT VALID;

ALTER TABLE transactions VALIDATE CONSTRAINT chk_fee_amount_non_negative;

COMMENT ON CONSTRAINT chk_fee_amount_non_negative ON transactions IS
'Prevents negative fee amounts';

-- ==========================================
-- 7. EXCHANGE RATE VALIDATION
-- ==========================================

-- Exchange rate must be positive if present
ALTER TABLE transactions
ADD CONSTRAINT chk_exchange_rate_positive
CHECK (
    exchange_rate IS NULL OR exchange_rate > 0
)
NOT VALID;

ALTER TABLE transactions VALIDATE CONSTRAINT chk_exchange_rate_positive;

COMMENT ON CONSTRAINT chk_exchange_rate_positive ON transactions IS
'Ensures exchange rates are positive when specified';

-- ==========================================
-- 8. DAILY/MONTHLY LIMIT VALIDATION
-- ==========================================

-- Daily limit must be positive if set
ALTER TABLE accounts
ADD CONSTRAINT chk_daily_limit_positive
CHECK (
    daily_limit IS NULL OR daily_limit > 0
)
NOT VALID;

ALTER TABLE accounts VALIDATE CONSTRAINT chk_daily_limit_positive;

-- Monthly limit must be positive if set
ALTER TABLE accounts
ADD CONSTRAINT chk_monthly_limit_positive
CHECK (
    monthly_limit IS NULL OR monthly_limit > 0
)
NOT VALID;

ALTER TABLE accounts VALIDATE CONSTRAINT chk_monthly_limit_positive;

COMMENT ON CONSTRAINT chk_daily_limit_positive ON accounts IS
'Ensures daily transaction limits are positive';

COMMENT ON CONSTRAINT chk_monthly_limit_positive ON accounts IS
'Ensures monthly transaction limits are positive';

-- ==========================================
-- 9. RISK SCORE BOUNDS (if exists)
-- ==========================================

-- Risk score should be between 0 and 100 if the column exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'accounts' AND column_name = 'risk_score'
    ) THEN
        ALTER TABLE accounts
        ADD CONSTRAINT chk_risk_score_range
        CHECK (
            risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 100)
        )
        NOT VALID;

        ALTER TABLE accounts VALIDATE CONSTRAINT chk_risk_score_range;

        COMMENT ON CONSTRAINT chk_risk_score_range ON accounts IS
        'Risk score must be between 0 and 100';
    END IF;
END $$;

-- ==========================================
-- 10. TRANSACTION REVERSAL INTEGRITY
-- ==========================================

-- Original transaction ID should not reference itself
ALTER TABLE transactions
ADD CONSTRAINT chk_no_self_reversal
CHECK (
    original_transaction_id IS NULL OR original_transaction_id != id
)
NOT VALID;

ALTER TABLE transactions VALIDATE CONSTRAINT chk_no_self_reversal;

COMMENT ON CONSTRAINT chk_no_self_reversal ON transactions IS
'Prevents a transaction from being its own reversal';

-- ==========================================
-- MIGRATION VALIDATION QUERIES
-- ==========================================

-- Count accounts that would violate constraints (should be 0 after validation)
DO $$
DECLARE
    violation_count INTEGER;
BEGIN
    -- Check for negative balances without overdraft
    SELECT COUNT(*) INTO violation_count
    FROM accounts
    WHERE balance < 0 AND (credit_limit IS NULL OR balance < -credit_limit);

    IF violation_count > 0 THEN
        RAISE WARNING '% accounts have invalid negative balances', violation_count;
    END IF;

    -- Check for available > balance
    SELECT COUNT(*) INTO violation_count
    FROM accounts
    WHERE available_balance > balance;

    IF violation_count > 0 THEN
        RAISE WARNING '% accounts have available balance exceeding total balance', violation_count;
    END IF;

    -- Check for invalid reserved balances
    SELECT COUNT(*) INTO violation_count
    FROM accounts
    WHERE reserved_balance < 0 OR reserved_balance > balance;

    IF violation_count > 0 THEN
        RAISE WARNING '% accounts have invalid reserved balances', violation_count;
    END IF;

    RAISE NOTICE 'Constraint validation completed';
END $$;

-- ==========================================
-- ROLLBACK SCRIPT (for reference)
-- ==========================================

-- To rollback this migration, run:
/*
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_balance_non_negative;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_available_balance_consistency;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_reserved_balance_valid;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_pending_balance_non_negative;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_daily_limit_positive;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_monthly_limit_positive;
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_risk_score_range;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_transaction_amount_positive;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_fee_amount_non_negative;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_exchange_rate_positive;
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS chk_no_self_reversal;
*/
