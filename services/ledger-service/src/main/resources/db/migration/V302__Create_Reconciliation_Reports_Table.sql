-- ============================================================================
-- P1: Wallet-Ledger Reconciliation Reports Table
-- ============================================================================
-- Stores daily reconciliation results for audit trail and trending analysis
--
-- Purpose:
-- - Track reconciliation history
-- - Identify patterns in mismatches
-- - Compliance reporting (SOX 404)
-- - Performance monitoring
--
-- Author: Waqiti Engineering Team
-- Date: 2025-10-25
-- ============================================================================

CREATE TABLE IF NOT EXISTS reconciliation_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Reconciliation metadata
    reconciliation_date TIMESTAMP NOT NULL,
    total_wallets INTEGER NOT NULL,
    mismatch_count INTEGER NOT NULL DEFAULT 0,

    -- Status
    success BOOLEAN NOT NULL DEFAULT false,
    error_message TEXT,

    -- Summary statistics
    total_discrepancy_amount NUMERIC(19,4),
    largest_mismatch_amount NUMERIC(19,4),

    -- Execution metadata
    execution_duration_ms BIGINT,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient querying
CREATE INDEX idx_reconciliation_reports_date ON reconciliation_reports(reconciliation_date DESC);
CREATE INDEX idx_reconciliation_reports_success ON reconciliation_reports(success) WHERE success = false;
CREATE INDEX idx_reconciliation_reports_created ON reconciliation_reports(created_at DESC);

-- Table for individual mismatch details
CREATE TABLE IF NOT EXISTS reconciliation_mismatches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID NOT NULL REFERENCES reconciliation_reports(id) ON DELETE CASCADE,

    -- Wallet details
    wallet_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),

    -- Balance comparison
    ledger_balance NUMERIC(19,4) NOT NULL,
    wallet_balance NUMERIC(19,4) NOT NULL,
    difference NUMERIC(19,4) NOT NULL,
    difference_percentage NUMERIC(10,4),

    -- Resolution
    resolved BOOLEAN DEFAULT false,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(255),
    resolution_notes TEXT,

    -- Audit
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_reconciliation_mismatches_report ON reconciliation_mismatches(report_id);
CREATE INDEX idx_reconciliation_mismatches_wallet ON reconciliation_mismatches(wallet_id);
CREATE INDEX idx_reconciliation_mismatches_unresolved ON reconciliation_mismatches(resolved) WHERE resolved = false;
CREATE INDEX idx_reconciliation_mismatches_difference ON reconciliation_mismatches(difference DESC);

-- Comments
COMMENT ON TABLE reconciliation_reports IS
'Daily wallet-ledger reconciliation reports for financial integrity monitoring';

COMMENT ON TABLE reconciliation_mismatches IS
'Individual balance mismatches detected during reconciliation';

COMMENT ON COLUMN reconciliation_mismatches.difference_percentage IS
'Percentage difference: (difference / ledger_balance) * 100';

-- Grant permissions
GRANT SELECT, INSERT, UPDATE ON reconciliation_reports TO ledger_service_app;
GRANT SELECT, INSERT, UPDATE ON reconciliation_mismatches TO ledger_service_app;

-- Log migration
DO $$
BEGIN
    RAISE NOTICE '✅ Migration V302 completed: Reconciliation reports tables created';
END $$;
