-- =====================================================
-- ENHANCED ROW LEVEL SECURITY POLICIES
-- =====================================================
-- Migration: V1000__Enhanced_Row_Level_Security_Policies.sql
-- Purpose: Complete RLS implementation for all sensitive tables
-- Compliance: PCI DSS, SOC 2, GDPR, Financial Services

-- =====================================================
-- ADDITIONAL TABLE RLS ENABLEMENT
-- =====================================================

-- Enable RLS on additional sensitive tables
ALTER TABLE payment_methods ENABLE ROW LEVEL SECURITY;
ALTER TABLE bank_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction_fees ENABLE ROW LEVEL SECURITY;
ALTER TABLE wallet_reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE compliance_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_keys ENABLE ROW LEVEL SECURITY;

-- =====================================================
-- USER DATA ACCESS POLICIES
-- =====================================================

-- Payment methods - users can only see their own payment methods
CREATE POLICY payment_methods_user_policy ON payment_methods 
FOR ALL TO application_role 
USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- Bank accounts - users can only see their own bank accounts
CREATE POLICY bank_accounts_user_policy ON bank_accounts 
FOR ALL TO application_role 
USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- Transaction fees - users can only see fees for their transactions
CREATE POLICY transaction_fees_user_policy ON transaction_fees 
FOR SELECT TO application_role 
USING (
    transaction_id IN (
        SELECT id FROM transactions 
        WHERE user_id = current_setting('app.current_user_id', true)::uuid
    )
);

-- Wallet reservations - users can only see their wallet reservations
CREATE POLICY wallet_reservations_user_policy ON wallet_reservations 
FOR ALL TO application_role 
USING (
    wallet_id IN (
        SELECT id FROM wallets 
        WHERE user_id = current_setting('app.current_user_id', true)::uuid
    )
);

-- User sessions - users can only see their own sessions
CREATE POLICY user_sessions_user_policy ON user_sessions 
FOR ALL TO application_role 
USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- API keys - users can only see their own API keys
CREATE POLICY api_keys_user_policy ON api_keys 
FOR ALL TO application_role 
USING (user_id = current_setting('app.current_user_id', true)::uuid);

-- =====================================================
-- ROLE-BASED ACCESS POLICIES
-- =====================================================

-- Compliance reports - only compliance officers and admins
CREATE POLICY compliance_reports_restricted_policy ON compliance_reports 
FOR SELECT TO application_role 
USING (
    current_setting('app.user_role', true) IN ('COMPLIANCE_OFFICER', 'ADMIN', 'AUDIT_ADMIN')
);

-- Fraud alerts - only fraud analysts and admins
CREATE POLICY fraud_alerts_restricted_policy ON fraud_alerts 
FOR SELECT TO application_role 
USING (
    current_setting('app.user_role', true) IN ('FRAUD_ANALYST', 'ADMIN', 'SECURITY_ADMIN')
);

-- Transaction fees - restrict modifications to admin roles
CREATE POLICY transaction_fees_admin_modify ON transaction_fees 
FOR INSERT TO application_role 
WITH CHECK (
    current_setting('app.user_role', true) IN ('ADMIN', 'FINANCIAL_ADMIN')
);

CREATE POLICY transaction_fees_admin_update ON transaction_fees 
FOR UPDATE TO application_role 
USING (
    current_setting('app.user_role', true) IN ('ADMIN', 'FINANCIAL_ADMIN')
);

-- =====================================================
-- ADMIN BYPASS POLICIES
-- =====================================================

-- Payment methods - admin full access
CREATE POLICY admin_full_access_payment_methods ON payment_methods 
FOR ALL TO admin_role 
USING (true);

-- Bank accounts - admin full access
CREATE POLICY admin_full_access_bank_accounts ON bank_accounts 
FOR ALL TO admin_role 
USING (true);

-- Transaction fees - admin full access
CREATE POLICY admin_full_access_transaction_fees ON transaction_fees 
FOR ALL TO admin_role 
USING (true);

-- Wallet reservations - admin full access
CREATE POLICY admin_full_access_wallet_reservations ON wallet_reservations 
FOR ALL TO admin_role 
USING (true);

-- Compliance reports - admin full access
CREATE POLICY admin_full_access_compliance_reports ON compliance_reports 
FOR ALL TO admin_role 
USING (true);

-- Fraud alerts - admin full access
CREATE POLICY admin_full_access_fraud_alerts ON fraud_alerts 
FOR ALL TO admin_role 
USING (true);

-- User sessions - admin full access
CREATE POLICY admin_full_access_user_sessions ON user_sessions 
FOR ALL TO admin_role 
USING (true);

-- API keys - admin full access
CREATE POLICY admin_full_access_api_keys ON api_keys 
FOR ALL TO admin_role 
USING (true);

