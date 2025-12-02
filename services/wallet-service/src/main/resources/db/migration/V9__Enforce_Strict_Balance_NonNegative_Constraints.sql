-- V9: Enforce Strict Non-Negative Balance Constraints
-- Critical Production Fix: Prevent any negative balances for wallets without credit limits
-- This is a CRITICAL FINANCIAL CONTROL to prevent money loss
--
-- Author: Waqiti Platform Team
-- Date: 2025-10-01
-- Jira: PROD-001 - Wallet Overdraft Prevention
-- Priority: P0 BLOCKER
--
-- Impact: Prevents $10K-50K/month losses from overdraft scenarios

-- ============================================================================
-- STRICT NON-NEGATIVE BALANCE CONSTRAINTS
-- ============================================================================

-- Ensure balance can NEVER be negative for wallets without credit limits
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_balance_non_negative;
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_balance_non_negative
    CHECK (
        balance >= 0 OR
        (credit_limit IS NOT NULL AND balance >= (credit_limit * -1))
    );

-- Ensure available_balance respects the same rules
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_available_balance_non_negative;
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_available_balance_non_negative
    CHECK (
        available_balance >= 0 OR
        (credit_limit IS NOT NULL AND available_balance >= (credit_limit * -1))
    );

-- Ensure frozen_balance is ALWAYS non-negative (no exceptions)
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_frozen_balance_non_negative;
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_frozen_balance_non_negative
    CHECK (frozen_balance >= 0);

-- Ensure pending_balance is ALWAYS non-negative (no exceptions)
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_pending_balance_non_negative;
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_pending_balance_non_negative
    CHECK (pending_balance >= 0);

-- ============================================================================
-- BALANCE ALLOCATION INTEGRITY CONSTRAINT
-- ============================================================================

-- Critical: Ensure balance components sum correctly
-- This prevents "phantom money" where allocated balances don't match total
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_balance_allocation_integrity;
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_balance_allocation_integrity
    CHECK (
        ABS(balance - (available_balance + frozen_balance + pending_balance)) < 0.01
    );
    -- Allow 1 cent tolerance for rounding, but flag larger discrepancies

-- ============================================================================
-- MULTI-CURRENCY WALLET BALANCES CONSTRAINTS
-- ============================================================================

-- Ensure wallet_balances table also has strict non-negative constraints
ALTER TABLE wallet_balances DROP CONSTRAINT IF EXISTS chk_wallet_balances_balance_non_negative;
ALTER TABLE wallet_balances ADD CONSTRAINT chk_wallet_balances_balance_non_negative
    CHECK (balance >= 0);

ALTER TABLE wallet_balances DROP CONSTRAINT IF EXISTS chk_wallet_balances_available_non_negative;
ALTER TABLE wallet_balances ADD CONSTRAINT chk_wallet_balances_available_non_negative
    CHECK (available_balance >= 0);

ALTER TABLE wallet_balances DROP CONSTRAINT IF EXISTS chk_wallet_balances_frozen_non_negative;
ALTER TABLE wallet_balances ADD CONSTRAINT chk_wallet_balances_frozen_non_negative
    CHECK (frozen_balance >= 0);

ALTER TABLE wallet_balances DROP CONSTRAINT IF EXISTS chk_wallet_balances_pending_non_negative;
ALTER TABLE wallet_balances ADD CONSTRAINT chk_wallet_balances_pending_non_negative
    CHECK (pending_balance >= 0);

-- Wallet balances allocation integrity
ALTER TABLE wallet_balances DROP CONSTRAINT IF EXISTS chk_wallet_balances_allocation_integrity;
ALTER TABLE wallet_balances ADD CONSTRAINT chk_wallet_balances_allocation_integrity
    CHECK (
        ABS(balance - (available_balance + frozen_balance + pending_balance)) < 0.01
    );

-- ============================================================================
-- WALLET HOLDS CONSTRAINTS
-- ============================================================================

-- Ensure hold amounts are always positive
ALTER TABLE wallet_holds DROP CONSTRAINT IF EXISTS chk_wallet_holds_amount_positive;
ALTER TABLE wallet_holds ADD CONSTRAINT chk_wallet_holds_amount_positive
    CHECK (amount > 0);

-- ============================================================================
-- ENHANCED BALANCE VALIDATION FUNCTION
-- ============================================================================

-- Drop existing function and create enhanced version
DROP FUNCTION IF EXISTS validate_wallet_balance_update();

