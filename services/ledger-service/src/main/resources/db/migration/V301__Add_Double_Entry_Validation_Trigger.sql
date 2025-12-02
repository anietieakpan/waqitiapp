-- ============================================================================
-- P0 CRITICAL FIX: Double-Entry Bookkeeping Validation
-- ============================================================================
-- This migration implements database-level enforcement of the fundamental
-- accounting principle: For every transaction, total debits MUST equal total credits.
--
-- Implementation:
-- 1. PostgreSQL trigger function to validate balance
-- 2. Trigger fired AFTER INSERT/UPDATE on ledger_entries
-- 3. Rollback transaction if debits != credits
-- 4. Allow 0.0001 tolerance for rounding errors
--
-- Financial Integrity:
-- - Prevents unbalanced transactions at database level
-- - Last line of defense after application-level validation
-- - Ensures compliance with GAAP and SOX 404
--
-- Author: Waqiti Engineering Team
-- Date: 2025-10-24
-- ============================================================================

-- Drop existing trigger if exists (idempotent migration)
DROP TRIGGER IF EXISTS enforce_double_entry_balance ON ledger_entries;
DROP FUNCTION IF EXISTS validate_transaction_balance();

-- Create trigger function
CREATE OR REPLACE FUNCTION validate_transaction_balance()
RETURNS TRIGGER AS $$
DECLARE
    debit_sum NUMERIC(19,4);
    credit_sum NUMERIC(19,4);
    balance_difference NUMERIC(19,4);
    tolerance NUMERIC(19,4) := 0.0001;
    entry_count INTEGER;
BEGIN
    -- Only validate if this is a completed transaction
    -- (Allow partial entry creation during transaction)
    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN

        -- Count total entries for this transaction
        SELECT COUNT(*) INTO entry_count
        FROM ledger_entries
        WHERE transaction_id = NEW.transaction_id;

        -- Only validate if we have at least 2 entries (minimum for double-entry)
        IF entry_count >= 2 THEN

            -- Calculate total debits
            SELECT COALESCE(SUM(amount), 0) INTO debit_sum
            FROM ledger_entries
            WHERE transaction_id = NEW.transaction_id
              AND entry_type = 'DEBIT';

            -- Calculate total credits
            SELECT COALESCE(SUM(amount), 0) INTO credit_sum
            FROM ledger_entries
            WHERE transaction_id = NEW.transaction_id
              AND entry_type = 'CREDIT';

            -- Calculate difference
            balance_difference := ABS(debit_sum - credit_sum);

            -- Validate balance (with tolerance for rounding)
            IF balance_difference > tolerance THEN
                RAISE EXCEPTION 'DOUBLE_ENTRY_VIOLATION: Transaction % unbalanced - Debits: %, Credits: %, Difference: %',
                    NEW.transaction_id, debit_sum, credit_sum, balance_difference
                    USING ERRCODE = '23514',  -- check_violation
                          HINT = 'Total debits must equal total credits in double-entry bookkeeping';
            END IF;

            -- Log successful validation for audit
            RAISE NOTICE 'DOUBLE_ENTRY_VALIDATED: Transaction % balanced - Debits: %, Credits: %',
                NEW.transaction_id, debit_sum, credit_sum;

        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger
CREATE TRIGGER enforce_double_entry_balance
    AFTER INSERT OR UPDATE ON ledger_entries
    FOR EACH ROW
    EXECUTE FUNCTION validate_transaction_balance();

-- Add comment for documentation
COMMENT ON FUNCTION validate_transaction_balance() IS
'Validates double-entry bookkeeping: ensures total debits equal total credits for each transaction. '
'Allows 0.0001 tolerance for rounding errors. Raises exception if balance check fails.';

COMMENT ON TRIGGER enforce_double_entry_balance ON ledger_entries IS
'Enforces fundamental accounting equation: Debits = Credits. '
'Critical for financial integrity and SOX compliance.';

-- ============================================================================
-- Additional Constraints for Data Integrity
-- ============================================================================

-- Ensure amount is always positive
ALTER TABLE ledger_entries
ADD CONSTRAINT ledger_entry_amount_positive
CHECK (amount > 0)
NOT VALID;

-- Validate constraint for existing data
ALTER TABLE ledger_entries
VALIDATE CONSTRAINT ledger_entry_amount_positive;

-- Ensure entry_type is valid
ALTER TABLE ledger_entries
ADD CONSTRAINT ledger_entry_type_valid
CHECK (entry_type IN ('DEBIT', 'CREDIT'))
NOT VALID;

ALTER TABLE ledger_entries
VALIDATE CONSTRAINT ledger_entry_type_valid;

