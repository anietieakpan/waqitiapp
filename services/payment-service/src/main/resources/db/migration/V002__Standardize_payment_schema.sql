-- Standardize Payment Service Database Schema
-- This migration standardizes all payment-related tables to follow Waqiti patterns

-- =====================================================================
-- PAYMENT METHODS TABLE STANDARDIZATION
-- =====================================================================

-- Add missing audit and version columns to payment_methods table
ALTER TABLE payment_methods 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are using TIMESTAMP WITH TIME ZONE
ALTER TABLE payment_methods 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_payment_methods_updated_at 
    BEFORE UPDATE ON payment_methods
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add business rule constraints
ALTER TABLE payment_methods 
ADD CONSTRAINT check_payment_method_type 
CHECK (method_type IN ('BANK_ACCOUNT', 'CREDIT_CARD', 'DEBIT_CARD', 'DIGITAL_WALLET', 'CRYPTOCURRENCY'));

ALTER TABLE payment_methods 
ADD CONSTRAINT check_payment_method_status 
CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED', 'BLOCKED'));

ALTER TABLE payment_methods 
ADD CONSTRAINT check_payment_method_verification 
CHECK (verification_status IN ('PENDING', 'VERIFIED', 'FAILED'));

-- =====================================================================
-- PAYMENT PROCESSING TABLE STANDARDIZATION
-- =====================================================================

-- Standardize payment_processing table
ALTER TABLE payment_processing 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE payment_processing 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN processed_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN settled_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE payment_processing 
ALTER COLUMN amount TYPE DECIMAL(19,4),
ALTER COLUMN processing_fee TYPE DECIMAL(19,4),
ALTER COLUMN settled_amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_payment_processing_updated_at 
    BEFORE UPDATE ON payment_processing
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add business rule constraints
ALTER TABLE payment_processing 
ADD CONSTRAINT check_payment_processing_status 
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED'));

ALTER TABLE payment_processing 
ADD CONSTRAINT check_processing_amount_positive 
CHECK (amount > 0);

ALTER TABLE payment_processing 
ADD CONSTRAINT check_processing_fee_non_negative 
CHECK (processing_fee >= 0);

-- =====================================================================
-- PAYMENT GATEWAYS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize payment_gateways table
ALTER TABLE payment_gateways 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE payment_gateways 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN last_health_check TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_payment_gateways_updated_at 
    BEFORE UPDATE ON payment_gateways
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE payment_gateways 
ADD CONSTRAINT check_gateway_health_status 
CHECK (health_status IN ('HEALTHY', 'DEGRADED', 'DOWN'));

-- =====================================================================
-- PAYMENT WEBHOOKS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize payment_webhooks table
ALTER TABLE payment_webhooks 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Ensure timestamp columns are standardized
ALTER TABLE payment_webhooks 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN processed_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_payment_webhooks_updated_at 
    BEFORE UPDATE ON payment_webhooks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE payment_webhooks 
ADD CONSTRAINT check_webhook_processing_status 
CHECK (processing_status IN ('PENDING', 'PROCESSED', 'FAILED', 'IGNORED'));

-- =====================================================================
-- PAYMENT REFUNDS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize payment_refunds table
ALTER TABLE payment_refunds 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Ensure timestamp columns are standardized
ALTER TABLE payment_refunds 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN processed_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN completed_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE payment_refunds 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_payment_refunds_updated_at 
    BEFORE UPDATE ON payment_refunds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE payment_refunds 
ADD CONSTRAINT check_refund_type 
CHECK (refund_type IN ('FULL', 'PARTIAL', 'CHARGEBACK'));

ALTER TABLE payment_refunds 
ADD CONSTRAINT check_refund_status 
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'));

ALTER TABLE payment_refunds 
ADD CONSTRAINT check_refund_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- PAYMENT DISPUTES TABLE STANDARDIZATION
-- =====================================================================

-- Standardize payment_disputes table
ALTER TABLE payment_disputes 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE payment_disputes 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE payment_disputes 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_payment_disputes_updated_at 
    BEFORE UPDATE ON payment_disputes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE payment_disputes 
ADD CONSTRAINT check_dispute_type 
CHECK (dispute_type IN ('CHARGEBACK', 'RETRIEVAL_REQUEST', 'PRE_ARBITRATION', 'ARBITRATION'));

ALTER TABLE payment_disputes 
ADD CONSTRAINT check_dispute_status 
CHECK (status IN ('RECEIVED', 'UNDER_REVIEW', 'ACCEPTED', 'DISPUTED', 'WON', 'LOST'));

