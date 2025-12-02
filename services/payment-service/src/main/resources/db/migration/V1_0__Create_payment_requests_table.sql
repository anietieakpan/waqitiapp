-- ============================================================================
-- Payment Service Database Schema - Payment Requests Table
-- Version: 1.0
-- Description: Core payment requests table with optimized indexes
-- ============================================================================

-- Create payment_requests table
CREATE TABLE IF NOT EXISTS payment_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    recipient_id UUID,
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE,
    description TEXT,
    metadata JSONB,
    external_reference_id VARCHAR(255),
    failure_reason TEXT,
    failure_code VARCHAR(50),
    fraud_score DECIMAL(5,4) CHECK (fraud_score >= 0 AND fraud_score <= 1),
    ip_address VARCHAR(45),
    user_agent TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    completed_at TIMESTAMP,

    CONSTRAINT chk_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'REQUIRES_ACTION', 'REQUIRES_CONFIRMATION',
        'AUTHORIZED', 'SUCCEEDED', 'FAILED', 'CANCELED', 'EXPIRED',
        'REFUNDED', 'PARTIALLY_REFUNDED'
    ))
);

-- ============================================================================
-- Indexes for Performance Optimization
-- ============================================================================

-- Index for user payment history queries
CREATE INDEX idx_payment_requests_user
ON payment_requests(user_id, created_at DESC);

-- Index for status-based queries (admin dashboard, processing jobs)
CREATE INDEX idx_payment_requests_status
ON payment_requests(status, created_at DESC);

-- Index for idempotency key lookups (duplicate prevention)
CREATE UNIQUE INDEX idx_payment_requests_idempotency
ON payment_requests(idempotency_key)
WHERE idempotency_key IS NOT NULL;

-- Index for expiration cleanup jobs
CREATE INDEX idx_payment_requests_expires
ON payment_requests(expires_at)
WHERE expires_at IS NOT NULL AND status IN ('PENDING', 'PROCESSING');

-- Index for recipient payment queries
CREATE INDEX idx_payment_requests_recipient
ON payment_requests(recipient_id, created_at DESC)
WHERE recipient_id IS NOT NULL;

-- Index for external reference lookups (Stripe, PayPal, etc.)
CREATE INDEX idx_payment_requests_external_ref
ON payment_requests(external_reference_id)
WHERE external_reference_id IS NOT NULL;

-- Index for fraud analysis queries
CREATE INDEX idx_payment_requests_fraud
ON payment_requests(fraud_score DESC, created_at DESC)
WHERE fraud_score IS NOT NULL AND fraud_score >= 0.7;

-- Index for IP-based fraud detection
CREATE INDEX idx_payment_requests_ip
ON payment_requests(ip_address, created_at DESC)
WHERE ip_address IS NOT NULL;

-- Composite index for velocity checks (user + time window)
CREATE INDEX idx_payment_requests_velocity
ON payment_requests(user_id, status, created_at DESC)
WHERE status = 'SUCCEEDED';

-- Index for amount-based analytics
CREATE INDEX idx_payment_requests_amount
ON payment_requests(amount DESC, created_at DESC)
WHERE status = 'SUCCEEDED';

-- ============================================================================
-- Comments for Documentation
-- ============================================================================

COMMENT ON TABLE payment_requests IS 'Core payment requests table with full audit trail and fraud detection';
COMMENT ON COLUMN payment_requests.id IS 'Primary key (UUID)';
COMMENT ON COLUMN payment_requests.user_id IS 'User initiating the payment';
COMMENT ON COLUMN payment_requests.recipient_id IS 'Payment recipient (nullable for certain payment types)';
COMMENT ON COLUMN payment_requests.amount IS 'Payment amount with 4 decimal precision';
COMMENT ON COLUMN payment_requests.currency IS 'ISO 4217 currency code';
COMMENT ON COLUMN payment_requests.status IS 'Current payment status';
COMMENT ON COLUMN payment_requests.payment_method IS 'Payment method used (card, bank, crypto, etc.)';
COMMENT ON COLUMN payment_requests.idempotency_key IS 'Unique key for duplicate prevention';
COMMENT ON COLUMN payment_requests.metadata IS 'Flexible JSONB metadata';
COMMENT ON COLUMN payment_requests.external_reference_id IS 'External provider reference (Stripe, PayPal)';
COMMENT ON COLUMN payment_requests.fraud_score IS 'Fraud detection score (0.0 - 1.0)';
COMMENT ON COLUMN payment_requests.version IS 'Optimistic locking version';
COMMENT ON COLUMN payment_requests.expires_at IS 'Expiration timestamp for pending requests';

-- ============================================================================
-- Grants (adjust based on your database user setup)
-- ============================================================================

-- GRANT SELECT, INSERT, UPDATE, DELETE ON payment_requests TO payment_service_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO payment_service_app;

-- ============================================================================
-- Initial Data (Optional - Add default test data for development)
-- ============================================================================

-- INSERT INTO payment_requests (user_id, amount, currency, status, payment_method, idempotency_key)
-- VALUES
--   (gen_random_uuid(), 100.00, 'USD', 'SUCCEEDED', 'card', 'test-idem-key-001'),
--   (gen_random_uuid(), 250.00, 'USD', 'PENDING', 'bank', 'test-idem-key-002');
