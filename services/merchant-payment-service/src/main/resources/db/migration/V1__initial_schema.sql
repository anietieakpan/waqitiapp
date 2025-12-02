-- Merchant Payment Service Initial Schema
-- Created: 2025-09-27
-- Description: Merchant payment processing and settlement schema

CREATE TABLE IF NOT EXISTS merchant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(100) UNIQUE NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    merchant_category_code VARCHAR(10) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    tax_id_encrypted VARCHAR(255) NOT NULL,
    website_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    onboarding_status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    settlement_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    settlement_frequency VARCHAR(20) DEFAULT 'DAILY',
    settlement_account_encrypted VARCHAR(255),
    processing_fee_percentage DECIMAL(5, 4) NOT NULL,
    flat_fee_per_transaction DECIMAL(10, 2) DEFAULT 0,
    monthly_volume_limit DECIMAL(15, 2),
    transaction_limit DECIMAL(15, 2),
    chargeback_count INTEGER DEFAULT 0,
    chargeback_ratio DECIMAL(5, 4) DEFAULT 0.0000,
    onboarded_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by VARCHAR(100),
    kyb_verified BOOLEAN DEFAULT FALSE,
    kyb_verified_at TIMESTAMP,
    pci_compliant BOOLEAN DEFAULT FALSE,
    pci_compliance_expires_at DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    suspended_at TIMESTAMP,
    suspension_reason TEXT
);