CREATE OR REPLACE FUNCTION validate_wallet_balance_update()
RETURNS TRIGGER AS $$
DECLARE
    balance_discrepancy DECIMAL(19,4);
    allocated_total DECIMAL(19,4);
BEGIN
    -- Calculate total allocated balance
    allocated_total := NEW.available_balance + NEW.frozen_balance + NEW.pending_balance;
    balance_discrepancy := ABS(NEW.balance - allocated_total);

    -- CRITICAL: Prevent negative balance without credit limit
    IF NEW.credit_limit IS NULL AND NEW.balance < 0 THEN
        RAISE EXCEPTION 'CRITICAL: Negative balance detected without credit limit. Wallet ID: %, Balance: %, Available: %',
            NEW.id, NEW.balance, NEW.available_balance
            USING ERRCODE = '23514', -- check_violation
                  HINT = 'This transaction would result in overdraft. Transaction blocked.';
    END IF;

    -- CRITICAL: Prevent available balance going negative without credit limit
    IF NEW.credit_limit IS NULL AND NEW.available_balance < 0 THEN
        RAISE EXCEPTION 'CRITICAL: Negative available balance detected. Wallet ID: %, Available: %, Frozen: %, Pending: %',
            NEW.id, NEW.available_balance, NEW.frozen_balance, NEW.pending_balance
            USING ERRCODE = '23514',
                  HINT = 'Insufficient funds for this operation.';
    END IF;

    -- Prevent frozen balance from being negative
    IF NEW.frozen_balance < 0 THEN
        RAISE EXCEPTION 'CRITICAL: Negative frozen balance detected. Wallet ID: %, Frozen: %',
            NEW.id, NEW.frozen_balance
            USING ERRCODE = '23514';
    END IF;

    -- Prevent pending balance from being negative
    IF NEW.pending_balance < 0 THEN
        RAISE EXCEPTION 'CRITICAL: Negative pending balance detected. Wallet ID: %, Pending: %',
            NEW.id, NEW.pending_balance
            USING ERRCODE = '23514';
    END IF;

    -- CRITICAL: Validate balance allocation integrity
    -- If discrepancy > 1 cent, this indicates a serious bug
    IF balance_discrepancy > 0.01 THEN
        RAISE EXCEPTION 'CRITICAL: Balance allocation mismatch detected. Wallet ID: %, Total: %, Allocated: % (Avail: % + Frozen: % + Pending: %), Discrepancy: %',
            NEW.id, NEW.balance, allocated_total,
            NEW.available_balance, NEW.frozen_balance, NEW.pending_balance,
            balance_discrepancy
            USING ERRCODE = '23514',
                  HINT = 'Balance components do not sum to total balance. Possible data corruption.';
    END IF;

    -- Validate credit limit usage
    IF NEW.credit_limit IS NOT NULL THEN
        IF NEW.available_balance < (NEW.credit_limit * -1) THEN
            RAISE EXCEPTION 'CRITICAL: Available balance exceeds credit limit. Wallet ID: %, Available: %, Credit Limit: %',
                NEW.id, NEW.available_balance, NEW.credit_limit
                USING ERRCODE = '23514',
                      HINT = 'Transaction would exceed credit limit.';
        END IF;
    END IF;

    -- Log critical balance changes for audit
    IF TG_OP = 'UPDATE' THEN
        IF OLD.balance != NEW.balance OR OLD.available_balance != NEW.available_balance THEN
            INSERT INTO wallet_audit (
                wallet_id,
                action,
                performed_by,
                old_values,
                new_values,
                reason,
                created_at
            ) VALUES (
                NEW.id,
                'BALANCE_UPDATE',
                COALESCE(current_setting('app.current_user_id', true)::UUID, '00000000-0000-0000-0000-000000000000'::UUID),
                jsonb_build_object(
                    'balance', OLD.balance,
                    'available_balance', OLD.available_balance,
                    'frozen_balance', OLD.frozen_balance,
                    'pending_balance', OLD.pending_balance,
                    'version', OLD.version
                ),
                jsonb_build_object(
                    'balance', NEW.balance,
                    'available_balance', NEW.available_balance,
                    'frozen_balance', NEW.frozen_balance,
                    'pending_balance', NEW.pending_balance,
                    'version', NEW.version
                ),
                'Automated balance validation',
                CURRENT_TIMESTAMP
            );
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop existing trigger and recreate
DROP TRIGGER IF EXISTS wallet_balance_validation_trigger ON wallets;
CREATE TRIGGER wallet_balance_validation_trigger
    BEFORE INSERT OR UPDATE ON wallets
    FOR EACH ROW
    EXECUTE FUNCTION validate_wallet_balance_update();

