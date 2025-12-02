-- Data Validation Script for Cyclos to Internal Migration
-- This script validates data consistency between Cyclos and internal banking system

-- Set search path and configuration
\set ON_ERROR_STOP on
\set ECHO all

-- Create temporary tables for validation results
CREATE TEMP TABLE IF NOT EXISTS validation_results (
    check_name VARCHAR(255),
    status VARCHAR(20),
    cyclos_count BIGINT,
    internal_count BIGINT,
    difference BIGINT,
    details TEXT
);

-- Function to log validation results
CREATE OR REPLACE FUNCTION log_validation_result(
    p_check_name TEXT,
    p_status TEXT,
    p_cyclos_count BIGINT DEFAULT NULL,
    p_internal_count BIGINT DEFAULT NULL,
    p_details TEXT DEFAULT NULL
) RETURNS VOID AS $$
BEGIN
    INSERT INTO validation_results (check_name, status, cyclos_count, internal_count, difference, details)
    VALUES (
        p_check_name,
        p_status,
        p_cyclos_count,
        p_internal_count,
        COALESCE(p_cyclos_count, 0) - COALESCE(p_internal_count, 0),
        p_details
    );
END;
$$ LANGUAGE plpgsql;

-- Begin validation checks
\echo 'Starting data validation between Cyclos and Internal Banking System...'

-- 1. User Count Validation
\echo 'Checking user counts...'
DO $$
DECLARE
    cyclos_users BIGINT;
    internal_users BIGINT;
BEGIN
    -- Count active users in Cyclos (this would need to be run against Cyclos DB)
    -- For demonstration, we'll use placeholder values
    cyclos_users := 0; -- This would be: SELECT COUNT(*) FROM cyclos.users WHERE removed = false;
    
    -- Count users in internal system
    SELECT COUNT(*) INTO internal_users FROM users WHERE status = 'ACTIVE';
    
    IF cyclos_users = internal_users THEN
        PERFORM log_validation_result('User Count', 'PASS', cyclos_users, internal_users, 'User counts match');
    ELSE
        PERFORM log_validation_result('User Count', 'FAIL', cyclos_users, internal_users, 'User count mismatch detected');
    END IF;
END $$;

-- 2. Account Count Validation
\echo 'Checking account counts...'
DO $$
DECLARE
    cyclos_accounts BIGINT;
    internal_accounts BIGINT;
BEGIN
    -- Count active accounts in Cyclos
    cyclos_accounts := 0; -- Placeholder
    
    -- Count accounts in internal system
    SELECT COUNT(*) INTO internal_accounts FROM accounts WHERE account_status = 'ACTIVE';
    
    IF cyclos_accounts = internal_accounts THEN
        PERFORM log_validation_result('Account Count', 'PASS', cyclos_accounts, internal_accounts, 'Account counts match');
    ELSE
        PERFORM log_validation_result('Account Count', 'FAIL', cyclos_accounts, internal_accounts, 'Account count mismatch detected');
    END IF;
END $$;

-- 3. Transaction Count Validation
\echo 'Checking transaction counts...'
DO $$
DECLARE
    cyclos_transactions BIGINT;
    internal_transactions BIGINT;
BEGIN
    -- Count processed transactions in Cyclos
    cyclos_transactions := 0; -- Placeholder
    
    -- Count transactions in internal system
    SELECT COUNT(*) INTO internal_transactions FROM transactions WHERE transaction_status = 'COMPLETED';
    
    IF cyclos_transactions = internal_transactions THEN
        PERFORM log_validation_result('Transaction Count', 'PASS', cyclos_transactions, internal_transactions, 'Transaction counts match');
    ELSE
        PERFORM log_validation_result('Transaction Count', 'FAIL', cyclos_transactions, internal_transactions, 'Transaction count mismatch detected');
    END IF;
END $$;

-- 4. Balance Validation
\echo 'Checking balance consistency...'
DO $$
DECLARE
    total_balance_diff DECIMAL(19,4);
    accounts_with_diff INTEGER;
BEGIN
    -- Check for accounts with balance discrepancies
    -- This would compare balances between Cyclos and internal system
    SELECT COUNT(*) INTO accounts_with_diff 
    FROM accounts a
    WHERE NOT EXISTS (
        SELECT 1 FROM balance_snapshots bs 
        WHERE bs.account_id = a.id 
        AND bs.balance = a.balance
        AND bs.snapshot_date = CURRENT_DATE
    );
    
    IF accounts_with_diff = 0 THEN
        PERFORM log_validation_result('Balance Consistency', 'PASS', NULL, NULL, 'All account balances are consistent');
    ELSE
        PERFORM log_validation_result('Balance Consistency', 'FAIL', NULL, accounts_with_diff, 
            'Found ' || accounts_with_diff || ' accounts with balance discrepancies');
    END IF;
END $$;

-- 5. Data Integrity Validation
\echo 'Checking data integrity...'

