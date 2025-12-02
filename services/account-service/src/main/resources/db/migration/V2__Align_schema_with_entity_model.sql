-- ============================================================================
-- CRITICAL FIX: Align Database Schema with Entity Model (Account.java)
-- ============================================================================
-- Migration V2: Schema alignment to match Account.java entity definition
-- Author: Production Readiness Team
-- Date: 2025-01-15
-- Issue: V1 schema has separate account_balances table but entity expects inline
-- ============================================================================

-- Step 1: Add missing columns to accounts table that match Account.java entity
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_name VARCHAR(100);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS account_category VARCHAR(20);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS parent_account_id UUID REFERENCES accounts(id);

-- Balance fields (inline, not separate table)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS balance NUMERIC(19,4) NOT NULL DEFAULT 0.0000;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS available_balance NUMERIC(19,4) NOT NULL DEFAULT 0.0000;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS ledger_balance NUMERIC(19,4) NOT NULL DEFAULT 0.0000;

-- Spending tracking
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS daily_spent NUMERIC(19,4) DEFAULT 0.0000;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS monthly_spent NUMERIC(19,4) DEFAULT 0.0000;

-- KYC fields
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS kyc_level VARCHAR(20);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS kyc_verified_at TIMESTAMP WITH TIME ZONE;

-- Account features
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS international_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS virtual_card_enabled BOOLEAN DEFAULT FALSE;

-- Timestamps
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS opened_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_transaction_at TIMESTAMP WITH TIME ZONE;

-- Interest (for savings accounts)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS interest_rate NUMERIC(5,4);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_interest_calculated_at TIMESTAMP WITH TIME ZONE;

-- Freeze management
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS frozen BOOLEAN DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS freeze_reason VARCHAR(500);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS frozen_at TIMESTAMP WITH TIME ZONE;

-- Notification preferences (JSONB for flexibility)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS notification_preferences JSONB;

-- Risk and compliance
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS risk_score INTEGER;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS compliance_flags JSONB;

-- Account tier
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS tier_level VARCHAR(20);

-- Soft delete support
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

-- Optimistic locking (JPA @Version support)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Audit fields from BaseEntity
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS business_key VARCHAR(255);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_modified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_modified_by UUID;

-- Step 2: Migrate data from account_balances to accounts table (if data exists)
-- This handles the case where V1 was already applied
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'account_balances') THEN
        UPDATE accounts a
        SET
            balance = COALESCE(ab.current_balance, 0.0),
            available_balance = COALESCE(ab.available_balance, 0.0),
            ledger_balance = COALESCE(ab.current_balance, 0.0),
            last_transaction_at = ab.last_transaction_at
        FROM account_balances ab
        WHERE a.id = ab.account_id;

        RAISE NOTICE 'Migrated balance data from account_balances to accounts table';
    END IF;
END $$;

-- Step 3: Update status column to match AccountStatus enum
ALTER TABLE accounts ALTER COLUMN status TYPE VARCHAR(50);
UPDATE accounts SET status = 'PENDING_ACTIVATION' WHERE status = 'ACTIVE' AND balance = 0;

-- Step 4: Add CHECK constraints to match entity validation
ALTER TABLE accounts ADD CONSTRAINT chk_account_type
    CHECK (account_type IN ('SAVINGS', 'CHECKING', 'INVESTMENT', 'CREDIT', 'LOAN', 'WALLET'));

ALTER TABLE accounts ADD CONSTRAINT chk_account_status
    CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'FROZEN', 'CLOSED'));

ALTER TABLE accounts ADD CONSTRAINT chk_account_category
    CHECK (account_category IN ('PERSONAL', 'BUSINESS', 'JOINT', 'TRUST', 'CORPORATE'));

ALTER TABLE accounts ADD CONSTRAINT chk_tier_level
    CHECK (tier_level IN ('BASIC', 'STANDARD', 'PREMIUM', 'VIP', 'PLATINUM'));

ALTER TABLE accounts ADD CONSTRAINT chk_kyc_level
    CHECK (kyc_level IN ('LEVEL_0', 'LEVEL_1', 'LEVEL_2', 'LEVEL_3'));

-- Ensure balances are non-negative
ALTER TABLE accounts ADD CONSTRAINT chk_balance_non_negative
    CHECK (balance >= 0);

ALTER TABLE accounts ADD CONSTRAINT chk_available_balance_valid
    CHECK (available_balance >= 0 AND available_balance <= balance);

