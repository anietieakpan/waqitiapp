-- Integration Service Initial Schema
-- Created: 2025-09-27
-- Description: Third-party integration and API connector management schema

CREATE TABLE IF NOT EXISTS integration_connector (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id VARCHAR(100) UNIQUE NOT NULL,
    connector_name VARCHAR(255) NOT NULL,
    connector_type VARCHAR(50) NOT NULL,
    provider_name VARCHAR(255) NOT NULL,
    provider_category VARCHAR(100) NOT NULL,
    description TEXT,
    connector_version VARCHAR(50) NOT NULL,
    api_version VARCHAR(50),
    base_url VARCHAR(1000) NOT NULL,
    authentication_type VARCHAR(50) NOT NULL,
    authentication_config JSONB NOT NULL,
    connection_status VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED',
    last_connection_test TIMESTAMP,
    last_successful_connection TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    rate_limits JSONB,
    timeout_seconds INTEGER DEFAULT 30,
    retry_policy JSONB,
    circuit_breaker_config JSONB,
    supported_operations TEXT[] NOT NULL,
    capabilities JSONB,
    data_mappings JSONB,
    webhook_config JSONB,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    certification_details JSONB,
    documentation_url VARCHAR(1000),
    support_contact JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS integration_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id VARCHAR(100) UNIQUE NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    connection_name VARCHAR(255) NOT NULL,
    customer_id UUID,
    organization_id VARCHAR(100),
    connection_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    credentials_encrypted JSONB NOT NULL,
    oauth_tokens JSONB,
    token_expiry TIMESTAMP,
    refresh_token_encrypted VARCHAR(500),
    api_key_encrypted VARCHAR(500),
    scopes TEXT[],
    permissions JSONB,
    connection_metadata JSONB,
    last_used_at TIMESTAMP,
    usage_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    last_error_at TIMESTAMP,
    last_error_message TEXT,
    health_status VARCHAR(20) DEFAULT 'UNKNOWN',
    last_health_check TIMESTAMP,
    auto_reconnect BOOLEAN DEFAULT TRUE,
    notification_enabled BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_connection FOREIGN KEY (connector_id) REFERENCES integration_connector(connector_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(100) UNIQUE NOT NULL,
    connection_id VARCHAR(100) NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    request_type VARCHAR(50) NOT NULL,
    operation_name VARCHAR(255) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    endpoint_path VARCHAR(1000) NOT NULL,
    request_headers JSONB,
    request_body JSONB,
    request_parameters JSONB,
    request_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_status INTEGER,
    response_headers JSONB,
    response_body JSONB,
    response_timestamp TIMESTAMP,
    processing_time_ms INTEGER,
    success BOOLEAN DEFAULT FALSE,
    error_code VARCHAR(50),
    error_message TEXT,
    error_details JSONB,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    idempotency_key VARCHAR(100),
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    user_id VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent TEXT,
    request_source VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_request_connection FOREIGN KEY (connection_id) REFERENCES integration_connection(connection_id) ON DELETE CASCADE,
    CONSTRAINT fk_integration_request_connector FOREIGN KEY (connector_id) REFERENCES integration_connector(connector_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_webhook (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id VARCHAR(100) UNIQUE NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100),
    webhook_name VARCHAR(255) NOT NULL,
    webhook_type VARCHAR(50) NOT NULL,
    event_types TEXT[] NOT NULL,
    endpoint_url VARCHAR(1000) NOT NULL,
    secret_key_encrypted VARCHAR(255) NOT NULL,
    signature_method VARCHAR(50) DEFAULT 'HMAC-SHA256',
    is_active BOOLEAN DEFAULT TRUE,
    verification_token VARCHAR(255),
    last_triggered_at TIMESTAMP,
    total_deliveries INTEGER DEFAULT 0,
    successful_deliveries INTEGER DEFAULT 0,
    failed_deliveries INTEGER DEFAULT 0,
    retry_policy JSONB,
    timeout_seconds INTEGER DEFAULT 30,
    custom_headers JSONB,
    payload_transformation JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_webhook_connector FOREIGN KEY (connector_id) REFERENCES integration_connector(connector_id) ON DELETE CASCADE,
    CONSTRAINT fk_integration_webhook_connection FOREIGN KEY (connection_id) REFERENCES integration_connection(connection_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS integration_webhook_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id VARCHAR(100) UNIQUE NOT NULL,
    webhook_id VARCHAR(100) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_data JSONB NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_number INTEGER NOT NULL DEFAULT 1,
    scheduled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempted_at TIMESTAMP,
    completed_at TIMESTAMP,
    http_status_code INTEGER,
    response_body TEXT,
    response_headers JSONB,
    processing_time_ms INTEGER,
    error_message TEXT,
    next_retry_at TIMESTAMP,
    signature VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_webhook_delivery FOREIGN KEY (webhook_id) REFERENCES integration_webhook(webhook_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_sync_job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id VARCHAR(100) UNIQUE NOT NULL,
    connection_id VARCHAR(100) NOT NULL,
    job_name VARCHAR(255) NOT NULL,
    sync_type VARCHAR(50) NOT NULL,
    sync_direction VARCHAR(20) NOT NULL,
    source_entity VARCHAR(255) NOT NULL,
    target_entity VARCHAR(255) NOT NULL,
    sync_mode VARCHAR(50) NOT NULL,
    sync_frequency VARCHAR(20),
    cron_expression VARCHAR(100),
    job_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    is_active BOOLEAN DEFAULT TRUE,
    last_sync_at TIMESTAMP,
    next_sync_at TIMESTAMP,
    sync_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    records_synced INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0,
    sync_configuration JSONB,
    field_mappings JSONB NOT NULL,
    transformation_rules JSONB,
    filter_criteria JSONB,
    conflict_resolution_strategy VARCHAR(100),
    error_handling_strategy VARCHAR(100),
    notification_enabled BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_sync_job FOREIGN KEY (connection_id) REFERENCES integration_connection(connection_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_sync_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id VARCHAR(100) UNIQUE NOT NULL,
    job_id VARCHAR(100) NOT NULL,
    execution_status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    records_processed INTEGER DEFAULT 0,
    records_created INTEGER DEFAULT 0,
    records_updated INTEGER DEFAULT 0,
    records_deleted INTEGER DEFAULT 0,
    records_skipped INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0,
    error_summary JSONB,
    execution_logs TEXT,
    sync_metrics JSONB,
    triggered_by VARCHAR(100),
    trigger_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_sync_execution FOREIGN KEY (job_id) REFERENCES integration_sync_job(job_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mapping_id VARCHAR(100) UNIQUE NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    mapping_name VARCHAR(255) NOT NULL,
    mapping_type VARCHAR(50) NOT NULL,
    source_schema JSONB NOT NULL,
    target_schema JSONB NOT NULL,
    field_mappings JSONB NOT NULL,
    transformation_rules JSONB,
    validation_rules JSONB,
    is_bidirectional BOOLEAN DEFAULT FALSE,
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_mapping FOREIGN KEY (connector_id) REFERENCES integration_connector(connector_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_transformation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transformation_id VARCHAR(100) UNIQUE NOT NULL,
    transformation_name VARCHAR(255) NOT NULL,
    transformation_type VARCHAR(50) NOT NULL,
    source_format VARCHAR(50) NOT NULL,
    target_format VARCHAR(50) NOT NULL,
    transformation_logic JSONB NOT NULL,
    input_schema JSONB,
    output_schema JSONB,
    is_reversible BOOLEAN DEFAULT FALSE,
    reverse_transformation_logic JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    execution_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    avg_execution_time_ms DECIMAL(10, 2),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS integration_rate_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100),
    window_start TIMESTAMP NOT NULL,
    window_duration_seconds INTEGER NOT NULL,
    request_count INTEGER DEFAULT 0,
    limit_exceeded BOOLEAN DEFAULT FALSE,
    reset_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_rate_limit_connector FOREIGN KEY (connector_id) REFERENCES integration_connector(connector_id) ON DELETE CASCADE,
    CONSTRAINT fk_integration_rate_limit_connection FOREIGN KEY (connection_id) REFERENCES integration_connection(connection_id) ON DELETE CASCADE,
    CONSTRAINT unique_connector_connection_window UNIQUE (connector_id, connection_id, window_start)
);

CREATE TABLE IF NOT EXISTS integration_error_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    error_id VARCHAR(100) UNIQUE NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100),
    request_id VARCHAR(100),
    error_type VARCHAR(50) NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT NOT NULL,
    error_details JSONB,
    stack_trace TEXT,
    severity VARCHAR(20) NOT NULL,
    retry_eligible BOOLEAN DEFAULT TRUE,
    resolution_status VARCHAR(20) DEFAULT 'OPEN',
    resolution_notes TEXT,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_integration_error_log_connector FOREIGN KEY (connector_id) REFERENCES integration_connector(connector_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS integration_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id VARCHAR(100) UNIQUE NOT NULL,
    connector_id VARCHAR(100),
    connection_id VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    previous_state JSONB,
    new_state JSONB,
    change_details JSONB,
    ip_address VARCHAR(50),
    user_agent TEXT,
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS integration_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_requests INTEGER DEFAULT 0,
    successful_requests INTEGER DEFAULT 0,
    failed_requests INTEGER DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    total_sync_jobs INTEGER DEFAULT 0,
    successful_syncs INTEGER DEFAULT 0,
    failed_syncs INTEGER DEFAULT 0,
    records_synced BIGINT DEFAULT 0,
    webhook_deliveries INTEGER DEFAULT 0,
    webhook_success_rate DECIMAL(5, 4),
    by_connector JSONB,
    by_operation JSONB,
    by_error_type JSONB,
    rate_limit_violations INTEGER DEFAULT 0,
    performance_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS integration_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_connectors INTEGER DEFAULT 0,
    active_connectors INTEGER DEFAULT 0,
    total_connections INTEGER DEFAULT 0,
    active_connections INTEGER DEFAULT 0,
    total_api_requests BIGINT DEFAULT 0,
    successful_requests BIGINT DEFAULT 0,
    failed_requests BIGINT DEFAULT 0,
    request_success_rate DECIMAL(5, 4),
    avg_response_time_ms DECIMAL(10, 2),
    total_data_synced_gb DECIMAL(15, 6),
    by_provider JSONB,
    by_integration_type JSONB,
    top_errors JSONB,
    performance_trends JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_integration_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_integration_connector_type ON integration_connector(connector_type);
CREATE INDEX idx_integration_connector_provider ON integration_connector(provider_name);
CREATE INDEX idx_integration_connector_category ON integration_connector(provider_category);
CREATE INDEX idx_integration_connector_status ON integration_connector(connection_status);
CREATE INDEX idx_integration_connector_active ON integration_connector(is_active) WHERE is_active = true;

CREATE INDEX idx_integration_connection_connector ON integration_connection(connector_id);
CREATE INDEX idx_integration_connection_customer ON integration_connection(customer_id);
CREATE INDEX idx_integration_connection_organization ON integration_connection(organization_id);
CREATE INDEX idx_integration_connection_status ON integration_connection(connection_status);
CREATE INDEX idx_integration_connection_health ON integration_connection(health_status);

CREATE INDEX idx_integration_request_connection ON integration_request(connection_id);
CREATE INDEX idx_integration_request_connector ON integration_request(connector_id);
CREATE INDEX idx_integration_request_type ON integration_request(request_type);
CREATE INDEX idx_integration_request_timestamp ON integration_request(request_timestamp DESC);
CREATE INDEX idx_integration_request_correlation ON integration_request(correlation_id);

CREATE INDEX idx_integration_webhook_connector ON integration_webhook(connector_id);
CREATE INDEX idx_integration_webhook_connection ON integration_webhook(connection_id);
CREATE INDEX idx_integration_webhook_active ON integration_webhook(is_active) WHERE is_active = true;

CREATE INDEX idx_integration_webhook_delivery_webhook ON integration_webhook_delivery(webhook_id);
CREATE INDEX idx_integration_webhook_delivery_event ON integration_webhook_delivery(event_id);
CREATE INDEX idx_integration_webhook_delivery_status ON integration_webhook_delivery(delivery_status);
CREATE INDEX idx_integration_webhook_delivery_scheduled ON integration_webhook_delivery(scheduled_at);

CREATE INDEX idx_integration_sync_job_connection ON integration_sync_job(connection_id);
CREATE INDEX idx_integration_sync_job_status ON integration_sync_job(job_status);
CREATE INDEX idx_integration_sync_job_active ON integration_sync_job(is_active) WHERE is_active = true;
CREATE INDEX idx_integration_sync_job_next_sync ON integration_sync_job(next_sync_at);

CREATE INDEX idx_integration_sync_execution_job ON integration_sync_execution(job_id);
CREATE INDEX idx_integration_sync_execution_status ON integration_sync_execution(execution_status);
CREATE INDEX idx_integration_sync_execution_started ON integration_sync_execution(started_at DESC);

CREATE INDEX idx_integration_mapping_connector ON integration_mapping(connector_id);
CREATE INDEX idx_integration_mapping_type ON integration_mapping(mapping_type);
CREATE INDEX idx_integration_mapping_active ON integration_mapping(is_active) WHERE is_active = true;

CREATE INDEX idx_integration_transformation_type ON integration_transformation(transformation_type);
CREATE INDEX idx_integration_transformation_active ON integration_transformation(is_active) WHERE is_active = true;

CREATE INDEX idx_integration_rate_limit_connector ON integration_rate_limit(connector_id);
CREATE INDEX idx_integration_rate_limit_connection ON integration_rate_limit(connection_id);
CREATE INDEX idx_integration_rate_limit_reset ON integration_rate_limit(reset_at);

CREATE INDEX idx_integration_error_log_connector ON integration_error_log(connector_id);
CREATE INDEX idx_integration_error_log_connection ON integration_error_log(connection_id);
CREATE INDEX idx_integration_error_log_type ON integration_error_log(error_type);
CREATE INDEX idx_integration_error_log_severity ON integration_error_log(severity);
CREATE INDEX idx_integration_error_log_occurred ON integration_error_log(occurred_at DESC);

CREATE INDEX idx_integration_audit_log_connector ON integration_audit_log(connector_id);
CREATE INDEX idx_integration_audit_log_connection ON integration_audit_log(connection_id);
CREATE INDEX idx_integration_audit_log_action ON integration_audit_log(action);
CREATE INDEX idx_integration_audit_log_actor ON integration_audit_log(actor);
CREATE INDEX idx_integration_audit_log_timestamp ON integration_audit_log(timestamp DESC);

CREATE INDEX idx_integration_analytics_period ON integration_analytics(period_end DESC);
CREATE INDEX idx_integration_statistics_period ON integration_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_integration_connector_updated_at BEFORE UPDATE ON integration_connector
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_integration_connection_updated_at BEFORE UPDATE ON integration_connection
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_integration_webhook_updated_at BEFORE UPDATE ON integration_webhook
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_integration_sync_job_updated_at BEFORE UPDATE ON integration_sync_job
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_integration_mapping_updated_at BEFORE UPDATE ON integration_mapping
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_integration_transformation_updated_at BEFORE UPDATE ON integration_transformation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();