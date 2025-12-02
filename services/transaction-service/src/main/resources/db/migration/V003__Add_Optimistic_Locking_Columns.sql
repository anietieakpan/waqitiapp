-- =====================================================
-- Flyway Migration: Add Optimistic Locking (@Version)
-- Version: V003
-- Description: Add version columns to Receipt and ReceiptAuditLog for optimistic locking
-- Author: System Architect
-- Date: 2025-01-27
-- =====================================================

-- Add opt_lock_version column to receipts table
ALTER TABLE receipts
ADD COLUMN IF NOT EXISTS opt_lock_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_receipts_opt_lock_version ON receipts(opt_lock_version);

COMMENT ON COLUMN receipts.opt_lock_version IS 'Optimistic locking version - prevents concurrent modifications to receipt records';

-- Add opt_lock_version column to receipt_audit_logs table
ALTER TABLE receipt_audit_logs
ADD COLUMN IF NOT EXISTS opt_lock_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_receipt_audit_opt_lock ON receipt_audit_logs(opt_lock_version);

COMMENT ON COLUMN receipt_audit_logs.opt_lock_version IS 'Optimistic locking version - ensures audit log integrity';

-- =====================================================
-- Verification Query
-- =====================================================

DO $$
DECLARE
    receipt_count INTEGER;
    audit_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO receipt_count FROM receipts;
    SELECT COUNT(*) INTO audit_count FROM receipt_audit_logs;
    
    RAISE NOTICE 'Migration V003 completed successfully:';
    RAISE NOTICE '  - receipts: % records, opt_lock_version column added', receipt_count;
    RAISE NOTICE '  - receipt_audit_logs: % records, opt_lock_version column added', audit_count;
    RAISE NOTICE 'Optimistic locking is now enabled for all transaction-service entities';
END $$;