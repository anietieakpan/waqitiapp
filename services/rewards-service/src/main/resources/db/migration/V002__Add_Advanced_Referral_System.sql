-- V002: Advanced Referral System Migration
-- Consolidates referral-service features into rewards-service
-- Created: 2025-11-08
-- Purpose: Production-ready comprehensive referral program management

-- ============================================================================
-- REFERRAL PROGRAMS
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id VARCHAR(100) UNIQUE NOT NULL,
    program_name VARCHAR(255) NOT NULL,
    program_type VARCHAR(50) NOT NULL, -- STANDARD, TIERED, MILESTONE_BASED, TIME_LIMITED
    description TEXT,

    -- Program Schedule
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    is_public BOOLEAN DEFAULT TRUE,

    -- Eligibility
    target_audience TEXT[], -- PREMIUM, BUSINESS, PERSONAL, etc.
    eligible_products TEXT[], -- Product categories eligible for referral
    min_account_age_days INTEGER DEFAULT 0,
    requires_kyc_verification BOOLEAN DEFAULT TRUE,

    -- Referrer Rewards
    referrer_reward_type VARCHAR(50) NOT NULL, -- POINTS, CASHBACK, PERCENTAGE, FIXED_AMOUNT
    referrer_reward_amount DECIMAL(15, 2),
    referrer_reward_currency VARCHAR(3) DEFAULT 'USD',
    referrer_reward_percentage DECIMAL(5, 4),
    referrer_reward_points BIGINT,

    -- Referee (New User) Rewards
    referee_reward_type VARCHAR(50), -- POINTS, CASHBACK, PERCENTAGE, FIXED_AMOUNT
    referee_reward_amount DECIMAL(15, 2),
    referee_reward_currency VARCHAR(3) DEFAULT 'USD',
    referee_reward_percentage DECIMAL(5, 4),
    referee_reward_points BIGINT,

    -- Limits and Constraints
    max_referrals_per_user INTEGER,
    max_rewards_per_user DECIMAL(15, 2),
    max_program_budget DECIMAL(18, 2),
    total_rewards_issued DECIMAL(18, 2) DEFAULT 0.00,
    minimum_transaction_amount DECIMAL(15, 2) DEFAULT 0.00,
    reward_expiry_days INTEGER,
    conversion_window_days INTEGER DEFAULT 30,

    -- Auto-activation conditions for referee
    auto_activate_on_signup BOOLEAN DEFAULT FALSE,
    auto_activate_on_first_deposit BOOLEAN DEFAULT TRUE,
    auto_activate_on_kyc_complete BOOLEAN DEFAULT FALSE,
    min_deposit_amount DECIMAL(15, 2),

    -- Terms
    terms_and_conditions TEXT,
    privacy_policy_url TEXT,

    -- Tracking
    total_referrals INTEGER DEFAULT 0,
    successful_referrals INTEGER DEFAULT 0,
    total_clicks INTEGER DEFAULT 0,
    conversion_rate DECIMAL(5, 4) DEFAULT 0.0000,

    -- Audit
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(100),

    CONSTRAINT check_referral_program_dates CHECK (end_date IS NULL OR end_date > start_date),
    CONSTRAINT check_max_referrals CHECK (max_referrals_per_user IS NULL OR max_referrals_per_user > 0),
    CONSTRAINT check_program_budget CHECK (max_program_budget IS NULL OR max_program_budget > 0)
);

