-- Add missing performance indexes to disputes table
-- Optimizes common query patterns

-- Single column indexes
CREATE INDEX IF NOT EXISTS idx_dispute_merchant ON disputes(merchant_id);
CREATE INDEX IF NOT EXISTS idx_dispute_resolved_at ON disputes(resolved_at);
CREATE INDEX IF NOT EXISTS idx_dispute_type ON disputes(dispute_type);
CREATE INDEX IF NOT EXISTS idx_dispute_escalation_level ON disputes(escalation_level);
CREATE INDEX IF NOT EXISTS idx_dispute_chargeback_code ON disputes(chargeback_code);
CREATE INDEX IF NOT EXISTS idx_dispute_funds_locked ON disputes(funds_locked);
CREATE INDEX IF NOT EXISTS idx_dispute_escalated_at ON disputes(escalated_at);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_dispute_user_status ON disputes(user_id, status);
CREATE INDEX IF NOT EXISTS idx_dispute_status_created ON disputes(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dispute_priority_status ON disputes(priority, status);
CREATE INDEX IF NOT EXISTS idx_dispute_type_status ON disputes(dispute_type, status);
CREATE INDEX IF NOT EXISTS idx_dispute_merchant_status ON disputes(merchant_id, status) WHERE merchant_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_dispute_date_range ON disputes(created_at DESC, status);

-- Partial indexes for active disputes (most frequent queries)
CREATE INDEX IF NOT EXISTS idx_dispute_active ON disputes(created_at DESC)
WHERE status IN ('OPEN', 'UNDER_REVIEW', 'ESCALATED');

CREATE INDEX IF NOT EXISTS idx_dispute_pending_resolution ON disputes(created_at DESC)
WHERE status = 'UNDER_REVIEW' AND escalation_level > 0;

-- Index for SLA monitoring
CREATE INDEX IF NOT EXISTS idx_dispute_sla_tracking ON disputes(created_at, status, priority)
WHERE resolved_at IS NULL;

-- Index for chargeback processing
CREATE INDEX IF NOT EXISTS idx_dispute_chargebacks ON disputes(chargeback_code, status)
WHERE chargeback_code IS NOT NULL;

-- Indexes for dispute_evidence table (if exists)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'dispute_evidence') THEN
        CREATE INDEX IF NOT EXISTS idx_evidence_dispute ON dispute_evidence(dispute_id);
        CREATE INDEX IF NOT EXISTS idx_evidence_submitted ON dispute_evidence(submitted_at DESC);
        CREATE INDEX IF NOT EXISTS idx_evidence_type ON dispute_evidence(evidence_type);
        CREATE INDEX IF NOT EXISTS idx_evidence_verification ON dispute_evidence(verification_status);
        CREATE INDEX IF NOT EXISTS idx_evidence_dispute_type ON dispute_evidence(dispute_id, evidence_type);
    END IF;
END $$;

-- Indexes for dispute_metadata table
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'dispute_metadata') THEN
        CREATE INDEX IF NOT EXISTS idx_metadata_dispute ON dispute_metadata(dispute_id);
        CREATE INDEX IF NOT EXISTS idx_metadata_key ON dispute_metadata(metadata_key);
    END IF;
END $$;

-- Comments
COMMENT ON INDEX idx_dispute_user_status IS 'Optimizes getUserDisputes queries with status filter';
COMMENT ON INDEX idx_dispute_status_created IS 'Optimizes status-based listings with date sorting';
COMMENT ON INDEX idx_dispute_active IS 'Partial index for frequently queried active disputes';
COMMENT ON INDEX idx_dispute_sla_tracking IS 'Supports SLA monitoring and escalation queries';
