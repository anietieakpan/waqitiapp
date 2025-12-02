-- ROLLBACK SCRIPT for V300__Fix_critical_decimal_precision_and_constraints.sql
-- This script safely reverts the DECIMAL precision changes and NOT NULL constraints
-- DANGER: Only run if absolutely necessary - financial data precision will be reduced

-- =============================================================================
-- PART 1: PRE-ROLLBACK VALIDATION
-- =============================================================================

DO $$
DECLARE
    backup_exists BOOLEAN;
    data_loss_risk BOOLEAN := FALSE;
    max_balance DECIMAL(19,4);
    records_at_risk INTEGER := 0;
BEGIN
    -- Verify backup tables exist
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'accounts_backup_pre_v300'
    ) INTO backup_exists;

    IF NOT backup_exists THEN
        RAISE EXCEPTION 'ROLLBACK ABORTED: Backup tables not found. Cannot safely rollback without backups.';
    END IF;

    -- Check if any balances would lose precision
    SELECT MAX(GREATEST(
        ABS(available_balance),
        ABS(current_balance),
        ABS(pending_balance),
        ABS(held_balance)
    )) INTO max_balance
    FROM account_balances;

    -- DECIMAL(15,2) max value is 9,999,999,999,999.99
    IF max_balance > 9999999999999.99 THEN
        data_loss_risk := TRUE;
        RAISE WARNING 'DATA LOSS RISK: Maximum balance (%) exceeds DECIMAL(15,2) capacity', max_balance;
    END IF;

    -- Check for values that use more than 2 decimal places
    SELECT COUNT(*) INTO records_at_risk
    FROM account_balances
    WHERE MOD(available_balance * 10000, 100) != 0
       OR MOD(current_balance * 10000, 100) != 0
       OR MOD(pending_balance * 10000, 100) != 0
       OR MOD(held_balance * 10000, 100) != 0;

    IF records_at_risk > 0 THEN
        RAISE WARNING 'PRECISION LOSS WARNING: % records have more than 2 decimal places', records_at_risk;
        RAISE WARNING 'These values will be rounded during rollback';
    END IF;

    IF data_loss_risk THEN
        RAISE EXCEPTION 'ROLLBACK ABORTED: Data loss risk detected. Manual intervention required.';
    END IF;

    RAISE NOTICE 'Pre-rollback validation passed';
    RAISE NOTICE 'Backup tables verified: accounts_backup_pre_v300 exists';
    IF records_at_risk > 0 THEN
        RAISE NOTICE 'WARNING: % records will lose decimal precision', records_at_risk;
    END IF;
END $$;

-- =============================================================================
-- PART 2: CREATE PRE-ROLLBACK SNAPSHOT
-- =============================================================================

CREATE TABLE account_balances_pre_rollback_v300 AS
SELECT * FROM account_balances;

RAISE NOTICE 'Created pre-rollback snapshot: account_balances_pre_rollback_v300';

-- =============================================================================
-- PART 3: REMOVE NOT NULL CONSTRAINTS
-- =============================================================================

-- Remove NOT NULL constraints added in V300
ALTER TABLE account_balances
    ALTER COLUMN available_balance DROP NOT NULL,
    ALTER COLUMN current_balance DROP NOT NULL,
    ALTER COLUMN pending_balance DROP NOT NULL,
    ALTER COLUMN held_balance DROP NOT NULL;

ALTER TABLE balance_history
    ALTER COLUMN previous_balance DROP NOT NULL,
    ALTER COLUMN new_balance DROP NOT NULL,
    ALTER COLUMN change_amount DROP NOT NULL;

ALTER TABLE account_statements
    ALTER COLUMN opening_balance DROP NOT NULL,
    ALTER COLUMN closing_balance DROP NOT NULL;

RAISE NOTICE 'Removed NOT NULL constraints';

-- =============================================================================
-- PART 4: REVERT DECIMAL PRECISION (WITH ROUNDING)
-- =============================================================================

-- WARNING: This will round values to 2 decimal places

-- Revert accounts table
ALTER TABLE accounts
    ALTER COLUMN overdraft_limit TYPE DECIMAL(15,2);

ALTER TABLE accounts
    ALTER COLUMN daily_transaction_limit TYPE DECIMAL(15,2),
    ALTER COLUMN monthly_transaction_limit TYPE DECIMAL(15,2),
    ALTER COLUMN annual_transaction_limit TYPE DECIMAL(15,2);

-- Revert account_balances (CRITICAL - will lose precision)
ALTER TABLE account_balances
    ALTER COLUMN available_balance TYPE DECIMAL(15,2),
    ALTER COLUMN current_balance TYPE DECIMAL(15,2),
    ALTER COLUMN pending_balance TYPE DECIMAL(15,2),
    ALTER COLUMN held_balance TYPE DECIMAL(15,2);

-- Revert balance_history
ALTER TABLE balance_history
    ALTER COLUMN previous_balance TYPE DECIMAL(15,2),
    ALTER COLUMN new_balance TYPE DECIMAL(15,2),
    ALTER COLUMN change_amount TYPE DECIMAL(15,2);

