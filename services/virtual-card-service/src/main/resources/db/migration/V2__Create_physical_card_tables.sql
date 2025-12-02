-- Physical Card Service Database Migration
-- Creates additional tables for physical card management beyond the base virtual card schema

-- Card orders for tracking physical card production and shipping
CREATE TABLE card_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    original_card_id VARCHAR(255),
    provider_order_id VARCHAR(255),
    
    -- Order details
    type VARCHAR(20) NOT NULL CHECK (type IN ('VIRTUAL', 'PHYSICAL')),
    brand VARCHAR(20) NOT NULL CHECK (brand IN ('VISA', 'MASTERCARD', 'AMEX', 'DISCOVER')),
    
    -- Design and personalization (stored as JSON for flexibility)
    design JSONB,
    personalization JSONB,
    
    -- Shipping details (embedded as JSON)
    shipping_address JSONB NOT NULL,
    shipping_method VARCHAR(20) NOT NULL CHECK (shipping_method IN ('STANDARD', 'EXPRESS', 'OVERNIGHT', 'PRIORITY', 'CERTIFIED', 'REGISTERED')),
    
    -- Pricing
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    order_fee DECIMAL(10,2) DEFAULT 0.00,
    shipping_fee DECIMAL(10,2) DEFAULT 0.00,
    total_fee DECIMAL(10,2) DEFAULT 0.00,
    
    -- Status and timeline
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUBMITTED', 'IN_PRODUCTION', 'READY_TO_SHIP', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'FAILED', 'EXPIRED')),
    ordered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    estimated_delivery TIMESTAMP WITH TIME ZONE,
    
    -- Replacement details
    is_replacement BOOLEAN DEFAULT FALSE,
    replacement_reason VARCHAR(20) CHECK (replacement_reason IN ('LOST', 'STOLEN', 'DAMAGED', 'DEFECTIVE', 'NAME_CHANGE')),
    
    notes TEXT,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Physical cards (extends the base cards table concept)
CREATE TABLE physical_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id VARCHAR(255) NOT NULL,
    original_card_id VARCHAR(255), -- For replacements
    user_id VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL UNIQUE,
    
    -- Card details
    type VARCHAR(20) NOT NULL CHECK (type IN ('VIRTUAL', 'PHYSICAL')),
    brand VARCHAR(20) NOT NULL CHECK (brand IN ('VISA', 'MASTERCARD', 'AMEX', 'DISCOVER')),
    status VARCHAR(20) NOT NULL DEFAULT 'ORDERED' CHECK (status IN ('ORDERED', 'IN_PRODUCTION', 'SHIPPED', 'DELIVERED', 'ACTIVE', 'BLOCKED', 'SUSPENDED', 'EXPIRED', 'CANCELLED', 'REPLACED', 'CLOSED', 'DAMAGED')),
    
    -- Design and personalization
    design JSONB,
    personalization JSONB,
    
    -- Financial details
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    balance DECIMAL(19,2) DEFAULT 0.00,
    
    -- Card information (masked/tokenized)
    last_four_digits VARCHAR(4),
    expiry_month INTEGER CHECK (expiry_month >= 1 AND expiry_month <= 12),
    expiry_year INTEGER CHECK (expiry_year >= 2024),
    
    -- Timeline
    ordered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    activated_at TIMESTAMP WITH TIME ZONE,
    blocked_at TIMESTAMP WITH TIME ZONE,
    replaced_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    estimated_delivery TIMESTAMP WITH TIME ZONE,
    production_updated_at TIMESTAMP WITH TIME ZONE,
    
    -- Status details
    block_reason TEXT,
    replacement_reason VARCHAR(20) CHECK (replacement_reason IN ('LOST', 'STOLEN', 'DAMAGED', 'DEFECTIVE', 'NAME_CHANGE')),
    
    -- Flags
    is_replacement BOOLEAN DEFAULT FALSE,
    pin_set BOOLEAN DEFAULT FALSE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Card shipping tracking
