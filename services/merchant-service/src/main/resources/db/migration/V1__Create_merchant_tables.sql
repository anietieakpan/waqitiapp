-- Merchant Service Database Schema
-- Creates tables for merchant management, payment processing, and settlements

-- Merchants table
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id VARCHAR(50) UNIQUE NOT NULL,
    
    -- Business information
    business_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    business_type VARCHAR(50) NOT NULL CHECK (business_type IN ('SOLE_PROPRIETOR', 'PARTNERSHIP', 'CORPORATION', 'LLC', 'NON_PROFIT')),
    industry_category VARCHAR(100) NOT NULL,
    
    -- Contact information
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(20),
    website_url VARCHAR(500),
    
    -- Address
    business_address_line1 VARCHAR(255) NOT NULL,
    business_address_line2 VARCHAR(255),
    business_city VARCHAR(100) NOT NULL,
    business_state VARCHAR(100) NOT NULL,
    business_postal_code VARCHAR(20) NOT NULL,
    business_country VARCHAR(2) NOT NULL DEFAULT 'US',
    
    -- Tax information
    tax_id VARCHAR(50),
    tax_id_type VARCHAR(20) CHECK (tax_id_type IN ('EIN', 'SSN', 'VAT')),
    
    -- Business verification
    verification_status VARCHAR(20) DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    verification_documents JSONB,
    verified_at TIMESTAMP WITH TIME ZONE,
    verified_by UUID,
    
    -- Account status
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED')),
    risk_level VARCHAR(20) DEFAULT 'LOW' CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    
    -- Business metrics
    monthly_volume_limit DECIMAL(15,2) DEFAULT 50000.00,
    current_monthly_volume DECIMAL(15,2) DEFAULT 0.00,
    total_processed_volume DECIMAL(15,2) DEFAULT 0.00,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);

-- Merchant payment settings
CREATE TABLE merchant_payment_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    
    -- Accepted payment methods
    accepts_cards BOOLEAN DEFAULT TRUE,
    accepts_bank_transfers BOOLEAN DEFAULT TRUE,
    accepts_digital_wallets BOOLEAN DEFAULT TRUE,
    accepts_crypto BOOLEAN DEFAULT FALSE,
    
    -- Processing preferences
    auto_settlement BOOLEAN DEFAULT TRUE,
    settlement_frequency VARCHAR(20) DEFAULT 'DAILY' CHECK (settlement_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    minimum_settlement_amount DECIMAL(10,2) DEFAULT 1.00,
    
    -- Fee structure
    transaction_fee_percentage DECIMAL(5,4) DEFAULT 2.9000, -- 2.90%
    fixed_fee_per_transaction DECIMAL(6,2) DEFAULT 0.30,
    chargeback_fee DECIMAL(6,2) DEFAULT 15.00,
    
    -- Limits
    single_transaction_limit DECIMAL(15,2) DEFAULT 10000.00,
    daily_transaction_limit DECIMAL(15,2) DEFAULT 50000.00,
    monthly_transaction_limit DECIMAL(15,2) DEFAULT 1000000.00,
    
    -- Security settings
    require_cvv BOOLEAN DEFAULT TRUE,
    require_address_verification BOOLEAN DEFAULT FALSE,
    enable_3d_secure BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(merchant_id)
);

