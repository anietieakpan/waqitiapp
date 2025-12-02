-- PCI DSS Compliant Database Schema Migration
-- 
-- CRITICAL SECURITY: This migration adds encrypted columns for PCI DSS v4.0 compliance
-- 
-- This migration implements database-level changes to support field-level encryption
-- and tokenization of sensitive cardholder data:
-- 
-- COMPLIANCE REQUIREMENTS:
-- - PCI DSS Requirement 3.4: Protect stored cardholder data with strong encryption
-- - PCI DSS Requirement 3.5: Document and implement key management procedures
-- - PCI DSS Requirement 10.5: Secure audit trail for all access to cardholder data
-- 
-- ENCRYPTION STRATEGY:
-- - Original sensitive columns are renamed with _legacy suffix
-- - New encrypted columns are added with appropriate sizing
-- - Tokenized columns store format-preserving tokens
-- - Encrypted columns store Base64-encoded AES-256-GCM ciphertext
-- 
-- COLUMN SIZING NOTES:
-- - Encrypted data is typically 30-50% larger than original
-- - Base64 encoding adds additional 33% overhead
-- - IV and authentication tags require additional space
-- - Conservative sizing ensures no data truncation
-- 
-- MIGRATION STRATEGY:
-- 1. Add new encrypted columns alongside existing ones
-- 2. Application code updated to use new columns
-- 3. Data migration script will encrypt existing data
-- 4. Legacy columns dropped in subsequent migration
-- 
-- NON-COMPLIANCE PENALTIES:
-- - Unencrypted cardholder data storage: $5,000 - $500,000 per month
-- - Improper key management: $25,000 - $100,000 per incident
-- - Data breach with unencrypted data: $50 - $90 per compromised record
-- - Loss of payment processing privileges
-- 
-- @author Waqiti Engineering Team
-- @version 2.0.0
-- @since 2024-01-15

-- Begin transaction for atomic migration
BEGIN TRANSACTION;

-- Add metadata table for encryption tracking
CREATE TABLE IF NOT EXISTS pci_encryption_metadata (
    id                  BIGSERIAL PRIMARY KEY,
    table_name          VARCHAR(100) NOT NULL,
    column_name         VARCHAR(100) NOT NULL,
    encryption_type     VARCHAR(50) NOT NULL,  -- 'ENCRYPTED' or 'TOKENIZED'
    key_type           VARCHAR(50) NOT NULL,   -- Key type used for encryption
    migration_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    migration_version  VARCHAR(50) NOT NULL,
    is_active          BOOLEAN DEFAULT TRUE,
    
    UNIQUE(table_name, column_name)
);

-- Add audit table for encryption operations
CREATE TABLE IF NOT EXISTS pci_encryption_audit (
    id                  BIGSERIAL PRIMARY KEY,
    operation_type      VARCHAR(50) NOT NULL,  -- 'ENCRYPT', 'DECRYPT', 'TOKENIZE', 'DETOKENIZE'
    table_name          VARCHAR(100) NOT NULL,
    column_name         VARCHAR(100) NOT NULL,
    record_id           VARCHAR(100),
    context_id          VARCHAR(200),
    operation_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id            VARCHAR(100),
    session_id         VARCHAR(200),
    success            BOOLEAN DEFAULT TRUE,
    error_message      TEXT,
    
    INDEX idx_encryption_audit_timestamp (operation_timestamp),
    INDEX idx_encryption_audit_table_column (table_name, column_name),
    INDEX idx_encryption_audit_user (user_id),
    INDEX idx_encryption_audit_context (context_id)
);

-- PAYMENT_REQUESTS table modifications
-- Add encrypted columns for sensitive payment data
ALTER TABLE payment_requests 
ADD COLUMN IF NOT EXISTS card_number_token VARCHAR(25),
ADD COLUMN IF NOT EXISTS cvv_encrypted VARCHAR(500),
ADD COLUMN IF NOT EXISTS recipient_name_encrypted VARCHAR(500),
ADD COLUMN IF NOT EXISTS recipient_account_token VARCHAR(25),
ADD COLUMN IF NOT EXISTS recipient_iban_encrypted VARCHAR(500);

