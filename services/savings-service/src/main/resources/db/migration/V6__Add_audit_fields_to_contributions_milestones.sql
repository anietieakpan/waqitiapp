--
-- Migration V6: Add missing audit and version fields to SavingsContribution and Milestone entities
-- Author: Waqiti Development Team
-- Date: 2025-11-19
-- Purpose: Add created_by, modified_by, and version fields for audit compliance and optimistic locking
--

-- Add audit fields to savings_contributions table
ALTER TABLE savings_contributions
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add audit fields to savings_milestones table
ALTER TABLE savings_milestones
    ADD COLUMN IF NOT EXISTS user_id UUID,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS modified_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_custom BOOLEAN DEFAULT false;

-- Update existing records to have system as creator
UPDATE savings_contributions SET created_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE savings_milestones SET created_by = 'SYSTEM' WHERE created_by IS NULL;

-- Add indexes for audit queries
CREATE INDEX IF NOT EXISTS idx_savings_contributions_created_by ON savings_contributions(created_by);
CREATE INDEX IF NOT EXISTS idx_savings_milestones_created_by ON savings_milestones(created_by);
CREATE INDEX IF NOT EXISTS idx_savings_milestones_user ON savings_milestones(user_id);

-- Add comments for audit fields
COMMENT ON COLUMN savings_contributions.created_by IS 'User who created this contribution';
COMMENT ON COLUMN savings_contributions.modified_by IS 'User who last modified this contribution';
COMMENT ON COLUMN savings_contributions.version IS 'Optimistic locking version - prevents concurrent updates';

COMMENT ON COLUMN savings_milestones.user_id IS 'User who owns the goal this milestone belongs to';
COMMENT ON COLUMN savings_milestones.created_by IS 'User who created this milestone';
COMMENT ON COLUMN savings_milestones.modified_by IS 'User who last modified this milestone';
COMMENT ON COLUMN savings_milestones.version IS 'Optimistic locking version - prevents concurrent updates';
COMMENT ON COLUMN savings_milestones.is_custom IS 'True if user-created milestone, false if system-generated';

-- Note: SavingsGoal and AutoSaveRule audit fields were already added in V002
-- This migration completes the audit trail across all entities
