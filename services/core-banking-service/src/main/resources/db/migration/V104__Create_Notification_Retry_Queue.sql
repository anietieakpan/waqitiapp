-- V104: Create Notification Retry Queue Table
-- Purpose: Store failed notifications for retry processing with exponential backoff
-- Features: Database-backed persistent queue, auto-retry scheduling, cleanup
-- Author: Principal Software Engineer
-- Date: 2025-11-20

-- ============================================================================
-- Notification Retry Queue Table
-- ============================================================================

CREATE TABLE IF NOT EXISTS notification_retry_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_type VARCHAR(50) NOT NULL,
    recipient_id VARCHAR(255),
    recipient_email VARCHAR(255),
    recipient_phone VARCHAR(50),
    subject VARCHAR(500),
    message TEXT NOT NULL,
    template_id VARCHAR(100),
    template_data TEXT, -- JSON
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_attempts INTEGER NOT NULL DEFAULT 6,
    next_retry_at TIMESTAMP,
    last_retry_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    related_entity_type VARCHAR(50),
    related_entity_id UUID,

    -- Constraints
    CONSTRAINT chk_notification_type CHECK (notification_type IN ('EMAIL', 'SMS', 'PUSH_NOTIFICATION', 'IN_APP')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'RETRYING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0 AND retry_count <= max_retry_attempts)
);

-- ============================================================================
-- Indexes for Query Performance
-- ============================================================================

-- Index for retry worker query (most critical)
CREATE INDEX idx_notification_retry_queue_status_next_retry
ON notification_retry_queue(status, next_retry_at)
WHERE status IN ('PENDING', 'RETRYING');

-- Index for created date (cleanup queries)
CREATE INDEX idx_notification_retry_queue_created_at
ON notification_retry_queue(created_at);

-- Index for notification type analytics
CREATE INDEX idx_notification_retry_queue_type_status
ON notification_retry_queue(notification_type, status);

-- Index for related entity lookups
CREATE INDEX idx_notification_retry_queue_related_entity
ON notification_retry_queue(related_entity_type, related_entity_id)
WHERE related_entity_id IS NOT NULL;

-- ============================================================================
-- Comments for Documentation
-- ============================================================================

COMMENT ON TABLE notification_retry_queue IS
'Persistent queue for failed notifications with exponential backoff retry strategy.
Stores notifications when NotificationService is unavailable.
Scheduled worker processes queue every minute.
Old records auto-deleted after 7 days.';

COMMENT ON COLUMN notification_retry_queue.notification_type IS
'Type of notification: EMAIL, SMS, PUSH_NOTIFICATION, IN_APP';

COMMENT ON COLUMN notification_retry_queue.status IS
'Retry status: PENDING (initial), RETRYING (in progress), COMPLETED (success), FAILED (max retries exceeded), CANCELLED (manually cancelled)';

COMMENT ON COLUMN notification_retry_queue.retry_count IS
'Current retry attempt number. Starts at 0.';

COMMENT ON COLUMN notification_retry_queue.next_retry_at IS
'Scheduled time for next retry attempt using exponential backoff: 1min, 5min, 30min, 2hr, 6hr, 24hr';

COMMENT ON COLUMN notification_retry_queue.template_data IS
'JSON data for notification templates (e.g., transaction details, account info)';

-- ============================================================================
-- Sample Queries for Monitoring
-- ============================================================================

-- Find notifications ready for retry:
-- SELECT * FROM notification_retry_queue
-- WHERE status IN ('PENDING', 'RETRYING')
-- AND next_retry_at <= CURRENT_TIMESTAMP
-- ORDER BY next_retry_at ASC;

-- Count pending notifications by type:
-- SELECT notification_type, COUNT(*)
-- FROM notification_retry_queue
-- WHERE status = 'PENDING'
-- GROUP BY notification_type;

-- Find failed notifications (require manual investigation):
-- SELECT * FROM notification_retry_queue
-- WHERE status = 'FAILED'
-- ORDER BY created_at DESC;

-- ============================================================================
-- Validation Query
-- ============================================================================

-- Verify table created successfully
SELECT
    table_name,
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_name = 'notification_retry_queue') as column_count,
    (SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE table_name = 'notification_retry_queue' AND constraint_type = 'CHECK') as constraint_count
FROM information_schema.tables
WHERE table_name = 'notification_retry_queue';

-- Expected result: 1 row with column_count=18, constraint_count=3