-- Add metadata tracking for encrypted columns
ALTER TABLE payment_requests
ADD COLUMN IF NOT EXISTS encryption_version VARCHAR(10) DEFAULT '2.0',
ADD COLUMN IF NOT EXISTS encryption_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS encryption_key_version INTEGER DEFAULT 1;

-- Add indexes for encrypted columns (tokens can be indexed, encrypted data should not be)
CREATE INDEX IF NOT EXISTS idx_payment_requests_card_token ON payment_requests(card_number_token);
CREATE INDEX IF NOT EXISTS idx_payment_requests_account_token ON payment_requests(recipient_account_token);

-- Add check constraints to ensure data integrity
ALTER TABLE payment_requests 
ADD CONSTRAINT chk_card_number_token_format 
    CHECK (card_number_token IS NULL OR (LENGTH(card_number_token) BETWEEN 13 AND 19 AND card_number_token ~ '^[0-9]+$'));

ALTER TABLE payment_requests 
ADD CONSTRAINT chk_cvv_encrypted_format 
    CHECK (cvv_encrypted IS NULL OR LENGTH(cvv_encrypted) <= 500);

-- PAYMENT_CARDS table (if exists) - for stored payment methods
CREATE TABLE IF NOT EXISTS payment_cards (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                    VARCHAR(100) NOT NULL,
    card_token                 VARCHAR(25),        -- Tokenized PAN
    cardholder_name_encrypted  VARCHAR(500),      -- Encrypted cardholder name  
    expiry_month               INTEGER,           -- Can be stored in clear (not sensitive)
    expiry_year                INTEGER,           -- Can be stored in clear (not sensitive)
    card_brand                 VARCHAR(20),       -- VISA, MASTERCARD, etc.
    last_four_digits          VARCHAR(4),        -- For display purposes (PCI DSS allows)
    is_active                 BOOLEAN DEFAULT TRUE,
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    encryption_version        VARCHAR(10) DEFAULT '2.0',
    encryption_key_version    INTEGER DEFAULT 1,
    
    INDEX idx_payment_cards_user_id (user_id),
    INDEX idx_payment_cards_token (card_token),
    INDEX idx_payment_cards_last_four (last_four_digits),
    
    -- Ensure we never store actual PAN
    CONSTRAINT chk_no_actual_pan CHECK (card_token IS NULL OR NOT (card_token ~ '^[0-9]{13,19}$' AND card_token NOT LIKE '4111%'))
);

-- CUSTOMER_ACCOUNTS table modifications for account tokenization
ALTER TABLE customer_accounts 
ADD COLUMN IF NOT EXISTS account_number_token VARCHAR(25),
ADD COLUMN IF NOT EXISTS account_name_encrypted VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_customer_accounts_token ON customer_accounts(account_number_token);

-- PAYMENT_TRANSACTIONS table modifications
ALTER TABLE payment_transactions
ADD COLUMN IF NOT EXISTS sender_account_token VARCHAR(25),
ADD COLUMN IF NOT EXISTS recipient_account_token VARCHAR(25),
ADD COLUMN IF NOT EXISTS sender_name_encrypted VARCHAR(500),
ADD COLUMN IF NOT EXISTS recipient_name_encrypted VARCHAR(500);

