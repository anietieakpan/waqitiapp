-- =============================================================================
-- WAQITI PAYMENT SERVICE - EXTERNAL PROVIDER TABLES
-- Version: V101
-- Description: External payment provider integration and API client tables
-- Author: Waqiti Engineering Team
-- Date: 2024-01-15
-- =============================================================================

-- Create external provider enum
CREATE TYPE external_provider AS ENUM (
    'STRIPE',
    'PAYPAL', 
    'WISE',
    'PLAID',
    'DWOLLA',
    'ADYEN',
    'SQUARE'
);

-- Create provider operation status enum
CREATE TYPE provider_operation_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
    'REFUNDED',
    'DISPUTED',
    'UNKNOWN'
);

-- Create provider connection status enum
CREATE TYPE provider_connection_status AS ENUM (
    'ACTIVE',
    'INACTIVE',
    'SUSPENDED',
    'RATE_LIMITED',
    'MAINTENANCE',
    'ERROR'
);

-- =============================================================================
-- EXTERNAL PROVIDER CONFIGURATION TABLES
-- =============================================================================

-- External provider configurations
CREATE TABLE external_provider_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider external_provider NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'production', -- sandbox, production
    
    -- Connection details
    base_url TEXT NOT NULL,
    api_version VARCHAR(20),
    connection_status provider_connection_status NOT NULL DEFAULT 'ACTIVE',
    
    -- Configuration
    enabled BOOLEAN NOT NULL DEFAULT true,
    priority INTEGER DEFAULT 5, -- Higher number = higher priority
    
    -- Rate limiting
    rate_limit_requests_per_minute INTEGER DEFAULT 1000,
    rate_limit_requests_per_hour INTEGER DEFAULT 60000,
    current_rate_limit_usage INTEGER DEFAULT 0,
    rate_limit_reset_at TIMESTAMP WITH TIME ZONE,
    
    -- Health monitoring
    last_health_check TIMESTAMP WITH TIME ZONE,
    health_check_interval_minutes INTEGER DEFAULT 5,
    consecutive_failures INTEGER DEFAULT 0,
    max_consecutive_failures INTEGER DEFAULT 5,
    
    -- Timeouts and retries
    connection_timeout_seconds INTEGER DEFAULT 30,
    read_timeout_seconds INTEGER DEFAULT 60,
    max_retries INTEGER DEFAULT 3,
    retry_delay_seconds INTEGER DEFAULT 5,
    
    -- Features support
    supports_refunds BOOLEAN DEFAULT true,
    supports_cancellation BOOLEAN DEFAULT true,
    supports_webhooks BOOLEAN DEFAULT true,
    supports_split_payments BOOLEAN DEFAULT false,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    
    -- Constraints
    CONSTRAINT uk_provider_environment UNIQUE(provider, environment),
    CONSTRAINT chk_priority CHECK (priority BETWEEN 1 AND 10),
    CONSTRAINT chk_rate_limits CHECK (
        rate_limit_requests_per_minute > 0 AND 
        rate_limit_requests_per_hour > 0 AND
        rate_limit_requests_per_hour >= rate_limit_requests_per_minute
    ),
    CONSTRAINT chk_timeouts CHECK (
        connection_timeout_seconds > 0 AND 
        read_timeout_seconds > 0 AND
        max_retries >= 0 AND
        retry_delay_seconds > 0
    ),
    CONSTRAINT chk_health_check CHECK (health_check_interval_minutes > 0),
    CONSTRAINT chk_consecutive_failures CHECK (
        consecutive_failures >= 0 AND 
        max_consecutive_failures > 0 AND
        consecutive_failures <= max_consecutive_failures * 2
    )
);

