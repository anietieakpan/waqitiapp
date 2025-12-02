-- Savings Service Database Schema
-- Creates tables for savings goals, automated savings, and investment tracking

-- Savings goals
CREATE TABLE savings_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    account_id UUID NOT NULL,
    
    -- Goal details
    goal_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50) CHECK (category IN ('EMERGENCY', 'VACATION', 'HOME', 'CAR', 'EDUCATION', 'RETIREMENT', 'GENERAL')),
    
    -- Target and progress
    target_amount DECIMAL(15,2) NOT NULL CHECK (target_amount > 0),
    current_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00 CHECK (current_amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Timeline
    target_date DATE,
    created_date DATE DEFAULT CURRENT_DATE,
    
    -- Progress tracking
    progress_percentage DECIMAL(5,2) GENERATED ALWAYS AS (
        CASE 
            WHEN target_amount > 0 THEN (current_amount / target_amount * 100)
            ELSE 0 
        END
    ) STORED,
    
    -- Visual settings
    goal_image_url VARCHAR(500),
    color_hex VARCHAR(7) DEFAULT '#2196F3',
    emoji VARCHAR(10),
    
    -- Status
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- Notifications
    milestone_notifications BOOLEAN DEFAULT TRUE,
    reminder_frequency VARCHAR(20) DEFAULT 'WEEKLY' CHECK (reminder_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'NEVER')),
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Automatic savings rules
CREATE TABLE savings_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    savings_goal_id UUID REFERENCES savings_goals(id) ON DELETE CASCADE,
    
    -- Rule details
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(30) NOT NULL CHECK (rule_type IN ('ROUND_UP', 'PERCENTAGE', 'FIXED_AMOUNT', 'RECURRING', 'TRANSACTION_TRIGGER')),
    
    -- Rule configuration
    rule_config JSONB NOT NULL, -- Flexible configuration based on rule type
    
    -- Scheduling
    frequency VARCHAR(20) CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    execution_day INTEGER, -- Day of week/month
    next_execution_date DATE,
    
    -- Limits
    minimum_amount DECIMAL(10,2) DEFAULT 0.01,
    maximum_amount DECIMAL(10,2),
    monthly_limit DECIMAL(15,2),
    current_monthly_total DECIMAL(15,2) DEFAULT 0.00,
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    last_executed_at TIMESTAMP WITH TIME ZONE,
    execution_count INTEGER DEFAULT 0,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Savings transactions
CREATE TABLE savings_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    savings_goal_id UUID NOT NULL REFERENCES savings_goals(id),
    user_id UUID NOT NULL,
    
    -- Transaction details
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'INTEREST', 'BONUS')),
    amount DECIMAL(15,2) NOT NULL CHECK (amount != 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Source and trigger
    source_account_id UUID,
    source_transaction_id UUID,
    savings_rule_id UUID REFERENCES savings_rules(id),
    trigger_type VARCHAR(30), -- What triggered this savings transaction
    
    -- Processing details
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    processed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    
    -- Balance tracking
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    
    -- Description and metadata
    description TEXT,
    metadata JSONB,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Savings challenges and campaigns
CREATE TABLE savings_challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Challenge details
    challenge_name VARCHAR(255) NOT NULL,
    challenge_type VARCHAR(30) NOT NULL CHECK (challenge_type IN ('52_WEEK', '365_DAY', 'NO_SPEND', 'ROUND_UP', 'CUSTOM')),
    description TEXT NOT NULL,
    
    -- Challenge parameters
    duration_days INTEGER NOT NULL CHECK (duration_days > 0),
    target_amount DECIMAL(15,2),
    difficulty_level VARCHAR(20) DEFAULT 'MEDIUM' CHECK (difficulty_level IN ('EASY', 'MEDIUM', 'HARD', 'EXPERT')),
    
    -- Challenge rules
    rules_config JSONB NOT NULL,
    completion_criteria JSONB NOT NULL,
    
    -- Rewards
    points_reward INTEGER DEFAULT 0,
    badge_reward VARCHAR(100),
    cash_bonus DECIMAL(10,2) DEFAULT 0.00,
    
    -- Timing
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    registration_deadline DATE,
    
    -- Participation
    max_participants INTEGER,
    current_participants INTEGER DEFAULT 0,
    
    -- Status
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'OPEN', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    is_featured BOOLEAN DEFAULT FALSE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- User participation in savings challenges
CREATE TABLE user_challenge_participation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    challenge_id UUID NOT NULL REFERENCES savings_challenges(id),
    savings_goal_id UUID REFERENCES savings_goals(id),
    
    -- Participation status
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'FAILED', 'WITHDRAWN')),
    
    -- Progress tracking
    current_progress DECIMAL(15,2) DEFAULT 0.00,
    progress_percentage DECIMAL(5,2) DEFAULT 0.00,
    milestone_data JSONB,
    
    -- Completion details
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    final_amount DECIMAL(15,2),
    
    -- Rewards earned
    points_earned INTEGER DEFAULT 0,
    bonus_earned DECIMAL(10,2) DEFAULT 0.00,
    badges_earned JSONB,
    
    UNIQUE(user_id, challenge_id)
);

