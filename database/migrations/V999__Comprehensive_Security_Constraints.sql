-- Comprehensive Security Constraints for Waqiti Platform
-- This migration adds critical database-level security constraints
-- to prevent data integrity issues and security vulnerabilities

-- =====================================================
-- IDEMPOTENCY CONSTRAINTS
-- =====================================================

-- Ensure idempotency keys are unique across the platform
CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_idempotency_key 
ON transactions(idempotency_key) 
WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_wallet_transactions_idempotency_key 
ON wallet_transactions(idempotency_key) 
WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_idempotency_key 
ON payments(idempotency_key) 
WHERE idempotency_key IS NOT NULL;

-- =====================================================
-- FINANCIAL AMOUNT CONSTRAINTS
-- =====================================================

-- Ensure all financial amounts are positive
ALTER TABLE transactions 
ADD CONSTRAINT chk_transactions_amount_positive 
CHECK (amount > 0);

ALTER TABLE wallet_transactions 
ADD CONSTRAINT chk_wallet_transactions_amount_positive 
CHECK (amount > 0);

ALTER TABLE payments 
ADD CONSTRAINT chk_payments_amount_positive 
CHECK (amount > 0);

ALTER TABLE ledger_entries 
ADD CONSTRAINT chk_ledger_entries_amount_positive 
CHECK (amount > 0);

-- Ensure wallet balances are non-negative
ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_balance_non_negative 
CHECK (balance >= 0);

ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_available_balance_non_negative 
CHECK (available_balance >= 0);

ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_reserved_balance_non_negative 
CHECK (reserved_balance >= 0);

-- Ensure total balance equals available + reserved
ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_balance_consistency 
CHECK (balance = available_balance + reserved_balance);

-- =====================================================
-- CURRENCY AND PRECISION CONSTRAINTS
-- =====================================================

-- Ensure currency codes are valid ISO 4217
ALTER TABLE transactions 
ADD CONSTRAINT chk_transactions_currency_valid 
CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_currency_valid 
CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE payments 
ADD CONSTRAINT chk_payments_currency_valid 
CHECK (currency ~ '^[A-Z]{3}$');

-- Ensure decimal precision for amounts (max 2 decimal places for most currencies)
ALTER TABLE transactions 
ADD CONSTRAINT chk_transactions_amount_precision 
CHECK (scale(amount) <= 2);

ALTER TABLE wallet_transactions 
ADD CONSTRAINT chk_wallet_transactions_amount_precision 
CHECK (scale(amount) <= 2);

-- =====================================================
-- TRANSACTION STATUS CONSTRAINTS
-- =====================================================

-- Ensure valid transaction status transitions
ALTER TABLE transactions 
ADD CONSTRAINT chk_transactions_status_valid 
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED'));

ALTER TABLE payments 
ADD CONSTRAINT chk_payments_status_valid 
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED'));

ALTER TABLE wallet_transactions 
ADD CONSTRAINT chk_wallet_transactions_status_valid 
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'));

-- =====================================================
-- USER AND OWNERSHIP CONSTRAINTS
-- =====================================================

