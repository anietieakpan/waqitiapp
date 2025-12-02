-- Add missing foreign key indexes for rewards-service
-- Critical for rewards lookup and referral tracking performance

-- user_rewards_account table - tier lookup
CREATE INDEX IF NOT EXISTS idx_user_rewards_account_tier_id_fk
    ON user_rewards_account(tier_id);

-- redemptions table - user lookup (already has index but ensure consistency)
CREATE INDEX IF NOT EXISTS idx_redemptions_catalog_item_id
    ON redemptions(catalog_item_id);

-- referrals table - referred user lookup
CREATE INDEX IF NOT EXISTS idx_referrals_referred_user_id
    ON referrals(referred_user_id)
    WHERE referred_user_id IS NOT NULL;

-- user_challenge_progress table - challenge lookup
CREATE INDEX IF NOT EXISTS idx_user_challenge_progress_challenge_id
    ON user_challenge_progress(challenge_id);

-- rewards_catalog table - partner lookup
CREATE INDEX IF NOT EXISTS idx_rewards_catalog_partner_id
    ON rewards_catalog(partner_id)
    WHERE partner_id IS NOT NULL;

COMMENT ON INDEX idx_user_rewards_account_tier_id_fk IS
    'Performance: Tier benefits lookup for rewards calculations';
COMMENT ON INDEX idx_referrals_referred_user_id IS
    'Performance: Find referral record for referred users';
COMMENT ON INDEX idx_user_challenge_progress_challenge_id IS
    'Performance: Find all users participating in specific challenge';

-- Analyze tables
ANALYZE user_rewards_account;
ANALYZE redemptions;
ANALYZE referrals;
ANALYZE user_challenge_progress;
ANALYZE rewards_catalog;
