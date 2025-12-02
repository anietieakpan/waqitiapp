-- Create Rewards Program Tables
-- Comprehensive loyalty points and rewards system

-- User Rewards Account
CREATE TABLE user_rewards_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    
    -- Points Balance
    points_balance BIGINT NOT NULL DEFAULT 0,
    lifetime_points_earned BIGINT NOT NULL DEFAULT 0,
    lifetime_points_redeemed BIGINT NOT NULL DEFAULT 0,
    pending_points BIGINT NOT NULL DEFAULT 0,
    
    -- Tier Information
    tier_id UUID NOT NULL,
    tier_progress_points BIGINT NOT NULL DEFAULT 0,
    tier_expiry_date TIMESTAMP WITH TIME ZONE,
    
    -- Cashback
    cashback_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    lifetime_cashback_earned DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    lifetime_cashback_redeemed DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    
    -- Statistics
    total_transactions INTEGER NOT NULL DEFAULT 0,
    total_referrals INTEGER NOT NULL DEFAULT 0,
    streak_days INTEGER NOT NULL DEFAULT 0,
    last_activity_date TIMESTAMP WITH TIME ZONE,
    
    -- Account Status
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    enrollment_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_points_balance CHECK (points_balance >= 0),
    CONSTRAINT check_cashback_balance CHECK (cashback_balance >= 0)
);

-- Rewards Tiers
CREATE TABLE rewards_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_name VARCHAR(100) NOT NULL UNIQUE,
    tier_level INTEGER NOT NULL UNIQUE,
    
    -- Requirements
    points_required BIGINT NOT NULL,
    transactions_required INTEGER DEFAULT 0,
    
    -- Benefits
    points_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    cashback_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0100, -- 1% default
    
    -- Perks
    perks JSONB,
    
    -- Display
    color_code VARCHAR(7),
    icon_url TEXT,
    description TEXT,
    
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_tier_level CHECK (tier_level >= 0),
    CONSTRAINT check_points_required CHECK (points_required >= 0),
    CONSTRAINT check_multiplier CHECK (points_multiplier >= 1.00)
);

-- Points Transactions
CREATE TABLE points_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    
    -- Transaction Details
    transaction_type VARCHAR(50) NOT NULL, -- EARNED, REDEEMED, EXPIRED, ADJUSTED
    points_amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    
    -- Source Information
    source_type VARCHAR(50) NOT NULL, -- PAYMENT, REFERRAL, BONUS, REDEMPTION, etc.
    source_id VARCHAR(100),
    
    -- Additional Details
    description TEXT,
    metadata JSONB,
    
    -- Expiry
    expires_at TIMESTAMP WITH TIME ZONE,
    is_expired BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_points_transactions_user_id (user_id),
    INDEX idx_points_transactions_created_at (created_at),
    INDEX idx_points_transactions_expires_at (expires_at) WHERE expires_at IS NOT NULL
);

-- Cashback Transactions
CREATE TABLE cashback_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    
    -- Transaction Details
    transaction_type VARCHAR(50) NOT NULL, -- EARNED, REDEEMED, TRANSFERRED
    amount DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    
    -- Source Information
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(100),
    
    -- Category
    category VARCHAR(50),
    merchant_name VARCHAR(255),
    
    -- Additional Details
    description TEXT,
    metadata JSONB,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_cashback_transactions_user_id (user_id),
    INDEX idx_cashback_transactions_created_at (created_at),
    CONSTRAINT check_amount CHECK (amount != 0)
);

-- Rewards Catalog
CREATE TABLE rewards_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Item Details
    item_name VARCHAR(200) NOT NULL,
    item_type VARCHAR(50) NOT NULL, -- GIFT_CARD, CASHBACK, EXPERIENCE, MERCHANDISE
    category VARCHAR(100),
    
    -- Redemption
    points_cost BIGINT NOT NULL,
    cash_value DECIMAL(15,2),
    
    -- Availability
    quantity_available INTEGER,
    quantity_redeemed INTEGER DEFAULT 0,
    max_per_user INTEGER DEFAULT 1,
    
    -- Validity
    valid_from TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP WITH TIME ZONE,
    
    -- Display
    description TEXT,
    terms_conditions TEXT,
    image_url TEXT,
    brand_logo_url TEXT,
    
    -- Partner Information
    partner_id UUID,
    partner_name VARCHAR(255),
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_featured BOOLEAN DEFAULT FALSE,
    sort_order INTEGER DEFAULT 0,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_points_cost CHECK (points_cost > 0),
    INDEX idx_rewards_catalog_active (is_active, valid_until)
);

-- Redemptions
CREATE TABLE redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    catalog_item_id UUID NOT NULL REFERENCES rewards_catalog(id),
    
    -- Redemption Details
    points_spent BIGINT NOT NULL,
    cash_value DECIMAL(15,2),
    quantity INTEGER NOT NULL DEFAULT 1,
    
    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    
    -- Fulfillment
    fulfillment_type VARCHAR(50), -- INSTANT, EMAIL, PHYSICAL
    fulfillment_details JSONB,
    redemption_code VARCHAR(100),
    
    -- Timestamps
    redeemed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- Additional
    notes TEXT,
    metadata JSONB,
    
    INDEX idx_redemptions_user_id (user_id),
    INDEX idx_redemptions_status (status),
    INDEX idx_redemptions_redeemed_at (redeemed_at)
);

