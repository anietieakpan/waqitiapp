-- =============================================================================
-- WAQITI NOTIFICATION SERVICE - COMPENSATION TABLES
-- Version: V3
-- Description: Additional notification tables for compensation system
-- Author: Waqiti Engineering Team
-- Date: 2024-01-15
-- =============================================================================

-- Create notification priority enum
CREATE TYPE notification_priority AS ENUM (
    'LOW',
    'NORMAL', 
    'HIGH',
    'CRITICAL'
);

-- Create notification status enum
CREATE TYPE notification_status AS ENUM (
    'PENDING',
    'QUEUED',
    'PROCESSING',
    'SENT',
    'DELIVERED',
    'FAILED',
    'CANCELLED',
    'EXPIRED'
);

-- Create delivery channel enum (matches NotificationChannel in code)
CREATE TYPE delivery_channel AS ENUM (
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

-- =============================================================================
-- NOTIFICATION DELIVERY TABLES
-- =============================================================================

-- Notification delivery queue
CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    
    -- Content
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    channel delivery_channel NOT NULL,
    
    -- Priority and routing
    priority notification_priority NOT NULL DEFAULT 'NORMAL',
    status notification_status NOT NULL DEFAULT 'PENDING',
    
    -- Delivery configuration
    scheduled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    delivery_attempts INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    
    -- Results
    delivered_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    provider_response JSONB,
    
    -- Content details
    email_subject TEXT,
    email_body TEXT,
    sms_text TEXT,
    push_payload JSONB,
    action_url TEXT,
    
    -- Metadata
    context_data JSONB,
    delivery_options JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_delivery_attempts CHECK (delivery_attempts >= 0 AND delivery_attempts <= max_attempts),
    CONSTRAINT chk_max_attempts CHECK (max_attempts > 0),
    CONSTRAINT chk_scheduled_delivery CHECK (scheduled_at <= CURRENT_TIMESTAMP + INTERVAL '30 days')
);

-- Notification delivery attempts log
CREATE TABLE notification_delivery_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id UUID NOT NULL REFERENCES notification_deliveries(id),
    attempt_number INTEGER NOT NULL,
    
    -- Attempt details
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider VARCHAR(50) NOT NULL,
    provider_endpoint TEXT,
    
    -- Request details
    request_payload JSONB,
    request_headers JSONB,
    
    -- Response details
    response_code INTEGER,
    response_body TEXT,
    response_headers JSONB,
    response_time_ms INTEGER,
    
    -- Result
    successful BOOLEAN NOT NULL,
    error_type VARCHAR(50),
    error_message TEXT,
    retry_after_seconds INTEGER,
    
    -- Constraints
    CONSTRAINT chk_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_response_time CHECK (response_time_ms >= 0),
    CONSTRAINT uk_delivery_attempt UNIQUE(delivery_id, attempt_number)
);

-- Notification preferences cache (for performance)
CREATE TABLE notification_preference_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    
    -- Cached preferences
    enabled_channels delivery_channel[] NOT NULL,
    priority_order INTEGER[] NOT NULL,
    
    -- Cache metadata
    cache_key VARCHAR(255) NOT NULL,
    cached_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Constraints
    CONSTRAINT uk_preference_cache UNIQUE(user_id, event_type),
    CONSTRAINT chk_cache_expiry CHECK (expires_at > cached_at)
);

-- =============================================================================
-- ROLLBACK-SPECIFIC NOTIFICATION TABLES
-- =============================================================================

-- Rollback notification tracking
CREATE TABLE rollback_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    compensation_action_id VARCHAR(100) NOT NULL,
    
    -- Notification details
    recipient_type VARCHAR(50) NOT NULL, -- sender, recipient, merchant, admin
    recipient_user_id UUID,
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(20),
    
    -- Content details
    notification_type VARCHAR(100) NOT NULL, -- email, sms, push, in_app
    template_used VARCHAR(100) NOT NULL,
    subject TEXT,
    content TEXT,
    
    -- Delivery tracking
    status notification_status NOT NULL DEFAULT 'PENDING',
    priority notification_priority NOT NULL DEFAULT 'HIGH',
    
    -- Timing
    scheduled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    
    -- Error tracking
    delivery_attempts INTEGER DEFAULT 0,
    last_error TEXT,
    provider_reference VARCHAR(255),
    
    -- Metadata
    rollback_context JSONB,
    personalization_data JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_rollback_delivery_attempts CHECK (delivery_attempts >= 0),
    CONSTRAINT chk_rollback_recipient CHECK (
        (recipient_user_id IS NOT NULL) OR 
        (recipient_email IS NOT NULL) OR 
        (recipient_phone IS NOT NULL)
    )
);

