-- V2 Migration: Add Expenses and Budgets Tables
-- Created: 2025-11-10
-- Updated: 2025-11-10 - Refactored to use UUID types
-- Description: Adds expenses and budgets tables to match domain entities
--              Uses native PostgreSQL UUID type for all ID and user_id columns
--              Fixes decimal precision from DECIMAL(15,2) to DECIMAL(19,4)
--              Adds supporting tables for expense attachments, tags, and budget alerts

-- =========================================================================
-- PART 1: CREATE EXPENSES TABLE AND SUPPORTING TABLES
-- =========================================================================

-- Main expenses table matching Expense.java entity
CREATE TABLE IF NOT EXISTS expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_id VARCHAR(255),
    description VARCHAR(500) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    expense_date DATE NOT NULL,
    category_id UUID,
    budget_id UUID,
    expense_type VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50),
    status VARCHAR(50) NOT NULL,

    -- Merchant and Location Information
    merchant_name VARCHAR(255),
    merchant_category VARCHAR(255),
    merchant_id VARCHAR(255),
    location_city VARCHAR(255),
    location_country VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,

    -- Expense Details
    is_recurring BOOLEAN DEFAULT FALSE,
    recurring_frequency VARCHAR(50),
    parent_expense_id UUID,
    is_reimbursable BOOLEAN DEFAULT FALSE,
    reimbursement_status VARCHAR(50),
    is_business_expense BOOLEAN DEFAULT FALSE,
    tax_deductible BOOLEAN DEFAULT FALSE,

    -- Analytics and Classification
    auto_categorized BOOLEAN DEFAULT FALSE,
    confidence_score DOUBLE PRECISION,
    needs_review BOOLEAN DEFAULT FALSE,
    review_reason VARCHAR(500),

    -- Notes
    notes TEXT,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    version BIGINT DEFAULT 0,

    -- Foreign keys (will be added after creating referenced tables)
    CONSTRAINT fk_expense_category FOREIGN KEY (category_id) REFERENCES expense_category(category_id),
    CONSTRAINT fk_expense_budget FOREIGN KEY (budget_id) REFERENCES budgets(id)
);

-- Expense attachments (ElementCollection)
CREATE TABLE IF NOT EXISTS expense_attachments (
    expense_id UUID NOT NULL,
    attachment_url VARCHAR(1000) NOT NULL,
    attachment_order INTEGER NOT NULL,
    CONSTRAINT fk_expense_attachments_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE
);

-- Expense tags (ElementCollection)
CREATE TABLE IF NOT EXISTS expense_tags (
    expense_id UUID NOT NULL,
    tag VARCHAR(100) NOT NULL,
    tag_order INTEGER NOT NULL,
    CONSTRAINT fk_expense_tags_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE
);

-- Expense metadata (ElementCollection)
CREATE TABLE IF NOT EXISTS expense_metadata (
    expense_id UUID NOT NULL,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value VARCHAR(1000),
    PRIMARY KEY (expense_id, metadata_key),
    CONSTRAINT fk_expense_metadata_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE
);

-- =========================================================================
-- PART 2: CREATE BUDGETS TABLE AND SUPPORTING TABLES
-- =========================================================================

-- Main budgets table matching Budget.java entity
CREATE TABLE IF NOT EXISTS budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    budget_type VARCHAR(50) NOT NULL,
    budget_period VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,

    -- Budget Amounts
    planned_amount DECIMAL(19, 4) NOT NULL,
    spent_amount DECIMAL(19, 4) DEFAULT 0,
    remaining_amount DECIMAL(19, 4) DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Period Information
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    is_recurring BOOLEAN DEFAULT FALSE,
    auto_renew BOOLEAN DEFAULT FALSE,

    -- Alert Thresholds (percentage values)
    warning_threshold DECIMAL(5, 2) DEFAULT 80.00,
    critical_threshold DECIMAL(5, 2) DEFAULT 95.00,
    overspend_allowed BOOLEAN DEFAULT FALSE,
    max_overspend_amount DECIMAL(19, 4),

    -- Analytics and Tracking
    average_daily_spend DECIMAL(19, 4),
    projected_spend DECIMAL(19, 4),
    variance_amount DECIMAL(19, 4),
    variance_percentage DECIMAL(5, 2),
    days_remaining INTEGER,
    performance_score DECIMAL(5, 2),

    -- Notifications and Settings
    notifications_enabled BOOLEAN DEFAULT TRUE,
    email_alerts BOOLEAN DEFAULT TRUE,
    push_notifications BOOLEAN DEFAULT TRUE,
    weekly_digest BOOLEAN DEFAULT TRUE,

    -- Goals and Targets
    savings_goal DECIMAL(19, 4),
    savings_achieved DECIMAL(19, 4),
    improvement_target DECIMAL(5, 2),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_calculated_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Budget categories (ManyToMany)