CREATE TABLE card_shipping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id VARCHAR(255) NOT NULL,
    card_id VARCHAR(255) NOT NULL,
    tracking_number VARCHAR(255) UNIQUE,
    
    -- Shipping details
    method VARCHAR(20) NOT NULL CHECK (method IN ('STANDARD', 'EXPRESS', 'OVERNIGHT', 'PRIORITY', 'CERTIFIED', 'REGISTERED')),
    status VARCHAR(20) NOT NULL DEFAULT 'PREPARING' CHECK (status IN ('PREPARING', 'READY_TO_SHIP', 'SHIPPED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_ATTEMPTED', 'RETURNED_TO_SENDER', 'EXCEPTION', 'LOST', 'DAMAGED')),
    
    -- Shipping address (embedded as JSON)
    address JSONB NOT NULL,
    
    -- Carrier information
    carrier VARCHAR(100),
    service_type VARCHAR(100),
    
    -- Timeline
    estimated_delivery TIMESTAMP WITH TIME ZONE,
    actual_delivery TIMESTAMP WITH TIME ZONE,
    shipped_at TIMESTAMP WITH TIME ZONE,
    
    -- Tracking details
    current_location VARCHAR(500),
    last_update TIMESTAMP WITH TIME ZONE,
    delivery_attempts INTEGER DEFAULT 0,
    delivery_notes TEXT,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Shipping events for detailed tracking
CREATE TABLE shipping_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipping_id UUID NOT NULL REFERENCES card_shipping(id) ON DELETE CASCADE,
    
    -- Event details
    status VARCHAR(20) NOT NULL,
    description TEXT,
    location VARCHAR(500),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Provider details
    event_code VARCHAR(50),
    carrier_message TEXT,
    exception_code VARCHAR(50),
    exception_description TEXT,
    
    -- Delivery details
    signed_by VARCHAR(255),
    delivery_attempt_count INTEGER,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Card designs available for physical cards
CREATE TABLE card_designs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    design_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    
    -- Design assets
    image_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    preview_url VARCHAR(500),
    
    -- Pricing and availability
    is_premium BOOLEAN DEFAULT FALSE,
    fee DECIMAL(10,2) DEFAULT 0.00,
    active BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    
    -- Ordering
    sort_order INTEGER DEFAULT 0,
    
    -- Availability constraints
    available_from TIMESTAMP WITH TIME ZONE,
    available_until TIMESTAMP WITH TIME ZONE,
    max_orders INTEGER,
    current_orders INTEGER DEFAULT 0,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Card incidents (lost, stolen, etc.)
CREATE TABLE card_incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    
    -- Incident details
    type VARCHAR(20) NOT NULL CHECK (type IN ('LOST', 'STOLEN', 'DAMAGED', 'DEFECTIVE', 'NAME_CHANGE')),
    description TEXT,
    location VARCHAR(500),
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Investigation
    police_report_number VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'REPORTED' CHECK (status IN ('REPORTED', 'UNDER_INVESTIGATION', 'RESOLVED', 'CLOSED', 'ESCALATED')),
    investigation_id VARCHAR(255),
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Card activation attempts for security auditing
CREATE TABLE card_activation_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    
    -- Attempt details
    activation_code_hash VARCHAR(255), -- Store hash, not actual code
    successful BOOLEAN NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Security context
    ip_address INET,
    user_agent TEXT,
    device_id VARCHAR(255),
    failure_reason VARCHAR(255),
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_card_orders_user_id ON card_orders(user_id);
CREATE INDEX idx_card_orders_status ON card_orders(status);
CREATE INDEX idx_card_orders_ordered_at ON card_orders(ordered_at);
CREATE INDEX idx_card_orders_provider_order_id ON card_orders(provider_order_id);

CREATE INDEX idx_physical_cards_user_id ON physical_cards(user_id);
CREATE INDEX idx_physical_cards_status ON physical_cards(status);
CREATE INDEX idx_physical_cards_provider_id ON physical_cards(provider_id);
CREATE INDEX idx_physical_cards_order_id ON physical_cards(order_id);
CREATE INDEX idx_physical_cards_original_card_id ON physical_cards(original_card_id);