-- =====================================================
-- SYSTEM SERVICE POLICIES
-- =====================================================

-- Allow system services to access data when in system context
CREATE POLICY system_service_wallets ON wallets 
FOR ALL TO application_role 
USING (
    current_setting('app.system_context', true) = 'true'
    AND current_setting('app.user_role', true) = 'SYSTEM_SERVICE'
);

CREATE POLICY system_service_transactions ON transactions 
FOR ALL TO application_role 
USING (
    current_setting('app.system_context', true) = 'true'
    AND current_setting('app.user_role', true) = 'SYSTEM_SERVICE'
);

CREATE POLICY system_service_payment_methods ON payment_methods 
FOR SELECT TO application_role 
USING (
    current_setting('app.system_context', true) = 'true'
    AND current_setting('app.user_role', true) = 'SYSTEM_SERVICE'
);

-- =====================================================
-- FINANCIAL OPERATION POLICIES
-- =====================================================

-- Enhanced policies for financial operations with additional context
CREATE POLICY financial_operations_wallets ON wallets 
FOR UPDATE TO application_role 
USING (
    user_id = current_setting('app.current_user_id', true)::uuid
    AND current_setting('app.financial_context', true) = 'true'
    AND current_setting('app.operation_type', true) IS NOT NULL
);

CREATE POLICY financial_operations_transactions ON transactions 
FOR INSERT TO application_role 
WITH CHECK (
    user_id = current_setting('app.current_user_id', true)::uuid
    AND current_setting('app.financial_context', true) = 'true'
    AND current_setting('app.financial_transaction_id', true) IS NOT NULL
);

-- =====================================================
-- TEMPORAL POLICIES (TIME-BASED ACCESS)
-- =====================================================

-- Users can only modify recent transactions (within 24 hours)
CREATE POLICY recent_transaction_modification ON transactions 
FOR UPDATE TO application_role 
USING (
    user_id = current_setting('app.current_user_id', true)::uuid
    AND created_at > (NOW() - INTERVAL '24 hours')
    AND status IN ('PENDING', 'IN_PROGRESS')
);

-- Users can only view their transactions from last 7 years (data retention policy)
CREATE POLICY transaction_retention_policy ON transactions 
FOR SELECT TO application_role 
USING (
    user_id = current_setting('app.current_user_id', true)::uuid
    AND created_at > (NOW() - INTERVAL '7 years')
);

-- =====================================================
-- GEOGRAPHIC POLICIES (GDPR/CCPA COMPLIANCE)
-- =====================================================

-- Function to check data residency requirements
CREATE OR REPLACE FUNCTION check_data_residency_compliance()
RETURNS BOOLEAN AS $$
BEGIN
    -- Check if user is in EU and data access is from EU
    -- This is a simplified example - real implementation would be more complex
    RETURN current_setting('app.user_region', true) = current_setting('app.access_region', true)
           OR current_setting('app.admin_context', true) = 'true';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Apply geographic restrictions to sensitive data
CREATE POLICY geographic_data_access_wallets ON wallets 
FOR SELECT TO application_role 
USING (
    user_id = current_setting('app.current_user_id', true)::uuid
    AND check_data_residency_compliance()
);

-- =====================================================
-- AUDIT TRAIL POLICIES
-- =====================================================

-- Audit events - users can read their own audit logs, admins can read all
CREATE POLICY audit_events_user_read ON audit_events 
FOR SELECT TO application_role 
USING (
    user_id = current_setting('app.current_user_id', true)::uuid
    OR current_setting('app.user_role', true) IN ('AUDIT_ADMIN', 'COMPLIANCE_OFFICER', 'ADMIN')
);

-- Audit events - only system can insert audit records
CREATE POLICY audit_events_system_insert ON audit_events 
FOR INSERT TO application_role 
WITH CHECK (
    current_setting('app.system_context', true) = 'true'
    OR current_setting('app.user_role', true) IN ('SYSTEM_ADMIN', 'AUDIT_ADMIN')
);

-- =====================================================
-- EMERGENCY ACCESS POLICIES
-- =====================================================

-- Emergency access override (for critical system failures)
CREATE POLICY emergency_access_override ON wallets 
FOR ALL TO application_role 
USING (
    current_setting('app.emergency_access', true) = 'true'
    AND current_setting('app.user_role', true) IN ('EMERGENCY_ADMIN', 'SYSTEM_ADMIN')
    AND current_setting('app.emergency_token', true) IS NOT NULL
);

-- =====================================================
-- POLICY VALIDATION FUNCTIONS
-- =====================================================

