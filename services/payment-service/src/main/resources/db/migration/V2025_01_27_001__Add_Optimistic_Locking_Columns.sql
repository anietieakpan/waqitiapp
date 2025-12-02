-- =====================================================
-- Flyway Migration: Add Optimistic Locking (@Version)
-- Version: V2025_01_27_001
-- Description: Add version columns for optimistic locking to prevent race conditions
-- Author: System Architect
-- Date: 2025-01-27
-- =====================================================

-- Add version column to nfc_sessions table
ALTER TABLE nfc_sessions
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_nfc_sessions_version ON nfc_sessions(version);

COMMENT ON COLUMN nfc_sessions.version IS 'Optimistic locking version - prevents concurrent modification race conditions';

-- Add opt_lock_version column to webhook_delivery_attempts table
ALTER TABLE webhook_delivery_attempts
ADD COLUMN IF NOT EXISTS opt_lock_version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_webhook_delivery_opt_lock ON webhook_delivery_attempts(opt_lock_version);

COMMENT ON COLUMN webhook_delivery_attempts.opt_lock_version IS 'Optimistic locking version for webhook retry safety';

-- Add version column to journal_entries table
ALTER TABLE journal_entries
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_journal_entries_version ON journal_entries(version);

COMMENT ON COLUMN journal_entries.version IS 'Optimistic locking version - critical for double-entry bookkeeping integrity';

-- Add version column to scheduled_payments table
ALTER TABLE scheduled_payments
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_scheduled_payments_version ON scheduled_payments(version);

COMMENT ON COLUMN scheduled_payments.version IS 'Optimistic locking version - prevents duplicate execution of recurring payments';

-- Add version column to payment_audit_trail table
ALTER TABLE payment_audit_trail
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_payment_audit_version ON payment_audit_trail(version);

COMMENT ON COLUMN payment_audit_trail.version IS 'Optimistic locking version - ensures audit log integrity';

-- =====================================================
-- Verification Queries
-- =====================================================

-- Count records in each table (for verification)
DO $$
DECLARE
    nfc_count INTEGER;
    webhook_count INTEGER;
    journal_count INTEGER;
    scheduled_count INTEGER;
    audit_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO nfc_count FROM nfc_sessions;
    SELECT COUNT(*) INTO webhook_count FROM webhook_delivery_attempts;
    SELECT COUNT(*) INTO journal_count FROM journal_entries;
    SELECT COUNT(*) INTO scheduled_count FROM scheduled_payments;
    SELECT COUNT(*) INTO audit_count FROM payment_audit_trail;
    
    RAISE NOTICE 'Migration V2025_01_27_001 completed successfully:';
    RAISE NOTICE '  - nfc_sessions: % records, version column added', nfc_count;
    RAISE NOTICE '  - webhook_delivery_attempts: % records, opt_lock_version column added', webhook_count;
    RAISE NOTICE '  - journal_entries: % records, version column added', journal_count;
    RAISE NOTICE '  - scheduled_payments: % records, version column added', scheduled_count;
    RAISE NOTICE '  - payment_audit_trail: % records, version column added', audit_count;
END $$;