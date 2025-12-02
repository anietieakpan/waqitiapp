-- Virtual Card Service Database Schema
-- Creates tables for virtual and physical card management

-- Card programs and types
CREATE TABLE card_programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_name VARCHAR(255) NOT NULL UNIQUE,
    program_code VARCHAR(50) NOT NULL UNIQUE,
    
    -- Program details
    card_type VARCHAR(20) NOT NULL CHECK (card_type IN ('DEBIT', 'CREDIT', 'PREPAID')),
    card_brand VARCHAR(20) NOT NULL CHECK (card_brand IN ('VISA', 'MASTERCARD', 'AMEX', 'DISCOVER')),
    
    -- BIN ranges
    bin_range_start VARCHAR(6) NOT NULL,
    bin_range_end VARCHAR(6) NOT NULL,
    
    -- Program settings
    is_active BOOLEAN DEFAULT TRUE,
    supports_virtual BOOLEAN DEFAULT TRUE,
    supports_physical BOOLEAN DEFAULT TRUE,
    
    -- Limits
    default_spending_limit DECIMAL(15,2) DEFAULT 5000.00,
    max_spending_limit DECIMAL(15,2) DEFAULT 50000.00,
    
    -- Fees
    virtual_card_fee DECIMAL(6,2) DEFAULT 0.00,
    physical_card_fee DECIMAL(6,2) DEFAULT 5.00,
    replacement_fee DECIMAL(6,2) DEFAULT 10.00,
    
    -- External provider details
    provider_name VARCHAR(100) NOT NULL,
    provider_config JSONB,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Virtual and physical cards
CREATE TABLE cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_number_hash VARCHAR(255) NOT NULL UNIQUE, -- Hashed PAN for lookup
    card_token VARCHAR(100) NOT NULL UNIQUE, -- Tokenized reference
    
    -- Card details
    program_id UUID NOT NULL REFERENCES card_programs(id),
    user_id UUID NOT NULL,
    account_id UUID NOT NULL,
    
    -- Card type
    card_form VARCHAR(20) NOT NULL CHECK (card_form IN ('VIRTUAL', 'PHYSICAL')),
    card_type VARCHAR(20) NOT NULL CHECK (card_type IN ('DEBIT', 'CREDIT', 'PREPAID')),
    
    -- Card information (stored encrypted in application layer)
    encrypted_pan TEXT NOT NULL, -- Encrypted Primary Account Number
    encrypted_cvv TEXT NOT NULL, -- Encrypted CVV
    encrypted_pin TEXT, -- Encrypted PIN (for physical cards)
    
    -- Card metadata
    expiry_month INTEGER NOT NULL CHECK (expiry_month >= 1 AND expiry_month <= 12),
    expiry_year INTEGER NOT NULL CHECK (expiry_year >= 2024),
    cardholder_name VARCHAR(255) NOT NULL,
    
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'BLOCKED', 'SUSPENDED', 'EXPIRED', 'CANCELLED')),
    activation_status VARCHAR(20) DEFAULT 'INACTIVE' CHECK (activation_status IN ('INACTIVE', 'ACTIVE', 'LOCKED')),
    
    -- Limits and controls
    spending_limit DECIMAL(15,2) NOT NULL DEFAULT 5000.00,
    daily_limit DECIMAL(15,2) DEFAULT 1000.00,
    monthly_limit DECIMAL(15,2) DEFAULT 5000.00,
    
    -- Usage statistics
    total_transactions INTEGER DEFAULT 0,
    total_spent DECIMAL(15,2) DEFAULT 0.00,
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    
    -- Physical card specific
    shipping_address JSONB, -- For physical cards
    tracking_number VARCHAR(100),
    shipped_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    
    -- Security
    pin_attempts INTEGER DEFAULT 0,
    pin_locked_until TIMESTAMP WITH TIME ZONE,
    
    -- Provider details
    provider_card_id VARCHAR(100),
    provider_metadata JSONB,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE
);

