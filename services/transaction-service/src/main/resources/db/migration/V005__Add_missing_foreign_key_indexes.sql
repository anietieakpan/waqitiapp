-- Add missing foreign key indexes for transaction-service
-- Priority: HIGH - Improves dispute and audit query performance

-- transaction_disputes table - CRITICAL for audit queries
CREATE INDEX IF NOT EXISTS idx_transaction_disputes_created_by
    ON transaction_disputes(created_by);

CREATE INDEX IF NOT EXISTS idx_transaction_disputes_resolved_by
    ON transaction_disputes(resolved_by)
    WHERE resolved_by IS NOT NULL;

-- transaction_events table - audit trail queries
CREATE INDEX IF NOT EXISTS idx_transaction_events_created_by
    ON transaction_events(created_by)
    WHERE created_by IS NOT NULL;

-- transaction_limits table
CREATE INDEX IF NOT EXISTS idx_transaction_limits_created_by
    ON transaction_limits(created_by);

-- transaction_fees table
CREATE INDEX IF NOT EXISTS idx_transaction_fees_created_by
    ON transaction_fees(created_by)
    WHERE created_by IS NOT NULL;

COMMENT ON INDEX idx_transaction_disputes_created_by IS
    'Audit queries: Find disputes created by specific user';
COMMENT ON INDEX idx_transaction_disputes_resolved_by IS
    'Audit queries: Find disputes resolved by specific admin';

-- Analyze tables
ANALYZE transaction_disputes;
ANALYZE transaction_events;
ANALYZE transaction_limits;
ANALYZE transaction_fees;