CREATE INDEX idx_referral_programs_active ON referral_programs(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_referral_programs_type ON referral_programs(program_type);
CREATE INDEX idx_referral_programs_dates ON referral_programs(start_date, end_date);
CREATE INDEX idx_referral_programs_public ON referral_programs(is_public) WHERE is_public = TRUE;

-- ============================================================================
-- REFERRAL LINKS
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    program_id VARCHAR(100) NOT NULL,
    referral_code VARCHAR(50) NOT NULL UNIQUE,

    -- Link Details
    short_url VARCHAR(255) UNIQUE NOT NULL,
    full_url TEXT NOT NULL,
    qr_code_url TEXT,

    -- Link Configuration
    link_type VARCHAR(50) NOT NULL DEFAULT 'WEB', -- WEB, MOBILE_APP, QR_CODE, EMAIL, SMS, SOCIAL
    channel VARCHAR(50) NOT NULL DEFAULT 'DIRECT', -- DIRECT, EMAIL, SMS, WHATSAPP, FACEBOOK, TWITTER, etc.

    -- Tracking
    click_count INTEGER DEFAULT 0,
    unique_click_count INTEGER DEFAULT 0,
    signup_count INTEGER DEFAULT 0,
    conversion_count INTEGER DEFAULT 0,
    last_clicked_at TIMESTAMP WITH TIME ZONE,

    -- UTM Parameters
    utm_source VARCHAR(100),
    utm_medium VARCHAR(100),
    utm_campaign VARCHAR(100),
    utm_term VARCHAR(100),
    utm_content VARCHAR(100),

    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,

    -- Metadata
    custom_metadata JSONB,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referral_link_program FOREIGN KEY (program_id) REFERENCES referral_programs(program_id) ON DELETE CASCADE,
    CONSTRAINT check_link_expiry CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX idx_referral_links_user ON referral_links(user_id);
CREATE INDEX idx_referral_links_program ON referral_links(program_id);
CREATE INDEX idx_referral_links_code ON referral_links(referral_code);
CREATE INDEX idx_referral_links_active ON referral_links(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_referral_links_channel ON referral_links(channel);

-- ============================================================================
-- REFERRAL CLICKS (Analytics)
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_clicks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    click_id VARCHAR(100) UNIQUE NOT NULL,
    link_id VARCHAR(100) NOT NULL,
    referral_code VARCHAR(50) NOT NULL,

    -- Click Details
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent TEXT,
    device_type VARCHAR(50), -- DESKTOP, MOBILE, TABLET
    device_model VARCHAR(100),
    browser VARCHAR(50),
    browser_version VARCHAR(20),
    operating_system VARCHAR(50),
    os_version VARCHAR(20),

    -- Geolocation
    country_code VARCHAR(3),
    country_name VARCHAR(100),
    region VARCHAR(100),
    city VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),

    -- Referrer Information
    referrer_url TEXT,
    landing_page_url TEXT,

    -- Session Tracking
    session_id VARCHAR(100),
    is_unique_click BOOLEAN DEFAULT TRUE,
    is_bot BOOLEAN DEFAULT FALSE,

    -- Conversion Tracking
    converted BOOLEAN DEFAULT FALSE,
    conversion_id VARCHAR(100),
    converted_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referral_click_link FOREIGN KEY (link_id) REFERENCES referral_links(link_id) ON DELETE CASCADE
);

CREATE INDEX idx_referral_clicks_link ON referral_clicks(link_id);
CREATE INDEX idx_referral_clicks_code ON referral_clicks(referral_code);
CREATE INDEX idx_referral_clicks_timestamp ON referral_clicks(clicked_at DESC);
CREATE INDEX idx_referral_clicks_session ON referral_clicks(session_id);
CREATE INDEX idx_referral_clicks_converted ON referral_clicks(converted) WHERE converted = TRUE;
CREATE INDEX idx_referral_clicks_ip ON referral_clicks(ip_address);
CREATE INDEX idx_referral_clicks_country ON referral_clicks(country_code);