-- Card controls and restrictions
CREATE TABLE card_controls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    
    -- Merchant controls
    allowed_merchant_categories JSONB, -- Array of MCC codes
    blocked_merchant_categories JSONB, -- Array of MCC codes
    allowed_merchants JSONB, -- Specific merchant IDs
    blocked_merchants JSONB, -- Specific merchant IDs
    
    -- Geographic controls
    allowed_countries JSONB, -- Array of country codes
    blocked_countries JSONB, -- Array of country codes
    domestic_only BOOLEAN DEFAULT FALSE,
    
    -- Transaction type controls
    allow_online BOOLEAN DEFAULT TRUE,
    allow_in_store BOOLEAN DEFAULT TRUE,
    allow_atm BOOLEAN DEFAULT FALSE,
    allow_contactless BOOLEAN DEFAULT TRUE,
    
    -- Time-based controls
    active_hours_start TIME,
    active_hours_end TIME,
    active_days JSONB, -- Array of day numbers (1-7)
    timezone VARCHAR(50) DEFAULT 'UTC',
    
    -- Amount controls
    per_transaction_limit DECIMAL(10,2),
    velocity_limits JSONB, -- Custom velocity rules
    
    -- Security controls
    require_3d_secure BOOLEAN DEFAULT FALSE,
    block_gambling BOOLEAN DEFAULT FALSE,
    block_adult_content BOOLEAN DEFAULT FALSE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(card_id)
);

-- Card transactions
CREATE TABLE card_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES cards(id),
    transaction_reference VARCHAR(100) UNIQUE NOT NULL,
    
    -- Transaction details
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('PURCHASE', 'REFUND', 'WITHDRAWAL', 'REVERSAL')),
    
    -- Merchant information
    merchant_name VARCHAR(255),
    merchant_category_code VARCHAR(4),
    merchant_id VARCHAR(100),
    
    -- Authorization details
    authorization_code VARCHAR(20),
    authorization_response_code VARCHAR(4),
    authorization_message TEXT,
    
    -- Processing details
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'AUTHORIZED', 'SETTLED', 'DECLINED', 'REVERSED')),
    decline_reason VARCHAR(100),
    
    -- Transaction context
    transaction_source VARCHAR(20) CHECK (transaction_source IN ('ONLINE', 'IN_STORE', 'ATM', 'CONTACTLESS', 'MANUAL')),
    entry_method VARCHAR(20) CHECK (entry_method IN ('CHIP', 'SWIPE', 'CONTACTLESS', 'KEYED', 'PIN')),
    
    -- Geographic details
    transaction_country VARCHAR(2),
    transaction_city VARCHAR(100),
    
    -- Fees and exchange
    interchange_fee DECIMAL(8,4) DEFAULT 0.00,
    foreign_exchange_fee DECIMAL(8,4) DEFAULT 0.00,
    original_amount DECIMAL(15,2),
    original_currency VARCHAR(3),
    exchange_rate DECIMAL(10,6),
    
    -- Network details
    network_reference VARCHAR(100),
    acquirer_id VARCHAR(50),
    
    -- Timing
    authorized_at TIMESTAMP WITH TIME ZONE,
    settled_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Card statements
CREATE TABLE card_statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES cards(id),
    
    -- Statement period
    statement_date DATE NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    
    -- Statement summary
    opening_balance DECIMAL(15,2) DEFAULT 0.00,
    closing_balance DECIMAL(15,2) DEFAULT 0.00,
    total_purchases DECIMAL(15,2) DEFAULT 0.00,
    total_refunds DECIMAL(15,2) DEFAULT 0.00,
    total_fees DECIMAL(15,2) DEFAULT 0.00,
    
    -- Transaction counts
    purchase_count INTEGER DEFAULT 0,
    refund_count INTEGER DEFAULT 0,
    
    -- Statement file
    statement_file_path VARCHAR(500),
    statement_format VARCHAR(10) DEFAULT 'PDF',
    
    -- Status
    status VARCHAR(20) DEFAULT 'GENERATED' CHECK (status IN ('GENERATED', 'SENT', 'VIEWED')),
    generated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(card_id, statement_date)
);