-- Merchant notification settings
CREATE TABLE merchant_notification_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    
    -- Rollback notification preferences
    rollback_email_enabled BOOLEAN DEFAULT true,
    rollback_webhook_enabled BOOLEAN DEFAULT false,
    rollback_sms_enabled BOOLEAN DEFAULT false,
    
    -- Contact information
    notification_email VARCHAR(255),
    notification_phone VARCHAR(20),
    webhook_url TEXT,
    webhook_secret VARCHAR(255),
    
    -- Timing preferences
    business_hours_only BOOLEAN DEFAULT false,
    timezone VARCHAR(50) DEFAULT 'UTC',
    quiet_hours_start INTEGER CHECK (quiet_hours_start BETWEEN 0 AND 23),
    quiet_hours_end INTEGER CHECK (quiet_hours_end BETWEEN 0 AND 23),
    
    -- Frequency limits
    max_daily_rollback_notifications INTEGER DEFAULT 100,
    batch_notifications BOOLEAN DEFAULT false,
    batch_interval_minutes INTEGER DEFAULT 60,
    
    -- Compliance settings
    require_delivery_confirmation BOOLEAN DEFAULT true,
    include_pii BOOLEAN DEFAULT false,
    retention_days INTEGER DEFAULT 2555, -- 7 years
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_merchant_notification_settings UNIQUE(merchant_id),
    CONSTRAINT chk_batch_interval CHECK (batch_interval_minutes > 0),
    CONSTRAINT chk_max_daily_notifications CHECK (max_daily_rollback_notifications > 0),
    CONSTRAINT chk_retention_days CHECK (retention_days > 0)
);

-- =============================================================================
-- NOTIFICATION ANALYTICS TABLES
-- =============================================================================

-- Notification metrics and analytics
CREATE TABLE notification_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Dimensions
    date_hour TIMESTAMP WITH TIME ZONE NOT NULL, -- Hourly aggregation
    template_code VARCHAR(100),
    channel delivery_channel,
    priority notification_priority,
    user_segment VARCHAR(50), -- premium, standard, enterprise, etc.
    
    -- Metrics
    total_sent INTEGER NOT NULL DEFAULT 0,
    total_delivered INTEGER NOT NULL DEFAULT 0,
    total_failed INTEGER NOT NULL DEFAULT 0,
    total_opened INTEGER NOT NULL DEFAULT 0,
    total_clicked INTEGER NOT NULL DEFAULT 0,
    
    -- Performance metrics
    avg_delivery_time_seconds DECIMAL(10,2),
    avg_response_time_ms DECIMAL(10,2),
    delivery_rate DECIMAL(5,4), -- percentage as decimal (0.95 = 95%)
    
    -- Error analysis
    error_rate DECIMAL(5,4),
    top_error_type VARCHAR(100),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_metrics_counts CHECK (
        total_sent >= 0 AND total_delivered >= 0 AND total_failed >= 0 AND
        total_opened >= 0 AND total_clicked >= 0 AND
        total_delivered <= total_sent AND total_failed <= total_sent
    ),
    CONSTRAINT chk_delivery_rate CHECK (delivery_rate >= 0 AND delivery_rate <= 1),
    CONSTRAINT chk_error_rate CHECK (error_rate >= 0 AND error_rate <= 1)
);

-- =============================================================================
-- PERFORMANCE INDEXES
-- =============================================================================

-- Notification deliveries indexes
CREATE INDEX idx_notification_deliveries_user ON notification_deliveries(user_id);
CREATE INDEX idx_notification_deliveries_status ON notification_deliveries(status);
CREATE INDEX idx_notification_deliveries_channel ON notification_deliveries(channel);
CREATE INDEX idx_notification_deliveries_priority ON notification_deliveries(priority);
CREATE INDEX idx_notification_deliveries_scheduled ON notification_deliveries(scheduled_at) WHERE status IN ('PENDING', 'QUEUED');
CREATE INDEX idx_notification_deliveries_next_attempt ON notification_deliveries(next_attempt_at) WHERE next_attempt_at IS NOT NULL;
CREATE INDEX idx_notification_deliveries_template ON notification_deliveries(template_code);
CREATE INDEX idx_notification_deliveries_created ON notification_deliveries(created_at);

-- Notification delivery attempts indexes
CREATE INDEX idx_notification_attempts_delivery ON notification_delivery_attempts(delivery_id);
CREATE INDEX idx_notification_attempts_attempted ON notification_delivery_attempts(attempted_at);
CREATE INDEX idx_notification_attempts_provider ON notification_delivery_attempts(provider);
CREATE INDEX idx_notification_attempts_successful ON notification_delivery_attempts(successful);

-- Notification preference cache indexes
CREATE INDEX idx_preference_cache_user ON notification_preference_cache(user_id);
CREATE INDEX idx_preference_cache_expires ON notification_preference_cache(expires_at);
CREATE INDEX idx_preference_cache_key ON notification_preference_cache(cache_key);

-- Rollback notifications indexes
CREATE INDEX idx_rollback_notifications_transaction ON rollback_notifications(transaction_id);
CREATE INDEX idx_rollback_notifications_action ON rollback_notifications(compensation_action_id);
CREATE INDEX idx_rollback_notifications_recipient ON rollback_notifications(recipient_user_id);
CREATE INDEX idx_rollback_notifications_status ON rollback_notifications(status);
CREATE INDEX idx_rollback_notifications_type ON rollback_notifications(recipient_type);
CREATE INDEX idx_rollback_notifications_scheduled ON rollback_notifications(scheduled_at);

