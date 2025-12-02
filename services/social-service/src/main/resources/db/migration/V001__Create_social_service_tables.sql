-- Social Service Database Schema
-- Version: 1.0.0
-- Description: Complete social networking features for P2P payments

-- Social Connections table
CREATE TABLE social_connections (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    connected_user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONNECTED', 'BLOCKED', 'DECLINED', 'SUSPENDED')),
    connection_type VARCHAR(20) NOT NULL CHECK (connection_type IN ('FRIEND', 'FAMILY', 'COLLEAGUE', 'BUSINESS', 'ACQUAINTANCE')),
    privacy_level VARCHAR(20) DEFAULT 'FRIENDS' CHECK (privacy_level IN ('PUBLIC', 'FRIENDS', 'PRIVATE', 'CUSTOM')),
    
    -- Request details
    request_message TEXT,
    acceptance_message TEXT,
    requested_at TIMESTAMP,
    connected_at TIMESTAMP,
    blocked_at TIMESTAMP,
    
    -- Permissions and limits
    can_send_money BOOLEAN DEFAULT TRUE NOT NULL,
    can_request_money BOOLEAN DEFAULT TRUE NOT NULL,
    can_view_activity BOOLEAN DEFAULT TRUE NOT NULL,
    transaction_limit DECIMAL(19,2) DEFAULT 1000.00,
    monthly_limit DECIMAL(19,2) DEFAULT 5000.00,
    
    -- Connection statistics
    total_transactions INTEGER DEFAULT 0,
    total_amount_sent DECIMAL(19,2) DEFAULT 0.00,
    total_amount_received DECIMAL(19,2) DEFAULT 0.00,
    first_transaction_at TIMESTAMP,
    last_transaction_at TIMESTAMP,
    
    -- Trust and relationship metrics
    trust_score DECIMAL(3,2) DEFAULT 0.50 CHECK (trust_score BETWEEN 0 AND 1),
    interaction_frequency VARCHAR(20) DEFAULT 'LOW' CHECK (interaction_frequency IN ('HIGH', 'MEDIUM', 'LOW', 'RARE')),
    relationship_strength DECIMAL(3,2) DEFAULT 0.30 CHECK (relationship_strength BETWEEN 0 AND 1),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(user_id, connected_user_id)
);

-- Social Payments table
CREATE TABLE social_payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(50) UNIQUE NOT NULL,
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) DEFAULT 'USD',
    payment_type VARCHAR(20) NOT NULL CHECK (payment_type IN ('SEND', 'REQUEST', 'SPLIT', 'GROUP', 'GIFT', 'CHARITY')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'REQUESTED', 'APPROVED', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED', 'DECLINED')),
    
    -- Social features
    message TEXT,
    emoji VARCHAR(10),
    is_public BOOLEAN DEFAULT FALSE NOT NULL,
    visibility VARCHAR(20) DEFAULT 'FRIENDS' CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE', 'CUSTOM')),
    media_attachments TEXT[], -- URLs to images/videos
    tags TEXT[],
    location JSONB,
    occasion VARCHAR(50),
    
    -- Payment processing details
    transaction_id VARCHAR(50),
    source_method VARCHAR(30),
    destination_method VARCHAR(30),
    processing_fee DECIMAL(19,2) DEFAULT 0.00,
    exchange_rate DECIMAL(10,6),
    initiated_via VARCHAR(20) CHECK (initiated_via IN ('APP', 'WEB', 'SMS', 'EMAIL', 'QR_CODE', 'NFC', 'VOICE')),
    
    -- Request specific fields
    request_expires_at TIMESTAMP,
    requested_at TIMESTAMP,
    approved_at TIMESTAMP,
    
    -- Split payment details
    split_details JSONB,
    split_type VARCHAR(20) CHECK (split_type IN ('EQUAL', 'CUSTOM', 'PERCENTAGE', 'RATIO')),
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,
    
    -- Metadata
    metadata JSONB
);