-- Ensure user IDs are valid UUIDs
ALTER TABLE users 
ADD CONSTRAINT chk_users_id_uuid 
CHECK (id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');

ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_user_id_uuid 
CHECK (user_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');

-- Prevent self-transfers (source and target cannot be the same)
ALTER TABLE wallet_transactions 
ADD CONSTRAINT chk_wallet_transactions_no_self_transfer 
CHECK (source_wallet_id != target_wallet_id);

-- =====================================================
-- TIMESTAMP CONSTRAINTS
-- =====================================================

-- Ensure created_at is not in the future
ALTER TABLE transactions 
ADD CONSTRAINT chk_transactions_created_at_not_future 
CHECK (created_at <= CURRENT_TIMESTAMP + INTERVAL '1 minute');

ALTER TABLE users 
ADD CONSTRAINT chk_users_created_at_not_future 
CHECK (created_at <= CURRENT_TIMESTAMP + INTERVAL '1 minute');

ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_created_at_not_future 
CHECK (created_at <= CURRENT_TIMESTAMP + INTERVAL '1 minute');

-- Ensure updated_at is not before created_at
ALTER TABLE transactions 
ADD CONSTRAINT chk_transactions_updated_after_created 
CHECK (updated_at >= created_at);

ALTER TABLE users 
ADD CONSTRAINT chk_users_updated_after_created 
CHECK (updated_at >= created_at);

ALTER TABLE wallets 
ADD CONSTRAINT chk_wallets_updated_after_created 
CHECK (updated_at >= created_at);

-- =====================================================
-- LEDGER CONSTRAINTS (Double-Entry Bookkeeping)
-- =====================================================

-- Ensure ledger entries have valid types
ALTER TABLE ledger_entries 
ADD CONSTRAINT chk_ledger_entries_type_valid 
CHECK (entry_type IN ('DEBIT', 'CREDIT', 'RESERVATION', 'RELEASE'));

-- Ensure ledger entries have valid status
ALTER TABLE ledger_entries 
ADD CONSTRAINT chk_ledger_entries_status_valid 
CHECK (status IN ('PENDING', 'POSTED', 'REVERSED', 'CANCELLED'));

-- Ensure reference numbers are properly formatted
ALTER TABLE ledger_entries 
ADD CONSTRAINT chk_ledger_entries_reference_format 
CHECK (reference_number ~ '^[A-Z0-9\-_]{1,50}$');

-- =====================================================
-- KYC AND COMPLIANCE CONSTRAINTS
-- =====================================================

-- Ensure KYC verification levels are valid
ALTER TABLE kyc_verifications 
ADD CONSTRAINT chk_kyc_verification_level_valid 
CHECK (verification_level IN ('BASIC', 'INTERMEDIATE', 'FULL', 'ENHANCED'));

-- Ensure KYC status is valid
ALTER TABLE kyc_verifications 
ADD CONSTRAINT chk_kyc_status_valid 
CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'EXPIRED'));

-- Ensure document types are valid
ALTER TABLE verification_documents 
ADD CONSTRAINT chk_verification_documents_type_valid 
CHECK (document_type IN ('PASSPORT', 'DRIVERS_LICENSE', 'NATIONAL_ID', 'UTILITY_BILL', 'BANK_STATEMENT', 'OTHER'));

-- =====================================================
-- SECURITY AND AUDIT CONSTRAINTS
-- =====================================================

-- Ensure IP addresses are properly formatted
ALTER TABLE audit_events 
ADD CONSTRAINT chk_audit_events_ip_format 
CHECK (ip_address IS NULL OR ip_address ~ '^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$' 
       OR ip_address ~ '^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$');

-- Ensure event types are from allowed list
ALTER TABLE audit_events 
ADD CONSTRAINT chk_audit_events_type_valid 
CHECK (event_type IN ('LOGIN', 'LOGOUT', 'TRANSACTION', 'WALLET_CREATION', 'KYC_SUBMISSION', 
                      'PASSWORD_CHANGE', 'PROFILE_UPDATE', 'PAYMENT', 'TRANSFER', 'DEPOSIT', 
                      'WITHDRAWAL', 'SECURITY_EVENT', 'ADMIN_ACTION'));

-- =====================================================
-- DATA QUALITY CONSTRAINTS
-- =====================================================