-- ============================================================================
-- REFERRAL REWARDS (Extended from existing)
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_rewards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reward_id VARCHAR(100) UNIQUE NOT NULL,
    referral_id VARCHAR(100) NOT NULL, -- References referrals.id from existing table
    program_id VARCHAR(100) NOT NULL,

    -- Recipient Information
    recipient_user_id UUID NOT NULL,
    recipient_type VARCHAR(20) NOT NULL, -- REFERRER, REFEREE

    -- Reward Details
    reward_type VARCHAR(50) NOT NULL, -- POINTS, CASHBACK, BONUS, COMMISSION
    points_amount BIGINT,
    cashback_amount DECIMAL(15, 2),
    currency VARCHAR(3) DEFAULT 'USD',

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, ISSUED, REDEEMED, EXPIRED, REJECTED

    -- Lifecycle Dates
    earned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP WITH TIME ZONE,
    issued_at TIMESTAMP WITH TIME ZONE,
    redeemed_at TIMESTAMP WITH TIME ZONE,
    expired_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    expiry_date DATE,

    -- Redemption Details
    redemption_method VARCHAR(50), -- WALLET_CREDIT, BANK_TRANSFER, VOUCHER
    redemption_reference VARCHAR(100),
    account_credited VARCHAR(100),
    transaction_id VARCHAR(100),

    -- Rejection
    rejection_reason TEXT,
    rejection_code VARCHAR(50),

    -- Approval Workflow
    requires_approval BOOLEAN DEFAULT FALSE,
    approved_by VARCHAR(100),
    approval_notes TEXT,

    -- Metadata
    reward_metadata JSONB,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referral_reward_program FOREIGN KEY (program_id) REFERENCES referral_programs(program_id) ON DELETE CASCADE,
    CONSTRAINT check_reward_amounts CHECK (
        (reward_type = 'POINTS' AND points_amount > 0) OR
        (reward_type IN ('CASHBACK', 'BONUS') AND cashback_amount > 0)
    )
);

CREATE INDEX idx_referral_rewards_referral ON referral_rewards(referral_id);
CREATE INDEX idx_referral_rewards_recipient ON referral_rewards(recipient_user_id);
CREATE INDEX idx_referral_rewards_program ON referral_rewards(program_id);
CREATE INDEX idx_referral_rewards_status ON referral_rewards(status);
CREATE INDEX idx_referral_rewards_type ON referral_rewards(reward_type);
CREATE INDEX idx_referral_rewards_expiry ON referral_rewards(expiry_date) WHERE status = 'ISSUED';

-- ============================================================================
-- REFERRAL TIERS
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id VARCHAR(100) UNIQUE NOT NULL,
    program_id VARCHAR(100) NOT NULL,

    -- Tier Details
    tier_name VARCHAR(255) NOT NULL,
    tier_level INTEGER NOT NULL,
    description TEXT,

    -- Requirements
    min_referrals INTEGER NOT NULL,
    max_referrals INTEGER,
    min_successful_conversions INTEGER DEFAULT 0,
    min_revenue_generated DECIMAL(18, 2),

    -- Rewards Multiplier
    reward_multiplier DECIMAL(5, 4) NOT NULL DEFAULT 1.0000,
    bonus_points BIGINT,
    bonus_cashback_amount DECIMAL(15, 2),
    bonus_cashback_currency VARCHAR(3) DEFAULT 'USD',

    -- Additional Perks
    additional_benefits TEXT[],
    exclusive_offers TEXT[],
    priority_support BOOLEAN DEFAULT FALSE,

    -- Display
    badge_icon_url TEXT,
    badge_color VARCHAR(7),

    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referral_tier_program FOREIGN KEY (program_id) REFERENCES referral_programs(program_id) ON DELETE CASCADE,
    CONSTRAINT check_tier_level_positive CHECK (tier_level > 0),
    CONSTRAINT check_tier_min_referrals CHECK (min_referrals >= 0),
    CONSTRAINT check_tier_max_referrals CHECK (max_referrals IS NULL OR max_referrals > min_referrals),
    CONSTRAINT unique_program_tier_level UNIQUE (program_id, tier_level)
);

CREATE INDEX idx_referral_tiers_program ON referral_tiers(program_id);
CREATE INDEX idx_referral_tiers_level ON referral_tiers(tier_level);
CREATE INDEX idx_referral_tiers_active ON referral_tiers(is_active) WHERE is_active = TRUE;

