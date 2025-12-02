-- Payment Service Database Schema
-- Creates tables for payment processing, methods, and reconciliation

-- Payment methods
CREATE TABLE payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    method_id VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    method_type VARCHAR(20) NOT NULL CHECK (method_type IN ('BANK_ACCOUNT', 'CREDIT_CARD', 'DEBIT_CARD', 'DIGITAL_WALLET', 'CRYPTOCURRENCY')),
    provider VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED', 'BLOCKED')) DEFAULT 'ACTIVE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    display_name VARCHAR(100),
    masked_details VARCHAR(100),
    encrypted_details TEXT,
    verification_status VARCHAR(20) NOT NULL CHECK (verification_status IN ('PENDING', 'VERIFIED', 'FAILED')) DEFAULT 'PENDING',
    verification_data JSONB,
    expires_at DATE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Payment processing
CREATE TABLE payment_processing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id VARCHAR(50) UNIQUE NOT NULL,
    transaction_id VARCHAR(50) NOT NULL,
    payment_method_id UUID REFERENCES payment_methods(id),
    processor_name VARCHAR(50) NOT NULL,
    processor_transaction_id VARCHAR(100),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    processing_fee DECIMAL(19,4) DEFAULT 0,
    exchange_rate DECIMAL(19,8),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED')) DEFAULT 'PENDING',
    failure_reason TEXT,
    gateway_response JSONB,
    risk_assessment JSONB,
    settlement_date DATE,
    settled_amount DECIMAL(19,4),
    chargeback_risk INTEGER CHECK (chargeback_risk >= 0 AND chargeback_risk <= 100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    settled_at TIMESTAMP WITH TIME ZONE
);

