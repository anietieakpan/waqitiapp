-- ============================================================================
-- V15 Migration: Create Manual Filing Queue Tables
-- ============================================================================
--
-- CRITICAL REGULATORY REQUIREMENT:
-- When automated SAR/CTR filing to FinCEN fails, reports MUST be queued
-- for manual filing by the compliance team. This prevents regulatory
-- violations from missed filing deadlines.
--
-- SAR Filing Deadline: Within 30 days of detection
-- CTR Filing Deadline: Within 15 days of transaction
-- FBAR Filing Deadline: April 15 (with extension to October 15)
--
-- FEATURES:
-- - Priority-based queue management
-- - Retry tracking and failure history
-- - Compliance team assignment
-- - Deadline tracking and alerts
-- - Full audit trail
--
-- Author: Waqiti Engineering Team
-- Date: 2025-11-21
-- JIRA: COMP-1245 (SAR Manual Filing Queue Implementation)
-- ============================================================================

-- ============================================================================
-- MANUAL FILING QUEUE (Main Table)
-- ============================================================================

CREATE TABLE manual_filing_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Report identification
    report_type VARCHAR(20) NOT NULL, -- SAR, CTR, FBAR
    report_reference_id VARCHAR(100) NOT NULL, -- Internal reference
    original_report_id UUID, -- Link to original SAR/CTR record

    -- Filing details
    filing_data JSONB NOT NULL, -- Complete filing data in JSON
    filing_format VARCHAR(20) DEFAULT 'XML', -- XML, PDF, etc.

    -- Queue management
    queue_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    -- PENDING, ASSIGNED, IN_PROGRESS, FILED, FAILED, ESCALATED, CANCELLED
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    -- CRITICAL, HIGH, NORMAL, LOW

    -- Deadline tracking
    detection_date TIMESTAMP NOT NULL, -- When suspicious activity was detected
    filing_deadline TIMESTAMP NOT NULL, -- Regulatory deadline
    days_until_deadline INTEGER GENERATED ALWAYS AS (
        EXTRACT(DAY FROM filing_deadline - CURRENT_TIMESTAMP)
    ) STORED,
    is_overdue BOOLEAN GENERATED ALWAYS AS (
        filing_deadline < CURRENT_TIMESTAMP AND queue_status NOT IN ('FILED', 'CANCELLED')
    ) STORED,

    -- Assignment
    assigned_to VARCHAR(255), -- User ID of assigned compliance officer
    assigned_at TIMESTAMP,
    assigned_by VARCHAR(255),

    -- Retry tracking
    auto_filing_attempts INTEGER NOT NULL DEFAULT 0,
    last_auto_attempt_at TIMESTAMP,
    last_auto_error TEXT,
    max_auto_retries INTEGER DEFAULT 3,

    -- Manual filing tracking
    manual_filing_attempts INTEGER DEFAULT 0,
    last_manual_attempt_at TIMESTAMP,
    last_manual_error TEXT,

    -- Filing completion
    filed_at TIMESTAMP,
    filed_by VARCHAR(255),
    fincen_confirmation_number VARCHAR(100),
    fincen_tracking_id VARCHAR(100),
    filing_receipt_url VARCHAR(500),

    -- Escalation
    escalation_level INTEGER DEFAULT 0, -- 0=none, 1=supervisor, 2=manager, 3=compliance officer
    escalated_at TIMESTAMP,
    escalated_to VARCHAR(255),
    escalation_reason TEXT,

    -- Notifications
    notification_sent BOOLEAN DEFAULT false,
    notification_sent_at TIMESTAMP,
    reminder_count INTEGER DEFAULT 0,
    last_reminder_at TIMESTAMP,

    -- Source information
    source_system VARCHAR(50), -- Which system generated this
    source_transaction_ids TEXT, -- Comma-separated transaction IDs

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255) DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT uk_manual_filing_report_ref UNIQUE (report_type, report_reference_id)
);

