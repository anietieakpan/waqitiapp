-- Configuration Service Initial Schema
-- Created: 2025-09-27
-- Description: Centralized configuration management and versioning schema

CREATE TABLE IF NOT EXISTS configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id VARCHAR(100) UNIQUE NOT NULL,
    config_key VARCHAR(255) NOT NULL,
    config_value JSONB NOT NULL,
    value_type VARCHAR(50) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    service_name VARCHAR(100),
    is_encrypted BOOLEAN DEFAULT FALSE,
    is_sensitive BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    requires_restart BOOLEAN DEFAULT FALSE,
    validation_rules JSONB,
    default_value JSONB,
    min_value JSONB,
    max_value JSONB,
    allowed_values JSONB,
    tags TEXT[],
    version INTEGER DEFAULT 1,
    created_by VARCHAR(100) NOT NULL,
    last_modified_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_config_key_env UNIQUE (config_key, environment, service_name)
);

CREATE TABLE IF NOT EXISTS configuration_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    history_id VARCHAR(100) UNIQUE NOT NULL,
    config_id VARCHAR(100) NOT NULL,
    previous_value JSONB,
    new_value JSONB NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    change_reason TEXT,
    changed_by VARCHAR(100) NOT NULL,
    ip_address VARCHAR(50),
    user_agent TEXT,
    rollback_id VARCHAR(100),
    is_rollback BOOLEAN DEFAULT FALSE,
    change_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_configuration_history FOREIGN KEY (config_id) REFERENCES configuration(config_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS configuration_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(100) UNIQUE NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    description TEXT,
    template_schema JSONB NOT NULL,
    default_values JSONB,
    validation_rules JSONB,
    category VARCHAR(100) NOT NULL,
    applicable_services TEXT[],
    is_active BOOLEAN DEFAULT TRUE,
    version INTEGER DEFAULT 1,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_environment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id VARCHAR(100) UNIQUE NOT NULL,
    environment_name VARCHAR(255) NOT NULL,
    environment_type VARCHAR(50) NOT NULL,
    description TEXT,
    display_order INTEGER DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    parent_environment_id VARCHAR(100),
    inheritance_enabled BOOLEAN DEFAULT FALSE,
    configuration_count INTEGER DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_configuration_environment FOREIGN KEY (parent_environment_id) REFERENCES configuration_environment(environment_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS configuration_group (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id VARCHAR(100) UNIQUE NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    group_type VARCHAR(50) NOT NULL,
    description TEXT,
    config_keys TEXT[] NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    deployment_order INTEGER DEFAULT 100,
    dependencies TEXT[],
    validation_script TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_deployment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deployment_id VARCHAR(100) UNIQUE NOT NULL,
    deployment_name VARCHAR(255) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    service_name VARCHAR(100),
    config_ids TEXT[] NOT NULL,
    deployment_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds INTEGER,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    rollback_on_failure BOOLEAN DEFAULT TRUE,
    rollback_deployment_id VARCHAR(100),
    error_message TEXT,
    deployment_notes TEXT,
    deployed_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_validation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    validation_id VARCHAR(100) UNIQUE NOT NULL,
    config_id VARCHAR(100) NOT NULL,
    validation_type VARCHAR(50) NOT NULL,
    validation_rule JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    validated_value JSONB,
    validated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validated_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_configuration_validation FOREIGN KEY (config_id) REFERENCES configuration(config_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS configuration_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    log_id VARCHAR(100) UNIQUE NOT NULL,
    config_id VARCHAR(100) NOT NULL,
    access_type VARCHAR(50) NOT NULL,
    accessed_by VARCHAR(100) NOT NULL,
    service_name VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent TEXT,
    request_headers JSONB,
    response_status INTEGER,
    access_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_time_ms INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_configuration_access_log FOREIGN KEY (config_id) REFERENCES configuration(config_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS configuration_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cache_key VARCHAR(255) UNIQUE NOT NULL,
    environment VARCHAR(20) NOT NULL,
    service_name VARCHAR(100),
    cached_configs JSONB NOT NULL,
    config_version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_accessed_at TIMESTAMP,
    access_count INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS configuration_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    config_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    notification_method VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_configuration_notification FOREIGN KEY (config_id) REFERENCES configuration(config_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS configuration_schema (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schema_id VARCHAR(100) UNIQUE NOT NULL,
    schema_name VARCHAR(255) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    json_schema JSONB NOT NULL,
    applicable_categories TEXT[],
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_feature_flag (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_id VARCHAR(100) UNIQUE NOT NULL,
    flag_name VARCHAR(255) NOT NULL,
    flag_type VARCHAR(50) NOT NULL,
    is_enabled BOOLEAN DEFAULT FALSE,
    environment VARCHAR(20) NOT NULL,
    service_name VARCHAR(100),
    rollout_percentage DECIMAL(5, 2) DEFAULT 0,
    target_users TEXT[],
    target_groups TEXT[],
    conditions JSONB,
    expires_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_backup (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    backup_id VARCHAR(100) UNIQUE NOT NULL,
    backup_name VARCHAR(255) NOT NULL,
    backup_type VARCHAR(50) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    service_name VARCHAR(100),
    configuration_data JSONB NOT NULL,
    backup_size_bytes INTEGER NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_import_export (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id VARCHAR(100) UNIQUE NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    source_environment VARCHAR(20),
    target_environment VARCHAR(20),
    service_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    file_path VARCHAR(1000),
    total_configs INTEGER DEFAULT 0,
    processed_configs INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    error_log JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    performed_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS configuration_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_configurations INTEGER DEFAULT 0,
    active_configurations INTEGER DEFAULT 0,
    by_environment JSONB,
    by_service JSONB,
    by_category JSONB,
    total_changes INTEGER DEFAULT 0,
    total_deployments INTEGER DEFAULT 0,
    deployment_success_rate DECIMAL(5, 4),
    most_changed_configs JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_config_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_configuration_key ON configuration(config_key);
CREATE INDEX idx_configuration_category ON configuration(category);
CREATE INDEX idx_configuration_environment ON configuration(environment);
CREATE INDEX idx_configuration_service ON configuration(service_name);
CREATE INDEX idx_configuration_active ON configuration(is_active) WHERE is_active = true;
CREATE INDEX idx_configuration_sensitive ON configuration(is_sensitive) WHERE is_sensitive = true;
CREATE INDEX idx_configuration_tags ON configuration USING gin(tags);

CREATE INDEX idx_configuration_history_config ON configuration_history(config_id);
CREATE INDEX idx_configuration_history_timestamp ON configuration_history(change_timestamp DESC);
CREATE INDEX idx_configuration_history_changed_by ON configuration_history(changed_by);
CREATE INDEX idx_configuration_history_rollback ON configuration_history(is_rollback) WHERE is_rollback = true;

CREATE INDEX idx_configuration_template_type ON configuration_template(template_type);
CREATE INDEX idx_configuration_template_category ON configuration_template(category);
CREATE INDEX idx_configuration_template_active ON configuration_template(is_active) WHERE is_active = true;

CREATE INDEX idx_configuration_environment_type ON configuration_environment(environment_type);
CREATE INDEX idx_configuration_environment_parent ON configuration_environment(parent_environment_id);
CREATE INDEX idx_configuration_environment_active ON configuration_environment(is_active) WHERE is_active = true;

CREATE INDEX idx_configuration_group_type ON configuration_group(group_type);
CREATE INDEX idx_configuration_group_active ON configuration_group(is_active) WHERE is_active = true;

CREATE INDEX idx_configuration_deployment_environment ON configuration_deployment(environment);
CREATE INDEX idx_configuration_deployment_service ON configuration_deployment(service_name);
CREATE INDEX idx_configuration_deployment_status ON configuration_deployment(status);
CREATE INDEX idx_configuration_deployment_scheduled ON configuration_deployment(scheduled_at);

CREATE INDEX idx_configuration_validation_config ON configuration_validation(config_id);
CREATE INDEX idx_configuration_validation_status ON configuration_validation(status);
CREATE INDEX idx_configuration_validation_timestamp ON configuration_validation(validated_at DESC);

CREATE INDEX idx_configuration_access_log_config ON configuration_access_log(config_id);
CREATE INDEX idx_configuration_access_log_service ON configuration_access_log(service_name);
CREATE INDEX idx_configuration_access_log_timestamp ON configuration_access_log(access_timestamp DESC);

CREATE INDEX idx_configuration_cache_key ON configuration_cache(cache_key);
CREATE INDEX idx_configuration_cache_environment ON configuration_cache(environment);
CREATE INDEX idx_configuration_cache_expires ON configuration_cache(expires_at);

CREATE INDEX idx_configuration_notification_config ON configuration_notification(config_id);
CREATE INDEX idx_configuration_notification_status ON configuration_notification(status);
CREATE INDEX idx_configuration_notification_sent ON configuration_notification(sent_at);

CREATE INDEX idx_configuration_schema_name ON configuration_schema(schema_name);
CREATE INDEX idx_configuration_schema_active ON configuration_schema(is_active) WHERE is_active = true;

CREATE INDEX idx_configuration_feature_flag_name ON configuration_feature_flag(flag_name);
CREATE INDEX idx_configuration_feature_flag_environment ON configuration_feature_flag(environment);
CREATE INDEX idx_configuration_feature_flag_enabled ON configuration_feature_flag(is_enabled) WHERE is_enabled = true;

CREATE INDEX idx_configuration_backup_environment ON configuration_backup(environment);
CREATE INDEX idx_configuration_backup_service ON configuration_backup(service_name);
CREATE INDEX idx_configuration_backup_created ON configuration_backup(created_at DESC);

CREATE INDEX idx_configuration_import_export_type ON configuration_import_export(operation_type);
CREATE INDEX idx_configuration_import_export_status ON configuration_import_export(status);
CREATE INDEX idx_configuration_import_export_environment ON configuration_import_export(target_environment);

CREATE INDEX idx_configuration_statistics_period ON configuration_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_configuration_updated_at BEFORE UPDATE ON configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_configuration_template_updated_at BEFORE UPDATE ON configuration_template
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_configuration_environment_updated_at BEFORE UPDATE ON configuration_environment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_configuration_group_updated_at BEFORE UPDATE ON configuration_group
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_configuration_deployment_updated_at BEFORE UPDATE ON configuration_deployment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_configuration_schema_updated_at BEFORE UPDATE ON configuration_schema
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_configuration_feature_flag_updated_at BEFORE UPDATE ON configuration_feature_flag
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();