ALTER TABLE payment_disputes 
ADD CONSTRAINT check_dispute_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- PAYMENT REQUESTS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize payment_requests table
ALTER TABLE payment_requests 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE payment_requests 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN expiry_date TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE payment_requests 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_payment_requests_updated_at 
    BEFORE UPDATE ON payment_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE payment_requests 
ADD CONSTRAINT check_payment_request_status 
CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED'));

ALTER TABLE payment_requests 
ADD CONSTRAINT check_payment_request_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- SCHEDULED PAYMENTS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize scheduled_payments table
ALTER TABLE scheduled_payments 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE scheduled_payments 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE scheduled_payments 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_scheduled_payments_updated_at 
    BEFORE UPDATE ON scheduled_payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE scheduled_payments 
ADD CONSTRAINT check_scheduled_payment_status 
CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'));

ALTER TABLE scheduled_payments 
ADD CONSTRAINT check_scheduled_payment_frequency 
CHECK (frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'));

ALTER TABLE scheduled_payments 
ADD CONSTRAINT check_scheduled_payment_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- SCHEDULED PAYMENT EXECUTIONS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize scheduled_payment_executions table
ALTER TABLE scheduled_payment_executions 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Ensure timestamp columns are standardized
ALTER TABLE scheduled_payment_executions 
ALTER COLUMN execution_date TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE scheduled_payment_executions 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_scheduled_payment_executions_updated_at 
    BEFORE UPDATE ON scheduled_payment_executions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE scheduled_payment_executions 
ADD CONSTRAINT check_execution_status 
CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'));

ALTER TABLE scheduled_payment_executions 
ADD CONSTRAINT check_execution_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- SPLIT PAYMENTS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize split_payments table
ALTER TABLE split_payments 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE split_payments 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN expiry_date TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE split_payments 
ALTER COLUMN total_amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_split_payments_updated_at 
    BEFORE UPDATE ON split_payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE split_payments 
ADD CONSTRAINT check_split_payment_status 
CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED'));

ALTER TABLE split_payments 
ADD CONSTRAINT check_split_payment_total_amount_positive 
CHECK (total_amount > 0);

-- =====================================================================
-- SPLIT PAYMENT PARTICIPANTS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize split_payment_participants table
ALTER TABLE split_payment_participants 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE split_payment_participants 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN payment_date TYPE TIMESTAMP WITH TIME ZONE;

-- Ensure amount uses standard financial precision
ALTER TABLE split_payment_participants 
ALTER COLUMN amount TYPE DECIMAL(19,4);

-- Add updated_at trigger
CREATE TRIGGER update_split_payment_participants_updated_at 
    BEFORE UPDATE ON split_payment_participants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE split_payment_participants 
ADD CONSTRAINT check_participant_amount_positive 
CHECK (amount > 0);

-- =====================================================================
-- STANDARDIZED INDEXES FOR PERFORMANCE
-- =====================================================================

-- Payment methods indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_payment_methods_verification_status ON payment_methods(verification_status);
CREATE INDEX IF NOT EXISTS idx_payment_methods_is_default ON payment_methods(is_default);
CREATE INDEX IF NOT EXISTS idx_payment_methods_created_at ON payment_methods(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_methods_updated_at ON payment_methods(updated_at);

-- Payment processing indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_payment_processing_updated_at ON payment_processing(updated_at);
CREATE INDEX IF NOT EXISTS idx_payment_processing_settlement_date ON payment_processing(settlement_date);
CREATE INDEX IF NOT EXISTS idx_payment_processing_amount ON payment_processing(amount);

-- Payment gateways indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_payment_gateways_priority ON payment_gateways(priority);
CREATE INDEX IF NOT EXISTS idx_payment_gateways_updated_at ON payment_gateways(updated_at);

-- Payment webhooks indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_payment_webhooks_event_type ON payment_webhooks(event_type);
CREATE INDEX IF NOT EXISTS idx_payment_webhooks_updated_at ON payment_webhooks(updated_at);

-- Payment refunds indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_payment_refunds_created_at ON payment_refunds(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_refunds_updated_at ON payment_refunds(updated_at);
CREATE INDEX IF NOT EXISTS idx_payment_refunds_amount ON payment_refunds(amount);

-- Payment disputes indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_payment_disputes_created_at ON payment_disputes(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_disputes_updated_at ON payment_disputes(updated_at);
CREATE INDEX IF NOT EXISTS idx_payment_disputes_amount ON payment_disputes(amount);

-- Payment requests indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_payment_requests_created_at ON payment_requests(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_requests_updated_at ON payment_requests(updated_at);
CREATE INDEX IF NOT EXISTS idx_payment_requests_amount ON payment_requests(amount);

-- Scheduled payments indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_scheduled_payments_frequency ON scheduled_payments(frequency);
CREATE INDEX IF NOT EXISTS idx_scheduled_payments_created_at ON scheduled_payments(created_at);
CREATE INDEX IF NOT EXISTS idx_scheduled_payments_updated_at ON scheduled_payments(updated_at);

-- Split payments indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_split_payments_created_at ON split_payments(created_at);
CREATE INDEX IF NOT EXISTS idx_split_payments_updated_at ON split_payments(updated_at);
CREATE INDEX IF NOT EXISTS idx_split_payments_total_amount ON split_payments(total_amount);

-- Split payment participants indexes (additional to existing)
CREATE INDEX IF NOT EXISTS idx_split_payment_participants_created_at ON split_payment_participants(created_at);
CREATE INDEX IF NOT EXISTS idx_split_payment_participants_updated_at ON split_payment_participants(updated_at);
CREATE INDEX IF NOT EXISTS idx_split_payment_participants_amount ON split_payment_participants(amount);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_payment_processing_status_amount ON payment_processing(status, amount);
CREATE INDEX IF NOT EXISTS idx_payment_methods_user_status ON payment_methods(user_id, status);
CREATE INDEX IF NOT EXISTS idx_payment_disputes_status_amount ON payment_disputes(status, amount);

-- =====================================================================
-- UNIQUE CONSTRAINTS FOR DATA INTEGRITY
-- =====================================================================

-- Ensure unique payment method IDs
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_methods_method_id_unique 
ON payment_methods(method_id);

-- Ensure unique payment processing IDs
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_processing_payment_id_unique 
ON payment_processing(payment_id);

-- Ensure unique refund IDs
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_refunds_refund_id_unique 
ON payment_refunds(refund_id);

-- Ensure unique dispute IDs
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_disputes_dispute_id_unique 
ON payment_disputes(dispute_id);

-- =====================================================================
-- FINANCIAL INTEGRITY CONSTRAINTS
-- =====================================================================

-- Add constraint for payment processing to payment methods
ALTER TABLE payment_processing 
ADD CONSTRAINT fk_payment_processing_method 
FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id);

-- Add constraint for scheduled payment executions
ALTER TABLE scheduled_payment_executions 
ADD CONSTRAINT fk_scheduled_payment_executions_scheduled_payment 
FOREIGN KEY (scheduled_payment_id) REFERENCES scheduled_payments(id);

-- Add constraint for split payment participants
ALTER TABLE split_payment_participants 
ADD CONSTRAINT fk_split_payment_participants_split_payment 
FOREIGN KEY (split_payment_id) REFERENCES split_payments(id);

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON TABLE payment_methods IS 'User payment methods and instruments';
COMMENT ON TABLE payment_processing IS 'Payment processing and gateway transactions';
COMMENT ON TABLE payment_gateways IS 'Payment gateway configurations';
COMMENT ON TABLE payment_webhooks IS 'Payment gateway webhook processing';
COMMENT ON TABLE payment_refunds IS 'Payment refunds and reversals';
COMMENT ON TABLE payment_disputes IS 'Payment disputes and chargebacks';
COMMENT ON TABLE payment_requests IS 'Payment requests between users';
COMMENT ON TABLE scheduled_payments IS 'Recurring and scheduled payments';
COMMENT ON TABLE scheduled_payment_executions IS 'Individual executions of scheduled payments';
COMMENT ON TABLE split_payments IS 'Split bill payments';
COMMENT ON TABLE split_payment_participants IS 'Participants in split payments';

COMMENT ON COLUMN payment_methods.encrypted_details IS 'Encrypted sensitive payment details';
COMMENT ON COLUMN payment_processing.amount IS 'Transaction amount with 4 decimal precision';
COMMENT ON COLUMN payment_processing.processing_fee IS 'Processing fee charged by gateway';
COMMENT ON COLUMN payment_refunds.amount IS 'Refund amount with 4 decimal precision';
COMMENT ON COLUMN payment_disputes.amount IS 'Disputed amount with 4 decimal precision';

-- =====================================================================
-- CLEANUP DUPLICATE CONSTRAINTS/INDEXES
-- =====================================================================

-- Drop any duplicate or conflicting constraints that might exist
-- (This would be customized based on actual existing constraints)