-- Payment gateways configuration
CREATE TABLE payment_gateways (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway_name VARCHAR(50) UNIQUE NOT NULL,
    provider VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    supported_methods JSONB NOT NULL,
    supported_currencies JSONB NOT NULL,
    configuration JSONB NOT NULL,
    fee_structure JSONB,
    rate_limits JSONB,
    webhook_config JSONB,
    priority INTEGER NOT NULL DEFAULT 0,
    health_status VARCHAR(20) NOT NULL CHECK (health_status IN ('HEALTHY', 'DEGRADED', 'DOWN')) DEFAULT 'HEALTHY',
    last_health_check TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Payment webhooks
CREATE TABLE payment_webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id VARCHAR(50) UNIQUE NOT NULL,
    payment_id VARCHAR(50) NOT NULL,
    gateway_name VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    webhook_payload JSONB NOT NULL,
    signature VARCHAR(255),
    processing_status VARCHAR(20) NOT NULL CHECK (processing_status IN ('PENDING', 'PROCESSED', 'FAILED', 'IGNORED')) DEFAULT 'PENDING',
    processing_attempts INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Payment refunds
CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id VARCHAR(50) UNIQUE NOT NULL,
    original_payment_id VARCHAR(50) NOT NULL,
    refund_type VARCHAR(20) NOT NULL CHECK (refund_type IN ('FULL', 'PARTIAL', 'CHARGEBACK')),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')) DEFAULT 'PENDING',
    processor_refund_id VARCHAR(100),
    initiated_by UUID NOT NULL,
    approved_by UUID,
    gateway_response JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Payment disputes and chargebacks
CREATE TABLE payment_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id VARCHAR(50) UNIQUE NOT NULL,
    payment_id VARCHAR(50) NOT NULL,
    dispute_type VARCHAR(20) NOT NULL CHECK (dispute_type IN ('CHARGEBACK', 'RETRIEVAL_REQUEST', 'PRE_ARBITRATION', 'ARBITRATION')),
    reason_code VARCHAR(20),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECEIVED', 'UNDER_REVIEW', 'ACCEPTED', 'DISPUTED', 'WON', 'LOST')) DEFAULT 'RECEIVED',
    due_date DATE,
    evidence JSONB,
    response_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    liability_shift BOOLEAN NOT NULL DEFAULT FALSE,
    processor_dispute_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create Payment Requests Table
CREATE TABLE payment_requests (
    id UUID PRIMARY KEY,
    requestor_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    reference_number VARCHAR(50),
    transaction_id UUID,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create Scheduled Payments Table
CREATE TABLE scheduled_payments (
    id UUID PRIMARY KEY,
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    source_wallet_id UUID NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_execution_date DATE,
    last_execution_date DATE,
    total_executions INT NOT NULL,
    completed_executions INT NOT NULL,
    max_executions INT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create Scheduled Payment Executions Table
CREATE TABLE scheduled_payment_executions (
    id UUID PRIMARY KEY,
    scheduled_payment_id UUID NOT NULL REFERENCES scheduled_payments(id),
    transaction_id UUID,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    execution_date TIMESTAMP NOT NULL,
    created_by VARCHAR(100)
);

-- Create Split Payments Table
CREATE TABLE split_payments (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    total_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Create Split Payment Participants Table
CREATE TABLE split_payment_participants (
    id UUID PRIMARY KEY,
    split_payment_id UUID NOT NULL REFERENCES split_payments(id),
    user_id UUID NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    transaction_id UUID,
    paid BOOLEAN NOT NULL,
    payment_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create Indexes
CREATE INDEX idx_payment_requests_requestor_id ON payment_requests(requestor_id);
CREATE INDEX idx_payment_requests_recipient_id ON payment_requests(recipient_id);
CREATE INDEX idx_payment_requests_status ON payment_requests(status);
CREATE INDEX idx_payment_requests_reference_number ON payment_requests(reference_number);
CREATE INDEX idx_payment_requests_expiry_date ON payment_requests(expiry_date);

CREATE INDEX idx_scheduled_payments_sender_id ON scheduled_payments(sender_id);
CREATE INDEX idx_scheduled_payments_recipient_id ON scheduled_payments(recipient_id);
CREATE INDEX idx_scheduled_payments_status ON scheduled_payments(status);
CREATE INDEX idx_scheduled_payments_next_execution_date ON scheduled_payments(next_execution_date);

CREATE INDEX idx_split_payments_organizer_id ON split_payments(organizer_id);
CREATE INDEX idx_split_payments_status ON split_payments(status);
CREATE INDEX idx_split_payments_expiry_date ON split_payments(expiry_date);

CREATE INDEX idx_split_payment_participants_split_payment_id ON split_payment_participants(split_payment_id);
CREATE INDEX idx_split_payment_participants_user_id ON split_payment_participants(user_id);
CREATE INDEX idx_split_payment_participants_paid ON split_payment_participants(paid);

-- New indexes for enhanced payment tables
CREATE INDEX idx_payment_methods_user_id ON payment_methods(user_id);
CREATE INDEX idx_payment_methods_method_id ON payment_methods(method_id);
CREATE INDEX idx_payment_methods_type ON payment_methods(method_type);
CREATE INDEX idx_payment_methods_status ON payment_methods(status);

CREATE INDEX idx_payment_processing_payment_id ON payment_processing(payment_id);
CREATE INDEX idx_payment_processing_transaction_id ON payment_processing(transaction_id);
CREATE INDEX idx_payment_processing_status ON payment_processing(status);
CREATE INDEX idx_payment_processing_processor ON payment_processing(processor_name);
CREATE INDEX idx_payment_processing_created_at ON payment_processing(created_at);

CREATE INDEX idx_payment_gateways_name ON payment_gateways(gateway_name);
CREATE INDEX idx_payment_gateways_active ON payment_gateways(is_active);
CREATE INDEX idx_payment_gateways_health ON payment_gateways(health_status);

CREATE INDEX idx_payment_webhooks_payment_id ON payment_webhooks(payment_id);
CREATE INDEX idx_payment_webhooks_gateway ON payment_webhooks(gateway_name);
CREATE INDEX idx_payment_webhooks_status ON payment_webhooks(processing_status);
CREATE INDEX idx_payment_webhooks_created_at ON payment_webhooks(created_at);

CREATE INDEX idx_payment_refunds_payment_id ON payment_refunds(original_payment_id);
CREATE INDEX idx_payment_refunds_status ON payment_refunds(status);
CREATE INDEX idx_payment_refunds_type ON payment_refunds(refund_type);

CREATE INDEX idx_payment_disputes_payment_id ON payment_disputes(payment_id);
CREATE INDEX idx_payment_disputes_status ON payment_disputes(status);
CREATE INDEX idx_payment_disputes_type ON payment_disputes(dispute_type);
CREATE INDEX idx_payment_disputes_due_date ON payment_disputes(due_date);

-- Function for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_payment_methods_updated_at 
    BEFORE UPDATE ON payment_methods 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_processing_updated_at 
    BEFORE UPDATE ON payment_processing 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_gateways_updated_at 
    BEFORE UPDATE ON payment_gateways 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_disputes_updated_at 
    BEFORE UPDATE ON payment_disputes 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();