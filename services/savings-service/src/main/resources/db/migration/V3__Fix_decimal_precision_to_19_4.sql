-- Fix DECIMAL(15,2) to DECIMAL(19,4) for all financial amounts in Savings service
-- Priority: HIGH - Critical for goal tracking and automated savings accuracy

-- savings_goals table - Fix target and current amount
ALTER TABLE savings_goals
    ALTER COLUMN target_amount TYPE DECIMAL(19,4),
    ALTER COLUMN current_amount TYPE DECIMAL(19,4);

-- savings_rules table - Fix limit amounts
ALTER TABLE savings_rules
    ALTER COLUMN minimum_amount TYPE DECIMAL(19,4),
    ALTER COLUMN maximum_amount TYPE DECIMAL(19,4),
    ALTER COLUMN monthly_limit TYPE DECIMAL(19,4),
    ALTER COLUMN current_monthly_total TYPE DECIMAL(19,4);

-- savings_transactions table - Fix amount fields
ALTER TABLE savings_transactions
    ALTER COLUMN amount TYPE DECIMAL(19,4),
    ALTER COLUMN running_balance TYPE DECIMAL(19,4);

-- savings_accounts table (if exists with balance fields)
DO $$
BEGIN
    IF EXISTS (
        SELECT FROM information_schema.columns
        WHERE table_name = 'savings_accounts'
        AND column_name = 'available_balance'
    ) THEN
        ALTER TABLE savings_accounts
            ALTER COLUMN available_balance TYPE DECIMAL(19,4),
            ALTER COLUMN pending_balance TYPE DECIMAL(19,4),
            ALTER COLUMN total_balance TYPE DECIMAL(19,4);
    END IF;
END $$;

-- Add comments
COMMENT ON COLUMN savings_goals.target_amount IS 'Target amount with 4 decimal precision for accurate goal tracking';
COMMENT ON COLUMN savings_goals.current_amount IS 'Current savings with 4 decimal precision';
COMMENT ON COLUMN savings_transactions.amount IS 'Transaction amount with 4 decimal precision for accurate accumulation';

-- Analyze tables
ANALYZE savings_goals;
ANALYZE savings_rules;
ANALYZE savings_transactions;
