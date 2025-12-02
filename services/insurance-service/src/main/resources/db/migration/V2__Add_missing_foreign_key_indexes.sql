-- Add missing foreign key indexes for insurance-service
-- Critical for policy holder queries and claim processing

-- insurance_policy table - policy holder lookup
CREATE INDEX IF NOT EXISTS idx_insurance_policy_holder_id
    ON insurance_policy(policy_holder_id);

CREATE INDEX IF NOT EXISTS idx_insurance_policy_agent_id
    ON insurance_policy(agent_id)
    WHERE agent_id IS NOT NULL;

-- insurance_claim table - policy holder and adjuster lookup
CREATE INDEX IF NOT EXISTS idx_insurance_claim_policy_holder_id
    ON insurance_claim(policy_holder_id);

CREATE INDEX IF NOT EXISTS idx_insurance_claim_adjuster_assigned
    ON insurance_claim(adjuster_assigned)
    WHERE adjuster_assigned IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_insurance_claim_approved_by
    ON insurance_claim(approved_by)
    WHERE approved_by IS NOT NULL;

-- insurance_premium_payment table - policy holder lookup
CREATE INDEX IF NOT EXISTS idx_insurance_premium_payment_policy_holder_id
    ON insurance_premium_payment(policy_holder_id);

-- insurance_quote table - user lookup
CREATE INDEX IF NOT EXISTS idx_insurance_quote_user_id
    ON insurance_quote(user_id);

COMMENT ON INDEX idx_insurance_policy_holder_id IS
    'Performance: Find all policies for specific user';
COMMENT ON INDEX idx_insurance_claim_adjuster_assigned IS
    'Performance: Find all claims assigned to specific adjuster';
COMMENT ON INDEX idx_insurance_claim_approved_by IS
    'Audit: Track claim approvals by specific officer';

-- Analyze tables
ANALYZE insurance_policy;
ANALYZE insurance_claim;
ANALYZE insurance_premium_payment;
ANALYZE insurance_quote;