-- Check for orphaned accounts (accounts without users)
DO $$
DECLARE
    orphaned_accounts INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphaned_accounts
    FROM accounts a
    WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = a.user_id);
    
    IF orphaned_accounts = 0 THEN
        PERFORM log_validation_result('Orphaned Accounts', 'PASS', NULL, NULL, 'No orphaned accounts found');
    ELSE
        PERFORM log_validation_result('Orphaned Accounts', 'FAIL', NULL, orphaned_accounts, 
            'Found ' || orphaned_accounts || ' orphaned accounts');
    END IF;
END $$;

-- Check for transactions without valid accounts
DO $$
DECLARE
    invalid_transactions INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_transactions
    FROM transactions t
    WHERE (t.from_account_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.from_account_id))
       OR (t.to_account_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.to_account_id));
    
    IF invalid_transactions = 0 THEN
        PERFORM log_validation_result('Transaction Integrity', 'PASS', NULL, NULL, 'All transactions have valid accounts');
    ELSE
        PERFORM log_validation_result('Transaction Integrity', 'FAIL', NULL, invalid_transactions, 
            'Found ' || invalid_transactions || ' transactions with invalid account references');
    END IF;
END $$;

-- 6. Business Rule Validation
\echo 'Checking business rules...'

-- Check for negative balances where not allowed
DO $$
DECLARE
    negative_balance_accounts INTEGER;
BEGIN
    SELECT COUNT(*) INTO negative_balance_accounts
    FROM accounts a
    WHERE a.balance < 0 AND a.overdraft_limit <= 0;
    
    IF negative_balance_accounts = 0 THEN
        PERFORM log_validation_result('Negative Balance Check', 'PASS', NULL, NULL, 'No unauthorized negative balances');
    ELSE
        PERFORM log_validation_result('Negative Balance Check', 'FAIL', NULL, negative_balance_accounts, 
            'Found ' || negative_balance_accounts || ' accounts with unauthorized negative balances');
    END IF;
END $$;

-- Check for transactions exceeding account limits
DO $$
DECLARE
    overlimit_transactions INTEGER;
BEGIN
    SELECT COUNT(*) INTO overlimit_transactions
    FROM transactions t
    JOIN accounts a ON a.id = t.from_account_id
    WHERE t.amount > a.daily_withdrawal_limit 
    AND a.daily_withdrawal_limit > 0;
    
    IF overlimit_transactions = 0 THEN
        PERFORM log_validation_result('Transaction Limits', 'PASS', NULL, NULL, 'All transactions within limits');
    ELSE
        PERFORM log_validation_result('Transaction Limits', 'FAIL', NULL, overlimit_transactions, 
            'Found ' || overlimit_transactions || ' transactions exceeding daily limits');
    END IF;
END $$;

-- 7. Temporal Data Validation
\echo 'Checking temporal data consistency...'

-- Check for future-dated transactions
DO $$
DECLARE
    future_transactions INTEGER;
BEGIN
    SELECT COUNT(*) INTO future_transactions
    FROM transactions t
    WHERE t.created_at > CURRENT_TIMESTAMP;
    
    IF future_transactions = 0 THEN
        PERFORM log_validation_result('Future Transactions', 'PASS', NULL, NULL, 'No future-dated transactions');
    ELSE
        PERFORM log_validation_result('Future Transactions', 'FAIL', NULL, future_transactions, 
            'Found ' || future_transactions || ' future-dated transactions');
    END IF;
END $$;

-- Check for accounts created before users
DO $$
DECLARE
    invalid_account_dates INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_account_dates
    FROM accounts a
    JOIN users u ON u.id = a.user_id
    WHERE a.created_at < u.created_at;
    
    IF invalid_account_dates = 0 THEN
        PERFORM log_validation_result('Account Creation Dates', 'PASS', NULL, NULL, 'All account creation dates are valid');
    ELSE
        PERFORM log_validation_result('Account Creation Dates', 'FAIL', NULL, invalid_account_dates, 
            'Found ' || invalid_account_dates || ' accounts created before their users');
    END IF;
END $$;

-- 8. Currency Validation
\echo 'Checking currency consistency...'

-- Check for invalid currency codes
DO $$
DECLARE
    invalid_currencies INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_currencies
    FROM accounts a
    WHERE a.currency NOT IN ('USD', 'EUR', 'GBP', 'CAD', 'AUD');
    
    IF invalid_currencies = 0 THEN
        PERFORM log_validation_result('Currency Codes', 'PASS', NULL, NULL, 'All currency codes are valid');
    ELSE
        PERFORM log_validation_result('Currency Codes', 'FAIL', NULL, invalid_currencies, 
            'Found ' || invalid_currencies || ' accounts with invalid currency codes');
    END IF;
END $$;

-- 9. Status Validation
\echo 'Checking status field consistency...'

