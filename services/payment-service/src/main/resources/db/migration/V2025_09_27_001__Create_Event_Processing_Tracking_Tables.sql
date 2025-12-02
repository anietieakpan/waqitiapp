-- Migration: Create Event Processing Tracking Tables
-- Version: V2025_09_27_001
-- Date: 2025-09-27
-- Author: Waqiti Platform Team
-- Purpose: Create tables to track event processing, idempotency, and audit trails for event consumers
-- Related: PaymentCompletedEventConsumer, FraudContainmentExecutedEventConsumer

-- ============================================================================
-- EVENT PROCESSING TRACKING TABLE
-- ============================================================================
-- Tracks all processed events for idempotency and audit purposes
CREATE TABLE IF NOT EXISTS event_processing_log (
    id BIGSERIAL PRIMARY KEY,
    
    -- Event identification
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(500) NOT NULL UNIQUE,
    correlation_id VARCHAR(255),
    
    -- Event metadata
    topic_name VARCHAR(255),
    partition_id INTEGER,
    offset_value BIGINT,
    
    -- Processing status
    processing_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    
    -- Entity references
    user_id VARCHAR(255),
    transaction_id VARCHAR(255),
    payment_id VARCHAR(255),
    alert_id VARCHAR(255),
    
    -- Event payload (for debugging and replay)
    event_payload JSONB,
    
    -- Processing details
    processed_at TIMESTAMP,
    first_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMP,
    
    -- Error tracking
    error_message TEXT,
    error_class VARCHAR(500),
    error_stacktrace TEXT,
    
    -- Timing metrics
    processing_time_ms BIGINT,
    total_processing_time_ms BIGINT,
    
    -- Metadata
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    CONSTRAINT chk_processing_status CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD_LETTER'))
);

-- Indexes for event_processing_log
CREATE INDEX idx_event_processing_log_event_id ON event_processing_log(event_id);
CREATE INDEX idx_event_processing_log_event_type ON event_processing_log(event_type);
CREATE INDEX idx_event_processing_log_idempotency_key ON event_processing_log(idempotency_key);
CREATE INDEX idx_event_processing_log_correlation_id ON event_processing_log(correlation_id);
CREATE INDEX idx_event_processing_log_status ON event_processing_log(processing_status);
CREATE INDEX idx_event_processing_log_user_id ON event_processing_log(user_id);
CREATE INDEX idx_event_processing_log_transaction_id ON event_processing_log(transaction_id);
CREATE INDEX idx_event_processing_log_created_at ON event_processing_log(created_at DESC);
CREATE INDEX idx_event_processing_log_user_created ON event_processing_log(user_id, created_at DESC);
CREATE INDEX idx_event_processing_log_type_status ON event_processing_log(event_type, processing_status);
CREATE INDEX idx_event_processing_log_correlation_status ON event_processing_log(correlation_id, processing_status);

-- ============================================================================
-- PAYMENT COMPLETED EVENT PROCESSING TABLE
-- ============================================================================
-- Specific tracking for payment-completed events
CREATE TABLE IF NOT EXISTS payment_completed_event_processing (
    id BIGSERIAL PRIMARY KEY,
    
    -- Event reference
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_processing_log_id BIGINT REFERENCES event_processing_log(id),
    
    -- Payment details
    payment_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255),
    
    -- Processing steps tracking
    rewards_processed BOOLEAN NOT NULL DEFAULT FALSE,
    rewards_processed_at TIMESTAMP,
    rewards_error TEXT,
    
    analytics_processed BOOLEAN NOT NULL DEFAULT FALSE,
    analytics_processed_at TIMESTAMP,
    analytics_error TEXT,
    
    ledger_processed BOOLEAN NOT NULL DEFAULT FALSE,
    ledger_processed_at TIMESTAMP,
    ledger_error TEXT,
    
    notifications_sent BOOLEAN NOT NULL DEFAULT FALSE,
    notifications_sent_at TIMESTAMP,
    notifications_error TEXT,
    
    -- Overall status
    fully_processed BOOLEAN NOT NULL DEFAULT FALSE,
    fully_processed_at TIMESTAMP,
    
    -- Metrics
    total_processing_time_ms BIGINT,
    
    -- Metadata
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for payment_completed_event_processing
CREATE INDEX idx_payment_completed_event_event_id ON payment_completed_event_processing(event_id);
CREATE INDEX idx_payment_completed_event_payment_id ON payment_completed_event_processing(payment_id);
CREATE INDEX idx_payment_completed_event_user_id ON payment_completed_event_processing(user_id);
CREATE INDEX idx_payment_completed_event_transaction_id ON payment_completed_event_processing(transaction_id);
CREATE INDEX idx_payment_completed_event_fully_processed ON payment_completed_event_processing(fully_processed);
CREATE INDEX idx_payment_completed_event_created_at ON payment_completed_event_processing(created_at DESC);