-- Social Groups table
CREATE TABLE social_groups (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    group_description TEXT,
    group_code VARCHAR(20) UNIQUE NOT NULL,
    creator_id UUID NOT NULL,
    group_type VARCHAR(20) NOT NULL CHECK (group_type IN ('FRIENDS', 'FAMILY', 'COLLEAGUES', 'CLUB', 'BUSINESS', 'COMMUNITY', 'EVENT')),
    privacy_level VARCHAR(20) DEFAULT 'PRIVATE' CHECK (privacy_level IN ('PUBLIC', 'PRIVATE', 'INVITE_ONLY', 'SECRET')),
    
    -- Group settings
    max_members INTEGER DEFAULT 50 CHECK (max_members > 0),
    current_members INTEGER DEFAULT 0 CHECK (current_members >= 0),
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    allow_member_invites BOOLEAN DEFAULT TRUE NOT NULL,
    allow_group_payments BOOLEAN DEFAULT TRUE NOT NULL,
    allow_bill_splitting BOOLEAN DEFAULT TRUE NOT NULL,
    require_approval_for_join BOOLEAN DEFAULT FALSE NOT NULL,
    
    -- Payment settings
    default_split_type VARCHAR(20) DEFAULT 'EQUAL',
    group_spending_limit DECIMAL(19,2),
    monthly_spending_limit DECIMAL(19,2),
    
    -- Visual and content
    group_image_url TEXT,
    group_banner_url TEXT,
    tags TEXT[],
    location JSONB,
    
    -- Group statistics
    total_payments DECIMAL(19,2) DEFAULT 0.00,
    total_transactions INTEGER DEFAULT 0,
    last_activity_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Social Group Members table
CREATE TABLE social_group_members (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT REFERENCES social_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'MODERATOR', 'MEMBER', 'GUEST')),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'INVITED', 'BANNED', 'LEFT')),
    
    -- Member permissions
    can_invite_members BOOLEAN DEFAULT FALSE NOT NULL,
    can_manage_payments BOOLEAN DEFAULT FALSE NOT NULL,
    can_view_financial_summary BOOLEAN DEFAULT TRUE NOT NULL,
    can_initiate_group_payments BOOLEAN DEFAULT TRUE NOT NULL,
    can_approve_expenses BOOLEAN DEFAULT FALSE NOT NULL,
    
    -- Member statistics
    total_contributions DECIMAL(19,2) DEFAULT 0.00,
    total_payments_received DECIMAL(19,2) DEFAULT 0.00,
    transactions_count INTEGER DEFAULT 0,
    
    -- Timestamps
    invited_at TIMESTAMP,
    joined_at TIMESTAMP,
    left_at TIMESTAMP,
    invited_by UUID,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    UNIQUE(group_id, user_id)
);

