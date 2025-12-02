-- Add missing foreign key indexes for lending-service
-- Critical for loan approval tracking and audit compliance

-- loan_applications table - audit fields
CREATE INDEX IF NOT EXISTS idx_loan_applications_created_by
    ON loan_applications(created_by);

CREATE INDEX IF NOT EXISTS idx_loan_applications_approved_by
    ON loan_applications(approved_by)
    WHERE approved_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_loan_applications_rejected_by
    ON loan_applications(rejected_by)
    WHERE rejected_by IS NOT NULL;

-- loan_disbursements table
CREATE INDEX IF NOT EXISTS idx_loan_disbursements_disbursed_by
    ON loan_disbursements(disbursed_by);

-- loan_payments table
CREATE INDEX IF NOT EXISTS idx_loan_payments_processed_by
    ON loan_payments(processed_by)
    WHERE processed_by IS NOT NULL;

-- collateral table
CREATE INDEX IF NOT EXISTS idx_collateral_verified_by
    ON collateral(verified_by)
    WHERE verified_by IS NOT NULL;

COMMENT ON INDEX idx_loan_applications_approved_by IS
    'Audit: Track loan approvals by specific underwriter';
COMMENT ON INDEX idx_loan_payments_processed_by IS
    'Performance: Payment processing audit queries';

-- Analyze tables
ANALYZE loan_applications;
ANALYZE loan_disbursements;
ANALYZE loan_payments;
ANALYZE collateral;
