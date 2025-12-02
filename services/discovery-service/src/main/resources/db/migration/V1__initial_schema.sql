-- Discovery Service Initial Schema
-- Created: 2025-09-27
-- Description: Service discovery and registry management schema

CREATE TABLE IF NOT EXISTS service_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id VARCHAR(100) UNIQUE NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_version VARCHAR(50) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    description TEXT,
    service_host VARCHAR(255) NOT NULL,
    service_port INTEGER NOT NULL,
    service_scheme VARCHAR(10) DEFAULT 'http',
    service_path VARCHAR(500) DEFAULT '/',
    health_check_url VARCHAR(1000),
    admin_url VARCHAR(1000),
    metrics_url VARCHAR(1000),
    documentation_url VARCHAR(1000),
    service_status VARCHAR(20) NOT NULL DEFAULT 'UP',
    last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registration_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    tags TEXT[],
    zone VARCHAR(100),
    region VARCHAR(100),
    datacenter VARCHAR(100),
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    weight INTEGER DEFAULT 100,
    is_secure BOOLEAN DEFAULT FALSE,
    ssl_info JSONB,
    auto_deregister_critical_after VARCHAR(20) DEFAULT '30m',
    deregister_critical_service_after VARCHAR(20) DEFAULT '90m',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS service_instance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    instance_host VARCHAR(255) NOT NULL,
    instance_port INTEGER NOT NULL,
    instance_status VARCHAR(20) NOT NULL DEFAULT 'STARTING',
    health_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_health_check TIMESTAMP,
    consecutive_failures INTEGER DEFAULT 0,
    consecutive_successes INTEGER DEFAULT 0,
    registration_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deregistration_timestamp TIMESTAMP,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    instance_metadata JSONB,
    load_balancer_weight INTEGER DEFAULT 100,
    is_healthy BOOLEAN DEFAULT FALSE,
    response_time_ms INTEGER,
    cpu_usage_percent DECIMAL(5, 2),
    memory_usage_mb INTEGER,
    disk_usage_percent DECIMAL(5, 2),
    active_connections INTEGER DEFAULT 0,
    request_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    uptime_seconds INTEGER DEFAULT 0,
    version VARCHAR(50),
    build_info JSONB,
    deployment_id VARCHAR(100),
    container_id VARCHAR(100),
    node_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_instance FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_health_check (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    instance_id VARCHAR(100) NOT NULL,
    check_name VARCHAR(255) NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    check_url VARCHAR(1000),
    check_method VARCHAR(10) DEFAULT 'GET',
    check_headers JSONB,
    check_body TEXT,
    expected_status_codes INTEGER[] DEFAULT ARRAY[200],
    timeout_seconds INTEGER DEFAULT 10,
    interval_seconds INTEGER DEFAULT 30,
    check_status VARCHAR(20) NOT NULL DEFAULT 'PASSING',
    last_check_time TIMESTAMP,
    last_success_time TIMESTAMP,
    last_failure_time TIMESTAMP,
    response_time_ms INTEGER,
    response_status_code INTEGER,
    response_body TEXT,
    error_message TEXT,
    failure_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    is_critical BOOLEAN DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_health_check_service FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE,
    CONSTRAINT fk_service_health_check_instance FOREIGN KEY (instance_id) REFERENCES service_instance(instance_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_dependency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dependency_id VARCHAR(100) UNIQUE NOT NULL,
    consumer_service_id VARCHAR(100) NOT NULL,
    provider_service_id VARCHAR(100) NOT NULL,
    dependency_type VARCHAR(50) NOT NULL,
    dependency_level VARCHAR(20) NOT NULL,
    is_critical BOOLEAN DEFAULT FALSE,
    circuit_breaker_enabled BOOLEAN DEFAULT TRUE,
    timeout_seconds INTEGER DEFAULT 30,
    retry_attempts INTEGER DEFAULT 3,
    fallback_strategy VARCHAR(100),
    last_call_timestamp TIMESTAMP,
    call_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    circuit_breaker_state VARCHAR(20) DEFAULT 'CLOSED',
    last_circuit_breaker_change TIMESTAMP,
    health_impact_score INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_dependency_consumer FOREIGN KEY (consumer_service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE,
    CONSTRAINT fk_service_dependency_provider FOREIGN KEY (provider_service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE,
    CONSTRAINT no_self_dependency CHECK (consumer_service_id != provider_service_id)
);

CREATE TABLE IF NOT EXISTS service_load_balancer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    load_balancer_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    load_balancer_name VARCHAR(255) NOT NULL,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'ROUND_ROBIN',
    sticky_sessions BOOLEAN DEFAULT FALSE,
    session_affinity_type VARCHAR(50),
    health_check_enabled BOOLEAN DEFAULT TRUE,
    health_check_interval_seconds INTEGER DEFAULT 30,
    health_check_timeout_seconds INTEGER DEFAULT 10,
    health_check_threshold INTEGER DEFAULT 3,
    unhealthy_threshold INTEGER DEFAULT 3,
    max_connections_per_instance INTEGER DEFAULT 1000,
    connection_timeout_seconds INTEGER DEFAULT 30,
    idle_timeout_seconds INTEGER DEFAULT 60,
    is_active BOOLEAN DEFAULT TRUE,
    configuration JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_load_balancer FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_endpoint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    endpoint_name VARCHAR(255),
    description TEXT,
    is_public BOOLEAN DEFAULT FALSE,
    requires_auth BOOLEAN DEFAULT TRUE,
    required_roles TEXT[],
    rate_limit_rpm INTEGER DEFAULT 1000,
    timeout_seconds INTEGER DEFAULT 30,
    cache_ttl_seconds INTEGER DEFAULT 0,
    request_schema JSONB,
    response_schema JSONB,
    tags TEXT[],
    deprecated BOOLEAN DEFAULT FALSE,
    deprecated_date DATE,
    replacement_endpoint VARCHAR(100),
    documentation_url VARCHAR(1000),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_endpoint FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    config_key VARCHAR(255) NOT NULL,
    config_value JSONB NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    is_sensitive BOOLEAN DEFAULT FALSE,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    effective_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expiry_date TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_configuration FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE,
    CONSTRAINT unique_service_config UNIQUE (service_id, config_key, environment)
);

CREATE TABLE IF NOT EXISTS service_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100),
    instance_id VARCHAR(100),
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(100) NOT NULL,
    event_level VARCHAR(20) NOT NULL,
    event_message TEXT NOT NULL,
    event_data JSONB,
    source VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100),
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS service_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    instance_id VARCHAR(100),
    metric_name VARCHAR(255) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DECIMAL(18, 6) NOT NULL,
    metric_unit VARCHAR(50),
    dimensions JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retention_days INTEGER DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_metrics_service FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_circuit_breaker (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    circuit_breaker_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    target_service_id VARCHAR(100) NOT NULL,
    circuit_breaker_name VARCHAR(255) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    failure_threshold INTEGER DEFAULT 5,
    success_threshold INTEGER DEFAULT 3,
    timeout_seconds INTEGER DEFAULT 60,
    slow_call_threshold_seconds INTEGER DEFAULT 30,
    slow_call_rate_threshold DECIMAL(5, 4) DEFAULT 0.5,
    minimum_throughput INTEGER DEFAULT 10,
    sliding_window_size INTEGER DEFAULT 100,
    sliding_window_type VARCHAR(20) DEFAULT 'COUNT_BASED',
    failure_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    slow_call_count INTEGER DEFAULT 0,
    last_failure_time TIMESTAMP,
    last_success_time TIMESTAMP,
    next_attempt_time TIMESTAMP,
    state_transition_history JSONB,
    configuration JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_circuit_breaker_service FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE,
    CONSTRAINT fk_service_circuit_breaker_target FOREIGN KEY (target_service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_rate_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_limit_id VARCHAR(100) UNIQUE NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    endpoint_id VARCHAR(100),
    rate_limit_name VARCHAR(255) NOT NULL,
    limit_type VARCHAR(50) NOT NULL,
    requests_per_minute INTEGER,
    requests_per_hour INTEGER,
    requests_per_day INTEGER,
    burst_capacity INTEGER,
    window_size_seconds INTEGER DEFAULT 60,
    key_extraction_strategy VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    enforcement_action VARCHAR(50) DEFAULT 'REJECT',
    bypass_roles TEXT[],
    whitelist_ips TEXT[],
    blacklist_ips TEXT[],
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_service_rate_limit_service FOREIGN KEY (service_id) REFERENCES service_registry(service_id) ON DELETE CASCADE,
    CONSTRAINT fk_service_rate_limit_endpoint FOREIGN KEY (endpoint_id) REFERENCES service_endpoint(endpoint_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS discovery_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_services INTEGER DEFAULT 0,
    healthy_services INTEGER DEFAULT 0,
    unhealthy_services INTEGER DEFAULT 0,
    total_instances INTEGER DEFAULT 0,
    healthy_instances INTEGER DEFAULT 0,
    average_response_time_ms DECIMAL(10, 2),
    service_availability DECIMAL(5, 4),
    health_check_success_rate DECIMAL(5, 4),
    circuit_breaker_trips INTEGER DEFAULT 0,
    service_registrations INTEGER DEFAULT 0,
    service_deregistrations INTEGER DEFAULT 0,
    by_service_type JSONB,
    by_environment JSONB,
    by_zone JSONB,
    performance_trends JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS discovery_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_registered_services INTEGER DEFAULT 0,
    active_service_instances INTEGER DEFAULT 0,
    health_checks_performed BIGINT DEFAULT 0,
    successful_health_checks BIGINT DEFAULT 0,
    failed_health_checks BIGINT DEFAULT 0,
    avg_service_uptime_hours DECIMAL(10, 2),
    service_discovery_requests BIGINT DEFAULT 0,
    load_balancer_decisions BIGINT DEFAULT 0,
    circuit_breaker_activations INTEGER DEFAULT 0,
    by_service_category JSONB,
    by_region JSONB,
    top_unhealthy_services JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_discovery_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_service_registry_name ON service_registry(service_name);
CREATE INDEX idx_service_registry_type ON service_registry(service_type);
CREATE INDEX idx_service_registry_status ON service_registry(service_status);
CREATE INDEX idx_service_registry_environment ON service_registry(environment);
CREATE INDEX idx_service_registry_zone ON service_registry(zone);
CREATE INDEX idx_service_registry_tags ON service_registry USING gin(tags);
CREATE INDEX idx_service_registry_heartbeat ON service_registry(last_heartbeat DESC);

CREATE INDEX idx_service_instance_service ON service_instance(service_id);
CREATE INDEX idx_service_instance_status ON service_instance(instance_status);
CREATE INDEX idx_service_instance_health ON service_instance(health_status);
CREATE INDEX idx_service_instance_healthy ON service_instance(is_healthy) WHERE is_healthy = true;
CREATE INDEX idx_service_instance_last_seen ON service_instance(last_seen DESC);

CREATE INDEX idx_service_health_check_service ON service_health_check(service_id);
CREATE INDEX idx_service_health_check_instance ON service_health_check(instance_id);
CREATE INDEX idx_service_health_check_status ON service_health_check(check_status);
CREATE INDEX idx_service_health_check_critical ON service_health_check(is_critical) WHERE is_critical = true;
CREATE INDEX idx_service_health_check_last_check ON service_health_check(last_check_time DESC);

CREATE INDEX idx_service_dependency_consumer ON service_dependency(consumer_service_id);
CREATE INDEX idx_service_dependency_provider ON service_dependency(provider_service_id);
CREATE INDEX idx_service_dependency_critical ON service_dependency(is_critical) WHERE is_critical = true;
CREATE INDEX idx_service_dependency_circuit_state ON service_dependency(circuit_breaker_state);

CREATE INDEX idx_service_load_balancer_service ON service_load_balancer(service_id);
CREATE INDEX idx_service_load_balancer_active ON service_load_balancer(is_active) WHERE is_active = true;

CREATE INDEX idx_service_endpoint_service ON service_endpoint(service_id);
CREATE INDEX idx_service_endpoint_path ON service_endpoint(endpoint_path);
CREATE INDEX idx_service_endpoint_method ON service_endpoint(http_method);
CREATE INDEX idx_service_endpoint_public ON service_endpoint(is_public) WHERE is_public = true;
CREATE INDEX idx_service_endpoint_deprecated ON service_endpoint(deprecated) WHERE deprecated = true;

CREATE INDEX idx_service_configuration_service ON service_configuration(service_id);
CREATE INDEX idx_service_configuration_environment ON service_configuration(environment);
CREATE INDEX idx_service_configuration_active ON service_configuration(is_active) WHERE is_active = true;
CREATE INDEX idx_service_configuration_sensitive ON service_configuration(is_sensitive) WHERE is_sensitive = true;

CREATE INDEX idx_service_event_service ON service_event(service_id);
CREATE INDEX idx_service_event_instance ON service_event(instance_id);
CREATE INDEX idx_service_event_type ON service_event(event_type);
CREATE INDEX idx_service_event_level ON service_event(event_level);
CREATE INDEX idx_service_event_timestamp ON service_event(event_timestamp DESC);
CREATE INDEX idx_service_event_processed ON service_event(processed) WHERE processed = false;

CREATE INDEX idx_service_metrics_service ON service_metrics(service_id);
CREATE INDEX idx_service_metrics_instance ON service_metrics(instance_id);
CREATE INDEX idx_service_metrics_name ON service_metrics(metric_name);
CREATE INDEX idx_service_metrics_timestamp ON service_metrics(timestamp DESC);

CREATE INDEX idx_service_circuit_breaker_service ON service_circuit_breaker(service_id);
CREATE INDEX idx_service_circuit_breaker_target ON service_circuit_breaker(target_service_id);
CREATE INDEX idx_service_circuit_breaker_state ON service_circuit_breaker(state);

CREATE INDEX idx_service_rate_limit_service ON service_rate_limit(service_id);
CREATE INDEX idx_service_rate_limit_endpoint ON service_rate_limit(endpoint_id);
CREATE INDEX idx_service_rate_limit_active ON service_rate_limit(is_active) WHERE is_active = true;

CREATE INDEX idx_discovery_analytics_period ON discovery_analytics(period_end DESC);
CREATE INDEX idx_discovery_statistics_period ON discovery_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_service_registry_updated_at BEFORE UPDATE ON service_registry
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_instance_updated_at BEFORE UPDATE ON service_instance
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_health_check_updated_at BEFORE UPDATE ON service_health_check
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_dependency_updated_at BEFORE UPDATE ON service_dependency
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_load_balancer_updated_at BEFORE UPDATE ON service_load_balancer
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_endpoint_updated_at BEFORE UPDATE ON service_endpoint
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_configuration_updated_at BEFORE UPDATE ON service_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_circuit_breaker_updated_at BEFORE UPDATE ON service_circuit_breaker
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_rate_limit_updated_at BEFORE UPDATE ON service_rate_limit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();