-- ============================================================================
-- FRAUD CONTAINMENT EVENT PROCESSING TABLE
-- ============================================================================
-- Specific tracking for fraud-containment-executed events
CREATE TABLE IF NOT EXISTS fraud_containment_event_processing (
    id BIGSERIAL PRIMARY KEY,
    
    -- Event reference
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_processing_log_id BIGINT REFERENCES event_processing_log(id),
    
    -- Fraud details
    alert_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255),
    fraud_type VARCHAR(100),
    fraud_score DECIMAL(5, 4),
    risk_level VARCHAR(50),
    severity VARCHAR(50),
    
    -- Processing steps tracking
    risk_scores_updated BOOLEAN NOT NULL DEFAULT FALSE,
    risk_scores_updated_at TIMESTAMP,
    risk_scores_error TEXT,
    
    compliance_alert_created BOOLEAN NOT NULL DEFAULT FALSE,
    compliance_alert_created_at TIMESTAMP,
    compliance_alert_error TEXT,
    
    user_notified BOOLEAN NOT NULL DEFAULT FALSE,
    user_notified_at TIMESTAMP,
    user_notification_error TEXT,
    
    security_team_notified BOOLEAN NOT NULL DEFAULT FALSE,
    security_team_notified_at TIMESTAMP,
    security_team_error TEXT,
    
    regulatory_notified BOOLEAN NOT NULL DEFAULT FALSE,
    regulatory_notified_at TIMESTAMP,
    regulatory_notification_error TEXT,
    
    requires_regulatory_notification BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Overall status
    fully_processed BOOLEAN NOT NULL DEFAULT FALSE,
    fully_processed_at TIMESTAMP,
    
    -- Metrics
    total_processing_time_ms BIGINT,
    
    -- Metadata
    containment_actions JSONB,
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for fraud_containment_event_processing
CREATE INDEX idx_fraud_containment_event_event_id ON fraud_containment_event_processing(event_id);
CREATE INDEX idx_fraud_containment_event_alert_id ON fraud_containment_event_processing(alert_id);
CREATE INDEX idx_fraud_containment_event_user_id ON fraud_containment_event_processing(user_id);
CREATE INDEX idx_fraud_containment_event_transaction_id ON fraud_containment_event_processing(transaction_id);
CREATE INDEX idx_fraud_containment_event_fraud_type ON fraud_containment_event_processing(fraud_type);
CREATE INDEX idx_fraud_containment_event_severity ON fraud_containment_event_processing(severity);
CREATE INDEX idx_fraud_containment_event_fully_processed ON fraud_containment_event_processing(fully_processed);
CREATE INDEX idx_fraud_containment_event_created_at ON fraud_containment_event_processing(created_at DESC);
CREATE INDEX idx_fraud_containment_event_regulatory ON fraud_containment_event_processing(requires_regulatory_notification, regulatory_notified);

