-- Add missing foreign key indexes for dispute-service
-- Critical for dispute resolution tracking and audit queries

-- disputes table - audit and assignment indexes
CREATE INDEX IF NOT EXISTS idx_disputes_created_by
    ON disputes(created_by);

CREATE INDEX IF NOT EXISTS idx_disputes_assigned_to
    ON disputes(assigned_to)
    WHERE assigned_to IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_disputes_resolved_by
    ON disputes(resolved_by)
    WHERE resolved_by IS NOT NULL;

-- dispute_messages table - user lookup
CREATE INDEX IF NOT EXISTS idx_dispute_messages_sent_by
    ON dispute_messages(sent_by);

-- dispute_evidence table - uploaded_by lookup
CREATE INDEX IF NOT EXISTS idx_dispute_evidence_uploaded_by
    ON dispute_evidence(uploaded_by);

COMMENT ON INDEX idx_disputes_assigned_to IS
    'Performance: Find disputes assigned to specific agent';
COMMENT ON INDEX idx_disputes_resolved_by IS
    'Audit: Track resolutions by specific agent';

-- Analyze tables
ANALYZE disputes;
ANALYZE dispute_messages;
ANALYZE dispute_evidence;
