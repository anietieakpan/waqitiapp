-- V2: Align schema with JPA entities
-- This migration aligns the database schema with the actual entity structure
-- Drops the old complex schema and creates a simplified, entity-matched schema

-- Drop old tables if they exist (from V1 migration)
DROP TABLE IF EXISTS configuration_statistics CASCADE;
DROP TABLE IF EXISTS configuration_import_export CASCADE;
DROP TABLE IF EXISTS configuration_backup CASCADE;
DROP TABLE IF EXISTS configuration_feature_flag CASCADE;
DROP TABLE IF EXISTS configuration_schema CASCADE;
DROP TABLE IF EXISTS configuration_notification CASCADE;
DROP TABLE IF EXISTS configuration_cache CASCADE;
DROP TABLE IF EXISTS configuration_access_log CASCADE;
DROP TABLE IF EXISTS configuration_validation CASCADE;
DROP TABLE IF EXISTS configuration_deployment CASCADE;
DROP TABLE IF EXISTS configuration_group CASCADE;
DROP TABLE IF EXISTS configuration_environment CASCADE;
DROP TABLE IF EXISTS configuration_template CASCADE;
DROP TABLE IF EXISTS configuration_history CASCADE;
DROP TABLE IF EXISTS configuration CASCADE;

-- Create configurations table (matches Configuration.java entity exactly)
CREATE TABLE IF NOT EXISTS configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(500) NOT NULL UNIQUE,
    value TEXT,
    service VARCHAR(100),
    environment VARCHAR(50),
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    sensitive BOOLEAN NOT NULL DEFAULT false,
    encrypted BOOLEAN NOT NULL DEFAULT false,
    data_type VARCHAR(50),
    default_value TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0
);

-- Create feature_flags table (matches FeatureFlag.java entity exactly)
CREATE TABLE IF NOT EXISTS feature_flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL UNIQUE,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT false,
    environment VARCHAR(50),
    rules TEXT,
    target_users TEXT,
    target_groups TEXT,
    rollout_percentage INTEGER DEFAULT 0,
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0
);

-- Create config_audit table (matches ConfigAudit.java entity exactly)
CREATE TABLE IF NOT EXISTS config_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(500) NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    user_id VARCHAR(100) NOT NULL,
    user_name VARCHAR(100),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    reason TEXT,
    success BOOLEAN NOT NULL DEFAULT true,
    error_message TEXT
);

-- Create service_configs table (matches ServiceConfig.java entity exactly)
CREATE TABLE IF NOT EXISTS service_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    config_data TEXT,
    secrets_data TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT unique_service_env UNIQUE (service_name, environment)
);

-- Indexes for configurations table (matches @Index annotations in Configuration.java)
CREATE INDEX idx_config_key ON configurations(key);
CREATE INDEX idx_config_service ON configurations(service);
CREATE INDEX idx_config_environment ON configurations(environment);
CREATE INDEX idx_config_active ON configurations(active) WHERE active = true;
CREATE INDEX idx_config_last_modified ON configurations(last_modified);
CREATE INDEX idx_config_service_env ON configurations(service, environment);
CREATE INDEX idx_config_sensitive ON configurations(sensitive) WHERE sensitive = true;
CREATE INDEX idx_config_encrypted ON configurations(encrypted) WHERE encrypted = true;

-- Indexes for feature_flags table (matches @Index annotations in FeatureFlag.java)
CREATE INDEX idx_feature_name ON feature_flags(name);
CREATE INDEX idx_feature_enabled ON feature_flags(enabled) WHERE enabled = true;
CREATE INDEX idx_feature_environment ON feature_flags(environment);
CREATE INDEX idx_feature_flag_dates ON feature_flags(start_date, end_date);

-- Indexes for config_audit table (matches @Index annotations in ConfigAudit.java)
CREATE INDEX idx_audit_config_key ON config_audit(config_key);
CREATE INDEX idx_audit_timestamp ON config_audit(timestamp DESC);
CREATE INDEX idx_audit_user ON config_audit(user_id);
CREATE INDEX idx_audit_action ON config_audit(action);

