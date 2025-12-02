-- ============================================================================
-- DLQ (Dead Letter Queue) Tracking Tables
-- ============================================================================
-- Migration: V400__Create_DLQ_tracking_tables
-- Purpose: Create tables for tracking failed Kafka messages and their recovery
-- Compliance: SOX/GDPR 7-year retention requirement for permanent failures
-- Author: Waqiti Platform Team
-- Date: 2025-11-15
-- ============================================================================

-- ============================================================================
-- TABLE: dlq_retry_queue
-- Purpose: Tracks messages scheduled for retry with exponential backoff
-- Retention: Messages deleted after successful retry or permanent failure
-- ============================================================================
CREATE TABLE dlq_retry_queue (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Original Message Metadata
    original_topic VARCHAR(255) NOT NULL,
    original_partition INTEGER NOT NULL,
    original_offset BIGINT NOT NULL,
    original_key VARCHAR(500),

    -- Message Payload (sanitized - PII masked)
    payload TEXT NOT NULL,

    -- Error Information
    exception_message TEXT,
    exception_class VARCHAR(500),
    exception_stack_trace TEXT,

    -- Failure Metadata
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failure_reason VARCHAR(500),

    -- Retry Strategy
    retry_attempt INTEGER NOT NULL DEFAULT 0,
    max_retry_attempts INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_reason VARCHAR(500),
    backoff_delay_ms BIGINT NOT NULL,

    -- Status Tracking
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RETRYING', 'SUCCESS', 'FAILED', 'CANCELLED')),

    -- Audit Fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(255) DEFAULT 'system',

    -- Handler Information
    handler_name VARCHAR(255) NOT NULL,
    recovery_action VARCHAR(100),

    -- Correlation for Tracing
    correlation_id VARCHAR(255),

    -- Constraints
    CONSTRAINT dlq_retry_unique_message
        UNIQUE (original_topic, original_partition, original_offset, retry_attempt)
);

-- Indexes for dlq_retry_queue
CREATE INDEX idx_dlq_retry_next_retry
    ON dlq_retry_queue(next_retry_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_dlq_retry_topic
    ON dlq_retry_queue(original_topic, status);

CREATE INDEX idx_dlq_retry_created
    ON dlq_retry_queue(created_at DESC);

CREATE INDEX idx_dlq_retry_status
    ON dlq_retry_queue(status);

CREATE INDEX idx_dlq_retry_correlation
    ON dlq_retry_queue(correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Comments for dlq_retry_queue
COMMENT ON TABLE dlq_retry_queue IS
    'Tracks Kafka messages scheduled for retry with exponential backoff. Messages are processed by scheduled job.';

COMMENT ON COLUMN dlq_retry_queue.retry_attempt IS
    'Current retry attempt number (0-based). Max attempts defined by max_retry_attempts.';

COMMENT ON COLUMN dlq_retry_queue.next_retry_at IS
    'Timestamp when next retry should be attempted. Uses exponential backoff calculation.';

COMMENT ON COLUMN dlq_retry_queue.payload IS
    'Message payload with PII masked for security. Used for retry processing.';


-- ============================================================================
-- TABLE: dlq_manual_review_queue
-- Purpose: Tracks messages requiring manual operations team review
-- Retention: Messages deleted after resolution
-- ============================================================================
CREATE TABLE dlq_manual_review_queue (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Original Message Metadata
    original_topic VARCHAR(255) NOT NULL,
    original_partition INTEGER NOT NULL,
    original_offset BIGINT NOT NULL,
    original_key VARCHAR(500),

    -- Message Payload (sanitized - PII masked)
    payload TEXT NOT NULL,

    -- Error Information
    exception_message TEXT,
    exception_class VARCHAR(500),
    exception_stack_trace TEXT,

    -- Failure Metadata
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_attempts INTEGER NOT NULL DEFAULT 0,

    -- Review Information
    review_reason VARCHAR(500) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    -- Assignment and Status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'IN_REVIEW', 'RESOLVED', 'ESCALATED', 'DISMISSED')),
    assigned_to VARCHAR(255),
    assigned_at TIMESTAMP WITH TIME ZONE,

    -- Resolution
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by VARCHAR(255),
    resolution_notes TEXT,
    resolution_action VARCHAR(100),

    -- SLA Tracking
    sla_due_at TIMESTAMP WITH TIME ZONE,
    sla_breached BOOLEAN DEFAULT FALSE,

    -- Audit Fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(255) DEFAULT 'system',

    -- Handler Information
    handler_name VARCHAR(255) NOT NULL,

    -- Correlation for Tracing
    correlation_id VARCHAR(255),

    -- Additional Context (JSON)
    context_data JSONB,

    -- Constraints
    CONSTRAINT dlq_review_unique_message
        UNIQUE (original_topic, original_partition, original_offset)
);

