-- Add Foreign Key Indexes for Performance
-- Service: lending-service
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
-- Total Indexes: 5

CREATE INDEX IF NOT EXISTS idx_loans_borrower_id ON loans(borrower_id);
CREATE INDEX IF NOT EXISTS idx_loans_approved_by ON loans(approved_by);
CREATE INDEX IF NOT EXISTS idx_loan_payments_loan_id ON loan_payments(loan_id);
CREATE INDEX IF NOT EXISTS idx_loan_collateral_loan_id ON loan_collateral(loan_id);
CREATE INDEX IF NOT EXISTS idx_loan_defaults_loan_id ON loan_defaults(loan_id);

-- Index comments for documentation
COMMENT ON INDEX idx_loans_borrower_id IS 'Foreign key index for loans.borrower_id - Performance optimization';
COMMENT ON INDEX idx_loans_approved_by IS 'Foreign key index for loans.approved_by - Performance optimization';
COMMENT ON INDEX idx_loan_payments_loan_id IS 'Foreign key index for loan_payments.loan_id - Performance optimization';
COMMENT ON INDEX idx_loan_collateral_loan_id IS 'Foreign key index for loan_collateral.loan_id - Performance optimization';
COMMENT ON INDEX idx_loan_defaults_loan_id IS 'Foreign key index for loan_defaults.loan_id - Performance optimization';