-- Check for invalid account statuses
DO $$
DECLARE
    invalid_account_statuses INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_account_statuses
    FROM accounts a
    WHERE a.account_status NOT IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED');
    
    IF invalid_account_statuses = 0 THEN
        PERFORM log_validation_result('Account Statuses', 'PASS', NULL, NULL, 'All account statuses are valid');
    ELSE
        PERFORM log_validation_result('Account Statuses', 'FAIL', NULL, invalid_account_statuses, 
            'Found ' || invalid_account_statuses || ' accounts with invalid statuses');
    END IF;
END $$;

-- Check for invalid transaction statuses
DO $$
DECLARE
    invalid_transaction_statuses INTEGER;
BEGIN
    SELECT COUNT(*) INTO invalid_transaction_statuses
    FROM transactions t
    WHERE t.transaction_status NOT IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED');
    
    IF invalid_transaction_statuses = 0 THEN
        PERFORM log_validation_result('Transaction Statuses', 'PASS', NULL, NULL, 'All transaction statuses are valid');
    ELSE
        PERFORM log_validation_result('Transaction Statuses', 'FAIL', NULL, invalid_transaction_statuses, 
            'Found ' || invalid_transaction_statuses || ' transactions with invalid statuses');
    END IF;
END $$;

-- 10. Ledger Balance Validation
\echo 'Checking ledger balance consistency...'

-- Validate that account balances match ledger entries
DO $$
DECLARE
    balance_mismatches INTEGER;
BEGIN
    WITH ledger_balances AS (
        SELECT 
            le.account_id,
            SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END) as calculated_balance
        FROM ledger_entries le
        GROUP BY le.account_id
    )
    SELECT COUNT(*) INTO balance_mismatches
    FROM accounts a
    LEFT JOIN ledger_balances lb ON a.id = lb.account_id::uuid
    WHERE ABS(COALESCE(a.balance, 0) - COALESCE(lb.calculated_balance, 0)) > 0.01;
    
    IF balance_mismatches = 0 THEN
        PERFORM log_validation_result('Ledger Balance Consistency', 'PASS', NULL, NULL, 'All account balances match ledger');
    ELSE
        PERFORM log_validation_result('Ledger Balance Consistency', 'FAIL', NULL, balance_mismatches, 
            'Found ' || balance_mismatches || ' accounts with ledger balance mismatches');
    END IF;
END $$;

-- Display validation results
\echo ''
\echo '========================================='
\echo 'VALIDATION RESULTS SUMMARY'
\echo '========================================='

SELECT 
    check_name as "Check Name",
    status as "Status",
    CASE 
        WHEN cyclos_count IS NOT NULL AND internal_count IS NOT NULL 
        THEN cyclos_count || ' / ' || internal_count 
        ELSE COALESCE(internal_count::text, 'N/A')
    END as "Counts (Cyclos/Internal)",
    CASE 
        WHEN difference IS NOT NULL AND difference != 0 
        THEN difference::text 
        ELSE 'N/A'
    END as "Difference",
    details as "Details"
FROM validation_results
ORDER BY 
    CASE status 
        WHEN 'FAIL' THEN 1 
        WHEN 'WARN' THEN 2 
        WHEN 'PASS' THEN 3 
        ELSE 4 
    END,
    check_name;

-- Summary statistics
\echo ''
\echo 'VALIDATION SUMMARY:'

DO $$
DECLARE
    total_checks INTEGER;
    passed_checks INTEGER;
    failed_checks INTEGER;
    success_rate DECIMAL(5,2);
BEGIN
    SELECT COUNT(*) INTO total_checks FROM validation_results;
    SELECT COUNT(*) INTO passed_checks FROM validation_results WHERE status = 'PASS';
    SELECT COUNT(*) INTO failed_checks FROM validation_results WHERE status = 'FAIL';
    
    IF total_checks > 0 THEN
        success_rate := (passed_checks::DECIMAL / total_checks::DECIMAL) * 100;
    ELSE
        success_rate := 0;
    END IF;
    
    RAISE NOTICE 'Total Checks: %', total_checks;
    RAISE NOTICE 'Passed: %', passed_checks;
    RAISE NOTICE 'Failed: %', failed_checks;
    RAISE NOTICE 'Success Rate: %', success_rate || '%';
    
    IF failed_checks = 0 THEN
        RAISE NOTICE 'STATUS: ALL VALIDATIONS PASSED';
    ELSE
        RAISE NOTICE 'STATUS: % VALIDATION(S) FAILED', failed_checks;
    END IF;
END $$;

-- Export validation results to CSV for reporting
\echo ''
\echo 'Exporting validation results...'

\copy (SELECT check_name, status, cyclos_count, internal_count, difference, details FROM validation_results ORDER BY check_name) TO '/tmp/validation_results.csv' WITH CSV HEADER;

\echo 'Validation results exported to /tmp/validation_results.csv'
\echo 'Validation script completed.'

-- Clean up
DROP FUNCTION IF EXISTS log_validation_result(TEXT, TEXT, BIGINT, BIGINT, TEXT);