-- ============================================================================
-- REFERRAL LEADERBOARD
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_leaderboard (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    program_id VARCHAR(100) NOT NULL,

    -- Period Information
    period_type VARCHAR(20) NOT NULL, -- DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, ALL_TIME
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,

    -- Performance Metrics
    total_referrals INTEGER NOT NULL DEFAULT 0,
    successful_referrals INTEGER DEFAULT 0,
    pending_referrals INTEGER DEFAULT 0,
    conversion_rate DECIMAL(5, 4) DEFAULT 0.0000,

    -- Rewards Earned
    total_points_earned BIGINT DEFAULT 0,
    total_cashback_earned DECIMAL(15, 2) DEFAULT 0.00,
    currency VARCHAR(3) DEFAULT 'USD',

    -- Revenue Impact
    total_revenue_generated DECIMAL(18, 2) DEFAULT 0.00,
    average_revenue_per_referral DECIMAL(15, 2) DEFAULT 0.00,

    -- Ranking
    rank INTEGER,
    previous_rank INTEGER,
    rank_change INTEGER DEFAULT 0,
    tier_level INTEGER,

    -- Streaks
    current_streak_days INTEGER DEFAULT 0,
    longest_streak_days INTEGER DEFAULT 0,

    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referral_leaderboard_program FOREIGN KEY (program_id) REFERENCES referral_programs(program_id) ON DELETE CASCADE,
    CONSTRAINT unique_user_program_period UNIQUE (user_id, program_id, period_type, period_start, period_end),
    CONSTRAINT check_successful_referrals CHECK (successful_referrals <= total_referrals)
);

CREATE INDEX idx_referral_leaderboard_user ON referral_leaderboard(user_id);
CREATE INDEX idx_referral_leaderboard_program ON referral_leaderboard(program_id);
CREATE INDEX idx_referral_leaderboard_period ON referral_leaderboard(period_type, period_end DESC);
CREATE INDEX idx_referral_leaderboard_rank ON referral_leaderboard(program_id, period_type, rank);

-- ============================================================================
-- REFERRAL CAMPAIGNS
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id VARCHAR(100) UNIQUE NOT NULL,
    program_id VARCHAR(100) NOT NULL,

    -- Campaign Details
    campaign_name VARCHAR(255) NOT NULL,
    campaign_type VARCHAR(50) NOT NULL, -- SEASONAL, PROMOTIONAL, CONTEST, CHALLENGE
    description TEXT,

    -- Schedule
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SCHEDULED, ACTIVE, PAUSED, COMPLETED, CANCELLED

    -- Targeting
    target_audience TEXT[],
    target_regions TEXT[],
    target_user_segments TEXT[],

    -- Promotional Details
    promotional_channels TEXT[], -- EMAIL, SMS, PUSH, IN_APP, SOCIAL_MEDIA
    promotional_materials JSONB, -- {email_template_id, sms_template_id, banner_url, etc.}
    landing_page_url TEXT,

    -- Budget and Goals
    budget_amount DECIMAL(15, 2),
    budget_currency VARCHAR(3) DEFAULT 'USD',
    target_referrals INTEGER,
    target_conversions INTEGER,
    target_revenue DECIMAL(18, 2),

    -- Performance Tracking
    total_referrals INTEGER DEFAULT 0,
    successful_referrals INTEGER DEFAULT 0,
    total_clicks INTEGER DEFAULT 0,
    total_rewards_issued DECIMAL(15, 2) DEFAULT 0.00,
    total_revenue_generated DECIMAL(18, 2) DEFAULT 0.00,

    -- ROI Metrics
    roi DECIMAL(10, 4),
    cost_per_acquisition DECIMAL(15, 2),
    customer_lifetime_value DECIMAL(15, 2),

    -- Contest/Challenge Specific
    prize_pool DECIMAL(15, 2),
    winner_count INTEGER,
    winners JSONB, -- Array of winner user_ids and prizes

    -- Audit
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(100),

    CONSTRAINT fk_referral_campaign_program FOREIGN KEY (program_id) REFERENCES referral_programs(program_id) ON DELETE CASCADE,
    CONSTRAINT check_campaign_dates CHECK (end_date IS NULL OR end_date > start_date)
);

CREATE INDEX idx_referral_campaigns_program ON referral_campaigns(program_id);
CREATE INDEX idx_referral_campaigns_status ON referral_campaigns(status);
CREATE INDEX idx_referral_campaigns_dates ON referral_campaigns(start_date, end_date);
CREATE INDEX idx_referral_campaigns_type ON referral_campaigns(campaign_type);