-- Interest rates and yields
CREATE TABLE interest_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Rate details
    rate_type VARCHAR(30) NOT NULL CHECK (rate_type IN ('SAVINGS_GOAL', 'EMERGENCY_FUND', 'HIGH_YIELD', 'PROMOTIONAL')),
    annual_percentage_yield DECIMAL(6,4) NOT NULL CHECK (annual_percentage_yield >= 0),
    
    -- Eligibility criteria
    minimum_balance DECIMAL(15,2) DEFAULT 0.00,
    maximum_balance DECIMAL(15,2),
    user_tier VARCHAR(20), -- Premium, standard, etc.
    
    -- Rate period
    effective_date DATE NOT NULL,
    expiry_date DATE,
    
    -- Compounding
    compounding_frequency VARCHAR(20) DEFAULT 'MONTHLY' CHECK (compounding_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_promotional BOOLEAN DEFAULT FALSE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Interest calculations and payments
CREATE TABLE interest_calculations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    savings_goal_id UUID NOT NULL REFERENCES savings_goals(id),
    interest_rate_id UUID NOT NULL REFERENCES interest_rates(id),
    
    -- Calculation period
    calculation_date DATE NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    
    -- Balance information
    average_daily_balance DECIMAL(15,2) NOT NULL,
    days_in_period INTEGER NOT NULL,
    
    -- Interest calculation
    interest_rate_applied DECIMAL(6,4) NOT NULL,
    interest_earned DECIMAL(15,2) NOT NULL,
    
    -- Processing
    status VARCHAR(20) DEFAULT 'CALCULATED' CHECK (status IN ('CALCULATED', 'PENDING', 'PAID', 'FAILED')),
    paid_at TIMESTAMP WITH TIME ZONE,
    transaction_id UUID,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(savings_goal_id, calculation_date)
);

-- Milestones and achievements
CREATE TABLE savings_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    savings_goal_id UUID NOT NULL REFERENCES savings_goals(id),
    
    -- Milestone details
    milestone_type VARCHAR(30) NOT NULL CHECK (milestone_type IN ('PERCENTAGE', 'AMOUNT', 'TIME_BASED', 'STREAK')),
    milestone_value DECIMAL(15,2) NOT NULL,
    milestone_description VARCHAR(255),
    
    -- Achievement
    achieved_at TIMESTAMP WITH TIME ZONE,
    achieved_amount DECIMAL(15,2),
    is_achieved BOOLEAN DEFAULT FALSE,
    
    -- Rewards
    points_reward INTEGER DEFAULT 0,
    badge_reward VARCHAR(100),
    
    -- Notifications
    notification_sent BOOLEAN DEFAULT FALSE,
    celebrated BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_savings_goals_user_id ON savings_goals(user_id);
CREATE INDEX idx_savings_goals_account_id ON savings_goals(account_id);
CREATE INDEX idx_savings_goals_status ON savings_goals(status);
CREATE INDEX idx_savings_goals_category ON savings_goals(category);
CREATE INDEX idx_savings_goals_target_date ON savings_goals(target_date);