-- External provider credentials (encrypted in production)
CREATE TABLE external_provider_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_config_id UUID NOT NULL REFERENCES external_provider_configs(id),
    
    -- Credential types
    credential_type VARCHAR(50) NOT NULL, -- api_key, client_secret, webhook_secret, etc.
    credential_value_encrypted TEXT NOT NULL,
    encryption_key_id VARCHAR(100) NOT NULL,
    
    -- Metadata
    description TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    rotation_reminder_days INTEGER DEFAULT 90,
    
    -- Usage tracking
    last_used_at TIMESTAMP WITH TIME ZONE,
    usage_count BIGINT DEFAULT 0,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID,
    
    -- Constraints
    CONSTRAINT uk_provider_credential_type UNIQUE(provider_config_id, credential_type),
    CONSTRAINT chk_rotation_reminder CHECK (rotation_reminder_days > 0)
);

-- =============================================================================
-- EXTERNAL PROVIDER OPERATION TRACKING
-- =============================================================================

-- External provider operations log
CREATE TABLE external_provider_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID, -- References transactions(id) but not enforced for flexibility
    provider external_provider NOT NULL,
    operation_type VARCHAR(50) NOT NULL, -- refund, cancel, capture, void, etc.
    
    -- External references
    external_transaction_id VARCHAR(255),
    external_operation_id VARCHAR(255),
    external_status VARCHAR(100),
    
    -- Operation details
    operation_status provider_operation_status NOT NULL DEFAULT 'PENDING',
    amount DECIMAL(19,4),
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Request/Response tracking
    request_payload JSONB,
    response_payload JSONB,
    error_details JSONB,
    
    -- Timing
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    response_time_ms INTEGER,
    
    -- Retry tracking
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    
    -- Idempotency
    idempotency_key VARCHAR(100),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_amount_currency CHECK (
        (amount IS NULL AND currency IS NULL) OR
        (amount IS NOT NULL AND currency IS NOT NULL AND amount >= 0)
    ),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0 AND retry_count <= max_retries),
    CONSTRAINT chk_response_time CHECK (response_time_ms >= 0),
    CONSTRAINT chk_completion_status CHECK (
        (operation_status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND completed_at IS NOT NULL) OR
        (operation_status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED'))
    )
);

-- External provider API call metrics
CREATE TABLE external_provider_api_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Dimensions
    provider external_provider NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    date_hour TIMESTAMP WITH TIME ZONE NOT NULL, -- Hourly aggregation
    
    -- Success metrics
    total_calls INTEGER NOT NULL DEFAULT 0,
    successful_calls INTEGER NOT NULL DEFAULT 0,
    failed_calls INTEGER NOT NULL DEFAULT 0,
    
    -- Performance metrics
    avg_response_time_ms DECIMAL(10,2),
    min_response_time_ms INTEGER,
    max_response_time_ms INTEGER,
    p95_response_time_ms INTEGER,
    p99_response_time_ms INTEGER,
    
    -- Error analysis
    client_errors INTEGER DEFAULT 0, -- 4xx errors
    server_errors INTEGER DEFAULT 0, -- 5xx errors
    timeout_errors INTEGER DEFAULT 0,
    network_errors INTEGER DEFAULT 0,
    
    -- Rate limiting
    rate_limited_calls INTEGER DEFAULT 0,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_provider_metrics_hour UNIQUE(provider, operation_type, date_hour),
    CONSTRAINT chk_call_counts CHECK (
        total_calls >= 0 AND successful_calls >= 0 AND failed_calls >= 0 AND
        successful_calls + failed_calls <= total_calls
    ),
    CONSTRAINT chk_response_times CHECK (
        (avg_response_time_ms IS NULL) OR
        (avg_response_time_ms >= 0 AND min_response_time_ms >= 0 AND 
         max_response_time_ms >= min_response_time_ms AND
         p95_response_time_ms >= min_response_time_ms AND
         p99_response_time_ms >= p95_response_time_ms)
    ),
    CONSTRAINT chk_error_counts CHECK (
        client_errors >= 0 AND server_errors >= 0 AND 
        timeout_errors >= 0 AND network_errors >= 0 AND
        rate_limited_calls >= 0
    )
);

-- =============================================================================
-- EXTERNAL PROVIDER HEALTH MONITORING
-- =============================================================================