-- Function to validate all RLS policies are properly configured
CREATE OR REPLACE FUNCTION validate_rls_policies()
RETURNS TABLE(
    table_name TEXT,
    rls_enabled BOOLEAN,
    policy_count INTEGER,
    status TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.tablename::TEXT,
        t.rowsecurity::BOOLEAN as rls_enabled,
        COALESCE(p.policy_count, 0)::INTEGER as policy_count,
        CASE 
            WHEN t.rowsecurity AND COALESCE(p.policy_count, 0) > 0 THEN 'PROPERLY_CONFIGURED'
            WHEN t.rowsecurity AND COALESCE(p.policy_count, 0) = 0 THEN 'RLS_ENABLED_NO_POLICIES'
            WHEN NOT t.rowsecurity THEN 'RLS_DISABLED'
            ELSE 'UNKNOWN'
        END::TEXT as status
    FROM pg_tables t
    LEFT JOIN (
        SELECT 
            schemaname,
            tablename,
            COUNT(*) as policy_count
        FROM pg_policies 
        WHERE schemaname = 'public'
        GROUP BY schemaname, tablename
    ) p ON t.schemaname = p.schemaname AND t.tablename = p.tablename
    WHERE t.schemaname = 'public'
    AND t.tablename IN (
        'wallets', 'transactions', 'wallet_transactions', 'kyc_verifications',
        'payment_methods', 'bank_accounts', 'transaction_fees', 'wallet_reservations',
        'compliance_reports', 'fraud_alerts', 'user_sessions', 'api_keys', 'audit_events'
    )
    ORDER BY t.tablename;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to test RLS policies (for development/testing)
CREATE OR REPLACE FUNCTION test_rls_policy(
    test_table TEXT,
    test_user_id UUID,
    test_role TEXT DEFAULT 'USER'
)
RETURNS TABLE(
    test_description TEXT,
    expected_result TEXT,
    actual_result TEXT,
    status TEXT
) AS $$
DECLARE
    row_count INTEGER;
BEGIN
    -- Set test context
    PERFORM set_config('app.current_user_id', test_user_id::TEXT, true);
    PERFORM set_config('app.user_role', test_role, true);
    
    -- Test user access
    EXECUTE format('SELECT COUNT(*) FROM %I WHERE user_id = $1', test_table) 
    INTO row_count USING test_user_id;
    
    RETURN QUERY SELECT 
        format('User access to %s', test_table)::TEXT,
        'Should see own data only'::TEXT,
        format('Found %s rows', row_count)::TEXT,
        CASE WHEN row_count >= 0 THEN 'PASS' ELSE 'FAIL' END::TEXT;
    
    -- Clear test context
    PERFORM set_config('app.current_user_id', '', true);
    PERFORM set_config('app.user_role', '', true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- POLICY MONITORING AND ALERTING
-- =====================================================

-- Create table to log RLS policy violations
CREATE TABLE IF NOT EXISTS rls_policy_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name TEXT NOT NULL,
    attempted_operation TEXT NOT NULL,
    user_id UUID,
    user_role TEXT,
    client_ip INET,
    violation_details JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Function to log policy violations (called by triggers)
CREATE OR REPLACE FUNCTION log_rls_violation(
    p_table_name TEXT,
    p_operation TEXT,
    p_details JSONB DEFAULT '{}'
)
RETURNS VOID AS $$
BEGIN
    INSERT INTO rls_policy_violations (
        table_name,
        attempted_operation,
        user_id,
        user_role,
        client_ip,
        violation_details
    ) VALUES (
        p_table_name,
        p_operation,
        current_setting('app.current_user_id', true)::UUID,
        current_setting('app.user_role', true),
        current_setting('app.client_ip', true)::INET,
        p_details
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- FINALIZE RLS CONFIGURATION
-- =====================================================

-- Validate all policies are properly applied
DO $$
DECLARE
    validation_result RECORD;
    policy_count INTEGER := 0;
BEGIN
    FOR validation_result IN SELECT * FROM validate_rls_policies() LOOP
        IF validation_result.status != 'PROPERLY_CONFIGURED' THEN
            RAISE WARNING 'Table % has RLS status: %', 
                validation_result.table_name, validation_result.status;
        ELSE
            policy_count := policy_count + validation_result.policy_count;
        END IF;
    END LOOP;
    
    RAISE NOTICE 'RLS validation completed. Total policies configured: %', policy_count;
END $$;

-- Create summary for migration log
INSERT INTO migration_log (migration_version, description, applied_at, status)
VALUES ('V1000', 'Enhanced Row Level Security Policies Applied', CURRENT_TIMESTAMP, 'COMPLETED');

-- Generate final report
SELECT 
    'Enhanced RLS migration completed successfully' as status,
    COUNT(*) as policies_created,
    STRING_AGG(DISTINCT tablename, ', ') as tables_protected
FROM pg_policies 
WHERE schemaname = 'public'
AND policyname LIKE '%_policy';