-- Step 5: Create new indexes matching Account.java @Table(indexes={...})
CREATE INDEX IF NOT EXISTS idx_account_user_id ON accounts(user_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_account_number_unique ON accounts(account_number) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_account_type ON accounts(account_type) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_account_status ON accounts(status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_account_created_at ON accounts(created_at);
CREATE INDEX IF NOT EXISTS idx_account_parent_id ON accounts(parent_account_id) WHERE parent_account_id IS NOT NULL;

-- Additional performance indexes
CREATE INDEX IF NOT EXISTS idx_account_tier_level ON accounts(tier_level) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_account_kyc_level ON accounts(kyc_level) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_account_frozen ON accounts(frozen) WHERE frozen = TRUE;
CREATE INDEX IF NOT EXISTS idx_account_balance ON accounts(balance) WHERE deleted = FALSE AND status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_account_last_transaction ON accounts(last_transaction_at) WHERE deleted = FALSE;

-- Step 6: Create account_tags table for @ElementCollection
CREATE TABLE IF NOT EXISTS account_tags (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (account_id, tag)
);

CREATE INDEX idx_account_tags_account_id ON account_tags(account_id);
CREATE INDEX idx_account_tags_tag ON account_tags(tag);

-- Step 7: Update trigger for updated_at and last_modified_at
CREATE OR REPLACE FUNCTION update_account_timestamps()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.last_modified_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_accounts_updated_at ON accounts;
CREATE TRIGGER update_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION update_account_timestamps();

-- Step 8: Populate default values for new columns
UPDATE accounts
SET
    account_name = COALESCE(account_name, name),
    account_category = COALESCE(account_category, 'PERSONAL'),
    tier_level = COALESCE(tier_level, 'STANDARD'),
    kyc_level = COALESCE(kyc_level, 'LEVEL_1'),
    business_key = COALESCE(business_key, account_number),
    opened_at = COALESCE(opened_at, created_at),
    active = COALESCE(active, TRUE)
WHERE account_name IS NULL
   OR account_category IS NULL
   OR tier_level IS NULL;

-- Step 9: Add NOT NULL constraints after populating defaults
ALTER TABLE accounts ALTER COLUMN account_name SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN account_category SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN tier_level SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN business_key SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN frozen SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN international_enabled SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN virtual_card_enabled SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN active SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN deleted SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN version SET NOT NULL;

-- Step 10: Add comprehensive table comments
COMMENT ON TABLE accounts IS 'Main accounts table - aligned with Account.java entity model. Includes inline balance tracking, KYC, compliance, and audit fields.';
COMMENT ON COLUMN accounts.balance IS 'Current account balance with 4 decimal precision for accurate financial calculations';
COMMENT ON COLUMN accounts.available_balance IS 'Available balance excluding holds and pending transactions';
COMMENT ON COLUMN accounts.ledger_balance IS 'End-of-day ledger balance for reconciliation';
COMMENT ON COLUMN accounts.version IS 'Optimistic locking version field (JPA @Version) - incremented on each update';
COMMENT ON COLUMN accounts.business_key IS 'Business key for domain-driven design (typically account_number)';
COMMENT ON COLUMN accounts.notification_preferences IS 'JSON field storing user notification preferences';
COMMENT ON COLUMN accounts.compliance_flags IS 'JSON field storing compliance-related flags and metadata';
COMMENT ON COLUMN accounts.frozen IS 'Account freeze status for security or compliance reasons';
COMMENT ON COLUMN accounts.risk_score IS 'Risk assessment score (0-100) for fraud prevention';

-- Step 11: Create view for backward compatibility with old account_balances queries
CREATE OR REPLACE VIEW account_balances_view AS
SELECT
    id as account_id,
    currency,
    available_balance,
    balance as current_balance,
    (balance - available_balance) as pending_balance,
    0.00 as held_balance,
    last_transaction_at,
    updated_at as balance_updated_at
FROM accounts
WHERE deleted = FALSE;

COMMENT ON VIEW account_balances_view IS 'Backward compatibility view - mimics old account_balances table structure';

-- Step 12: Grant necessary permissions (adjust as needed for your environment)
-- GRANT SELECT, INSERT, UPDATE ON accounts TO account_service_app;
-- GRANT SELECT, INSERT, DELETE ON account_tags TO account_service_app;
-- GRANT SELECT ON account_balances_view TO account_service_app;

-- Step 13: Create function for balance updates with proper locking
CREATE OR REPLACE FUNCTION update_account_balance(
    p_account_id UUID,
    p_amount NUMERIC(19,4),
    p_transaction_type VARCHAR(10)
) RETURNS BOOLEAN AS $$
DECLARE
    v_current_balance NUMERIC(19,4);
    v_new_balance NUMERIC(19,4);
BEGIN
    -- Lock the row for update
    SELECT balance INTO v_current_balance
    FROM accounts
    WHERE id = p_account_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Account not found: %', p_account_id;
    END IF;

    -- Calculate new balance
    IF p_transaction_type = 'DEBIT' THEN
        v_new_balance = v_current_balance - p_amount;
        IF v_new_balance < 0 THEN
            RAISE EXCEPTION 'Insufficient funds';
        END IF;
    ELSIF p_transaction_type = 'CREDIT' THEN
        v_new_balance = v_current_balance + p_amount;
    ELSE
        RAISE EXCEPTION 'Invalid transaction type: %', p_transaction_type;
    END IF;

    -- Update balance
    UPDATE accounts
    SET
        balance = v_new_balance,
        available_balance = available_balance + (v_new_balance - v_current_balance),
        last_transaction_at = CURRENT_TIMESTAMP
    WHERE id = p_account_id;

    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION update_account_balance IS 'Thread-safe function for updating account balances with proper locking';

-- ============================================================================
-- Migration V2 Complete
-- ============================================================================
-- Summary:
-- 1. ✅ Added all missing columns from Account.java entity
-- 2. ✅ Migrated data from old account_balances table
-- 3. ✅ Added proper constraints and checks
-- 4. ✅ Created indexes matching entity definition
-- 5. ✅ Added optimistic locking support (version column)
-- 6. ✅ Created account_tags table for @ElementCollection
-- 7. ✅ Added triggers for automatic timestamp updates
-- 8. ✅ Created backward compatibility view
-- 9. ✅ Added helper function for safe balance updates
-- 10. ✅ Comprehensive documentation
-- ============================================================================
