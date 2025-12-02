-- Infrastructure Service Initial Schema
-- Created: 2025-09-27
-- Description: Infrastructure monitoring, resource management, and deployment schema

CREATE TABLE IF NOT EXISTS infrastructure_resource (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id VARCHAR(100) UNIQUE NOT NULL,
    resource_name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_category VARCHAR(100) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    region VARCHAR(100) NOT NULL,
    availability_zone VARCHAR(100),
    resource_status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    resource_arn VARCHAR(500),
    external_id VARCHAR(255),
    resource_configuration JSONB NOT NULL,
    capacity_config JSONB,
    network_config JSONB,
    security_config JSONB,
    tags JSONB,
    cost_allocation_tags JSONB,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    owner_team VARCHAR(100) NOT NULL,
    cost_center VARCHAR(100),
    provisioning_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decommission_date TIMESTAMP,
    backup_enabled BOOLEAN DEFAULT TRUE,
    monitoring_enabled BOOLEAN DEFAULT TRUE,
    auto_scaling_enabled BOOLEAN DEFAULT FALSE,
    high_availability_enabled BOOLEAN DEFAULT FALSE,
    disaster_recovery_enabled BOOLEAN DEFAULT FALSE,
    compliance_requirements TEXT[],
    documentation_url VARCHAR(1000),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS infrastructure_deployment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deployment_id VARCHAR(100) UNIQUE NOT NULL,
    deployment_name VARCHAR(255) NOT NULL,
    deployment_type VARCHAR(50) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    target_resources TEXT[] NOT NULL,
    deployment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deployment_strategy VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,
    previous_version VARCHAR(50),
    artifact_url VARCHAR(1000),
    configuration JSONB,
    rollback_enabled BOOLEAN DEFAULT TRUE,
    health_check_config JSONB,
    canary_percentage DECIMAL(5, 2),
    deployment_stages JSONB,
    current_stage INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    success_rate DECIMAL(5, 4),
    error_count INTEGER DEFAULT 0,
    warning_count INTEGER DEFAULT 0,
    deployment_logs TEXT,
    rollback_initiated BOOLEAN DEFAULT FALSE,
    rollback_reason VARCHAR(255),
    rollback_completed_at TIMESTAMP,
    deployed_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS infrastructure_metric (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DECIMAL(18, 6) NOT NULL,
    metric_unit VARCHAR(50) NOT NULL,
    dimensions JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggregation_type VARCHAR(20),
    aggregation_period VARCHAR(20),
    threshold_value DECIMAL(18, 6),
    threshold_breached BOOLEAN DEFAULT FALSE,
    anomaly_detected BOOLEAN DEFAULT FALSE,
    anomaly_score DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructure_metric FOREIGN KEY (resource_id) REFERENCES infrastructure_resource(resource_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS infrastructure_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(100),
    alert_type VARCHAR(50) NOT NULL,
    alert_severity VARCHAR(20) NOT NULL,
    alert_name VARCHAR(255) NOT NULL,
    alert_description TEXT NOT NULL,
    alert_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    trigger_condition VARCHAR(500) NOT NULL,
    threshold_value DECIMAL(18, 6),
    current_value DECIMAL(18, 6),
    evaluation_periods INTEGER DEFAULT 1,
    consecutive_breaches INTEGER DEFAULT 0,
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_notes TEXT,
    auto_resolved BOOLEAN DEFAULT FALSE,
    notification_sent BOOLEAN DEFAULT FALSE,
    notification_channels TEXT[],
    escalation_level INTEGER DEFAULT 0,
    escalated_to VARCHAR(100),
    alert_data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS infrastructure_capacity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    capacity_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    capacity_type VARCHAR(50) NOT NULL,
    total_capacity DECIMAL(18, 2) NOT NULL,
    used_capacity DECIMAL(18, 2) NOT NULL,
    available_capacity DECIMAL(18, 2) NOT NULL,
    utilization_percentage DECIMAL(5, 2) NOT NULL,
    capacity_unit VARCHAR(50) NOT NULL,
    capacity_threshold DECIMAL(5, 2) DEFAULT 80,
    threshold_breached BOOLEAN DEFAULT FALSE,
    forecast_capacity DECIMAL(18, 2),
    forecast_days INTEGER,
    capacity_trend VARCHAR(20),
    growth_rate DECIMAL(5, 4),
    measurement_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recommendations TEXT[],
    scaling_suggestion VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructure_capacity FOREIGN KEY (resource_id) REFERENCES infrastructure_resource(resource_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS infrastructure_cost (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cost_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    cost_period_start DATE NOT NULL,
    cost_period_end DATE NOT NULL,
    cost_amount DECIMAL(18, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    cost_type VARCHAR(50) NOT NULL,
    cost_category VARCHAR(100),
    billing_mode VARCHAR(50),
    unit_cost DECIMAL(18, 6),
    quantity DECIMAL(18, 6),
    unit_type VARCHAR(50),
    cost_breakdown JSONB,
    budget_allocated DECIMAL(18, 2),
    budget_variance DECIMAL(18, 2),
    cost_optimization_suggestions TEXT[],
    tags JSONB,
    invoice_id VARCHAR(100),
    payment_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructure_cost FOREIGN KEY (resource_id) REFERENCES infrastructure_resource(resource_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS infrastructure_backup (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    backup_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    backup_name VARCHAR(255) NOT NULL,
    backup_type VARCHAR(50) NOT NULL,
    backup_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    backup_method VARCHAR(50) NOT NULL,
    backup_size_bytes BIGINT,
    compressed_size_bytes BIGINT,
    compression_ratio DECIMAL(5, 4),
    backup_location VARCHAR(1000) NOT NULL,
    storage_class VARCHAR(50),
    encryption_enabled BOOLEAN DEFAULT TRUE,
    encryption_key_id VARCHAR(255),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    retention_days INTEGER NOT NULL DEFAULT 30,
    expiry_date DATE NOT NULL,
    is_automated BOOLEAN DEFAULT TRUE,
    backup_schedule_id VARCHAR(100),
    verification_status VARCHAR(20),
    verified_at TIMESTAMP,
    restore_tested BOOLEAN DEFAULT FALSE,
    last_restore_test_date DATE,
    checksum VARCHAR(64),
    error_message TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructure_backup FOREIGN KEY (resource_id) REFERENCES infrastructure_resource(resource_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS infrastructure_incident (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id VARCHAR(100) UNIQUE NOT NULL,
    incident_title VARCHAR(255) NOT NULL,
    incident_description TEXT NOT NULL,
    incident_type VARCHAR(50) NOT NULL,
    incident_severity VARCHAR(20) NOT NULL,
    incident_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    affected_resources TEXT[] NOT NULL,
    affected_services TEXT[],
    impact_scope VARCHAR(50) NOT NULL,
    user_impact VARCHAR(100),
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detection_method VARCHAR(100),
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    investigation_started_at TIMESTAMP,
    root_cause TEXT,
    resolution TEXT,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_time_minutes INTEGER,
    post_mortem_required BOOLEAN DEFAULT FALSE,
    post_mortem_url VARCHAR(1000),
    communication_sent BOOLEAN DEFAULT FALSE,
    communication_channels TEXT[],
    lessons_learned TEXT,
    action_items JSONB,
    related_incidents TEXT[],
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS infrastructure_maintenance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    maintenance_id VARCHAR(100) UNIQUE NOT NULL,
    maintenance_name VARCHAR(255) NOT NULL,
    maintenance_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    affected_resources TEXT[] NOT NULL,
    maintenance_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_start TIMESTAMP NOT NULL,
    scheduled_end TIMESTAMP NOT NULL,
    actual_start TIMESTAMP,
    actual_end TIMESTAMP,
    duration_minutes INTEGER,
    maintenance_window VARCHAR(100),
    impact_level VARCHAR(20) NOT NULL,
    downtime_required BOOLEAN DEFAULT FALSE,
    estimated_downtime_minutes INTEGER,
    actual_downtime_minutes INTEGER,
    approval_required BOOLEAN DEFAULT TRUE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    notification_sent BOOLEAN DEFAULT FALSE,
    notification_recipients TEXT[],
    rollback_plan TEXT,
    pre_maintenance_checklist JSONB,
    post_maintenance_checklist JSONB,
    success BOOLEAN DEFAULT TRUE,
    issues_encountered TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS infrastructure_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(100),
    config_name VARCHAR(255) NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    config_value JSONB NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    is_sensitive BOOLEAN DEFAULT FALSE,
    validation_rules JSONB,
    change_approval_required BOOLEAN DEFAULT FALSE,
    last_modified_by VARCHAR(100) NOT NULL,
    change_reason VARCHAR(255),
    previous_value JSONB,
    applied_at TIMESTAMP,
    rollback_possible BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructure_configuration FOREIGN KEY (resource_id) REFERENCES infrastructure_resource(resource_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS infrastructure_compliance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    compliance_id VARCHAR(100) UNIQUE NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    compliance_framework VARCHAR(100) NOT NULL,
    compliance_rule VARCHAR(255) NOT NULL,
    compliance_status VARCHAR(20) NOT NULL DEFAULT 'COMPLIANT',
    last_check_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_check_date TIMESTAMP,
    check_frequency VARCHAR(20) DEFAULT 'DAILY',
    violation_details TEXT,
    severity VARCHAR(20),
    remediation_steps TEXT,
    remediation_deadline DATE,
    remediation_status VARCHAR(20),
    remediated_by VARCHAR(100),
    remediated_at TIMESTAMP,
    exception_granted BOOLEAN DEFAULT FALSE,
    exception_reason TEXT,
    exception_expiry DATE,
    evidence_url VARCHAR(1000),
    auditor_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_infrastructure_compliance FOREIGN KEY (resource_id) REFERENCES infrastructure_resource(resource_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS infrastructure_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_resources INTEGER DEFAULT 0,
    by_resource_type JSONB,
    by_provider JSONB,
    by_region JSONB,
    by_environment JSONB,
    total_deployments INTEGER DEFAULT 0,
    successful_deployments INTEGER DEFAULT 0,
    failed_deployments INTEGER DEFAULT 0,
    deployment_success_rate DECIMAL(5, 4),
    avg_deployment_time_minutes DECIMAL(10, 2),
    total_incidents INTEGER DEFAULT 0,
    avg_resolution_time_minutes DECIMAL(10, 2),
    uptime_percentage DECIMAL(5, 4),
    total_cost DECIMAL(18, 2) DEFAULT 0,
    cost_by_category JSONB,
    capacity_utilization JSONB,
    compliance_score DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS infrastructure_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_infrastructure_resources INTEGER DEFAULT 0,
    active_resources INTEGER DEFAULT 0,
    total_deployments INTEGER DEFAULT 0,
    deployment_success_rate DECIMAL(5, 4),
    total_incidents INTEGER DEFAULT 0,
    critical_incidents INTEGER DEFAULT 0,
    avg_incident_resolution_hours DECIMAL(10, 2),
    infrastructure_uptime DECIMAL(5, 4),
    total_infrastructure_cost DECIMAL(18, 2) DEFAULT 0,
    by_provider JSONB,
    by_resource_category JSONB,
    capacity_trends JSONB,
    cost_optimization_savings DECIMAL(18, 2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_infrastructure_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_infrastructure_resource_type ON infrastructure_resource(resource_type);
CREATE INDEX idx_infrastructure_resource_category ON infrastructure_resource(resource_category);
CREATE INDEX idx_infrastructure_resource_provider ON infrastructure_resource(provider);
CREATE INDEX idx_infrastructure_resource_region ON infrastructure_resource(region);
CREATE INDEX idx_infrastructure_resource_status ON infrastructure_resource(resource_status);
CREATE INDEX idx_infrastructure_resource_environment ON infrastructure_resource(environment);
CREATE INDEX idx_infrastructure_resource_owner ON infrastructure_resource(owner_team);

CREATE INDEX idx_infrastructure_deployment_environment ON infrastructure_deployment(environment);
CREATE INDEX idx_infrastructure_deployment_status ON infrastructure_deployment(deployment_status);
CREATE INDEX idx_infrastructure_deployment_started ON infrastructure_deployment(started_at DESC);
CREATE INDEX idx_infrastructure_deployment_deployed_by ON infrastructure_deployment(deployed_by);

CREATE INDEX idx_infrastructure_metric_resource ON infrastructure_metric(resource_id);
CREATE INDEX idx_infrastructure_metric_name ON infrastructure_metric(metric_name);
CREATE INDEX idx_infrastructure_metric_timestamp ON infrastructure_metric(timestamp DESC);
CREATE INDEX idx_infrastructure_metric_breached ON infrastructure_metric(threshold_breached) WHERE threshold_breached = true;

CREATE INDEX idx_infrastructure_alert_resource ON infrastructure_alert(resource_id);
CREATE INDEX idx_infrastructure_alert_type ON infrastructure_alert(alert_type);
CREATE INDEX idx_infrastructure_alert_severity ON infrastructure_alert(alert_severity);
CREATE INDEX idx_infrastructure_alert_status ON infrastructure_alert(alert_status);
CREATE INDEX idx_infrastructure_alert_triggered ON infrastructure_alert(triggered_at DESC);

CREATE INDEX idx_infrastructure_capacity_resource ON infrastructure_capacity(resource_id);
CREATE INDEX idx_infrastructure_capacity_type ON infrastructure_capacity(capacity_type);
CREATE INDEX idx_infrastructure_capacity_breached ON infrastructure_capacity(threshold_breached) WHERE threshold_breached = true;
CREATE INDEX idx_infrastructure_capacity_timestamp ON infrastructure_capacity(measurement_timestamp DESC);

CREATE INDEX idx_infrastructure_cost_resource ON infrastructure_cost(resource_id);
CREATE INDEX idx_infrastructure_cost_period ON infrastructure_cost(cost_period_end DESC);
CREATE INDEX idx_infrastructure_cost_type ON infrastructure_cost(cost_type);

CREATE INDEX idx_infrastructure_backup_resource ON infrastructure_backup(resource_id);
CREATE INDEX idx_infrastructure_backup_status ON infrastructure_backup(backup_status);
CREATE INDEX idx_infrastructure_backup_started ON infrastructure_backup(started_at DESC);
CREATE INDEX idx_infrastructure_backup_expiry ON infrastructure_backup(expiry_date);

CREATE INDEX idx_infrastructure_incident_severity ON infrastructure_incident(incident_severity);
CREATE INDEX idx_infrastructure_incident_status ON infrastructure_incident(incident_status);
CREATE INDEX idx_infrastructure_incident_detected ON infrastructure_incident(detected_at DESC);

CREATE INDEX idx_infrastructure_maintenance_status ON infrastructure_maintenance(maintenance_status);
CREATE INDEX idx_infrastructure_maintenance_scheduled ON infrastructure_maintenance(scheduled_start);

CREATE INDEX idx_infrastructure_configuration_resource ON infrastructure_configuration(resource_id);
CREATE INDEX idx_infrastructure_configuration_type ON infrastructure_configuration(config_type);
CREATE INDEX idx_infrastructure_configuration_active ON infrastructure_configuration(is_active) WHERE is_active = true;

CREATE INDEX idx_infrastructure_compliance_resource ON infrastructure_compliance(resource_id);
CREATE INDEX idx_infrastructure_compliance_framework ON infrastructure_compliance(compliance_framework);
CREATE INDEX idx_infrastructure_compliance_status ON infrastructure_compliance(compliance_status);

CREATE INDEX idx_infrastructure_analytics_period ON infrastructure_analytics(period_end DESC);
CREATE INDEX idx_infrastructure_statistics_period ON infrastructure_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_infrastructure_resource_updated_at BEFORE UPDATE ON infrastructure_resource
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_infrastructure_deployment_updated_at BEFORE UPDATE ON infrastructure_deployment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_infrastructure_alert_updated_at BEFORE UPDATE ON infrastructure_alert
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_infrastructure_backup_updated_at BEFORE UPDATE ON infrastructure_backup
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_infrastructure_incident_updated_at BEFORE UPDATE ON infrastructure_incident
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_infrastructure_maintenance_updated_at BEFORE UPDATE ON infrastructure_maintenance
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_infrastructure_configuration_updated_at BEFORE UPDATE ON infrastructure_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_infrastructure_compliance_updated_at BEFORE UPDATE ON infrastructure_compliance
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();