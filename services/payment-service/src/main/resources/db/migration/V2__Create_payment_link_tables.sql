-- Migration to create Payment Links tables
-- This adds shareable payment link functionality to the Waqiti platform

-- Create payment_links table
CREATE TABLE payment_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    link_id VARCHAR(50) NOT NULL UNIQUE,
    creator_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    amount DECIMAL(19,2),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    min_amount DECIMAL(19,2),
    max_amount DECIMAL(19,2),
    link_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP,
    max_uses INTEGER,
    current_uses INTEGER NOT NULL DEFAULT 0,
    total_collected DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    requires_note BOOLEAN NOT NULL DEFAULT FALSE,
    custom_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_payment_links_amount_positive CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT chk_payment_links_min_amount_positive CHECK (min_amount IS NULL OR min_amount > 0),
    CONSTRAINT chk_payment_links_max_amount_positive CHECK (max_amount IS NULL OR max_amount > 0),
    CONSTRAINT chk_payment_links_amount_range CHECK (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount),
    CONSTRAINT chk_payment_links_max_uses_positive CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT chk_payment_links_current_uses_non_negative CHECK (current_uses >= 0),
    CONSTRAINT chk_payment_links_total_collected_non_negative CHECK (total_collected >= 0),
    CONSTRAINT chk_payment_links_valid_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payment_links_valid_link_type CHECK (link_type IN ('REQUEST_MONEY', 'DONATION', 'INVOICE', 'SUBSCRIPTION', 'EVENT_TICKET', 'PRODUCT_SALE')),
    CONSTRAINT chk_payment_links_valid_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED', 'COMPLETED', 'CANCELLED'))
);

-- Create payment_link_transactions table
CREATE TABLE payment_link_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_link_id UUID NOT NULL,
    transaction_id VARCHAR(50) NOT NULL UNIQUE,
    payer_id UUID,
    payer_email VARCHAR(100),
    payer_name VARCHAR(100),
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_note TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(50),
    payment_reference VARCHAR(100),
    provider_transaction_id VARCHAR(100),
    failure_reason TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_payment_link_transactions_payment_link FOREIGN KEY (payment_link_id) REFERENCES payment_links(id) ON DELETE CASCADE,
    
    -- Check constraints
    CONSTRAINT chk_payment_link_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payment_link_transactions_valid_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payment_link_transactions_valid_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_payment_link_transactions_payer_info CHECK (payer_id IS NOT NULL OR payer_email IS NOT NULL)
);

-- Create payment_link_metadata table for key-value metadata storage
CREATE TABLE payment_link_metadata (
    payment_link_id UUID NOT NULL,
    meta_key VARCHAR(50) NOT NULL,
    meta_value TEXT,
    
    PRIMARY KEY (payment_link_id, meta_key),
    CONSTRAINT fk_payment_link_metadata_payment_link FOREIGN KEY (payment_link_id) REFERENCES payment_links(id) ON DELETE CASCADE
);

-- Create payment_link_transaction_metadata table
CREATE TABLE payment_link_transaction_metadata (
    transaction_id UUID NOT NULL,
    meta_key VARCHAR(50) NOT NULL,
    meta_value TEXT,
    
    PRIMARY KEY (transaction_id, meta_key),
    CONSTRAINT fk_payment_link_transaction_metadata_transaction FOREIGN KEY (transaction_id) REFERENCES payment_link_transactions(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_payment_links_creator_id ON payment_links(creator_id);
CREATE INDEX idx_payment_links_link_id ON payment_links(link_id);
CREATE INDEX idx_payment_links_status ON payment_links(status);
CREATE INDEX idx_payment_links_created_at ON payment_links(created_at);
CREATE INDEX idx_payment_links_expires_at ON payment_links(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_payment_links_creator_status ON payment_links(creator_id, status);
CREATE INDEX idx_payment_links_link_type ON payment_links(link_type);

CREATE INDEX idx_payment_link_transactions_payment_link_id ON payment_link_transactions(payment_link_id);
CREATE INDEX idx_payment_link_transactions_transaction_id ON payment_link_transactions(transaction_id);
CREATE INDEX idx_payment_link_transactions_payer_id ON payment_link_transactions(payer_id) WHERE payer_id IS NOT NULL;
CREATE INDEX idx_payment_link_transactions_payer_email ON payment_link_transactions(payer_email) WHERE payer_email IS NOT NULL;
CREATE INDEX idx_payment_link_transactions_status ON payment_link_transactions(status);
CREATE INDEX idx_payment_link_transactions_created_at ON payment_link_transactions(created_at);
CREATE INDEX idx_payment_link_transactions_ip_address ON payment_link_transactions(ip_address) WHERE ip_address IS NOT NULL;
CREATE INDEX idx_payment_link_transactions_payment_method ON payment_link_transactions(payment_method) WHERE payment_method IS NOT NULL;

-- Create composite indexes for common query patterns
CREATE INDEX idx_payment_links_creator_created ON payment_links(creator_id, created_at DESC);
CREATE INDEX idx_payment_links_status_expires ON payment_links(status, expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_payment_links_active_uses ON payment_links(status, current_uses, max_uses) WHERE status = 'ACTIVE' AND max_uses IS NOT NULL;

CREATE INDEX idx_payment_link_transactions_link_created ON payment_link_transactions(payment_link_id, created_at DESC);
CREATE INDEX idx_payment_link_transactions_status_created ON payment_link_transactions(status, created_at DESC);

-- Create triggers to automatically update updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_payment_links_updated_at BEFORE UPDATE ON payment_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payment_link_transactions_updated_at BEFORE UPDATE ON payment_link_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some example data for testing (remove in production)
-- INSERT INTO payment_links (link_id, creator_id, title, description, amount, currency, link_type, custom_message) 
-- VALUES 
--     ('DEMO001', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'Coffee Fund', 'Buy me a coffee!', 5.00, 'USD', 'DONATION', 'Thanks for supporting my work!'),
--     ('DEMO002', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'Freelance Invoice', 'Web development services', 500.00, 'USD', 'INVOICE', 'Payment for project XYZ'),
--     ('DEMO003', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'Split Dinner Bill', 'Italian restaurant bill', NULL, 'USD', 'REQUEST_MONEY', 'Your share of dinner last night');

-- Add comments for documentation
COMMENT ON TABLE payment_links IS 'Shareable payment links for collecting payments';
COMMENT ON TABLE payment_link_transactions IS 'Transactions processed through payment links';
COMMENT ON TABLE payment_link_metadata IS 'Key-value metadata for payment links';
COMMENT ON TABLE payment_link_transaction_metadata IS 'Key-value metadata for payment link transactions';

COMMENT ON COLUMN payment_links.link_id IS 'Short, shareable identifier for the payment link';
COMMENT ON COLUMN payment_links.amount IS 'Fixed amount for the payment link (null for flexible amount)';
COMMENT ON COLUMN payment_links.min_amount IS 'Minimum amount allowed for flexible amount links';
COMMENT ON COLUMN payment_links.max_amount IS 'Maximum amount allowed for flexible amount links';
COMMENT ON COLUMN payment_links.current_uses IS 'Number of times the link has been used';
COMMENT ON COLUMN payment_links.total_collected IS 'Total amount collected through this link';

COMMENT ON COLUMN payment_link_transactions.payer_id IS 'User ID of the payer (null for anonymous payments)';
COMMENT ON COLUMN payment_link_transactions.payment_reference IS 'Reference from the payment processor';
COMMENT ON COLUMN payment_link_transactions.provider_transaction_id IS 'External transaction ID from payment provider';