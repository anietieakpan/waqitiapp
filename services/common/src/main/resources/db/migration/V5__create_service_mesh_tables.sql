-- Service Mesh Architecture Tables
-- Manages service registration, routing policies, and observability data

-- Service registry table
CREATE TABLE IF NOT EXISTS service_registry (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL UNIQUE,
    version VARCHAR(50) NOT NULL,
    namespace VARCHAR(100) DEFAULT 'waqiti',
    health_check_path VARCHAR(255) DEFAULT '/actuator/health',
    configuration_endpoint VARCHAR(255),
    
    -- Service metadata
    description TEXT,
    owner_team VARCHAR(100),
    sla_tier VARCHAR(20) DEFAULT 'STANDARD', -- CRITICAL, HIGH, STANDARD, LOW
    
    -- Status
    status VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE, DEPRECATED
    healthy BOOLEAN DEFAULT true,
    last_health_check TIMESTAMP,
    consecutive_failures INT DEFAULT 0,
    
    -- Timestamps
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_sla_tier CHECK (sla_tier IN ('CRITICAL', 'HIGH', 'STANDARD', 'LOW')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DEPRECATED'))
);

-- Service instances table
CREATE TABLE IF NOT EXISTS service_instances (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    instance_id VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    
    -- Instance metadata
    zone VARCHAR(50),
    region VARCHAR(50),
    weight INT DEFAULT 100,
    version VARCHAR(50),
    
    -- Health status
    healthy BOOLEAN DEFAULT true,
    active_requests INT DEFAULT 0,
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Timestamps
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE(service_id, instance_id),
    CONSTRAINT chk_port CHECK (port > 0 AND port <= 65535),
    CONSTRAINT chk_weight CHECK (weight >= 0 AND weight <= 1000)
);

-- Service dependencies table
CREATE TABLE IF NOT EXISTS service_dependencies (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    depends_on_service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    dependency_type VARCHAR(20) DEFAULT 'REQUIRED', -- REQUIRED, OPTIONAL
    
    -- Constraints
    UNIQUE(service_id, depends_on_service_id),
    CONSTRAINT chk_no_self_dependency CHECK (service_id != depends_on_service_id),
    CONSTRAINT chk_dependency_type CHECK (dependency_type IN ('REQUIRED', 'OPTIONAL'))
);

-- Traffic policies table
CREATE TABLE IF NOT EXISTS traffic_policies (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    policy_name VARCHAR(255) NOT NULL,
    policy_type VARCHAR(50) NOT NULL, -- LOAD_BALANCING, CIRCUIT_BREAKER, RETRY, TIMEOUT, CANARY
    
    -- Policy configuration (JSON)
    configuration JSONB NOT NULL,
    
    -- Status
    enabled BOOLEAN DEFAULT true,
    priority INT DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE(service_id, policy_name),
    CONSTRAINT chk_policy_type CHECK (policy_type IN ('LOAD_BALANCING', 'CIRCUIT_BREAKER', 'RETRY', 'TIMEOUT', 'CANARY'))
);

-- Circuit breaker states table
CREATE TABLE IF NOT EXISTS circuit_breaker_states (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    state VARCHAR(20) NOT NULL, -- CLOSED, OPEN, HALF_OPEN
    
    -- Metrics
    failure_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    last_failure_time TIMESTAMP,
    
    -- State transition
    state_changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    next_retry_time TIMESTAMP,
    
    -- Constraints
    UNIQUE(service_id),
    CONSTRAINT chk_state CHECK (state IN ('CLOSED', 'OPEN', 'HALF_OPEN'))
);

-- Service mesh routes table
CREATE TABLE IF NOT EXISTS service_mesh_routes (
    id BIGSERIAL PRIMARY KEY,
    source_service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    target_service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    
    -- Route configuration
    path_pattern VARCHAR(500),
    method VARCHAR(10),
    weight INT DEFAULT 100,
    
    -- Canary deployment
    is_canary BOOLEAN DEFAULT false,
    canary_percentage INT DEFAULT 0,
    header_match JSONB,
    
    -- Status
    enabled BOOLEAN DEFAULT true,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_method CHECK (method IN ('GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS', 'HEAD', '*')),
    CONSTRAINT chk_canary_percentage CHECK (canary_percentage >= 0 AND canary_percentage <= 100)
);

-- Service mesh security policies table
CREATE TABLE IF NOT EXISTS service_mesh_security_policies (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    
    -- mTLS configuration
    mtls_enabled BOOLEAN DEFAULT true,
    mtls_mode VARCHAR(20) DEFAULT 'STRICT', -- STRICT, PERMISSIVE, DISABLED
    
    -- Authorization
    authorization_enabled BOOLEAN DEFAULT true,
    allowed_services TEXT[], -- Array of allowed service names
    denied_services TEXT[],
    
    -- JWT validation
    jwt_validation_enabled BOOLEAN DEFAULT false,
    jwt_issuer VARCHAR(255),
    jwks_uri VARCHAR(500),
    
    -- Rate limiting per service
    rate_limit_enabled BOOLEAN DEFAULT false,
    requests_per_minute INT,
    
    -- IP restrictions
    ip_whitelist INET[],
    ip_blacklist INET[],
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE(service_id),
    CONSTRAINT chk_mtls_mode CHECK (mtls_mode IN ('STRICT', 'PERMISSIVE', 'DISABLED'))
);

-- Service mesh metrics table (for aggregated metrics)
CREATE TABLE IF NOT EXISTS service_mesh_metrics (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    
    -- Time window
    metric_timestamp TIMESTAMP NOT NULL,
    window_minutes INT DEFAULT 1,
    
    -- Request metrics
    total_requests BIGINT DEFAULT 0,
    successful_requests BIGINT DEFAULT 0,
    failed_requests BIGINT DEFAULT 0,
    
    -- Latency metrics (in milliseconds)
    avg_latency NUMERIC(10, 2),
    p50_latency NUMERIC(10, 2),
    p95_latency NUMERIC(10, 2),
    p99_latency NUMERIC(10, 2),
    max_latency NUMERIC(10, 2),
    
    -- Error metrics
    error_rate NUMERIC(5, 2),
    timeout_count INT DEFAULT 0,
    circuit_breaker_trips INT DEFAULT 0,
    
    -- Throughput
    requests_per_second NUMERIC(10, 2),
    bytes_in BIGINT DEFAULT 0,
    bytes_out BIGINT DEFAULT 0,
    
    -- Constraints
    CONSTRAINT chk_window CHECK (window_minutes IN (1, 5, 15, 60))
);

-- Service mesh traces table (for distributed tracing)
CREATE TABLE IF NOT EXISTS service_mesh_traces (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    
    -- Service information
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    operation_name VARCHAR(255),
    
    -- Timing
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    
    -- Status
    status VARCHAR(20) DEFAULT 'OK', -- OK, ERROR, CANCELLED
    error_message TEXT,
    
    -- Tags and attributes
    tags JSONB,
    
    -- Constraints
    UNIQUE(trace_id, span_id),
    CONSTRAINT chk_status CHECK (status IN ('OK', 'ERROR', 'CANCELLED'))
);

-- Service mesh access logs table
CREATE TABLE IF NOT EXISTS service_mesh_access_logs (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    
    -- Request information
    method VARCHAR(10),
    path VARCHAR(500),
    query_params TEXT,
    
    -- Response information
    status_code INT,
    response_time_ms BIGINT,
    response_size_bytes BIGINT,
    
    -- Client information
    client_ip INET,
    client_service_id BIGINT REFERENCES service_registry(id) ON DELETE SET NULL,
    user_agent TEXT,
    
    -- Trace context
    trace_id VARCHAR(64),
    
    -- Timestamp
    logged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Service mesh events table
CREATE TABLE IF NOT EXISTS service_mesh_events (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL, -- REGISTERED, DEREGISTERED, HEALTH_CHECK_FAILED, CIRCUIT_OPENED, POLICY_UPDATED
    event_data JSONB,
    severity VARCHAR(20) DEFAULT 'INFO', -- DEBUG, INFO, WARNING, ERROR, CRITICAL
    
    -- Timestamp
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_severity CHECK (severity IN ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL'))
);

-- Canary deployments table
CREATE TABLE IF NOT EXISTS canary_deployments (
    id BIGSERIAL PRIMARY KEY,
    service_id BIGINT REFERENCES service_registry(id) ON DELETE CASCADE,
    
    -- Canary configuration
    canary_version VARCHAR(50) NOT NULL,
    stable_version VARCHAR(50) NOT NULL,
    traffic_percentage INT DEFAULT 10,
    
    -- Routing rules
    header_rules JSONB,
    cookie_rules JSONB,
    
    -- Metrics comparison
    canary_success_rate NUMERIC(5, 2),
    stable_success_rate NUMERIC(5, 2),
    canary_avg_latency NUMERIC(10, 2),
    stable_avg_latency NUMERIC(10, 2),
    
    -- Status
    status VARCHAR(20) DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, PROMOTED, ROLLED_BACK
    
    -- Timestamps
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_percentage CHECK (traffic_percentage >= 0 AND traffic_percentage <= 100),
    CONSTRAINT chk_canary_status CHECK (status IN ('IN_PROGRESS', 'PROMOTED', 'ROLLED_BACK'))
);

-- Create indexes for performance
CREATE INDEX idx_service_instances_service_id ON service_instances(service_id);
CREATE INDEX idx_service_instances_healthy ON service_instances(healthy);
CREATE INDEX idx_service_dependencies_service ON service_dependencies(service_id);
CREATE INDEX idx_traffic_policies_service ON traffic_policies(service_id);
CREATE INDEX idx_traffic_policies_enabled ON traffic_policies(enabled);
CREATE INDEX idx_circuit_breaker_states_service ON circuit_breaker_states(service_id);
CREATE INDEX idx_service_mesh_routes_source ON service_mesh_routes(source_service_id);
CREATE INDEX idx_service_mesh_routes_target ON service_mesh_routes(target_service_id);
CREATE INDEX idx_service_mesh_metrics_service_time ON service_mesh_metrics(service_id, metric_timestamp);
CREATE INDEX idx_service_mesh_traces_trace_id ON service_mesh_traces(trace_id);
CREATE INDEX idx_service_mesh_traces_service_time ON service_mesh_traces(service_id, start_time);
CREATE INDEX idx_service_mesh_access_logs_service_time ON service_mesh_access_logs(service_id, logged_at);
CREATE INDEX idx_service_mesh_access_logs_trace ON service_mesh_access_logs(trace_id);
CREATE INDEX idx_service_mesh_events_service_time ON service_mesh_events(service_id, occurred_at);
CREATE INDEX idx_canary_deployments_service ON canary_deployments(service_id);

-- Create triggers for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_service_registry_updated_at BEFORE UPDATE ON service_registry
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_traffic_policies_updated_at BEFORE UPDATE ON traffic_policies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_mesh_security_policies_updated_at BEFORE UPDATE ON service_mesh_security_policies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();