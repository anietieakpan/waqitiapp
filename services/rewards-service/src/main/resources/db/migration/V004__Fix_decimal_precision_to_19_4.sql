-- Fix DECIMAL(15,2) to DECIMAL(19,4) for all financial amounts in Rewards service
-- Priority: HIGH - Critical for accurate cashback tracking and redemption values
-- Impact: Prevents precision loss in cashback accumulation and high-value redemptions

-- user_rewards_account table - Fix all cashback fields
ALTER TABLE user_rewards_account
    ALTER COLUMN cashback_balance TYPE DECIMAL(19,4),
    ALTER COLUMN lifetime_cashback_earned TYPE DECIMAL(19,4),
    ALTER COLUMN lifetime_cashback_redeemed TYPE DECIMAL(19,4);

-- cashback_transactions table - Fix amount fields
ALTER TABLE cashback_transactions
    ALTER COLUMN amount TYPE DECIMAL(19,4),
    ALTER COLUMN balance_after TYPE DECIMAL(19,4);

-- rewards_catalog table - Fix cash value field
ALTER TABLE rewards_catalog
    ALTER COLUMN cash_value TYPE DECIMAL(19,4);

-- redemptions table - Fix cash value field
ALTER TABLE redemptions
    ALTER COLUMN cash_value TYPE DECIMAL(19,4);

-- referrals table - Fix cashback earned fields
ALTER TABLE referrals
    ALTER COLUMN referrer_cashback_earned TYPE DECIMAL(19,4),
    ALTER COLUMN referred_cashback_earned TYPE DECIMAL(19,4);

-- challenges table - Fix cashback reward field
ALTER TABLE challenges
    ALTER COLUMN cashback_reward TYPE DECIMAL(19,4);

-- user_challenge_progress table - Fix cashback claimed field
ALTER TABLE user_challenge_progress
    ALTER COLUMN cashback_claimed TYPE DECIMAL(19,4);

-- Add comments for documentation
COMMENT ON COLUMN user_rewards_account.cashback_balance IS 'Cashback balance with 4 decimal precision for accurate accumulation';
COMMENT ON COLUMN cashback_transactions.amount IS 'Transaction amount with 4 decimal precision to prevent rounding errors';
COMMENT ON COLUMN redemptions.cash_value IS 'Redemption cash value with 4 decimal precision for accurate payouts';
COMMENT ON COLUMN referrals.referrer_cashback_earned IS 'Referrer cashback with 4 decimal precision for accurate rewards';

-- Analyze tables to update statistics
ANALYZE user_rewards_account;
ANALYZE cashback_transactions;
ANALYZE rewards_catalog;
ANALYZE redemptions;
ANALYZE referrals;
ANALYZE challenges;
ANALYZE user_challenge_progress;