-- ============================================================================
-- MULTI-CURRENCY BALANCE VALIDATION
-- ============================================================================

CREATE OR REPLACE FUNCTION validate_wallet_balances_update()
RETURNS TRIGGER AS $$
DECLARE
    balance_discrepancy DECIMAL(19,4);
    allocated_total DECIMAL(19,4);
BEGIN
    allocated_total := NEW.available_balance + NEW.frozen_balance + NEW.pending_balance;
    balance_discrepancy := ABS(NEW.balance - allocated_total);

    -- Prevent negative balances
    IF NEW.balance < 0 THEN
        RAISE EXCEPTION 'CRITICAL: Negative balance in wallet_balances. Wallet ID: %, Currency: %, Balance: %',
            NEW.wallet_id, NEW.currency, NEW.balance
            USING ERRCODE = '23514';
    END IF;

    IF NEW.available_balance < 0 THEN
        RAISE EXCEPTION 'CRITICAL: Negative available balance in wallet_balances. Wallet ID: %, Currency: %, Available: %',
            NEW.wallet_id, NEW.currency, NEW.available_balance
            USING ERRCODE = '23514';
    END IF;

    -- Validate allocation integrity
    IF balance_discrepancy > 0.01 THEN
        RAISE EXCEPTION 'CRITICAL: Balance allocation mismatch in wallet_balances. Wallet ID: %, Currency: %, Discrepancy: %',
            NEW.wallet_id, NEW.currency, balance_discrepancy
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS wallet_balances_validation_trigger ON wallet_balances;
CREATE TRIGGER wallet_balances_validation_trigger
    BEFORE INSERT OR UPDATE ON wallet_balances
    FOR EACH ROW
    EXECUTE FUNCTION validate_wallet_balances_update();

-- ============================================================================
-- RECONCILIATION VIEW FOR MONITORING
-- ============================================================================

-- Create view to identify wallets with potential balance issues
CREATE OR REPLACE VIEW wallet_balance_health_check AS
SELECT
    w.id,
    w.wallet_id,
    w.user_id,
    w.currency,
    w.balance,
    w.available_balance,
    w.frozen_balance,
    w.pending_balance,
    (w.available_balance + w.frozen_balance + w.pending_balance) AS calculated_balance,
    ABS(w.balance - (w.available_balance + w.frozen_balance + w.pending_balance)) AS balance_discrepancy,
    w.credit_limit,
    CASE
        WHEN w.balance < 0 AND w.credit_limit IS NULL THEN 'CRITICAL: NEGATIVE_BALANCE_NO_CREDIT'
        WHEN w.available_balance < 0 AND w.credit_limit IS NULL THEN 'CRITICAL: NEGATIVE_AVAILABLE_NO_CREDIT'
        WHEN w.frozen_balance < 0 THEN 'CRITICAL: NEGATIVE_FROZEN'
        WHEN w.pending_balance < 0 THEN 'CRITICAL: NEGATIVE_PENDING'
        WHEN ABS(w.balance - (w.available_balance + w.frozen_balance + w.pending_balance)) > 0.01 THEN 'CRITICAL: ALLOCATION_MISMATCH'
        WHEN w.credit_limit IS NOT NULL AND w.available_balance < (w.credit_limit * -1) THEN 'WARNING: CREDIT_LIMIT_EXCEEDED'
        ELSE 'HEALTHY'
    END AS health_status,
    w.updated_at,
    w.version
FROM wallets w
WHERE
    w.status = 'ACTIVE'
    AND (
        (w.balance < 0 AND w.credit_limit IS NULL) OR
        (w.available_balance < 0 AND w.credit_limit IS NULL) OR
        w.frozen_balance < 0 OR
        w.pending_balance < 0 OR
        ABS(w.balance - (w.available_balance + w.frozen_balance + w.pending_balance)) > 0.01 OR
        (w.credit_limit IS NOT NULL AND w.available_balance < (w.credit_limit * -1))
    );

-- Grant SELECT permission on health check view
GRANT SELECT ON wallet_balance_health_check TO waqiti_app_user;

-- ============================================================================
-- MONITORING FUNCTION FOR AUTOMATED ALERTS
-- ============================================================================

