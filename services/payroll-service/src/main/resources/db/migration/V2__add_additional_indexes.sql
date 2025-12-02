-- Additional indexes for performance optimization
-- Version: 2.0
-- Description: Add composite indexes and partial indexes for common queries

-- ============================================
-- Composite indexes for payroll_batches
-- ============================================

-- Index for finding active/processing batches by company
CREATE INDEX idx_payroll_batches_company_status_created
ON payroll_batches(company_id, status, created_at DESC);

-- Index for compliance queries
CREATE INDEX idx_payroll_batches_compliance
ON payroll_batches(company_id, compliance_violations)
WHERE compliance_violations > 0;

-- Index for retry logic
CREATE INDEX idx_payroll_batches_retry
ON payroll_batches(status, retry_count, created_at)
WHERE status = 'FAILED' AND retry_count < 3;

-- Index for stale batch detection
CREATE INDEX idx_payroll_batches_stale
ON payroll_batches(status, processing_started_at)
WHERE status = 'PROCESSING';

-- ============================================
-- Composite indexes for payroll_payments
-- ============================================

-- Index for employee payment history queries
CREATE INDEX idx_payroll_payments_employee_history
ON payroll_payments(employee_id, pay_period DESC, status);

-- Index for tax reporting (W-2 generation)
CREATE INDEX idx_payroll_payments_tax_reporting
ON payroll_payments(employee_id, pay_period)
WHERE EXTRACT(YEAR FROM pay_period) = EXTRACT(YEAR FROM CURRENT_DATE);

-- Index for failed payment retry
CREATE INDEX idx_payroll_payments_failed_retry
ON payroll_payments(status, retry_count, last_retry_at)
WHERE status = 'FAILED';

-- Index for large payment AML screening
CREATE INDEX idx_payroll_payments_aml
ON payroll_payments(company_id, net_amount, pay_date)
WHERE net_amount > 10000;

-- Index for payment method breakdown
CREATE INDEX idx_payroll_payments_method
ON payroll_payments(company_id, payment_method, pay_period);

-- ============================================
-- GIN indexes for JSONB metadata searches
-- ============================================

-- Enable fast JSONB queries on metadata
CREATE INDEX idx_payroll_batches_metadata_gin
ON payroll_batches USING gin(metadata);

CREATE INDEX idx_payroll_payments_metadata_gin
ON payroll_payments USING gin(metadata);

-- ============================================
-- Partial indexes for analytics
-- ============================================

-- Index for completed batches in last 90 days
CREATE INDEX idx_payroll_batches_recent_completed
ON payroll_batches(company_id, completed_at DESC, gross_amount)
WHERE status = 'COMPLETED'
  AND completed_at >= CURRENT_DATE - INTERVAL '90 days';

-- Index for pending approval workflow
CREATE INDEX idx_payroll_batches_pending_approval
ON payroll_batches(company_id, created_at)
WHERE requires_approval = true
  AND approved_at IS NULL
  AND status = 'PENDING_APPROVAL';

-- ============================================
-- Comments
-- ============================================
COMMENT ON INDEX idx_payroll_batches_company_status_created IS 'Optimizes queries for batch listing by company and status';
COMMENT ON INDEX idx_payroll_batches_compliance IS 'Fast lookup for batches with compliance violations';
COMMENT ON INDEX idx_payroll_batches_retry IS 'Optimizes retry batch queries';
COMMENT ON INDEX idx_payroll_payments_employee_history IS 'Optimizes employee payment history queries';
COMMENT ON INDEX idx_payroll_payments_tax_reporting IS 'Optimizes W-2 and tax reporting queries';
COMMENT ON INDEX idx_payroll_payments_aml IS 'Fast AML screening for large payments';
