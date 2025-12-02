-- Family Account Service Database Schema
-- Version: 1.0.0
-- Description: Complete family account management with parental controls

-- Family Accounts table
CREATE TABLE family_accounts (
    id BIGSERIAL PRIMARY KEY,
    family_id VARCHAR(50) UNIQUE NOT NULL,
    family_name VARCHAR(100) NOT NULL,
    primary_parent_user_id VARCHAR(50) NOT NULL,
    secondary_parent_user_id VARCHAR(50),
    family_wallet_id VARCHAR(50),
    total_family_balance DECIMAL(19,2) DEFAULT 0.00 CHECK (total_family_balance >= 0),
    monthly_family_limit DECIMAL(19,2) CHECK (monthly_family_limit >= 0),
    current_month_spent DECIMAL(19,2) DEFAULT 0.00 CHECK (current_month_spent >= 0),
    family_status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (family_status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'CLOSED')),
    parental_controls_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    educational_mode_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    family_sharing_enabled BOOLEAN DEFAULT TRUE NOT NULL,
    emergency_contact_phone VARCHAR(20),
    family_pin_hash VARCHAR(255),
    family_timezone VARCHAR(50) DEFAULT 'UTC',
    allowance_day_of_month INTEGER CHECK (allowance_day_of_month BETWEEN 1 AND 28),
    auto_savings_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    auto_savings_percentage DECIMAL(5,2) DEFAULT 0.00 CHECK (auto_savings_percentage BETWEEN 0 AND 100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50)
);

-- Family Members table
CREATE TABLE family_members (
    id BIGSERIAL PRIMARY KEY,
    family_account_id BIGINT REFERENCES family_accounts(id) ON DELETE CASCADE,
    user_id VARCHAR(50) NOT NULL,
    nickname VARCHAR(50),
    member_role VARCHAR(20) NOT NULL CHECK (member_role IN ('PRIMARY_PARENT', 'SECONDARY_PARENT', 'TEEN', 'CHILD', 'YOUNG_ADULT')),
    date_of_birth DATE,
    individual_wallet_id VARCHAR(50),
    current_balance DECIMAL(19,2) DEFAULT 0.00 CHECK (current_balance >= 0),
    daily_spend_limit DECIMAL(19,2) CHECK (daily_spend_limit >= 0),
    weekly_spend_limit DECIMAL(19,2) CHECK (weekly_spend_limit >= 0),
    monthly_spend_limit DECIMAL(19,2) CHECK (monthly_spend_limit >= 0),
    current_daily_spent DECIMAL(19,2) DEFAULT 0.00 CHECK (current_daily_spent >= 0),
    current_weekly_spent DECIMAL(19,2) DEFAULT 0.00 CHECK (current_weekly_spent >= 0),
    current_monthly_spent DECIMAL(19,2) DEFAULT 0.00 CHECK (current_monthly_spent >= 0),
    allowance_amount DECIMAL(19,2) DEFAULT 0.00 CHECK (allowance_amount >= 0),
    last_allowance_date DATE,
    chore_earnings DECIMAL(19,2) DEFAULT 0.00 CHECK (chore_earnings >= 0),
    savings_goal_amount DECIMAL(19,2) CHECK (savings_goal_amount >= 0),
    current_savings DECIMAL(19,2) DEFAULT 0.00 CHECK (current_savings >= 0),
    member_status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (member_status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'PENDING_APPROVAL')),
    
    -- Parental Controls
    transaction_approval_required BOOLEAN DEFAULT FALSE NOT NULL,
    spending_time_restrictions_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    spending_allowed_start_time TIME,
    spending_allowed_end_time TIME,
    weekends_spending_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    online_purchases_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    atm_withdrawals_allowed BOOLEAN DEFAULT FALSE NOT NULL,
    international_transactions_allowed BOOLEAN DEFAULT FALSE NOT NULL,
    peer_payments_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    investment_allowed BOOLEAN DEFAULT FALSE NOT NULL,
    crypto_transactions_allowed BOOLEAN DEFAULT FALSE NOT NULL,
    
    -- Educational Features
    financial_literacy_score INTEGER DEFAULT 0 CHECK (financial_literacy_score BETWEEN 0 AND 100),
    completed_courses INTEGER DEFAULT 0 CHECK (completed_courses >= 0),
    badges_earned INTEGER DEFAULT 0 CHECK (badges_earned >= 0),
    last_quiz_date DATE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    added_by VARCHAR(50) NOT NULL,
    
    UNIQUE(family_account_id, user_id)
);