-- Referral Program
CREATE TABLE referrals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_user_id UUID NOT NULL,
    referred_user_id UUID UNIQUE,
    
    -- Referral Details
    referral_code VARCHAR(50) UNIQUE NOT NULL,
    referral_link TEXT,
    
    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, COMPLETED, EXPIRED, CANCELLED
    
    -- Rewards
    referrer_points_earned BIGINT DEFAULT 0,
    referred_points_earned BIGINT DEFAULT 0,
    referrer_cashback_earned DECIMAL(15,2) DEFAULT 0.00,
    referred_cashback_earned DECIMAL(15,2) DEFAULT 0.00,
    
    -- Tracking
    clicked_at TIMESTAMP WITH TIME ZONE,
    signed_up_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- Source
    source_channel VARCHAR(50), -- EMAIL, SMS, SOCIAL, APP
    campaign_id VARCHAR(100),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    INDEX idx_referrals_referrer (referrer_user_id),
    INDEX idx_referrals_code (referral_code),
    INDEX idx_referrals_status (status)
);

-- Earning Rules
CREATE TABLE earning_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(50) NOT NULL, -- TRANSACTION, CATEGORY, MERCHANT, SPECIAL
    
    -- Conditions
    conditions JSONB NOT NULL,
    
    -- Rewards
    points_formula VARCHAR(255), -- e.g., "amount * 2" or "100"
    cashback_rate DECIMAL(5,4),
    bonus_points BIGINT,
    
    -- Limits
    max_points_per_transaction BIGINT,
    max_points_per_period BIGINT,
    period_type VARCHAR(20), -- DAY, WEEK, MONTH
    
    -- Validity
    valid_from TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP WITH TIME ZONE,
    
    -- Priority (higher number = higher priority)
    priority INTEGER DEFAULT 0,
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_earning_rules_active (is_active, valid_from, valid_until)
);

-- Challenges/Missions
CREATE TABLE challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Challenge Details
    challenge_name VARCHAR(200) NOT NULL,
    challenge_type VARCHAR(50) NOT NULL, -- TRANSACTION_COUNT, SPEND_AMOUNT, CATEGORY_SPEND, STREAK
    description TEXT,
    
    -- Requirements
    requirements JSONB NOT NULL,
    
    -- Rewards
    points_reward BIGINT,
    cashback_reward DECIMAL(15,2),
    tier_progress_bonus BIGINT,
    
    -- Timing
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Limits
    max_participants INTEGER,
    max_completions_per_user INTEGER DEFAULT 1,
    
    -- Display
    icon_url TEXT,
    banner_url TEXT,
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_challenges_active (is_active, start_date, end_date)
);

-- User Challenge Progress
CREATE TABLE user_challenge_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    challenge_id UUID NOT NULL REFERENCES challenges(id),
    
    -- Progress
    current_progress JSONB NOT NULL DEFAULT '{}',
    progress_percentage INTEGER DEFAULT 0,
    
    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, CLAIMED, EXPIRED
    
    -- Timestamps
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    claimed_at TIMESTAMP WITH TIME ZONE,
    
    -- Rewards Claimed
    points_claimed BIGINT DEFAULT 0,
    cashback_claimed DECIMAL(15,2) DEFAULT 0.00,
    
    UNIQUE(user_id, challenge_id),
    INDEX idx_user_challenge_progress_user (user_id, status),
    CONSTRAINT check_progress CHECK (progress_percentage >= 0 AND progress_percentage <= 100)
);

-- Create indexes
CREATE INDEX idx_user_rewards_account_user_id ON user_rewards_account(user_id);
CREATE INDEX idx_user_rewards_account_tier_id ON user_rewards_account(tier_id);
CREATE INDEX idx_user_rewards_account_status ON user_rewards_account(status);

-- Create triggers for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_rewards_account_updated_at 
    BEFORE UPDATE ON user_rewards_account 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_rewards_catalog_updated_at 
    BEFORE UPDATE ON rewards_catalog 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_earning_rules_updated_at 
    BEFORE UPDATE ON earning_rules 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default tiers
INSERT INTO rewards_tiers (tier_name, tier_level, points_required, points_multiplier, cashback_rate, perks) VALUES
('Bronze', 0, 0, 1.00, 0.0100, '{"free_transfers": 3, "atm_fee_rebate": false}'),
('Silver', 1, 5000, 1.25, 0.0150, '{"free_transfers": 5, "atm_fee_rebate": true, "priority_support": false}'),
('Gold', 2, 25000, 1.50, 0.0200, '{"free_transfers": 10, "atm_fee_rebate": true, "priority_support": true, "airport_lounge": false}'),
('Platinum', 3, 100000, 2.00, 0.0300, '{"free_transfers": "unlimited", "atm_fee_rebate": true, "priority_support": true, "airport_lounge": true, "concierge": true}');