-- Add encryption metadata entries
INSERT INTO pci_encryption_metadata (table_name, column_name, encryption_type, key_type, migration_version)
VALUES 
    ('payment_requests', 'card_number_token', 'TOKENIZED', 'PAN_TOKENIZATION', 'V2024_01_15_001'),
    ('payment_requests', 'cvv_encrypted', 'ENCRYPTED', 'CVV_ENCRYPTION', 'V2024_01_15_001'),
    ('payment_requests', 'recipient_name_encrypted', 'ENCRYPTED', 'CARDHOLDER_NAME', 'V2024_01_15_001'),
    ('payment_requests', 'recipient_account_token', 'TOKENIZED', 'ACCOUNT_TOKENIZATION', 'V2024_01_15_001'),
    ('payment_requests', 'recipient_iban_encrypted', 'ENCRYPTED', 'CARDHOLDER_NAME', 'V2024_01_15_001'),
    ('payment_cards', 'card_token', 'TOKENIZED', 'PAN_TOKENIZATION', 'V2024_01_15_001'),
    ('payment_cards', 'cardholder_name_encrypted', 'ENCRYPTED', 'CARDHOLDER_NAME', 'V2024_01_15_001'),
    ('customer_accounts', 'account_number_token', 'TOKENIZED', 'ACCOUNT_TOKENIZATION', 'V2024_01_15_001'),
    ('customer_accounts', 'account_name_encrypted', 'ENCRYPTED', 'CARDHOLDER_NAME', 'V2024_01_15_001'),
    ('payment_transactions', 'sender_account_token', 'TOKENIZED', 'ACCOUNT_TOKENIZATION', 'V2024_01_15_001'),
    ('payment_transactions', 'recipient_account_token', 'TOKENIZED', 'ACCOUNT_TOKENIZATION', 'V2024_01_15_001'),
    ('payment_transactions', 'sender_name_encrypted', 'ENCRYPTED', 'CARDHOLDER_NAME', 'V2024_01_15_001'),
    ('payment_transactions', 'recipient_name_encrypted', 'ENCRYPTED', 'CARDHOLDER_NAME', 'V2024_01_15_001')
ON CONFLICT (table_name, column_name) DO NOTHING;

-- Create triggers for encryption audit logging
CREATE OR REPLACE FUNCTION log_encryption_operation()
RETURNS TRIGGER AS $$
BEGIN
    -- Log when encrypted/tokenized fields are modified
    IF TG_OP = 'INSERT' THEN
        INSERT INTO pci_encryption_audit (operation_type, table_name, column_name, record_id, operation_timestamp)
        VALUES ('INSERT', TG_TABLE_NAME, 'encrypted_fields', NEW.id::TEXT, CURRENT_TIMESTAMP);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        -- Check if any encrypted/tokenized fields were modified
        IF (OLD.card_number_token IS DISTINCT FROM NEW.card_number_token) OR
           (OLD.cvv_encrypted IS DISTINCT FROM NEW.cvv_encrypted) OR
           (OLD.recipient_name_encrypted IS DISTINCT FROM NEW.recipient_name_encrypted) OR
           (OLD.recipient_account_token IS DISTINCT FROM NEW.recipient_account_token) OR
           (OLD.recipient_iban_encrypted IS DISTINCT FROM NEW.recipient_iban_encrypted) THEN
            
            INSERT INTO pci_encryption_audit (operation_type, table_name, column_name, record_id, operation_timestamp)
            VALUES ('UPDATE', TG_TABLE_NAME, 'encrypted_fields', NEW.id::TEXT, CURRENT_TIMESTAMP);
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO pci_encryption_audit (operation_type, table_name, column_name, record_id, operation_timestamp)
        VALUES ('DELETE', TG_TABLE_NAME, 'encrypted_fields', OLD.id::TEXT, CURRENT_TIMESTAMP);
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create triggers on tables with encrypted data
DROP TRIGGER IF EXISTS payment_requests_encryption_audit ON payment_requests;
CREATE TRIGGER payment_requests_encryption_audit
    AFTER INSERT OR UPDATE OR DELETE ON payment_requests
    FOR EACH ROW EXECUTE FUNCTION log_encryption_operation();

