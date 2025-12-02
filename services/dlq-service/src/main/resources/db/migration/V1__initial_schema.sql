-- DLQ Service Initial Schema
-- Created: 2025-09-27
-- Description: Dead letter queue management and message recovery schema

CREATE TABLE IF NOT EXISTS dlq_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id VARCHAR(100) UNIQUE NOT NULL,
    dlq_name VARCHAR(255) NOT NULL,
    source_queue VARCHAR(255) NOT NULL,
    source_topic VARCHAR(255),
    source_service VARCHAR(100) NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    message_payload JSONB NOT NULL,
    message_headers JSONB,
    original_timestamp TIMESTAMP NOT NULL,
    dlq_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failure_reason VARCHAR(255) NOT NULL,
    error_message TEXT,
    error_stack_trace TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    processing_attempts INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
    priority INTEGER DEFAULT 100,
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    idempotency_key VARCHAR(100),
    message_size_bytes INTEGER,
    expiry_timestamp TIMESTAMP,
    retention_days INTEGER DEFAULT 30,
    is_poisonous BOOLEAN DEFAULT FALSE,
    poison_detection_reason VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_processing_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id VARCHAR(100) UNIQUE NOT NULL,
    message_id VARCHAR(100) NOT NULL,
    attempt_number INTEGER NOT NULL,
    processing_strategy VARCHAR(50) NOT NULL,
    attempt_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER,
    error_code VARCHAR(50),
    error_message TEXT,
    processor_id VARCHAR(100),
    processor_version VARCHAR(20),
    retry_delay_seconds INTEGER,
    next_retry_at TIMESTAMP,
    success BOOLEAN DEFAULT FALSE,
    recovery_action VARCHAR(100),
    processing_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dlq_processing_attempt FOREIGN KEY (message_id) REFERENCES dlq_message(message_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dlq_recovery_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id VARCHAR(100) UNIQUE NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    source_queue VARCHAR(255),
    source_service VARCHAR(100),
    message_type VARCHAR(50),
    failure_reason_pattern VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    recovery_strategy VARCHAR(100) NOT NULL,
    retry_policy JSONB,
    transformation_rules JSONB,
    routing_rules JSONB,
    condition_expression TEXT,
    max_recovery_attempts INTEGER DEFAULT 5,
    backoff_strategy VARCHAR(50) DEFAULT 'EXPONENTIAL',
    initial_delay_seconds INTEGER DEFAULT 60,
    max_delay_seconds INTEGER DEFAULT 3600,
    success_rate DECIMAL(5, 4) DEFAULT 0,
    total_applications INTEGER DEFAULT 0,
    successful_recoveries INTEGER DEFAULT 0,
    failed_recoveries INTEGER DEFAULT 0,
    last_applied_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_recovery_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id VARCHAR(100) UNIQUE NOT NULL,
    message_id VARCHAR(100) NOT NULL,
    rule_id VARCHAR(100) NOT NULL,
    execution_status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    recovery_strategy VARCHAR(100) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER,
    transformation_applied BOOLEAN DEFAULT FALSE,
    transformed_payload JSONB,
    target_queue VARCHAR(255),
    target_topic VARCHAR(255),
    delivery_status VARCHAR(20),
    delivery_timestamp TIMESTAMP,
    delivery_confirmation VARCHAR(100),
    error_code VARCHAR(50),
    error_message TEXT,
    success BOOLEAN DEFAULT FALSE,
    rollback_required BOOLEAN DEFAULT FALSE,
    rollback_completed BOOLEAN DEFAULT FALSE,
    execution_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dlq_recovery_execution_message FOREIGN KEY (message_id) REFERENCES dlq_message(message_id) ON DELETE CASCADE,
    CONSTRAINT fk_dlq_recovery_execution_rule FOREIGN KEY (rule_id) REFERENCES dlq_recovery_rule(rule_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dlq_queue_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id VARCHAR(100) UNIQUE NOT NULL,
    dlq_name VARCHAR(255) UNIQUE NOT NULL,
    source_queue VARCHAR(255) NOT NULL,
    queue_type VARCHAR(50) NOT NULL,
    description TEXT,
    retention_days INTEGER DEFAULT 30,
    max_message_size_bytes INTEGER DEFAULT 1048576,
    message_ttl_seconds INTEGER,
    auto_recovery_enabled BOOLEAN DEFAULT FALSE,
    auto_recovery_strategy VARCHAR(100),
    poison_message_threshold INTEGER DEFAULT 10,
    alert_threshold INTEGER DEFAULT 100,
    monitoring_enabled BOOLEAN DEFAULT TRUE,
    notification_channels TEXT[],
    storage_backend VARCHAR(50) DEFAULT 'DATABASE',
    compression_enabled BOOLEAN DEFAULT FALSE,
    encryption_enabled BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    configuration_metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    dlq_name VARCHAR(255) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    alert_severity VARCHAR(20) NOT NULL,
    alert_message TEXT NOT NULL,
    message_count INTEGER,
    threshold_value INTEGER,
    current_value INTEGER,
    alert_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    notification_sent BOOLEAN DEFAULT FALSE,
    notification_channels TEXT[],
    alert_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_batch_recovery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id VARCHAR(100) UNIQUE NOT NULL,
    batch_name VARCHAR(255) NOT NULL,
    dlq_name VARCHAR(255) NOT NULL,
    recovery_rule_id VARCHAR(100),
    batch_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    selection_criteria JSONB NOT NULL,
    total_messages INTEGER DEFAULT 0,
    processed_messages INTEGER DEFAULT 0,
    successful_recoveries INTEGER DEFAULT 0,
    failed_recoveries INTEGER DEFAULT 0,
    skipped_messages INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    recovery_rate_per_minute DECIMAL(10, 2),
    error_summary JSONB,
    initiated_by VARCHAR(100) NOT NULL,
    approval_required BOOLEAN DEFAULT FALSE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    cancellation_requested BOOLEAN DEFAULT FALSE,
    cancelled_at TIMESTAMP,
    cancelled_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dlq_batch_recovery_rule FOREIGN KEY (recovery_rule_id) REFERENCES dlq_recovery_rule(rule_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS dlq_poison_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    poison_id VARCHAR(100) UNIQUE NOT NULL,
    message_id VARCHAR(100) NOT NULL,
    dlq_name VARCHAR(255) NOT NULL,
    detection_reason VARCHAR(255) NOT NULL,
    detection_criteria JSONB,
    failure_pattern TEXT,
    consecutive_failures INTEGER NOT NULL,
    affected_services TEXT[],
    quarantine_status VARCHAR(20) NOT NULL DEFAULT 'QUARANTINED',
    quarantined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    investigation_status VARCHAR(20) DEFAULT 'PENDING',
    assigned_to VARCHAR(100),
    root_cause_analysis TEXT,
    remediation_plan TEXT,
    resolution_action VARCHAR(100),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    prevention_measures TEXT,
    message_archive_path VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dlq_poison_message FOREIGN KEY (message_id) REFERENCES dlq_message(message_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dlq_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id VARCHAR(100) UNIQUE NOT NULL,
    message_id VARCHAR(100),
    action VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL,
    previous_state JSONB,
    new_state JSONB,
    change_reason VARCHAR(255),
    change_details JSONB,
    ip_address VARCHAR(50),
    user_agent TEXT,
    success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id VARCHAR(100) UNIQUE NOT NULL,
    dlq_name VARCHAR(255) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DECIMAL(18, 6) NOT NULL,
    metric_unit VARCHAR(50),
    dimensions JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggregation_period VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_messages INTEGER DEFAULT 0,
    by_dlq_name JSONB,
    by_source_service JSONB,
    by_failure_reason JSONB,
    by_message_type JSONB,
    recovery_attempts INTEGER DEFAULT 0,
    successful_recoveries INTEGER DEFAULT 0,
    failed_recoveries INTEGER DEFAULT 0,
    recovery_success_rate DECIMAL(5, 4),
    poison_messages_detected INTEGER DEFAULT 0,
    avg_message_age_hours DECIMAL(10, 2),
    avg_recovery_time_minutes DECIMAL(10, 2),
    alerts_triggered INTEGER DEFAULT 0,
    batch_recoveries_executed INTEGER DEFAULT 0,
    message_retention_stats JSONB,
    performance_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dlq_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_dlq_messages BIGINT DEFAULT 0,
    messages_recovered BIGINT DEFAULT 0,
    messages_expired BIGINT DEFAULT 0,
    poison_messages BIGINT DEFAULT 0,
    recovery_success_rate DECIMAL(5, 4),
    avg_time_in_dlq_hours DECIMAL(10, 2),
    by_source_service JSONB,
    by_failure_category JSONB,
    top_failure_reasons JSONB,
    recovery_trends JSONB,
    queue_health_scores JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_dlq_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_dlq_message_dlq_name ON dlq_message(dlq_name);
CREATE INDEX idx_dlq_message_source_queue ON dlq_message(source_queue);
CREATE INDEX idx_dlq_message_source_service ON dlq_message(source_service);
CREATE INDEX idx_dlq_message_status ON dlq_message(status);
CREATE INDEX idx_dlq_message_type ON dlq_message(message_type);
CREATE INDEX idx_dlq_message_dlq_timestamp ON dlq_message(dlq_timestamp DESC);
CREATE INDEX idx_dlq_message_poisonous ON dlq_message(is_poisonous) WHERE is_poisonous = true;
CREATE INDEX idx_dlq_message_correlation ON dlq_message(correlation_id);
CREATE INDEX idx_dlq_message_expiry ON dlq_message(expiry_timestamp);

CREATE INDEX idx_dlq_processing_attempt_message ON dlq_processing_attempt(message_id);
CREATE INDEX idx_dlq_processing_attempt_status ON dlq_processing_attempt(attempt_status);
CREATE INDEX idx_dlq_processing_attempt_started ON dlq_processing_attempt(started_at DESC);
CREATE INDEX idx_dlq_processing_attempt_next_retry ON dlq_processing_attempt(next_retry_at);

CREATE INDEX idx_dlq_recovery_rule_type ON dlq_recovery_rule(rule_type);
CREATE INDEX idx_dlq_recovery_rule_source ON dlq_recovery_rule(source_service);
CREATE INDEX idx_dlq_recovery_rule_active ON dlq_recovery_rule(is_active) WHERE is_active = true;
CREATE INDEX idx_dlq_recovery_rule_priority ON dlq_recovery_rule(priority DESC);

CREATE INDEX idx_dlq_recovery_execution_message ON dlq_recovery_execution(message_id);
CREATE INDEX idx_dlq_recovery_execution_rule ON dlq_recovery_execution(rule_id);
CREATE INDEX idx_dlq_recovery_execution_status ON dlq_recovery_execution(execution_status);
CREATE INDEX idx_dlq_recovery_execution_started ON dlq_recovery_execution(started_at DESC);

CREATE INDEX idx_dlq_queue_configuration_name ON dlq_queue_configuration(dlq_name);
CREATE INDEX idx_dlq_queue_configuration_source ON dlq_queue_configuration(source_queue);
CREATE INDEX idx_dlq_queue_configuration_active ON dlq_queue_configuration(is_active) WHERE is_active = true;

CREATE INDEX idx_dlq_alert_dlq_name ON dlq_alert(dlq_name);
CREATE INDEX idx_dlq_alert_type ON dlq_alert(alert_type);
CREATE INDEX idx_dlq_alert_severity ON dlq_alert(alert_severity);
CREATE INDEX idx_dlq_alert_status ON dlq_alert(alert_status);
CREATE INDEX idx_dlq_alert_triggered ON dlq_alert(triggered_at DESC);

CREATE INDEX idx_dlq_batch_recovery_dlq_name ON dlq_batch_recovery(dlq_name);
CREATE INDEX idx_dlq_batch_recovery_rule ON dlq_batch_recovery(recovery_rule_id);
CREATE INDEX idx_dlq_batch_recovery_status ON dlq_batch_recovery(batch_status);
CREATE INDEX idx_dlq_batch_recovery_started ON dlq_batch_recovery(started_at DESC);

CREATE INDEX idx_dlq_poison_message_message ON dlq_poison_message(message_id);
CREATE INDEX idx_dlq_poison_message_dlq_name ON dlq_poison_message(dlq_name);
CREATE INDEX idx_dlq_poison_message_status ON dlq_poison_message(quarantine_status);
CREATE INDEX idx_dlq_poison_message_investigation ON dlq_poison_message(investigation_status);

CREATE INDEX idx_dlq_audit_log_message ON dlq_audit_log(message_id);
CREATE INDEX idx_dlq_audit_log_action ON dlq_audit_log(action);
CREATE INDEX idx_dlq_audit_log_actor ON dlq_audit_log(actor);
CREATE INDEX idx_dlq_audit_log_timestamp ON dlq_audit_log(timestamp DESC);

CREATE INDEX idx_dlq_metrics_dlq_name ON dlq_metrics(dlq_name);
CREATE INDEX idx_dlq_metrics_name ON dlq_metrics(metric_name);
CREATE INDEX idx_dlq_metrics_timestamp ON dlq_metrics(timestamp DESC);

CREATE INDEX idx_dlq_analytics_period ON dlq_analytics(period_end DESC);
CREATE INDEX idx_dlq_statistics_period ON dlq_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_dlq_message_updated_at BEFORE UPDATE ON dlq_message
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dlq_recovery_rule_updated_at BEFORE UPDATE ON dlq_recovery_rule
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dlq_queue_configuration_updated_at BEFORE UPDATE ON dlq_queue_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dlq_alert_updated_at BEFORE UPDATE ON dlq_alert
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dlq_batch_recovery_updated_at BEFORE UPDATE ON dlq_batch_recovery
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dlq_poison_message_updated_at BEFORE UPDATE ON dlq_poison_message
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();