-- External provider health checks
CREATE TABLE external_provider_health_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_config_id UUID NOT NULL REFERENCES external_provider_configs(id),
    
    -- Health check details
    check_type VARCHAR(50) NOT NULL DEFAULT 'api_ping', -- api_ping, auth_test, etc.
    endpoint_tested TEXT,
    
    -- Results
    healthy BOOLEAN NOT NULL,
    response_time_ms INTEGER,
    response_code INTEGER,
    response_message TEXT,
    error_details JSONB,
    
    -- Timing
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_health_response_time CHECK (response_time_ms >= 0)
);

-- External provider incidents
CREATE TABLE external_provider_incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider external_provider NOT NULL,
    
    -- Incident details
    incident_type VARCHAR(50) NOT NULL, -- outage, degraded_performance, rate_limit, etc.
    severity VARCHAR(20) NOT NULL, -- low, medium, high, critical
    title VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'open', -- open, investigating, resolved, closed
    
    -- Impact
    affected_operations VARCHAR(100)[], -- Array of operation types affected
    estimated_affected_transactions INTEGER,
    
    -- Timeline
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    
    -- Resolution
    resolution_summary TEXT,
    root_cause TEXT,
    prevention_measures TEXT,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    resolved_by UUID,
    
    -- Constraints
    CONSTRAINT chk_incident_severity CHECK (severity IN ('low', 'medium', 'high', 'critical')),
    CONSTRAINT chk_incident_status CHECK (status IN ('open', 'investigating', 'resolved', 'closed')),
    CONSTRAINT chk_incident_resolution CHECK (
        (status IN ('resolved', 'closed') AND resolved_at IS NOT NULL) OR
        (status NOT IN ('resolved', 'closed'))
    ),
    CONSTRAINT chk_estimated_affected CHECK (estimated_affected_transactions >= 0)
);

-- =============================================================================
-- CIRCUIT BREAKER STATE TRACKING
-- =============================================================================

-- Circuit breaker states for external providers
CREATE TABLE external_provider_circuit_breakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider external_provider NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    
    -- Circuit breaker state
    state VARCHAR(20) NOT NULL DEFAULT 'CLOSED', -- CLOSED, OPEN, HALF_OPEN
    failure_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    
    -- Thresholds
    failure_threshold INTEGER DEFAULT 5,
    success_threshold INTEGER DEFAULT 3, -- for half-open to closed transition
    timeout_seconds INTEGER DEFAULT 60,
    
    -- Timing
    last_failure_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    state_changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    next_attempt_allowed_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT uk_circuit_breaker UNIQUE(provider, operation_type),
    CONSTRAINT chk_circuit_breaker_state CHECK (state IN ('CLOSED', 'OPEN', 'HALF_OPEN')),
    CONSTRAINT chk_circuit_breaker_counts CHECK (failure_count >= 0 AND success_count >= 0),
    CONSTRAINT chk_circuit_breaker_thresholds CHECK (
        failure_threshold > 0 AND success_threshold > 0 AND timeout_seconds > 0
    )
);

-- =============================================================================
-- PERFORMANCE INDEXES
-- =============================================================================

-- External provider configs indexes
CREATE INDEX idx_external_provider_configs_provider ON external_provider_configs(provider);
CREATE INDEX idx_external_provider_configs_enabled ON external_provider_configs(enabled) WHERE enabled = true;
CREATE INDEX idx_external_provider_configs_priority ON external_provider_configs(priority DESC);
CREATE INDEX idx_external_provider_configs_health ON external_provider_configs(last_health_check);

-- External provider credentials indexes
CREATE INDEX idx_external_provider_credentials_config ON external_provider_credentials(provider_config_id);
CREATE INDEX idx_external_provider_credentials_type ON external_provider_credentials(credential_type);
CREATE INDEX idx_external_provider_credentials_expires ON external_provider_credentials(expires_at) WHERE expires_at IS NOT NULL;

