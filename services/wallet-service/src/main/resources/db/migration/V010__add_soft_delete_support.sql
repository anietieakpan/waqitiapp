-- Wallet Service - Add Soft Delete Support
-- Migration: V010
-- Created: 2025-10-11
-- Purpose: Add soft delete columns for GDPR compliance (right to be forgotten)
-- Impact: All major wallet-related tables

-- =============================================================================
-- PART 1: Add soft delete columns to wallets table
-- =============================================================================

-- Add columns if they don't exist
ALTER TABLE wallets
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted_by UUID,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

-- Create index for efficient queries on non-deleted records
CREATE INDEX IF NOT EXISTS idx_wallets_deleted ON wallets(deleted, deleted_at) WHERE deleted = false;

-- Add comment
COMMENT ON COLUMN wallets.deleted IS 'Soft delete flag for GDPR compliance';
COMMENT ON COLUMN wallets.deleted_at IS 'Timestamp when wallet was soft deleted';
COMMENT ON COLUMN wallets.deleted_by IS 'User ID who initiated the deletion';
COMMENT ON COLUMN wallets.deletion_reason IS 'Reason for deletion (e.g., GDPR request, account closure)';

-- =============================================================================
-- PART 2: Add soft delete to wallet_transactions table
-- =============================================================================

ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_deleted ON wallet_transactions(deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_active ON wallet_transactions(wallet_id, deleted, created_at DESC);

COMMENT ON COLUMN wallet_transactions.deleted IS 'Soft delete flag - transactions retained for audit trail';
COMMENT ON COLUMN wallet_transactions.deleted_at IS 'When transaction record was soft deleted';

-- =============================================================================
-- PART 3: Add soft delete to wallet_balances table
-- =============================================================================

ALTER TABLE wallet_balances
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_wallet_balances_deleted ON wallet_balances(deleted) WHERE deleted = false;

-- =============================================================================
-- PART 4: Add soft delete to wallet_limits table (if exists)
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wallet_limits') THEN
        ALTER TABLE wallet_limits
            ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL,
            ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

        CREATE INDEX IF NOT EXISTS idx_wallet_limits_deleted ON wallet_limits(deleted) WHERE deleted = false;

        RAISE NOTICE 'Soft delete added to wallet_limits';
    END IF;
END $$;

-- =============================================================================
-- PART 5: Add soft delete to wallet_audit_logs (if exists)
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wallet_audit_logs') THEN
        ALTER TABLE wallet_audit_logs
            ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE NOT NULL,
            ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

        CREATE INDEX IF NOT EXISTS idx_wallet_audit_archived ON wallet_audit_logs(archived) WHERE archived = false;

        COMMENT ON COLUMN wallet_audit_logs.archived IS 'Audit logs are never deleted, only archived';

        RAISE NOTICE 'Archival support added to wallet_audit_logs';
    END IF;
END $$;

-- =============================================================================
-- PART 6: Create soft delete functions
-- =============================================================================

-- Function to soft delete a wallet
CREATE OR REPLACE FUNCTION soft_delete_wallet(
    p_wallet_id UUID,
    p_deleted_by UUID,
    p_reason VARCHAR(500)
)
RETURNS void AS $$
BEGIN
    -- Soft delete the wallet
    UPDATE wallets
    SET deleted = true,
        deleted_at = CURRENT_TIMESTAMP,
        deleted_by = p_deleted_by,
        deletion_reason = p_reason,
        status = 'DELETED'
    WHERE id = p_wallet_id
    AND deleted = false;

    -- Soft delete associated balances
    UPDATE wallet_balances
    SET deleted = true,
        deleted_at = CURRENT_TIMESTAMP
    WHERE wallet_id = p_wallet_id
    AND deleted = false;

    -- Note: Transactions are NOT deleted (audit trail requirement)
    -- They are kept for regulatory compliance (7+ years)

    RAISE NOTICE 'Wallet % soft deleted by user %', p_wallet_id, p_deleted_by;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION soft_delete_wallet(UUID, UUID, VARCHAR) IS 'Soft deletes a wallet and associated data per GDPR requirements';

-- Function to restore a soft deleted wallet (within grace period)
CREATE OR REPLACE FUNCTION restore_wallet(p_wallet_id UUID)
RETURNS void AS $$
BEGIN
    -- Check if wallet was deleted within grace period (30 days)
    IF NOT EXISTS (
        SELECT 1 FROM wallets
        WHERE id = p_wallet_id
        AND deleted = true
        AND deleted_at > CURRENT_TIMESTAMP - INTERVAL '30 days'
    ) THEN
        RAISE EXCEPTION 'Wallet % cannot be restored (not found or grace period expired)', p_wallet_id;
    END IF;

    -- Restore wallet
    UPDATE wallets
    SET deleted = false,
        deleted_at = NULL,
        deleted_by = NULL,
        deletion_reason = NULL,
        status = 'ACTIVE'
    WHERE id = p_wallet_id;

    -- Restore balances
    UPDATE wallet_balances
    SET deleted = false,
        deleted_at = NULL
    WHERE wallet_id = p_wallet_id;

    RAISE NOTICE 'Wallet % restored successfully', p_wallet_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION restore_wallet(UUID) IS 'Restores a soft deleted wallet within 30-day grace period';

-- Function to permanently delete old soft-deleted wallets (GDPR archival)
CREATE OR REPLACE FUNCTION permanent_delete_old_wallets(retention_days INT DEFAULT 90)
RETURNS TABLE(deleted_wallet_id UUID, deleted_count INT) AS $$
DECLARE
    cutoff_date TIMESTAMP;
    wallet_record RECORD;
    total_deleted INT := 0;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL;

    FOR wallet_record IN
        SELECT id FROM wallets
        WHERE deleted = true
        AND deleted_at < cutoff_date
    LOOP
        -- Archive to cold storage would happen here in production
        -- For now, we'll just mark for permanent deletion

        -- Delete associated data
        DELETE FROM wallet_balances WHERE wallet_id = wallet_record.id;
        DELETE FROM wallet_transactions WHERE wallet_id = wallet_record.id AND deleted = true;

        -- Delete wallet
        DELETE FROM wallets WHERE id = wallet_record.id;

        total_deleted := total_deleted + 1;
        deleted_wallet_id := wallet_record.id;
        deleted_count := total_deleted;
        RETURN NEXT;
    END LOOP;

    RAISE NOTICE 'Permanently deleted % wallets older than % days', total_deleted, retention_days;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION permanent_delete_old_wallets(INT) IS 'Permanently deletes wallets soft-deleted more than N days ago (default 90). Use cautiously.';

-- =============================================================================
-- PART 7: Create views for active (non-deleted) records
-- =============================================================================

-- View for active wallets only
CREATE OR REPLACE VIEW active_wallets AS
SELECT *
FROM wallets
WHERE deleted = false;

COMMENT ON VIEW active_wallets IS 'View of non-deleted wallets for regular queries';

-- View for active wallet balances
CREATE OR REPLACE VIEW active_wallet_balances AS
SELECT *
FROM wallet_balances
WHERE deleted = false;

-- View for active wallet transactions
CREATE OR REPLACE VIEW active_wallet_transactions AS
SELECT *
FROM wallet_transactions
WHERE deleted = false;

-- =============================================================================
-- PART 8: Update existing queries to filter deleted records (examples)
-- =============================================================================

-- Example: Find wallet by user_id (excluding deleted)
-- Before: SELECT * FROM wallets WHERE user_id = ?
-- After:  SELECT * FROM wallets WHERE user_id = ? AND deleted = false
-- Or:     SELECT * FROM active_wallets WHERE user_id = ?

-- =============================================================================
-- PART 9: Add GDPR compliance tracking table
-- =============================================================================

CREATE TABLE IF NOT EXISTS wallet_gdpr_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL,
    user_id UUID NOT NULL,
    request_type VARCHAR(50) NOT NULL, -- 'DATA_EXPORT', 'RIGHT_TO_ERASURE', 'DATA_PORTABILITY'
    request_status VARCHAR(50) DEFAULT 'PENDING' NOT NULL, -- 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    completed_by UUID,
    request_data JSONB,
    fulfillment_data JSONB,
    notes TEXT,
    CONSTRAINT fk_gdpr_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

CREATE INDEX idx_gdpr_requests_wallet ON wallet_gdpr_requests(wallet_id);
CREATE INDEX idx_gdpr_requests_user ON wallet_gdpr_requests(user_id);
CREATE INDEX idx_gdpr_requests_status ON wallet_gdpr_requests(request_status);
CREATE INDEX idx_gdpr_requests_created ON wallet_gdpr_requests(requested_at DESC);

COMMENT ON TABLE wallet_gdpr_requests IS 'Tracks GDPR data subject requests for compliance auditing';

-- =============================================================================
-- PART 10: Add trigger to prevent accidental hard deletes
-- =============================================================================

CREATE OR REPLACE FUNCTION prevent_wallet_hard_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Direct DELETE on wallets table is not allowed. Use soft_delete_wallet() function instead.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger (can be disabled for administrative operations if needed)
-- DROP TRIGGER IF EXISTS no_wallet_hard_delete ON wallets;
-- CREATE TRIGGER no_wallet_hard_delete
--     BEFORE DELETE ON wallets
--     FOR EACH ROW
--     EXECUTE FUNCTION prevent_wallet_hard_delete();

-- Note: Trigger commented out by default to allow administrative operations
-- Enable in production after thorough testing

-- =============================================================================
-- VERIFICATION QUERIES
-- =============================================================================

-- Check soft delete columns added
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'wallets'
-- AND column_name IN ('deleted', 'deleted_at', 'deleted_by', 'deletion_reason');

-- Count active vs deleted wallets
-- SELECT
--     COUNT(*) FILTER (WHERE deleted = false) AS active_wallets,
--     COUNT(*) FILTER (WHERE deleted = true) AS deleted_wallets,
--     COUNT(*) AS total_wallets
-- FROM wallets;

-- Find wallets deleted in last 30 days (within grace period)
-- SELECT id, user_id, deleted_at, deletion_reason
-- FROM wallets
-- WHERE deleted = true
-- AND deleted_at > CURRENT_TIMESTAMP - INTERVAL '30 days'
-- ORDER BY deleted_at DESC;