-- ============================================================================
-- REFERRAL MILESTONES
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_id VARCHAR(100) UNIQUE NOT NULL,
    program_id VARCHAR(100) NOT NULL,

    -- Milestone Details
    milestone_name VARCHAR(255) NOT NULL,
    milestone_type VARCHAR(50) NOT NULL, -- REFERRAL_COUNT, CONVERSION_COUNT, REVENUE_TARGET
    description TEXT,

    -- Achievement Criteria
    required_referrals INTEGER,
    required_conversions INTEGER,
    required_revenue DECIMAL(18, 2),
    required_timeframe_days INTEGER,

    -- Rewards
    reward_type VARCHAR(50) NOT NULL, -- POINTS, CASHBACK, BONUS, BADGE, SPECIAL_OFFER
    points_reward BIGINT,
    cashback_reward DECIMAL(15, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    special_offer_description TEXT,
    badge_icon_url TEXT,

    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_repeatable BOOLEAN DEFAULT FALSE,
    cooldown_period_days INTEGER,

    -- Display
    display_order INTEGER DEFAULT 0,
    progress_message_template TEXT,
    completion_message_template TEXT,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referral_milestone_program FOREIGN KEY (program_id) REFERENCES referral_programs(program_id) ON DELETE CASCADE,
    CONSTRAINT check_milestone_criteria CHECK (
        required_referrals IS NOT NULL OR
        required_conversions IS NOT NULL OR
        required_revenue IS NOT NULL
    )
);

CREATE INDEX idx_referral_milestones_program ON referral_milestones(program_id);
CREATE INDEX idx_referral_milestones_active ON referral_milestones(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_referral_milestones_display ON referral_milestones(program_id, display_order);

-- ============================================================================
-- REFERRAL MILESTONE ACHIEVEMENTS
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_milestone_achievements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    achievement_id VARCHAR(100) UNIQUE NOT NULL,
    milestone_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL,

    -- Achievement Details
    achieved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    achievement_data JSONB, -- Snapshot of achievement criteria met

    -- Reward Status
    reward_issued BOOLEAN DEFAULT FALSE,
    reward_id VARCHAR(100),
    reward_issued_at TIMESTAMP WITH TIME ZONE,

    -- Notification
    user_notified BOOLEAN DEFAULT FALSE,
    notified_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_milestone_achievement FOREIGN KEY (milestone_id) REFERENCES referral_milestones(milestone_id) ON DELETE CASCADE
);

CREATE INDEX idx_milestone_achievements_milestone ON referral_milestone_achievements(milestone_id);
CREATE INDEX idx_milestone_achievements_user ON referral_milestone_achievements(user_id);
CREATE INDEX idx_milestone_achievements_achieved ON referral_milestone_achievements(achieved_at DESC);
CREATE INDEX idx_milestone_achievements_reward ON referral_milestone_achievements(reward_id) WHERE reward_id IS NOT NULL;

-- ============================================================================
-- REFERRAL STATISTICS (Aggregated)
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id VARCHAR(100) NOT NULL,

    -- Period
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,

    -- Referral Metrics
    total_referrals INTEGER NOT NULL DEFAULT 0,
    pending_referrals INTEGER DEFAULT 0,
    successful_referrals INTEGER DEFAULT 0,
    rejected_referrals INTEGER DEFAULT 0,
    expired_referrals INTEGER DEFAULT 0,

    -- Conversion Metrics
    total_clicks INTEGER DEFAULT 0,
    unique_clicks INTEGER DEFAULT 0,
    total_signups INTEGER DEFAULT 0,
    conversion_rate DECIMAL(5, 4) DEFAULT 0.0000,
    click_to_signup_rate DECIMAL(5, 4) DEFAULT 0.0000,
    signup_to_conversion_rate DECIMAL(5, 4) DEFAULT 0.0000,

    -- Financial Metrics
    total_rewards_issued DECIMAL(18, 2) DEFAULT 0.00,
    total_rewards_redeemed DECIMAL(18, 2) DEFAULT 0.00,
    total_rewards_pending DECIMAL(18, 2) DEFAULT 0.00,
    total_rewards_expired DECIMAL(18, 2) DEFAULT 0.00,
    average_reward_amount DECIMAL(15, 2) DEFAULT 0.00,
    total_revenue_generated DECIMAL(18, 2) DEFAULT 0.00,

    -- Breakdown by Channel
    by_channel JSONB, -- {EMAIL: {clicks: X, conversions: Y}, SMS: {...}, etc.}
    by_source JSONB, -- {ORGANIC: X, PAID: Y, etc.}
    by_region JSONB, -- {US: X, UK: Y, etc.}

    -- Top Performers
    top_referrers JSONB, -- [{user_id, referrals, rewards}, ...]

    -- Engagement Metrics
    average_time_to_conversion_hours DECIMAL(10, 2),
    bounce_rate DECIMAL(5, 4),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referral_statistics_program FOREIGN KEY (program_id) REFERENCES referral_programs(program_id) ON DELETE CASCADE,
    CONSTRAINT unique_program_period UNIQUE (program_id, period_start, period_end)
);

