-- Card Service Initial Schema
-- Created: 2025-09-27
-- Description: Card management, transactions, and lifecycle schema

CREATE TABLE IF NOT EXISTS card (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id VARCHAR(50) UNIQUE NOT NULL,
    card_number_encrypted VARCHAR(255) NOT NULL,
    card_number_last_four VARCHAR(4) NOT NULL,
    card_type VARCHAR(20) NOT NULL,
    card_brand VARCHAR(20) NOT NULL,
    user_id UUID NOT NULL,
    account_id UUID NOT NULL,
    card_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    expiry_date DATE NOT NULL,
    cvv_encrypted VARCHAR(255),
    pin_hash VARCHAR(255),
    pin_attempts INTEGER DEFAULT 0,
    pin_locked_until TIMESTAMP,
    credit_limit DECIMAL(15, 2),
    available_credit DECIMAL(15, 2),
    outstanding_balance DECIMAL(15, 2) DEFAULT 0,
    statement_balance DECIMAL(15, 2) DEFAULT 0,
    minimum_payment DECIMAL(15, 2) DEFAULT 0,
    payment_due_date DATE,
    interest_rate DECIMAL(5, 4),
    rewards_program VARCHAR(50),
    rewards_balance DECIMAL(15, 2) DEFAULT 0,
    is_contactless BOOLEAN DEFAULT TRUE,
    is_virtual BOOLEAN DEFAULT FALSE,
    replacement_reason VARCHAR(100),
    replaced_card_id VARCHAR(50),
    delivery_address TEXT,
    delivery_status VARCHAR(20),
    delivered_at TIMESTAMP,
    activated_at TIMESTAMP,
    blocked_at TIMESTAMP,
    blocked_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS card_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    merchant_id VARCHAR(100),
    merchant_name VARCHAR(255),
    merchant_category_code VARCHAR(10),
    merchant_country VARCHAR(3),
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settlement_date TIMESTAMP,
    authorization_code VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decline_reason VARCHAR(255),
    is_recurring BOOLEAN DEFAULT FALSE,
    is_international BOOLEAN DEFAULT FALSE,
    is_contactless BOOLEAN DEFAULT FALSE,
    is_3ds_verified BOOLEAN DEFAULT FALSE,
    risk_score DECIMAL(5, 4),
    fraud_flags TEXT[],
    cashback_amount DECIMAL(15, 2) DEFAULT 0,
    rewards_earned DECIMAL(15, 2) DEFAULT 0,
    fee DECIMAL(10, 2) DEFAULT 0,
    exchange_rate DECIMAL(18, 8),
    original_amount DECIMAL(15, 2),
    original_currency VARCHAR(3),
    terminal_id VARCHAR(50),
    pos_entry_mode VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_card FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS card_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id VARCHAR(50) NOT NULL,
    limit_type VARCHAR(50) NOT NULL,
    limit_period VARCHAR(20) NOT NULL,
    limit_amount DECIMAL(15, 2) NOT NULL,
    current_usage DECIMAL(15, 2) DEFAULT 0,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_limit FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE,
    CONSTRAINT unique_card_limit UNIQUE (card_id, limit_type, limit_period)
);

CREATE TABLE IF NOT EXISTS card_statement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id VARCHAR(100) UNIQUE NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    statement_period_start DATE NOT NULL,
    statement_period_end DATE NOT NULL,
    opening_balance DECIMAL(15, 2) NOT NULL,
    closing_balance DECIMAL(15, 2) NOT NULL,
    total_purchases DECIMAL(15, 2) DEFAULT 0,
    total_payments DECIMAL(15, 2) DEFAULT 0,
    total_fees DECIMAL(15, 2) DEFAULT 0,
    total_interest DECIMAL(15, 2) DEFAULT 0,
    total_cashback DECIMAL(15, 2) DEFAULT 0,
    minimum_payment_due DECIMAL(15, 2) NOT NULL,
    payment_due_date DATE NOT NULL,
    statement_generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_paid BOOLEAN DEFAULT FALSE,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_statement FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS card_authorization (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authorization_id VARCHAR(100) UNIQUE NOT NULL,
    card_id VARCHAR(50) NOT NULL,
    merchant_id VARCHAR(100),
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    authorization_code VARCHAR(50),
    expires_at TIMESTAMP NOT NULL,
    captured_amount DECIMAL(15, 2) DEFAULT 0,
    is_captured BOOLEAN DEFAULT FALSE,
    captured_at TIMESTAMP,
    is_reversed BOOLEAN DEFAULT FALSE,
    reversed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_authorization FOREIGN KEY (card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS card_replacement_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(100) UNIQUE NOT NULL,
    old_card_id VARCHAR(50) NOT NULL,
    new_card_id VARCHAR(50),
    user_id UUID NOT NULL,
    reason VARCHAR(100) NOT NULL,
    request_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivery_address TEXT NOT NULL,
    priority VARCHAR(20) DEFAULT 'STANDARD',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP,
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    tracking_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_replacement FOREIGN KEY (old_card_id) REFERENCES card(card_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_card_user ON card(user_id);
CREATE INDEX idx_card_account ON card(account_id);
CREATE INDEX idx_card_status ON card(card_status);
CREATE INDEX idx_card_expiry ON card(expiry_date);
CREATE INDEX idx_card_deleted ON card(deleted_at) WHERE deleted_at IS NULL;

CREATE INDEX idx_card_transaction_card ON card_transaction(card_id);
CREATE INDEX idx_card_transaction_user ON card_transaction(user_id);
CREATE INDEX idx_card_transaction_date ON card_transaction(transaction_date DESC);
CREATE INDEX idx_card_transaction_merchant ON card_transaction(merchant_id);
CREATE INDEX idx_card_transaction_status ON card_transaction(status);
CREATE INDEX idx_card_transaction_fraud ON card_transaction(risk_score DESC) WHERE risk_score > 0.7;

CREATE INDEX idx_card_limit_card ON card_limit(card_id);
CREATE INDEX idx_card_limit_period ON card_limit(period_start, period_end);

CREATE INDEX idx_card_statement_card ON card_statement(card_id);
CREATE INDEX idx_card_statement_user ON card_statement(user_id);
CREATE INDEX idx_card_statement_period ON card_statement(statement_period_end DESC);

CREATE INDEX idx_card_authorization_card ON card_authorization(card_id);
CREATE INDEX idx_card_authorization_status ON card_authorization(status);
CREATE INDEX idx_card_authorization_expires ON card_authorization(expires_at);

CREATE INDEX idx_card_replacement_old ON card_replacement_request(old_card_id);
CREATE INDEX idx_card_replacement_new ON card_replacement_request(new_card_id);
CREATE INDEX idx_card_replacement_status ON card_replacement_request(request_status);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_card_updated_at BEFORE UPDATE ON card
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_card_limit_updated_at BEFORE UPDATE ON card_limit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();