-- Family Spending Rules table
CREATE TABLE family_spending_rules (
    id BIGSERIAL PRIMARY KEY,
    family_account_id BIGINT REFERENCES family_accounts(id) ON DELETE CASCADE,
    rule_name VARCHAR(100) NOT NULL,
    rule_description VARCHAR(500),
    rule_type VARCHAR(30) NOT NULL CHECK (rule_type IN ('SPENDING_LIMIT', 'TIME_RESTRICTION', 'CATEGORY_RESTRICTION', 'MERCHANT_RESTRICTION', 'TRANSACTION_TYPE', 'APPROVAL_REQUIREMENT', 'EDUCATIONAL_REQUIREMENT', 'SAVINGS_ENFORCEMENT')),
    rule_scope VARCHAR(20) NOT NULL CHECK (rule_scope IN ('FAMILY_WIDE', 'INDIVIDUAL_MEMBER', 'AGE_GROUP', 'ROLE_BASED')),
    target_member_id VARCHAR(50),
    target_age_group VARCHAR(20) CHECK (target_age_group IN ('CHILD_UNDER_13', 'TEEN_13_17', 'YOUNG_ADULT_18_25', 'ALL_CHILDREN')),
    
    -- Amount-based rules
    max_transaction_amount DECIMAL(19,2) CHECK (max_transaction_amount >= 0),
    daily_limit DECIMAL(19,2) CHECK (daily_limit >= 0),
    weekly_limit DECIMAL(19,2) CHECK (weekly_limit >= 0),
    monthly_limit DECIMAL(19,2) CHECK (monthly_limit >= 0),
    
    -- Time-based rules
    allowed_start_time TIME,
    allowed_end_time TIME,
    weekdays_only BOOLEAN DEFAULT FALSE NOT NULL,
    weekends_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    
    -- Transaction type rules
    online_purchases_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    atm_withdrawals_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    international_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    peer_payments_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    subscription_payments_allowed BOOLEAN DEFAULT TRUE NOT NULL,
    
    -- Approval requirements
    requires_parent_approval BOOLEAN DEFAULT FALSE NOT NULL,
    approval_threshold DECIMAL(19,2) CHECK (approval_threshold >= 0),
    automatic_decline BOOLEAN DEFAULT FALSE NOT NULL,
    
    -- Educational features
    requires_quiz_completion BOOLEAN DEFAULT FALSE NOT NULL,
    educational_prompt VARCHAR(500),
    savings_goal_percentage DECIMAL(5,2) CHECK (savings_goal_percentage BETWEEN 0 AND 100),
    
    rule_status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (rule_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'EXPIRED')),
    priority INTEGER DEFAULT 5 CHECK (priority BETWEEN 1 AND 10),
    effective_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expiration_date TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50)
);