-- Merchant bank accounts for settlements
CREATE TABLE merchant_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    
    -- Bank account details
    account_name VARCHAR(255) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    routing_number VARCHAR(20) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    
    -- Account type and currency
    account_type VARCHAR(20) DEFAULT 'CHECKING' CHECK (account_type IN ('CHECKING', 'SAVINGS', 'BUSINESS')),
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Verification
    verification_status VARCHAR(20) DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING', 'VERIFIED', 'FAILED')),
    verification_attempts INTEGER DEFAULT 0,
    verified_at TIMESTAMP WITH TIME ZONE,
    
    -- Status
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Merchant transactions
CREATE TABLE merchant_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    transaction_reference VARCHAR(100) UNIQUE NOT NULL,
    
    -- Transaction details
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('PAYMENT', 'REFUND', 'CHARGEBACK', 'ADJUSTMENT')),
    
    -- Customer information
    customer_id UUID,
    customer_email VARCHAR(255),
    customer_name VARCHAR(255),
    
    -- Payment method
    payment_method VARCHAR(50) NOT NULL,
    payment_method_details JSONB,
    
    -- Processing details
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED')),
    gateway_transaction_id VARCHAR(100),
    gateway_response JSONB,
    
    -- Fees
    merchant_fee DECIMAL(10,4) NOT NULL DEFAULT 0.00,
    gateway_fee DECIMAL(10,4) DEFAULT 0.00,
    net_amount DECIMAL(15,2) NOT NULL,
    
    -- Risk assessment
    fraud_score DECIMAL(3,2) DEFAULT 0.00,
    risk_flags JSONB,
    
    -- Timing
    processed_at TIMESTAMP WITH TIME ZONE,
    settled_at TIMESTAMP WITH TIME ZONE,
    settlement_id UUID,
    
    -- External references
    order_id VARCHAR(100),
    invoice_id VARCHAR(100),
    description TEXT,
    metadata JSONB,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Settlement batches
CREATE TABLE settlement_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    batch_reference VARCHAR(100) UNIQUE NOT NULL,
    
    -- Settlement details
    settlement_date DATE NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    total_fees DECIMAL(15,2) NOT NULL,
    net_amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Transaction counts
    transaction_count INTEGER NOT NULL DEFAULT 0,
    refund_count INTEGER DEFAULT 0,
    chargeback_count INTEGER DEFAULT 0,
    
    -- Bank details
    bank_account_id UUID REFERENCES merchant_bank_accounts(id),
    
    -- Status
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    
    -- Processing details
    initiated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    bank_confirmation_number VARCHAR(100),
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- API keys and webhooks
CREATE TABLE merchant_api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    
    -- Key details
    key_name VARCHAR(255) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL, -- Hashed version of API key
    key_prefix VARCHAR(20) NOT NULL, -- First few characters for identification
    
    -- Permissions
    permissions JSONB NOT NULL, -- Array of allowed operations
    environment VARCHAR(20) DEFAULT 'LIVE' CHECK (environment IN ('TEST', 'LIVE')),
    
    -- Usage tracking
    last_used_at TIMESTAMP WITH TIME ZONE,
    usage_count INTEGER DEFAULT 0,
    
    -- Security
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    
    UNIQUE(merchant_id, key_name)
);

-- Webhook endpoints
CREATE TABLE merchant_webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    
    -- Webhook details
    endpoint_url VARCHAR(500) NOT NULL,
    secret_key VARCHAR(255) NOT NULL, -- For signature verification
    
    -- Event subscriptions
    subscribed_events JSONB NOT NULL, -- Array of event types
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Delivery settings
    retry_attempts INTEGER DEFAULT 3,
    timeout_seconds INTEGER DEFAULT 30,
    
    -- Status tracking
    last_successful_delivery TIMESTAMP WITH TIME ZONE,
    consecutive_failures INTEGER DEFAULT 0,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Webhook delivery logs
CREATE TABLE webhook_delivery_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id UUID NOT NULL REFERENCES merchant_webhooks(id) ON DELETE CASCADE,
    
    -- Delivery details
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    
    -- Response details
    http_status_code INTEGER,
    response_body TEXT,
    response_time_ms INTEGER,
    
    -- Delivery status
    delivery_status VARCHAR(20) NOT NULL CHECK (delivery_status IN ('SUCCESS', 'FAILED', 'RETRY')),
    attempt_number INTEGER NOT NULL DEFAULT 1,
    error_message TEXT,
    
    -- Timing
    attempted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    next_retry_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for performance
