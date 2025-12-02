-- Gamification Service Database Schema
-- Creates tables for rewards, points, badges, achievements, and challenges

-- User points tracking
CREATE TABLE user_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    point_type VARCHAR(50) NOT NULL DEFAULT 'TRANSACTION' CHECK (point_type IN ('TRANSACTION', 'REFERRAL', 'MILESTONE', 'BONUS', 'CHALLENGE')),
    
    -- Points details
    points_earned INTEGER NOT NULL CHECK (points_earned > 0),
    points_current INTEGER NOT NULL DEFAULT 0,
    points_lifetime INTEGER NOT NULL DEFAULT 0,
    
    -- Multipliers and bonuses
    multiplier DECIMAL(3,2) DEFAULT 1.00,
    bonus_applied BOOLEAN DEFAULT FALSE,
    
    -- Point expiration
    expires_at TIMESTAMP WITH TIME ZONE,
    expired_points INTEGER DEFAULT 0,
    
    -- Source tracking
    source_transaction_id UUID,
    source_activity VARCHAR(100),
    source_reference VARCHAR(255),
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(user_id, point_type)
);

-- Point transactions for detailed tracking
CREATE TABLE point_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('EARNED', 'REDEEMED', 'EXPIRED', 'ADJUSTED')),
    
    -- Points movement
    points_amount INTEGER NOT NULL,
    points_balance_after INTEGER NOT NULL,
    
    -- Transaction details
    description TEXT NOT NULL,
    category VARCHAR(50),
    
    -- Source reference
    source_transaction_id UUID,
    source_activity VARCHAR(100),
    reference_id VARCHAR(255),
    
    -- Processing
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- Badge definitions
CREATE TABLE badges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(200) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    
    -- Badge appearance
    icon_url VARCHAR(500),
    color VARCHAR(7), -- Hex color
    rarity VARCHAR(20) DEFAULT 'COMMON' CHECK (rarity IN ('COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY')),
    
    -- Requirements
    criteria JSONB NOT NULL, -- JSON criteria for earning badge
    points_reward INTEGER DEFAULT 0,
    
    -- Badge settings
    is_active BOOLEAN DEFAULT TRUE,
    is_one_time BOOLEAN DEFAULT TRUE, -- Can only be earned once
    category VARCHAR(50),
    
    -- Display order
    sort_order INTEGER DEFAULT 0,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- User badge achievements
CREATE TABLE user_badges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    badge_id UUID NOT NULL REFERENCES badges(id),
    
    -- Achievement details
    earned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    progress_data JSONB, -- Progress towards badge requirements
    
    -- Badge instance data
    points_earned INTEGER DEFAULT 0,
    achievement_level INTEGER DEFAULT 1,
    
    -- Display settings
    is_displayed BOOLEAN DEFAULT TRUE,
    is_featured BOOLEAN DEFAULT FALSE,
    
    UNIQUE(user_id, badge_id, achievement_level)
);

-- Challenges and missions
CREATE TABLE challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    
    -- Challenge details
    challenge_type VARCHAR(20) NOT NULL CHECK (challenge_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'LIMITED_TIME', 'MILESTONE')),
    difficulty VARCHAR(20) DEFAULT 'MEDIUM' CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    
    -- Requirements and rewards
    requirements JSONB NOT NULL, -- JSON requirements to complete
    points_reward INTEGER NOT NULL CHECK (points_reward > 0),
    badge_reward_id UUID REFERENCES badges(id),
    
    -- Timing
    start_date DATE,
    end_date DATE,
    duration_days INTEGER,
    
    -- Limits
    max_participants INTEGER,
    current_participants INTEGER DEFAULT 0,
    
    -- Status
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    is_featured BOOLEAN DEFAULT FALSE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- User challenge participation
CREATE TABLE user_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    challenge_id UUID NOT NULL REFERENCES challenges(id),
    
    -- Participation status
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'ABANDONED')),
    
    -- Progress tracking
    progress_data JSONB, -- Current progress towards requirements
    progress_percentage DECIMAL(5,2) DEFAULT 0.00 CHECK (progress_percentage >= 0.00 AND progress_percentage <= 100.00),
    
    -- Completion details
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    points_earned INTEGER DEFAULT 0,
    
    -- Streak tracking
    current_streak INTEGER DEFAULT 0,
    best_streak INTEGER DEFAULT 0,
    last_activity_at TIMESTAMP WITH TIME ZONE,
    
    UNIQUE(user_id, challenge_id)
);