CREATE INDEX idx_referral_statistics_program ON referral_statistics(program_id);
CREATE INDEX idx_referral_statistics_period ON referral_statistics(period_end DESC);

-- ============================================================================
-- REFERRAL FRAUD DETECTION
-- ============================================================================
CREATE TABLE IF NOT EXISTS referral_fraud_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id VARCHAR(100) UNIQUE NOT NULL,
    referral_id VARCHAR(100) NOT NULL,

    -- Check Details
    check_type VARCHAR(50) NOT NULL, -- DUPLICATE_IP, SELF_REFERRAL, VELOCITY, PATTERN_MATCH
    check_status VARCHAR(20) NOT NULL, -- PASSED, FAILED, SUSPICIOUS, REVIEW_REQUIRED
    risk_score DECIMAL(5, 2), -- 0.00 to 100.00

    -- Detection Details
    fraud_indicators JSONB,
    detection_rules_triggered TEXT[],

    -- Resolution
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_decision VARCHAR(20), -- APPROVED, REJECTED, PENDING
    review_notes TEXT,

    -- Actions Taken
    action_taken VARCHAR(50), -- BLOCKED, FLAGGED, ALLOWED, MANUAL_REVIEW
    automated_action BOOLEAN DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_risk_score_range CHECK (risk_score >= 0 AND risk_score <= 100)
);

CREATE INDEX idx_fraud_checks_referral ON referral_fraud_checks(referral_id);
CREATE INDEX idx_fraud_checks_status ON referral_fraud_checks(check_status);
CREATE INDEX idx_fraud_checks_review ON referral_fraud_checks(review_decision) WHERE review_decision = 'PENDING';

-- ============================================================================
-- UPDATE TRIGGERS
-- ============================================================================

-- Trigger function for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers to all tables
CREATE TRIGGER update_referral_programs_timestamp BEFORE UPDATE ON referral_programs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_referral_links_timestamp BEFORE UPDATE ON referral_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_referral_rewards_timestamp BEFORE UPDATE ON referral_rewards
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_referral_tiers_timestamp BEFORE UPDATE ON referral_tiers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_referral_campaigns_timestamp BEFORE UPDATE ON referral_campaigns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_referral_milestones_timestamp BEFORE UPDATE ON referral_milestones
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_referral_statistics_timestamp BEFORE UPDATE ON referral_statistics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE referral_programs IS 'Master table for referral program configurations';
COMMENT ON TABLE referral_links IS 'Personalized referral links with tracking';
COMMENT ON TABLE referral_clicks IS 'Detailed click analytics for referral links';
COMMENT ON TABLE referral_rewards IS 'Individual reward transactions for referrals';
COMMENT ON TABLE referral_tiers IS 'Tiered reward structure for high-performing referrers';
COMMENT ON TABLE referral_leaderboard IS 'Leaderboard rankings for competitive referral programs';
COMMENT ON TABLE referral_campaigns IS 'Time-bound marketing campaigns for referrals';
COMMENT ON TABLE referral_milestones IS 'Achievement milestones for referral programs';
COMMENT ON TABLE referral_milestone_achievements IS 'User milestone achievement records';
COMMENT ON TABLE referral_statistics IS 'Aggregated statistics for program performance';
COMMENT ON TABLE referral_fraud_checks IS 'Fraud detection and prevention records';