-- Social Feed table
CREATE TABLE social_feed (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    activity_id BIGINT,
    activity_type VARCHAR(30) NOT NULL CHECK (activity_type IN ('PAYMENT_SENT', 'PAYMENT_RECEIVED', 'PAYMENT_REQUESTED', 'BILL_SPLIT', 'GROUP_PAYMENT', 'FRIEND_ADDED', 'GROUP_JOINED', 'CHALLENGE_COMPLETED', 'ACHIEVEMENT_EARNED')),
    
    -- Content
    title VARCHAR(200) NOT NULL,
    description TEXT,
    amount DECIMAL(19,2),
    currency VARCHAR(3),
    emoji VARCHAR(10),
    
    -- Social interaction
    participants UUID[],
    mentioned_users UUID[],
    media_urls TEXT[],
    tags TEXT[],
    location JSONB,
    
    -- Visibility and privacy
    visibility VARCHAR(20) DEFAULT 'FRIENDS' CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE', 'CUSTOM')),
    audience UUID[], -- Specific users who can see this
    
    -- Engagement metrics
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    views_count INTEGER DEFAULT 0,
    
    -- Content flags
    is_pinned BOOLEAN DEFAULT FALSE NOT NULL,
    is_promoted BOOLEAN DEFAULT FALSE NOT NULL,
    is_sensitive BOOLEAN DEFAULT FALSE NOT NULL,
    is_reported BOOLEAN DEFAULT FALSE NOT NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Social Feed Interactions table
CREATE TABLE social_feed_interactions (
    id BIGSERIAL PRIMARY KEY,
    feed_id BIGINT REFERENCES social_feed(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    interaction_type VARCHAR(20) NOT NULL CHECK (interaction_type IN ('LIKE', 'COMMENT', 'SHARE', 'VIEW', 'REACT')),
    reaction_type VARCHAR(20) CHECK (reaction_type IN ('LIKE', 'LOVE', 'LAUGH', 'WOW', 'ANGRY', 'CELEBRATE')),
    comment_text TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    UNIQUE(feed_id, user_id, interaction_type)
);

-- Social Challenges table
CREATE TABLE social_challenges (
    id BIGSERIAL PRIMARY KEY,
    challenge_name VARCHAR(100) NOT NULL,
    challenge_description TEXT,
    creator_id UUID NOT NULL,
    challenge_type VARCHAR(30) NOT NULL CHECK (challenge_type IN ('SAVINGS', 'SPENDING', 'SOCIAL', 'CHARITY', 'EDUCATIONAL', 'STREAK', 'MILESTONE')),
    difficulty_level VARCHAR(20) DEFAULT 'MEDIUM' CHECK (difficulty_level IN ('EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    
    -- Challenge goals
    target_amount DECIMAL(19,2),
    target_transactions INTEGER,
    target_days INTEGER,
    completion_criteria JSONB,
    
    -- Timing
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    duration_days INTEGER,
    
    -- Participation
    is_public BOOLEAN DEFAULT TRUE NOT NULL,
    max_participants INTEGER,
    current_participants INTEGER DEFAULT 0,
    min_participants INTEGER DEFAULT 1,
    
    -- Financial aspects
    entry_fee DECIMAL(19,2) DEFAULT 0.00,
    prize_pool DECIMAL(19,2) DEFAULT 0.00,
    reward_structure JSONB,
    sponsor_id UUID,
    sponsor_info JSONB,
    
    -- Challenge rules and content
    rules TEXT[],
    tags TEXT[],
    category VARCHAR(50),
    image_url TEXT,
    
    -- Status and metrics
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    completion_rate DECIMAL(5,2) DEFAULT 0.00,
    average_progress DECIMAL(5,2) DEFAULT 0.00,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Social Challenge Participants table
CREATE TABLE social_challenge_participants (
    id BIGSERIAL PRIMARY KEY,
    challenge_id BIGINT REFERENCES social_challenges(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'WITHDRAWN', 'DISQUALIFIED')),
    
    -- Progress tracking
    current_progress DECIMAL(19,2) DEFAULT 0.00,
    target_progress DECIMAL(19,2),
    progress_percentage DECIMAL(5,2) DEFAULT 0.00,
    transaction_count INTEGER DEFAULT 0,
    
    -- Achievements
    milestones_reached INTEGER DEFAULT 0,
    badges_earned TEXT[],
    rank_position INTEGER,
    points_earned INTEGER DEFAULT 0,
    
    -- Financial tracking
    entry_fee_paid DECIMAL(19,2) DEFAULT 0.00,
    potential_winnings DECIMAL(19,2) DEFAULT 0.00,
    actual_winnings DECIMAL(19,2) DEFAULT 0.00,
    
    -- Timestamps
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Metadata
    participation_metadata JSONB,
    
    UNIQUE(challenge_id, user_id)
);

-- Social Recommendations table
CREATE TABLE social_recommendations (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    recommended_user_id UUID,
    recommended_group_id BIGINT REFERENCES social_groups(id) ON DELETE CASCADE,
    recommended_challenge_id BIGINT REFERENCES social_challenges(id) ON DELETE CASCADE,
    recommendation_type VARCHAR(30) NOT NULL CHECK (recommendation_type IN ('FRIEND_SUGGESTION', 'GROUP_SUGGESTION', 'CHALLENGE_SUGGESTION', 'MERCHANT_SUGGESTION', 'CONTENT_SUGGESTION')),
    
    -- Recommendation details
    reason TEXT,
    confidence_score DECIMAL(3,2) CHECK (confidence_score BETWEEN 0 AND 1),
    recommendation_source VARCHAR(30) CHECK (recommendation_source IN ('MUTUAL_FRIENDS', 'COMMON_INTERESTS', 'LOCATION', 'TRANSACTION_HISTORY', 'ML_ALGORITHM', 'MANUAL')),
    
    -- Interaction tracking
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    was_accepted BOOLEAN DEFAULT FALSE NOT NULL,
    was_dismissed BOOLEAN DEFAULT FALSE NOT NULL,
    view_count INTEGER DEFAULT 0,
    
    -- Timing
    expires_at TIMESTAMP,
    accepted_at TIMESTAMP,
    dismissed_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Social Profiles table
CREATE TABLE social_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL,
    display_name VARCHAR(100),
    bio TEXT,
    profile_image_url TEXT,
    cover_image_url TEXT,
    
    -- Social stats
    connection_count INTEGER DEFAULT 0,
    group_count INTEGER DEFAULT 0,
    challenge_count INTEGER DEFAULT 0,
    achievement_count INTEGER DEFAULT 0,
    
    -- Social scores and metrics
    social_score INTEGER DEFAULT 0,
    trust_rating DECIMAL(3,2) DEFAULT 0.00,
    activity_level VARCHAR(20) DEFAULT 'LOW' CHECK (activity_level IN ('VERY_HIGH', 'HIGH', 'MEDIUM', 'LOW', 'INACTIVE')),
    influence_score INTEGER DEFAULT 0,
    
    -- Privacy settings
    profile_visibility VARCHAR(20) DEFAULT 'FRIENDS' CHECK (profile_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')),
    activity_visibility VARCHAR(20) DEFAULT 'FRIENDS' CHECK (activity_visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE')),
    allow_friend_requests BOOLEAN DEFAULT TRUE NOT NULL,
    allow_group_invites BOOLEAN DEFAULT TRUE NOT NULL,
    allow_challenge_invites BOOLEAN DEFAULT TRUE NOT NULL,
    
    -- Preferences
    preferred_privacy_level VARCHAR(20) DEFAULT 'FRIENDS',
    notification_preferences JSONB,
    social_preferences JSONB,
    
    -- Verification and trust
    is_verified BOOLEAN DEFAULT FALSE NOT NULL,
    verification_level VARCHAR(20) DEFAULT 'NONE' CHECK (verification_level IN ('NONE', 'EMAIL', 'PHONE', 'IDENTITY', 'BUSINESS')),
    trust_badges TEXT[],
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Social Achievements table
CREATE TABLE social_achievements (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    achievement_type VARCHAR(30) NOT NULL CHECK (achievement_type IN ('FIRST_PAYMENT', 'FIRST_FRIEND', 'PAYMENT_STREAK', 'SOCIAL_BUTTERFLY', 'GROUP_CREATOR', 'CHALLENGE_CHAMPION', 'TRUST_BUILDER', 'EARLY_ADOPTER')),
    achievement_name VARCHAR(100) NOT NULL,
    achievement_description TEXT,
    
    -- Achievement details
    criteria_met JSONB,
    points_awarded INTEGER DEFAULT 0,
    badge_image_url TEXT,
    rarity_level VARCHAR(20) DEFAULT 'COMMON' CHECK (rarity_level IN ('COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'LEGENDARY')),
    
    -- Achievement progress
    progress_current INTEGER DEFAULT 0,
    progress_target INTEGER DEFAULT 1,
    is_completed BOOLEAN DEFAULT FALSE NOT NULL,
    is_claimed BOOLEAN DEFAULT FALSE NOT NULL,
    
    -- Timestamps
    earned_at TIMESTAMP,
    claimed_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    UNIQUE(user_id, achievement_type)
);

-- Social Content Moderation table
CREATE TABLE social_content_moderation (
    id BIGSERIAL PRIMARY KEY,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('FEED_POST', 'COMMENT', 'MESSAGE', 'GROUP_POST', 'CHALLENGE_POST', 'PROFILE')),
    content_id BIGINT NOT NULL,
    reported_by UUID NOT NULL,
    content_owner_id UUID NOT NULL,
    
    -- Report details
    report_reason VARCHAR(50) NOT NULL CHECK (report_reason IN ('SPAM', 'INAPPROPRIATE', 'HARASSMENT', 'FRAUD', 'VIOLENCE', 'HATE_SPEECH', 'COPYRIGHT', 'OTHER')),
    report_description TEXT,
    evidence_urls TEXT[],
    
    -- Moderation action
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWED', 'APPROVED', 'REMOVED', 'WARNING_ISSUED', 'USER_SUSPENDED')),
    moderator_id UUID,
    moderation_action VARCHAR(50),
    moderation_notes TEXT,
    
    -- AI moderation
    ai_confidence_score DECIMAL(3,2),
    ai_flags TEXT[],
    requires_human_review BOOLEAN DEFAULT TRUE NOT NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
);

-- Social Analytics Events table
CREATE TABLE social_analytics_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(30) NOT NULL CHECK (event_category IN ('SOCIAL', 'PAYMENT', 'ENGAGEMENT', 'CONTENT', 'NAVIGATION')),
    event_properties JSONB,
    
    -- Context
    session_id VARCHAR(50),
    device_type VARCHAR(20),
    platform VARCHAR(20),
    app_version VARCHAR(20),
    
    -- Metrics
    duration_ms INTEGER,
    value_amount DECIMAL(19,2),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Create indexes for performance
CREATE INDEX idx_social_connections_user_id ON social_connections(user_id);
CREATE INDEX idx_social_connections_connected_user_id ON social_connections(connected_user_id);
CREATE INDEX idx_social_connections_status ON social_connections(status);
CREATE INDEX idx_social_connections_created_at ON social_connections(created_at);

CREATE INDEX idx_social_payments_sender_id ON social_payments(sender_id);
CREATE INDEX idx_social_payments_recipient_id ON social_payments(recipient_id);
CREATE INDEX idx_social_payments_status ON social_payments(status);
CREATE INDEX idx_social_payments_payment_type ON social_payments(payment_type);
CREATE INDEX idx_social_payments_created_at ON social_payments(created_at);
CREATE INDEX idx_social_payments_is_public ON social_payments(is_public);

CREATE INDEX idx_social_groups_creator_id ON social_groups(creator_id);
CREATE INDEX idx_social_groups_group_type ON social_groups(group_type);
CREATE INDEX idx_social_groups_privacy_level ON social_groups(privacy_level);
CREATE INDEX idx_social_groups_is_active ON social_groups(is_active);
CREATE INDEX idx_social_groups_created_at ON social_groups(created_at);

CREATE INDEX idx_social_group_members_group_id ON social_group_members(group_id);
CREATE INDEX idx_social_group_members_user_id ON social_group_members(user_id);
CREATE INDEX idx_social_group_members_status ON social_group_members(status);

CREATE INDEX idx_social_feed_user_id ON social_feed(user_id);
CREATE INDEX idx_social_feed_activity_type ON social_feed(activity_type);
CREATE INDEX idx_social_feed_visibility ON social_feed(visibility);
CREATE INDEX idx_social_feed_created_at ON social_feed(created_at);
CREATE INDEX idx_social_feed_participants ON social_feed USING GIN(participants);

CREATE INDEX idx_social_feed_interactions_feed_id ON social_feed_interactions(feed_id);
CREATE INDEX idx_social_feed_interactions_user_id ON social_feed_interactions(user_id);
CREATE INDEX idx_social_feed_interactions_type ON social_feed_interactions(interaction_type);

CREATE INDEX idx_social_challenges_creator_id ON social_challenges(creator_id);
CREATE INDEX idx_social_challenges_challenge_type ON social_challenges(challenge_type);
CREATE INDEX idx_social_challenges_status ON social_challenges(status);
CREATE INDEX idx_social_challenges_is_public ON social_challenges(is_public);
CREATE INDEX idx_social_challenges_start_date ON social_challenges(start_date);
CREATE INDEX idx_social_challenges_end_date ON social_challenges(end_date);

CREATE INDEX idx_social_challenge_participants_challenge_id ON social_challenge_participants(challenge_id);
CREATE INDEX idx_social_challenge_participants_user_id ON social_challenge_participants(user_id);
CREATE INDEX idx_social_challenge_participants_status ON social_challenge_participants(status);

CREATE INDEX idx_social_recommendations_user_id ON social_recommendations(user_id);
CREATE INDEX idx_social_recommendations_type ON social_recommendations(recommendation_type);
CREATE INDEX idx_social_recommendations_is_active ON social_recommendations(is_active);
CREATE INDEX idx_social_recommendations_confidence ON social_recommendations(confidence_score);

CREATE INDEX idx_social_profiles_user_id ON social_profiles(user_id);
CREATE INDEX idx_social_profiles_social_score ON social_profiles(social_score);
CREATE INDEX idx_social_profiles_activity_level ON social_profiles(activity_level);

CREATE INDEX idx_social_achievements_user_id ON social_achievements(user_id);
CREATE INDEX idx_social_achievements_type ON social_achievements(achievement_type);
CREATE INDEX idx_social_achievements_completed ON social_achievements(is_completed);
CREATE INDEX idx_social_achievements_earned_at ON social_achievements(earned_at);

CREATE INDEX idx_social_content_moderation_content_type ON social_content_moderation(content_type);
CREATE INDEX idx_social_content_moderation_content_id ON social_content_moderation(content_id);
CREATE INDEX idx_social_content_moderation_status ON social_content_moderation(status);
CREATE INDEX idx_social_content_moderation_reported_by ON social_content_moderation(reported_by);

CREATE INDEX idx_social_analytics_events_user_id ON social_analytics_events(user_id);
CREATE INDEX idx_social_analytics_events_event_type ON social_analytics_events(event_type);
CREATE INDEX idx_social_analytics_events_event_category ON social_analytics_events(event_category);
CREATE INDEX idx_social_analytics_events_created_at ON social_analytics_events(created_at);

-- Create GIN indexes for JSONB columns
CREATE INDEX idx_social_payments_metadata ON social_payments USING GIN(metadata);
CREATE INDEX idx_social_payments_split_details ON social_payments USING GIN(split_details);
CREATE INDEX idx_social_feed_location ON social_feed USING GIN(location);
CREATE INDEX idx_social_groups_location ON social_groups USING GIN(location);
CREATE INDEX idx_social_analytics_event_properties ON social_analytics_events USING GIN(event_properties);

-- Create triggers for updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_social_connections_updated_at BEFORE UPDATE ON social_connections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_social_groups_updated_at BEFORE UPDATE ON social_groups
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_social_feed_updated_at BEFORE UPDATE ON social_feed
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_social_challenges_updated_at BEFORE UPDATE ON social_challenges
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_social_recommendations_updated_at BEFORE UPDATE ON social_recommendations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_social_profiles_updated_at BEFORE UPDATE ON social_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add table comments
COMMENT ON TABLE social_connections IS 'Social connections and relationships between users with permissions and limits';
COMMENT ON TABLE social_payments IS 'Social payments with enhanced features like messaging, media, and social visibility';
COMMENT ON TABLE social_groups IS 'Social groups for organizing users and facilitating group payments and activities';
COMMENT ON TABLE social_group_members IS 'Group membership with roles and permissions';
COMMENT ON TABLE social_feed IS 'Social activity feed showing payments, connections, and other social activities';
COMMENT ON TABLE social_feed_interactions IS 'User interactions with feed items (likes, comments, shares)';
COMMENT ON TABLE social_challenges IS 'Social challenges and competitions to gamify financial activities';
COMMENT ON TABLE social_challenge_participants IS 'Participant progress and achievements in social challenges';
COMMENT ON TABLE social_recommendations IS 'AI-powered recommendations for friends, groups, and challenges';
COMMENT ON TABLE social_profiles IS 'Extended social profiles with privacy settings and social metrics';
COMMENT ON TABLE social_achievements IS 'Gamification achievements and badges for social activities';
COMMENT ON TABLE social_content_moderation IS 'Content moderation and reporting system for social features';
COMMENT ON TABLE social_analytics_events IS 'Analytics tracking for social feature usage and engagement';