-- Indexes for dlq_manual_review_queue
CREATE INDEX idx_manual_review_status
    ON dlq_manual_review_queue(status, priority DESC);

CREATE INDEX idx_manual_review_priority
    ON dlq_manual_review_queue(priority DESC, created_at ASC)
    WHERE status IN ('PENDING', 'IN_REVIEW');

CREATE INDEX idx_manual_review_created
    ON dlq_manual_review_queue(created_at DESC);

CREATE INDEX idx_manual_review_assigned
    ON dlq_manual_review_queue(assigned_to)
    WHERE assigned_to IS NOT NULL;

CREATE INDEX idx_manual_review_sla
    ON dlq_manual_review_queue(sla_due_at)
    WHERE status IN ('PENDING', 'IN_REVIEW') AND sla_breached = FALSE;

CREATE INDEX idx_manual_review_topic
    ON dlq_manual_review_queue(original_topic);

CREATE INDEX idx_manual_review_correlation
    ON dlq_manual_review_queue(correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Comments for dlq_manual_review_queue
COMMENT ON TABLE dlq_manual_review_queue IS
    'Tracks messages requiring manual review by operations team. Prioritized by business impact.';

COMMENT ON COLUMN dlq_manual_review_queue.priority IS
    'CRITICAL=First account failures, HIGH=Financial impact, MEDIUM=Standard, LOW=Minor issues';

COMMENT ON COLUMN dlq_manual_review_queue.sla_due_at IS
    'SLA deadline for review. CRITICAL=15min, HIGH=1hr, MEDIUM=4hrs, LOW=24hrs';

COMMENT ON COLUMN dlq_manual_review_queue.context_data IS
    'Additional context in JSON format (account details, user info, business impact)';


-- ============================================================================
-- TABLE: dlq_permanent_failures
-- Purpose: Audit log of permanent failures (NEVER DELETE - compliance)
-- Retention: 7 years minimum (SOX/GDPR requirement)
-- ============================================================================
CREATE TABLE dlq_permanent_failures (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Original Message Metadata
    original_topic VARCHAR(255) NOT NULL,
    original_partition INTEGER NOT NULL,
    original_offset BIGINT NOT NULL,
    original_key VARCHAR(500),

    -- Message Payload (sanitized - PII masked for compliance)
    payload TEXT NOT NULL,

    -- Failure Classification
    failure_reason VARCHAR(500) NOT NULL,
    failure_category VARCHAR(100) NOT NULL
        CHECK (failure_category IN (
            'BUSINESS_RULE_VIOLATION',
            'DATA_VALIDATION_ERROR',
            'INVALID_STATE',
            'RESOURCE_NOT_FOUND',
            'DUPLICATE_OPERATION',
            'COMPLIANCE_BLOCK',
            'MAX_RETRIES_EXCEEDED',
            'UNRECOVERABLE_ERROR',
            'OTHER'
        )),

    -- Error Information
    exception_message TEXT,
    exception_class VARCHAR(500),
    exception_stack_trace TEXT,

    -- Failure Metadata
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    retry_attempts INTEGER NOT NULL DEFAULT 0,

    -- Handler Information
    handler_name VARCHAR(255) NOT NULL,
    recovery_attempts TEXT,  -- JSON array of recovery attempts

    -- Compliance & Audit (CRITICAL)
    audit_retention_until DATE NOT NULL
        GENERATED ALWAYS AS (
            (recorded_at AT TIME ZONE 'UTC')::date + INTERVAL '7 years'
        ) STORED,
    compliance_reviewed BOOLEAN DEFAULT FALSE,
    compliance_reviewed_at TIMESTAMP WITH TIME ZONE,
    compliance_reviewed_by VARCHAR(255),

    -- Business Impact Assessment
    business_impact VARCHAR(50)
        CHECK (business_impact IN ('NONE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    impact_description TEXT,
    financial_impact_amount DECIMAL(19,4),
    financial_impact_currency VARCHAR(3) DEFAULT 'USD',

    -- Correlation for Tracing
    correlation_id VARCHAR(255),
    trace_id VARCHAR(255),

    -- Additional Context
    context_data JSONB,

    -- Audit Fields
    created_by VARCHAR(255) DEFAULT 'system' NOT NULL,

    -- Remediation Tracking
    remediation_required BOOLEAN DEFAULT FALSE,
    remediation_status VARCHAR(50),
    remediation_notes TEXT,
    remediation_completed_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for dlq_permanent_failures
CREATE INDEX idx_permanent_failures_topic
    ON dlq_permanent_failures(original_topic, failed_at DESC);

CREATE INDEX idx_permanent_failures_recorded
    ON dlq_permanent_failures(recorded_at DESC);

CREATE INDEX idx_permanent_failures_retention
    ON dlq_permanent_failures(audit_retention_until);

CREATE INDEX idx_permanent_failures_category
    ON dlq_permanent_failures(failure_category, failed_at DESC);

CREATE INDEX idx_permanent_failures_impact
    ON dlq_permanent_failures(business_impact)
    WHERE business_impact IN ('HIGH', 'CRITICAL');

CREATE INDEX idx_permanent_failures_compliance
    ON dlq_permanent_failures(compliance_reviewed)
    WHERE compliance_reviewed = FALSE;

CREATE INDEX idx_permanent_failures_remediation
    ON dlq_permanent_failures(remediation_required, remediation_status)
    WHERE remediation_required = TRUE;

CREATE INDEX idx_permanent_failures_correlation
    ON dlq_permanent_failures(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX idx_permanent_failures_handler
    ON dlq_permanent_failures(handler_name, failed_at DESC);

-- Comments for dlq_permanent_failures
COMMENT ON TABLE dlq_permanent_failures IS
    '⚠️ CRITICAL: Permanent failure audit log with 7-year retention for SOX/GDPR compliance. NEVER DELETE RECORDS.';

COMMENT ON COLUMN dlq_permanent_failures.audit_retention_until IS
    'Auto-calculated retention date (recorded_at + 7 years). Records MUST be kept until this date per compliance.';

COMMENT ON COLUMN dlq_permanent_failures.payload IS
    'Message payload with ALL PII masked per GDPR. Account numbers show last 4 digits only.';

COMMENT ON COLUMN dlq_permanent_failures.failure_category IS
    'Classification for compliance reporting and analytics. Determines remediation requirements.';

COMMENT ON COLUMN dlq_permanent_failures.financial_impact_amount IS
    'Estimated or actual financial impact in base currency. Used for compliance reporting.';


-- ============================================================================
-- TRIGGER: Prevent deletion of permanent failure records (COMPLIANCE)
-- ============================================================================
CREATE OR REPLACE FUNCTION prevent_permanent_failure_deletion()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'COMPLIANCE VIOLATION: Deletion of permanent failure records is prohibited. Record ID: %. ' ||
            'Retention required until: %. Contact compliance team for data retention policy.',
            OLD.id, OLD.audit_retention_until
            USING ERRCODE = '23502';  -- not_null_violation code
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_permanent_failure_deletion
BEFORE DELETE ON dlq_permanent_failures
FOR EACH ROW
EXECUTE FUNCTION prevent_permanent_failure_deletion();

COMMENT ON FUNCTION prevent_permanent_failure_deletion() IS
    'Compliance trigger: Prevents deletion of permanent failure audit records per SOX/GDPR 7-year retention.';


-- ============================================================================
-- TRIGGER: Update updated_at timestamp automatically
-- ============================================================================
CREATE OR REPLACE FUNCTION update_dlq_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dlq_retry_queue_updated_at
BEFORE UPDATE ON dlq_retry_queue
FOR EACH ROW
EXECUTE FUNCTION update_dlq_updated_at();

CREATE TRIGGER trg_dlq_manual_review_queue_updated_at
BEFORE UPDATE ON dlq_manual_review_queue
FOR EACH ROW
EXECUTE FUNCTION update_dlq_updated_at();

COMMENT ON FUNCTION update_dlq_updated_at() IS
    'Automatically updates the updated_at timestamp on record modification.';


-- ============================================================================
-- TRIGGER: Auto-calculate SLA deadline for manual review
-- ============================================================================
CREATE OR REPLACE FUNCTION calculate_manual_review_sla()
RETURNS TRIGGER AS $$
DECLARE
    sla_interval INTERVAL;
BEGIN
    -- Calculate SLA based on priority
    sla_interval := CASE NEW.priority
        WHEN 'CRITICAL' THEN INTERVAL '15 minutes'
        WHEN 'HIGH' THEN INTERVAL '1 hour'
        WHEN 'MEDIUM' THEN INTERVAL '4 hours'
        WHEN 'LOW' THEN INTERVAL '24 hours'
        ELSE INTERVAL '4 hours'
    END;

    -- Set SLA deadline if not explicitly provided
    IF NEW.sla_due_at IS NULL THEN
        NEW.sla_due_at := NEW.created_at + sla_interval;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_calculate_manual_review_sla
BEFORE INSERT ON dlq_manual_review_queue
FOR EACH ROW
EXECUTE FUNCTION calculate_manual_review_sla();

COMMENT ON FUNCTION calculate_manual_review_sla() IS
    'Auto-calculates SLA deadline: CRITICAL=15min, HIGH=1hr, MEDIUM=4hrs, LOW=24hrs';


-- ============================================================================
-- FUNCTION: Clean up completed retry queue entries (scheduled job)
-- ============================================================================
CREATE OR REPLACE FUNCTION cleanup_completed_dlq_retries(retention_days INTEGER DEFAULT 7)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete completed or failed retries older than retention period
    DELETE FROM dlq_retry_queue
    WHERE status IN ('SUCCESS', 'FAILED', 'CANCELLED')
    AND updated_at < (CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL);

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_completed_dlq_retries(INTEGER) IS
    'Removes completed/failed retry records older than retention period (default 7 days). Run via scheduled job.';


-- ============================================================================
-- FUNCTION: Mark SLA breaches (scheduled job - run every minute)
-- ============================================================================
CREATE OR REPLACE FUNCTION mark_sla_breaches()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    -- Mark records as SLA breached if past due date
    UPDATE dlq_manual_review_queue
    SET sla_breached = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE status IN ('PENDING', 'IN_REVIEW')
    AND sla_due_at < CURRENT_TIMESTAMP
    AND sla_breached = FALSE;

    GET DIAGNOSTICS updated_count = ROW_COUNT;

    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION mark_sla_breaches() IS
    'Marks manual review records as SLA breached when past due date. Run every minute via scheduler.';


-- ============================================================================
-- VIEW: DLQ Dashboard Summary
-- ============================================================================
CREATE OR REPLACE VIEW v_dlq_dashboard_summary AS
SELECT
    -- Retry Queue Stats
    (SELECT COUNT(*) FROM dlq_retry_queue WHERE status = 'PENDING') as pending_retries,
    (SELECT COUNT(*) FROM dlq_retry_queue WHERE status = 'RETRYING') as active_retries,
    (SELECT COUNT(*) FROM dlq_retry_queue WHERE status = 'FAILED') as failed_retries,

    -- Manual Review Stats
    (SELECT COUNT(*) FROM dlq_manual_review_queue WHERE status = 'PENDING') as pending_reviews,
    (SELECT COUNT(*) FROM dlq_manual_review_queue WHERE status = 'IN_REVIEW') as active_reviews,
    (SELECT COUNT(*) FROM dlq_manual_review_queue WHERE status = 'PENDING' AND priority = 'CRITICAL') as critical_reviews,
    (SELECT COUNT(*) FROM dlq_manual_review_queue WHERE sla_breached = TRUE AND status IN ('PENDING', 'IN_REVIEW')) as sla_breached_reviews,

    -- Permanent Failures Stats
    (SELECT COUNT(*) FROM dlq_permanent_failures WHERE recorded_at > CURRENT_TIMESTAMP - INTERVAL '24 hours') as permanent_failures_24h,
    (SELECT COUNT(*) FROM dlq_permanent_failures WHERE recorded_at > CURRENT_TIMESTAMP - INTERVAL '7 days') as permanent_failures_7d,
    (SELECT COUNT(*) FROM dlq_permanent_failures WHERE compliance_reviewed = FALSE) as unreviewed_failures,
    (SELECT COUNT(*) FROM dlq_permanent_failures WHERE remediation_required = TRUE AND remediation_status IS NULL) as pending_remediation,

    -- Overall Health
    CASE
        WHEN (SELECT COUNT(*) FROM dlq_manual_review_queue WHERE priority = 'CRITICAL' AND status = 'PENDING') > 0
        THEN 'CRITICAL'
        WHEN (SELECT COUNT(*) FROM dlq_manual_review_queue WHERE sla_breached = TRUE) > 10
        THEN 'DEGRADED'
        WHEN (SELECT COUNT(*) FROM dlq_retry_queue WHERE status = 'PENDING') > 1000
        THEN 'WARNING'
        ELSE 'HEALTHY'
    END as overall_status,

    CURRENT_TIMESTAMP as snapshot_time;

COMMENT ON VIEW v_dlq_dashboard_summary IS
    'Real-time DLQ system health dashboard. Refresh for current metrics.';


-- ============================================================================
-- GRANTS (Adjust based on your user/role structure)
-- ============================================================================
-- Application service account (read/write)
GRANT SELECT, INSERT, UPDATE ON TABLE dlq_retry_queue TO waqiti;
GRANT SELECT, INSERT, UPDATE ON TABLE dlq_manual_review_queue TO waqiti;
GRANT SELECT, INSERT ON TABLE dlq_permanent_failures TO waqiti;  -- No UPDATE/DELETE allowed
GRANT SELECT ON TABLE v_dlq_dashboard_summary TO waqiti;

-- Operations/Admin account (full access except permanent_failures delete)
-- GRANT SELECT, INSERT, UPDATE ON TABLE dlq_retry_queue TO waqiti_admin;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE dlq_manual_review_queue TO waqiti_admin;
-- GRANT SELECT, INSERT, UPDATE ON TABLE dlq_permanent_failures TO waqiti_admin;  -- No DELETE
-- GRANT SELECT ON TABLE v_dlq_dashboard_summary TO waqiti_admin;


-- ============================================================================
-- SAMPLE DATA (for testing - remove in production)
-- ============================================================================
-- Uncomment for local development/testing
-- INSERT INTO dlq_retry_queue (original_topic, original_partition, original_offset, payload, exception_message, failed_at, next_retry_at, handler_name, backoff_delay_ms)
-- VALUES ('account-created-events', 0, 12345, '{"accountId":"***MASKED***"}', 'Connection timeout', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '5 seconds', 'AccountCreatedEventsConsumerDlqHandler', 5000);


-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
DO $$
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE '╔═══════════════════════════════════════════════════════════════════╗';
    RAISE NOTICE '║  V400 DLQ TRACKING TABLES MIGRATION COMPLETED SUCCESSFULLY        ║';
    RAISE NOTICE '╠═══════════════════════════════════════════════════════════════════╣';
    RAISE NOTICE '║  ✓ dlq_retry_queue created (exponential backoff retry)            ║';
    RAISE NOTICE '║  ✓ dlq_manual_review_queue created (ops team review)              ║';
    RAISE NOTICE '║  ✓ dlq_permanent_failures created (7-year audit retention)        ║';
    RAISE NOTICE '║  ✓ Indexes created for performance                                ║';
    RAISE NOTICE '║  ✓ Triggers created (auto-update, SLA, compliance protection)     ║';
    RAISE NOTICE '║  ✓ Views created (dashboard summary)                              ║';
    RAISE NOTICE '║  ✓ Functions created (cleanup, SLA marking)                       ║';
    RAISE NOTICE '║                                                                   ║';
    RAISE NOTICE '║  ⚠️  COMPLIANCE NOTE:                                              ║';
    RAISE NOTICE '║  Permanent failure records CANNOT be deleted (7-year retention)  ║';
    RAISE NOTICE '║  Trigger will prevent any DELETE operations on that table        ║';
    RAISE NOTICE '║                                                                   ║';
    RAISE NOTICE '║  NEXT STEPS:                                                      ║';
    RAISE NOTICE '║  1. Create DLQ repository classes (JPA)                           ║';
    RAISE NOTICE '║  2. Implement BaseDlqHandler abstract class                       ║';
    RAISE NOTICE '║  3. Refactor 47 DLQ handler implementations                       ║';
    RAISE NOTICE '║  4. Create scheduled job for cleanup_completed_dlq_retries()      ║';
    RAISE NOTICE '║  5. Create scheduled job for mark_sla_breaches()                  ║';
    RAISE NOTICE '║  6. Configure monitoring dashboard for v_dlq_dashboard_summary    ║';
    RAISE NOTICE '╚═══════════════════════════════════════════════════════════════════╝';
    RAISE NOTICE '';
END $$;
