-- ============================================================================
-- Payment Service Database Schema - Velocity Rules Table
-- Version: 1.1
-- Description: Velocity check rules for fraud prevention
-- ============================================================================

-- Create velocity_rules table
CREATE TABLE IF NOT EXISTS velocity_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    rule_type VARCHAR(50) NOT NULL,
    max_count INTEGER CHECK (max_count > 0),
    time_window_seconds INTEGER CHECK (time_window_seconds > 0),
    min_amount DECIMAL(19,4) CHECK (min_amount >= 0),
    max_amount DECIMAL(19,4) CHECK (max_amount >= 0),
    alert_only BOOLEAN DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    description TEXT,
    user_scope VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_triggered_at TIMESTAMP,
    trigger_count BIGINT DEFAULT 0,

    CONSTRAINT chk_amount_range CHECK (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount)
);

-- ============================================================================
-- Indexes for Performance Optimization
-- ============================================================================

-- Primary index for enabled rules ordered by priority
CREATE INDEX idx_velocity_rules_enabled
ON velocity_rules(enabled, priority ASC)
WHERE enabled = TRUE;

-- Index for rule type queries
CREATE INDEX idx_velocity_rules_type
ON velocity_rules(rule_type, enabled)
WHERE enabled = TRUE;

-- Index for priority-based evaluation
CREATE INDEX idx_velocity_rules_priority
ON velocity_rules(priority ASC, enabled)
WHERE enabled = TRUE;

-- Index for user scope filtering
CREATE INDEX idx_velocity_rules_user_scope
ON velocity_rules(user_scope, enabled, priority ASC)
WHERE user_scope IS NOT NULL AND enabled = TRUE;

-- Index for statistics queries (most triggered rules)
CREATE INDEX idx_velocity_rules_triggers
ON velocity_rules(trigger_count DESC, last_triggered_at DESC)
WHERE enabled = TRUE;

-- Index for time window queries
CREATE INDEX idx_velocity_rules_time_window
ON velocity_rules(time_window_seconds, enabled, priority ASC)
WHERE enabled = TRUE;

-- ============================================================================
-- Comments for Documentation
-- ============================================================================

COMMENT ON TABLE velocity_rules IS 'Configurable velocity check rules for transaction fraud prevention';
COMMENT ON COLUMN velocity_rules.id IS 'Primary key (UUID)';
COMMENT ON COLUMN velocity_rules.name IS 'Unique human-readable rule name';
COMMENT ON COLUMN velocity_rules.rule_type IS 'Rule type for categorization (DAILY_LIMIT, HOURLY_VELOCITY, etc.)';
COMMENT ON COLUMN velocity_rules.max_count IS 'Maximum number of transactions allowed in time window';
COMMENT ON COLUMN velocity_rules.time_window_seconds IS 'Time window for velocity calculation (in seconds)';
COMMENT ON COLUMN velocity_rules.min_amount IS 'Minimum transaction amount threshold';
COMMENT ON COLUMN velocity_rules.max_amount IS 'Maximum transaction amount threshold';
COMMENT ON COLUMN velocity_rules.alert_only IS 'If true, alert without blocking transaction';
COMMENT ON COLUMN velocity_rules.enabled IS 'Rule enabled/disabled toggle';
COMMENT ON COLUMN velocity_rules.priority IS 'Evaluation priority (lower number = higher priority)';
COMMENT ON COLUMN velocity_rules.user_scope IS 'User scope/tier/role (NULL = applies to all)';
COMMENT ON COLUMN velocity_rules.trigger_count IS 'Number of times rule has been triggered';

-- ============================================================================
-- Default Velocity Rules (Production-Ready Configuration)
-- ============================================================================

-- Rule 1: Daily transaction limit (general users)
INSERT INTO velocity_rules (name, rule_type, max_count, time_window_seconds, max_amount, enabled, priority, description)
VALUES (
    'Daily Transaction Limit - Standard',
    'DAILY_LIMIT',
    20,
    86400, -- 24 hours
    10000.00,
    TRUE,
    1,
    'Limits standard users to 20 transactions or $10,000 per day'
);

-- Rule 2: Hourly velocity check
INSERT INTO velocity_rules (name, rule_type, max_count, time_window_seconds, max_amount, enabled, priority, description)
VALUES (
    'Hourly Velocity Check',
    'HOURLY_VELOCITY',
    5,
    3600, -- 1 hour
    5000.00,
    TRUE,
    2,
    'Limits users to 5 transactions or $5,000 per hour'
);

-- Rule 3: Large transaction alert (monitoring only)
INSERT INTO velocity_rules (name, rule_type, max_count, min_amount, alert_only, enabled, priority, description)
VALUES (
    'Large Transaction Alert',
    'LARGE_AMOUNT',
    1,
    50000.00,
    TRUE, -- Alert only, don't block
    TRUE,
    3,
    'Alert on transactions above $50,000 (monitoring only)'
);

-- Rule 4: Rapid succession prevention (5 minutes)
INSERT INTO velocity_rules (name, rule_type, max_count, time_window_seconds, enabled, priority, description)
VALUES (
    'Rapid Succession Prevention',
    'RAPID_SUCCESSION',
    3,
    300, -- 5 minutes
    TRUE,
    4,
    'Prevents more than 3 transactions within 5 minutes'
);

-- Rule 5: First-time user limit (lower threshold)
INSERT INTO velocity_rules (name, rule_type, max_count, time_window_seconds, max_amount, enabled, priority, user_scope, description)
VALUES (
    'First-Time User Daily Limit',
    'FIRST_TRANSACTION',
    5,
    86400, -- 24 hours
    1000.00,
    TRUE,
    1,
    'NEW_USER',
    'Conservative limits for first-time users: 5 transactions or $1,000 per day'
);

-- Rule 6: Premium user higher limits
INSERT INTO velocity_rules (name, rule_type, max_count, time_window_seconds, max_amount, enabled, priority, user_scope, description)
VALUES (
    'Premium User Daily Limit',
    'DAILY_LIMIT',
    50,
    86400, -- 24 hours
    50000.00,
    TRUE,
    1,
    'PREMIUM',
    'Higher limits for premium users: 50 transactions or $50,000 per day'
);

-- Rule 7: Cumulative daily amount check
INSERT INTO velocity_rules (name, rule_type, time_window_seconds, max_amount, enabled, priority, description)
VALUES (
    'Daily Cumulative Amount Limit',
    'CUMULATIVE_DAILY',
    86400, -- 24 hours
    25000.00,
    TRUE,
    5,
    'Total daily transaction volume limit: $25,000'
);

-- Rule 8: High-risk payment method velocity
INSERT INTO velocity_rules (name, rule_type, max_count, time_window_seconds, enabled, priority, description)
VALUES (
    'High-Risk Payment Method Limit',
    'HIGH_RISK_MERCHANT',
    3,
    3600, -- 1 hour
    TRUE,
    2,
    'Limits high-risk payment methods to 3 transactions per hour'
);

-- ============================================================================
-- Grants (adjust based on your database user setup)
-- ============================================================================

-- GRANT SELECT, INSERT, UPDATE, DELETE ON velocity_rules TO payment_service_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO payment_service_app;

-- ============================================================================
-- Verification Query
-- ============================================================================

-- SELECT name, rule_type, enabled, priority
-- FROM velocity_rules
-- WHERE enabled = TRUE
-- ORDER BY priority ASC;
