-- ============================================================================
-- DEAD LETTER QUEUE (DLQ) TABLES
-- ============================================================================
-- Comprehensive DLQ infrastructure for failed message handling
--
-- Author: Waqiti Platform Team
-- Version: 1.0.0
-- Date: 2025-10-30
-- ============================================================================

-- DLQ Records Table
CREATE TABLE IF NOT EXISTS dlq_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id VARCHAR(255) NOT NULL,
    original_topic VARCHAR(255) NOT NULL,
    original_key TEXT,
    original_value TEXT NOT NULL,
    original_partition INTEGER,
    original_offset BIGINT,
    message_type VARCHAR(100),
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    -- Error information
    error_message TEXT,
    error_stack_trace TEXT,
    failure_reason TEXT,

    -- Timestamps
    original_timestamp TIMESTAMP,
    dlq_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Status tracking
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER DEFAULT 5,
    next_retry_at TIMESTAMP,
    last_retry_at TIMESTAMP,

    -- Escalation
    escalated_at TIMESTAMP,
    escalated_to VARCHAR(255),
    escalation_reason TEXT,

    -- Resolution
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(255),
    resolution_notes TEXT,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) DEFAULT 'SYSTEM',
    updated_by VARCHAR(255) DEFAULT 'SYSTEM',

    -- Constraints
    CONSTRAINT chk_dlq_status CHECK (status IN (
        'PENDING',
        'RETRY_SCHEDULED',
        'RETRYING',
        'RETRY_SENT',
        'RETRY_FAILED',
        'ESCALATED',
        'MANUALLY_REPROCESSED',
        'PROCESSED',
        'PERMANENTLY_FAILED',
        'IN_QUEUE',
        'QUEUE_SEND_FAILED'
    )),
    CONSTRAINT chk_dlq_priority CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'))
);

-- Indexes for DLQ Records
CREATE INDEX idx_dlq_records_status ON dlq_records(status);
CREATE INDEX idx_dlq_records_topic ON dlq_records(original_topic);
CREATE INDEX idx_dlq_records_message_type ON dlq_records(message_type);
CREATE INDEX idx_dlq_records_priority ON dlq_records(priority);
CREATE INDEX idx_dlq_records_retry_count ON dlq_records(retry_count);
CREATE INDEX idx_dlq_records_next_retry ON dlq_records(next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_dlq_records_created_at ON dlq_records(created_at);
CREATE INDEX idx_dlq_records_dlq_timestamp ON dlq_records(dlq_timestamp);
CREATE INDEX idx_dlq_records_message_id ON dlq_records(message_id);
CREATE INDEX idx_dlq_records_pending_retry ON dlq_records(status, next_retry_at)
    WHERE status = 'RETRY_SCHEDULED' AND next_retry_at <= NOW();

-- Composite indexes for common queries
CREATE INDEX idx_dlq_records_topic_status ON dlq_records(original_topic, status);
CREATE INDEX idx_dlq_records_type_priority ON dlq_records(message_type, priority);

-- ============================================================================
-- DLQ Statistics Table
-- ============================================================================
CREATE TABLE IF NOT EXISTS dlq_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic VARCHAR(255) NOT NULL,
    message_type VARCHAR(100),

    -- Counts
    total_count BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0,
    failure_count BIGINT NOT NULL DEFAULT 0,
    retry_count BIGINT NOT NULL DEFAULT 0,
    escalation_count BIGINT NOT NULL DEFAULT 0,

    -- Time period
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(topic, message_type, period_start)
);

CREATE INDEX idx_dlq_stats_topic ON dlq_statistics(topic);
CREATE INDEX idx_dlq_stats_period ON dlq_statistics(period_start, period_end);

-- ============================================================================
-- DLQ Alerts Table
-- ============================================================================
CREATE TABLE IF NOT EXISTS dlq_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dlq_record_id UUID REFERENCES dlq_records(id),

    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,

    -- Alert channels
    pagerduty_sent BOOLEAN DEFAULT FALSE,
    slack_sent BOOLEAN DEFAULT FALSE,
    email_sent BOOLEAN DEFAULT FALSE,

    -- Alert status
    status VARCHAR(50) DEFAULT 'SENT',
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_alert_type CHECK (alert_type IN (
        'CRITICAL',
        'HIGH_PRIORITY',
        'ESCALATION',
        'PERMANENT_FAILURE',
        'THRESHOLD_EXCEEDED'
    )),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_dlq_alerts_record ON dlq_alerts(dlq_record_id);
CREATE INDEX idx_dlq_alerts_type ON dlq_alerts(alert_type);
CREATE INDEX idx_dlq_alerts_created ON dlq_alerts(created_at);

