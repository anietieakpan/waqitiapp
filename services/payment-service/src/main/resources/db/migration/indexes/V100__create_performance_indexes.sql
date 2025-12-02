-- ===========================================================================
-- WAQITI PAYMENT SERVICE - PERFORMANCE OPTIMIZATION INDEXES
-- ===========================================================================
-- Version: 1.0.0
-- Purpose: Create comprehensive indexes for payment service tables
-- Author: Waqiti Platform Team
-- Date: 2025-01-15
-- ===========================================================================

-- ===========================================================================
-- PAYMENTS TABLE INDEXES
-- ===========================================================================

-- Primary lookup patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_user_id_created
    ON payments(user_id, created_at DESC)
    WHERE status != 'DELETED';
COMMENT ON INDEX idx_payments_user_id_created IS 'User payment history queries';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_status_created
    ON payments(status, created_at DESC)
    INCLUDE (amount, currency, payment_method_id);
COMMENT ON INDEX idx_payments_status_created IS 'Payment status monitoring and filtering';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_merchant_id
    ON payments(merchant_id, created_at DESC)
    WHERE merchant_id IS NOT NULL AND status = 'COMPLETED';
COMMENT ON INDEX idx_payments_merchant_id IS 'Merchant payment reconciliation';

-- Fraud detection lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_fraud_score
    ON payments(fraud_score DESC, created_at DESC)
    WHERE fraud_score > 50;
COMMENT ON INDEX idx_payments_fraud_score IS 'High-risk payment identification';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_ip_address
    ON payments USING hash(ip_address)
    WHERE ip_address IS NOT NULL;
COMMENT ON INDEX idx_payments_ip_address IS 'IP-based fraud detection';

-- Amount-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_amount_range
    ON payments(amount, created_at DESC)
    WHERE status = 'COMPLETED';
COMMENT ON INDEX idx_payments_amount_range IS 'High-value transaction monitoring';

-- Payment method tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_payment_method
    ON payments(payment_method_id, created_at DESC)
    WHERE status IN ('COMPLETED', 'PENDING');
COMMENT ON INDEX idx_payments_payment_method IS 'Payment method usage analysis';

-- Reference number lookups (unique identifiers)
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_reference_number
    ON payments(reference_number)
    WHERE reference_number IS NOT NULL;
COMMENT ON INDEX idx_payments_reference_number IS 'Fast reference number lookups';

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_external_reference
    ON payments(external_reference_id)
    WHERE external_reference_id IS NOT NULL;
COMMENT ON INDEX idx_payments_external_reference IS 'External system reconciliation';

