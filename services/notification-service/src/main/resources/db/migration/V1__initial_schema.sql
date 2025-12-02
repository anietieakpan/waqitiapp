-- Notification Service Initial Schema
-- Created: 2025-09-27
-- Description: Multi-channel notification delivery and tracking schema

CREATE TABLE IF NOT EXISTS notification_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(100) UNIQUE NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    subject VARCHAR(255),
    body_text TEXT NOT NULL,
    body_html TEXT,
    variables TEXT[],
    locale VARCHAR(10) DEFAULT 'en_US',
    priority VARCHAR(20) DEFAULT 'NORMAL',
    is_active BOOLEAN DEFAULT TRUE,
    version INTEGER DEFAULT 1,
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    template_id VARCHAR(100),
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    subject VARCHAR(255),
    content TEXT NOT NULL,
    content_html TEXT,
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(20),
    recipient_device_token TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP,
    provider VARCHAR(50),
    provider_message_id VARCHAR(255),
    provider_response JSONB,
    context JSONB,
    tags TEXT[],
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_email (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    from_address VARCHAR(255) NOT NULL,
    from_name VARCHAR(255),
    to_addresses TEXT[] NOT NULL,
    cc_addresses TEXT[],
    bcc_addresses TEXT[],
    reply_to VARCHAR(255),
    subject VARCHAR(255) NOT NULL,
    body_text TEXT NOT NULL,
    body_html TEXT,
    attachments JSONB,
    headers JSONB,
    tracking_enabled BOOLEAN DEFAULT TRUE,
    opened BOOLEAN DEFAULT FALSE,
    opened_at TIMESTAMP,
    open_count INTEGER DEFAULT 0,
    clicked BOOLEAN DEFAULT FALSE,
    clicked_at TIMESTAMP,
    click_count INTEGER DEFAULT 0,
    bounced BOOLEAN DEFAULT FALSE,
    bounce_type VARCHAR(20),
    bounce_reason TEXT,
    complained BOOLEAN DEFAULT FALSE,
    unsubscribed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_email FOREIGN KEY (notification_id) REFERENCES notification(notification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notification_sms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    from_number VARCHAR(20) NOT NULL,
    to_number VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    message_length INTEGER NOT NULL,
    segment_count INTEGER DEFAULT 1,
    encoding VARCHAR(20) DEFAULT 'GSM-7',
    is_unicode BOOLEAN DEFAULT FALSE,
    status_callback_url TEXT,
    delivery_status VARCHAR(20),
    delivery_status_updated_at TIMESTAMP,
    error_code VARCHAR(20),
    error_message TEXT,
    carrier VARCHAR(100),
    country_code VARCHAR(3),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_sms FOREIGN KEY (notification_id) REFERENCES notification(notification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notification_push (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    device_token TEXT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    badge_count INTEGER,
    sound VARCHAR(100),
    icon VARCHAR(100),
    image_url TEXT,
    action_url TEXT,
    category VARCHAR(50),
    thread_id VARCHAR(100),
    custom_data JSONB,
    time_to_live INTEGER,
    collapse_key VARCHAR(100),
    delivery_priority VARCHAR(20) DEFAULT 'HIGH',
    is_background BOOLEAN DEFAULT FALSE,
    clicked BOOLEAN DEFAULT FALSE,
    clicked_at TIMESTAMP,
    dismissed BOOLEAN DEFAULT FALSE,
    dismissed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_push FOREIGN KEY (notification_id) REFERENCES notification(notification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notification_in_app (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    notification_category VARCHAR(50) NOT NULL,
    action_type VARCHAR(50),
    action_url TEXT,
    action_data JSONB,
    icon VARCHAR(100),
    image_url TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    is_dismissed BOOLEAN DEFAULT FALSE,
    dismissed_at TIMESTAMP,
    is_archived BOOLEAN DEFAULT FALSE,
    archived_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_in_app FOREIGN KEY (notification_id) REFERENCES notification(notification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notification_preference (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID UNIQUE NOT NULL,
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT TRUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    in_app_enabled BOOLEAN DEFAULT TRUE,
    marketing_email BOOLEAN DEFAULT FALSE,
    marketing_sms BOOLEAN DEFAULT FALSE,
    marketing_push BOOLEAN DEFAULT FALSE,
    transactional_email BOOLEAN DEFAULT TRUE,
    transactional_sms BOOLEAN DEFAULT TRUE,
    transactional_push BOOLEAN DEFAULT TRUE,
    security_email BOOLEAN DEFAULT TRUE,
    security_sms BOOLEAN DEFAULT TRUE,
    security_push BOOLEAN DEFAULT TRUE,
    account_alerts_email BOOLEAN DEFAULT TRUE,
    account_alerts_sms BOOLEAN DEFAULT FALSE,
    account_alerts_push BOOLEAN DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN DEFAULT FALSE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    quiet_hours_timezone VARCHAR(50),
    preferred_channel VARCHAR(20) DEFAULT 'EMAIL',
    preferences JSONB,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    subscription_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    is_subscribed BOOLEAN DEFAULT TRUE,
    subscribed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unsubscribed_at TIMESTAMP,
    unsubscribe_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id VARCHAR(100) UNIQUE NOT NULL,
    batch_name VARCHAR(255) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    template_id VARCHAR(100),
    total_recipients INTEGER NOT NULL,
    total_sent INTEGER DEFAULT 0,
    total_delivered INTEGER DEFAULT 0,
    total_failed INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    notification_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent TEXT,
    geolocation VARCHAR(100),
    device_type VARCHAR(50),
    event_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_event FOREIGN KEY (notification_id) REFERENCES notification(notification_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notification_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    channel VARCHAR(20) NOT NULL,
    total_sent INTEGER NOT NULL DEFAULT 0,
    total_delivered INTEGER DEFAULT 0,
    total_failed INTEGER DEFAULT 0,
    total_opened INTEGER DEFAULT 0,
    total_clicked INTEGER DEFAULT 0,
    total_bounced INTEGER DEFAULT 0,
    total_complained INTEGER DEFAULT 0,
    delivery_rate DECIMAL(5, 4),
    open_rate DECIMAL(5, 4),
    click_rate DECIMAL(5, 4),
    bounce_rate DECIMAL(5, 4),
    by_type JSONB,
    by_priority JSONB,
    by_status JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_notification_period_channel UNIQUE (period_start, period_end, channel)
);

-- Indexes for performance
CREATE INDEX idx_notification_template_type ON notification_template(template_type);
CREATE INDEX idx_notification_template_channel ON notification_template(channel);
CREATE INDEX idx_notification_template_active ON notification_template(is_active) WHERE is_active = true;

CREATE INDEX idx_notification_customer ON notification(customer_id);
CREATE INDEX idx_notification_template ON notification(template_id);
CREATE INDEX idx_notification_type ON notification(notification_type);
CREATE INDEX idx_notification_channel ON notification(channel);
CREATE INDEX idx_notification_status ON notification(status);
CREATE INDEX idx_notification_priority ON notification(priority);
CREATE INDEX idx_notification_scheduled ON notification(scheduled_at) WHERE scheduled_at IS NOT NULL;
CREATE INDEX idx_notification_created ON notification(created_at DESC);
CREATE INDEX idx_notification_pending ON notification(status) WHERE status = 'PENDING';
CREATE INDEX idx_notification_retry ON notification(next_retry_at) WHERE next_retry_at IS NOT NULL;

CREATE INDEX idx_notification_email_notification ON notification_email(notification_id);
CREATE INDEX idx_notification_email_opened ON notification_email(opened);
CREATE INDEX idx_notification_email_clicked ON notification_email(clicked);

CREATE INDEX idx_notification_sms_notification ON notification_sms(notification_id);
CREATE INDEX idx_notification_sms_to ON notification_sms(to_number);

CREATE INDEX idx_notification_push_notification ON notification_push(notification_id);
CREATE INDEX idx_notification_push_platform ON notification_push(platform);

CREATE INDEX idx_notification_in_app_notification ON notification_in_app(notification_id);
CREATE INDEX idx_notification_in_app_read ON notification_in_app(is_read);
CREATE INDEX idx_notification_in_app_dismissed ON notification_in_app(is_dismissed);

CREATE INDEX idx_notification_preference_customer ON notification_preference(customer_id);

CREATE INDEX idx_notification_subscription_customer ON notification_subscription(customer_id);
CREATE INDEX idx_notification_subscription_topic ON notification_subscription(topic);
CREATE INDEX idx_notification_subscription_active ON notification_subscription(is_subscribed) WHERE is_subscribed = true;

CREATE INDEX idx_notification_batch_status ON notification_batch(status);
CREATE INDEX idx_notification_batch_channel ON notification_batch(channel);
CREATE INDEX idx_notification_batch_scheduled ON notification_batch(scheduled_at);

CREATE INDEX idx_notification_event_notification ON notification_event(notification_id);
CREATE INDEX idx_notification_event_type ON notification_event(event_type);
CREATE INDEX idx_notification_event_timestamp ON notification_event(event_timestamp DESC);

CREATE INDEX idx_notification_stats_period ON notification_statistics(period_end DESC);
CREATE INDEX idx_notification_stats_channel ON notification_statistics(channel);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_notification_template_updated_at BEFORE UPDATE ON notification_template
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_updated_at BEFORE UPDATE ON notification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_preference_updated_at BEFORE UPDATE ON notification_preference
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_subscription_updated_at BEFORE UPDATE ON notification_subscription
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_batch_updated_at BEFORE UPDATE ON notification_batch
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();