CREATE INDEX idx_card_shipping_card_id ON card_shipping(card_id);
CREATE INDEX idx_card_shipping_order_id ON card_shipping(order_id);
CREATE INDEX idx_card_shipping_tracking_number ON card_shipping(tracking_number);
CREATE INDEX idx_card_shipping_status ON card_shipping(status);

CREATE INDEX idx_shipping_events_shipping_id ON shipping_events(shipping_id);
CREATE INDEX idx_shipping_events_timestamp ON shipping_events(timestamp);

CREATE INDEX idx_card_designs_active ON card_designs(active);
CREATE INDEX idx_card_designs_design_code ON card_designs(design_code);
CREATE INDEX idx_card_designs_category ON card_designs(category);
CREATE INDEX idx_card_designs_is_premium ON card_designs(is_premium);

CREATE INDEX idx_card_incidents_card_id ON card_incidents(card_id);
CREATE INDEX idx_card_incidents_user_id ON card_incidents(user_id);
CREATE INDEX idx_card_incidents_type ON card_incidents(type);
CREATE INDEX idx_card_incidents_status ON card_incidents(status);
CREATE INDEX idx_card_incidents_reported_at ON card_incidents(reported_at);

CREATE INDEX idx_activation_attempts_card_id ON card_activation_attempts(card_id);
CREATE INDEX idx_activation_attempts_user_id ON card_activation_attempts(user_id);
CREATE INDEX idx_activation_attempts_attempted_at ON card_activation_attempts(attempted_at);
CREATE INDEX idx_activation_attempts_successful ON card_activation_attempts(successful);
CREATE INDEX idx_activation_attempts_ip_address ON card_activation_attempts(ip_address);

-- Foreign key constraints
ALTER TABLE card_shipping ADD CONSTRAINT fk_card_shipping_order 
    FOREIGN KEY (order_id) REFERENCES card_orders(id);
    
ALTER TABLE card_shipping ADD CONSTRAINT fk_card_shipping_card 
    FOREIGN KEY (card_id) REFERENCES physical_cards(id);
    
ALTER TABLE physical_cards ADD CONSTRAINT fk_physical_card_order 
    FOREIGN KEY (order_id) REFERENCES card_orders(id);

-- Triggers for updated_at timestamps
CREATE TRIGGER update_card_orders_updated_at BEFORE UPDATE
    ON card_orders FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_physical_cards_updated_at BEFORE UPDATE
    ON physical_cards FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_card_shipping_updated_at BEFORE UPDATE
    ON card_shipping FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_card_designs_updated_at BEFORE UPDATE
    ON card_designs FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_card_incidents_updated_at BEFORE UPDATE
    ON card_incidents FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Insert default card designs
INSERT INTO card_designs (design_code, name, description, category, is_premium, fee, active, is_default, sort_order) VALUES
('standard-blue', 'Standard Blue', 'Classic blue card design', 'Standard', FALSE, 0.00, TRUE, TRUE, 1),
('standard-black', 'Standard Black', 'Elegant black card design', 'Standard', FALSE, 0.00, TRUE, FALSE, 2),
('premium-gold', 'Premium Gold', 'Luxury gold card design', 'Premium', TRUE, 15.00, TRUE, FALSE, 3),
('premium-platinum', 'Premium Platinum', 'Exclusive platinum design', 'Premium', TRUE, 25.00, TRUE, FALSE, 4),
('eco-green', 'Eco Friendly', 'Environmentally conscious design', 'Eco', FALSE, 5.00, TRUE, FALSE, 5);

-- Comments for documentation
COMMENT ON TABLE card_orders IS 'Orders for physical card production and delivery';
COMMENT ON TABLE physical_cards IS 'Physical payment cards issued to users';
COMMENT ON TABLE card_shipping IS 'Shipping and delivery tracking for physical cards';
COMMENT ON TABLE shipping_events IS 'Detailed shipping event history';
COMMENT ON TABLE card_designs IS 'Available card designs and customization options';
COMMENT ON TABLE card_incidents IS 'Security incidents reported for cards';
COMMENT ON TABLE card_activation_attempts IS 'Card activation attempts for security auditing';