-- Idempotency
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_idempotency_key
    ON payments(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
COMMENT ON INDEX idx_payments_idempotency_key IS 'Idempotency enforcement';

-- ===========================================================================
-- TRANSACTIONS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_payment_id
    ON transactions(payment_id, created_at DESC);
COMMENT ON INDEX idx_transactions_payment_id IS 'Payment transaction history';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_wallet
    ON transactions(user_id, wallet_id, created_at DESC);
COMMENT ON INDEX idx_transactions_user_wallet IS 'User wallet transaction history';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_type_status
    ON transactions(transaction_type, status, created_at DESC);
COMMENT ON INDEX idx_transactions_type_status IS 'Transaction type analysis';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount
    ON transactions(amount, created_at DESC)
    WHERE status = 'COMPLETED' AND amount >= 1000;
COMMENT ON INDEX idx_transactions_amount IS 'Large transaction monitoring';

-- ===========================================================================
-- ACH TRANSFERS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ach_transfers_user_id
    ON ach_transfers(user_id, created_at DESC);
COMMENT ON INDEX idx_ach_transfers_user_id IS 'User ACH transfer history';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ach_transfers_status
    ON ach_transfers(status, scheduled_date)
    WHERE status IN ('PENDING', 'PROCESSING');
COMMENT ON INDEX idx_ach_transfers_status IS 'ACH batch processing';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ach_transfers_bank_account
    ON ach_transfers(bank_account_id, created_at DESC);
COMMENT ON INDEX idx_ach_transfers_bank_account IS 'Bank account ACH history';

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_ach_transfers_trace_number
    ON ach_transfers(trace_number)
    WHERE trace_number IS NOT NULL;
COMMENT ON INDEX idx_ach_transfers_trace_number IS 'ACH trace number lookups';

-- ===========================================================================
-- PAYMENT METHODS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_active
    ON payment_methods(user_id, is_active, created_at DESC);
COMMENT ON INDEX idx_payment_methods_user_active IS 'Active payment methods per user';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_type
    ON payment_methods(method_type, is_active)
    INCLUDE (user_id);
COMMENT ON INDEX idx_payment_methods_type IS 'Payment method type distribution';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_token
    ON payment_methods USING hash(token)
    WHERE token IS NOT NULL;
COMMENT ON INDEX idx_payment_methods_token IS 'Tokenized payment method lookups';

-- ===========================================================================
-- SCHEDULED PAYMENTS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_payments_next_execution
    ON scheduled_payments(next_execution_date, status)
    WHERE status = 'ACTIVE' AND next_execution_date <= CURRENT_DATE + INTERVAL '7 days';
COMMENT ON INDEX idx_scheduled_payments_next_execution IS 'Upcoming scheduled payment processing';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_payments_user
    ON scheduled_payments(user_id, status, created_at DESC);
COMMENT ON INDEX idx_scheduled_payments_user IS 'User recurring payment management';

-- ===========================================================================
-- CHARGEBACKS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chargebacks_payment_id
    ON chargebacks(payment_id, created_at DESC);
COMMENT ON INDEX idx_chargebacks_payment_id IS 'Payment chargeback lookups';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chargebacks_status
    ON chargebacks(status, created_at DESC)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');
COMMENT ON INDEX idx_chargebacks_status IS 'Active chargeback management';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chargebacks_deadline
    ON chargebacks(response_deadline)
    WHERE status = 'OPEN' AND response_deadline <= CURRENT_DATE + INTERVAL '3 days';
COMMENT ON INDEX idx_chargebacks_deadline IS 'Urgent chargeback alerts';

-- ===========================================================================
-- DISPUTES TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_disputes_payment_id
    ON disputes(payment_id, created_at DESC);
COMMENT ON INDEX idx_disputes_payment_id IS 'Payment dispute lookups';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_disputes_status
    ON disputes(status, created_at DESC);
COMMENT ON INDEX idx_disputes_status IS 'Dispute queue management';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_disputes_assigned_to
    ON disputes(assigned_to, status)
    WHERE assigned_to IS NOT NULL AND status NOT IN ('RESOLVED', 'CLOSED');
COMMENT ON INDEX idx_disputes_assigned_to IS 'Agent dispute workload';

-- ===========================================================================
-- FRAUD CHECK RECORDS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_checks_payment_id
    ON fraud_check_records(payment_id, created_at DESC);
COMMENT ON INDEX idx_fraud_checks_payment_id IS 'Payment fraud history';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_checks_risk_level
    ON fraud_check_records(risk_level, created_at DESC)
    WHERE risk_level IN ('HIGH', 'CRITICAL');
COMMENT ON INDEX idx_fraud_checks_risk_level IS 'High-risk transaction monitoring';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_checks_user_pattern
    ON fraud_check_records(user_id, created_at DESC)
    WHERE risk_level != 'LOW';
COMMENT ON INDEX idx_fraud_checks_user_pattern IS 'User fraud pattern analysis';

-- ===========================================================================
-- AUDIT TRAIL TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_trail_entity
    ON audit_trail(entity_type, entity_id, created_at DESC);
COMMENT ON INDEX idx_audit_trail_entity IS 'Entity audit history';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_trail_user_action
    ON audit_trail(user_id, action, created_at DESC);
COMMENT ON INDEX idx_audit_trail_user_action IS 'User action audit trail';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_trail_timestamp
    ON audit_trail(created_at DESC)
    WHERE created_at >= CURRENT_DATE - INTERVAL '90 days';
COMMENT ON INDEX idx_audit_trail_timestamp IS 'Recent audit log queries';

-- ===========================================================================
-- LEDGER TRANSACTIONS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_transactions_account
    ON ledger_transactions(account_id, transaction_date DESC);
COMMENT ON INDEX idx_ledger_transactions_account IS 'Account ledger history';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_transactions_payment
    ON ledger_transactions(payment_id)
    WHERE payment_id IS NOT NULL;
COMMENT ON INDEX idx_ledger_transactions_payment IS 'Payment ledger entries';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ledger_transactions_reconciliation
    ON ledger_transactions(reconciliation_status, transaction_date)
    WHERE reconciliation_status != 'RECONCILED';
COMMENT ON INDEX idx_ledger_transactions_reconciliation IS 'Unreconciled transaction tracking';

-- ===========================================================================
-- INSTANT DEPOSITS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_instant_deposits_user
    ON instant_deposits(user_id, created_at DESC);
COMMENT ON INDEX idx_instant_deposits_user IS 'User instant deposit history';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_instant_deposits_status
    ON instant_deposits(status, created_at DESC)
    WHERE status IN ('PENDING', 'PROCESSING');
COMMENT ON INDEX idx_instant_deposits_status IS 'Active instant deposit processing';

-- ===========================================================================
-- WEBHOOKS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_webhooks_event_type
    ON webhook_deliveries(event_type, created_at DESC);
COMMENT ON INDEX idx_webhooks_event_type IS 'Webhook event tracking';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_webhooks_retry
    ON webhook_deliveries(retry_count, next_retry_at)
    WHERE status = 'FAILED' AND retry_count < max_retries;
COMMENT ON INDEX idx_webhooks_retry IS 'Failed webhook retry queue';

-- ===========================================================================
-- QR CODE PAYMENTS TABLE INDEXES
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_payments_code
    ON qr_code_payments USING hash(qr_code);
COMMENT ON INDEX idx_qr_payments_code IS 'QR code payment lookups';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qr_payments_expiry
    ON qr_code_payments(expires_at)
    WHERE status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP;
COMMENT ON INDEX idx_qr_payments_expiry IS 'Active QR code management';

-- ===========================================================================
-- COMPOSITE INDEXES FOR COMPLEX QUERIES
-- ===========================================================================

-- Payment search/filter combo
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_search
    ON payments(user_id, status, created_at DESC, amount)
    WHERE deleted_at IS NULL;
COMMENT ON INDEX idx_payments_search IS 'Payment search and filtering';

-- Transaction reconciliation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reconciliation
    ON transactions(created_at, status, transaction_type)
    INCLUDE (amount, currency)
    WHERE status = 'COMPLETED';
COMMENT ON INDEX idx_transactions_reconciliation IS 'End-of-day reconciliation';

-- Fraud analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_fraud_analysis
    ON payments(user_id, ip_address, device_fingerprint, created_at)
    WHERE fraud_score > 30;
COMMENT ON INDEX idx_payments_fraud_analysis IS 'Multi-factor fraud detection';

-- ===========================================================================
-- PARTIAL INDEXES FOR SPECIFIC USE CASES
-- ===========================================================================

-- Active/pending operations only
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_active_only
    ON payments(created_at DESC)
    WHERE status IN ('PENDING', 'PROCESSING', 'AUTHORIZED');
COMMENT ON INDEX idx_payments_active_only IS 'Active payment monitoring';

-- Failed transactions needing review
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_failed_review
    ON payments(created_at DESC, failure_reason)
    WHERE status = 'FAILED' AND reviewed_at IS NULL;
COMMENT ON INDEX idx_payments_failed_review IS 'Failed payment review queue';

-- High-value transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_high_value
    ON payments(amount DESC, created_at DESC)
    WHERE amount >= 5000 AND status = 'COMPLETED';
COMMENT ON INDEX idx_payments_high_value IS 'High-value transaction monitoring';

-- ===========================================================================
-- GIN INDEXES FOR JSON/ARRAY COLUMNS
-- ===========================================================================

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_metadata_gin
    ON payments USING gin(metadata jsonb_path_ops)
    WHERE metadata IS NOT NULL;
COMMENT ON INDEX idx_payments_metadata_gin IS 'JSON metadata searches';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_checks_rules_gin
    ON fraud_check_records USING gin(triggered_rules)
    WHERE array_length(triggered_rules, 1) > 0;
COMMENT ON INDEX idx_fraud_checks_rules_gin IS 'Fraud rule pattern analysis';

-- ===========================================================================
-- FULL TEXT SEARCH INDEXES
-- ===========================================================================

-- Payment description search
ALTER TABLE payments ADD COLUMN IF NOT EXISTS search_vector tsvector;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_search_vector
    ON payments USING gin(search_vector);

CREATE OR REPLACE FUNCTION payments_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('english', coalesce(NEW.description, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.reference_number, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(NEW.notes, '')), 'C');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payments_search_vector_trigger
    BEFORE INSERT OR UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION payments_search_vector_update();

-- ===========================================================================
-- STATISTICS AND MAINTENANCE
-- ===========================================================================

-- Update statistics for better query planning
ANALYZE payments;
ANALYZE transactions;
ANALYZE ach_transfers;
ANALYZE payment_methods;
ANALYZE scheduled_payments;
ANALYZE chargebacks;
ANALYZE disputes;
ANALYZE fraud_check_records;
ANALYZE audit_trail;
ANALYZE ledger_transactions;

-- ===========================================================================
-- MONITORING QUERIES FOR INDEX USAGE
-- ===========================================================================

COMMENT ON DATABASE waqiti_payments IS 'Performance indexes created on 2025-01-15. Monitor index usage with:
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = ''public''
ORDER BY idx_scan DESC;';

-- ===========================================================================
-- END OF MIGRATION
-- ===========================================================================
