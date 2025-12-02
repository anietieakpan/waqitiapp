-- Add missing foreign key indexes for bnpl-service
-- Improves installment and transaction lookup performance

-- bnpl_installments table - transaction_id lookup
CREATE INDEX IF NOT EXISTS idx_bnpl_installments_transaction_id
    ON bnpl_installments(transaction_id)
    WHERE transaction_id IS NOT NULL;

COMMENT ON INDEX idx_bnpl_installments_transaction_id IS
    'Performance: Lookup installments by transaction - was missing';

-- bnpl_applications table
CREATE INDEX IF NOT EXISTS idx_bnpl_applications_created_by
    ON bnpl_applications(created_by);

-- bnpl_installments table - audit
CREATE INDEX IF NOT EXISTS idx_bnpl_installments_created_by
    ON bnpl_installments(created_by);

-- credit_assessments table
CREATE INDEX IF NOT EXISTS idx_credit_assessments_assessed_by
    ON credit_assessments(assessed_by)
    WHERE assessed_by IS NOT NULL;

-- Analyze tables
ANALYZE bnpl_installments;
ANALYZE bnpl_applications;
ANALYZE credit_assessments;