-- Indexes for manual_filing_queue
CREATE INDEX idx_manual_filing_status ON manual_filing_queue(queue_status);
CREATE INDEX idx_manual_filing_priority ON manual_filing_queue(priority);
CREATE INDEX idx_manual_filing_deadline ON manual_filing_queue(filing_deadline);
CREATE INDEX idx_manual_filing_overdue ON manual_filing_queue(is_overdue) WHERE is_overdue = true;
CREATE INDEX idx_manual_filing_assigned ON manual_filing_queue(assigned_to) WHERE assigned_to IS NOT NULL;
CREATE INDEX idx_manual_filing_type ON manual_filing_queue(report_type);
CREATE INDEX idx_manual_filing_escalation ON manual_filing_queue(escalation_level) WHERE escalation_level > 0;
CREATE INDEX idx_manual_filing_created ON manual_filing_queue(created_at DESC);
CREATE INDEX idx_manual_filing_pending ON manual_filing_queue(queue_status, priority, filing_deadline)
    WHERE queue_status IN ('PENDING', 'ASSIGNED');

-- Full-text search on filing data
CREATE INDEX idx_manual_filing_data ON manual_filing_queue USING gin(filing_data);

-- Comments
COMMENT ON TABLE manual_filing_queue IS 'Queue for SAR/CTR/FBAR reports requiring manual filing when automation fails';
COMMENT ON COLUMN manual_filing_queue.filing_deadline IS 'Regulatory deadline for filing (SAR: 30 days, CTR: 15 days)';
COMMENT ON COLUMN manual_filing_queue.escalation_level IS '0=none, 1=supervisor, 2=manager, 3=chief compliance officer';
COMMENT ON COLUMN manual_filing_queue.priority IS 'CRITICAL (overdue), HIGH (<7 days), NORMAL (7-14 days), LOW (>14 days)';

-- ============================================================================
-- FILING QUEUE HISTORY (Audit Trail)
-- ============================================================================

CREATE TABLE manual_filing_queue_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_item_id UUID NOT NULL,

    -- Action details
    action VARCHAR(50) NOT NULL,
    -- CREATED, ASSIGNED, REASSIGNED, ATTEMPTED, FILED, FAILED, ESCALATED, CANCELLED, REMINDER_SENT
    action_by VARCHAR(255),
    action_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- State change
    old_status VARCHAR(30),
    new_status VARCHAR(30),
    old_priority VARCHAR(10),
    new_priority VARCHAR(10),
    old_assigned_to VARCHAR(255),
    new_assigned_to VARCHAR(255),

    -- Details
    notes TEXT,
    error_details TEXT,
    metadata JSONB,

    CONSTRAINT fk_queue_history_item
        FOREIGN KEY (queue_item_id)
        REFERENCES manual_filing_queue(id)
        ON DELETE CASCADE
);

-- Indexes for history
CREATE INDEX idx_filing_history_item ON manual_filing_queue_history(queue_item_id);
CREATE INDEX idx_filing_history_action ON manual_filing_queue_history(action);
CREATE INDEX idx_filing_history_time ON manual_filing_queue_history(action_at DESC);
CREATE INDEX idx_filing_history_user ON manual_filing_queue_history(action_by);

-- Comments
COMMENT ON TABLE manual_filing_queue_history IS 'Complete audit trail for all queue item state changes';

-- ============================================================================
-- FILING QUEUE ATTACHMENTS
-- ============================================================================

