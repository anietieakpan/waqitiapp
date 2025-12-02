-- Production Database Indexes for Payment Service
-- These indexes are critical for production performance

-- Payment transactions table indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_user_created 
ON payment_transactions(user_id, created_at DESC) 
WHERE status != 'CANCELLED';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_status_created 
ON payment_transactions(status, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_provider_status 
ON payment_transactions(payment_provider, status, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_reference 
ON payment_transactions(transaction_reference) 
WHERE transaction_reference IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_idempotency 
ON payment_transactions(idempotency_key) 
WHERE idempotency_key IS NOT NULL;

-- Partial index for pending transactions (frequent queries)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_pending 
ON payment_transactions(created_at) 
WHERE status IN ('PENDING', 'PROCESSING');

-- Index for fraud detection queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_fraud_check 
ON payment_transactions(user_id, amount, created_at DESC) 
WHERE fraud_score IS NOT NULL;

-- Wallet transactions indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_wallet_created 
ON wallet_transactions(wallet_id, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_type_status 
ON wallet_transactions(transaction_type, status, created_at DESC);

-- Composite index for balance calculations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_balance_calc 
ON wallet_transactions(wallet_id, status, transaction_type, amount) 
WHERE status = 'COMPLETED';

-- Payment methods indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_active 
ON payment_methods(user_id, is_default DESC, created_at DESC) 
WHERE is_active = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_type_provider 
ON payment_methods(method_type, payment_provider) 
WHERE is_active = true;

-- Recurring payments indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_recurring_payments_next_execution 
ON recurring_payments(next_execution_date, status) 
WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_recurring_payments_user_active 
ON recurring_payments(user_id, status, created_at DESC) 
WHERE status IN ('ACTIVE', 'PAUSED');

-- Refunds indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refunds_transaction 
ON refunds(original_transaction_id, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refunds_pending 
ON refunds(created_at) 
WHERE status = 'PENDING';

-- Disputes indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_disputes_status_created 
ON disputes(status, created_at DESC) 
WHERE status IN ('OPEN', 'UNDER_REVIEW');

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_disputes_user_status 
ON disputes(user_id, status, created_at DESC);

-- Webhook events indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_webhook_events_status_retry 
ON webhook_events(status, retry_count, next_retry_at) 
WHERE status IN ('PENDING', 'FAILED') AND retry_count < max_retries;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_webhook_events_type_created 
ON webhook_events(event_type, created_at DESC);

-- Audit log indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_user_action 
ON audit_logs(user_id, action, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_entity 
ON audit_logs(entity_type, entity_id, created_at DESC);

-- Settlement indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_settlements_merchant_status 
ON settlements(merchant_id, status, settlement_date DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_settlements_pending 
ON settlements(settlement_date, status) 
WHERE status = 'PENDING';

-- KYC verification indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_verification_user_status 
ON kyc_verifications(user_id, verification_status, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_verification_pending 
ON kyc_verifications(created_at) 
WHERE verification_status = 'PENDING';

-- Rate limiting indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rate_limits_user_endpoint 
ON rate_limit_tracking(user_id, endpoint, window_start DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rate_limits_ip_endpoint 
ON rate_limit_tracking(ip_address, endpoint, window_start DESC);

-- Currency exchange indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_exchange_rates_pair_time 
ON exchange_rates(from_currency, to_currency, rate_timestamp DESC);

-- Business accounts indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_business_accounts_status 
ON business_accounts(status, created_at DESC) 
WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_business_accounts_merchant_id 
ON business_accounts(merchant_id) 
WHERE merchant_id IS NOT NULL;

-- Performance indexes for JOINs
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_wallet_join 
ON payment_transactions(wallet_id) 
WHERE wallet_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_payment_join 
ON wallet_transactions(payment_transaction_id) 
WHERE payment_transaction_id IS NOT NULL;

-- Text search indexes for transaction descriptions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_description_search 
ON payment_transactions USING gin(to_tsvector('english', description));

-- BRIN indexes for time-series data (very efficient for large tables)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_created_brin 
ON payment_transactions USING brin(created_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_created_brin 
ON audit_logs USING brin(created_at);

-- Partial unique indexes for business constraints
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_default_unique 
ON payment_methods(user_id) 
WHERE is_default = true AND is_active = true;

-- Function-based indexes for computed queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_date_trunc_day 
ON payment_transactions(date_trunc('day', created_at), status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_transactions_date_trunc_hour 
ON payment_transactions(date_trunc('hour', created_at), status) 
WHERE created_at > CURRENT_DATE - INTERVAL '7 days';

-- Analyze tables to update statistics after index creation
ANALYZE payment_transactions;
ANALYZE wallet_transactions;
ANALYZE payment_methods;
ANALYZE recurring_payments;
ANALYZE refunds;
ANALYZE disputes;
ANALYZE webhook_events;
ANALYZE audit_logs;
ANALYZE settlements;
ANALYZE kyc_verifications;