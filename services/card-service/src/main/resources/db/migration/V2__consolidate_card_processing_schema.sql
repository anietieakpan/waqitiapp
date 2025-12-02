-- Card Service Consolidated Schema - Phase 2
-- Date: 2025-11-09
-- Description: Consolidates card-processing-service schema into card-service
-- This migration adds all unique tables from card-processing-service and enhances existing tables

-- ============================================================================
-- PART 1: ENHANCE EXISTING TABLES FROM CARD-SERVICE WITH CARD-PROCESSING FIELDS
-- ============================================================================

-- Enhance card table with card_account fields from card-processing-service
ALTER TABLE card ADD COLUMN IF NOT EXISTS product_id VARCHAR(100);
ALTER TABLE card ADD COLUMN IF NOT EXISTS pan_token VARCHAR(100) UNIQUE;
ALTER TABLE card ADD COLUMN IF NOT EXISTS embossed_name VARCHAR(255);
ALTER TABLE card ADD COLUMN IF NOT EXISTS ledger_balance DECIMAL(18, 2) DEFAULT 0;
ALTER TABLE card ADD COLUMN IF NOT EXISTS cash_limit DECIMAL(18, 2) DEFAULT 0;
ALTER TABLE card ADD COLUMN IF NOT EXISTS daily_spend_limit DECIMAL(18, 2);
ALTER TABLE card ADD COLUMN IF NOT EXISTS monthly_spend_limit DECIMAL(18, 2);
ALTER TABLE card ADD COLUMN IF NOT EXISTS last_transaction_date TIMESTAMP;
ALTER TABLE card ADD COLUMN IF NOT EXISTS last_payment_date TIMESTAMP;
ALTER TABLE card ADD COLUMN IF NOT EXISTS payment_due_date DATE;
ALTER TABLE card ADD COLUMN IF NOT EXISTS overlimit_fee DECIMAL(18, 2) DEFAULT 0;
ALTER TABLE card ADD COLUMN IF NOT EXISTS late_payment_fee DECIMAL(18, 2) DEFAULT 0;
ALTER TABLE card ADD COLUMN IF NOT EXISTS foreign_transaction_fee_rate DECIMAL(5, 4) DEFAULT 0;
ALTER TABLE card ADD COLUMN IF NOT EXISTS is_international_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE card ADD COLUMN IF NOT EXISTS is_online_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE card ADD COLUMN IF NOT EXISTS replacement_card_id VARCHAR(50);

-- Add indexes for new card fields
CREATE INDEX IF NOT EXISTS idx_card_product ON card(product_id);
CREATE INDEX IF NOT EXISTS idx_card_pan_token ON card(pan_token);
CREATE INDEX IF NOT EXISTS idx_card_last_transaction ON card(last_transaction_date DESC);

-- Enhance card_transaction table with card-processing fields
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS account_id UUID;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS card_token VARCHAR(100);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS transaction_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS billing_amount DECIMAL(18, 2);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS billing_currency VARCHAR(3);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS acquirer_id VARCHAR(100);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS acquirer_reference VARCHAR(100);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS pos_condition_code VARCHAR(2);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS network_reference VARCHAR(100);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS retrieval_reference VARCHAR(12);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS system_trace_number VARCHAR(6);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS processing_code VARCHAR(6);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS point_of_service_data JSONB;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS card_acceptor_data JSONB;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS additional_data JSONB;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS fraud_score INTEGER DEFAULT 0;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS risk_assessment JSONB;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS dispute_eligible BOOLEAN DEFAULT TRUE;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS is_reversal BOOLEAN DEFAULT FALSE;
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS original_transaction_id VARCHAR(100);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS batch_id VARCHAR(100);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS settlement_amount DECIMAL(18, 2);
ALTER TABLE card_transaction ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(3);

