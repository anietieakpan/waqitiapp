-- Database migration for core event tracking and consumer support
-- Version: V001
-- Description: Create core event tracking tables for Kafka consumer infrastructure

-- Create schema for event tracking
CREATE SCHEMA IF NOT EXISTS event_tracking;

-- Core event log table for all Kafka events
CREATE TABLE event_tracking.kafka_event_log (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    topic_name VARCHAR(100) NOT NULL,
    partition_id INTEGER NOT NULL,
    offset_value BIGINT NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    processing_duration_ms INTEGER,
    status VARCHAR(20) DEFAULT 'PROCESSED' CHECK (status IN ('PROCESSED', 'FAILED', 'RETRIED')),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_kafka_event_log_event_type ON event_tracking.kafka_event_log(event_type);
CREATE INDEX idx_kafka_event_log_topic_name ON event_tracking.kafka_event_log(topic_name);
CREATE INDEX idx_kafka_event_log_processed_at ON event_tracking.kafka_event_log(processed_at);
CREATE INDEX idx_kafka_event_log_status ON event_tracking.kafka_event_log(status);
CREATE INDEX idx_kafka_event_log_consumer_group ON event_tracking.kafka_event_log(consumer_group);
CREATE INDEX idx_kafka_event_log_payload_gin ON event_tracking.kafka_event_log USING GIN (payload);

-- Consumer performance metrics table
CREATE TABLE event_tracking.consumer_metrics (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    topic_name VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    events_processed_count BIGINT DEFAULT 0,
    events_failed_count BIGINT DEFAULT 0,
    total_processing_time_ms BIGINT DEFAULT 0,
    avg_processing_time_ms DECIMAL(10,2) DEFAULT 0,
    min_processing_time_ms INTEGER DEFAULT 0,
    max_processing_time_ms INTEGER DEFAULT 0,
    last_processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(consumer_name, topic_name, consumer_group)
);

CREATE INDEX idx_consumer_metrics_consumer_name ON event_tracking.consumer_metrics(consumer_name);
CREATE INDEX idx_consumer_metrics_topic_name ON event_tracking.consumer_metrics(topic_name);

-- Dead Letter Queue tracking table
CREATE TABLE event_tracking.dlq_events (
    id BIGSERIAL PRIMARY KEY,
    original_event_id VARCHAR(255) NOT NULL,
    dlq_event_id VARCHAR(255) NOT NULL UNIQUE,
    source_topic VARCHAR(100) NOT NULL,
    dlq_topic VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    dlq_reason VARCHAR(255) NOT NULL,
    original_payload JSONB NOT NULL,
    error_details JSONB,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    dlq_status VARCHAR(20) DEFAULT 'PENDING' CHECK (dlq_status IN ('PENDING', 'RETRYING', 'RESOLVED', 'ABANDONED')),
    resolution_strategy VARCHAR(50),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dlq_events_source_topic ON event_tracking.dlq_events(source_topic);
CREATE INDEX idx_dlq_events_dlq_status ON event_tracking.dlq_events(dlq_status);
CREATE INDEX idx_dlq_events_next_retry_at ON event_tracking.dlq_events(next_retry_at);
CREATE INDEX idx_dlq_events_created_at ON event_tracking.dlq_events(created_at);

-- Event processing errors table
CREATE TABLE event_tracking.processing_errors (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    topic_name VARCHAR(100) NOT NULL,
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    event_payload JSONB,
    processing_context JSONB,
    error_count INTEGER DEFAULT 1,
    first_occurrence TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_occurrence TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolution_status VARCHAR(20) DEFAULT 'UNRESOLVED' CHECK (resolution_status IN ('UNRESOLVED', 'INVESTIGATING', 'RESOLVED', 'IGNORED')),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processing_errors_consumer_name ON event_tracking.processing_errors(consumer_name);
CREATE INDEX idx_processing_errors_error_type ON event_tracking.processing_errors(error_type);
CREATE INDEX idx_processing_errors_resolution_status ON event_tracking.processing_errors(resolution_status);
CREATE INDEX idx_processing_errors_last_occurrence ON event_tracking.processing_errors(last_occurrence);

-- Event correlation table for tracking related events
CREATE TABLE event_tracking.event_correlation (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(255) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic_name VARCHAR(100) NOT NULL,
    correlation_type VARCHAR(50) NOT NULL, -- 'TRANSACTION', 'USER_SESSION', 'SAGA', 'BUSINESS_FLOW'
    correlation_sequence INTEGER,
    parent_event_id VARCHAR(255),
    business_key VARCHAR(255), -- user_id, transaction_id, etc.
    correlation_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_correlation_correlation_id ON event_tracking.event_correlation(correlation_id);
CREATE INDEX idx_event_correlation_event_id ON event_tracking.event_correlation(event_id);
CREATE INDEX idx_event_correlation_business_key ON event_tracking.event_correlation(business_key);
CREATE INDEX idx_event_correlation_type ON event_tracking.event_correlation(correlation_type);

-- Consumer health check table
CREATE TABLE event_tracking.consumer_health (
    id BIGSERIAL PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    consumer_instance_id VARCHAR(255) NOT NULL,
    health_status VARCHAR(20) DEFAULT 'HEALTHY' CHECK (health_status IN ('HEALTHY', 'DEGRADED', 'UNHEALTHY', 'STOPPED')),
    last_heartbeat TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    lag_milliseconds BIGINT DEFAULT 0,
    processing_rate DECIMAL(10,2) DEFAULT 0, -- events per second
    error_rate DECIMAL(5,2) DEFAULT 0, -- percentage
    memory_usage_mb INTEGER,
    cpu_usage_percent DECIMAL(5,2),
    active_threads INTEGER,
    health_details JSONB,
    alert_threshold_breached BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(consumer_name, consumer_instance_id)
);

CREATE INDEX idx_consumer_health_consumer_name ON event_tracking.consumer_health(consumer_name);
CREATE INDEX idx_consumer_health_status ON event_tracking.consumer_health(health_status);
CREATE INDEX idx_consumer_health_last_heartbeat ON event_tracking.consumer_health(last_heartbeat);

-- Event schema registry for payload validation
CREATE TABLE event_tracking.event_schemas (
    id BIGSERIAL PRIMARY KEY,
    schema_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    json_schema JSONB NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    backward_compatible BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deprecated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(schema_name, schema_version)
);

CREATE INDEX idx_event_schemas_event_type ON event_tracking.event_schemas(event_type);
CREATE INDEX idx_event_schemas_is_active ON event_tracking.event_schemas(is_active);

-- Audit trail table for all consumer operations
CREATE TABLE event_tracking.consumer_audit (
    id BIGSERIAL PRIMARY KEY,
    audit_id VARCHAR(255) NOT NULL UNIQUE,
    consumer_name VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL, -- 'START', 'STOP', 'RESTART', 'CONFIG_CHANGE', 'ERROR_RECOVERY'
    operation_details JSONB,
    performed_by VARCHAR(100), -- system or user
    client_ip INET,
    user_agent TEXT,
    operation_result VARCHAR(20) DEFAULT 'SUCCESS' CHECK (operation_result IN ('SUCCESS', 'FAILED', 'PARTIAL')),
    error_message TEXT,
    operation_duration_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consumer_audit_consumer_name ON event_tracking.consumer_audit(consumer_name);
CREATE INDEX idx_consumer_audit_operation_type ON event_tracking.consumer_audit(operation_type);
CREATE INDEX idx_consumer_audit_created_at ON event_tracking.consumer_audit(created_at);

-- Create functions for automatic timestamp updates
CREATE OR REPLACE FUNCTION event_tracking.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply update triggers to relevant tables
CREATE TRIGGER update_kafka_event_log_updated_at BEFORE UPDATE ON event_tracking.kafka_event_log
    FOR EACH ROW EXECUTE FUNCTION event_tracking.update_updated_at_column();

CREATE TRIGGER update_consumer_metrics_updated_at BEFORE UPDATE ON event_tracking.consumer_metrics
    FOR EACH ROW EXECUTE FUNCTION event_tracking.update_updated_at_column();

CREATE TRIGGER update_dlq_events_updated_at BEFORE UPDATE ON event_tracking.dlq_events
    FOR EACH ROW EXECUTE FUNCTION event_tracking.update_updated_at_column();

CREATE TRIGGER update_processing_errors_updated_at BEFORE UPDATE ON event_tracking.processing_errors
    FOR EACH ROW EXECUTE FUNCTION event_tracking.update_updated_at_column();

CREATE TRIGGER update_consumer_health_updated_at BEFORE UPDATE ON event_tracking.consumer_health
    FOR EACH ROW EXECUTE FUNCTION event_tracking.update_updated_at_column();

-- Create partitioning for high-volume tables (kafka_event_log)
-- Partition by month to manage large volumes efficiently
CREATE TABLE event_tracking.kafka_event_log_template (LIKE event_tracking.kafka_event_log INCLUDING ALL);

-- Function to create monthly partitions
CREATE OR REPLACE FUNCTION event_tracking.create_monthly_partition(table_date DATE)
RETURNS void AS $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
BEGIN
    start_date := date_trunc('month', table_date)::DATE;
    end_date := (date_trunc('month', table_date) + INTERVAL '1 month')::DATE;
    partition_name := 'kafka_event_log_' || to_char(start_date, 'YYYY_MM');
    
    EXECUTE format('CREATE TABLE IF NOT EXISTS event_tracking.%I PARTITION OF event_tracking.kafka_event_log 
                    FOR VALUES FROM (%L) TO (%L)', 
                   partition_name, start_date, end_date);
END;
$$ LANGUAGE plpgsql;

-- Create initial partitions for current and next 3 months
SELECT event_tracking.create_monthly_partition(CURRENT_DATE);
SELECT event_tracking.create_monthly_partition(CURRENT_DATE + INTERVAL '1 month');
SELECT event_tracking.create_monthly_partition(CURRENT_DATE + INTERVAL '2 months');

-- Create materialized view for consumer performance dashboard
CREATE MATERIALIZED VIEW event_tracking.consumer_performance_summary AS
SELECT 
    consumer_name,
    COUNT(DISTINCT topic_name) as topics_handled,
    SUM(events_processed_count) as total_events_processed,
    SUM(events_failed_count) as total_events_failed,
    ROUND(AVG(avg_processing_time_ms), 2) as avg_processing_time_ms,
    ROUND((SUM(events_failed_count)::DECIMAL / NULLIF(SUM(events_processed_count + events_failed_count), 0)) * 100, 2) as error_rate_percent,
    MAX(last_processed_at) as last_activity
FROM event_tracking.consumer_metrics
GROUP BY consumer_name
ORDER BY total_events_processed DESC;

CREATE UNIQUE INDEX idx_consumer_performance_summary_consumer ON event_tracking.consumer_performance_summary(consumer_name);

-- Refresh function for the materialized view
CREATE OR REPLACE FUNCTION event_tracking.refresh_performance_summary()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY event_tracking.consumer_performance_summary;
END;
$$ LANGUAGE plpgsql;

-- Grant permissions
GRANT USAGE ON SCHEMA event_tracking TO waqiti_app_user;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA event_tracking TO waqiti_app_user;
GRANT SELECT, USAGE ON ALL SEQUENCES IN SCHEMA event_tracking TO waqiti_app_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA event_tracking TO waqiti_app_user;

-- Insert initial consumer registrations
INSERT INTO event_tracking.consumer_metrics (consumer_name, topic_name, consumer_group) VALUES
('CriticalAlertsConsumer', 'critical-alerts', 'critical-alert-processor'),
('CriticalAlertsConsumer', 'critical-system-alerts', 'critical-alert-processor'),
('CriticalAlertsConsumer', 'critical-security-alerts', 'critical-alert-processor'),
('CriticalAlertsConsumer', 'alerts-emergency', 'critical-alert-processor'),
('TransactionControlConsumer', 'transaction-blocks', 'transaction-control-processor'),
('TransactionControlConsumer', 'transaction-unblocks', 'transaction-control-processor'),
('TransactionControlConsumer', 'transaction-control', 'transaction-control-processor'),
('TransactionControlConsumer', 'transaction-delays', 'transaction-control-processor'),
('TransactionControlConsumer', 'transaction-resumes', 'transaction-control-processor'),
('TransactionControlConsumer', 'transaction-monitoring-blocks', 'transaction-control-processor'),
('TransactionControlConsumer', 'transaction-auto-review-blocks', 'transaction-control-processor'),
('ComplianceScreeningConsumer', 'compliance-screening-errors', 'compliance-screening-processor'),
('ComplianceScreeningConsumer', 'sanctions-clearance-notifications', 'compliance-screening-processor'),
('ComplianceScreeningConsumer', 'compliance-screening-completed', 'compliance-screening-processor'),
('ComplianceScreeningConsumer', 'aml-alerts', 'compliance-screening-processor'),
('ComplianceScreeningConsumer', 'regulatory-notifications', 'compliance-screening-processor'),
('ComplianceScreeningConsumer', 'pci-audit-events', 'compliance-screening-processor'),
('ComplianceScreeningConsumer', 'compliance-warnings', 'compliance-screening-processor'),
('ComplianceScreeningConsumer', 'compliance-incidents', 'compliance-screening-processor'),
('MonitoringAlertsConsumer', 'monitoring.alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'monitoring.sla.breaches', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'monitoring.metrics', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'system-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'incident-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'dlq-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'audit-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'anomaly-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'analytics-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'operations-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'real-time-alerts', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'circuit-breaker-metrics', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'service-metrics', 'monitoring-processor'),
('MonitoringAlertsConsumer', 'security-health-metrics', 'monitoring-processor');

COMMENT ON SCHEMA event_tracking IS 'Schema for tracking Kafka event processing and consumer performance';
COMMENT ON TABLE event_tracking.kafka_event_log IS 'Comprehensive log of all processed Kafka events';
COMMENT ON TABLE event_tracking.consumer_metrics IS 'Performance metrics for each Kafka consumer';
COMMENT ON TABLE event_tracking.dlq_events IS 'Dead Letter Queue events requiring manual intervention';
COMMENT ON TABLE event_tracking.processing_errors IS 'Aggregated processing errors for analysis';
COMMENT ON TABLE event_tracking.event_correlation IS 'Event correlation tracking for business flows';
COMMENT ON TABLE event_tracking.consumer_health IS 'Real-time health status of Kafka consumers';
COMMENT ON TABLE event_tracking.event_schemas IS 'Event schema registry for validation';
COMMENT ON TABLE event_tracking.consumer_audit IS 'Audit trail for all consumer operations';