-- Flyway Migration: Add critical performance indexes for payment service
-- Version: V100
-- Description: Adds missing indexes identified in forensic analysis to improve query performance
-- Author: Waqiti Platform Team
-- Date: 2024-01-15

-- ============================================
-- PAYMENT REQUESTS TABLE INDEXES
-- ============================================

-- Index for requestor queries (user payment history)
CREATE INDEX IF NOT EXISTS idx_payment_requests_requestor_id 
ON payment_requests(requestor_id)
WHERE status != 'CANCELLED';
COMMENT ON INDEX idx_payment_requests_requestor_id IS 'Index for efficient requestor payment lookups';

-- Index for recipient queries (incoming payments)
CREATE INDEX IF NOT EXISTS idx_payment_requests_recipient_id 
ON payment_requests(recipient_id)
WHERE status != 'CANCELLED';
COMMENT ON INDEX idx_payment_requests_recipient_id IS 'Index for efficient recipient payment lookups';

-- Index for status-based queries (payment processing workflows)
CREATE INDEX IF NOT EXISTS idx_payment_requests_status 
ON payment_requests(status)
WHERE status IN ('PENDING', 'PROCESSING', 'REQUIRES_APPROVAL');
COMMENT ON INDEX idx_payment_requests_status IS 'Partial index for active payment status queries';

-- Composite index for user payment status queries
CREATE INDEX IF NOT EXISTS idx_payment_requests_requestor_status 
ON payment_requests(requestor_id, status, created_at DESC);
COMMENT ON INDEX idx_payment_requests_requestor_status IS 'Composite index for user payment history with status';

-- Index for date-range queries (reporting and reconciliation)
CREATE INDEX IF NOT EXISTS idx_payment_requests_created_at 
ON payment_requests(created_at DESC);
COMMENT ON INDEX idx_payment_requests_created_at IS 'Index for time-based payment queries';

-- Index for payment method queries
CREATE INDEX IF NOT EXISTS idx_payment_requests_payment_method 
ON payment_requests(payment_method)
WHERE payment_method IS NOT NULL;
COMMENT ON INDEX idx_payment_requests_payment_method IS 'Index for payment method analysis';

-- Index for high-value payment monitoring
CREATE INDEX IF NOT EXISTS idx_payment_requests_amount_high_value 
ON payment_requests(amount)
WHERE amount > 10000;
COMMENT ON INDEX idx_payment_requests_amount_high_value IS 'Partial index for high-value payment monitoring';

-- ============================================
-- TRANSACTIONS TABLE INDEXES
-- ============================================

-- Composite index for user transaction queries
CREATE INDEX IF NOT EXISTS idx_transactions_user_id_status 
ON transactions(user_id, status, created_at DESC);
COMMENT ON INDEX idx_transactions_user_id_status IS 'Composite index for user transaction queries';

-- Index for transaction type queries
CREATE INDEX IF NOT EXISTS idx_transactions_type 
ON transactions(transaction_type, created_at DESC);
COMMENT ON INDEX idx_transactions_type IS 'Index for transaction type analysis';

-- Index for reconciliation queries
CREATE INDEX IF NOT EXISTS idx_transactions_reference_id 
ON transactions(reference_id)
WHERE reference_id IS NOT NULL;
COMMENT ON INDEX idx_transactions_reference_id IS 'Index for transaction reference lookups';

-- Index for settlement batch processing
CREATE INDEX IF NOT EXISTS idx_transactions_settlement_batch 
ON transactions(settlement_batch_id, status)
WHERE settlement_batch_id IS NOT NULL;
COMMENT ON INDEX idx_transactions_settlement_batch IS 'Index for settlement batch processing';

-- ============================================
-- ACH TRANSFERS TABLE INDEXES
-- ============================================

-- Index for ACH status tracking
CREATE INDEX IF NOT EXISTS idx_ach_transfers_status 
ON ach_transfers(status, created_at DESC)
WHERE status IN ('PENDING', 'PROCESSING', 'SUBMITTED');
COMMENT ON INDEX idx_ach_transfers_status IS 'Index for ACH transfer status monitoring';

-- Index for ACH batch processing
CREATE INDEX IF NOT EXISTS idx_ach_transfers_batch_id 
ON ach_transfers(batch_id, status)
WHERE batch_id IS NOT NULL;
COMMENT ON INDEX idx_ach_transfers_batch_id IS 'Index for ACH batch processing';

-- Index for ACH returns processing
CREATE INDEX IF NOT EXISTS idx_ach_transfers_return_status 
ON ach_transfers(return_code, processed_at)
WHERE return_code IS NOT NULL;
COMMENT ON INDEX idx_ach_transfers_return_status IS 'Index for ACH return processing';

-- ============================================
-- WIRE TRANSFERS TABLE INDEXES
-- ============================================

-- Index for wire transfer tracking
CREATE INDEX IF NOT EXISTS idx_wire_transfers_tracking_number 
ON wire_transfers(tracking_number)
WHERE tracking_number IS NOT NULL;
COMMENT ON INDEX idx_wire_transfers_tracking_number IS 'Index for wire transfer tracking';

-- Index for international wire queries
CREATE INDEX IF NOT EXISTS idx_wire_transfers_swift_code 
ON wire_transfers(beneficiary_swift_code, status)
WHERE beneficiary_swift_code IS NOT NULL;
COMMENT ON INDEX idx_wire_transfers_swift_code IS 'Index for international wire queries';