-- PIN change history
CREATE TABLE pin_change_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES cards(id),
    
    -- Change details
    change_method VARCHAR(20) NOT NULL CHECK (change_method IN ('ONLINE', 'ATM', 'PHONE', 'BRANCH')),
    previous_pin_hash VARCHAR(255), -- Hash of previous PIN
    new_pin_hash VARCHAR(255) NOT NULL, -- Hash of new PIN
    
    -- Verification
    verification_method VARCHAR(20) CHECK (verification_method IN ('SMS', 'EMAIL', '2FA', 'BIOMETRIC')),
    verified_by UUID,
    
    -- Audit
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    changed_by UUID,
    ip_address INET,
    user_agent TEXT
);

-- Card status history
CREATE TABLE card_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES cards(id),
    
    -- Status change
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    
    -- Change details
    changed_by UUID,
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    auto_changed BOOLEAN DEFAULT FALSE,
    
    -- Additional context
    metadata JSONB
);

-- Indexes for performance
CREATE INDEX idx_cards_user_id ON cards(user_id);
CREATE INDEX idx_cards_account_id ON cards(account_id);
CREATE INDEX idx_cards_status ON cards(status);
CREATE INDEX idx_cards_card_form ON cards(card_form);
CREATE INDEX idx_cards_token ON cards(card_token);
CREATE INDEX idx_cards_hash ON cards(card_number_hash);

CREATE INDEX idx_card_transactions_card_id ON card_transactions(card_id);
CREATE INDEX idx_card_transactions_reference ON card_transactions(transaction_reference);
CREATE INDEX idx_card_transactions_status ON card_transactions(status);
CREATE INDEX idx_card_transactions_created_at ON card_transactions(created_at);
CREATE INDEX idx_card_transactions_merchant ON card_transactions(merchant_category_code);

CREATE INDEX idx_card_statements_card_id ON card_statements(card_id);
CREATE INDEX idx_card_statements_statement_date ON card_statements(statement_date);

CREATE INDEX idx_pin_change_history_card_id ON pin_change_history(card_id);
CREATE INDEX idx_pin_change_history_changed_at ON pin_change_history(changed_at);

CREATE INDEX idx_card_status_history_card_id ON card_status_history(card_id);
CREATE INDEX idx_card_status_history_changed_at ON card_status_history(changed_at);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_card_programs_updated_at BEFORE UPDATE
    ON card_programs FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_cards_updated_at BEFORE UPDATE
    ON cards FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_card_controls_updated_at BEFORE UPDATE
    ON card_controls FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_card_transactions_updated_at BEFORE UPDATE
    ON card_transactions FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Function to track card status changes
CREATE OR REPLACE FUNCTION track_card_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO card_status_history (card_id, previous_status, new_status, changed_at, auto_changed)
        VALUES (NEW.id, OLD.status, NEW.status, CURRENT_TIMESTAMP, TRUE);
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER track_card_status_changes AFTER UPDATE
    ON cards FOR EACH ROW EXECUTE PROCEDURE track_card_status_change();

-- Function to update card statistics
CREATE OR REPLACE FUNCTION update_card_statistics()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'SETTLED' THEN
        UPDATE cards 
        SET total_transactions = total_transactions + 1,
            total_spent = total_spent + NEW.amount,
            last_transaction_at = NEW.created_at
        WHERE id = NEW.card_id;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_card_stats_on_transaction AFTER INSERT
    ON card_transactions FOR EACH ROW EXECUTE PROCEDURE update_card_statistics();

-- Comments for documentation
COMMENT ON TABLE card_programs IS 'Card program definitions and configurations';
COMMENT ON TABLE cards IS 'Virtual and physical cards issued to users';
COMMENT ON TABLE card_controls IS 'Spending controls and restrictions for cards';
COMMENT ON TABLE card_transactions IS 'All card transaction authorizations and settlements';
COMMENT ON TABLE card_statements IS 'Monthly card statements';
COMMENT ON TABLE pin_change_history IS 'History of PIN changes for security auditing';
COMMENT ON TABLE card_status_history IS 'Audit trail of card status changes';