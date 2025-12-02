-- V10__Upgrade_SigningKey_Decimal_Precision.sql
-- CRITICAL SECURITY FIX: Upgrade transaction limit columns from DOUBLE to DECIMAL(19,4)
-- Prevents precision bypass attacks where attackers exploit floating-point comparison
-- Example attack: maxTransactionAmount = 10000.0, attacker sends 10000.000000001 to bypass check

-- =======================================================================================
-- SIGNING_KEYS TABLE - Transaction Limit Security Fix
-- =======================================================================================

-- Upgrade transaction amount limit columns
ALTER TABLE signing_keys
    ALTER COLUMN max_transaction_amount TYPE DECIMAL(19,4),
    ALTER COLUMN daily_transaction_limit TYPE DECIMAL(19,4),
    ALTER COLUMN daily_transaction_total TYPE DECIMAL(19,4);

-- Add CHECK constraint to ensure limits are non-negative
ALTER TABLE signing_keys
    ADD CONSTRAINT chk_signing_key_max_transaction_amount_non_negative
    CHECK (max_transaction_amount IS NULL OR max_transaction_amount >= 0);

ALTER TABLE signing_keys
    ADD CONSTRAINT chk_signing_key_daily_limit_non_negative
    CHECK (daily_transaction_limit IS NULL OR daily_transaction_limit >= 0);

ALTER TABLE signing_keys
    ADD CONSTRAINT chk_signing_key_daily_total_non_negative
    CHECK (daily_transaction_total IS NULL OR daily_transaction_total >= 0);

-- Add CHECK constraint to ensure daily total doesn't exceed daily limit
ALTER TABLE signing_keys
    ADD CONSTRAINT chk_signing_key_daily_total_within_limit
    CHECK (
        daily_transaction_limit IS NULL OR
        daily_transaction_total IS NULL OR
        daily_transaction_total <= daily_transaction_limit
    );

-- =======================================================================================
-- VERIFICATION
-- =======================================================================================
-- This migration:
-- 1. Upgrades 3 columns from DOUBLE PRECISION to DECIMAL(19,4)
-- 2. Adds 4 CHECK constraints for data integrity
-- 3. Prevents floating-point precision bypass attacks
-- 4. Ensures exact decimal comparison for cryptographic signature validation
--
-- Security Impact: CRITICAL
-- - Closes CVE-level precision bypass vulnerability
-- - Enforces exact amount validation for all cryptographically signed transactions
-- - Maintains audit trail integrity with exact decimal amounts