-- ============================================================================
-- DLQ Audit Log Table
-- ============================================================================
CREATE TABLE IF NOT EXISTS dlq_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dlq_record_id UUID REFERENCES dlq_records(id),

    action VARCHAR(100) NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),

    details JSONB,
    performed_by VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_audit_action CHECK (action IN (
        'CREATED',
        'STATUS_CHANGED',
        'RETRY_SCHEDULED',
        'RETRY_EXECUTED',
        'ESCALATED',
        'RESOLVED',
        'MANUALLY_REPROCESSED'
    ))
);

CREATE INDEX idx_dlq_audit_record ON dlq_audit_log(dlq_record_id);
CREATE INDEX idx_dlq_audit_action ON dlq_audit_log(action);
CREATE INDEX idx_dlq_audit_created ON dlq_audit_log(created_at);

-- ============================================================================
-- Views for Monitoring
-- ============================================================================

-- Active DLQ Records View
CREATE OR REPLACE VIEW v_active_dlq_records AS
SELECT
    dr.id,
    dr.message_id,
    dr.original_topic,
    dr.message_type,
    dr.priority,
    dr.status,
    dr.retry_count,
    dr.max_retries,
    dr.error_message,
    dr.next_retry_at,
    dr.dlq_timestamp,
    dr.created_at,
    EXTRACT(EPOCH FROM (NOW() - dr.dlq_timestamp))/3600 as hours_in_dlq
FROM dlq_records dr
WHERE dr.status NOT IN ('PROCESSED', 'PERMANENTLY_FAILED', 'MANUALLY_REPROCESSED')
ORDER BY
    CASE dr.priority
        WHEN 'CRITICAL' THEN 1
        WHEN 'HIGH' THEN 2
        WHEN 'MEDIUM' THEN 3
        WHEN 'LOW' THEN 4
    END,
    dr.dlq_timestamp ASC;

-- DLQ Summary by Topic
CREATE OR REPLACE VIEW v_dlq_summary_by_topic AS
SELECT
    original_topic,
    COUNT(*) as total_records,
    COUNT(*) FILTER (WHERE status = 'PENDING') as pending,
    COUNT(*) FILTER (WHERE status = 'RETRY_SCHEDULED') as scheduled_retry,
    COUNT(*) FILTER (WHERE status = 'ESCALATED') as escalated,
    COUNT(*) FILTER (WHERE status = 'PERMANENTLY_FAILED') as permanently_failed,
    COUNT(*) FILTER (WHERE status = 'PROCESSED') as successfully_processed,
    AVG(retry_count) as avg_retry_count,
    MIN(dlq_timestamp) as oldest_record,
    MAX(dlq_timestamp) as newest_record
FROM dlq_records
WHERE created_at > NOW() - INTERVAL '7 days'
GROUP BY original_topic
ORDER BY total_records DESC;

-- Critical DLQ Records Requiring Attention
CREATE OR REPLACE VIEW v_critical_dlq_attention AS
SELECT
    dr.id,
    dr.message_id,
    dr.original_topic,
    dr.message_type,
    dr.priority,
    dr.status,
    dr.retry_count,
    dr.error_message,
    dr.dlq_timestamp,
    EXTRACT(EPOCH FROM (NOW() - dr.dlq_timestamp))/3600 as hours_in_dlq,
    CASE
        WHEN dr.priority = 'CRITICAL' AND dr.retry_count >= 5 THEN 'IMMEDIATE'
        WHEN dr.priority = 'HIGH' AND dr.retry_count >= 7 THEN 'URGENT'
        WHEN EXTRACT(EPOCH FROM (NOW() - dr.dlq_timestamp))/3600 > 24 THEN 'OVERDUE'
        ELSE 'REVIEW'
    END as attention_level
FROM dlq_records dr
WHERE dr.status IN ('PENDING', 'RETRY_FAILED', 'ESCALATED')
  AND (
      (dr.priority = 'CRITICAL' AND dr.retry_count >= 5) OR
      (dr.priority = 'HIGH' AND dr.retry_count >= 7) OR
      (EXTRACT(EPOCH FROM (NOW() - dr.dlq_timestamp))/3600 > 24)
  )
ORDER BY attention_level, dr.dlq_timestamp;

-- ============================================================================
-- Functions
-- ============================================================================

