-- Create payment status transitions tracking table
-- Tracks all status changes for payments and transfers
CREATE TABLE IF NOT EXISTS payment_status_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL, -- PAYMENT, TRANSFER, INSTANT_TRANSFER, PAYMENT_REQUEST
    entity_id UUID NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    transition_reason VARCHAR(500),
    metadata JSONB,
    performed_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create payment method verification table
CREATE TABLE IF NOT EXISTS payment_method_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_method_id UUID NOT NULL,
    verification_type VARCHAR(50) NOT NULL, -- MICRO_DEPOSIT, INSTANT_VERIFICATION, MANUAL
    verification_status VARCHAR(50) NOT NULL, -- PENDING, VERIFIED, FAILED, EXPIRED
    verification_code VARCHAR(100),
    attempts INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    verified_at TIMESTAMP,
    expires_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create payment routing preferences table
CREATE TABLE IF NOT EXISTS payment_routing_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    payment_type VARCHAR(50) NOT NULL, -- INSTANT, STANDARD, INTERNATIONAL
    preferred_method VARCHAR(50), -- FEDNOW, RTP, ZELLE, ACH, WIRE
    fallback_methods TEXT[], -- Array of fallback methods in priority order
    cost_preference VARCHAR(20), -- LOWEST_COST, FASTEST, BALANCED
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, payment_type)
);

-- Create payment service health monitoring table
CREATE TABLE IF NOT EXISTS payment_service_health (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL,
    service_type VARCHAR(50) NOT NULL, -- INTERNAL, EXTERNAL, NETWORK
    health_status VARCHAR(20) NOT NULL, -- UP, DOWN, DEGRADED, UNKNOWN
    response_time_ms INTEGER,
    error_rate DECIMAL(5, 2),
    success_rate DECIMAL(5, 2),
    last_check_time TIMESTAMP NOT NULL,
    consecutive_failures INTEGER DEFAULT 0,
    circuit_breaker_status VARCHAR(20), -- CLOSED, OPEN, HALF_OPEN
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create payment reconciliation table
CREATE TABLE IF NOT EXISTS payment_reconciliations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_date DATE NOT NULL,
    payment_type VARCHAR(50) NOT NULL,
    internal_count INTEGER NOT NULL DEFAULT 0,
    external_count INTEGER NOT NULL DEFAULT 0,
    internal_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    external_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    discrepancy_count INTEGER NOT NULL DEFAULT 0,
    discrepancy_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(50) NOT NULL, -- PENDING, IN_PROGRESS, COMPLETED, FAILED
    reconciled_by UUID,
    reconciled_at TIMESTAMP,
    report_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(reconciliation_date, payment_type)
);

-- Indexes for performance
CREATE INDEX idx_payment_status_transitions_entity ON payment_status_transitions(entity_type, entity_id);
CREATE INDEX idx_payment_status_transitions_created_at ON payment_status_transitions(created_at DESC);
CREATE INDEX idx_payment_status_transitions_from_to ON payment_status_transitions(from_status, to_status);

CREATE INDEX idx_payment_method_verifications_method ON payment_method_verifications(payment_method_id);
CREATE INDEX idx_payment_method_verifications_status ON payment_method_verifications(verification_status);
CREATE INDEX idx_payment_method_verifications_expires ON payment_method_verifications(expires_at);

CREATE INDEX idx_payment_routing_preferences_user ON payment_routing_preferences(user_id);
CREATE INDEX idx_payment_routing_preferences_type ON payment_routing_preferences(payment_type);

CREATE INDEX idx_payment_service_health_service ON payment_service_health(service_name, service_type);
CREATE INDEX idx_payment_service_health_status ON payment_service_health(health_status);
CREATE INDEX idx_payment_service_health_check_time ON payment_service_health(last_check_time DESC);

CREATE INDEX idx_payment_reconciliations_date ON payment_reconciliations(reconciliation_date);
CREATE INDEX idx_payment_reconciliations_status ON payment_reconciliations(status);

-- Triggers for updated_at
CREATE TRIGGER update_payment_method_verifications_updated_at BEFORE UPDATE ON payment_method_verifications
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_routing_preferences_updated_at BEFORE UPDATE ON payment_routing_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_reconciliations_updated_at BEFORE UPDATE ON payment_reconciliations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add comments
COMMENT ON TABLE payment_status_transitions IS 'Tracks all status changes for payment entities';
COMMENT ON TABLE payment_method_verifications IS 'Verification records for payment methods';
COMMENT ON TABLE payment_routing_preferences IS 'User preferences for payment routing';
COMMENT ON TABLE payment_service_health IS 'Health monitoring for payment services';
COMMENT ON TABLE payment_reconciliations IS 'Daily reconciliation records for payments';