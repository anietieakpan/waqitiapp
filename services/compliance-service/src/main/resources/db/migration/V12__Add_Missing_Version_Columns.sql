-- ============================================================================
-- V12 Migration: Add Missing Version Columns for Optimistic Locking
-- ============================================================================
--
-- CRITICAL FIX: Add version columns to tables that use @Version in entities
-- but are missing the column in the database schema.
--
-- This prevents OptimisticLockException runtime failures and ensures
-- proper concurrent update handling in a multi-instance deployment.
--
-- Tables Fixed:
-- 1. compliance_transactions (Entity has @Version, migration missing column)
-- 2. compliance_alerts (Concurrent updates need optimistic locking)
-- 3. compliance_rules (Concurrent rule updates need versioning)
--
-- REGULATORY IMPACT:
-- - Prevents duplicate SAR/CTR filings from concurrent processing
-- - Ensures data integrity for compliance alerts
-- - Prevents race conditions in compliance rule updates
--
-- Author: Waqiti Engineering Team
-- Date: 2025-11-19
-- JIRA: COMP-1234 (Production Readiness - Database Schema Fixes)
-- ============================================================================

-- Add version column to compliance_transactions
-- This table processes financial transactions and requires optimistic locking
ALTER TABLE compliance_transactions
ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

-- Add index on version for query performance
CREATE INDEX idx_compliance_transactions_version
ON compliance_transactions(version);

-- Add comment for documentation
COMMENT ON COLUMN compliance_transactions.version IS
'Optimistic locking version for concurrent transaction processing. Incremented on each update.';

-- ============================================================================

-- Add version column to compliance_alerts
-- Alerts are frequently updated by multiple processors
ALTER TABLE compliance_alerts
ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

-- Add index on version
CREATE INDEX idx_compliance_alerts_version
ON compliance_alerts(version);

-- Add comment
COMMENT ON COLUMN compliance_alerts.version IS
'Optimistic locking version. Prevents lost updates when multiple compliance officers review same alert.';

-- ============================================================================

-- Add version column to compliance_rules
-- Rules may be updated by compliance officers while being executed
ALTER TABLE compliance_rules
ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

-- Add index on version
CREATE INDEX idx_compliance_rules_version
ON compliance_rules(version);

-- Add comment
COMMENT ON COLUMN compliance_rules.version IS
'Optimistic locking version. Ensures rule consistency during concurrent updates.';

-- ============================================================================

-- Verification queries (commented out - for manual verification)
-- SELECT COUNT(*) FROM compliance_transactions WHERE version IS NULL;
-- SELECT COUNT(*) FROM compliance_alerts WHERE version IS NULL;
-- SELECT COUNT(*) FROM compliance_rules WHERE version IS NULL;
-- All should return 0

-- ============================================================================
-- ROLLBACK SCRIPT (if needed for emergency rollback)
-- ============================================================================
-- ALTER TABLE compliance_transactions DROP COLUMN IF EXISTS version;
-- ALTER TABLE compliance_alerts DROP COLUMN IF EXISTS version;
-- ALTER TABLE compliance_rules DROP COLUMN IF EXISTS version;
-- DROP INDEX IF EXISTS idx_compliance_transactions_version;
-- DROP INDEX IF EXISTS idx_compliance_alerts_version;
-- DROP INDEX IF EXISTS idx_compliance_rules_version;
