-- =============================================================================
-- WAQITI COMPENSATION SYSTEM DATABASE SCHEMA
-- Version: V002
-- Description: Comprehensive compensation and rollback audit tables
-- Author: Waqiti Engineering Team
-- Date: 2024-01-15
-- =============================================================================

-- Create compensation status enum
CREATE TYPE compensation_status AS ENUM (
    'PENDING',
    'IN_PROGRESS',
    'COMPLETED',
    'FAILED',
    'SKIPPED',
    'ALREADY_COMPLETED',
    'MANUAL_INTERVENTION',
    'QUEUED_FOR_RETRY',
    'UNKNOWN'
);

-- Create compensation action type enum
CREATE TYPE compensation_action_type AS ENUM (
    'WALLET_REVERSAL',
    'LEDGER_REVERSAL',
    'EXTERNAL_SYSTEM_REVERSAL',
    'NOTIFICATION_REVERSAL',
    'AUDIT_LOG',
    'CUSTOM_ACTION'
);

-- =============================================================================
-- COMPENSATION AUDIT TABLES
-- =============================================================================

-- Main compensation audit table
CREATE TABLE compensation_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    action_id VARCHAR(100) NOT NULL,
    compensation_type VARCHAR(50) NOT NULL,
    
    -- Status tracking
    status compensation_status NOT NULL DEFAULT 'PENDING',
    external_reference VARCHAR(255),
    provider_status VARCHAR(100),
    
    -- Compensation details
    original_amount DECIMAL(19,4),
    compensated_amount DECIMAL(19,4),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Error tracking
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 5,
    last_retry_at TIMESTAMP WITH TIME ZONE,
    
    -- Timing
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    
    -- Metadata
    metadata JSONB,
    compensation_data JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT uk_compensation_audit_action UNIQUE(transaction_id, action_id, compensation_type),
    CONSTRAINT chk_compensation_amounts CHECK (
        (original_amount IS NULL AND compensated_amount IS NULL) OR
        (original_amount >= 0 AND compensated_amount >= 0)
    ),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0 AND retry_count <= max_retries),
    CONSTRAINT chk_completed_status CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR
        (status = 'FAILED' AND failed_at IS NOT NULL) OR
        (status NOT IN ('COMPLETED', 'FAILED'))
    )
);

-- Compensation action execution history
CREATE TABLE compensation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    action_id VARCHAR(100) NOT NULL,
    action_type compensation_action_type NOT NULL,
    
    -- Action configuration
    target_service VARCHAR(100),
    target_resource_id VARCHAR(255),
    priority INTEGER DEFAULT 5,
    
    -- Execution details
    status compensation_status NOT NULL DEFAULT 'PENDING',
    execution_order INTEGER NOT NULL,
    depends_on_actions VARCHAR(100)[], -- Array of action IDs this depends on
    
    -- Retry configuration
    retryable BOOLEAN DEFAULT true,
    max_retries INTEGER DEFAULT 5,
    retry_delay_seconds INTEGER DEFAULT 30,
    current_retry INTEGER DEFAULT 0,
    
    -- Timing
    scheduled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    
    -- Results
    result_data JSONB,
    error_message TEXT,
    error_details JSONB,
    
    -- Compensation data
    compensation_data JSONB NOT NULL,
    rollback_data JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    
    -- Constraints
    CONSTRAINT uk_compensation_actions_id UNIQUE(transaction_id, action_id),
    CONSTRAINT chk_execution_order CHECK (execution_order > 0),
    CONSTRAINT chk_priority CHECK (priority BETWEEN 1 AND 10),
    CONSTRAINT chk_retry_delay CHECK (retry_delay_seconds > 0),
    CONSTRAINT chk_current_retry CHECK (current_retry >= 0 AND current_retry <= max_retries)
);

-- =============================================================================
-- WEBHOOK MANAGEMENT TABLES
-- =============================================================================

-- Create webhook status enum
CREATE TYPE webhook_status AS ENUM (
    'ACTIVE',
    'PENDING',
    'CANCELLED',
    'EXPIRED',
    'FAILED',
    'NOT_FOUND'
);

-- Webhook registrations for external providers
CREATE TABLE webhook_registrations (
    webhook_id VARCHAR(100) PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    action_id VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    
    -- Webhook configuration
    webhook_url TEXT NOT NULL,
    secret_key VARCHAR(255) NOT NULL,
    status webhook_status NOT NULL DEFAULT 'ACTIVE',
    
    -- Timing
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason VARCHAR(255),
    
    -- Retry configuration
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 5,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    
    -- Metadata
    metadata JSONB,
    
    -- Constraints
    CONSTRAINT chk_webhook_expiry CHECK (expires_at > registered_at),
    CONSTRAINT chk_webhook_retry_count CHECK (retry_count >= 0 AND retry_count <= max_retries),
    CONSTRAINT chk_webhook_counts CHECK (success_count >= 0 AND failure_count >= 0)
);