CREATE TABLE IF NOT EXISTS budget_categories (
    budget_id UUID NOT NULL,
    category_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (budget_id, category_id),
    CONSTRAINT fk_budget_categories_budget FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE CASCADE,
    CONSTRAINT fk_budget_categories_category FOREIGN KEY (category_id) REFERENCES expense_category(category_id) ON DELETE CASCADE
);

-- Budget metadata (ElementCollection)
CREATE TABLE IF NOT EXISTS budget_metadata (
    budget_id UUID NOT NULL,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value VARCHAR(1000),
    PRIMARY KEY (budget_id, metadata_key),
    CONSTRAINT fk_budget_metadata_budget FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE CASCADE
);

-- Budget alerts table (OneToMany from Budget entity)
CREATE TABLE IF NOT EXISTS budget_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    threshold_percentage DECIMAL(5, 2) NOT NULL,
    triggered_at TIMESTAMP,
    is_triggered BOOLEAN DEFAULT FALSE,
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMP,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_alerts_budget FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE CASCADE
);

-- =========================================================================
-- PART 3: UPDATE EXISTING TABLES TO FIX DECIMAL PRECISION
-- =========================================================================

-- Update expense_report table
ALTER TABLE expense_report
    ALTER COLUMN total_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN reimbursable_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN non_reimbursable_amount TYPE DECIMAL(19, 4);

-- Update expense_item table
ALTER TABLE expense_item
    ALTER COLUMN amount TYPE DECIMAL(19, 4),
    ALTER COLUMN base_currency_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN tax_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN tip_amount TYPE DECIMAL(19, 4);

-- Update expense_receipt table
ALTER TABLE expense_receipt
    ALTER COLUMN extracted_amount TYPE DECIMAL(19, 4);

-- Update expense_reimbursement table
ALTER TABLE expense_reimbursement
    ALTER COLUMN reimbursement_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN net_payment_amount TYPE DECIMAL(19, 4);

-- Update expense_advance table
ALTER TABLE expense_advance
    ALTER COLUMN advance_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN disbursed_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN settled_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN balance_amount TYPE DECIMAL(19, 4);

-- Update expense_mileage table
ALTER TABLE expense_mileage
    ALTER COLUMN calculated_amount TYPE DECIMAL(19, 4);

-- Update expense_per_diem table
ALTER TABLE expense_per_diem
    ALTER COLUMN actual_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN claimed_amount TYPE DECIMAL(19, 4);

-- Update expense_statistics table
ALTER TABLE expense_statistics
    ALTER COLUMN total_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN reimbursed_amount TYPE DECIMAL(19, 4),
    ALTER COLUMN pending_reimbursement TYPE DECIMAL(19, 4);

-- =========================================================================
-- PART 4: CREATE INDEXES FOR PERFORMANCE
-- =========================================================================

-- Expenses table indexes
CREATE INDEX idx_expenses_user_id ON expenses(user_id);
CREATE INDEX idx_expenses_expense_date ON expenses(expense_date DESC);
CREATE INDEX idx_expenses_category_id ON expenses(category_id);
CREATE INDEX idx_expenses_budget_id ON expenses(budget_id);
CREATE INDEX idx_expenses_status ON expenses(status);
CREATE INDEX idx_expenses_created_at ON expenses(created_at DESC);
CREATE INDEX idx_expenses_user_category_date ON expenses(user_id, category_id, expense_date);
CREATE INDEX idx_expenses_user_date_range ON expenses(user_id, expense_date);
CREATE INDEX idx_expenses_needs_review ON expenses(needs_review) WHERE needs_review = TRUE;
CREATE INDEX idx_expenses_recurring ON expenses(is_recurring) WHERE is_recurring = TRUE;
CREATE INDEX idx_expenses_reimbursable ON expenses(is_reimbursable) WHERE is_reimbursable = TRUE;
CREATE INDEX idx_expenses_merchant_name ON expenses(merchant_name);
CREATE INDEX idx_expenses_payment_method ON expenses(payment_method);