CREATE INDEX idx_merchants_merchant_id ON merchants(merchant_id);
CREATE INDEX idx_merchants_business_name ON merchants(business_name);
CREATE INDEX idx_merchants_status ON merchants(status);
CREATE INDEX idx_merchants_verification_status ON merchants(verification_status);

CREATE INDEX idx_merchant_bank_accounts_merchant_id ON merchant_bank_accounts(merchant_id);
CREATE INDEX idx_merchant_bank_accounts_primary ON merchant_bank_accounts(merchant_id, is_primary) WHERE is_primary = TRUE;

CREATE INDEX idx_merchant_transactions_merchant_id ON merchant_transactions(merchant_id);
CREATE INDEX idx_merchant_transactions_reference ON merchant_transactions(transaction_reference);
CREATE INDEX idx_merchant_transactions_status ON merchant_transactions(status);
CREATE INDEX idx_merchant_transactions_created_at ON merchant_transactions(created_at);
CREATE INDEX idx_merchant_transactions_settlement ON merchant_transactions(settlement_id) WHERE settlement_id IS NOT NULL;

CREATE INDEX idx_settlement_batches_merchant_id ON settlement_batches(merchant_id);
CREATE INDEX idx_settlement_batches_settlement_date ON settlement_batches(settlement_date);
CREATE INDEX idx_settlement_batches_status ON settlement_batches(status);

CREATE INDEX idx_merchant_api_keys_merchant_id ON merchant_api_keys(merchant_id);
CREATE INDEX idx_merchant_api_keys_active ON merchant_api_keys(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_merchant_api_keys_prefix ON merchant_api_keys(key_prefix);

CREATE INDEX idx_merchant_webhooks_merchant_id ON merchant_webhooks(merchant_id);
CREATE INDEX idx_merchant_webhooks_active ON merchant_webhooks(is_active) WHERE is_active = TRUE;

CREATE INDEX idx_webhook_delivery_logs_webhook_id ON webhook_delivery_logs(webhook_id);
CREATE INDEX idx_webhook_delivery_logs_attempted_at ON webhook_delivery_logs(attempted_at);
CREATE INDEX idx_webhook_delivery_logs_status ON webhook_delivery_logs(delivery_status);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_merchants_updated_at BEFORE UPDATE
    ON merchants FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_merchant_payment_settings_updated_at BEFORE UPDATE
    ON merchant_payment_settings FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_merchant_bank_accounts_updated_at BEFORE UPDATE
    ON merchant_bank_accounts FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_merchant_transactions_updated_at BEFORE UPDATE
    ON merchant_transactions FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_settlement_batches_updated_at BEFORE UPDATE
    ON settlement_batches FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_merchant_webhooks_updated_at BEFORE UPDATE
    ON merchant_webhooks FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Function to calculate net amount
CREATE OR REPLACE FUNCTION calculate_net_amount()
RETURNS TRIGGER AS $$
BEGIN
    NEW.net_amount = NEW.amount - NEW.merchant_fee - COALESCE(NEW.gateway_fee, 0);
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER calculate_net_amount_trigger BEFORE INSERT OR UPDATE
    ON merchant_transactions FOR EACH ROW EXECUTE PROCEDURE calculate_net_amount();

-- Comments for documentation
COMMENT ON TABLE merchants IS 'Merchant accounts and business information';
COMMENT ON TABLE merchant_payment_settings IS 'Payment processing settings and preferences';
COMMENT ON TABLE merchant_bank_accounts IS 'Bank accounts for merchant settlements';
COMMENT ON TABLE merchant_transactions IS 'All merchant payment transactions';
COMMENT ON TABLE settlement_batches IS 'Settlement batches for merchant payouts';
COMMENT ON TABLE merchant_api_keys IS 'API keys for merchant integration';
COMMENT ON TABLE merchant_webhooks IS 'Webhook endpoints for event notifications';
COMMENT ON TABLE webhook_delivery_logs IS 'Webhook delivery attempt logs';