-- Revert account_statements
ALTER TABLE account_statements
    ALTER COLUMN opening_balance TYPE DECIMAL(15,2),
    ALTER COLUMN closing_balance TYPE DECIMAL(15,2),
    ALTER COLUMN total_credits TYPE DECIMAL(15,2),
    ALTER COLUMN total_debits TYPE DECIMAL(15,2);

-- Revert account_permissions
ALTER TABLE account_permissions
    ALTER COLUMN daily_limit TYPE DECIMAL(15,2),
    ALTER COLUMN monthly_limit TYPE DECIMAL(15,2);

-- Revert account_settings
ALTER TABLE account_settings
    ALTER COLUMN low_balance_threshold TYPE DECIMAL(15,2);

RAISE NOTICE 'Reverted DECIMAL precision to (15,2) - precision loss occurred';

-- =============================================================================
-- PART 5: RESTORE ORIGINAL CHECK CONSTRAINT
-- =============================================================================

ALTER TABLE account_balances
    DROP CONSTRAINT IF EXISTS account_balances_balance_integrity_check;

-- Restore the strict equality constraint
ALTER TABLE account_balances
    ADD CONSTRAINT account_balances_current_balance_check
    CHECK (current_balance = available_balance + pending_balance + held_balance);

RAISE NOTICE 'Restored strict balance integrity check constraint';

-- =============================================================================
-- PART 6: REMOVE AUDIT TRACKING DEFAULT
-- =============================================================================

ALTER TABLE balance_history
    ALTER COLUMN created_by DROP DEFAULT;

RAISE NOTICE 'Removed created_by default value';

-- =============================================================================
-- PART 7: DROP CLEANUP FUNCTION
-- =============================================================================

DROP FUNCTION IF EXISTS cleanup_v300_backups();

RAISE NOTICE 'Dropped cleanup_v300_backups function';

-- =============================================================================
-- PART 8: REMOVE COMMENTS
-- =============================================================================

COMMENT ON COLUMN account_balances.available_balance IS NULL;
COMMENT ON COLUMN account_balances.current_balance IS NULL;
COMMENT ON COLUMN account_balances.pending_balance IS NULL;
COMMENT ON COLUMN account_balances.held_balance IS NULL;

-- =============================================================================
-- PART 9: VERIFY ROLLBACK
-- =============================================================================

DO $$
DECLARE
    precision_check BOOLEAN;
    records_changed INTEGER;
BEGIN
    -- Verify DECIMAL precision is back to (15,2)
    SELECT
        numeric_precision = 15 AND numeric_scale = 2
    INTO precision_check
    FROM information_schema.columns
    WHERE table_name = 'account_balances'
      AND column_name = 'available_balance';

    IF NOT precision_check THEN
        RAISE EXCEPTION 'Rollback verification failed: DECIMAL precision not restored';
    END IF;

    -- Count records that were changed
    SELECT COUNT(*) INTO records_changed
    FROM account_balances ab
    JOIN account_balances_pre_rollback_v300 pre
      ON ab.id = pre.id
    WHERE ab.available_balance != ROUND(pre.available_balance::numeric, 2)
       OR ab.current_balance != ROUND(pre.current_balance::numeric, 2)
       OR ab.pending_balance != ROUND(pre.pending_balance::numeric, 2)
       OR ab.held_balance != ROUND(pre.held_balance::numeric, 2);

    RAISE NOTICE 'Rollback verification:';
    RAISE NOTICE '  - DECIMAL precision: (15,2) ✓';
    RAISE NOTICE '  - NOT NULL constraints: Removed ✓';
    RAISE NOTICE '  - Records with rounded values: %', records_changed;

    IF records_changed > 0 THEN
        RAISE WARNING 'PRECISION LOSS: % records had values rounded to 2 decimal places', records_changed;
        RAISE WARNING 'Compare with account_balances_pre_rollback_v300 to see original values';
    END IF;
END $$;

-- =============================================================================
-- ROLLBACK COMPLETE
-- =============================================================================

DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '╔════════════════════════════════════════════════════════════════╗';
    RAISE NOTICE '║  V300 ROLLBACK COMPLETED                                       ║';
    RAISE NOTICE '╠════════════════════════════════════════════════════════════════╣';
    RAISE NOTICE '║  ⚠ DECIMAL precision reverted: (19,4) → (15,2)               ║';
    RAISE NOTICE '║  ⚠ NOT NULL constraints removed                               ║';
    RAISE NOTICE '║  ⚠ Some precision may have been lost                          ║';
    RAISE NOTICE '║                                                                ║';
    RAISE NOTICE '║  RETAINED TABLES FOR INVESTIGATION:                           ║';
    RAISE NOTICE '║  - accounts_backup_pre_v300 (original data)                   ║';
    RAISE NOTICE '║  - account_balances_pre_rollback_v300 (before rollback)       ║';
    RAISE NOTICE '║                                                                ║';
    RAISE NOTICE '║  NEXT STEPS:                                                   ║';
    RAISE NOTICE '║  1. Investigate why rollback was necessary                    ║';
    RAISE NOTICE '║  2. Fix root cause                                            ║';
    RAISE NOTICE '║  3. Re-apply V300 migration                                   ║';
    RAISE NOTICE '║  4. Consider data migration strategy                          ║';
    RAISE NOTICE '╚════════════════════════════════════════════════════════════════╝';
    RAISE NOTICE '';
END $$;