-- Add indexes for enhanced transaction fields
CREATE INDEX IF NOT EXISTS idx_card_transaction_status ON card_transaction(status);
CREATE INDEX IF NOT EXISTS idx_card_transaction_network_ref ON card_transaction(network_reference);
CREATE INDEX IF NOT EXISTS idx_card_transaction_retrieval_ref ON card_transaction(retrieval_reference);
CREATE INDEX IF NOT EXISTS idx_card_transaction_batch ON card_transaction(batch_id);
CREATE INDEX IF NOT EXISTS idx_card_transaction_fraud_score ON card_transaction(fraud_score DESC) WHERE fraud_score >= 70;

-- Enhance card_authorization table with card-processing fields
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS transaction_id VARCHAR(100);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS account_id UUID;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS available_balance_before DECIMAL(18, 2);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS available_balance_after DECIMAL(18, 2);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS authorization_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS decline_reason VARCHAR(100);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS risk_score INTEGER DEFAULT 0;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS fraud_check_result VARCHAR(20);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS velocity_check_result VARCHAR(20);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS limit_check_result VARCHAR(20);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS balance_check_result VARCHAR(20);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS merchant_verification_result VARCHAR(20);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS authorization_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS expiry_timestamp TIMESTAMP;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS reversal_timestamp TIMESTAMP;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS settlement_timestamp TIMESTAMP;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS is_partial_auth BOOLEAN DEFAULT FALSE;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS partial_amount DECIMAL(18, 2);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS hold_amount DECIMAL(18, 2);
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS processor_response JSONB;
ALTER TABLE card_authorization ADD COLUMN IF NOT EXISTS network_response JSONB;