-- ============================================
-- PAYMENT METHODS TABLE INDEXES
-- ============================================

-- Index for user payment method lookups
CREATE INDEX IF NOT EXISTS idx_payment_methods_user_id 
ON payment_methods(user_id, is_active, is_default DESC);
COMMENT ON INDEX idx_payment_methods_user_id IS 'Index for user payment method queries';

-- Index for payment method verification status
CREATE INDEX IF NOT EXISTS idx_payment_methods_verification 
ON payment_methods(verification_status, created_at DESC)
WHERE verification_status = 'PENDING';
COMMENT ON INDEX idx_payment_methods_verification IS 'Index for pending payment method verifications';

-- ============================================
-- PAYMENT SCHEDULES TABLE INDEXES
-- ============================================

-- Index for scheduled payment processing
CREATE INDEX IF NOT EXISTS idx_payment_schedules_next_run 
ON payment_schedules(next_run_date, status)
WHERE status = 'ACTIVE' AND next_run_date IS NOT NULL;
COMMENT ON INDEX idx_payment_schedules_next_run IS 'Index for scheduled payment processing';

-- Index for recurring payment queries
CREATE INDEX IF NOT EXISTS idx_payment_schedules_user_recurring 
ON payment_schedules(user_id, recurrence_pattern, status)
WHERE status = 'ACTIVE';
COMMENT ON INDEX idx_payment_schedules_user_recurring IS 'Index for user recurring payment queries';

-- ============================================
-- PAYMENT FEES TABLE INDEXES
-- ============================================

-- Index for fee calculation queries
CREATE INDEX IF NOT EXISTS idx_payment_fees_transaction_id 
ON payment_fees(transaction_id);
COMMENT ON INDEX idx_payment_fees_transaction_id IS 'Index for transaction fee lookups';

-- Index for fee reporting
CREATE INDEX IF NOT EXISTS idx_payment_fees_created_date 
ON payment_fees(created_at DESC, fee_type);
COMMENT ON INDEX idx_payment_fees_created_date IS 'Index for fee reporting and analysis';

-- ============================================
-- PAYMENT LIMITS TABLE INDEXES
-- ============================================

-- Index for limit checking
CREATE INDEX IF NOT EXISTS idx_payment_limits_user_type 
ON payment_limits(user_id, limit_type, is_active)
WHERE is_active = true;
COMMENT ON INDEX idx_payment_limits_user_type IS 'Index for payment limit validation';

-- ============================================
-- PAYMENT AUDIT LOG INDEXES
-- ============================================

-- Index for audit trail queries
CREATE INDEX IF NOT EXISTS idx_payment_audit_entity 
ON payment_audit_log(entity_type, entity_id, created_at DESC);
COMMENT ON INDEX idx_payment_audit_entity IS 'Index for payment audit trail queries';

-- Index for compliance reporting
CREATE INDEX IF NOT EXISTS idx_payment_audit_action 
ON payment_audit_log(action, created_at DESC)
WHERE action IN ('APPROVE', 'REJECT', 'BLOCK', 'FLAG');
COMMENT ON INDEX idx_payment_audit_action IS 'Index for compliance action queries';

-- ============================================
-- REFUNDS TABLE INDEXES
-- ============================================

-- Index for refund processing
CREATE INDEX IF NOT EXISTS idx_refunds_original_transaction 
ON refunds(original_transaction_id, status);
COMMENT ON INDEX idx_refunds_original_transaction IS 'Index for refund transaction lookups';

-- Index for refund status monitoring
CREATE INDEX IF NOT EXISTS idx_refunds_status_created 
ON refunds(status, created_at DESC)
WHERE status IN ('PENDING', 'PROCESSING');
COMMENT ON INDEX idx_refunds_status_created IS 'Index for active refund monitoring';

-- ============================================
-- DISPUTE TABLE INDEXES
-- ============================================

-- Index for dispute management
CREATE INDEX IF NOT EXISTS idx_disputes_transaction_status 
ON disputes(transaction_id, status, created_at DESC);
COMMENT ON INDEX idx_disputes_transaction_status IS 'Index for transaction dispute queries';

-- Index for user dispute history
CREATE INDEX IF NOT EXISTS idx_disputes_user_id 
ON disputes(user_id, status, created_at DESC);
COMMENT ON INDEX idx_disputes_user_id IS 'Index for user dispute history';

-- ============================================
-- STATISTICS UPDATE
-- ============================================

-- Update table statistics for query planner optimization
ANALYZE payment_requests;
ANALYZE transactions;
ANALYZE ach_transfers;
ANALYZE wire_transfers;
ANALYZE payment_methods;
ANALYZE payment_schedules;
ANALYZE payment_fees;
ANALYZE payment_limits;
ANALYZE payment_audit_log;
ANALYZE refunds;
ANALYZE disputes;

-- ============================================
-- PERFORMANCE MONITORING
-- ============================================

-- Create extension for query performance monitoring if not exists
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Log slow queries for analysis (requires PostgreSQL configuration)
-- Set in postgresql.conf:
-- log_min_duration_statement = 1000  -- Log queries slower than 1 second
-- log_statement = 'ddl'               -- Log DDL statements
-- log_checkpoints = on                -- Log checkpoint activity
-- log_connections = on                -- Log connection attempts
-- log_disconnections = on             -- Log disconnections