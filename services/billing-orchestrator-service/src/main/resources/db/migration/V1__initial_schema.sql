-- Billing Orchestrator Service Initial Schema
-- Created: 2025-09-27
-- Description: Billing workflow orchestration and coordination schema

CREATE TABLE IF NOT EXISTS billing_workflow (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id VARCHAR(100) UNIQUE NOT NULL,
    workflow_name VARCHAR(255) NOT NULL,
    workflow_type VARCHAR(50) NOT NULL,
    description TEXT,
    workflow_definition JSONB NOT NULL,
    workflow_version VARCHAR(20) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    trigger_conditions JSONB,
    input_schema JSONB,
    output_schema JSONB,
    timeout_minutes INTEGER DEFAULT 60,
    retry_policy JSONB,
    error_handling_policy JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_workflow_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id VARCHAR(100) UNIQUE NOT NULL,
    workflow_id VARCHAR(100) NOT NULL,
    execution_status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    customer_id UUID,
    billing_cycle_id VARCHAR(100),
    input_data JSONB NOT NULL,
    output_data JSONB,
    execution_context JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    current_step VARCHAR(100),
    total_steps INTEGER DEFAULT 0,
    completed_steps INTEGER DEFAULT 0,
    failed_steps INTEGER DEFAULT 0,
    skipped_steps INTEGER DEFAULT 0,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    parent_execution_id VARCHAR(100),
    correlation_id VARCHAR(100),
    priority INTEGER DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_workflow_execution FOREIGN KEY (workflow_id) REFERENCES billing_workflow(workflow_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_workflow_step (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    step_order INTEGER NOT NULL,
    step_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    step_definition JSONB NOT NULL,
    input_data JSONB,
    output_data JSONB,
    step_context JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_code VARCHAR(50),
    error_message TEXT,
    skip_reason VARCHAR(100),
    compensation_required BOOLEAN DEFAULT FALSE,
    compensated BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_workflow_step FOREIGN KEY (execution_id) REFERENCES billing_workflow_execution(execution_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_orchestration_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_source VARCHAR(100) NOT NULL,
    execution_id VARCHAR(100),
    step_id VARCHAR(100),
    event_data JSONB NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_compensation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    compensation_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    step_id VARCHAR(100) NOT NULL,
    compensation_type VARCHAR(50) NOT NULL,
    compensation_action VARCHAR(100) NOT NULL,
    compensation_data JSONB,
    compensation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    success BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_compensation_execution FOREIGN KEY (execution_id) REFERENCES billing_workflow_execution(execution_id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_compensation_step FOREIGN KEY (step_id) REFERENCES billing_workflow_step(step_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_orchestration_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_item_id VARCHAR(100) UNIQUE NOT NULL,
    queue_name VARCHAR(255) NOT NULL,
    workflow_id VARCHAR(100) NOT NULL,
    priority INTEGER DEFAULT 100,
    scheduled_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    input_data JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    assigned_to VARCHAR(100),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    execution_id VARCHAR(100),
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_orchestration_queue FOREIGN KEY (workflow_id) REFERENCES billing_workflow(workflow_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_dependency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dependency_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    dependent_step_id VARCHAR(100) NOT NULL,
    prerequisite_step_id VARCHAR(100) NOT NULL,
    dependency_type VARCHAR(50) NOT NULL,
    condition_expression VARCHAR(500),
    is_satisfied BOOLEAN DEFAULT FALSE,
    satisfied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_dependency_execution FOREIGN KEY (execution_id) REFERENCES billing_workflow_execution(execution_id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_dependency_dependent FOREIGN KEY (dependent_step_id) REFERENCES billing_workflow_step(step_id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_dependency_prerequisite FOREIGN KEY (prerequisite_step_id) REFERENCES billing_workflow_step(step_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id VARCHAR(100) UNIQUE NOT NULL,
    workflow_id VARCHAR(100) NOT NULL,
    schedule_name VARCHAR(255) NOT NULL,
    schedule_type VARCHAR(50) NOT NULL,
    cron_expression VARCHAR(100),
    frequency VARCHAR(20),
    schedule_parameters JSONB,
    timezone VARCHAR(50) DEFAULT 'UTC',
    is_active BOOLEAN DEFAULT TRUE,
    next_execution_time TIMESTAMP,
    last_execution_time TIMESTAMP,
    last_execution_status VARCHAR(20),
    execution_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    max_executions INTEGER,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_schedule FOREIGN KEY (workflow_id) REFERENCES billing_workflow(workflow_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_orchestration_lock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lock_id VARCHAR(100) UNIQUE NOT NULL,
    resource_name VARCHAR(255) NOT NULL,
    lock_owner VARCHAR(100) NOT NULL,
    execution_id VARCHAR(100),
    lock_type VARCHAR(50) NOT NULL,
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_resource_lock UNIQUE (resource_name)
);

CREATE TABLE IF NOT EXISTS billing_workflow_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(100) UNIQUE NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    description TEXT,
    template_definition JSONB NOT NULL,
    template_version VARCHAR(20) NOT NULL,
    category VARCHAR(100),
    tags TEXT[],
    is_active BOOLEAN DEFAULT TRUE,
    usage_count INTEGER DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_orchestration_metric (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100),
    step_id VARCHAR(100),
    metric_name VARCHAR(255) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DECIMAL(18, 6) NOT NULL,
    metric_unit VARCHAR(50),
    dimensions JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_workflow_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id VARCHAR(100) UNIQUE NOT NULL,
    execution_id VARCHAR(100) NOT NULL,
    step_id VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL,
    previous_state JSONB,
    new_state JSONB,
    change_reason VARCHAR(255),
    ip_address VARCHAR(50),
    user_agent TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_workflow_audit FOREIGN KEY (execution_id) REFERENCES billing_workflow_execution(execution_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_orchestration_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id VARCHAR(100) UNIQUE NOT NULL,
    config_name VARCHAR(255) NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    config_value JSONB NOT NULL,
    workflow_id VARCHAR(100),
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    last_modified_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_orchestration_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_executions INTEGER DEFAULT 0,
    successful_executions INTEGER DEFAULT 0,
    failed_executions INTEGER DEFAULT 0,
    avg_execution_time_minutes DECIMAL(10, 2),
    avg_step_count DECIMAL(10, 2),
    compensation_rate DECIMAL(5, 4),
    retry_rate DECIMAL(5, 4),
    by_workflow_type JSONB,
    by_execution_status JSONB,
    performance_metrics JSONB,
    error_analysis JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_orchestration_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_quiz(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_workflows INTEGER DEFAULT 0,
    active_workflows INTEGER DEFAULT 0,
    total_executions BIGINT DEFAULT 0,
    successful_executions BIGINT DEFAULT 0,
    failed_executions BIGINT DEFAULT 0,
    avg_execution_time_minutes DECIMAL(10, 2),
    success_rate DECIMAL(5, 4),
    compensation_events INTEGER DEFAULT 0,
    by_workflow_type JSONB,
    by_status JSONB,
    top_failing_workflows JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_billing_orchestration_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_billing_workflow_type ON billing_workflow(workflow_type);
CREATE INDEX idx_billing_workflow_active ON billing_workflow(is_active) WHERE is_active = true;
CREATE INDEX idx_billing_workflow_default ON billing_workflow(is_default) WHERE is_default = true;

CREATE INDEX idx_billing_workflow_execution_workflow ON billing_workflow_execution(workflow_id);
CREATE INDEX idx_billing_workflow_execution_status ON billing_workflow_execution(execution_status);
CREATE INDEX idx_billing_workflow_execution_customer ON billing_workflow_execution(customer_id);
CREATE INDEX idx_billing_workflow_execution_cycle ON billing_workflow_execution(billing_cycle_id);
CREATE INDEX idx_billing_workflow_execution_started ON billing_workflow_execution(started_at DESC);
CREATE INDEX idx_billing_workflow_execution_correlation ON billing_workflow_execution(correlation_id);

CREATE INDEX idx_billing_workflow_step_execution ON billing_workflow_step(execution_id);
CREATE INDEX idx_billing_workflow_step_status ON billing_workflow_step(step_status);
CREATE INDEX idx_billing_workflow_step_order ON billing_workflow_step(step_order);
CREATE INDEX idx_billing_workflow_step_compensation ON billing_workflow_step(compensation_required) WHERE compensation_required = true;

CREATE INDEX idx_billing_orchestration_event_type ON billing_orchestration_event(event_type);
CREATE INDEX idx_billing_orchestration_event_source ON billing_orchestration_event(event_source);
CREATE INDEX idx_billing_orchestration_event_execution ON billing_orchestration_event(execution_id);
CREATE INDEX idx_billing_orchestration_event_processed ON billing_orchestration_event(processed) WHERE processed = false;
CREATE INDEX idx_billing_orchestration_event_timestamp ON billing_orchestration_event(timestamp DESC);

CREATE INDEX idx_billing_compensation_execution ON billing_compensation(execution_id);
CREATE INDEX idx_billing_compensation_step ON billing_compensation(step_id);
CREATE INDEX idx_billing_compensation_status ON billing_compensation(compensation_status);

CREATE INDEX idx_billing_orchestration_queue_workflow ON billing_orchestration_queue(workflow_id);
CREATE INDEX idx_billing_orchestration_queue_status ON billing_orchestration_queue(status);
CREATE INDEX idx_billing_orchestration_queue_priority ON billing_orchestration_queue(priority DESC);
CREATE INDEX idx_billing_orchestration_queue_scheduled ON billing_orchestration_queue(scheduled_time);

CREATE INDEX idx_billing_dependency_execution ON billing_dependency(execution_id);
CREATE INDEX idx_billing_dependency_dependent ON billing_dependency(dependent_step_id);
CREATE INDEX idx_billing_dependency_prerequisite ON billing_dependency(prerequisite_step_id);
CREATE INDEX idx_billing_dependency_satisfied ON billing_dependency(is_satisfied);

CREATE INDEX idx_billing_schedule_workflow ON billing_schedule(workflow_id);
CREATE INDEX idx_billing_schedule_active ON billing_schedule(is_active) WHERE is_active = true;
CREATE INDEX idx_billing_schedule_next_execution ON billing_schedule(next_execution_time);

CREATE INDEX idx_billing_orchestration_lock_resource ON billing_orchestration_lock(resource_name);
CREATE INDEX idx_billing_orchestration_lock_owner ON billing_orchestration_lock(lock_owner);
CREATE INDEX idx_billing_orchestration_lock_expires ON billing_orchestration_lock(expires_at);
CREATE INDEX idx_billing_orchestration_lock_active ON billing_orchestration_lock(is_active) WHERE is_active = true;

CREATE INDEX idx_billing_workflow_template_type ON billing_workflow_template(template_type);
CREATE INDEX idx_billing_workflow_template_category ON billing_workflow_template(category);
CREATE INDEX idx_billing_workflow_template_active ON billing_workflow_template(is_active) WHERE is_active = true;
CREATE INDEX idx_billing_workflow_template_tags ON billing_workflow_template USING gin(tags);

CREATE INDEX idx_billing_orchestration_metric_execution ON billing_orchestration_metric(execution_id);
CREATE INDEX idx_billing_orchestration_metric_step ON billing_orchestration_metric(step_id);
CREATE INDEX idx_billing_orchestration_metric_name ON billing_orchestration_metric(metric_name);
CREATE INDEX idx_billing_orchestration_metric_timestamp ON billing_orchestration_metric(timestamp DESC);

CREATE INDEX idx_billing_workflow_audit_execution ON billing_workflow_audit(execution_id);
CREATE INDEX idx_billing_workflow_audit_step ON billing_workflow_audit(step_id);
CREATE INDEX idx_billing_workflow_audit_actor ON billing_workflow_audit(actor);
CREATE INDEX idx_billing_workflow_audit_timestamp ON billing_workflow_audit(timestamp DESC);

CREATE INDEX idx_billing_orchestration_configuration_type ON billing_orchestration_configuration(config_type);
CREATE INDEX idx_billing_orchestration_configuration_workflow ON billing_orchestration_configuration(workflow_id);
CREATE INDEX idx_billing_orchestration_configuration_active ON billing_orchestration_configuration(is_active) WHERE is_active = true;

CREATE INDEX idx_billing_orchestration_analytics_period ON billing_orchestration_analytics(period_end DESC);
CREATE INDEX idx_billing_orchestration_statistics_period ON billing_orchestration_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_billing_workflow_updated_at BEFORE UPDATE ON billing_workflow
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_workflow_execution_updated_at BEFORE UPDATE ON billing_workflow_execution
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_workflow_step_updated_at BEFORE UPDATE ON billing_workflow_step
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_compensation_updated_at BEFORE UPDATE ON billing_compensation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_orchestration_queue_updated_at BEFORE UPDATE ON billing_orchestration_queue
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_schedule_updated_at BEFORE UPDATE ON billing_schedule
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_workflow_template_updated_at BEFORE UPDATE ON billing_workflow_template
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_orchestration_configuration_updated_at BEFORE UPDATE ON billing_orchestration_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();