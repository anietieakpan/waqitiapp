-- Add Foreign Key Indexes for Performance
-- Service: purchase-protection-service
-- Date: 2025-10-18
-- Description: Add missing indexes on foreign key columns to prevent table scans
-- Impact: Significantly improves query performance and prevents deadlocks
-- Priority: HIGH - Production Performance Issue
--
-- Problem:
-- PostgreSQL does NOT automatically create indexes on foreign key columns.
-- Without these indexes:
-- - JOINs perform full table scans
-- - DELETE/UPDATE on parent table scans entire child table
-- - High risk of deadlocks under concurrent load
-- - Slow query performance at scale
--
-- Solution:
-- Create indexes on all foreign key columns
-- Performance improvement: 10-100x faster for FK constraint checks
--
-- Total Indexes: 7

CREATE INDEX IF NOT EXISTS idx_policies_user_id ON policies(user_id);
CREATE INDEX IF NOT EXISTS idx_policies_transaction_id ON policies(transaction_id);
CREATE INDEX IF NOT EXISTS idx_claims_policy_id ON claims(policy_id);
CREATE INDEX IF NOT EXISTS idx_claims_dispute_id ON claims(dispute_id);
CREATE INDEX IF NOT EXISTS idx_claims_filed_by ON claims(filed_by);
CREATE INDEX IF NOT EXISTS idx_claim_evidence_claim_id ON claim_evidence(claim_id);
CREATE INDEX IF NOT EXISTS idx_claim_evidence_uploaded_by ON claim_evidence(uploaded_by);

-- Index comments for documentation
COMMENT ON INDEX idx_policies_user_id IS 'Foreign key index for policies.user_id - Performance optimization';
COMMENT ON INDEX idx_policies_transaction_id IS 'Foreign key index for policies.transaction_id - Performance optimization';
COMMENT ON INDEX idx_claims_policy_id IS 'Foreign key index for claims.policy_id - Performance optimization';
COMMENT ON INDEX idx_claims_dispute_id IS 'Foreign key index for claims.dispute_id - Performance optimization';
COMMENT ON INDEX idx_claims_filed_by IS 'Foreign key index for claims.filed_by - Performance optimization';
COMMENT ON INDEX idx_claim_evidence_claim_id IS 'Foreign key index for claim_evidence.claim_id - Performance optimization';
COMMENT ON INDEX idx_claim_evidence_uploaded_by IS 'Foreign key index for claim_evidence.uploaded_by - Performance optimization';