CREATE TABLE manual_filing_queue_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_item_id UUID NOT NULL,

    -- Attachment details
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50), -- PDF, XML, IMAGE, etc.
    file_size_bytes BIGINT,
    content_type VARCHAR(100),

    -- Storage
    storage_path VARCHAR(500), -- S3 path or local path
    storage_type VARCHAR(20) DEFAULT 'S3', -- S3, LOCAL, ENCRYPTED
    checksum VARCHAR(64), -- SHA-256

    -- Metadata
    description TEXT,
    uploaded_by VARCHAR(255),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attachment_queue_item
        FOREIGN KEY (queue_item_id)
        REFERENCES manual_filing_queue(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_filing_attachments_item ON manual_filing_queue_attachments(queue_item_id);
CREATE INDEX idx_filing_attachments_type ON manual_filing_queue_attachments(file_type);

-- Comments
COMMENT ON TABLE manual_filing_queue_attachments IS 'Supporting documents for manual filing (receipts, confirmations, etc.)';

-- ============================================================================
-- FILING QUEUE COMMENTS
-- ============================================================================

CREATE TABLE manual_filing_queue_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_item_id UUID NOT NULL,

    -- Comment details
    comment_text TEXT NOT NULL,
    comment_type VARCHAR(20) DEFAULT 'NOTE', -- NOTE, ISSUE, RESOLUTION, INTERNAL

    -- Author
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Visibility
    is_internal BOOLEAN DEFAULT true, -- Internal comments not shown in reports

    CONSTRAINT fk_comment_queue_item
        FOREIGN KEY (queue_item_id)
        REFERENCES manual_filing_queue(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_filing_comments_item ON manual_filing_queue_comments(queue_item_id);
CREATE INDEX idx_filing_comments_type ON manual_filing_queue_comments(comment_type);
CREATE INDEX idx_filing_comments_created ON manual_filing_queue_comments(created_at DESC);

-- Comments
COMMENT ON TABLE manual_filing_queue_comments IS 'Notes and comments from compliance team during manual filing';

-- ============================================================================
-- QUEUE STATISTICS VIEW
-- ============================================================================

CREATE OR REPLACE VIEW v_manual_filing_queue_stats AS
SELECT
    report_type,
    queue_status,
    priority,
    COUNT(*) as count,
    COUNT(*) FILTER (WHERE is_overdue = true) as overdue_count,
    AVG(EXTRACT(DAY FROM filing_deadline - CURRENT_TIMESTAMP)) as avg_days_to_deadline,
    MIN(filing_deadline) as earliest_deadline
FROM manual_filing_queue
WHERE queue_status NOT IN ('FILED', 'CANCELLED')
GROUP BY report_type, queue_status, priority;

-- Comments
COMMENT ON VIEW v_manual_filing_queue_stats IS 'Real-time statistics for queue dashboard';

-- ============================================================================
-- OVERDUE ITEMS VIEW
-- ============================================================================

CREATE OR REPLACE VIEW v_overdue_filings AS
SELECT
    mfq.*,
    EXTRACT(DAY FROM CURRENT_TIMESTAMP - filing_deadline) as days_overdue
FROM manual_filing_queue mfq
WHERE is_overdue = true
ORDER BY filing_deadline ASC;

-- Comments
COMMENT ON VIEW v_overdue_filings IS 'All overdue filings requiring immediate attention';

-- ============================================================================
-- TRIGGER: Auto-update updated_at timestamp
-- ============================================================================

CREATE OR REPLACE FUNCTION update_manual_filing_queue_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_manual_filing_queue_updated
    BEFORE UPDATE ON manual_filing_queue
    FOR EACH ROW
    EXECUTE FUNCTION update_manual_filing_queue_timestamp();

-- ============================================================================
-- TRIGGER: Auto-calculate priority based on deadline
-- ============================================================================

CREATE OR REPLACE FUNCTION calculate_filing_priority()
RETURNS TRIGGER AS $$
DECLARE
    days_remaining INTEGER;
BEGIN
    days_remaining := EXTRACT(DAY FROM NEW.filing_deadline - CURRENT_TIMESTAMP);

    IF days_remaining < 0 THEN
        NEW.priority := 'CRITICAL';
    ELSIF days_remaining <= 7 THEN
        NEW.priority := 'HIGH';
    ELSIF days_remaining <= 14 THEN
        NEW.priority := 'NORMAL';
    ELSE
        NEW.priority := 'LOW';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_calculate_priority
    BEFORE INSERT OR UPDATE OF filing_deadline ON manual_filing_queue
    FOR EACH ROW
    WHEN (NEW.priority IS NULL OR NEW.priority = 'NORMAL')
    EXECUTE FUNCTION calculate_filing_priority();

-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Check pending items by priority:
-- SELECT priority, COUNT(*) FROM manual_filing_queue
-- WHERE queue_status = 'PENDING' GROUP BY priority;

-- Check overdue items:
-- SELECT * FROM v_overdue_filings;

-- Check queue statistics:
-- SELECT * FROM v_manual_filing_queue_stats;