CREATE INDEX idx_savings_rules_user_id ON savings_rules(user_id);
CREATE INDEX idx_savings_rules_goal_id ON savings_rules(savings_goal_id);
CREATE INDEX idx_savings_rules_type ON savings_rules(rule_type);
CREATE INDEX idx_savings_rules_active ON savings_rules(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_savings_rules_next_execution ON savings_rules(next_execution_date) WHERE is_active = TRUE;

CREATE INDEX idx_savings_transactions_goal_id ON savings_transactions(savings_goal_id);
CREATE INDEX idx_savings_transactions_user_id ON savings_transactions(user_id);
CREATE INDEX idx_savings_transactions_status ON savings_transactions(status);
CREATE INDEX idx_savings_transactions_created_at ON savings_transactions(created_at);
CREATE INDEX idx_savings_transactions_rule_id ON savings_transactions(savings_rule_id) WHERE savings_rule_id IS NOT NULL;

CREATE INDEX idx_savings_challenges_status ON savings_challenges(status);
CREATE INDEX idx_savings_challenges_type ON savings_challenges(challenge_type);
CREATE INDEX idx_savings_challenges_dates ON savings_challenges(start_date, end_date);
CREATE INDEX idx_savings_challenges_featured ON savings_challenges(is_featured) WHERE is_featured = TRUE;

CREATE INDEX idx_user_challenge_participation_user_id ON user_challenge_participation(user_id);
CREATE INDEX idx_user_challenge_participation_challenge_id ON user_challenge_participation(challenge_id);
CREATE INDEX idx_user_challenge_participation_status ON user_challenge_participation(status);

CREATE INDEX idx_interest_rates_type ON interest_rates(rate_type);
CREATE INDEX idx_interest_rates_active ON interest_rates(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_interest_rates_effective_date ON interest_rates(effective_date);

CREATE INDEX idx_interest_calculations_goal_id ON interest_calculations(savings_goal_id);
CREATE INDEX idx_interest_calculations_date ON interest_calculations(calculation_date);
CREATE INDEX idx_interest_calculations_status ON interest_calculations(status);

CREATE INDEX idx_savings_milestones_goal_id ON savings_milestones(savings_goal_id);
CREATE INDEX idx_savings_milestones_achieved ON savings_milestones(is_achieved);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_savings_goals_updated_at BEFORE UPDATE
    ON savings_goals FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_savings_rules_updated_at BEFORE UPDATE
    ON savings_rules FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_savings_transactions_updated_at BEFORE UPDATE
    ON savings_transactions FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_savings_challenges_updated_at BEFORE UPDATE
    ON savings_challenges FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_interest_rates_updated_at BEFORE UPDATE
    ON interest_rates FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Function to update goal current amount when transaction is completed
CREATE OR REPLACE FUNCTION update_goal_amount_on_transaction()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'COMPLETED' AND (OLD.status IS NULL OR OLD.status != 'COMPLETED') THEN
        UPDATE savings_goals 
        SET current_amount = NEW.balance_after,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = NEW.savings_goal_id;
        
        -- Check if goal is completed
        UPDATE savings_goals 
        SET status = 'COMPLETED',
            completed_at = CURRENT_TIMESTAMP
        WHERE id = NEW.savings_goal_id 
        AND current_amount >= target_amount 
        AND status = 'ACTIVE';
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_goal_on_transaction_completion AFTER UPDATE
    ON savings_transactions FOR EACH ROW EXECUTE PROCEDURE update_goal_amount_on_transaction();

-- Function to update challenge participation count
CREATE OR REPLACE FUNCTION update_challenge_participant_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE savings_challenges 
        SET current_participants = current_participants + 1
        WHERE id = NEW.challenge_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE savings_challenges 
        SET current_participants = current_participants - 1
        WHERE id = OLD.challenge_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_challenge_on_participant_change
    AFTER INSERT OR DELETE ON user_challenge_participation
    FOR EACH ROW EXECUTE PROCEDURE update_challenge_participant_count();

-- Comments for documentation
COMMENT ON TABLE savings_goals IS 'User savings goals and targets';
COMMENT ON TABLE savings_rules IS 'Automated savings rules and triggers';
COMMENT ON TABLE savings_transactions IS 'All savings-related transactions';
COMMENT ON TABLE savings_challenges IS 'Savings challenges and campaigns';
COMMENT ON TABLE user_challenge_participation IS 'User participation in savings challenges';
COMMENT ON TABLE interest_rates IS 'Interest rates for different savings products';
COMMENT ON TABLE interest_calculations IS 'Interest calculations and payments';
COMMENT ON TABLE savings_milestones IS 'Milestone achievements for savings goals';