-- Rule Allowed Categories table
CREATE TABLE rule_allowed_categories (
    rule_id BIGINT REFERENCES family_spending_rules(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    PRIMARY KEY (rule_id, category)
);

-- Rule Blocked Categories table
CREATE TABLE rule_blocked_categories (
    rule_id BIGINT REFERENCES family_spending_rules(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    PRIMARY KEY (rule_id, category)
);

-- Rule Allowed Merchants table
CREATE TABLE rule_allowed_merchants (
    rule_id BIGINT REFERENCES family_spending_rules(id) ON DELETE CASCADE,
    merchant VARCHAR(100) NOT NULL,
    PRIMARY KEY (rule_id, merchant)
);

-- Rule Blocked Merchants table
CREATE TABLE rule_blocked_merchants (
    rule_id BIGINT REFERENCES family_spending_rules(id) ON DELETE CASCADE,
    merchant VARCHAR(100) NOT NULL,
    PRIMARY KEY (rule_id, merchant)
);

-- Family Goals table
CREATE TABLE family_goals (
    id BIGSERIAL PRIMARY KEY,
    family_account_id BIGINT REFERENCES family_accounts(id) ON DELETE CASCADE,
    goal_name VARCHAR(100) NOT NULL,
    goal_description VARCHAR(500),
    goal_type VARCHAR(20) NOT NULL CHECK (goal_type IN ('SAVINGS', 'SPENDING_REDUCTION', 'EDUCATIONAL', 'CHARITABLE')),
    target_amount DECIMAL(19,2) CHECK (target_amount >= 0),
    current_amount DECIMAL(19,2) DEFAULT 0.00 CHECK (current_amount >= 0),
    target_date DATE,
    goal_status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (goal_status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'PAUSED')),
    involves_all_members BOOLEAN DEFAULT TRUE NOT NULL,
    reward_description VARCHAR(200),
    reward_amount DECIMAL(19,2) CHECK (reward_amount >= 0),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL,
    updated_by VARCHAR(50)
);

-- Member Transactions table
CREATE TABLE member_transactions (
    id BIGSERIAL PRIMARY KEY,
    family_member_id BIGINT REFERENCES family_members(id) ON DELETE CASCADE,
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    merchant_name VARCHAR(100),
    category VARCHAR(50),
    transaction_type VARCHAR(30),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AUTHORIZED', 'DECLINED', 'PENDING_APPROVAL', 'CANCELLED')),
    decline_reason TEXT,
    requires_approval BOOLEAN DEFAULT FALSE NOT NULL,
    approved_by VARCHAR(50),
    approved_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Chore Tasks table
CREATE TABLE chore_tasks (
    id BIGSERIAL PRIMARY KEY,
    family_member_id BIGINT REFERENCES family_members(id) ON DELETE CASCADE,
    task_name VARCHAR(100) NOT NULL,
    task_description VARCHAR(500),
    reward_amount DECIMAL(19,2) DEFAULT 0.00 CHECK (reward_amount >= 0),
    task_status VARCHAR(20) DEFAULT 'ASSIGNED' CHECK (task_status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'VERIFIED', 'PAID')),
    due_date DATE,
    completion_date DATE,
    verified_by VARCHAR(50),
    verified_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(50) NOT NULL
);

-- Savings Goals table (individual member goals)
CREATE TABLE savings_goals (
    id BIGSERIAL PRIMARY KEY,
    family_member_id BIGINT REFERENCES family_members(id) ON DELETE CASCADE,
    goal_name VARCHAR(100) NOT NULL,
    goal_description VARCHAR(500),
    target_amount DECIMAL(19,2) NOT NULL CHECK (target_amount > 0),
    current_amount DECIMAL(19,2) DEFAULT 0.00 CHECK (current_amount >= 0),
    target_date DATE,
    goal_status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (goal_status IN ('ACTIVE', 'COMPLETED', 'CANCELLED', 'PAUSED')),
    auto_save_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    auto_save_amount DECIMAL(19,2) CHECK (auto_save_amount >= 0),
    auto_save_frequency VARCHAR(20) CHECK (auto_save_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50) NOT NULL
);

-- Educational Content Progress table
CREATE TABLE educational_progress (
    id BIGSERIAL PRIMARY KEY,
    family_member_id BIGINT REFERENCES family_members(id) ON DELETE CASCADE,
    content_type VARCHAR(30) NOT NULL CHECK (content_type IN ('COURSE', 'QUIZ', 'GAME', 'ARTICLE')),
    content_id VARCHAR(50) NOT NULL,
    content_title VARCHAR(200) NOT NULL,
    progress_percentage INTEGER DEFAULT 0 CHECK (progress_percentage BETWEEN 0 AND 100),
    completed BOOLEAN DEFAULT FALSE NOT NULL,
    score INTEGER CHECK (score BETWEEN 0 AND 100),
    time_spent_minutes INTEGER DEFAULT 0 CHECK (time_spent_minutes >= 0),
    
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(family_member_id, content_id)
);

-- Family Activity Log table
CREATE TABLE family_activity_log (
    id BIGSERIAL PRIMARY KEY,
    family_account_id BIGINT REFERENCES family_accounts(id) ON DELETE CASCADE,
    activity_type VARCHAR(50) NOT NULL,
    activity_description TEXT NOT NULL,
    actor_user_id VARCHAR(50) NOT NULL,
    target_user_id VARCHAR(50),
    metadata JSONB,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Pending Approvals table
CREATE TABLE pending_approvals (
    id BIGSERIAL PRIMARY KEY,
    family_account_id BIGINT REFERENCES family_accounts(id) ON DELETE CASCADE,
    family_member_id BIGINT REFERENCES family_members(id) ON DELETE CASCADE,
    approval_type VARCHAR(30) NOT NULL CHECK (approval_type IN ('TRANSACTION', 'RULE_CHANGE', 'LIMIT_INCREASE', 'MEMBER_ADDITION')),
    transaction_id VARCHAR(50),
    request_amount DECIMAL(19,2),
    merchant_name VARCHAR(100),
    request_description TEXT,
    requested_by VARCHAR(50) NOT NULL,
    approval_status VARCHAR(20) DEFAULT 'PENDING' CHECK (approval_status IN ('PENDING', 'APPROVED', 'DENIED', 'EXPIRED')),
    approved_by VARCHAR(50),
    approval_reason TEXT,
    expires_at TIMESTAMP NOT NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_family_accounts_family_id ON family_accounts(family_id);
CREATE INDEX idx_family_accounts_primary_parent ON family_accounts(primary_parent_user_id);
CREATE INDEX idx_family_accounts_allowance_day ON family_accounts(allowance_day_of_month);

CREATE INDEX idx_family_members_family_account ON family_members(family_account_id);
CREATE INDEX idx_family_members_user_id ON family_members(user_id);
CREATE INDEX idx_family_members_role ON family_members(member_role);
CREATE INDEX idx_family_members_status ON family_members(member_status);

CREATE INDEX idx_spending_rules_family_account ON family_spending_rules(family_account_id);
CREATE INDEX idx_spending_rules_scope ON family_spending_rules(rule_scope);
CREATE INDEX idx_spending_rules_status ON family_spending_rules(rule_status);
CREATE INDEX idx_spending_rules_effective ON family_spending_rules(effective_date, expiration_date);

CREATE INDEX idx_member_transactions_member ON member_transactions(family_member_id);
CREATE INDEX idx_member_transactions_status ON member_transactions(status);
CREATE INDEX idx_member_transactions_created ON member_transactions(created_at);

CREATE INDEX idx_family_goals_account ON family_goals(family_account_id);
CREATE INDEX idx_family_goals_status ON family_goals(goal_status);

CREATE INDEX idx_chore_tasks_member ON chore_tasks(family_member_id);
CREATE INDEX idx_chore_tasks_status ON chore_tasks(task_status);
CREATE INDEX idx_chore_tasks_due_date ON chore_tasks(due_date);

CREATE INDEX idx_savings_goals_member ON savings_goals(family_member_id);
CREATE INDEX idx_savings_goals_status ON savings_goals(goal_status);

CREATE INDEX idx_educational_progress_member ON educational_progress(family_member_id);
CREATE INDEX idx_educational_progress_completed ON educational_progress(completed);

CREATE INDEX idx_activity_log_family ON family_activity_log(family_account_id);
CREATE INDEX idx_activity_log_actor ON family_activity_log(actor_user_id);
CREATE INDEX idx_activity_log_created ON family_activity_log(created_at);

CREATE INDEX idx_pending_approvals_family ON pending_approvals(family_account_id);
CREATE INDEX idx_pending_approvals_status ON pending_approvals(approval_status);
CREATE INDEX idx_pending_approvals_expires ON pending_approvals(expires_at);

-- Create triggers for updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_family_accounts_updated_at BEFORE UPDATE ON family_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_family_members_updated_at BEFORE UPDATE ON family_members
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_family_spending_rules_updated_at BEFORE UPDATE ON family_spending_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_family_goals_updated_at BEFORE UPDATE ON family_goals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_chore_tasks_updated_at BEFORE UPDATE ON chore_tasks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_savings_goals_updated_at BEFORE UPDATE ON savings_goals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_pending_approvals_updated_at BEFORE UPDATE ON pending_approvals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add comments for documentation
COMMENT ON TABLE family_accounts IS 'Main family account entity with hierarchical structure and parental controls';
COMMENT ON TABLE family_members IS 'Individual family members with specific roles, permissions and spending limits';
COMMENT ON TABLE family_spending_rules IS 'Configurable spending rules and restrictions for family members';
COMMENT ON TABLE family_goals IS 'Family-wide financial goals and objectives';
COMMENT ON TABLE member_transactions IS 'Transaction history and authorization records for family members';
COMMENT ON TABLE chore_tasks IS 'Chore assignments and reward system for children';
COMMENT ON TABLE savings_goals IS 'Individual savings goals for family members';
COMMENT ON TABLE educational_progress IS 'Educational content completion tracking for financial literacy';
COMMENT ON TABLE family_activity_log IS 'Audit trail of all family account activities';
COMMENT ON TABLE pending_approvals IS 'Parent approval queue for transactions and account changes';