-- Indexes for service_configs table (matches @Index annotations in ServiceConfig.java)
CREATE INDEX idx_service_name ON service_configs(service_name);
CREATE INDEX idx_service_environment ON service_configs(environment);

-- Update timestamp trigger function
CREATE OR REPLACE FUNCTION update_last_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_modified = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers to tables with last_modified column
CREATE TRIGGER update_configurations_last_modified
    BEFORE UPDATE ON configurations
    FOR EACH ROW EXECUTE FUNCTION update_last_modified_column();

CREATE TRIGGER update_feature_flags_last_modified
    BEFORE UPDATE ON feature_flags
    FOR EACH ROW EXECUTE FUNCTION update_last_modified_column();

CREATE TRIGGER update_service_configs_last_modified
    BEFORE UPDATE ON service_configs
    FOR EACH ROW EXECUTE FUNCTION update_last_modified_column();

-- Insert default configurations for development environment
INSERT INTO configurations (key, value, service, environment, description, data_type, active, sensitive, created_by)
VALUES
    ('app.version', '1.0.0', 'config-service', 'dev', 'Application version', 'STRING', true, false, 'system'),
    ('config.cache.ttl', '300', 'config-service', 'dev', 'Configuration cache TTL in seconds', 'INTEGER', true, false, 'system'),
    ('config.encryption.enabled', 'true', 'config-service', 'dev', 'Enable configuration value encryption', 'BOOLEAN', true, false, 'system'),
    ('vault.enabled', 'true', 'config-service', 'dev', 'Enable Vault integration', 'BOOLEAN', true, false, 'system'),
    ('config.refresh.enabled', 'true', 'config-service', 'dev', 'Enable configuration refresh via Spring Cloud Bus', 'BOOLEAN', true, false, 'system')
ON CONFLICT (key) DO NOTHING;

-- Insert default feature flags
INSERT INTO feature_flags (name, description, enabled, environment, rollout_percentage, created_by)
VALUES
    ('config-refresh-broadcast', 'Enable broadcasting config refresh events via Kafka', true, 'dev', 100, 'system'),
    ('config-audit-logging', 'Enable detailed audit logging for configuration changes', true, 'dev', 100, 'system'),
    ('config-validation-strict', 'Enable strict validation for configuration values', false, 'dev', 0, 'system'),
    ('config-encryption-required', 'Require encryption for all sensitive configuration values', true, 'dev', 100, 'system')
ON CONFLICT (name) DO NOTHING;

-- Add table comments for documentation
COMMENT ON TABLE configurations IS 'Centralized configuration storage for all microservices';
COMMENT ON TABLE feature_flags IS 'Feature flag management with advanced targeting capabilities';
COMMENT ON TABLE config_audit IS 'Audit trail for all configuration changes';
COMMENT ON TABLE service_configs IS 'Service-level configuration snapshots';

COMMENT ON COLUMN configurations.key IS 'Unique configuration key in format: service.category.name';
COMMENT ON COLUMN configurations.encrypted IS 'Indicates if the value is encrypted using AES-256-GCM';
COMMENT ON COLUMN configurations.sensitive IS 'Marks configuration as containing sensitive data (PII, credentials, etc.)';
COMMENT ON COLUMN configurations.version IS 'Optimistic locking version for concurrent update prevention';

COMMENT ON COLUMN feature_flags.rollout_percentage IS 'Percentage of users to enable this feature (0-100)';
COMMENT ON COLUMN feature_flags.rules IS 'JSON-encoded custom rules for feature flag evaluation';

COMMENT ON COLUMN config_audit.user_id IS 'User ID who performed the configuration change';
COMMENT ON COLUMN config_audit.success IS 'Indicates if the configuration change was successful';
