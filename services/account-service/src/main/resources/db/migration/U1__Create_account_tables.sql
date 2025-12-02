-- Rollback Script for V1__Create_account_tables.sql
-- This script safely reverses all changes made in the forward migration
-- Execute this script if V1 migration needs to be rolled back

-- Drop triggers first
DROP TRIGGER IF EXISTS update_account_settings_updated_at ON account_settings;
DROP TRIGGER IF EXISTS update_accounts_updated_at ON accounts;

-- Drop function
DROP FUNCTION IF EXISTS update_updated_at_column();

-- Drop indexes
DROP INDEX IF EXISTS idx_account_permissions_status;
DROP INDEX IF EXISTS idx_account_permissions_user_id;
DROP INDEX IF EXISTS idx_account_permissions_account_id;

DROP INDEX IF EXISTS idx_account_statements_statement_date;
DROP INDEX IF EXISTS idx_account_statements_account_id;

DROP INDEX IF EXISTS idx_balance_history_transaction_id;
DROP INDEX IF EXISTS idx_balance_history_created_at;
DROP INDEX IF EXISTS idx_balance_history_account_id;

DROP INDEX IF EXISTS idx_account_balances_currency;
DROP INDEX IF EXISTS idx_account_balances_account_id;

DROP INDEX IF EXISTS idx_accounts_external_account_id;
DROP INDEX IF EXISTS idx_accounts_status;
DROP INDEX IF EXISTS idx_accounts_account_number;
DROP INDEX IF EXISTS idx_accounts_business_id;
DROP INDEX IF EXISTS idx_accounts_user_id;

-- Drop tables in reverse dependency order
DROP TABLE IF EXISTS account_settings CASCADE;
DROP TABLE IF EXISTS account_permissions CASCADE;
DROP TABLE IF EXISTS account_statements CASCADE;
DROP TABLE IF EXISTS balance_history CASCADE;
DROP TABLE IF EXISTS account_balances CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;

-- Verification: Ensure all objects are dropped
DO $$
BEGIN
    -- Verify tables are dropped
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name IN (
        'accounts', 'account_balances', 'balance_history',
        'account_statements', 'account_permissions', 'account_settings'
    )) THEN
        RAISE EXCEPTION 'Rollback failed: Some tables still exist';
    END IF;

    -- Verify function is dropped
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'update_updated_at_column') THEN
        RAISE EXCEPTION 'Rollback failed: Function update_updated_at_column still exists';
    END IF;

    RAISE NOTICE 'Rollback V1__Create_account_tables completed successfully';
END $$;