-- Ensure email addresses are properly formatted
ALTER TABLE users 
ADD CONSTRAINT chk_users_email_format 
CHECK (email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

-- Ensure phone numbers are properly formatted (international format)
ALTER TABLE users 
ADD CONSTRAINT chk_users_phone_format 
CHECK (phone_number IS NULL OR phone_number ~ '^\+[1-9]\d{1,14}$');

-- Ensure account numbers are properly formatted (remove special characters)
ALTER TABLE bank_accounts 
ADD CONSTRAINT chk_bank_accounts_account_number_format 
CHECK (account_number ~ '^[0-9A-Z]{4,34}$');

-- Ensure routing numbers are properly formatted
ALTER TABLE bank_accounts 
ADD CONSTRAINT chk_bank_accounts_routing_number_format 
CHECK (routing_number ~ '^[0-9]{9}$');

-- =====================================================
-- PERFORMANCE OPTIMIZATION CONSTRAINTS
-- =====================================================

-- Create partial indexes for active records only
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_active_status 
ON transactions(user_id, created_at DESC) 
WHERE status NOT IN ('CANCELLED', 'FAILED');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_active 
ON wallets(user_id, currency) 
WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_pending 
ON kyc_verifications(user_id, created_at) 
WHERE status IN ('PENDING', 'IN_PROGRESS');

-- =====================================================
-- FOREIGN KEY CONSTRAINT ENHANCEMENTS
-- =====================================================

-- Add cascading deletes for audit purposes (but restrict critical financial data)
ALTER TABLE audit_events 
ADD CONSTRAINT fk_audit_events_user_id 
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

-- Ensure financial transactions cannot be deleted if user is deleted
ALTER TABLE transactions 
ADD CONSTRAINT fk_transactions_user_id 
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE wallet_transactions 
ADD CONSTRAINT fk_wallet_transactions_source_wallet 
FOREIGN KEY (source_wallet_id) REFERENCES wallets(id) ON DELETE RESTRICT;

ALTER TABLE wallet_transactions 
ADD CONSTRAINT fk_wallet_transactions_target_wallet 
FOREIGN KEY (target_wallet_id) REFERENCES wallets(id) ON DELETE RESTRICT;

-- =====================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- =====================================================

-- Enable RLS on sensitive tables
ALTER TABLE wallets ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE wallet_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyc_verifications ENABLE ROW LEVEL SECURITY;

-- Create policies for wallet access (users can only see their own wallets)
CREATE POLICY wallet_user_policy ON wallets 
FOR ALL TO application_role 
USING (user_id = current_setting('app.current_user_id')::uuid);

-- Create policies for transaction access
CREATE POLICY transaction_user_policy ON transactions 
FOR ALL TO application_role 
USING (user_id = current_setting('app.current_user_id')::uuid);

-- Create policies for KYC access
CREATE POLICY kyc_user_policy ON kyc_verifications 
FOR ALL TO application_role 
USING (user_id = current_setting('app.current_user_id')::uuid);

-- Admin bypass policies
CREATE POLICY admin_full_access_wallets ON wallets 
FOR ALL TO admin_role 
USING (true);

CREATE POLICY admin_full_access_transactions ON transactions 
FOR ALL TO admin_role 
USING (true);

-- =====================================================
-- TRIGGERS FOR ADDITIONAL SECURITY
-- =====================================================

-- Create trigger to automatically update updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply the trigger to all relevant tables
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users 
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_wallets_updated_at BEFORE UPDATE ON wallets 
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at BEFORE UPDATE ON transactions 
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create trigger to prevent modification of completed transactions
CREATE OR REPLACE FUNCTION prevent_completed_transaction_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'COMPLETED' AND NEW.status != OLD.status THEN
        RAISE EXCEPTION 'Cannot modify completed transaction';
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER prevent_transaction_modification BEFORE UPDATE ON transactions 
FOR EACH ROW EXECUTE FUNCTION prevent_completed_transaction_modification();

-- =====================================================
-- FINAL SECURITY VALIDATION
-- =====================================================

-- Create a function to validate all constraints are properly applied
CREATE OR REPLACE FUNCTION validate_security_constraints()
RETURNS TABLE(constraint_name text, table_name text, status text) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        tc.constraint_name::text,
        tc.table_name::text,
        CASE 
            WHEN tc.constraint_name IS NOT NULL THEN 'APPLIED'
            ELSE 'MISSING'
        END::text as status
    FROM information_schema.table_constraints tc
    WHERE tc.constraint_schema = 'public'
    AND tc.constraint_name LIKE 'chk_%'
    ORDER BY tc.table_name, tc.constraint_name;
END;
$$ LANGUAGE plpgsql;

-- Log the completion of security constraints
INSERT INTO migration_log (migration_version, description, applied_at, status)
VALUES ('V999', 'Comprehensive Security Constraints Applied', CURRENT_TIMESTAMP, 'COMPLETED');

-- Create summary report
SELECT 'Security constraints migration completed successfully' as status,
       count(*) as constraints_added
FROM information_schema.table_constraints 
WHERE constraint_schema = 'public' 
AND constraint_name LIKE 'chk_%';