-- Performance optimization indexes for payment service high-traffic queries
-- This migration adds specialized indexes for common payment query patterns

-- =====================================================================
-- COMPOSITE INDEXES FOR PAYMENT QUERIES
-- =====================================================================

-- Payment request queries by status and date
CREATE INDEX IF NOT EXISTS idx_payment_requests_status_created_at ON payment_requests(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_requests_user_status ON payment_requests(requestor_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_requests_recipient_status ON payment_requests(recipient_id, status, created_at DESC);

-- Payment processing optimization
CREATE INDEX IF NOT EXISTS idx_payment_requests_processing ON payment_requests(status, expires_at) 
    WHERE status IN ('PENDING', 'PROCESSING');

-- Payment history queries
CREATE INDEX IF NOT EXISTS idx_payment_requests_user_history ON payment_requests(requestor_id, created_at DESC) 
    INCLUDE (id, recipient_id, amount, currency, description, status);

-- =====================================================================
-- PAYMENT TRANSACTIONS INDEXES
-- =====================================================================

-- Transaction status and timing
CREATE INDEX IF NOT EXISTS idx_transactions_status_created_at ON transactions(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_type_status ON transactions(transaction_type, status, created_at DESC);

-- Payment analytics queries
CREATE INDEX IF NOT EXISTS idx_transactions_amount_date ON transactions(created_at DESC, amount DESC) 
    WHERE status = 'COMPLETED';
CREATE INDEX IF NOT EXISTS idx_transactions_currency_date ON transactions(currency, created_at DESC) 
    WHERE status = 'COMPLETED';

-- Fraud detection queries
CREATE INDEX IF NOT EXISTS idx_transactions_fraud_analysis ON transactions(user_id, amount DESC, created_at DESC) 
    WHERE status = 'COMPLETED';
CREATE INDEX IF NOT EXISTS idx_transactions_large_amounts ON transactions(amount DESC, created_at DESC) 
    WHERE amount > 1000.00;

-- =====================================================================
-- PAYMENT PROVIDER INDEXES
-- =====================================================================

-- Provider performance tracking
CREATE INDEX IF NOT EXISTS idx_payment_providers_status ON payment_providers(status, priority DESC);
CREATE INDEX IF NOT EXISTS idx_payment_providers_active ON payment_providers(provider_type, status) 
    WHERE status = 'ACTIVE';

-- Provider transaction history
CREATE INDEX IF NOT EXISTS idx_provider_transactions_provider_date ON provider_transactions(provider_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_provider_transactions_status ON provider_transactions(status, created_at DESC);

-- =====================================================================
-- WEBHOOK AND NOTIFICATION INDEXES
-- =====================================================================

-- Webhook delivery tracking
CREATE INDEX IF NOT EXISTS idx_webhooks_status_created ON webhooks(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhooks_retry ON webhooks(retry_count, next_retry_at) 
    WHERE status = 'FAILED' AND retry_count < 3;

-- Payment notifications
CREATE INDEX IF NOT EXISTS idx_payment_notifications_user ON payment_notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_notifications_status ON payment_notifications(status, created_at DESC);

-- =====================================================================
-- PARTIAL INDEXES FOR SPECIFIC CONDITIONS
-- =====================================================================

-- Active payment requests only
CREATE INDEX IF NOT EXISTS idx_payment_requests_active ON payment_requests(created_at DESC) 
    WHERE status IN ('PENDING', 'PROCESSING');

-- Failed payments for retry logic
CREATE INDEX IF NOT EXISTS idx_payment_requests_failed ON payment_requests(updated_at DESC) 
    WHERE status = 'FAILED';

-- Expired payment requests for cleanup
CREATE INDEX IF NOT EXISTS idx_payment_requests_expired ON payment_requests(expires_at) 
    WHERE expires_at < NOW() AND status = 'PENDING';

-- High-value transactions
CREATE INDEX IF NOT EXISTS idx_transactions_high_value ON transactions(created_at DESC, amount DESC) 
    WHERE amount > 5000.00;

-- =====================================================================
-- COVERING INDEXES FOR API ENDPOINTS
-- =====================================================================

-- Payment request list API
CREATE INDEX IF NOT EXISTS idx_payment_requests_api_list ON payment_requests(requestor_id, created_at DESC) 
    INCLUDE (id, recipient_id, amount, currency, description, status, expires_at);

-- Transaction history API
CREATE INDEX IF NOT EXISTS idx_transactions_api_history ON transactions(user_id, created_at DESC) 
    INCLUDE (id, transaction_type, amount, currency, status, description);

-- Payment analytics API
CREATE INDEX IF NOT EXISTS idx_transactions_analytics ON transactions(user_id, created_at DESC) 
    INCLUDE (amount, currency, transaction_type, status);

-- =====================================================================
-- DATE PARTITIONING SUPPORT INDEXES
-- =====================================================================

-- Monthly partitioning support
CREATE INDEX IF NOT EXISTS idx_payment_requests_monthly ON payment_requests(DATE_TRUNC('month', created_at), status);
CREATE INDEX IF NOT EXISTS idx_transactions_monthly ON transactions(DATE_TRUNC('month', created_at), user_id);

-- Daily analytics support
CREATE INDEX IF NOT EXISTS idx_transactions_daily_analytics ON transactions(DATE_TRUNC('day', created_at), status, currency) 
    WHERE status = 'COMPLETED';

-- =====================================================================
-- FULL-TEXT SEARCH INDEXES
-- =====================================================================

-- Payment description search
CREATE INDEX IF NOT EXISTS idx_payment_requests_description_search ON payment_requests 
    USING gin(to_tsvector('english', description)) WHERE description IS NOT NULL;

-- Transaction reference search
CREATE INDEX IF NOT EXISTS idx_transactions_reference_search ON transactions 
    USING gin(to_tsvector('english', reference_number || ' ' || COALESCE(description, '')));

-- =====================================================================
-- FUNCTIONAL INDEXES
-- =====================================================================

-- Amount range queries
CREATE INDEX IF NOT EXISTS idx_payment_requests_amount_ranges ON payment_requests(
    CASE 
        WHEN amount < 100 THEN 'small'
        WHEN amount < 1000 THEN 'medium'
        WHEN amount < 5000 THEN 'large'
        ELSE 'xlarge'
    END,
    created_at DESC
);

-- Currency conversion support
CREATE INDEX IF NOT EXISTS idx_transactions_usd_equivalent ON transactions(
    CASE 
        WHEN currency = 'USD' THEN amount
        ELSE amount * exchange_rate_to_usd
    END DESC,
    created_at DESC
) WHERE status = 'COMPLETED';

-- =====================================================================
-- DISPUTE AND REFUND INDEXES
-- =====================================================================

-- Dispute tracking
CREATE INDEX IF NOT EXISTS idx_payment_disputes_status ON payment_disputes(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_disputes_payment ON payment_disputes(payment_id, status);

-- Refund processing
CREATE INDEX IF NOT EXISTS idx_payment_refunds_status ON payment_refunds(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_refunds_payment ON payment_refunds(original_payment_id, status);

-- Chargeback management
CREATE INDEX IF NOT EXISTS idx_payment_chargebacks_status ON payment_chargebacks(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_chargebacks_payment ON payment_chargebacks(payment_id, status);

-- =====================================================================
-- COMPLIANCE AND AUDIT INDEXES
-- =====================================================================

-- AML/BSA reporting
CREATE INDEX IF NOT EXISTS idx_transactions_aml_reporting ON transactions(created_at, amount, currency) 
    WHERE amount > 10000.00 OR (amount > 3000.00 AND currency != 'USD');

-- Suspicious activity monitoring
CREATE INDEX IF NOT EXISTS idx_transactions_suspicious ON transactions(user_id, created_at DESC, amount DESC) 
    WHERE status = 'COMPLETED';

-- Audit trail queries
CREATE INDEX IF NOT EXISTS idx_payment_audit_trail ON payment_requests(updated_by, updated_at DESC) 
    WHERE updated_by IS NOT NULL;

-- =====================================================================
-- MAINTENANCE INDEXES
-- =====================================================================

-- Cleanup old completed payments
CREATE INDEX IF NOT EXISTS idx_payment_requests_cleanup ON payment_requests(created_at) 
    WHERE status = 'COMPLETED' AND created_at < NOW() - INTERVAL '2 years';

-- Archive old transactions
CREATE INDEX IF NOT EXISTS idx_transactions_archive ON transactions(created_at) 
    WHERE created_at < NOW() - INTERVAL '7 years';

-- Failed webhook cleanup
CREATE INDEX IF NOT EXISTS idx_webhooks_cleanup ON webhooks(created_at) 
    WHERE status = 'FAILED' AND created_at < NOW() - INTERVAL '30 days';

-- =====================================================================
-- STATISTICS OPTIMIZATION
-- =====================================================================

-- Update statistics for better query planning
ANALYZE payment_requests;
ANALYZE transactions;
ANALYZE payment_providers;
ANALYZE provider_transactions;
ANALYZE webhooks;
ANALYZE payment_notifications;

-- Set higher statistics targets for frequently queried columns
ALTER TABLE payment_requests ALTER COLUMN status SET STATISTICS 1000;
ALTER TABLE payment_requests ALTER COLUMN requestor_id SET STATISTICS 1000;
ALTER TABLE payment_requests ALTER COLUMN recipient_id SET STATISTICS 1000;
ALTER TABLE payment_requests ALTER COLUMN created_at SET STATISTICS 1000;

ALTER TABLE transactions ALTER COLUMN status SET STATISTICS 1000;
ALTER TABLE transactions ALTER COLUMN user_id SET STATISTICS 1000;
ALTER TABLE transactions ALTER COLUMN transaction_type SET STATISTICS 1000;
ALTER TABLE transactions ALTER COLUMN created_at SET STATISTICS 1000;

-- =====================================================================
-- QUERY HINTS FOR COMPLEX QUERIES
-- =====================================================================

-- Create materialized view for payment analytics (optional, commented out)
-- CREATE MATERIALIZED VIEW payment_analytics_daily AS
-- SELECT 
--     DATE_TRUNC('day', created_at) as date,
--     currency,
--     COUNT(*) as transaction_count,
--     SUM(amount) as total_amount,
--     AVG(amount) as avg_amount,
--     MAX(amount) as max_amount
-- FROM transactions 
-- WHERE status = 'COMPLETED' 
-- GROUP BY DATE_TRUNC('day', created_at), currency;

-- CREATE UNIQUE INDEX idx_payment_analytics_daily_unique ON payment_analytics_daily(date, currency);

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON INDEX idx_payment_requests_status_created_at IS 'Optimizes payment request queries by status and date';
COMMENT ON INDEX idx_transactions_user_date IS 'Optimizes user transaction history queries';
COMMENT ON INDEX idx_payment_requests_processing IS 'Optimizes active payment processing queries';
COMMENT ON INDEX idx_transactions_fraud_analysis IS 'Supports fraud detection algorithms';
COMMENT ON INDEX idx_payment_requests_api_list IS 'Covering index for payment list API';
COMMENT ON INDEX idx_transactions_aml_reporting IS 'Supports AML/BSA compliance reporting';
COMMENT ON INDEX idx_payment_requests_description_search IS 'Full-text search for payment descriptions';