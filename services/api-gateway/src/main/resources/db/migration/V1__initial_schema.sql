-- API Gateway Initial Schema
-- Created: 2025-09-27
-- Description: API gateway routing, rate limiting, and request tracking schema

CREATE TABLE IF NOT EXISTS api_route (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id VARCHAR(100) UNIQUE NOT NULL,
    route_name VARCHAR(255) NOT NULL,
    route_pattern VARCHAR(500) NOT NULL,
    target_service VARCHAR(100) NOT NULL,
    target_url VARCHAR(1000) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_public BOOLEAN DEFAULT FALSE,
    require_auth BOOLEAN DEFAULT TRUE,
    require_roles TEXT[],
    rate_limit_rpm INTEGER DEFAULT 1000,
    timeout_seconds INTEGER DEFAULT 30,
    retry_attempts INTEGER DEFAULT 3,
    circuit_breaker_enabled BOOLEAN DEFAULT TRUE,
    load_balancer_strategy VARCHAR(50) DEFAULT 'ROUND_ROBIN',
    health_check_path VARCHAR(200),
    description TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(100) UNIQUE NOT NULL,
    route_id VARCHAR(100),
    user_id VARCHAR(100),
    customer_id UUID,
    session_id VARCHAR(100),
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(1000) NOT NULL,
    query_params JSONB,
    request_headers JSONB,
    request_body_size INTEGER DEFAULT 0,
    source_ip VARCHAR(50) NOT NULL,
    user_agent TEXT,
    referer TEXT,
    request_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_status INTEGER,
    response_size INTEGER DEFAULT 0,
    processing_time_ms INTEGER,
    target_service VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_request_route FOREIGN KEY (route_id) REFERENCES api_route(route_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS api_rate_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100),
    customer_id UUID,
    api_key VARCHAR(100),
    source_ip VARCHAR(50),
    route_id VARCHAR(100),
    limit_type VARCHAR(50) NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_duration_seconds INTEGER NOT NULL,
    request_count INTEGER DEFAULT 0,
    limit_exceeded BOOLEAN DEFAULT FALSE,
    reset_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_rate_limit_window UNIQUE (user_id, route_id, window_start)
);