CREATE TABLE IF NOT EXISTS merchant_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(100) NOT NULL,
    contact_type VARCHAR(20) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_contact FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS merchant_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id VARCHAR(100) UNIQUE NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    location_name VARCHAR(255) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    phone_number VARCHAR(20),
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_location FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS merchant_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    location_id VARCHAR(100),
    customer_id UUID,
    payment_method VARCHAR(50) NOT NULL,
    card_last_four VARCHAR(4),
    transaction_type VARCHAR(50) NOT NULL DEFAULT 'SALE',
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    processing_fee DECIMAL(10, 2) NOT NULL,
    net_amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    authorization_code VARCHAR(50),
    terminal_id VARCHAR(50),
    pos_entry_mode VARCHAR(20),
    is_contactless BOOLEAN DEFAULT FALSE,
    is_3ds_verified BOOLEAN DEFAULT FALSE,
    risk_score DECIMAL(5, 4),
    fraud_flags TEXT[],
    order_id VARCHAR(100),
    invoice_number VARCHAR(100),
    description TEXT,
    customer_email VARCHAR(255),
    customer_phone VARCHAR(20),
    billing_address JSONB,
    shipping_address JSONB,
    metadata JSONB,
    settlement_batch_id VARCHAR(100),
    settled_at TIMESTAMP,
    refund_amount DECIMAL(15, 2) DEFAULT 0,
    is_refunded BOOLEAN DEFAULT FALSE,
    refunded_at TIMESTAMP,
    chargeback_amount DECIMAL(15, 2) DEFAULT 0,
    is_chargeback BOOLEAN DEFAULT FALSE,
    chargeback_date DATE,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_transaction FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS merchant_settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id VARCHAR(100) UNIQUE NOT NULL,
    batch_id VARCHAR(100) UNIQUE NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    settlement_date DATE NOT NULL,
    settlement_period_start DATE NOT NULL,
    settlement_period_end DATE NOT NULL,
    total_transactions INTEGER NOT NULL,
    gross_amount DECIMAL(15, 2) NOT NULL,
    total_fees DECIMAL(15, 2) NOT NULL,
    total_refunds DECIMAL(15, 2) NOT NULL,
    total_chargebacks DECIMAL(15, 2) NOT NULL,
    net_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settlement_account_encrypted VARCHAR(255) NOT NULL,
    payout_method VARCHAR(50) NOT NULL DEFAULT 'ACH',
    payout_reference VARCHAR(100),
    payout_initiated_at TIMESTAMP,
    payout_completed_at TIMESTAMP,
    payout_failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_settlement FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS merchant_refund (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id VARCHAR(100) UNIQUE NOT NULL,
    original_transaction_id VARCHAR(100) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    refund_amount DECIMAL(15, 2) NOT NULL,
    refund_reason VARCHAR(100),
    refund_notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by VARCHAR(100),
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_refund FOREIGN KEY (original_transaction_id) REFERENCES merchant_transaction(transaction_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS merchant_chargeback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chargeback_id VARCHAR(100) UNIQUE NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    chargeback_amount DECIMAL(15, 2) NOT NULL,
    chargeback_reason_code VARCHAR(20) NOT NULL,
    chargeback_reason TEXT NOT NULL,
    chargeback_date DATE NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    merchant_response TEXT,
    evidence_documents TEXT[],
    response_submitted_at TIMESTAMP,
    resolution VARCHAR(50),
    resolved_at TIMESTAMP,
    resolution_amount DECIMAL(15, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_chargeback FOREIGN KEY (transaction_id) REFERENCES merchant_transaction(transaction_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS merchant_api_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key_id VARCHAR(100) UNIQUE NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    key_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    permissions TEXT[] NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,
    usage_count INTEGER DEFAULT 0,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(100),
    CONSTRAINT fk_merchant_api_key FOREIGN KEY (merchant_id) REFERENCES merchant(merchant_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_merchant_status ON merchant(status);
CREATE INDEX idx_merchant_mcc ON merchant(merchant_category_code);
CREATE INDEX idx_merchant_risk ON merchant(risk_level);
CREATE INDEX idx_merchant_onboarding ON merchant(onboarding_status);

CREATE INDEX idx_merchant_contact_merchant ON merchant_contact(merchant_id);
CREATE INDEX idx_merchant_contact_email ON merchant_contact(email);

CREATE INDEX idx_merchant_location_merchant ON merchant_location(merchant_id);
CREATE INDEX idx_merchant_location_active ON merchant_location(is_active) WHERE is_active = true;

CREATE INDEX idx_merchant_txn_merchant ON merchant_transaction(merchant_id);
CREATE INDEX idx_merchant_txn_customer ON merchant_transaction(customer_id);
CREATE INDEX idx_merchant_txn_status ON merchant_transaction(status);
CREATE INDEX idx_merchant_txn_date ON merchant_transaction(transaction_date DESC);
CREATE INDEX idx_merchant_txn_settlement ON merchant_transaction(settlement_batch_id) WHERE settlement_batch_id IS NOT NULL;
CREATE INDEX idx_merchant_txn_fraud ON merchant_transaction(risk_score DESC) WHERE risk_score > 0.7;

CREATE INDEX idx_merchant_settlement_merchant ON merchant_settlement(merchant_id);
CREATE INDEX idx_merchant_settlement_date ON merchant_settlement(settlement_date DESC);
CREATE INDEX idx_merchant_settlement_status ON merchant_settlement(status);

CREATE INDEX idx_merchant_refund_transaction ON merchant_refund(original_transaction_id);
CREATE INDEX idx_merchant_refund_merchant ON merchant_refund(merchant_id);
CREATE INDEX idx_merchant_refund_status ON merchant_refund(status);

CREATE INDEX idx_merchant_chargeback_transaction ON merchant_chargeback(transaction_id);
CREATE INDEX idx_merchant_chargeback_merchant ON merchant_chargeback(merchant_id);
CREATE INDEX idx_merchant_chargeback_status ON merchant_chargeback(status);
CREATE INDEX idx_merchant_chargeback_due ON merchant_chargeback(due_date) WHERE status = 'OPEN';

CREATE INDEX idx_merchant_api_key_merchant ON merchant_api_key(merchant_id);
CREATE INDEX idx_merchant_api_key_hash ON merchant_api_key(key_hash);
CREATE INDEX idx_merchant_api_key_active ON merchant_api_key(is_active) WHERE is_active = true;

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_merchant_updated_at BEFORE UPDATE ON merchant
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_contact_updated_at BEFORE UPDATE ON merchant_contact
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_location_updated_at BEFORE UPDATE ON merchant_location
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_settlement_updated_at BEFORE UPDATE ON merchant_settlement
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_chargeback_updated_at BEFORE UPDATE ON merchant_chargeback
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();