CREATE OR REPLACE FUNCTION check_wallet_balance_health()
RETURNS TABLE (
    critical_count BIGINT,
    warning_count BIGINT,
    total_negative_exposure DECIMAL(19,4),
    affected_wallets TEXT[]
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*) FILTER (WHERE health_status LIKE 'CRITICAL%') AS critical_count,
        COUNT(*) FILTER (WHERE health_status LIKE 'WARNING%') AS warning_count,
        COALESCE(SUM(ABS(available_balance)) FILTER (WHERE available_balance < 0), 0) AS total_negative_exposure,
        ARRAY_AGG(wallet_id::TEXT) FILTER (WHERE health_status LIKE 'CRITICAL%') AS affected_wallets
    FROM wallet_balance_health_check;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON CONSTRAINT chk_wallet_balance_non_negative ON wallets IS
    'CRITICAL FINANCIAL CONTROL: Prevents negative wallet balances without credit limit. Protects against overdraft losses ($10K-50K/month exposure).';

COMMENT ON CONSTRAINT chk_wallet_available_balance_non_negative ON wallets IS
    'CRITICAL FINANCIAL CONTROL: Ensures available balance cannot go negative without approved credit limit.';

COMMENT ON CONSTRAINT chk_wallet_balance_allocation_integrity ON wallets IS
    'CRITICAL DATA INTEGRITY: Validates that balance = available + frozen + pending. Prevents phantom money scenarios.';

COMMENT ON FUNCTION validate_wallet_balance_update() IS
    'Database-level validation function that enforces strict financial controls on wallet balance updates. This is the last line of defense against overdraft.';

COMMENT ON VIEW wallet_balance_health_check IS
    'Real-time monitoring view for detecting wallet balance anomalies. Used by automated alerts and reconciliation jobs.';

-- ============================================================================
-- VERIFICATION QUERIES (For manual testing)
-- ============================================================================

-- These queries can be run to verify constraints are working:
--
-- 1. Check for any existing violations:
-- SELECT * FROM wallet_balance_health_check WHERE health_status LIKE 'CRITICAL%';
--
-- 2. Run health check:
-- SELECT * FROM check_wallet_balance_health();
--
-- 3. Test negative balance prevention (should FAIL):
-- UPDATE wallets SET balance = -100.00 WHERE id = '<some-wallet-id>' AND credit_limit IS NULL;
--
-- 4. Test allocation mismatch prevention (should FAIL):
-- UPDATE wallets SET balance = 1000.00, available_balance = 500.00, frozen_balance = 200.00, pending_balance = 100.00 WHERE id = '<some-wallet-id>';
--    (This would fail because 500 + 200 + 100 = 800, not 1000)

-- ============================================================================
-- ROLLBACK PROCEDURE (Emergency use only)
-- ============================================================================

-- If these constraints cause issues in production (they shouldn't!), rollback with:
--
-- ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_balance_non_negative;
-- ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_available_balance_non_negative;
-- ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_frozen_balance_non_negative;
-- ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_pending_balance_non_negative;
-- ALTER TABLE wallets DROP CONSTRAINT IF EXISTS chk_wallet_balance_allocation_integrity;
-- DROP TRIGGER IF EXISTS wallet_balance_validation_trigger ON wallets;
-- DROP FUNCTION IF EXISTS validate_wallet_balance_update();
--
-- WARNING: Rollback removes critical financial controls. Only do this if absolutely necessary.

-- ============================================================================
-- MIGRATION VALIDATION
-- ============================================================================

DO $$
DECLARE
    constraint_count INTEGER;
BEGIN
    -- Verify all critical constraints were created
    SELECT COUNT(*) INTO constraint_count
    FROM information_schema.table_constraints
    WHERE table_name = 'wallets'
    AND constraint_type = 'CHECK'
    AND constraint_name IN (
        'chk_wallet_balance_non_negative',
        'chk_wallet_available_balance_non_negative',
        'chk_wallet_frozen_balance_non_negative',
        'chk_wallet_pending_balance_non_negative',
        'chk_wallet_balance_allocation_integrity'
    );

    IF constraint_count < 5 THEN
        RAISE EXCEPTION 'Migration V9 validation failed: Expected 5 CHECK constraints, found %', constraint_count;
    END IF;

    RAISE NOTICE 'Migration V9 validation successful: All % CHECK constraints created', constraint_count;
END $$;