-- Leaderboards
CREATE TABLE leaderboards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    leaderboard_type VARCHAR(20) NOT NULL CHECK (leaderboard_type IN ('POINTS', 'TRANSACTIONS', 'REFERRALS', 'CHALLENGES')),
    
    -- Leaderboard scope
    scope VARCHAR(20) NOT NULL DEFAULT 'GLOBAL' CHECK (scope IN ('GLOBAL', 'FRIENDS', 'REGION')),
    time_period VARCHAR(20) NOT NULL DEFAULT 'ALL_TIME' CHECK (time_period IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'ALL_TIME')),
    
    -- Settings
    max_entries INTEGER DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    is_public BOOLEAN DEFAULT TRUE,
    
    -- Period tracking
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Leaderboard entries
CREATE TABLE leaderboard_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    leaderboard_id UUID NOT NULL REFERENCES leaderboards(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    
    -- Ranking
    rank_position INTEGER NOT NULL,
    score DECIMAL(15,2) NOT NULL,
    previous_rank INTEGER,
    rank_change INTEGER DEFAULT 0,
    
    -- Entry metadata
    entry_data JSONB, -- Additional data specific to leaderboard type
    
    -- Timing
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(leaderboard_id, user_id)
);

-- Reward redemptions
CREATE TABLE reward_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    
    -- Redemption details
    reward_type VARCHAR(50) NOT NULL,
    reward_name VARCHAR(255) NOT NULL,
    reward_description TEXT,
    
    -- Costs and values
    points_cost INTEGER NOT NULL CHECK (points_cost > 0),
    cash_value DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Status
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    
    -- Processing
    redeemed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    fulfilled_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- References
    external_reference VARCHAR(255),
    fulfillment_reference VARCHAR(255),
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_user_points_user_id ON user_points(user_id);
CREATE INDEX idx_user_points_point_type ON user_points(point_type);
CREATE INDEX idx_user_points_expires_at ON user_points(expires_at) WHERE expires_at IS NOT NULL;

CREATE INDEX idx_point_transactions_user_id ON point_transactions(user_id);
CREATE INDEX idx_point_transactions_type ON point_transactions(transaction_type);
CREATE INDEX idx_point_transactions_processed_at ON point_transactions(processed_at);
CREATE INDEX idx_point_transactions_source_transaction ON point_transactions(source_transaction_id) WHERE source_transaction_id IS NOT NULL;

CREATE INDEX idx_badges_active ON badges(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_badges_category ON badges(category);
CREATE INDEX idx_badges_rarity ON badges(rarity);

CREATE INDEX idx_user_badges_user_id ON user_badges(user_id);
CREATE INDEX idx_user_badges_badge_id ON user_badges(badge_id);
CREATE INDEX idx_user_badges_earned_at ON user_badges(earned_at);

CREATE INDEX idx_challenges_status ON challenges(status);
CREATE INDEX idx_challenges_type ON challenges(challenge_type);
CREATE INDEX idx_challenges_dates ON challenges(start_date, end_date);

CREATE INDEX idx_user_challenges_user_id ON user_challenges(user_id);
CREATE INDEX idx_user_challenges_challenge_id ON user_challenges(challenge_id);
CREATE INDEX idx_user_challenges_status ON user_challenges(status);
CREATE INDEX idx_user_challenges_completed_at ON user_challenges(completed_at);

CREATE INDEX idx_leaderboard_entries_leaderboard_id ON leaderboard_entries(leaderboard_id);
CREATE INDEX idx_leaderboard_entries_rank ON leaderboard_entries(leaderboard_id, rank_position);
CREATE INDEX idx_leaderboard_entries_score ON leaderboard_entries(leaderboard_id, score DESC);

CREATE INDEX idx_reward_redemptions_user_id ON reward_redemptions(user_id);
CREATE INDEX idx_reward_redemptions_status ON reward_redemptions(status);
CREATE INDEX idx_reward_redemptions_redeemed_at ON reward_redemptions(redeemed_at);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_points_updated_at BEFORE UPDATE
    ON user_points FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_badges_updated_at BEFORE UPDATE
    ON badges FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_challenges_updated_at BEFORE UPDATE
    ON challenges FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_leaderboards_updated_at BEFORE UPDATE
    ON leaderboards FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_reward_redemptions_updated_at BEFORE UPDATE
    ON reward_redemptions FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Function to update leaderboard entries count
CREATE OR REPLACE FUNCTION update_challenge_participants()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE challenges 
        SET current_participants = current_participants + 1
        WHERE id = NEW.challenge_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE challenges 
        SET current_participants = current_participants - 1
        WHERE id = OLD.challenge_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_challenge_on_participant_change
    AFTER INSERT OR DELETE ON user_challenges
    FOR EACH ROW EXECUTE PROCEDURE update_challenge_participants();

-- Comments for documentation
COMMENT ON TABLE user_points IS 'User points tracking for gamification';
COMMENT ON TABLE point_transactions IS 'Detailed history of point earning and spending';
COMMENT ON TABLE badges IS 'Badge definitions and metadata';
COMMENT ON TABLE user_badges IS 'User badge achievements and progress';
COMMENT ON TABLE challenges IS 'Challenges and missions for users to complete';
COMMENT ON TABLE user_challenges IS 'User participation in challenges';
COMMENT ON TABLE leaderboards IS 'Leaderboard configurations';
COMMENT ON TABLE leaderboard_entries IS 'User entries in leaderboards';
COMMENT ON TABLE reward_redemptions IS 'User reward redemption history';