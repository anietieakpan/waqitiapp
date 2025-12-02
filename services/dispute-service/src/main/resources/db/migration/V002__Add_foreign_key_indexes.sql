-- Add Foreign Key Indexes for Performance
-- Service: dispute-service
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
-- Total Indexes: 8

CREATE INDEX IF NOT EXISTS idx_disputes_transaction_id ON disputes(transaction_id);
CREATE INDEX IF NOT EXISTS idx_disputes_raised_by_user_id ON disputes(raised_by_user_id);
CREATE INDEX IF NOT EXISTS idx_disputes_assigned_to ON disputes(assigned_to);
CREATE INDEX IF NOT EXISTS idx_dispute_messages_dispute_id ON dispute_messages(dispute_id);
CREATE INDEX IF NOT EXISTS idx_dispute_messages_sender_id ON dispute_messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_dispute_evidence_dispute_id ON dispute_evidence(dispute_id);
CREATE INDEX IF NOT EXISTS idx_dispute_resolution_dispute_id ON dispute_resolution(dispute_id);
CREATE INDEX IF NOT EXISTS idx_dispute_resolution_resolved_by ON dispute_resolution(resolved_by);

-- Index comments for documentation
COMMENT ON INDEX idx_disputes_transaction_id IS 'Foreign key index for disputes.transaction_id - Performance optimization';
COMMENT ON INDEX idx_disputes_raised_by_user_id IS 'Foreign key index for disputes.raised_by_user_id - Performance optimization';
COMMENT ON INDEX idx_disputes_assigned_to IS 'Foreign key index for disputes.assigned_to - Performance optimization';
COMMENT ON INDEX idx_dispute_messages_dispute_id IS 'Foreign key index for dispute_messages.dispute_id - Performance optimization';
COMMENT ON INDEX idx_dispute_messages_sender_id IS 'Foreign key index for dispute_messages.sender_id - Performance optimization';
COMMENT ON INDEX idx_dispute_evidence_dispute_id IS 'Foreign key index for dispute_evidence.dispute_id - Performance optimization';
COMMENT ON INDEX idx_dispute_resolution_dispute_id IS 'Foreign key index for dispute_resolution.dispute_id - Performance optimization';
COMMENT ON INDEX idx_dispute_resolution_resolved_by IS 'Foreign key index for dispute_resolution.resolved_by - Performance optimization';