-- External provider operations indexes
CREATE INDEX idx_external_provider_ops_transaction ON external_provider_operations(transaction_id);
CREATE INDEX idx_external_provider_ops_provider ON external_provider_operations(provider);
CREATE INDEX idx_external_provider_ops_type ON external_provider_operations(operation_type);
CREATE INDEX idx_external_provider_ops_status ON external_provider_operations(operation_status);
CREATE INDEX idx_external_provider_ops_external_id ON external_provider_operations(external_transaction_id);
CREATE INDEX idx_external_provider_ops_started ON external_provider_operations(started_at);
CREATE INDEX idx_external_provider_ops_retry ON external_provider_operations(next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_external_provider_ops_idempotency ON external_provider_operations(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- External provider API metrics indexes
CREATE INDEX idx_external_provider_metrics_provider ON external_provider_api_metrics(provider);
CREATE INDEX idx_external_provider_metrics_operation ON external_provider_api_metrics(operation_type);
CREATE INDEX idx_external_provider_metrics_date ON external_provider_api_metrics(date_hour);

-- External provider health checks indexes
CREATE INDEX idx_external_provider_health_config ON external_provider_health_checks(provider_config_id);
CREATE INDEX idx_external_provider_health_checked ON external_provider_health_checks(checked_at);
CREATE INDEX idx_external_provider_health_healthy ON external_provider_health_checks(healthy);

-- External provider incidents indexes
CREATE INDEX idx_external_provider_incidents_provider ON external_provider_incidents(provider);
CREATE INDEX idx_external_provider_incidents_status ON external_provider_incidents(status);
CREATE INDEX idx_external_provider_incidents_severity ON external_provider_incidents(severity);
CREATE INDEX idx_external_provider_incidents_detected ON external_provider_incidents(detected_at);

-- Circuit breaker indexes
CREATE INDEX idx_external_provider_cb_provider ON external_provider_circuit_breakers(provider);
CREATE INDEX idx_external_provider_cb_state ON external_provider_circuit_breakers(state);
CREATE INDEX idx_external_provider_cb_next_attempt ON external_provider_circuit_breakers(next_attempt_allowed_at) WHERE next_attempt_allowed_at IS NOT NULL;

-- Composite indexes
CREATE INDEX idx_external_provider_ops_provider_status ON external_provider_operations(provider, operation_status);
CREATE INDEX idx_external_provider_ops_provider_type ON external_provider_operations(provider, operation_type);

-- =============================================================================
-- UPDATE TRIGGERS
-- =============================================================================

-- Create update trigger function for external provider tables
CREATE OR REPLACE FUNCTION update_external_provider_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add update triggers
CREATE TRIGGER update_external_provider_configs_updated_at 
    BEFORE UPDATE ON external_provider_configs 
    FOR EACH ROW EXECUTE FUNCTION update_external_provider_updated_at_column();

CREATE TRIGGER update_external_provider_credentials_updated_at 
    BEFORE UPDATE ON external_provider_credentials 
    FOR EACH ROW EXECUTE FUNCTION update_external_provider_updated_at_column();

CREATE TRIGGER update_external_provider_operations_updated_at 
    BEFORE UPDATE ON external_provider_operations 
    FOR EACH ROW EXECUTE FUNCTION update_external_provider_updated_at_column();

CREATE TRIGGER update_external_provider_api_metrics_updated_at 
    BEFORE UPDATE ON external_provider_api_metrics 
    FOR EACH ROW EXECUTE FUNCTION update_external_provider_updated_at_column();

CREATE TRIGGER update_external_provider_incidents_updated_at 
    BEFORE UPDATE ON external_provider_incidents 
    FOR EACH ROW EXECUTE FUNCTION update_external_provider_updated_at_column();

CREATE TRIGGER update_external_provider_circuit_breakers_updated_at 
    BEFORE UPDATE ON external_provider_circuit_breakers 
    FOR EACH ROW EXECUTE FUNCTION update_external_provider_updated_at_column();

-- =============================================================================
-- INITIAL DATA
-- =============================================================================

-- Insert default configurations for supported providers
INSERT INTO external_provider_configs (provider, environment, base_url, api_version, enabled, priority) VALUES 
('STRIPE', 'production', 'https://api.stripe.com', '2024-06-20', true, 9),
('PAYPAL', 'production', 'https://api.paypal.com', 'v2', true, 8),
('WISE', 'production', 'https://api.wise.com', 'v1', true, 7),
('PLAID', 'production', 'https://production.plaid.com', '2020-09-14', true, 6),
('DWOLLA', 'production', 'https://api.dwolla.com', 'v1', true, 5)
ON CONFLICT (provider, environment) DO NOTHING;

-- Initialize circuit breaker states for critical operations
INSERT INTO external_provider_circuit_breakers (provider, operation_type) 
SELECT p.provider, op.operation_type
FROM (VALUES 
    ('STRIPE'), ('PAYPAL'), ('WISE'), ('PLAID'), ('DWOLLA')
) AS p(provider)
CROSS JOIN (VALUES 
    ('refund'), ('cancel'), ('capture'), ('void'), ('transfer')
) AS op(operation_type)
ON CONFLICT (provider, operation_type) DO NOTHING;

-- =============================================================================
-- VIEWS FOR MONITORING
-- =============================================================================

-- View for provider health overview
CREATE VIEW external_provider_health_overview AS
SELECT 
    epc.provider,
    epc.environment,
    epc.enabled,
    epc.connection_status,
    epc.consecutive_failures,
    epc.last_health_check,
    CASE 
        WHEN epc.consecutive_failures >= epc.max_consecutive_failures THEN 'UNHEALTHY'
        WHEN epc.consecutive_failures > 0 THEN 'DEGRADED'
        ELSE 'HEALTHY'
    END as health_status,
    epc.rate_limit_requests_per_minute,
    epc.current_rate_limit_usage,
    ROUND(
        CASE 
            WHEN epc.rate_limit_requests_per_minute > 0 
            THEN (epc.current_rate_limit_usage * 100.0 / epc.rate_limit_requests_per_minute)
            ELSE 0 
        END, 2
    ) as rate_limit_usage_percent
FROM external_provider_configs epc
WHERE epc.enabled = true;

-- View for recent provider operations
CREATE VIEW external_provider_recent_operations AS
SELECT 
    epo.provider,
    epo.operation_type,
    epo.operation_status,
    COUNT(*) as operation_count,
    AVG(epo.response_time_ms) as avg_response_time_ms,
    COUNT(CASE WHEN epo.operation_status = 'COMPLETED' THEN 1 END) as successful_operations,
    COUNT(CASE WHEN epo.operation_status = 'FAILED' THEN 1 END) as failed_operations,
    ROUND(
        COUNT(CASE WHEN epo.operation_status = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(*), 2
    ) as success_rate_percent
FROM external_provider_operations epo
WHERE epo.started_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour'
GROUP BY epo.provider, epo.operation_type, epo.operation_status
ORDER BY epo.provider, epo.operation_type, epo.operation_status;

-- =============================================================================
-- COMMENTS
-- =============================================================================

COMMENT ON TABLE external_provider_configs IS 'Configuration and health monitoring for external payment providers';
COMMENT ON TABLE external_provider_credentials IS 'Encrypted credentials for external payment providers';
COMMENT ON TABLE external_provider_operations IS 'Comprehensive log of all external provider API operations';
COMMENT ON TABLE external_provider_api_metrics IS 'Aggregated performance metrics for external provider APIs';
COMMENT ON TABLE external_provider_health_checks IS 'Health check results for external provider monitoring';
COMMENT ON TABLE external_provider_incidents IS 'Incident tracking for external provider outages and issues';
COMMENT ON TABLE external_provider_circuit_breakers IS 'Circuit breaker state management for external providers';

COMMENT ON VIEW external_provider_health_overview IS 'Real-time health overview of all external payment providers';
COMMENT ON VIEW external_provider_recent_operations IS 'Recent operation performance summary by provider and operation type';