-- Webhook delivery attempts and responses
CREATE TABLE webhook_delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id VARCHAR(100) NOT NULL REFERENCES webhook_registrations(webhook_id),
    attempt_number INTEGER NOT NULL,
    
    -- Request details
    request_payload JSONB,
    request_headers JSONB,
    
    -- Response details
    response_code INTEGER,
    response_body TEXT,
    response_headers JSONB,
    
    -- Timing
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_time_ms INTEGER,
    
    -- Status
    successful BOOLEAN NOT NULL DEFAULT false,
    error_message TEXT,
    
    -- Constraints
    CONSTRAINT chk_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_response_time CHECK (response_time_ms >= 0),
    CONSTRAINT uk_webhook_attempt UNIQUE(webhook_id, attempt_number)
);

-- =============================================================================
-- USER PREFERENCE TABLES
-- =============================================================================

-- Create notification channel enum
CREATE TYPE notification_channel AS ENUM (
    'PUSH',
    'EMAIL',
    'SMS',
    'IN_APP',
    'WEBHOOK',
    'WHATSAPP',
    'SLACK',
    'DISCORD',
    'TELEGRAM'
);

-- User notification preferences
CREATE TABLE user_notification_preferences (
    preference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL, -- e.g., 'transaction_rollback', 'ALL'
    active BOOLEAN NOT NULL DEFAULT true,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_user_notification_prefs UNIQUE(user_id, event_type)
);

-- Notification channels for user preferences
CREATE TABLE notification_channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    preference_id UUID NOT NULL REFERENCES user_notification_preferences(preference_id),
    channel_name notification_channel NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER DEFAULT 1,
    settings JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_channel_priority CHECK (priority > 0),
    CONSTRAINT uk_preference_channel UNIQUE(preference_id, channel_name)
);

-- User channel overrides (special cases)
CREATE TABLE user_channel_overrides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    channel_override notification_channel NOT NULL,
    action VARCHAR(10) NOT NULL CHECK (action IN ('ADD', 'REMOVE')),
    active BOOLEAN NOT NULL DEFAULT true,
    reason VARCHAR(255),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- Constraints
    CONSTRAINT uk_user_channel_override UNIQUE(user_id, event_type, channel_override)
);

-- User webhook preferences
CREATE TABLE user_webhook_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    webhook_url TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    secret_key VARCHAR(255),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_user_webhook UNIQUE(user_id, webhook_url)
);

-- User profile preferences (timezone, language, etc.)
CREATE TABLE user_profile_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT NOT NULL,
    
    -- Audit fields
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_user_profile_pref UNIQUE(user_id, preference_key)
);

-- User communication frequency preferences
CREATE TABLE user_communication_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    frequency_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL', -- LOW, NORMAL, HIGH
    max_daily_notifications INTEGER DEFAULT 50,
    max_weekly_notifications INTEGER DEFAULT 200,
    quiet_hours_start INTEGER CHECK (quiet_hours_start BETWEEN 0 AND 23),
    quiet_hours_end INTEGER CHECK (quiet_hours_end BETWEEN 0 AND 23),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_user_communication_prefs UNIQUE(user_id),
    CONSTRAINT chk_notification_limits CHECK (
        max_daily_notifications > 0 AND 
        max_weekly_notifications > 0 AND 
        max_weekly_notifications >= max_daily_notifications
    )
);

-- User privacy preferences
CREATE TABLE user_privacy_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    data_sharing_enabled BOOLEAN NOT NULL DEFAULT false,
    marketing_emails_enabled BOOLEAN NOT NULL DEFAULT true,
    analytics_tracking_enabled BOOLEAN NOT NULL DEFAULT true,
    third_party_sharing_enabled BOOLEAN NOT NULL DEFAULT false,
    data_retention_days INTEGER DEFAULT 2555, -- 7 years default
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_user_privacy_prefs UNIQUE(user_id),
    CONSTRAINT chk_data_retention CHECK (data_retention_days > 0)
);

-- =============================================================================
-- NOTIFICATION TEMPLATE TABLES
-- =============================================================================