CREATE TABLE IF NOT EXISTS api_authentication (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_id VARCHAR(100) UNIQUE NOT NULL,
    auth_type VARCHAR(50) NOT NULL,
    user_id VARCHAR(100),
    api_key VARCHAR(100),
    token_hash VARCHAR(255),
    client_id VARCHAR(100),
    scope TEXT[],
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,
    usage_count INTEGER DEFAULT 0,
    source_ip VARCHAR(50),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_circuit_breaker (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) UNIQUE NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    failure_count INTEGER DEFAULT 0,
    failure_threshold INTEGER DEFAULT 5,
    success_threshold INTEGER DEFAULT 3,
    timeout_seconds INTEGER DEFAULT 60,
    last_failure_time TIMESTAMP,
    next_attempt_time TIMESTAMP,
    total_requests INTEGER DEFAULT 0,
    successful_requests INTEGER DEFAULT 0,
    failed_requests INTEGER DEFAULT 0,
    average_response_time_ms DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_health_check (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id VARCHAR(100) UNIQUE NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    endpoint_url VARCHAR(1000) NOT NULL,
    check_type VARCHAR(50) NOT NULL DEFAULT 'HTTP',
    status VARCHAR(20) NOT NULL,
    response_time_ms INTEGER,
    http_status_code INTEGER,
    response_body TEXT,
    error_message TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_check_at TIMESTAMP,
    check_interval_seconds INTEGER DEFAULT 30,
    consecutive_failures INTEGER DEFAULT 0,
    consecutive_successes INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_load_balancer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL,
    instance_id VARCHAR(100) NOT NULL,
    instance_url VARCHAR(1000) NOT NULL,
    instance_weight INTEGER DEFAULT 100,
    is_healthy BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    current_connections INTEGER DEFAULT 0,
    max_connections INTEGER DEFAULT 1000,
    response_time_ms INTEGER,
    last_health_check TIMESTAMP,
    health_check_failures INTEGER DEFAULT 0,
    total_requests INTEGER DEFAULT 0,
    successful_requests INTEGER DEFAULT 0,
    failed_requests INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_service_instance UNIQUE (service_name, instance_id)
);

CREATE TABLE IF NOT EXISTS api_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cache_key VARCHAR(255) UNIQUE NOT NULL,
    cache_value JSONB NOT NULL,
    route_id VARCHAR(100),
    request_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(100),
    cache_size_bytes INTEGER NOT NULL,
    hit_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_accessed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_transformation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transformation_id VARCHAR(100) UNIQUE NOT NULL,
    route_id VARCHAR(100) NOT NULL,
    transformation_name VARCHAR(255) NOT NULL,
    transformation_type VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    transformation_rules JSONB NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    execution_order INTEGER DEFAULT 100,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_transformation FOREIGN KEY (route_id) REFERENCES api_route(route_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS api_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_requests BIGINT DEFAULT 0,
    successful_requests BIGINT DEFAULT 0,
    failed_requests BIGINT DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    p95_response_time_ms INTEGER,
    p99_response_time_ms INTEGER,
    total_data_transferred_bytes BIGINT DEFAULT 0,
    unique_users BIGINT DEFAULT 0,
    by_route JSONB,
    by_status_code JSONB,
    by_user_agent JSONB,
    top_error_routes JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_security_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source_ip VARCHAR(50) NOT NULL,
    user_id VARCHAR(100),
    route_id VARCHAR(100),
    description TEXT NOT NULL,
    event_data JSONB,
    blocked BOOLEAN DEFAULT FALSE,
    action_taken VARCHAR(100),
    risk_score INTEGER DEFAULT 0,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_configuration (
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

CREATE TABLE IF NOT EXISTS api_quota (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quota_id VARCHAR(100) UNIQUE NOT NULL,
    user_id VARCHAR(100),
    customer_id UUID,
    api_key VARCHAR(100),
    quota_type VARCHAR(50) NOT NULL,
    quota_limit BIGINT NOT NULL,
    quota_period VARCHAR(20) NOT NULL,
    quota_used BIGINT DEFAULT 0,
    reset_date DATE NOT NULL,
    last_reset_at TIMESTAMP,
    overage_allowed BOOLEAN DEFAULT FALSE,
    overage_limit BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_requests BIGINT DEFAULT 0,
    unique_routes INTEGER DEFAULT 0,
    unique_users BIGINT DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    success_rate DECIMAL(5, 4),
    error_rate DECIMAL(5, 4),
    cache_hit_rate DECIMAL(5, 4),
    by_service JSONB,
    by_endpoint JSONB,
    peak_requests_per_minute INTEGER,
    bandwidth_usage_gb DECIMAL(15, 6),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_api_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_api_route_pattern ON api_route(route_pattern);
CREATE INDEX idx_api_route_service ON api_route(target_service);
CREATE INDEX idx_api_route_active ON api_route(is_active) WHERE is_active = true;
CREATE INDEX idx_api_route_public ON api_route(is_public) WHERE is_public = true;

CREATE INDEX idx_api_request_route ON api_request(route_id);
CREATE INDEX idx_api_request_user ON api_request(user_id);
CREATE INDEX idx_api_request_customer ON api_request(customer_id);
CREATE INDEX idx_api_request_timestamp ON api_request(request_timestamp DESC);
CREATE INDEX idx_api_request_status ON api_request(response_status);
CREATE INDEX idx_api_request_ip ON api_request(source_ip);

CREATE INDEX idx_api_rate_limit_user ON api_rate_limit(user_id);
CREATE INDEX idx_api_rate_limit_customer ON api_rate_limit(customer_id);
CREATE INDEX idx_api_rate_limit_ip ON api_rate_limit(source_ip);
CREATE INDEX idx_api_rate_limit_reset ON api_rate_limit(reset_at);

CREATE INDEX idx_api_authentication_user ON api_authentication(user_id);
CREATE INDEX idx_api_authentication_key ON api_authentication(api_key);
CREATE INDEX idx_api_authentication_active ON api_authentication(is_active) WHERE is_active = true;

CREATE INDEX idx_api_circuit_breaker_service ON api_circuit_breaker(service_name);
CREATE INDEX idx_api_circuit_breaker_state ON api_circuit_breaker(state);

CREATE INDEX idx_api_health_check_service ON api_health_check(service_name);
CREATE INDEX idx_api_health_check_status ON api_health_check(status);
CREATE INDEX idx_api_health_check_next ON api_health_check(next_check_at);

CREATE INDEX idx_api_load_balancer_service ON api_load_balancer(service_name);
CREATE INDEX idx_api_load_balancer_healthy ON api_load_balancer(is_healthy) WHERE is_healthy = true;
CREATE INDEX idx_api_load_balancer_active ON api_load_balancer(is_active) WHERE is_active = true;

CREATE INDEX idx_api_cache_key ON api_cache(cache_key);
CREATE INDEX idx_api_cache_expires ON api_cache(expires_at);
CREATE INDEX idx_api_cache_route ON api_cache(route_id);

CREATE INDEX idx_api_transformation_route ON api_transformation(route_id);
CREATE INDEX idx_api_transformation_active ON api_transformation(is_active) WHERE is_active = true;

CREATE INDEX idx_api_analytics_period ON api_analytics(period_end DESC);

CREATE INDEX idx_api_security_event_type ON api_security_event(event_type);
CREATE INDEX idx_api_security_event_severity ON api_security_event(severity);
CREATE INDEX idx_api_security_event_ip ON api_security_event(source_ip);
CREATE INDEX idx_api_security_event_occurred ON api_security_event(occurred_at DESC);

CREATE INDEX idx_api_configuration_type ON api_configuration(config_type);
CREATE INDEX idx_api_configuration_active ON api_configuration(is_active) WHERE is_active = true;

CREATE INDEX idx_api_quota_user ON api_quota(user_id);
CREATE INDEX idx_api_quota_customer ON api_quota(customer_id);
CREATE INDEX idx_api_quota_key ON api_quota(api_key);
CREATE INDEX idx_api_quota_active ON api_quota(is_active) WHERE is_active = true;

CREATE INDEX idx_api_statistics_period ON api_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_api_route_updated_at BEFORE UPDATE ON api_route
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_api_authentication_updated_at BEFORE UPDATE ON api_authentication
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_api_circuit_breaker_updated_at BEFORE UPDATE ON api_circuit_breaker
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_api_load_balancer_updated_at BEFORE UPDATE ON api_load_balancer
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_api_transformation_updated_at BEFORE UPDATE ON api_transformation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_api_configuration_updated_at BEFORE UPDATE ON api_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_api_quota_updated_at BEFORE UPDATE ON api_quota
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();