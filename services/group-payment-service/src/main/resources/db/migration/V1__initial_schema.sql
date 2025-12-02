-- Group Payment Service Initial Schema
-- Created: 2025-09-27
-- Description: Group payment, expense splitting, and collective payment schema

CREATE TABLE IF NOT EXISTS payment_group (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id VARCHAR(100) UNIQUE NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    group_type VARCHAR(50) NOT NULL,
    description TEXT,
    group_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    creator_id UUID NOT NULL,
    group_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    total_members INTEGER DEFAULT 0,
    active_members INTEGER DEFAULT 0,
    privacy_setting VARCHAR(20) DEFAULT 'PRIVATE',
    join_approval_required BOOLEAN DEFAULT FALSE,
    member_invite_allowed BOOLEAN DEFAULT TRUE,
    group_image_url VARCHAR(500),
    group_rules JSONB,
    default_split_method VARCHAR(50) DEFAULT 'EQUAL',
    settlement_preferences JSONB,
    notification_preferences JSONB,
    is_recurring BOOLEAN DEFAULT FALSE,
    recurring_frequency VARCHAR(20),
    recurring_day INTEGER,
    next_recurring_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS group_member (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL,
    member_role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    nickname VARCHAR(100),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    invited_by UUID,
    invitation_accepted_at TIMESTAMP,
    payment_method_id VARCHAR(100),
    auto_settle BOOLEAN DEFAULT FALSE,
    notification_enabled BOOLEAN DEFAULT TRUE,
    permissions JSONB,
    member_balance DECIMAL(18, 2) DEFAULT 0,
    total_paid DECIMAL(18, 2) DEFAULT 0,
    total_owed DECIMAL(18, 2) DEFAULT 0,
    total_borrowed DECIMAL(18, 2) DEFAULT 0,
    last_activity_at TIMESTAMP,
    left_at TIMESTAMP,
    left_reason VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_expense (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    expense_name VARCHAR(255) NOT NULL,
    description TEXT,
    expense_category VARCHAR(100),
    expense_type VARCHAR(50) NOT NULL,
    total_amount DECIMAL(18, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    paid_by_member_id VARCHAR(100) NOT NULL,
    split_method VARCHAR(50) NOT NULL DEFAULT 'EQUAL',
    split_configuration JSONB NOT NULL,
    expense_date DATE NOT NULL,
    expense_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settlement_status VARCHAR(20) DEFAULT 'UNSETTLED',
    receipt_url VARCHAR(1000),
    receipt_file_path VARCHAR(1000),
    location VARCHAR(255),
    notes TEXT,
    tags TEXT[],
    is_reimbursable BOOLEAN DEFAULT FALSE,
    reimbursement_status VARCHAR(20),
    approval_required BOOLEAN DEFAULT FALSE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    rejection_reason TEXT,
    recurring BOOLEAN DEFAULT FALSE,
    recurring_frequency VARCHAR(20),
    recurring_end_date DATE,
    parent_expense_id VARCHAR(100),
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_expense_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE,
    CONSTRAINT fk_group_expense_paid_by FOREIGN KEY (paid_by_member_id) REFERENCES group_member(member_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expense_split (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    split_id VARCHAR(100) UNIQUE NOT NULL,
    expense_id VARCHAR(100) NOT NULL,
    member_id VARCHAR(100) NOT NULL,
    split_amount DECIMAL(18, 2) NOT NULL,
    split_percentage DECIMAL(5, 4),
    split_type VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    paid_amount DECIMAL(18, 2) DEFAULT 0,
    outstanding_amount DECIMAL(18, 2) NOT NULL,
    payment_date DATE,
    payment_reference VARCHAR(100),
    payment_method VARCHAR(50),
    waived BOOLEAN DEFAULT FALSE,
    waived_by VARCHAR(100),
    waived_at TIMESTAMP,
    waive_reason VARCHAR(255),
    reminder_sent BOOLEAN DEFAULT FALSE,
    reminder_count INTEGER DEFAULT 0,
    last_reminder_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_split_expense FOREIGN KEY (expense_id) REFERENCES group_expense(expense_id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_split_member FOREIGN KEY (member_id) REFERENCES group_member(member_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    settlement_type VARCHAR(50) NOT NULL,
    settlement_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payer_member_id VARCHAR(100) NOT NULL,
    payee_member_id VARCHAR(100) NOT NULL,
    settlement_amount DECIMAL(18, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    settlement_method VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(100),
    transaction_reference VARCHAR(100),
    scheduled_date DATE,
    settlement_date DATE,
    completed_at TIMESTAMP,
    payment_proof_url VARCHAR(1000),
    confirmation_code VARCHAR(100),
    fee_amount DECIMAL(18, 2) DEFAULT 0,
    net_amount DECIMAL(18, 2),
    notes TEXT,
    disputed BOOLEAN DEFAULT FALSE,
    dispute_reason TEXT,
    dispute_resolved BOOLEAN DEFAULT FALSE,
    dispute_resolution TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_settlement_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE,
    CONSTRAINT fk_group_settlement_payer FOREIGN KEY (payer_member_id) REFERENCES group_member(member_id) ON DELETE CASCADE,
    CONSTRAINT fk_group_settlement_payee FOREIGN KEY (payee_member_id) REFERENCES group_member(member_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_balance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    balance_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    debtor_member_id VARCHAR(100) NOT NULL,
    creditor_member_id VARCHAR(100) NOT NULL,
    balance_amount DECIMAL(18, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    balance_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settlement_threshold DECIMAL(18, 2),
    auto_settle_enabled BOOLEAN DEFAULT FALSE,
    related_expenses INTEGER DEFAULT 0,
    oldest_expense_date DATE,
    newest_expense_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_balance_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE,
    CONSTRAINT fk_group_balance_debtor FOREIGN KEY (debtor_member_id) REFERENCES group_member(member_id) ON DELETE CASCADE,
    CONSTRAINT fk_group_balance_creditor FOREIGN KEY (creditor_member_id) REFERENCES group_member(member_id) ON DELETE CASCADE,
    CONSTRAINT unique_group_debtor_creditor UNIQUE (group_id, debtor_member_id, creditor_member_id)
);

CREATE TABLE IF NOT EXISTS group_invitation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invitation_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    invited_by UUID NOT NULL,
    invited_user_id UUID,
    invited_email VARCHAR(255),
    invited_phone VARCHAR(20),
    invitation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invitation_message TEXT,
    invitation_link VARCHAR(500),
    invitation_token VARCHAR(100) UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    viewed_at TIMESTAMP,
    accepted_at TIMESTAMP,
    rejected_at TIMESTAMP,
    rejection_reason TEXT,
    reminder_count INTEGER DEFAULT 0,
    last_reminder_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_invitation_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    activity_category VARCHAR(100) NOT NULL,
    actor_member_id VARCHAR(100),
    target_member_id VARCHAR(100),
    expense_id VARCHAR(100),
    settlement_id VARCHAR(100),
    activity_description TEXT NOT NULL,
    activity_data JSONB,
    amount DECIMAL(18, 2),
    currency_code VARCHAR(3),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_activity_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    member_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    notification_category VARCHAR(100) NOT NULL,
    subject VARCHAR(255),
    message TEXT NOT NULL,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivery_method VARCHAR(50) NOT NULL,
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    action_url VARCHAR(1000),
    action_data JSONB,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_notification_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE,
    CONSTRAINT fk_group_notification_member FOREIGN KEY (member_id) REFERENCES group_member(member_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_reminder (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_id VARCHAR(100) UNIQUE NOT NULL,
    group_id VARCHAR(100) NOT NULL,
    expense_id VARCHAR(100),
    settlement_id VARCHAR(100),
    member_id VARCHAR(100) NOT NULL,
    reminder_type VARCHAR(50) NOT NULL,
    amount_due DECIMAL(18, 2),
    due_date DATE,
    reminder_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_date DATE NOT NULL,
    sent_at TIMESTAMP,
    delivery_status VARCHAR(20),
    reminder_message TEXT,
    frequency VARCHAR(20),
    auto_escalate BOOLEAN DEFAULT FALSE,
    escalation_level INTEGER DEFAULT 0,
    max_escalations INTEGER DEFAULT 3,
    next_reminder_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_reminder_group FOREIGN KEY (group_id) REFERENCES payment_group(group_id) ON DELETE CASCADE,
    CONSTRAINT fk_group_reminder_member FOREIGN KEY (member_id) REFERENCES group_member(member_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS group_payment_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_groups INTEGER DEFAULT 0,
    active_groups INTEGER DEFAULT 0,
    total_members INTEGER DEFAULT 0,
    active_members INTEGER DEFAULT 0,
    total_expenses INTEGER DEFAULT 0,
    total_expense_amount DECIMAL(18, 2) DEFAULT 0,
    total_settlements INTEGER DEFAULT 0,
    total_settlement_amount DECIMAL(18, 2) DEFAULT 0,
    settlement_rate DECIMAL(5, 4),
    avg_group_size DECIMAL(10, 2),
    avg_expense_amount DECIMAL(18, 2),
    by_expense_category JSONB,
    by_split_method JSONB,
    by_settlement_method JSONB,
    payment_velocity_metrics JSONB,
    member_engagement_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS group_payment_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_payment_groups INTEGER DEFAULT 0,
    active_payment_groups INTEGER DEFAULT 0,
    total_group_expenses BIGINT DEFAULT 0,
    expense_volume DECIMAL(18, 2) DEFAULT 0,
    settlements_completed BIGINT DEFAULT 0,
    settlement_volume DECIMAL(18, 2) DEFAULT 0,
    avg_settlement_time_days DECIMAL(10, 2),
    settlement_success_rate DECIMAL(5, 4),
    by_group_type JSONB,
    by_expense_category JSONB,
    top_expense_categories JSONB,
    member_participation_rate DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_group_payment_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_payment_group_creator ON payment_group(creator_id);
CREATE INDEX idx_payment_group_type ON payment_group(group_type);
CREATE INDEX idx_payment_group_status ON payment_group(group_status);
CREATE INDEX idx_payment_group_recurring ON payment_group(is_recurring) WHERE is_recurring = true;

CREATE INDEX idx_group_member_group ON group_member(group_id);
CREATE INDEX idx_group_member_user ON group_member(user_id);
CREATE INDEX idx_group_member_role ON group_member(member_role);
CREATE INDEX idx_group_member_status ON group_member(member_status);

CREATE INDEX idx_group_expense_group ON group_expense(group_id);
CREATE INDEX idx_group_expense_paid_by ON group_expense(paid_by_member_id);
CREATE INDEX idx_group_expense_status ON group_expense(expense_status);
CREATE INDEX idx_group_expense_date ON group_expense(expense_date DESC);
CREATE INDEX idx_group_expense_category ON group_expense(expense_category);
CREATE INDEX idx_group_expense_settlement ON group_expense(settlement_status);

CREATE INDEX idx_expense_split_expense ON expense_split(expense_id);
CREATE INDEX idx_expense_split_member ON expense_split(member_id);
CREATE INDEX idx_expense_split_status ON expense_split(payment_status);
CREATE INDEX idx_expense_split_unpaid ON expense_split(payment_status) WHERE payment_status = 'UNPAID';

CREATE INDEX idx_group_settlement_group ON group_settlement(group_id);
CREATE INDEX idx_group_settlement_payer ON group_settlement(payer_member_id);
CREATE INDEX idx_group_settlement_payee ON group_settlement(payee_member_id);
CREATE INDEX idx_group_settlement_status ON group_settlement(settlement_status);
CREATE INDEX idx_group_settlement_date ON group_settlement(settlement_date DESC);

CREATE INDEX idx_group_balance_group ON group_balance(group_id);
CREATE INDEX idx_group_balance_debtor ON group_balance(debtor_member_id);
CREATE INDEX idx_group_balance_creditor ON group_balance(creditor_member_id);
CREATE INDEX idx_group_balance_status ON group_balance(balance_status);

CREATE INDEX idx_group_invitation_group ON group_invitation(group_id);
CREATE INDEX idx_group_invitation_invited_by ON group_invitation(invited_by);
CREATE INDEX idx_group_invitation_email ON group_invitation(invited_email);
CREATE INDEX idx_group_invitation_status ON group_invitation(invitation_status);
CREATE INDEX idx_group_invitation_expires ON group_invitation(expires_at);

CREATE INDEX idx_group_activity_group ON group_activity(group_id);
CREATE INDEX idx_group_activity_type ON group_activity(activity_type);
CREATE INDEX idx_group_activity_timestamp ON group_activity(timestamp DESC);
CREATE INDEX idx_group_activity_actor ON group_activity(actor_member_id);

CREATE INDEX idx_group_notification_group ON group_notification(group_id);
CREATE INDEX idx_group_notification_member ON group_notification(member_id);
CREATE INDEX idx_group_notification_type ON group_notification(notification_type);
CREATE INDEX idx_group_notification_status ON group_notification(status);

CREATE INDEX idx_group_reminder_group ON group_reminder(group_id);
CREATE INDEX idx_group_reminder_member ON group_reminder(member_id);
CREATE INDEX idx_group_reminder_status ON group_reminder(reminder_status);
CREATE INDEX idx_group_reminder_scheduled ON group_reminder(scheduled_date);

CREATE INDEX idx_group_payment_analytics_period ON group_payment_analytics(period_end DESC);
CREATE INDEX idx_group_payment_statistics_period ON group_payment_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_payment_group_updated_at BEFORE UPDATE ON payment_group
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_group_member_updated_at BEFORE UPDATE ON group_member
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_group_expense_updated_at BEFORE UPDATE ON group_expense
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_expense_split_updated_at BEFORE UPDATE ON expense_split
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_group_settlement_updated_at BEFORE UPDATE ON group_settlement
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_group_balance_updated_at BEFORE UPDATE ON group_balance
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_group_reminder_updated_at BEFORE UPDATE ON group_reminder
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();