-- Create template category enum
CREATE TYPE template_category AS ENUM (
    'TRANSACTION',
    'SECURITY',
    'SYSTEM',
    'MARKETING',
    'COMPLIANCE'
);

-- Notification templates
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    category template_category NOT NULL,
    locale VARCHAR(10) DEFAULT 'en',
    
    -- Template content
    title_template TEXT NOT NULL,
    message_template TEXT NOT NULL,
    email_subject_template TEXT,
    email_body_template TEXT,
    sms_template TEXT,
    action_url_template TEXT,
    
    -- Configuration
    enabled BOOLEAN NOT NULL DEFAULT true,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT uk_template_code_locale UNIQUE(code, locale)
);

-- =============================================================================
-- PERFORMANCE INDEXES
-- =============================================================================

-- Compensation audit indexes
CREATE INDEX idx_compensation_audit_transaction ON compensation_audit(transaction_id);
CREATE INDEX idx_compensation_audit_status ON compensation_audit(status);
CREATE INDEX idx_compensation_audit_type ON compensation_audit(compensation_type);
CREATE INDEX idx_compensation_audit_created ON compensation_audit(created_at);
CREATE INDEX idx_compensation_audit_action ON compensation_audit(action_id);
CREATE INDEX idx_compensation_audit_retry ON compensation_audit(status, retry_count) WHERE status = 'FAILED';