-- ============================================================================
-- DEAD LETTER QUEUE TABLE
-- ============================================================================
-- Stores events that failed after all retry attempts
CREATE TABLE IF NOT EXISTS event_dead_letter_queue (
    id BIGSERIAL PRIMARY KEY,
    
    -- Event identification
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(255),
    
    -- Original event data
    original_topic VARCHAR(255) NOT NULL,
    original_partition INTEGER,
    original_offset BIGINT,
    event_payload JSONB NOT NULL,
    
    -- Entity references
    user_id VARCHAR(255),
    transaction_id VARCHAR(255),
    payment_id VARCHAR(255),
    alert_id VARCHAR(255),
    
    -- Failure details
    failure_reason TEXT NOT NULL,
    error_class VARCHAR(500),
    error_stacktrace TEXT,
    retry_count INTEGER NOT NULL,
    
    -- Processing attempts history
    processing_attempts JSONB,
    
    -- Resolution tracking
    resolution_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    
    -- Manual intervention
    requires_manual_review BOOLEAN NOT NULL DEFAULT TRUE,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    
    -- Reprocessing
    reprocessed BOOLEAN NOT NULL DEFAULT FALSE,
    reprocessed_at TIMESTAMP,
    reprocessing_result VARCHAR(50),
    
    -- Priority
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    escalated BOOLEAN NOT NULL DEFAULT FALSE,
    escalated_at TIMESTAMP,
    
    -- Metadata
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_dlq_resolution_status CHECK (resolution_status IN ('PENDING', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'REPROCESSED')),
    CONSTRAINT chk_dlq_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- Indexes for event_dead_letter_queue
CREATE INDEX idx_event_dlq_event_id ON event_dead_letter_queue(event_id);
CREATE INDEX idx_event_dlq_event_type ON event_dead_letter_queue(event_type);
CREATE INDEX idx_event_dlq_resolution_status ON event_dead_letter_queue(resolution_status);
CREATE INDEX idx_event_dlq_user_id ON event_dead_letter_queue(user_id);
CREATE INDEX idx_event_dlq_created_at ON event_dead_letter_queue(created_at DESC);
CREATE INDEX idx_event_dlq_priority ON event_dead_letter_queue(priority, escalated);
CREATE INDEX idx_event_dlq_requires_review ON event_dead_letter_queue(requires_manual_review, reviewed_at NULLS FIRST);
CREATE INDEX idx_event_dlq_reprocessed ON event_dead_letter_queue(reprocessed);

-- ============================================================================
-- EVENT PROCESSING METRICS TABLE
-- ============================================================================
-- Aggregated metrics for monitoring and alerting
CREATE TABLE IF NOT EXISTS event_processing_metrics (
    id BIGSERIAL PRIMARY KEY,
    
    -- Metric identification
    event_type VARCHAR(100) NOT NULL,
    metric_date DATE NOT NULL,
    metric_hour INTEGER NOT NULL,
    
    -- Volume metrics
    total_events BIGINT NOT NULL DEFAULT 0,
    successful_events BIGINT NOT NULL DEFAULT 0,
    failed_events BIGINT NOT NULL DEFAULT 0,
    dead_letter_events BIGINT NOT NULL DEFAULT 0,
    retried_events BIGINT NOT NULL DEFAULT 0,
    
    -- Performance metrics
    avg_processing_time_ms BIGINT,
    min_processing_time_ms BIGINT,
    max_processing_time_ms BIGINT,
    p50_processing_time_ms BIGINT,
    p95_processing_time_ms BIGINT,
    p99_processing_time_ms BIGINT,
    
    -- Error metrics
    error_rate DECIMAL(5, 2),
    retry_rate DECIMAL(5, 2),
    dlq_rate DECIMAL(5, 2),
    
    -- Most common errors
    top_errors JSONB,
    
    -- Metadata
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint
    CONSTRAINT uq_event_metrics_type_date_hour UNIQUE (event_type, metric_date, metric_hour)
);

-- Indexes for event_processing_metrics
CREATE INDEX idx_event_metrics_event_type ON event_processing_metrics(event_type);
CREATE INDEX idx_event_metrics_date ON event_processing_metrics(metric_date DESC);
CREATE INDEX idx_event_metrics_date_hour ON event_processing_metrics(metric_date DESC, metric_hour DESC);
CREATE INDEX idx_event_metrics_error_rate ON event_processing_metrics(error_rate DESC);

-- ============================================================================
-- UPDATE TRIGGERS
-- ============================================================================

-- Trigger function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_event_processing_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers to all tables
CREATE TRIGGER trigger_update_event_processing_log_updated_at
    BEFORE UPDATE ON event_processing_log
    FOR EACH ROW
    EXECUTE FUNCTION update_event_processing_updated_at();

CREATE TRIGGER trigger_update_payment_completed_event_processing_updated_at
    BEFORE UPDATE ON payment_completed_event_processing
    FOR EACH ROW
    EXECUTE FUNCTION update_event_processing_updated_at();

CREATE TRIGGER trigger_update_fraud_containment_event_processing_updated_at
    BEFORE UPDATE ON fraud_containment_event_processing
    FOR EACH ROW
    EXECUTE FUNCTION update_event_processing_updated_at();

CREATE TRIGGER trigger_update_event_dlq_updated_at
    BEFORE UPDATE ON event_dead_letter_queue
    FOR EACH ROW
    EXECUTE FUNCTION update_event_processing_updated_at();

CREATE TRIGGER trigger_update_event_metrics_updated_at
    BEFORE UPDATE ON event_processing_metrics
    FOR EACH ROW
    EXECUTE FUNCTION update_event_processing_updated_at();

-- ============================================================================
-- HELPER VIEWS
-- ============================================================================

-- View for events requiring manual review
CREATE OR REPLACE VIEW v_events_requiring_review AS
SELECT 
    dlq.id,
    dlq.event_id,
    dlq.event_type,
    dlq.user_id,
    dlq.transaction_id,
    dlq.payment_id,
    dlq.alert_id,
    dlq.failure_reason,
    dlq.priority,
    dlq.escalated,
    dlq.created_at,
    dlq.resolution_status
FROM event_dead_letter_queue dlq
WHERE dlq.requires_manual_review = TRUE
  AND dlq.reviewed_at IS NULL
  AND dlq.resolution_status = 'PENDING'
ORDER BY 
    CASE dlq.priority
        WHEN 'CRITICAL' THEN 1
        WHEN 'HIGH' THEN 2
        WHEN 'MEDIUM' THEN 3
        WHEN 'LOW' THEN 4
    END,
    dlq.created_at ASC;

-- View for event processing statistics
CREATE OR REPLACE VIEW v_event_processing_stats AS
SELECT 
    epl.event_type,
    COUNT(*) as total_events,
    COUNT(*) FILTER (WHERE epl.processing_status = 'COMPLETED') as completed_events,
    COUNT(*) FILTER (WHERE epl.processing_status = 'FAILED') as failed_events,
    COUNT(*) FILTER (WHERE epl.processing_status = 'DEAD_LETTER') as dead_letter_events,
    AVG(epl.processing_time_ms) as avg_processing_time_ms,
    MAX(epl.processing_time_ms) as max_processing_time_ms,
    ROUND(100.0 * COUNT(*) FILTER (WHERE epl.processing_status = 'FAILED') / NULLIF(COUNT(*), 0), 2) as failure_rate_pct,
    DATE_TRUNC('hour', epl.created_at) as hour_bucket
FROM event_processing_log epl
WHERE epl.created_at >= NOW() - INTERVAL '24 hours'
GROUP BY epl.event_type, DATE_TRUNC('hour', epl.created_at)
ORDER BY hour_bucket DESC, epl.event_type;

-- View for payment completed events status
CREATE OR REPLACE VIEW v_payment_completed_events_status AS
SELECT 
    pce.event_id,
    pce.payment_id,
    pce.user_id,
    pce.rewards_processed,
    pce.analytics_processed,
    pce.ledger_processed,
    pce.notifications_sent,
    pce.fully_processed,
    pce.total_processing_time_ms,
    pce.created_at,
    epl.processing_status,
    epl.retry_count
FROM payment_completed_event_processing pce
LEFT JOIN event_processing_log epl ON pce.event_processing_log_id = epl.id
ORDER BY pce.created_at DESC;

-- View for fraud containment events status
CREATE OR REPLACE VIEW v_fraud_containment_events_status AS
SELECT 
    fce.event_id,
    fce.alert_id,
    fce.user_id,
    fce.fraud_type,
    fce.severity,
    fce.risk_scores_updated,
    fce.compliance_alert_created,
    fce.user_notified,
    fce.security_team_notified,
    fce.regulatory_notified,
    fce.requires_regulatory_notification,
    fce.fully_processed,
    fce.total_processing_time_ms,
    fce.created_at,
    epl.processing_status,
    epl.retry_count
FROM fraud_containment_event_processing fce
LEFT JOIN event_processing_log epl ON fce.event_processing_log_id = epl.id
ORDER BY fce.created_at DESC;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE event_processing_log IS 'Tracks all event processing for idempotency, audit, and monitoring';
COMMENT ON TABLE payment_completed_event_processing IS 'Detailed tracking of payment-completed event processing steps';
COMMENT ON TABLE fraud_containment_event_processing IS 'Detailed tracking of fraud-containment-executed event processing steps';
COMMENT ON TABLE event_dead_letter_queue IS 'Stores events that failed after all retry attempts for manual review';
COMMENT ON TABLE event_processing_metrics IS 'Aggregated metrics for event processing monitoring and alerting';

COMMENT ON COLUMN event_processing_log.idempotency_key IS 'Unique key to prevent duplicate processing of the same event';
COMMENT ON COLUMN event_processing_log.event_payload IS 'Full event payload stored as JSONB for debugging and replay';
COMMENT ON COLUMN event_dead_letter_queue.processing_attempts IS 'History of all processing attempts stored as JSONB array';

COMMENT ON VIEW v_events_requiring_review IS 'Events in DLQ that require manual review, ordered by priority';
COMMENT ON VIEW v_event_processing_stats IS 'Real-time statistics for event processing over the last 24 hours';
COMMENT ON VIEW v_payment_completed_events_status IS 'Current status of payment completed event processing';
COMMENT ON VIEW v_fraud_containment_events_status IS 'Current status of fraud containment event processing';

-- ============================================================================
-- DATA RETENTION POLICY SETUP
-- ============================================================================

-- Note: Implement data retention with a scheduled job (e.g., pg_cron)
-- Example retention policy:
-- - event_processing_log: Keep for 90 days
-- - payment_completed_event_processing: Keep for 7 years (financial records)
-- - fraud_containment_event_processing: Keep for 7 years (compliance records)
-- - event_dead_letter_queue: Keep indefinitely until resolved
-- - event_processing_metrics: Keep for 2 years

COMMENT ON DATABASE postgres IS 'Data Retention Policy:
- event_processing_log: 90 days
- payment_completed_event_processing: 7 years
- fraud_containment_event_processing: 7 years  
- event_dead_letter_queue: Until resolved
- event_processing_metrics: 2 years';

-- ============================================================================
-- GRANT PERMISSIONS
-- ============================================================================

-- Grant permissions to payment_service role (adjust role name as needed)
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO payment_service;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO payment_service;
-- GRANT SELECT ON ALL VIEWS IN SCHEMA public TO payment_service;

-- ============================================================================
-- END OF MIGRATION
-- ============================================================================