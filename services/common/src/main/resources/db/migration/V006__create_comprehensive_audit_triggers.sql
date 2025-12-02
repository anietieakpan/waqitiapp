-- Comprehensive Audit Triggers for Financial Compliance
-- This migration creates audit triggers for all critical tables
-- ensuring complete audit trail for regulatory compliance (SOX, PCI-DSS, GDPR)

-- Create audit log table if not exists
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    operation VARCHAR(10) NOT NULL, -- INSERT, UPDATE, DELETE
    row_id VARCHAR(100) NOT NULL,
    old_values JSONB,
    new_values JSONB,
    changed_fields TEXT[],
    user_id UUID,
    session_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    application_name VARCHAR(100),
    transaction_id VARCHAR(255),
    compliance_category VARCHAR(50),
    sensitivity_level VARCHAR(20), -- LOW, MEDIUM, HIGH, CRITICAL
    retention_years INTEGER DEFAULT 7,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    audit_context JSONB
);

-- Create partition key for audit log table (monthly partitions)
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at_month ON audit_log USING RANGE (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_table_operation ON audit_log (table_name, operation);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_session ON audit_log (user_id, session_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_compliance ON audit_log (compliance_category, sensitivity_level);
CREATE INDEX IF NOT EXISTS idx_audit_log_row_id ON audit_log (table_name, row_id);

-- Enable row level security for audit log
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

-- Create security policy for audit log access
CREATE POLICY audit_log_read_policy ON audit_log
    FOR SELECT
    USING (
        -- Only allow audit admins and compliance officers to read audit logs
        current_setting('app.user_role', true) IN ('AUDIT_ADMIN', 'COMPLIANCE_OFFICER', 'SYSTEM_ADMIN')
        OR
        -- Users can read their own audit logs for transparency
        user_id = current_setting('app.current_user_id', true)::UUID
    );

-- Generic audit trigger function
CREATE OR REPLACE FUNCTION create_audit_record()
RETURNS TRIGGER AS $$
DECLARE
    old_data JSONB := NULL;
    new_data JSONB := NULL;
    changed_fields TEXT[] := ARRAY[]::TEXT[];
    field_name TEXT;
    user_id_val UUID := NULL;
    session_id_val VARCHAR(255) := NULL;
    ip_address_val INET := NULL;
    user_agent_val TEXT := NULL;
    app_name_val VARCHAR(100) := 'waqiti-platform';
    transaction_id_val VARCHAR(255) := NULL;
    compliance_cat VARCHAR(50) := 'GENERAL';
    sensitivity VARCHAR(20) := 'MEDIUM';
    retention_years_val INTEGER := 7;
    audit_context_val JSONB := '{}';
BEGIN
    -- Extract audit context from session variables
    BEGIN
        user_id_val := current_setting('app.current_user_id', true)::UUID;
    EXCEPTION WHEN OTHERS THEN
        user_id_val := NULL;
    END;
    
    BEGIN
        session_id_val := current_setting('app.session_id', true);
    EXCEPTION WHEN OTHERS THEN
        session_id_val := NULL;
    END;
    
    BEGIN
        ip_address_val := current_setting('app.client_ip', true)::INET;
    EXCEPTION WHEN OTHERS THEN
        ip_address_val := NULL;
    END;
    
    BEGIN
        user_agent_val := current_setting('app.user_agent', true);
    EXCEPTION WHEN OTHERS THEN
        user_agent_val := NULL;
    END;
    
    BEGIN
        transaction_id_val := current_setting('app.transaction_id', true);
    EXCEPTION WHEN OTHERS THEN
        transaction_id_val := NULL;
    END;

    -- Determine compliance category and sensitivity based on table
    CASE TG_TABLE_NAME
        WHEN 'accounts', 'wallet_accounts', 'crypto_wallets' THEN
            compliance_cat := 'ACCOUNT_MANAGEMENT';
            sensitivity := 'HIGH';
            retention_years_val := 10;
        WHEN 'transactions', 'payments', 'crypto_transactions', 'ach_transfers' THEN
            compliance_cat := 'FINANCIAL_TRANSACTION';
            sensitivity := 'CRITICAL';
            retention_years_val := 10;
        WHEN 'user_profiles', 'user_accounts', 'kyc_data' THEN
            compliance_cat := 'CUSTOMER_DATA';
            sensitivity := 'HIGH';
            retention_years_val := 7;
        WHEN 'compliance_checks', 'fraud_alerts', 'sanctions_screening' THEN
            compliance_cat := 'COMPLIANCE_MONITORING';
            sensitivity := 'CRITICAL';
            retention_years_val := 15;
        WHEN 'api_keys', 'authentication_tokens', 'security_keys' THEN
            compliance_cat := 'SECURITY_CREDENTIAL';
            sensitivity := 'CRITICAL';
            retention_years_val := 5;
        WHEN 'business_accounts', 'merchant_accounts' THEN
            compliance_cat := 'BUSINESS_ACCOUNT';
            sensitivity := 'HIGH';
            retention_years_val := 10;
        ELSE
            compliance_cat := 'GENERAL';
            sensitivity := 'MEDIUM';
            retention_years_val := 7;
    END CASE;

    -- Handle different operations
    IF TG_OP = 'DELETE' THEN
        old_data := row_to_json(OLD)::JSONB;
        
        INSERT INTO audit_log (
            table_name, operation, row_id, old_values, changed_fields,
            user_id, session_id, ip_address, user_agent, application_name,
            transaction_id, compliance_category, sensitivity_level,
            retention_years, created_by, audit_context
        ) VALUES (
            TG_TABLE_NAME, TG_OP, COALESCE(OLD.id::TEXT, OLD.user_id::TEXT, 'unknown'),
            old_data, ARRAY['*'], user_id_val, session_id_val, ip_address_val,
            user_agent_val, app_name_val, transaction_id_val, compliance_cat,
            sensitivity, retention_years_val, current_user, audit_context_val
        );
        
        RETURN OLD;
        
    ELSIF TG_OP = 'UPDATE' THEN
        old_data := row_to_json(OLD)::JSONB;
        new_data := row_to_json(NEW)::JSONB;
        
        -- Identify changed fields
        FOR field_name IN SELECT * FROM jsonb_object_keys(old_data) LOOP
            IF old_data->field_name IS DISTINCT FROM new_data->field_name THEN
                changed_fields := array_append(changed_fields, field_name);
            END IF;
        END LOOP;
        
        -- Only log if there are actual changes
        IF array_length(changed_fields, 1) > 0 THEN
            INSERT INTO audit_log (
                table_name, operation, row_id, old_values, new_values, changed_fields,
                user_id, session_id, ip_address, user_agent, application_name,
                transaction_id, compliance_category, sensitivity_level,
                retention_years, created_by, audit_context
            ) VALUES (
                TG_TABLE_NAME, TG_OP, COALESCE(NEW.id::TEXT, NEW.user_id::TEXT, 'unknown'),
                old_data, new_data, changed_fields, user_id_val, session_id_val,
                ip_address_val, user_agent_val, app_name_val, transaction_id_val,
                compliance_cat, sensitivity, retention_years_val, current_user, audit_context_val
            );
        END IF;
        
        RETURN NEW;
        
    ELSIF TG_OP = 'INSERT' THEN
        new_data := row_to_json(NEW)::JSONB;
        
        INSERT INTO audit_log (
            table_name, operation, row_id, new_values, changed_fields,
            user_id, session_id, ip_address, user_agent, application_name,
            transaction_id, compliance_category, sensitivity_level,
            retention_years, created_by, audit_context
        ) VALUES (
            TG_TABLE_NAME, TG_OP, COALESCE(NEW.id::TEXT, NEW.user_id::TEXT, 'unknown'),
            new_data, ARRAY['*'], user_id_val, session_id_val, ip_address_val,
            user_agent_val, app_name_val, transaction_id_val, compliance_cat,
            sensitivity, retention_years_val, current_user, audit_context_val
        );
        
        RETURN NEW;
    END IF;
    
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create audit triggers for all critical tables

-- User and Account Management
DROP TRIGGER IF EXISTS audit_user_accounts ON user_accounts CASCADE;
CREATE TRIGGER audit_user_accounts
    AFTER INSERT OR UPDATE OR DELETE ON user_accounts
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_user_profiles ON user_profiles CASCADE;
CREATE TRIGGER audit_user_profiles
    AFTER INSERT OR UPDATE OR DELETE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_accounts ON accounts CASCADE;
CREATE TRIGGER audit_accounts
    AFTER INSERT OR UPDATE OR DELETE ON accounts
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- Financial Transactions
DROP TRIGGER IF EXISTS audit_transactions ON transactions CASCADE;
CREATE TRIGGER audit_transactions
    AFTER INSERT OR UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_payments ON payments CASCADE;
CREATE TRIGGER audit_payments
    AFTER INSERT OR UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_ach_transfers ON ach_transfers CASCADE;
CREATE TRIGGER audit_ach_transfers
    AFTER INSERT OR UPDATE OR DELETE ON ach_transfers
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- Cryptocurrency
DROP TRIGGER IF EXISTS audit_crypto_transactions ON crypto_transactions CASCADE;
CREATE TRIGGER audit_crypto_transactions
    AFTER INSERT OR UPDATE OR DELETE ON crypto_transactions
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_crypto_wallets ON crypto_wallets CASCADE;
CREATE TRIGGER audit_crypto_wallets
    AFTER INSERT OR UPDATE OR DELETE ON crypto_wallets
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- Compliance and Security
DROP TRIGGER IF EXISTS audit_compliance_checks ON compliance_checks CASCADE;
CREATE TRIGGER audit_compliance_checks
    AFTER INSERT OR UPDATE OR DELETE ON compliance_checks
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_kyc_data ON kyc_data CASCADE;
CREATE TRIGGER audit_kyc_data
    AFTER INSERT OR UPDATE OR DELETE ON kyc_data
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_fraud_alerts ON fraud_alerts CASCADE;
CREATE TRIGGER audit_fraud_alerts
    AFTER INSERT OR UPDATE OR DELETE ON fraud_alerts
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- Authentication and Security
DROP TRIGGER IF EXISTS audit_authentication_tokens ON authentication_tokens CASCADE;
CREATE TRIGGER audit_authentication_tokens
    AFTER INSERT OR UPDATE OR DELETE ON authentication_tokens
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_api_keys ON api_keys CASCADE;
CREATE TRIGGER audit_api_keys
    AFTER INSERT OR UPDATE OR DELETE ON api_keys
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- Business Accounts
DROP TRIGGER IF EXISTS audit_business_accounts ON business_accounts CASCADE;
CREATE TRIGGER audit_business_accounts
    AFTER INSERT OR UPDATE OR DELETE ON business_accounts
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_merchant_accounts ON merchant_accounts CASCADE;
CREATE TRIGGER audit_merchant_accounts
    AFTER INSERT OR UPDATE OR DELETE ON merchant_accounts
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- Investment and Savings
DROP TRIGGER IF EXISTS audit_investment_accounts ON investment_accounts CASCADE;
CREATE TRIGGER audit_investment_accounts
    AFTER INSERT OR UPDATE OR DELETE ON investment_accounts
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

DROP TRIGGER IF EXISTS audit_savings_accounts ON savings_accounts CASCADE;
CREATE TRIGGER audit_savings_accounts
    AFTER INSERT OR UPDATE OR DELETE ON savings_accounts
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- International Transfers
DROP TRIGGER IF EXISTS audit_international_transfers ON international_transfers CASCADE;
CREATE TRIGGER audit_international_transfers
    AFTER INSERT OR UPDATE OR DELETE ON international_transfers
    FOR EACH ROW EXECUTE FUNCTION create_audit_record();

-- Utility functions for audit management

-- Function to query audit history for a specific record
CREATE OR REPLACE FUNCTION get_audit_history(
    p_table_name TEXT,
    p_row_id TEXT,
    p_start_date TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    p_end_date TIMESTAMP WITH TIME ZONE DEFAULT NULL
)
RETURNS TABLE (
    audit_id BIGINT,
    operation VARCHAR(10),
    old_values JSONB,
    new_values JSONB,
    changed_fields TEXT[],
    user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE,
    compliance_category VARCHAR(50)
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        al.id,
        al.operation,
        al.old_values,
        al.new_values,
        al.changed_fields,
        al.user_id,
        al.created_at,
        al.compliance_category
    FROM audit_log al
    WHERE al.table_name = p_table_name
      AND al.row_id = p_row_id
      AND (p_start_date IS NULL OR al.created_at >= p_start_date)
      AND (p_end_date IS NULL OR al.created_at <= p_end_date)
    ORDER BY al.created_at DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to generate compliance reports
CREATE OR REPLACE FUNCTION generate_compliance_report(
    p_compliance_category VARCHAR(50),
    p_start_date TIMESTAMP WITH TIME ZONE,
    p_end_date TIMESTAMP WITH TIME ZONE
)
RETURNS TABLE (
    table_name VARCHAR(100),
    operation_count BIGINT,
    unique_users BIGINT,
    critical_changes BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        al.table_name,
        COUNT(*) as operation_count,
        COUNT(DISTINCT al.user_id) as unique_users,
        SUM(CASE WHEN al.sensitivity_level = 'CRITICAL' THEN 1 ELSE 0 END) as critical_changes
    FROM audit_log al
    WHERE al.compliance_category = p_compliance_category
      AND al.created_at BETWEEN p_start_date AND p_end_date
    GROUP BY al.table_name
    ORDER BY operation_count DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to detect suspicious audit patterns
CREATE OR REPLACE FUNCTION detect_suspicious_audit_patterns(
    p_hours_back INTEGER DEFAULT 24
)
RETURNS TABLE (
    user_id UUID,
    suspicious_activity TEXT,
    operation_count BIGINT,
    tables_affected TEXT[],
    risk_score INTEGER
) AS $$
BEGIN
    RETURN QUERY
    WITH suspicious_activities AS (
        -- High frequency operations
        SELECT 
            al.user_id,
            'HIGH_FREQUENCY_OPERATIONS' as activity_type,
            COUNT(*) as op_count,
            array_agg(DISTINCT al.table_name) as tables,
            CASE 
                WHEN COUNT(*) > 1000 THEN 90
                WHEN COUNT(*) > 500 THEN 70
                WHEN COUNT(*) > 100 THEN 50
                ELSE 20
            END as score
        FROM audit_log al
        WHERE al.created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour' * p_hours_back
          AND al.user_id IS NOT NULL
        GROUP BY al.user_id
        HAVING COUNT(*) > 50
        
        UNION ALL
        
        -- Cross-table modifications
        SELECT 
            al.user_id,
            'CROSS_TABLE_MODIFICATIONS' as activity_type,
            COUNT(*) as op_count,
            array_agg(DISTINCT al.table_name) as tables,
            CASE 
                WHEN COUNT(DISTINCT al.table_name) > 10 THEN 80
                WHEN COUNT(DISTINCT al.table_name) > 5 THEN 60
                ELSE 30
            END as score
        FROM audit_log al
        WHERE al.created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour' * p_hours_back
          AND al.user_id IS NOT NULL
          AND al.operation IN ('UPDATE', 'DELETE')
        GROUP BY al.user_id
        HAVING COUNT(DISTINCT al.table_name) > 3
        
        UNION ALL
        
        -- Critical data deletions
        SELECT 
            al.user_id,
            'CRITICAL_DATA_DELETIONS' as activity_type,
            COUNT(*) as op_count,
            array_agg(DISTINCT al.table_name) as tables,
            95 as score
        FROM audit_log al
        WHERE al.created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour' * p_hours_back
          AND al.user_id IS NOT NULL
          AND al.operation = 'DELETE'
          AND al.sensitivity_level = 'CRITICAL'
        GROUP BY al.user_id
        HAVING COUNT(*) > 0
    )
    SELECT 
        sa.user_id,
        sa.activity_type,
        sa.op_count,
        sa.tables,
        sa.score
    FROM suspicious_activities sa
    ORDER BY sa.score DESC, sa.op_count DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create materialized view for audit analytics
CREATE MATERIALIZED VIEW audit_analytics_daily AS
SELECT 
    DATE(created_at) as audit_date,
    table_name,
    operation,
    compliance_category,
    sensitivity_level,
    COUNT(*) as operation_count,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT session_id) as unique_sessions
FROM audit_log
WHERE created_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE(created_at), table_name, operation, compliance_category, sensitivity_level;

-- Create unique index for materialized view
CREATE UNIQUE INDEX idx_audit_analytics_daily_unique 
ON audit_analytics_daily (audit_date, table_name, operation, compliance_category, sensitivity_level);

-- Schedule refresh of materialized view (requires pg_cron extension)
-- SELECT cron.schedule('refresh-audit-analytics', '0 1 * * *', 'REFRESH MATERIALIZED VIEW CONCURRENTLY audit_analytics_daily;');

-- Grant appropriate permissions
GRANT SELECT ON audit_log TO audit_reader;
GRANT EXECUTE ON FUNCTION get_audit_history(TEXT, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE) TO audit_reader;
GRANT EXECUTE ON FUNCTION generate_compliance_report(VARCHAR(50), TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE) TO compliance_officer;
GRANT EXECUTE ON FUNCTION detect_suspicious_audit_patterns(INTEGER) TO security_admin;
GRANT SELECT ON audit_analytics_daily TO reporting_user;

-- Add comments for documentation
COMMENT ON TABLE audit_log IS 'Comprehensive audit trail for all critical table operations';
COMMENT ON FUNCTION create_audit_record() IS 'Generic trigger function for capturing audit information';
COMMENT ON FUNCTION get_audit_history(TEXT, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE) IS 'Retrieves complete audit history for a specific record';
COMMENT ON FUNCTION generate_compliance_report(VARCHAR(50), TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE) IS 'Generates compliance reports for regulatory requirements';
COMMENT ON FUNCTION detect_suspicious_audit_patterns(INTEGER) IS 'Detects suspicious patterns in audit logs for security monitoring';
COMMENT ON MATERIALIZED VIEW audit_analytics_daily IS 'Daily aggregated audit statistics for reporting and analytics';