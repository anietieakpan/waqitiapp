-- Saga Orchestration Service Initial Schema
-- Created: 2025-09-27
-- Description: Distributed transaction management, saga patterns, and compensation logic schema

CREATE TABLE IF NOT EXISTS saga_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id VARCHAR(100) UNIQUE NOT NULL,
    saga_name VARCHAR(255) NOT NULL,
    saga_version VARCHAR(50) NOT NULL,
    description TEXT,
    saga_type VARCHAR(50) NOT NULL,
    saga_pattern VARCHAR(50) NOT NULL,
    saga_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    participant_services TEXT[] NOT NULL,
    saga_steps JSONB NOT NULL,
    compensation_steps JSONB NOT NULL,
    timeout_seconds INTEGER DEFAULT 3600,
    retry_policy JSONB,
    isolation_level VARCHAR(50),
    consistency_model VARCHAR(50),
    recovery_strategy VARCHAR(50) NOT NULL,
    compensation_strategy VARCHAR(50) NOT NULL,
    failure_handling JSONB NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    execution_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    compensation_count INTEGER DEFAULT 0,
    avg_execution_time_seconds DECIMAL(10, 2),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_saga_version UNIQUE (saga_name, saga_version)
);

CREATE TABLE IF NOT EXISTS saga_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id VARCHAR(100) UNIQUE NOT NULL,
    saga_id VARCHAR(100) NOT NULL,
    execution_status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    correlation_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100),
    parent_execution_id VARCHAR(100),
    root_execution_id VARCHAR(100),
    saga_data JSONB NOT NULL,
    execution_context JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    current_step INTEGER DEFAULT 0,
    total_steps INTEGER NOT NULL,
    completed_steps INTEGER DEFAULT 0,
    failed_steps INTEGER DEFAULT 0,
    compensated_steps INTEGER DEFAULT 0,
    compensation_required BOOLEAN DEFAULT FALSE,
    compensation_started_at TIMESTAMP,
    compensation_completed_at TIMESTAMP,
    timeout_at TIMESTAMP,
    is_timeout BOOLEAN DEFAULT FALSE,
    error_details JSONB,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    initiated_by VARCHAR(100) NOT NULL,
    initiated_by_type VARCHAR(50) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saga_execution FOREIGN KEY (saga_id) REFERENCES saga_definition(saga_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS saga_step_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_execution_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    step_number INTEGER NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    step_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    participant_service VARCHAR(100) NOT NULL,
    operation_name VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    input_data JSONB NOT NULL,
    output_data JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    timeout_seconds INTEGER,
    is_timeout BOOLEAN DEFAULT FALSE,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    retry_delay_seconds INTEGER DEFAULT 30,
    last_retry_at TIMESTAMP,
    success BOOLEAN DEFAULT FALSE,
    error_code VARCHAR(50),
    error_message TEXT,
    error_details JSONB,
    compensation_required BOOLEAN DEFAULT FALSE,
    compensation_step_id VARCHAR(100),
    is_compensated BOOLEAN DEFAULT FALSE,
    compensation_started_at TIMESTAMP,
    compensation_completed_at TIMESTAMP,
    compensation_error TEXT,
    idempotency_key VARCHAR(100),
    transaction_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saga_step_execution FOREIGN KEY (execution_id) REFERENCES saga_execution(execution_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS saga_compensation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    compensation_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    step_execution_id VARCHAR(100) NOT NULL,
    compensation_type VARCHAR(50) NOT NULL,
    compensation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    participant_service VARCHAR(100) NOT NULL,
    compensation_operation VARCHAR(100) NOT NULL,
    compensation_data JSONB NOT NULL,
    original_step_data JSONB,
    scheduled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 5,
    retry_delay_seconds INTEGER DEFAULT 60,
    last_retry_at TIMESTAMP,
    success BOOLEAN DEFAULT FALSE,
    error_code VARCHAR(50),
    error_message TEXT,
    error_details JSONB,
    manual_intervention_required BOOLEAN DEFAULT FALSE,
    manual_compensation_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saga_compensation_execution FOREIGN KEY (execution_id) REFERENCES saga_execution(execution_id) ON DELETE CASCADE,
    CONSTRAINT fk_saga_compensation_step FOREIGN KEY (step_execution_id) REFERENCES saga_step_execution(step_execution_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS saga_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(100) NOT NULL,
    saga_id VARCHAR(100),
    execution_id VARCHAR(100) NOT NULL,
    step_execution_id VARCHAR(100),
    event_data JSONB NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    causation_id VARCHAR(100),
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    processing_error TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saga_event_execution FOREIGN KEY (execution_id) REFERENCES saga_execution(execution_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS saga_lock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lock_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    lock_type VARCHAR(50) NOT NULL,
    lock_mode VARCHAR(20) NOT NULL,
    lock_status VARCHAR(20) NOT NULL DEFAULT 'ACQUIRED',
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    heartbeat_at TIMESTAMP,
    heartbeat_interval_seconds INTEGER DEFAULT 30,
    owner_service VARCHAR(100) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saga_lock FOREIGN KEY (execution_id) REFERENCES saga_execution(execution_id) ON DELETE CASCADE,
    CONSTRAINT unique_resource_lock UNIQUE (resource_id, execution_id)
);

CREATE TABLE IF NOT EXISTS saga_participant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id VARCHAR(100) UNIQUE NOT NULL,
    participant_name VARCHAR(255) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    health_check_endpoint VARCHAR(500),
    participant_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    supported_operations JSONB NOT NULL,
    compensation_capabilities JSONB NOT NULL,
    timeout_seconds INTEGER DEFAULT 300,
    retry_policy JSONB,
    circuit_breaker_config JSONB,
    last_health_check TIMESTAMP,
    health_status VARCHAR(20),
    total_invocations INTEGER DEFAULT 0,
    successful_invocations INTEGER DEFAULT 0,
    failed_invocations INTEGER DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_correlation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id VARCHAR(100) UNIQUE NOT NULL,
    correlation_type VARCHAR(50) NOT NULL,
    root_execution_id VARCHAR(100) NOT NULL,
    related_executions TEXT[] NOT NULL,
    correlation_context JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    is_complete BOOLEAN DEFAULT FALSE,
    total_executions INTEGER DEFAULT 0,
    successful_executions INTEGER DEFAULT 0,
    failed_executions INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_state_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    snapshot_type VARCHAR(50) NOT NULL,
    sequence_number INTEGER NOT NULL,
    saga_state JSONB NOT NULL,
    execution_state JSONB NOT NULL,
    step_states JSONB NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saga_snapshot FOREIGN KEY (execution_id) REFERENCES saga_execution(execution_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS saga_timeout (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timeout_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100),
    step_execution_id VARCHAR(100),
    timeout_type VARCHAR(50) NOT NULL,
    timeout_at TIMESTAMP NOT NULL,
    timeout_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    triggered_at TIMESTAMP,
    timeout_action VARCHAR(50) NOT NULL,
    action_taken TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id VARCHAR(100) UNIQUE NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    saga_id VARCHAR(100),
    execution_id VARCHAR(100),
    metric_value DECIMAL(18, 6) NOT NULL,
    metric_unit VARCHAR(50) NOT NULL,
    dimensions JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_sagas INTEGER DEFAULT 0,
    total_executions BIGINT DEFAULT 0,
    successful_executions BIGINT DEFAULT 0,
    failed_executions BIGINT DEFAULT 0,
    compensated_executions BIGINT DEFAULT 0,
    execution_success_rate DECIMAL(5, 4),
    avg_execution_time_seconds DECIMAL(10, 2),
    avg_steps_per_execution DECIMAL(10, 2),
    total_compensations BIGINT DEFAULT 0,
    successful_compensations BIGINT DEFAULT 0,
    failed_compensations BIGINT DEFAULT 0,
    compensation_success_rate DECIMAL(5, 4),
    timeout_rate DECIMAL(5, 4),
    by_saga JSONB,
    by_participant JSONB,
    by_failure_reason JSONB,
    performance_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS saga_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_saga_executions BIGINT DEFAULT 0,
    successful_executions BIGINT DEFAULT 0,
    failed_executions BIGINT DEFAULT 0,
    compensated_executions BIGINT DEFAULT 0,
    execution_success_rate DECIMAL(5, 4),
    avg_execution_duration_seconds DECIMAL(10, 2),
    total_compensation_attempts BIGINT DEFAULT 0,
    successful_compensations BIGINT DEFAULT 0,
    compensation_success_rate DECIMAL(5, 4),
    total_timeouts INTEGER DEFAULT 0,
    by_saga_type JSONB,
    by_participant_service JSONB,
    failure_analysis JSONB,
    performance_trends JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_saga_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_saga_definition_name ON saga_definition(saga_name);
CREATE INDEX idx_saga_definition_type ON saga_definition(saga_type);
CREATE INDEX idx_saga_definition_pattern ON saga_definition(saga_pattern);
CREATE INDEX idx_saga_definition_status ON saga_definition(saga_status);
CREATE INDEX idx_saga_definition_active ON saga_definition(is_active) WHERE is_active = true;

CREATE INDEX idx_saga_execution_saga ON saga_execution(saga_id);
CREATE INDEX idx_saga_execution_status ON saga_execution(execution_status);
CREATE INDEX idx_saga_execution_correlation ON saga_execution(correlation_id);
CREATE INDEX idx_saga_execution_started ON saga_execution(started_at DESC);
CREATE INDEX idx_saga_execution_parent ON saga_execution(parent_execution_id);
CREATE INDEX idx_saga_execution_root ON saga_execution(root_execution_id);

CREATE INDEX idx_saga_step_execution_execution ON saga_step_execution(execution_id);
CREATE INDEX idx_saga_step_execution_status ON saga_step_execution(step_status);
CREATE INDEX idx_saga_step_execution_service ON saga_step_execution(participant_service);
CREATE INDEX idx_saga_step_execution_compensated ON saga_step_execution(is_compensated);

CREATE INDEX idx_saga_compensation_execution ON saga_compensation(execution_id);
CREATE INDEX idx_saga_compensation_step ON saga_compensation(step_execution_id);
CREATE INDEX idx_saga_compensation_status ON saga_compensation(compensation_status);
CREATE INDEX idx_saga_compensation_service ON saga_compensation(participant_service);

CREATE INDEX idx_saga_event_execution ON saga_event(execution_id);
CREATE INDEX idx_saga_event_type ON saga_event(event_type);
CREATE INDEX idx_saga_event_correlation ON saga_event(correlation_id);
CREATE INDEX idx_saga_event_timestamp ON saga_event(event_timestamp DESC);
CREATE INDEX idx_saga_event_processed ON saga_event(processed) WHERE processed = false;

CREATE INDEX idx_saga_lock_resource ON saga_lock(resource_id);
CREATE INDEX idx_saga_lock_execution ON saga_lock(execution_id);
CREATE INDEX idx_saga_lock_status ON saga_lock(lock_status);
CREATE INDEX idx_saga_lock_expires ON saga_lock(expires_at);

CREATE INDEX idx_saga_participant_service ON saga_participant(service_name);
CREATE INDEX idx_saga_participant_status ON saga_participant(participant_status);
CREATE INDEX idx_saga_participant_active ON saga_participant(is_active) WHERE is_active = true;

CREATE INDEX idx_saga_correlation_root ON saga_correlation(root_execution_id);
CREATE INDEX idx_saga_correlation_type ON saga_correlation(correlation_type);
CREATE INDEX idx_saga_correlation_complete ON saga_correlation(is_complete);

CREATE INDEX idx_saga_snapshot_execution ON saga_state_snapshot(execution_id);
CREATE INDEX idx_saga_snapshot_sequence ON saga_state_snapshot(sequence_number);
CREATE INDEX idx_saga_snapshot_timestamp ON saga_state_snapshot(timestamp DESC);

CREATE INDEX idx_saga_timeout_execution ON saga_timeout(execution_id);
CREATE INDEX idx_saga_timeout_step ON saga_timeout(step_execution_id);
CREATE INDEX idx_saga_timeout_at ON saga_timeout(timeout_at);
CREATE INDEX idx_saga_timeout_status ON saga_timeout(timeout_status);

CREATE INDEX idx_saga_metrics_saga ON saga_metrics(saga_id);
CREATE INDEX idx_saga_metrics_execution ON saga_metrics(execution_id);
CREATE INDEX idx_saga_metrics_name ON saga_metrics(metric_name);
CREATE INDEX idx_saga_metrics_timestamp ON saga_metrics(timestamp DESC);

CREATE INDEX idx_saga_analytics_period ON saga_analytics(period_end DESC);
CREATE INDEX idx_saga_statistics_period ON saga_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_saga_definition_updated_at BEFORE UPDATE ON saga_definition
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_saga_execution_updated_at BEFORE UPDATE ON saga_execution
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_saga_step_execution_updated_at BEFORE UPDATE ON saga_step_execution
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_saga_compensation_updated_at BEFORE UPDATE ON saga_compensation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_saga_participant_updated_at BEFORE UPDATE ON saga_participant
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_saga_correlation_updated_at BEFORE UPDATE ON saga_correlation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_saga_timeout_updated_at BEFORE UPDATE ON saga_timeout
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();