-- WebSocket Service Initial Schema
-- Created: 2025-09-27
-- Description: WebSocket connection management and real-time messaging schema

CREATE TABLE IF NOT EXISTS websocket_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id VARCHAR(100) UNIQUE NOT NULL,
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    customer_id UUID,
    device_id VARCHAR(100),
    connection_status VARCHAR(20) NOT NULL DEFAULT 'CONNECTED',
    connection_type VARCHAR(50) NOT NULL,
    client_info JSONB,
    server_node VARCHAR(100) NOT NULL,
    ip_address VARCHAR(50),
    user_agent TEXT,
    protocol_version VARCHAR(20),
    subprotocols TEXT[],
    extensions TEXT[],
    heartbeat_interval_seconds INTEGER DEFAULT 30,
    last_heartbeat_at TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    connected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disconnected_at TIMESTAMP,
    disconnect_reason VARCHAR(100),
    bytes_sent BIGINT DEFAULT 0,
    bytes_received BIGINT DEFAULT 0,
    messages_sent INTEGER DEFAULT 0,
    messages_received INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id VARCHAR(100) UNIQUE NOT NULL,
    connection_id VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    topic_type VARCHAR(50) NOT NULL,
    subscription_filters JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    subscribed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unsubscribed_at TIMESTAMP,
    message_count INTEGER DEFAULT 0,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_websocket_subscription FOREIGN KEY (connection_id) REFERENCES websocket_connection(connection_id) ON DELETE CASCADE,
    CONSTRAINT unique_connection_topic UNIQUE (connection_id, topic)
);

CREATE TABLE IF NOT EXISTS websocket_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id VARCHAR(100) UNIQUE NOT NULL,
    connection_id VARCHAR(100),
    message_type VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    topic VARCHAR(255),
    payload JSONB NOT NULL,
    payload_size_bytes INTEGER NOT NULL,
    message_format VARCHAR(20) DEFAULT 'JSON',
    correlation_id VARCHAR(100),
    reply_to VARCHAR(100),
    priority INTEGER DEFAULT 100,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_websocket_message FOREIGN KEY (connection_id) REFERENCES websocket_connection(connection_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS websocket_room (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id VARCHAR(100) UNIQUE NOT NULL,
    room_name VARCHAR(255) NOT NULL,
    room_type VARCHAR(50) NOT NULL,
    description TEXT,
    is_private BOOLEAN DEFAULT FALSE,
    max_connections INTEGER DEFAULT 1000,
    current_connections INTEGER DEFAULT 0,
    owner_id VARCHAR(100),
    access_control JSONB,
    room_metadata JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_room_membership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id VARCHAR(100) UNIQUE NOT NULL,
    room_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100),
    role VARCHAR(50) DEFAULT 'MEMBER',
    permissions TEXT[],
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_membership_room FOREIGN KEY (room_id) REFERENCES websocket_room(room_id) ON DELETE CASCADE,
    CONSTRAINT fk_room_membership_connection FOREIGN KEY (connection_id) REFERENCES websocket_connection(connection_id) ON DELETE CASCADE,
    CONSTRAINT unique_room_connection UNIQUE (room_id, connection_id)
);