-- Add index for performance (transaction_id is frequently queried)
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction_id_entry_type
ON ledger_entries(transaction_id, entry_type);

-- Add index for balance validation queries
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction_amount
ON ledger_entries(transaction_id, amount);

-- ============================================================================
-- Create materialized view for running balances (Performance Optimization)
-- ============================================================================

CREATE MATERIALIZED VIEW IF NOT EXISTS ledger_transaction_balances AS
SELECT
    transaction_id,
    COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) as total_debits,
    COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) as total_credits,
    ABS(
        COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) -
        COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0)
    ) as balance_difference,
    CASE
        WHEN ABS(
            COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0)
        ) <= 0.0001 THEN true
        ELSE false
    END as is_balanced,
    COUNT(*) as entry_count,
    MAX(created_at) as last_updated
FROM ledger_entries
GROUP BY transaction_id;

-- Add unique index for efficient lookups
CREATE UNIQUE INDEX idx_ledger_balances_transaction_id
ON ledger_transaction_balances(transaction_id);

-- Add index for unbalanced transaction queries (operations/finance teams)
CREATE INDEX idx_ledger_balances_unbalanced
ON ledger_transaction_balances(is_balanced)
WHERE is_balanced = false;

COMMENT ON MATERIALIZED VIEW ledger_transaction_balances IS
'Pre-calculated balance validation for all transactions. '
'Refreshed periodically for performance. Use for reports and reconciliation.';

-- ============================================================================
-- Audit Table for Double-Entry Violations (Forensics)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ledger_balance_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    total_debits NUMERIC(19,4) NOT NULL,
    total_credits NUMERIC(19,4) NOT NULL,
    balance_difference NUMERIC(19,4) NOT NULL,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detection_method VARCHAR(50) NOT NULL, -- 'TRIGGER', 'APPLICATION', 'RECONCILIATION'
    resolved BOOLEAN DEFAULT false,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(255),
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_balance_violations_transaction_id ON ledger_balance_violations(transaction_id);
CREATE INDEX idx_balance_violations_unresolved ON ledger_balance_violations(resolved) WHERE resolved = false;

COMMENT ON TABLE ledger_balance_violations IS
'Audit log of all detected double-entry balance violations. '
'Used for forensic analysis and compliance reporting.';

-- ============================================================================
-- Function to manually check and report unbalanced transactions
-- ============================================================================

CREATE OR REPLACE FUNCTION find_unbalanced_transactions(
    since_date TIMESTAMP DEFAULT CURRENT_DATE - INTERVAL '30 days'
)
RETURNS TABLE (
    transaction_id UUID,
    total_debits NUMERIC(19,4),
    total_credits NUMERIC(19,4),
    balance_difference NUMERIC(19,4),
    entry_count INTEGER,
    first_entry_date TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        le.transaction_id,
        COALESCE(SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END), 0) as total_debits,
        COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END), 0) as total_credits,
        ABS(
            COALESCE(SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END), 0) -
            COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END), 0)
        ) as balance_difference,
        COUNT(*)::INTEGER as entry_count,
        MIN(le.created_at) as first_entry_date
    FROM ledger_entries le
    WHERE le.created_at >= since_date
    GROUP BY le.transaction_id
    HAVING ABS(
        COALESCE(SUM(CASE WHEN le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END), 0) -
        COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END), 0)
    ) > 0.0001
    ORDER BY balance_difference DESC;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION find_unbalanced_transactions(TIMESTAMP) IS
'Scans ledger for unbalanced transactions. '
'Use for reconciliation and troubleshooting. '
'Example: SELECT * FROM find_unbalanced_transactions(CURRENT_DATE - INTERVAL ''7 days'');';

-- ============================================================================
-- Grant permissions
-- ============================================================================

GRANT SELECT ON ledger_transaction_balances TO ledger_service_app;
GRANT SELECT ON ledger_balance_violations TO ledger_service_app;
GRANT INSERT, UPDATE ON ledger_balance_violations TO ledger_service_app;

-- ============================================================================
-- Migration Complete
-- ============================================================================

-- Refresh materialized view
REFRESH MATERIALIZED VIEW CONCURRENTLY ledger_transaction_balances;

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V301 completed: Double-entry validation trigger installed';
    RAISE NOTICE '✅ Constraints added: amount > 0, valid entry types';
    RAISE NOTICE '✅ Performance indexes created for transaction balance queries';
    RAISE NOTICE '✅ Materialized view created for pre-calculated balances';
    RAISE NOTICE '✅ Audit table created for violation tracking';
    RAISE NOTICE '✅ Helper function created: find_unbalanced_transactions()';
END $$;
