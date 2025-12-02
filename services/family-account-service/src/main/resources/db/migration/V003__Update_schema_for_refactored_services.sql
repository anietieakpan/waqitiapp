-- Migration V003: Update schema for refactored services
-- Date: 2025-10-17
-- Description: Adds missing columns and updates existing tables for refactored service architecture

-- Update family_members table to add missing columns
ALTER TABLE family_members
    ADD COLUMN IF NOT EXISTS wallet_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS daily_spending_limit DECIMAL(19,2),
    ADD COLUMN IF NOT EXISTS weekly_spending_limit DECIMAL(19,2),
    ADD COLUMN IF NOT EXISTS monthly_spending_limit DECIMAL(19,2),
    ADD COLUMN IF NOT EXISTS allowance_frequency VARCHAR(20) CHECK (allowance_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    ADD COLUMN IF NOT EXISTS can_view_family_account BOOLEAN DEFAULT TRUE NOT NULL,
    ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Update family_spending_rules table to align with refactored SpendingRuleService
ALTER TABLE family_spending_rules
    ADD COLUMN IF NOT EXISTS restricted_merchant_category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS time_restriction_start VARCHAR(5),  -- HH:mm format
    ADD COLUMN IF NOT EXISTS time_restriction_end VARCHAR(5),    -- HH:mm format
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE NOT NULL;

-- Rename columns for consistency with refactored code
DO $$
BEGIN
    -- Rename rule_status to is_active if it exists
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='family_spending_rules' AND column_name='rule_status') THEN
        -- Add is_active column
        ALTER TABLE family_spending_rules
            ADD COLUMN IF NOT EXISTS is_active_temp BOOLEAN;

        -- Migrate data
        UPDATE family_spending_rules
        SET is_active_temp = (rule_status = 'ACTIVE');

        -- Drop old column (after backup in production)
        -- ALTER TABLE family_spending_rules DROP COLUMN rule_status;

        -- Rename temp column
        -- ALTER TABLE family_spending_rules RENAME COLUMN is_active_temp TO is_active;
    END IF;
END $$;

-- Update family_accounts table for default spending limits
ALTER TABLE family_accounts
    ADD COLUMN IF NOT EXISTS default_daily_limit DECIMAL(19,2),
    ADD COLUMN IF NOT EXISTS default_weekly_limit DECIMAL(19,2),
    ADD COLUMN IF NOT EXISTS default_monthly_limit DECIMAL(19,2);

-- Add indexes for new columns
CREATE INDEX IF NOT EXISTS idx_family_members_wallet ON family_members(wallet_id);
CREATE INDEX IF NOT EXISTS idx_family_members_joined ON family_members(joined_at);
CREATE INDEX IF NOT EXISTS idx_spending_rules_active ON family_spending_rules(is_active);

-- Add comments
COMMENT ON COLUMN family_members.wallet_id IS 'Individual wallet ID for member (linked to wallet service)';
COMMENT ON COLUMN family_members.daily_spending_limit IS 'Daily spending limit for member';
COMMENT ON COLUMN family_members.weekly_spending_limit IS 'Weekly spending limit for member';
COMMENT ON COLUMN family_members.monthly_spending_limit IS 'Monthly spending limit for member';
COMMENT ON COLUMN family_members.allowance_frequency IS 'Frequency of allowance payments: DAILY, WEEKLY, or MONTHLY';
COMMENT ON COLUMN family_members.can_view_family_account IS 'Whether member can view family account details';
COMMENT ON COLUMN family_members.joined_at IS 'Timestamp when member joined the family account';

COMMENT ON COLUMN family_spending_rules.restricted_merchant_category IS 'Merchant category to restrict (for MERCHANT_RESTRICTION rule type)';
COMMENT ON COLUMN family_spending_rules.time_restriction_start IS 'Start time for allowed transactions (HH:mm format)';
COMMENT ON COLUMN family_spending_rules.time_restriction_end IS 'End time for allowed transactions (HH:mm format)';
COMMENT ON COLUMN family_spending_rules.is_active IS 'Whether rule is currently active';

COMMENT ON COLUMN family_accounts.default_daily_limit IS 'Default daily spending limit for new members';
COMMENT ON COLUMN family_accounts.default_weekly_limit IS 'Default weekly spending limit for new members';
COMMENT ON COLUMN family_accounts.default_monthly_limit IS 'Default monthly spending limit for new members';