-- Merchant notification settings indexes
CREATE INDEX idx_merchant_notification_settings_merchant ON merchant_notification_settings(merchant_id);

-- Notification metrics indexes
CREATE INDEX idx_notification_metrics_date ON notification_metrics(date_hour);
CREATE INDEX idx_notification_metrics_template ON notification_metrics(template_code);
CREATE INDEX idx_notification_metrics_channel ON notification_metrics(channel);
CREATE INDEX idx_notification_metrics_priority ON notification_metrics(priority);
CREATE INDEX idx_notification_metrics_segment ON notification_metrics(user_segment);

-- Composite indexes for common queries
CREATE INDEX idx_notification_deliveries_user_status ON notification_deliveries(user_id, status);
CREATE INDEX idx_notification_deliveries_channel_priority ON notification_deliveries(channel, priority);
CREATE INDEX idx_rollback_notifications_transaction_type ON rollback_notifications(transaction_id, recipient_type);

-- =============================================================================
-- UPDATE TRIGGERS
-- =============================================================================

-- Add update triggers for updated_at fields
CREATE OR REPLACE FUNCTION update_notification_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_notification_deliveries_updated_at 
    BEFORE UPDATE ON notification_deliveries 
    FOR EACH ROW EXECUTE FUNCTION update_notification_updated_at_column();

CREATE TRIGGER update_rollback_notifications_updated_at 
    BEFORE UPDATE ON rollback_notifications 
    FOR EACH ROW EXECUTE FUNCTION update_notification_updated_at_column();

CREATE TRIGGER update_merchant_notification_settings_updated_at 
    BEFORE UPDATE ON merchant_notification_settings 
    FOR EACH ROW EXECUTE FUNCTION update_notification_updated_at_column();

CREATE TRIGGER update_notification_metrics_updated_at 
    BEFORE UPDATE ON notification_metrics 
    FOR EACH ROW EXECUTE FUNCTION update_notification_updated_at_column();

-- =============================================================================
-- VIEWS FOR COMMON QUERIES
-- =============================================================================

-- View for rollback notification summary
CREATE VIEW rollback_notification_summary AS
SELECT 
    rn.transaction_id,
    rn.compensation_action_id,
    COUNT(*) as total_notifications,
    COUNT(CASE WHEN rn.status = 'DELIVERED' THEN 1 END) as delivered_count,
    COUNT(CASE WHEN rn.status = 'FAILED' THEN 1 END) as failed_count,
    COUNT(CASE WHEN rn.status = 'PENDING' THEN 1 END) as pending_count,
    STRING_AGG(DISTINCT rn.recipient_type, ', ') as recipient_types,
    MIN(rn.created_at) as first_notification,
    MAX(rn.delivered_at) as last_delivered
FROM rollback_notifications rn
GROUP BY rn.transaction_id, rn.compensation_action_id;

-- View for notification delivery performance
CREATE VIEW notification_delivery_performance AS
SELECT 
    nd.channel,
    nd.priority,
    DATE_TRUNC('hour', nd.created_at) as hour,
    COUNT(*) as total_notifications,
    COUNT(CASE WHEN nd.status = 'DELIVERED' THEN 1 END) as delivered_count,
    COUNT(CASE WHEN nd.status = 'FAILED' THEN 1 END) as failed_count,
    AVG(CASE WHEN nd.delivered_at IS NOT NULL 
        THEN EXTRACT(EPOCH FROM nd.delivered_at - nd.created_at) END) as avg_delivery_time_seconds,
    AVG(nd.delivery_attempts) as avg_delivery_attempts
FROM notification_deliveries nd
WHERE nd.created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY nd.channel, nd.priority, DATE_TRUNC('hour', nd.created_at)
ORDER BY hour DESC, nd.channel, nd.priority;

-- =============================================================================
-- PARTITIONING PREPARATION
-- =============================================================================

-- Comments about partitioning for high-volume tables
COMMENT ON TABLE notification_deliveries IS 'High-volume table - consider partitioning by created_at for performance';
COMMENT ON TABLE notification_delivery_attempts IS 'High-volume table - consider partitioning by attempted_at for performance';
COMMENT ON TABLE notification_metrics IS 'Analytics table - consider partitioning by date_hour for performance';

-- =============================================================================
-- COMMENTS
-- =============================================================================

COMMENT ON TABLE notification_deliveries IS 'Main notification delivery queue with comprehensive tracking';
COMMENT ON TABLE notification_delivery_attempts IS 'Detailed log of all notification delivery attempts';
COMMENT ON TABLE notification_preference_cache IS 'Performance cache for user notification preferences';
COMMENT ON TABLE rollback_notifications IS 'Specialized tracking for transaction rollback notifications';
COMMENT ON TABLE merchant_notification_settings IS 'Merchant-specific notification configuration and preferences';
COMMENT ON TABLE notification_metrics IS 'Aggregated analytics and performance metrics for notifications';

COMMENT ON VIEW rollback_notification_summary IS 'Summary view of rollback notification status by transaction';
COMMENT ON VIEW notification_delivery_performance IS 'Performance metrics view for notification delivery analysis';