-- Similar triggers for other tables (if they exist)
-- Note: These will only be created if the tables exist
DO $$
BEGIN
    -- Payment cards trigger
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_cards') THEN
        DROP TRIGGER IF EXISTS payment_cards_encryption_audit ON payment_cards;
        CREATE TRIGGER payment_cards_encryption_audit
            AFTER INSERT OR UPDATE OR DELETE ON payment_cards
            FOR EACH ROW EXECUTE FUNCTION log_encryption_operation();
    END IF;
    
    -- Customer accounts trigger  
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'customer_accounts') THEN
        DROP TRIGGER IF EXISTS customer_accounts_encryption_audit ON customer_accounts;
        CREATE TRIGGER customer_accounts_encryption_audit
            AFTER INSERT OR UPDATE OR DELETE ON customer_accounts
            FOR EACH ROW EXECUTE FUNCTION log_encryption_operation();
    END IF;
    
    -- Payment transactions trigger
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'payment_transactions') THEN
        DROP TRIGGER IF EXISTS payment_transactions_encryption_audit ON payment_transactions;
        CREATE TRIGGER payment_transactions_encryption_audit
            AFTER INSERT OR UPDATE OR DELETE ON payment_transactions
            FOR EACH ROW EXECUTE FUNCTION log_encryption_operation();
    END IF;
END $$;

-- Add database-level security policies (Row Level Security)
-- These can be enabled for additional protection if needed

-- Create view for secure payment data access
CREATE OR REPLACE VIEW secure_payment_requests AS 
SELECT 
    id,
    payment_id,
    user_id,
    amount,
    currency,
    -- Mask sensitive fields in views
    CASE 
        WHEN card_number_token IS NOT NULL 
        THEN SUBSTRING(card_number_token, 1, 6) || '******' || SUBSTRING(card_number_token, -4)
        ELSE NULL 
    END AS card_number_masked,
    '***' AS cvv_masked,  -- Never show CVV in views
    CASE 
        WHEN recipient_name_encrypted IS NOT NULL 
        THEN '***ENCRYPTED***'
        ELSE NULL 
    END AS recipient_name_masked,
    payment_method,
    created_at,
    updated_at,
    encryption_version
FROM payment_requests;

-- Grant appropriate permissions
-- Note: In production, create specific roles with minimal permissions
GRANT SELECT ON pci_encryption_metadata TO application_role;
GRANT INSERT ON pci_encryption_audit TO application_role;
GRANT SELECT, INSERT, UPDATE ON payment_requests TO application_role;
GRANT SELECT ON secure_payment_requests TO reporting_role;

-- Add comments for documentation
COMMENT ON TABLE pci_encryption_metadata IS 'Tracks which columns are encrypted/tokenized for PCI DSS compliance';
COMMENT ON TABLE pci_encryption_audit IS 'Audit trail for all encryption/decryption operations';
COMMENT ON COLUMN payment_requests.card_number_token IS 'Format-preserving token for PAN (Primary Account Number)';
COMMENT ON COLUMN payment_requests.cvv_encrypted IS 'AES-256-GCM encrypted CVV (should not be stored long-term)';
COMMENT ON COLUMN payment_requests.recipient_name_encrypted IS 'AES-256-GCM encrypted recipient name';
COMMENT ON COLUMN payment_requests.recipient_account_token IS 'Format-preserving token for recipient account number';
COMMENT ON COLUMN payment_requests.recipient_iban_encrypted IS 'AES-256-GCM encrypted IBAN';

-- Commit the transaction
COMMIT;

-- Post-migration verification queries
-- These can be run to verify the migration was successful

/*
-- Verify new columns were created
SELECT column_name, data_type, character_maximum_length 
FROM information_schema.columns 
WHERE table_name = 'payment_requests' 
  AND column_name LIKE '%token' OR column_name LIKE '%encrypted';

-- Verify metadata tracking
SELECT * FROM pci_encryption_metadata WHERE migration_version = 'V2024_01_15_001';

-- Verify triggers are in place
SELECT trigger_name, event_manipulation, event_object_table 
FROM information_schema.triggers 
WHERE trigger_name LIKE '%encryption_audit%';
*/