-- Expense attachments index
CREATE INDEX idx_expense_attachments_expense ON expense_attachments(expense_id);

-- Expense tags indexes
CREATE INDEX idx_expense_tags_expense ON expense_tags(expense_id);
CREATE INDEX idx_expense_tags_tag ON expense_tags(tag);

-- Budgets table indexes
CREATE INDEX idx_budgets_user_id ON budgets(user_id);
CREATE INDEX idx_budgets_period_start ON budgets(period_start);
CREATE INDEX idx_budgets_period_end ON budgets(period_end DESC);
CREATE INDEX idx_budgets_status ON budgets(status);
CREATE INDEX idx_budgets_budget_type ON budgets(budget_type);
CREATE INDEX idx_budgets_user_period ON budgets(user_id, period_start, period_end);
CREATE INDEX idx_budgets_user_active ON budgets(user_id, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_budgets_recurring ON budgets(is_recurring) WHERE is_recurring = TRUE;
CREATE INDEX idx_budgets_auto_renew ON budgets(auto_renew) WHERE auto_renew = TRUE;

-- Budget alerts indexes
CREATE INDEX idx_budget_alerts_budget ON budget_alerts(budget_id);
CREATE INDEX idx_budget_alerts_triggered ON budget_alerts(is_triggered) WHERE is_triggered = TRUE;
CREATE INDEX idx_budget_alerts_unacknowledged ON budget_alerts(is_acknowledged) WHERE is_acknowledged = FALSE;
CREATE INDEX idx_budget_alerts_triggered_at ON budget_alerts(triggered_at DESC);

-- =========================================================================
-- PART 5: UPDATE TRIGGERS FOR NEW TABLES
-- =========================================================================

-- Expenses update trigger
CREATE TRIGGER update_expenses_updated_at
BEFORE UPDATE ON expenses
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Budgets update trigger
CREATE TRIGGER update_budgets_updated_at
BEFORE UPDATE ON budgets
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =========================================================================
-- PART 6: ADD CONSTRAINTS AND CHECKS
-- =========================================================================

-- Expense constraints
ALTER TABLE expenses ADD CONSTRAINT check_expense_amount_positive CHECK (amount >= 0);
ALTER TABLE expenses ADD CONSTRAINT check_expense_currency_code CHECK (LENGTH(currency) = 3);
ALTER TABLE expenses ADD CONSTRAINT check_expense_confidence_score CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1));

-- Budget constraints
ALTER TABLE budgets ADD CONSTRAINT check_budget_planned_amount_positive CHECK (planned_amount > 0);
ALTER TABLE budgets ADD CONSTRAINT check_budget_spent_amount_non_negative CHECK (spent_amount >= 0);
ALTER TABLE budgets ADD CONSTRAINT check_budget_currency_code CHECK (LENGTH(currency) = 3);
ALTER TABLE budgets ADD CONSTRAINT check_budget_period_valid CHECK (period_end >= period_start);
ALTER TABLE budgets ADD CONSTRAINT check_budget_warning_threshold CHECK (warning_threshold >= 0 AND warning_threshold <= 100);
ALTER TABLE budgets ADD CONSTRAINT check_budget_critical_threshold CHECK (critical_threshold >= 0 AND critical_threshold <= 100);
ALTER TABLE budgets ADD CONSTRAINT check_budget_performance_score CHECK (performance_score IS NULL OR (performance_score >= 0 AND performance_score <= 100));

-- =========================================================================
-- PART 7: ADD FOREIGN KEY FROM EXPENSES TO BUDGETS (deferred)
-- =========================================================================

-- Note: The FK constraint fk_expense_budget was already added in expenses CREATE TABLE above
-- This ensures referential integrity between expenses and budgets

-- =========================================================================
-- MIGRATION COMPLETE
-- =========================================================================

-- Summary:
-- - Created expenses table with all fields matching Expense.java entity
-- - Created expense_attachments, expense_tags, expense_metadata supporting tables
-- - Created budgets table with all fields matching Budget.java entity
-- - Created budget_categories, budget_metadata, budget_alerts supporting tables
-- - Updated all DECIMAL(15,2) to DECIMAL(19,4) across existing tables
-- - Added comprehensive indexes for query performance
-- - Added update triggers for automatic timestamp management
-- - Added check constraints for data integrity
-- - Established foreign key relationships between all tables