-- Function to update DLQ statistics
CREATE OR REPLACE FUNCTION update_dlq_statistics()
RETURNS TRIGGER AS $$
BEGIN
    -- Update or insert statistics for the topic
    INSERT INTO dlq_statistics (
        topic,
        message_type,
        total_count,
        success_count,
        failure_count,
        retry_count,
        escalation_count,
        period_start,
        period_end
    )
    VALUES (
        NEW.original_topic,
        NEW.message_type,
        1,
        CASE WHEN NEW.status = 'PROCESSED' THEN 1 ELSE 0 END,
        CASE WHEN NEW.status = 'PERMANENTLY_FAILED' THEN 1 ELSE 0 END,
        NEW.retry_count,
        CASE WHEN NEW.status = 'ESCALATED' THEN 1 ELSE 0 END,
        DATE_TRUNC('hour', NOW()),
        DATE_TRUNC('hour', NOW()) + INTERVAL '1 hour'
    )
    ON CONFLICT (topic, message_type, period_start)
    DO UPDATE SET
        total_count = dlq_statistics.total_count + 1,
        success_count = dlq_statistics.success_count + CASE WHEN NEW.status = 'PROCESSED' THEN 1 ELSE 0 END,
        failure_count = dlq_statistics.failure_count + CASE WHEN NEW.status = 'PERMANENTLY_FAILED' THEN 1 ELSE 0 END,
        retry_count = dlq_statistics.retry_count + NEW.retry_count,
        escalation_count = dlq_statistics.escalation_count + CASE WHEN NEW.status = 'ESCALATED' THEN 1 ELSE 0 END,
        updated_at = NOW();

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for DLQ statistics
CREATE TRIGGER trg_update_dlq_statistics
AFTER INSERT ON dlq_records
FOR EACH ROW
EXECUTE FUNCTION update_dlq_statistics();

-- Function to automatically update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for updated_at
CREATE TRIGGER trg_dlq_records_updated_at
BEFORE UPDATE ON dlq_records
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Comments
-- ============================================================================

COMMENT ON TABLE dlq_records IS 'Stores all messages that failed processing and were sent to Dead Letter Queue';
COMMENT ON TABLE dlq_statistics IS 'Aggregated statistics for DLQ monitoring and dashboards';
COMMENT ON TABLE dlq_alerts IS 'Tracks alerts sent for DLQ events';
COMMENT ON TABLE dlq_audit_log IS 'Complete audit trail of all DLQ record changes';

COMMENT ON VIEW v_active_dlq_records IS 'All DLQ records currently pending or in retry';
COMMENT ON VIEW v_dlq_summary_by_topic IS 'Summary statistics by topic for last 7 days';
COMMENT ON VIEW v_critical_dlq_attention IS 'Critical DLQ records requiring immediate attention';

-- ============================================================================
-- Grants (adjust as needed for your security model)
-- ============================================================================

GRANT SELECT, INSERT, UPDATE ON dlq_records TO waqiti_application_user;
GRANT SELECT, INSERT, UPDATE ON dlq_statistics TO waqiti_application_user;
GRANT SELECT, INSERT ON dlq_alerts TO waqiti_application_user;
GRANT SELECT, INSERT ON dlq_audit_log TO waqiti_application_user;
GRANT SELECT ON v_active_dlq_records TO waqiti_application_user;
GRANT SELECT ON v_dlq_summary_by_topic TO waqiti_application_user;
GRANT SELECT ON v_critical_dlq_attention TO waqiti_application_user;

-- ============================================================================
-- Initial Data / Configuration
-- ============================================================================

-- Insert default configuration (optional)
-- This can be used to track DLQ health thresholds
CREATE TABLE IF NOT EXISTS dlq_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO dlq_configuration (config_key, config_value, description) VALUES
('max_retry_attempts_critical', '10', 'Maximum retry attempts for CRITICAL priority messages'),
('max_retry_attempts_high', '7', 'Maximum retry attempts for HIGH priority messages'),
('max_retry_attempts_medium', '5', 'Maximum retry attempts for MEDIUM priority messages'),
('max_retry_attempts_low', '3', 'Maximum retry attempts for LOW priority messages'),
('escalation_threshold_hours', '24', 'Hours before auto-escalation'),
('alert_threshold_per_hour', '100', 'Alert if DLQ receives more than this many messages per hour')
ON CONFLICT (config_key) DO NOTHING;

COMMENT ON TABLE dlq_configuration IS 'DLQ system configuration parameters';

-- ============================================================================
-- Maintenance
-- ============================================================================

-- Cleanup old DLQ records (run periodically, e.g., monthly)
-- Keep for 90 days minimum for compliance
CREATE OR REPLACE FUNCTION cleanup_old_dlq_records(retention_days INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Only delete successfully processed or permanently failed records older than retention period
    WITH deleted AS (
        DELETE FROM dlq_records
        WHERE status IN ('PROCESSED', 'PERMANENTLY_FAILED')
          AND created_at < NOW() - (retention_days || ' days')::INTERVAL
        RETURNING *
    )
    SELECT COUNT(*) INTO deleted_count FROM deleted;

    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_dlq_records IS 'Cleanup DLQ records older than specified retention period (default 90 days)';

-- ============================================================================
-- End of DLQ Tables Migration
-- ============================================================================
