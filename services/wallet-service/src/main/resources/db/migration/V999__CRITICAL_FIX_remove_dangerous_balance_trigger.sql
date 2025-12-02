-- CRITICAL FIX: Remove Dangerous Database Trigger
--
-- ISSUE: The trigger_update_wallet_balance causes double-counting because:
-- 1. Application code (WalletBalanceService) also updates balances
-- 2. Trigger fires automatically on transaction insert/update
-- 3. This results in balance = application_update + trigger_update (WRONG!)
--
-- IMPACT: Financial integrity violations, incorrect balances
-- PRIORITY: P0 - IMMEDIATE
-- DATE: 2025-10-31
--
-- RESOLUTION: Remove trigger and manage all balance updates in application code
--             with proper pessimistic locking

-- Drop the dangerous trigger
DROP TRIGGER IF EXISTS trigger_update_wallet_balance ON transactions;

-- Drop the trigger function
DROP FUNCTION IF EXISTS update_wallet_balance();

-- Add audit log entry
INSERT INTO wallet_audit (
    wallet_id,
    action,
    performed_by,
    old_values,
    new_values,
    reason,
    created_at
) SELECT
    id as wallet_id,
    'CRITICAL_FIX_TRIGGER_REMOVAL' as action,
    '00000000-0000-0000-0000-000000000000'::uuid as performed_by,
    jsonb_build_object(
        'trigger_name', 'trigger_update_wallet_balance',
        'function_name', 'update_wallet_balance()',
        'risk_level', 'CRITICAL',
        'issue', 'Double-counting balance updates'
    ) as old_values,
    jsonb_build_object(
        'resolution', 'All balance updates now managed in WalletBalanceService',
        'locking_strategy', 'Pessimistic write locks',
        'applied_at', CURRENT_TIMESTAMP
    ) as new_values,
    'CRITICAL P0 FIX: Removed dangerous database trigger that caused balance double-counting. ' ||
    'All balance updates are now managed exclusively in application code with proper locking.' as reason,
    CURRENT_TIMESTAMP as created_at
FROM wallets
LIMIT 1;

-- Add comment for future reference
COMMENT ON TABLE wallets IS 'Wallet balances are managed exclusively in WalletBalanceService.java with pessimistic locking. DO NOT add database triggers for balance updates.';

-- Create index for daily limit reset queries (performance optimization)
CREATE INDEX IF NOT EXISTS idx_wallets_limit_reset_date ON wallets(limit_reset_date);

-- Add columns for daily limit tracking if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='wallets' AND column_name='daily_spent') THEN
        ALTER TABLE wallets ADD COLUMN daily_spent DECIMAL(19,4) DEFAULT 0.0000 NOT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='wallets' AND column_name='monthly_spent') THEN
        ALTER TABLE wallets ADD COLUMN monthly_spent DECIMAL(19,4) DEFAULT 0.0000 NOT NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='wallets' AND column_name='limit_reset_date') THEN
        ALTER TABLE wallets ADD COLUMN limit_reset_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='wallets' AND column_name='monthly_reset_date') THEN
        ALTER TABLE wallets ADD COLUMN monthly_reset_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
    END IF;
END$$;

-- Verify trigger removal
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trigger_update_wallet_balance') THEN
        RAISE EXCEPTION 'CRITICAL: Trigger was not removed successfully!';
    END IF;

    RAISE NOTICE 'SUCCESS: Dangerous trigger removed. Balance updates now managed safely in application code.';
END$$;
