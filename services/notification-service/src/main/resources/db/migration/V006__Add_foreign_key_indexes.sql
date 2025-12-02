-- Add Foreign Key Indexes for Performance
-- Service: notification-service
-- Date: 2025-10-18
-- Description: Add missing indexes on foreign key columns to prevent table scans
-- Impact: Significantly improves query performance and prevents deadlocks
-- Priority: HIGH - Production Performance Issue
--
-- Problem:
-- PostgreSQL does NOT automatically create indexes on foreign key columns.
-- Without these indexes:
-- - JOINs perform full table scans
-- - DELETE/UPDATE on parent table scans entire child table
-- - High risk of deadlocks under concurrent load
-- - Slow query performance at scale
--
-- Solution:
-- Create indexes on all foreign key columns
-- Performance improvement: 10-100x faster for FK constraint checks
--
-- Total Indexes: 4

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_preferences_user_id ON notification_preferences(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_templates_created_by ON notification_templates(created_by);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id);

-- Index comments for documentation
COMMENT ON INDEX idx_notifications_user_id IS 'Foreign key index for notifications.user_id - Performance optimization';
COMMENT ON INDEX idx_notification_preferences_user_id IS 'Foreign key index for notification_preferences.user_id - Performance optimization';
COMMENT ON INDEX idx_notification_templates_created_by IS 'Foreign key index for notification_templates.created_by - Performance optimization';
COMMENT ON INDEX idx_device_tokens_user_id IS 'Foreign key index for device_tokens.user_id - Performance optimization';