CREATE TABLE IF NOT EXISTS websocket_broadcast (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    broadcast_id VARCHAR(100) UNIQUE NOT NULL,
    broadcast_name VARCHAR(255),
    broadcast_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_criteria JSONB NOT NULL,
    message JSONB NOT NULL,
    priority INTEGER DEFAULT 100,
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_targets INTEGER DEFAULT 0,
    successful_deliveries INTEGER DEFAULT 0,
    failed_deliveries INTEGER DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    target_user_id VARCHAR(100),
    target_connection_id VARCHAR(100),
    target_room_id VARCHAR(100),
    title VARCHAR(255),
    message TEXT NOT NULL,
    payload JSONB,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    expires_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_presence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presence_id VARCHAR(100) UNIQUE NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    custom_status VARCHAR(255),
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    presence_data JSONB,
    location VARCHAR(100),
    device_type VARCHAR(50),
    activity VARCHAR(100),
    is_invisible BOOLEAN DEFAULT FALSE,
    auto_away_minutes INTEGER DEFAULT 15,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_websocket_presence FOREIGN KEY (connection_id) REFERENCES websocket_connection(connection_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS websocket_rate_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id VARCHAR(100) NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_duration_seconds INTEGER NOT NULL DEFAULT 60,
    message_count INTEGER DEFAULT 0,
    bytes_count BIGINT DEFAULT 0,
    limit_exceeded BOOLEAN DEFAULT FALSE,
    reset_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_websocket_rate_limit FOREIGN KEY (connection_id) REFERENCES websocket_connection(connection_id) ON DELETE CASCADE,
    CONSTRAINT unique_connection_window UNIQUE (connection_id, window_start)
);

CREATE TABLE IF NOT EXISTS websocket_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_connections INTEGER DEFAULT 0,
    peak_concurrent_connections INTEGER DEFAULT 0,
    avg_concurrent_connections DECIMAL(10, 2),
    total_messages BIGINT DEFAULT 0,
    messages_per_second DECIMAL(10, 2),
    total_bytes BIGINT DEFAULT 0,
    avg_connection_duration_seconds DECIMAL(10, 2),
    by_connection_type JSONB,
    by_message_type JSONB,
    by_topic JSONB,
    performance_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_error_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    error_id VARCHAR(100) UNIQUE NOT NULL,
    connection_id VARCHAR(100),
    error_type VARCHAR(50) NOT NULL,
    error_code VARCHAR(20),
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    context JSONB,
    severity VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_health_check (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id VARCHAR(100) UNIQUE NOT NULL,
    server_node VARCHAR(100) NOT NULL,
    check_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_time_ms INTEGER,
    active_connections INTEGER,
    memory_usage_mb INTEGER,
    cpu_usage_percent DECIMAL(5, 2),
    error_message TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id VARCHAR(100) UNIQUE NOT NULL,
    config_name VARCHAR(255) NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    config_value JSONB NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    last_modified_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS websocket_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_connections INTEGER DEFAULT 0,
    unique_users INTEGER DEFAULT 0,
    peak_concurrent_connections INTEGER DEFAULT 0,
    total_messages BIGINT DEFAULT 0,
    total_bytes BIGINT DEFAULT 0,
    avg_connection_duration_minutes DECIMAL(10, 2),
    connection_success_rate DECIMAL(5, 4),
    message_delivery_rate DECIMAL(5, 4),
    by_connection_type JSONB,
    by_device_type JSONB,
    error_rate DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_websocket_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_websocket_connection_user ON websocket_connection(user_id);
CREATE INDEX idx_websocket_connection_customer ON websocket_connection(customer_id);
CREATE INDEX idx_websocket_connection_device ON websocket_connection(device_id);
CREATE INDEX idx_websocket_connection_status ON websocket_connection(connection_status);
CREATE INDEX idx_websocket_connection_node ON websocket_connection(server_node);
CREATE INDEX idx_websocket_connection_connected ON websocket_connection(connected_at DESC);
CREATE INDEX idx_websocket_connection_active ON websocket_connection(connection_status) WHERE connection_status = 'CONNECTED';

CREATE INDEX idx_websocket_subscription_connection ON websocket_subscription(connection_id);
CREATE INDEX idx_websocket_subscription_topic ON websocket_subscription(topic);
CREATE INDEX idx_websocket_subscription_type ON websocket_subscription(topic_type);
CREATE INDEX idx_websocket_subscription_active ON websocket_subscription(is_active) WHERE is_active = true;

CREATE INDEX idx_websocket_message_connection ON websocket_message(connection_id);
CREATE INDEX idx_websocket_message_type ON websocket_message(message_type);
CREATE INDEX idx_websocket_message_topic ON websocket_message(topic);
CREATE INDEX idx_websocket_message_timestamp ON websocket_message(timestamp DESC);
CREATE INDEX idx_websocket_message_direction ON websocket_message(direction);

CREATE INDEX idx_websocket_room_type ON websocket_room(room_type);
CREATE INDEX idx_websocket_room_owner ON websocket_room(owner_id);
CREATE INDEX idx_websocket_room_active ON websocket_room(is_active) WHERE is_active = true;
CREATE INDEX idx_websocket_room_private ON websocket_room(is_private);

CREATE INDEX idx_websocket_room_membership_room ON websocket_room_membership(room_id);
CREATE INDEX idx_websocket_room_membership_connection ON websocket_room_membership(connection_id);
CREATE INDEX idx_websocket_room_membership_user ON websocket_room_membership(user_id);
CREATE INDEX idx_websocket_room_membership_active ON websocket_room_membership(is_active) WHERE is_active = true;

CREATE INDEX idx_websocket_broadcast_type ON websocket_broadcast(broadcast_type);
CREATE INDEX idx_websocket_broadcast_status ON websocket_broadcast(status);
CREATE INDEX idx_websocket_broadcast_scheduled ON websocket_broadcast(scheduled_at);

CREATE INDEX idx_websocket_notification_type ON websocket_notification(notification_type);
CREATE INDEX idx_websocket_notification_user ON websocket_notification(target_user_id);
CREATE INDEX idx_websocket_notification_status ON websocket_notification(status);
CREATE INDEX idx_websocket_notification_scheduled ON websocket_notification(scheduled_at);

CREATE INDEX idx_websocket_presence_user ON websocket_presence(user_id);
CREATE INDEX idx_websocket_presence_connection ON websocket_presence(connection_id);
CREATE INDEX idx_websocket_presence_status ON websocket_presence(status);
CREATE INDEX idx_websocket_presence_last_seen ON websocket_presence(last_seen_at DESC);

CREATE INDEX idx_websocket_rate_limit_connection ON websocket_rate_limit(connection_id);
CREATE INDEX idx_websocket_rate_limit_reset ON websocket_rate_limit(reset_at);

CREATE INDEX idx_websocket_analytics_period ON websocket_analytics(period_end DESC);

CREATE INDEX idx_websocket_error_log_connection ON websocket_error_log(connection_id);
CREATE INDEX idx_websocket_error_log_type ON websocket_error_log(error_type);
CREATE INDEX idx_websocket_error_log_severity ON websocket_error_log(severity);
CREATE INDEX idx_websocket_error_log_occurred ON websocket_error_log(occurred_at DESC);

CREATE INDEX idx_websocket_health_check_node ON websocket_health_check(server_node);
CREATE INDEX idx_websocket_health_check_type ON websocket_health_check(check_type);
CREATE INDEX idx_websocket_health_check_checked ON websocket_health_check(checked_at DESC);

CREATE INDEX idx_websocket_configuration_type ON websocket_configuration(config_type);
CREATE INDEX idx_websocket_configuration_active ON websocket_configuration(is_active) WHERE is_active = true;

CREATE INDEX idx_websocket_statistics_period ON websocket_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_websocket_connection_updated_at BEFORE UPDATE ON websocket_connection
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_websocket_room_updated_at BEFORE UPDATE ON websocket_room
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_websocket_broadcast_updated_at BEFORE UPDATE ON websocket_broadcast
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_websocket_notification_updated_at BEFORE UPDATE ON websocket_notification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_websocket_presence_updated_at BEFORE UPDATE ON websocket_presence
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_websocket_configuration_updated_at BEFORE UPDATE ON websocket_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();