-- Compensation actions indexes
CREATE INDEX idx_compensation_actions_transaction ON compensation_actions(transaction_id);
CREATE INDEX idx_compensation_actions_status ON compensation_actions(status);
CREATE INDEX idx_compensation_actions_type ON compensation_actions(action_type);
CREATE INDEX idx_compensation_actions_retry ON compensation_actions(next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_compensation_actions_depends ON compensation_actions USING GIN(depends_on_actions);
CREATE INDEX idx_compensation_actions_execution_order ON compensation_actions(transaction_id, execution_order);

-- Webhook indexes
CREATE INDEX idx_webhook_registrations_transaction ON webhook_registrations(transaction_id);
CREATE INDEX idx_webhook_registrations_provider ON webhook_registrations(provider);
CREATE INDEX idx_webhook_registrations_status ON webhook_registrations(status);
CREATE INDEX idx_webhook_registrations_expires ON webhook_registrations(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_webhook_delivery_attempts_webhook ON webhook_delivery_attempts(webhook_id);
CREATE INDEX idx_webhook_delivery_attempts_time ON webhook_delivery_attempts(attempted_at);

-- User preference indexes
CREATE INDEX idx_user_notification_prefs_user ON user_notification_preferences(user_id);
CREATE INDEX idx_user_notification_prefs_event ON user_notification_preferences(event_type);
CREATE INDEX idx_user_notification_prefs_active ON user_notification_preferences(user_id, active) WHERE active = true;
CREATE INDEX idx_notification_channels_preference ON notification_channels(preference_id);
CREATE INDEX idx_notification_channels_enabled ON notification_channels(preference_id, enabled) WHERE enabled = true;
CREATE INDEX idx_user_channel_overrides_user ON user_channel_overrides(user_id);
CREATE INDEX idx_user_webhook_prefs_user ON user_webhook_preferences(user_id);
CREATE INDEX idx_user_profile_prefs_user ON user_profile_preferences(user_id);
CREATE INDEX idx_user_communication_prefs_user ON user_communication_preferences(user_id);
CREATE INDEX idx_user_privacy_prefs_user ON user_privacy_preferences(user_id);

-- Template indexes
CREATE INDEX idx_notification_templates_code ON notification_templates(code);
CREATE INDEX idx_notification_templates_category ON notification_templates(category);
CREATE INDEX idx_notification_templates_enabled ON notification_templates(enabled) WHERE enabled = true;
CREATE INDEX idx_notification_templates_locale ON notification_templates(locale);

-- =============================================================================
-- UPDATE TRIGGERS
-- =============================================================================

-- Add update triggers for updated_at fields
CREATE TRIGGER update_compensation_audit_updated_at 
    BEFORE UPDATE ON compensation_audit 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compensation_actions_updated_at 
    BEFORE UPDATE ON compensation_actions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_notification_prefs_updated_at 
    BEFORE UPDATE ON user_notification_preferences 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_webhook_prefs_updated_at 
    BEFORE UPDATE ON user_webhook_preferences 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_communication_prefs_updated_at 
    BEFORE UPDATE ON user_communication_preferences 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_privacy_prefs_updated_at 
    BEFORE UPDATE ON user_privacy_preferences 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_templates_updated_at 
    BEFORE UPDATE ON notification_templates 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- INITIAL DATA
-- =============================================================================

-- Insert default notification templates for transaction rollbacks
INSERT INTO notification_templates (code, name, category, title_template, message_template, email_subject_template, email_body_template, sms_template) VALUES 
('TRANSACTION_ROLLBACK_SENDER_EMAIL', 'Transaction Rollback - Sender Email', 'TRANSACTION', 
 'Transaction Reversed - ${transactionId}', 
 'Your transaction ${transactionId} for ${amount} ${currency} has been reversed. Reason: ${rollbackReason}',
 'Transaction Reversed - Action Required',
 'Dear ${recipientType}, <br><br>Your transaction ${transactionId} for ${amount} ${currency} has been reversed.<br><br>Reason: ${rollbackReason}<br><br>Expected refund timeline: ${refundTimeline}<br><br>If you have questions, contact support: ${supportContact}',
 'Transaction ${transactionId} reversed. Refund in ${refundTimeline}. Support: ${supportContact}'),

('TRANSACTION_ROLLBACK_RECIPIENT_EMAIL', 'Transaction Rollback - Recipient Email', 'TRANSACTION',
 'Expected Payment Cancelled - ${transactionId}',
 'The payment ${transactionId} for ${amount} ${currency} that you were expecting has been cancelled. Reason: ${rollbackReason}',
 'Expected Payment Cancelled',
 'Dear ${recipientType}, <br><br>The payment ${transactionId} for ${amount} ${currency} that you were expecting has been cancelled.<br><br>Reason: ${rollbackReason}<br><br>Alternative options: ${alternativeOptions}<br><br>If you have questions, contact support: ${supportContact}',
 'Expected payment ${transactionId} cancelled. Reason: ${rollbackReason}. Contact: ${supportContact}'),

('MERCHANT_TRANSACTION_ROLLBACK_EMAIL', 'Merchant Transaction Rollback', 'TRANSACTION',
 'Merchant Transaction Rollback - ${transactionId}',
 'Merchant transaction ${transactionId} for ${amount} ${currency} has been rolled back. Settlement impact: ${settlementImpact}',
 'Merchant Transaction Rollback Notification',
 'Dear ${merchantName}, <br><br>Transaction ${transactionId} for ${amount} ${currency} has been rolled back.<br><br>Settlement Impact: ${settlementImpact}<br><br>Reporting Period: ${reportingPeriod}<br><br>Compliance Requirements: ${complianceRequirements}',
 'Merchant rollback ${transactionId}. Settlement impact: ${settlementImpact}'),

('TRANSACTION_ROLLBACK_SENDER_SMS', 'Transaction Rollback - Sender SMS', 'TRANSACTION',
 'Transaction Reversed',
 'Transaction ${transactionId} reversed. Refund in ${refundTimeline}.',
 NULL, NULL,
 'Transaction ${transactionId} reversed. Refund in ${refundTimeline}. Support: ${supportContact}'),

('TRANSACTION_ROLLBACK_PUSH_SENDER', 'Transaction Rollback - Push Notification', 'TRANSACTION',
 'Transaction Reversed',
 'Your transaction has been reversed. Tap for details.',
 NULL, NULL, NULL)

ON CONFLICT (code, locale) DO NOTHING;

-- =============================================================================
-- COMMENTS
-- =============================================================================

COMMENT ON TABLE compensation_audit IS 'Comprehensive audit trail for all compensation operations during transaction rollbacks';
COMMENT ON TABLE compensation_actions IS 'Individual compensation actions with execution tracking and retry logic';
COMMENT ON TABLE webhook_registrations IS 'External provider webhook registrations for async compensation status updates';
COMMENT ON TABLE webhook_delivery_attempts IS 'Detailed log of webhook delivery attempts and responses';
COMMENT ON TABLE user_notification_preferences IS 'User preferences for notification channels per event type';
COMMENT ON TABLE notification_channels IS 'Notification channel configuration for user preferences';
COMMENT ON TABLE user_channel_overrides IS 'Special case channel overrides for specific scenarios';
COMMENT ON TABLE user_webhook_preferences IS 'User webhook configuration for custom notifications';
COMMENT ON TABLE user_profile_preferences IS 'General user profile preferences (timezone, language, etc.)';
COMMENT ON TABLE user_communication_preferences IS 'User communication frequency and timing preferences';
COMMENT ON TABLE user_privacy_preferences IS 'User privacy and data sharing preferences';
COMMENT ON TABLE notification_templates IS 'Notification message templates for various channels and scenarios';