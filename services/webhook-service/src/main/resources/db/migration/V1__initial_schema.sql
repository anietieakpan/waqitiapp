-- Webhook Service Initial Schema
-- Created: 2025-09-27
-- Description: Webhook registration, delivery, and management schema

CREATE TABLE IF NOT EXISTS webhook_endpoint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID,
    application_id VARCHAR(100),
    endpoint_url VARCHAR(1000) NOT NULL,
    endpoint_name VARCHAR(255),
    description TEXT,
    secret_key_encrypted VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    http_method VARCHAR(10) DEFAULT 'POST',
    content_type VARCHAR(100) DEFAULT 'application/json',
    timeout_seconds INTEGER DEFAULT 30,
    max_retries INTEGER DEFAULT 3,
    retry_backoff_seconds INTEGER DEFAULT 60,
    event_types TEXT[] NOT NULL,
    event_filters JSONB,
    custom_headers JSONB,
    signature_algorithm VARCHAR(50) DEFAULT 'HMAC-SHA256',
    verify_ssl BOOLEAN DEFAULT TRUE,
    rate_limit_requests INTEGER DEFAULT 100,
    rate_limit_window_seconds INTEGER DEFAULT 60,
    delivery_attempts INTEGER DEFAULT 0,
    successful_deliveries INTEGER DEFAULT 0,
    failed_deliveries INTEGER DEFAULT 0,
    last_successful_delivery TIMESTAMP,
    last_failed_delivery TIMESTAMP,
    last_delivery_status VARCHAR(20),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version VARCHAR(20) DEFAULT '1.0',
    source_service VARCHAR(100) NOT NULL,
    source_id VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    aggregate_id VARCHAR(100),
    aggregate_version INTEGER,
    customer_id UUID,
    tenant_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id VARCHAR(100) UNIQUE NOT NULL,
    endpoint_id VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempted_at TIMESTAMP,
    completed_at TIMESTAMP,
    http_status_code INTEGER,
    response_body TEXT,
    response_headers JSONB,
    error_message TEXT,
    processing_time_ms INTEGER,
    payload JSONB NOT NULL,
    signature VARCHAR(255),
    next_retry_at TIMESTAMP,
    is_retry BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_delivery_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(endpoint_id) ON DELETE CASCADE,
    CONSTRAINT fk_webhook_delivery_event FOREIGN KEY (event_id) REFERENCES webhook_event(event_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS webhook_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    application_id VARCHAR(100),
    subscription_name VARCHAR(255) NOT NULL,
    description TEXT,
    event_types TEXT[] NOT NULL,
    event_filters JSONB,
    endpoint_url VARCHAR(1000) NOT NULL,
    secret_key_encrypted VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(100) UNIQUE NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    template_version VARCHAR(20) DEFAULT '1.0',
    payload_template JSONB NOT NULL,
    transformation_rules JSONB,
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id VARCHAR(100) UNIQUE NOT NULL,
    queue_name VARCHAR(255) NOT NULL,
    queue_type VARCHAR(50) NOT NULL,
    priority INTEGER DEFAULT 100,
    max_size INTEGER DEFAULT 10000,
    current_size INTEGER DEFAULT 0,
    processing_rate_per_second INTEGER DEFAULT 10,
    dead_letter_queue_id VARCHAR(100),
    retention_hours INTEGER DEFAULT 72,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_queue_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id VARCHAR(100) UNIQUE NOT NULL,
    queue_id VARCHAR(100) NOT NULL,
    delivery_id VARCHAR(100) NOT NULL,
    priority INTEGER DEFAULT 100,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    enqueued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dequeued_at TIMESTAMP,
    processed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_attempt_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_queue_item_queue FOREIGN KEY (queue_id) REFERENCES webhook_queue(queue_id) ON DELETE CASCADE,
    CONSTRAINT fk_webhook_queue_item_delivery FOREIGN KEY (delivery_id) REFERENCES webhook_delivery(delivery_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS webhook_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    log_id VARCHAR(100) UNIQUE NOT NULL,
    endpoint_id VARCHAR(100),
    delivery_id VARCHAR(100),
    log_level VARCHAR(20) NOT NULL,
    log_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    details JSONB,
    correlation_id VARCHAR(100),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_rate_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id VARCHAR(100) NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_duration_seconds INTEGER NOT NULL,
    request_count INTEGER DEFAULT 0,
    limit_exceeded BOOLEAN DEFAULT FALSE,
    reset_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_rate_limit FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(endpoint_id) ON DELETE CASCADE,
    CONSTRAINT unique_endpoint_window UNIQUE (endpoint_id, window_start)
);

CREATE TABLE IF NOT EXISTS webhook_health_check (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id VARCHAR(100) UNIQUE NOT NULL,
    endpoint_id VARCHAR(100) NOT NULL,
    check_type VARCHAR(20) NOT NULL DEFAULT 'PING',
    status VARCHAR(20) NOT NULL,
    response_time_ms INTEGER,
    http_status_code INTEGER,
    error_message TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_check_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_health_check FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(endpoint_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS webhook_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    endpoint_id VARCHAR(100),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_events INTEGER DEFAULT 0,
    successful_deliveries INTEGER DEFAULT 0,
    failed_deliveries INTEGER DEFAULT 0,
    retry_attempts INTEGER DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    success_rate DECIMAL(5, 4),
    error_rate DECIMAL(5, 4),
    by_event_type JSONB,
    by_status_code JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_analytics FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(endpoint_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS webhook_transformation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transformation_id VARCHAR(100) UNIQUE NOT NULL,
    endpoint_id VARCHAR(100) NOT NULL,
    transformation_name VARCHAR(255) NOT NULL,
    transformation_type VARCHAR(50) NOT NULL,
    input_schema JSONB,
    output_schema JSONB,
    transformation_rules JSONB NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    version INTEGER DEFAULT 1,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_transformation FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(endpoint_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS webhook_security_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id VARCHAR(100) UNIQUE NOT NULL,
    policy_name VARCHAR(255) NOT NULL,
    ip_whitelist TEXT[],
    ip_blacklist TEXT[],
    allowed_user_agents TEXT[],
    blocked_user_agents TEXT[],
    require_https BOOLEAN DEFAULT TRUE,
    max_payload_size_bytes INTEGER DEFAULT 1048576,
    signature_required BOOLEAN DEFAULT TRUE,
    timestamp_tolerance_seconds INTEGER DEFAULT 300,
    rate_limit_per_minute INTEGER DEFAULT 60,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS webhook_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_endpoints INTEGER DEFAULT 0,
    active_endpoints INTEGER DEFAULT 0,
    total_events INTEGER DEFAULT 0,
    total_deliveries INTEGER DEFAULT 0,
    successful_deliveries INTEGER DEFAULT 0,
    failed_deliveries INTEGER DEFAULT 0,
    retry_attempts INTEGER DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    success_rate DECIMAL(5, 4),
    by_event_type JSONB,
    by_http_status JSONB,
    top_failing_endpoints JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_webhook_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_webhook_endpoint_customer ON webhook_endpoint(customer_id);
CREATE INDEX idx_webhook_endpoint_application ON webhook_endpoint(application_id);
CREATE INDEX idx_webhook_endpoint_active ON webhook_endpoint(is_active) WHERE is_active = true;
CREATE INDEX idx_webhook_endpoint_event_types ON webhook_endpoint USING gin(event_types);

CREATE INDEX idx_webhook_event_type ON webhook_event(event_type);
CREATE INDEX idx_webhook_event_source ON webhook_event(source_service);
CREATE INDEX idx_webhook_event_customer ON webhook_event(customer_id);
CREATE INDEX idx_webhook_event_timestamp ON webhook_event(timestamp DESC);
CREATE INDEX idx_webhook_event_correlation ON webhook_event(correlation_id);

CREATE INDEX idx_webhook_delivery_endpoint ON webhook_delivery(endpoint_id);
CREATE INDEX idx_webhook_delivery_event ON webhook_delivery(event_id);
CREATE INDEX idx_webhook_delivery_status ON webhook_delivery(status);
CREATE INDEX idx_webhook_delivery_scheduled ON webhook_delivery(scheduled_at);
CREATE INDEX idx_webhook_delivery_retry ON webhook_delivery(next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_webhook_delivery_pending ON webhook_delivery(status) WHERE status = 'PENDING';

CREATE INDEX idx_webhook_subscription_customer ON webhook_subscription(customer_id);
CREATE INDEX idx_webhook_subscription_application ON webhook_subscription(application_id);
CREATE INDEX idx_webhook_subscription_active ON webhook_subscription(is_active) WHERE is_active = true;
CREATE INDEX idx_webhook_subscription_event_types ON webhook_subscription USING gin(event_types);

CREATE INDEX idx_webhook_template_event_type ON webhook_template(event_type);
CREATE INDEX idx_webhook_template_active ON webhook_template(is_active) WHERE is_active = true;
CREATE INDEX idx_webhook_template_default ON webhook_template(is_default) WHERE is_default = true;

CREATE INDEX idx_webhook_queue_type ON webhook_queue(queue_type);
CREATE INDEX idx_webhook_queue_active ON webhook_queue(is_active) WHERE is_active = true;

CREATE INDEX idx_webhook_queue_item_queue ON webhook_queue_item(queue_id);
CREATE INDEX idx_webhook_queue_item_delivery ON webhook_queue_item(delivery_id);
CREATE INDEX idx_webhook_queue_item_status ON webhook_queue_item(status);
CREATE INDEX idx_webhook_queue_item_next_attempt ON webhook_queue_item(next_attempt_at);

CREATE INDEX idx_webhook_log_endpoint ON webhook_log(endpoint_id);
CREATE INDEX idx_webhook_log_delivery ON webhook_log(delivery_id);
CREATE INDEX idx_webhook_log_level ON webhook_log(log_level);
CREATE INDEX idx_webhook_log_timestamp ON webhook_log(timestamp DESC);

CREATE INDEX idx_webhook_rate_limit_endpoint ON webhook_rate_limit(endpoint_id);
CREATE INDEX idx_webhook_rate_limit_reset ON webhook_rate_limit(reset_at);

CREATE INDEX idx_webhook_health_check_endpoint ON webhook_health_check(endpoint_id);
CREATE INDEX idx_webhook_health_check_status ON webhook_health_check(status);
CREATE INDEX idx_webhook_health_check_next ON webhook_health_check(next_check_at);

CREATE INDEX idx_webhook_analytics_endpoint ON webhook_analytics(endpoint_id);
CREATE INDEX idx_webhook_analytics_period ON webhook_analytics(period_end DESC);

CREATE INDEX idx_webhook_transformation_endpoint ON webhook_transformation(endpoint_id);
CREATE INDEX idx_webhook_transformation_active ON webhook_transformation(is_active) WHERE is_active = true;

CREATE INDEX idx_webhook_security_policy_active ON webhook_security_policy(is_active) WHERE is_active = true;

CREATE INDEX idx_webhook_statistics_period ON webhook_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_webhook_endpoint_updated_at BEFORE UPDATE ON webhook_endpoint
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_webhook_subscription_updated_at BEFORE UPDATE ON webhook_subscription
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_webhook_template_updated_at BEFORE UPDATE ON webhook_template
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_webhook_queue_updated_at BEFORE UPDATE ON webhook_queue
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_webhook_transformation_updated_at BEFORE UPDATE ON webhook_transformation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_webhook_security_policy_updated_at BEFORE UPDATE ON webhook_security_policy
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();