-- Add indexes for enhanced authorization fields
CREATE INDEX IF NOT EXISTS idx_card_authorization_transaction ON card_authorization(transaction_id);
CREATE INDEX IF NOT EXISTS idx_card_authorization_account ON card_authorization(account_id);
CREATE INDEX IF NOT EXISTS idx_card_authorization_auth_status ON card_authorization(authorization_status);
CREATE INDEX IF NOT EXISTS idx_card_authorization_timestamp ON card_authorization(authorization_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_card_authorization_risk_score ON card_authorization(risk_score DESC) WHERE risk_score >= 50;

-- ============================================================================
-- PART 2: ADD UNIQUE TABLES FROM CARD-PROCESSING-SERVICE
-- ============================================================================

-- Card Product Table (from card-processing-service)
CREATE TABLE IF NOT EXISTS card_product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(100) UNIQUE NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_type VARCHAR(50) NOT NULL,
    card_network VARCHAR(50) NOT NULL,
    bin_range VARCHAR(20) NOT NULL,
    card_level VARCHAR(50),
    issuer_id VARCHAR(100) NOT NULL,
    program_manager VARCHAR(100),
    processor VARCHAR(100) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    spending_limits JSONB,
    transaction_limits JSONB,
    fee_structure JSONB,
    rewards_program JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    activation_date DATE,
    expiration_date DATE,
    terms_and_conditions TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_card_product_type ON card_product(product_type);
CREATE INDEX idx_card_product_network ON card_product(card_network);
CREATE INDEX idx_card_product_active ON card_product(is_active) WHERE is_active = true;
CREATE INDEX idx_card_product_issuer ON card_product(issuer_id);

-- Card Settlement Table
CREATE TABLE IF NOT EXISTS card_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id VARCHAR(100) UNIQUE NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    settlement_amount DECIMAL(18, 2) NOT NULL,
    settlement_currency VARCHAR(3) NOT NULL,
    settlement_date DATE NOT NULL,
    settlement_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    interchange_fee DECIMAL(18, 2) DEFAULT 0,
    network_fee DECIMAL(18, 2) DEFAULT 0,
    processor_fee DECIMAL(18, 2) DEFAULT 0,
    total_fees DECIMAL(18, 2) DEFAULT 0,
    net_settlement_amount DECIMAL(18, 2) NOT NULL,
    batch_id VARCHAR(100),
    acquirer_settlement_id VARCHAR(100),
    issuer_settlement_id VARCHAR(100),
    settlement_reference VARCHAR(100),
    reconciliation_status VARCHAR(20) DEFAULT 'PENDING',
    reconciled_at TIMESTAMP,
    discrepancy_amount DECIMAL(18, 2) DEFAULT 0,
    discrepancy_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_transaction FOREIGN KEY (transaction_id) REFERENCES card_transaction(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_settlement_card FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

CREATE INDEX idx_card_settlement_transaction ON card_settlement(transaction_id);
CREATE INDEX idx_card_settlement_card ON card_settlement(card_id);
CREATE INDEX idx_card_settlement_status ON card_settlement(settlement_status);
CREATE INDEX idx_card_settlement_date ON card_settlement(settlement_date DESC);
CREATE INDEX idx_card_settlement_batch ON card_settlement(batch_id);
CREATE INDEX idx_card_settlement_reconciliation ON card_settlement(reconciliation_status);

-- Card Dispute Table
CREATE TABLE IF NOT EXISTS card_dispute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id VARCHAR(100) UNIQUE NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    dispute_type VARCHAR(50) NOT NULL,
    dispute_reason VARCHAR(100) NOT NULL,
    dispute_amount DECIMAL(18, 2) NOT NULL,
    dispute_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    dispute_category VARCHAR(50) NOT NULL,
    chargeback_reason_code VARCHAR(10),
    liability_shift VARCHAR(20),
    merchant_response_deadline DATE,
    cardholder_response_deadline DATE,
    network_dispute_id VARCHAR(100),
    case_number VARCHAR(100),
    dispute_date DATE NOT NULL,
    transaction_date DATE NOT NULL,
    merchant_name VARCHAR(255),
    merchant_id VARCHAR(100),
    supporting_documents TEXT[],
    cardholder_statement TEXT,
    merchant_response TEXT,
    provisional_credit_amount DECIMAL(18, 2) DEFAULT 0,
    provisional_credit_date DATE,
    resolution_amount DECIMAL(18, 2),
    resolution_reason VARCHAR(100),
    resolved_date DATE,
    resolved_by VARCHAR(100),
    chargeback_fee DECIMAL(18, 2) DEFAULT 0,
    representment_fee DECIMAL(18, 2) DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_transaction FOREIGN KEY (transaction_id) REFERENCES card_transaction(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_dispute_card FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

CREATE INDEX idx_card_dispute_transaction ON card_dispute(transaction_id);
CREATE INDEX idx_card_dispute_card ON card_dispute(card_id);
CREATE INDEX idx_card_dispute_user ON card_dispute(user_id);
CREATE INDEX idx_card_dispute_status ON card_dispute(dispute_status);
CREATE INDEX idx_card_dispute_type ON card_dispute(dispute_type);
CREATE INDEX idx_card_dispute_date ON card_dispute(dispute_date DESC);
CREATE INDEX idx_card_dispute_case_number ON card_dispute(case_number);

-- Card Fraud Rule Table
CREATE TABLE IF NOT EXISTS card_fraud_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id VARCHAR(100) UNIQUE NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    rule_category VARCHAR(100) NOT NULL,
    rule_description TEXT,
    rule_logic JSONB NOT NULL,
    risk_score INTEGER NOT NULL,
    action VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    false_positive_rate DECIMAL(5, 4) DEFAULT 0,
    detection_rate DECIMAL(5, 4) DEFAULT 0,
    last_tuned_date DATE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_card_fraud_rule_type ON card_fraud_rule(rule_type);
CREATE INDEX idx_card_fraud_rule_category ON card_fraud_rule(rule_category);
CREATE INDEX idx_card_fraud_rule_active ON card_fraud_rule(is_active) WHERE is_active = true;
CREATE INDEX idx_card_fraud_rule_priority ON card_fraud_rule(priority);

-- Card Fraud Alert Table
CREATE TABLE IF NOT EXISTS card_fraud_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    rule_id VARCHAR(100) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    alert_severity VARCHAR(20) NOT NULL,
    risk_score INTEGER NOT NULL,
    alert_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    alert_reason VARCHAR(255) NOT NULL,
    triggered_rules TEXT[] NOT NULL,
    recommended_action VARCHAR(100),
    investigation_notes TEXT,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    resolution_action VARCHAR(100),
    false_positive BOOLEAN DEFAULT FALSE,
    feedback_provided BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fraud_alert_transaction FOREIGN KEY (transaction_id) REFERENCES card_transaction(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_fraud_alert_card FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE,
    CONSTRAINT fk_fraud_alert_rule FOREIGN KEY (rule_id) REFERENCES card_fraud_rule(rule_id) ON DELETE CASCADE
);

CREATE INDEX idx_card_fraud_alert_transaction ON card_fraud_alert(transaction_id);
CREATE INDEX idx_card_fraud_alert_card ON card_fraud_alert(card_id);
CREATE INDEX idx_card_fraud_alert_rule ON card_fraud_alert(rule_id);
CREATE INDEX idx_card_fraud_alert_status ON card_fraud_alert(alert_status);
CREATE INDEX idx_card_fraud_alert_severity ON card_fraud_alert(alert_severity);
CREATE INDEX idx_card_fraud_alert_risk_score ON card_fraud_alert(risk_score DESC) WHERE risk_score >= 70;

-- Card Velocity Limit Table
CREATE TABLE IF NOT EXISTS card_velocity_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    limit_id VARCHAR(100) UNIQUE NOT NULL,
    card_id VARCHAR(50),
    product_id VARCHAR(100),
    limit_type VARCHAR(50) NOT NULL,
    limit_scope VARCHAR(50) NOT NULL,
    time_window VARCHAR(20) NOT NULL,
    transaction_count_limit INTEGER,
    amount_limit DECIMAL(18, 2),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    merchant_category_codes TEXT[],
    transaction_types TEXT[],
    channel_restrictions TEXT[],
    geographic_restrictions JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_velocity_limit_card FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE,
    CONSTRAINT fk_velocity_limit_product FOREIGN KEY (product_id) REFERENCES card_product(product_id) ON DELETE CASCADE
);

CREATE INDEX idx_card_velocity_limit_card ON card_velocity_limit(card_id);
CREATE INDEX idx_card_velocity_limit_product ON card_velocity_limit(product_id);
CREATE INDEX idx_card_velocity_limit_type ON card_velocity_limit(limit_type);
CREATE INDEX idx_card_velocity_limit_active ON card_velocity_limit(is_active) WHERE is_active = true;
CREATE INDEX idx_card_velocity_limit_scope ON card_velocity_limit(limit_scope);

-- Card PIN Management Table
CREATE TABLE IF NOT EXISTS card_pin_management (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pin_id VARCHAR(100) UNIQUE NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    pin_encrypted VARCHAR(255) NOT NULL,
    pin_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    pin_set_date DATE NOT NULL,
    pin_attempts INTEGER DEFAULT 0,
    max_attempts INTEGER DEFAULT 3,
    locked_until TIMESTAMP,
    last_attempt_date DATE,
    pin_change_required BOOLEAN DEFAULT FALSE,
    pin_expiry_date DATE,
    previous_pins_encrypted TEXT[],
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pin_management_card FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

CREATE INDEX idx_card_pin_management_card ON card_pin_management(card_id);
CREATE INDEX idx_card_pin_management_user ON card_pin_management(user_id);
CREATE INDEX idx_card_pin_management_status ON card_pin_management(pin_status);

-- Card Token Management Table
CREATE TABLE IF NOT EXISTS card_token_management (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id VARCHAR(100) UNIQUE NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    token_type VARCHAR(50) NOT NULL,
    payment_token VARCHAR(100) UNIQUE NOT NULL,
    token_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    token_requestor VARCHAR(100) NOT NULL,
    provisioning_method VARCHAR(50) NOT NULL,
    wallet_provider VARCHAR(100),
    device_id VARCHAR(100),
    device_type VARCHAR(50),
    device_fingerprint VARCHAR(255),
    token_expiry_date DATE NOT NULL,
    last_used_date DATE,
    usage_count INTEGER DEFAULT 0,
    provisioned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    suspended_at TIMESTAMP,
    suspension_reason VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_management_card FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

CREATE INDEX idx_card_token_management_card ON card_token_management(card_id);
CREATE INDEX idx_card_token_management_token ON card_token_management(payment_token);
CREATE INDEX idx_card_token_management_status ON card_token_management(token_status);
CREATE INDEX idx_card_token_management_requestor ON card_token_management(token_requestor);
CREATE INDEX idx_card_token_management_wallet ON card_token_management(wallet_provider);

-- Card Processing Analytics Table
CREATE TABLE IF NOT EXISTS card_processing_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_transactions INTEGER DEFAULT 0,
    approved_transactions INTEGER DEFAULT 0,
    declined_transactions INTEGER DEFAULT 0,
    transaction_volume DECIMAL(18, 2) DEFAULT 0,
    authorization_rate DECIMAL(5, 4),
    settlement_rate DECIMAL(5, 4),
    fraud_rate DECIMAL(5, 4),
    dispute_rate DECIMAL(5, 4),
    average_transaction_amount DECIMAL(18, 2),
    by_card_network JSONB,
    by_merchant_category JSONB,
    by_transaction_type JSONB,
    by_decline_reason JSONB,
    fraud_detection_metrics JSONB,
    velocity_limit_violations INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_card_processing_analytics_period ON card_processing_analytics(period_end DESC);
CREATE INDEX idx_card_processing_analytics_start ON card_processing_analytics(period_start);

-- Card Processing Statistics Table
CREATE TABLE IF NOT EXISTS card_processing_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_cards_issued INTEGER DEFAULT 0,
    active_cards INTEGER DEFAULT 0,
    blocked_cards INTEGER DEFAULT 0,
    total_transactions BIGINT DEFAULT 0,
    transaction_volume DECIMAL(18, 2) DEFAULT 0,
    approval_rate DECIMAL(5, 4),
    fraud_detection_rate DECIMAL(5, 4),
    dispute_resolution_rate DECIMAL(5, 4),
    by_card_type JSONB,
    by_network JSONB,
    top_decline_reasons JSONB,
    performance_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_card_processing_period UNIQUE (period_start, period_end)
);

CREATE INDEX idx_card_processing_statistics_period ON card_processing_statistics(period_end DESC);

-- ============================================================================
-- PART 3: ADD UPDATE TRIGGERS FOR NEW TABLES
-- ============================================================================

CREATE TRIGGER update_card_product_updated_at BEFORE UPDATE ON card_product
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_settlement_updated_at BEFORE UPDATE ON card_settlement
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_dispute_updated_at BEFORE UPDATE ON card_dispute
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_fraud_rule_updated_at BEFORE UPDATE ON card_fraud_rule
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_fraud_alert_updated_at BEFORE UPDATE ON card_fraud_alert
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_velocity_limit_updated_at BEFORE UPDATE ON card_velocity_limit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_pin_management_updated_at BEFORE UPDATE ON card_pin_management
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_token_management_updated_at BEFORE UPDATE ON card_token_management
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- PART 4: ADD FOREIGN KEY CONSTRAINT FOR CARD PRODUCT
-- ============================================================================

ALTER TABLE card ADD CONSTRAINT fk_card_product
    FOREIGN KEY (product_id) REFERENCES card_product(product_id) ON DELETE SET NULL;

-- ============================================================================
-- CONSOLIDATION COMPLETE
-- ============================================================================
-- Total tables in consolidated card-service: 15
--   From original card-service: 6 (enhanced)
--   From card-processing-service: 9 (added)
-- Schema consolidation status: COMPLETE
-- ============================================================================
