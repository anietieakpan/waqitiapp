-- =============================================================================
-- Migration: V10__Create_Wallet_Freeze_History_Table.sql
-- Description: Create comprehensive wallet freeze history table for audit trail
-- Author: Waqiti Engineering Team
-- Date: 2025-10-18
-- =============================================================================

-- Create wallet_freeze_history table
CREATE TABLE IF NOT EXISTS wallet_freeze_history (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL,
    user_id UUID NOT NULL,
    freeze_type VARCHAR(50) NOT NULL,
    freeze_reason VARCHAR(100) NOT NULL,
    previous_status VARCHAR(50) NOT NULL,
    frozen_by VARCHAR(255) NOT NULL,
    frozen_at TIMESTAMP NOT NULL,
    unfrozen_by VARCHAR(255),
    unfrozen_at TIMESTAMP,
    event_id UUID UNIQUE,  -- For idempotency
    correlation_id UUID,   -- For distributed tracing
    freeze_duration_seconds BIGINT,
    metadata TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT fk_wallet_freeze_wallet FOREIGN KEY (wallet_id)
        REFERENCES wallets(id) ON DELETE CASCADE,

    CONSTRAINT check_freeze_unfroze_order
        CHECK (unfrozen_at IS NULL OR unfrozen_at >= frozen_at),

    CONSTRAINT check_freeze_duration
        CHECK (freeze_duration_seconds IS NULL OR freeze_duration_seconds >= 0),

    CONSTRAINT check_freeze_type
        CHECK (freeze_type IN (
            'FRAUD_PREVENTION',
            'COMPLIANCE',
            'SECURITY',
            'ADMINISTRATIVE',
            'COURT_ORDER',
            'SUSPICIOUS_ACTIVITY',
            'CUSTOMER_REQUESTED',
            'KYC_PENDING',
            'SANCTIONS',
            'DISPUTE_INVESTIGATION',
            'SYSTEM_MAINTENANCE'
        )),

    CONSTRAINT check_previous_status
        CHECK (previous_status IN (
            'ACTIVE',
            'INACTIVE',
            'FROZEN',
            'SUSPENDED',
            'CLOSED',
            'PENDING'
        ))
);

-- Create indexes for performance
CREATE INDEX idx_wallet_freeze_wallet_id ON wallet_freeze_history(wallet_id);
CREATE INDEX idx_wallet_freeze_user_id ON wallet_freeze_history(user_id);
CREATE INDEX idx_wallet_freeze_event_id ON wallet_freeze_history(event_id);
CREATE INDEX idx_wallet_freeze_frozen_at ON wallet_freeze_history(frozen_at);
CREATE INDEX idx_wallet_freeze_type_reason ON wallet_freeze_history(freeze_type, freeze_reason);
CREATE INDEX idx_wallet_freeze_correlation_id ON wallet_freeze_history(correlation_id);

-- Index for finding active (unresolved) freezes
CREATE INDEX idx_wallet_freeze_active ON wallet_freeze_history(wallet_id, unfrozen_at)
    WHERE unfrozen_at IS NULL;

-- Index for compliance reporting
CREATE INDEX idx_wallet_freeze_compliance_report
    ON wallet_freeze_history(user_id, frozen_at, freeze_type);

-- Index for analytics
CREATE INDEX idx_wallet_freeze_analytics
    ON wallet_freeze_history(freeze_reason, frozen_at DESC);

-- Add comments for documentation
COMMENT ON TABLE wallet_freeze_history IS 'Comprehensive audit trail of all wallet freeze and unfreeze operations';
COMMENT ON COLUMN wallet_freeze_history.event_id IS 'Unique event ID from Kafka for idempotency check';
COMMENT ON COLUMN wallet_freeze_history.correlation_id IS 'Correlation ID for distributed tracing across services';
COMMENT ON COLUMN wallet_freeze_history.freeze_duration_seconds IS 'Duration of freeze in seconds (calculated on unfreeze)';
COMMENT ON COLUMN wallet_freeze_history.metadata IS 'JSON metadata for additional context and evidence';

-- Trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_wallet_freeze_history_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;

    -- Calculate freeze duration if being unfrozen
    IF NEW.unfrozen_at IS NOT NULL AND OLD.unfrozen_at IS NULL THEN
        NEW.freeze_duration_seconds = EXTRACT(EPOCH FROM (NEW.unfrozen_at - NEW.frozen_at));
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_wallet_freeze_history_timestamp
    BEFORE UPDATE ON wallet_freeze_history
    FOR EACH ROW
    EXECUTE FUNCTION update_wallet_freeze_history_updated_at();

-- Grant permissions
GRANT SELECT, INSERT, UPDATE ON wallet_freeze_history TO wallet_service_role;

-- Create view for active freezes (commonly queried)
CREATE OR REPLACE VIEW active_wallet_freezes AS
SELECT
    f.id,
    f.wallet_id,
    f.user_id,
    f.freeze_type,
    f.freeze_reason,
    f.frozen_by,
    f.frozen_at,
    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - f.frozen_at)) AS seconds_frozen,
    f.correlation_id,
    f.metadata
FROM wallet_freeze_history f
WHERE f.unfrozen_at IS NULL
ORDER BY f.frozen_at DESC;

COMMENT ON VIEW active_wallet_freezes IS 'View of currently frozen wallets with duration calculation';

-- Create materialized view for freeze statistics (for analytics dashboard)
CREATE MATERIALIZED VIEW wallet_freeze_statistics AS
SELECT
    DATE_TRUNC('day', frozen_at) AS freeze_date,
    freeze_type,
    freeze_reason,
    COUNT(*) AS freeze_count,
    COUNT(DISTINCT wallet_id) AS unique_wallets_frozen,
    COUNT(DISTINCT user_id) AS unique_users_affected,
    AVG(freeze_duration_seconds) AS avg_duration_seconds,
    MAX(freeze_duration_seconds) AS max_duration_seconds,
    MIN(freeze_duration_seconds) AS min_duration_seconds,
    COUNT(CASE WHEN unfrozen_at IS NULL THEN 1 END) AS currently_frozen_count
FROM wallet_freeze_history
WHERE frozen_at >= CURRENT_DATE - INTERVAL '90 days'
GROUP BY DATE_TRUNC('day', frozen_at), freeze_type, freeze_reason
ORDER BY freeze_date DESC, freeze_count DESC;

CREATE UNIQUE INDEX idx_wallet_freeze_stats_unique
    ON wallet_freeze_statistics(freeze_date, freeze_type, freeze_reason);

COMMENT ON MATERIALIZED VIEW wallet_freeze_statistics IS
    'Pre-aggregated freeze statistics for analytics (refreshed daily)';

-- =============================================================================
-- Verification queries
-- =============================================================================

-- Verify table creation
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_name = 'wallet_freeze_history') THEN
        RAISE EXCEPTION 'Table wallet_freeze_history was not created successfully';
    END IF;

    RAISE NOTICE 'Migration V10 completed successfully';
    RAISE NOTICE 'Created wallet_freeze_history table with % indexes',
        (SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'wallet_freeze_history');
END$$;
