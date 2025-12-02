-- Instant Deposit Tables
-- Enables immediate fund availability through debit card networks for ACH deposits

-- Create enum type for instant deposit status
CREATE TYPE instant_deposit_status AS ENUM (
    'PENDING',      -- Initial state
    'PROCESSING',   -- Being processed through card networks
    'COMPLETED',    -- Successfully completed
    'FAILED'        -- Failed to process
);

-- Main instant deposits table
CREATE TABLE instant_deposits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ach_transfer_id UUID NOT NULL UNIQUE REFERENCES ach_transfers(id),
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL,
    
    -- Amount breakdown
    original_amount DECIMAL(19,4) NOT NULL CHECK (original_amount > 0),
    fee_amount DECIMAL(19,4) NOT NULL CHECK (fee_amount >= 0),
    net_amount DECIMAL(19,4) NOT NULL CHECK (net_amount > 0),
    
    -- Status tracking
    status instant_deposit_status NOT NULL DEFAULT 'PENDING',
    
    -- Debit card details
    debit_card_id UUID NOT NULL,
    
    -- Security and fraud detection
    device_id VARCHAR(255),
    ip_address VARCHAR(45), -- IPv6 compatible length
    
    -- Payment network details
    network_reference_id VARCHAR(100),
    network_response_code VARCHAR(10),
    
    -- Status timestamps
    processing_started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    
    -- Error handling
    failure_reason VARCHAR(500),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_instant_deposit_ach_transfer_id ON instant_deposits(ach_transfer_id);
CREATE INDEX idx_instant_deposit_user_id ON instant_deposits(user_id);
CREATE INDEX idx_instant_deposit_wallet_id ON instant_deposits(wallet_id);
CREATE INDEX idx_instant_deposit_status ON instant_deposits(status);
CREATE INDEX idx_instant_deposit_created_at ON instant_deposits(created_at DESC);
CREATE INDEX idx_instant_deposit_debit_card_id ON instant_deposits(debit_card_id);
CREATE INDEX idx_instant_deposit_network_ref ON instant_deposits(network_reference_id) 
    WHERE network_reference_id IS NOT NULL;
CREATE INDEX idx_instant_deposit_user_created ON instant_deposits(user_id, created_at DESC);
CREATE INDEX idx_instant_deposit_processing ON instant_deposits(status, processing_started_at) 
    WHERE status = 'PROCESSING';

-- Add instant deposit tracking fields to ach_transfers table
ALTER TABLE ach_transfers 
ADD COLUMN IF NOT EXISTS instant_deposit_processed BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN IF NOT EXISTS instant_deposit_at TIMESTAMP WITH TIME ZONE;

-- Create index for instant deposit queries on ach_transfers
CREATE INDEX idx_ach_instant_deposit_processed ON ach_transfers(instant_deposit_processed, created_at DESC);

-- Instant deposit fees configuration table
CREATE TABLE instant_deposit_fees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_tier VARCHAR(50) NOT NULL, -- BASIC, STANDARD, PREMIUM, ENTERPRISE
    fee_type VARCHAR(20) NOT NULL, -- FLAT, PERCENTAGE, TIERED
    
    -- Fee structure
    flat_fee DECIMAL(19,4) DEFAULT 0,
    percentage_fee DECIMAL(5,4) DEFAULT 0, -- e.g., 0.0125 for 1.25%
    minimum_fee DECIMAL(19,4) DEFAULT 0,
    maximum_fee DECIMAL(19,4),
    
    -- Amount thresholds for tiered pricing
    min_amount DECIMAL(19,4) DEFAULT 0,
    max_amount DECIMAL(19,4),
    
    -- Configuration
    is_active BOOLEAN DEFAULT true,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insert default instant deposit fee structure
INSERT INTO instant_deposit_fees (user_tier, fee_type, flat_fee, percentage_fee, minimum_fee, maximum_fee, effective_date) VALUES
('BASIC', 'FLAT', 1.99, 0.0000, 1.99, 5.00, CURRENT_DATE),
('STANDARD', 'PERCENTAGE', 0.00, 0.0125, 0.99, 5.00, CURRENT_DATE),
('PREMIUM', 'PERCENTAGE', 0.00, 0.0100, 0.99, 3.00, CURRENT_DATE),
('ENTERPRISE', 'PERCENTAGE', 0.00, 0.0075, 0.50, 2.00, CURRENT_DATE);

-- Instant deposit limits configuration table
CREATE TABLE instant_deposit_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_tier VARCHAR(50) NOT NULL, -- BASIC, STANDARD, PREMIUM, ENTERPRISE
    daily_limit DECIMAL(19,4) NOT NULL,
    single_transaction_limit DECIMAL(19,4) NOT NULL,
    monthly_limit DECIMAL(19,4) NOT NULL,
    daily_transaction_count INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insert default instant deposit limits
INSERT INTO instant_deposit_limits (user_tier, daily_limit, single_transaction_limit, monthly_limit, daily_transaction_count) VALUES
('BASIC', 500.00, 200.00, 2500.00, 3),
('STANDARD', 2500.00, 1000.00, 12500.00, 5),
('PREMIUM', 5000.00, 2500.00, 25000.00, 10),
('ENTERPRISE', 10000.00, 5000.00, 50000.00, 20);

-- Instant deposit audit log for compliance
CREATE TABLE instant_deposit_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instant_deposit_id UUID NOT NULL REFERENCES instant_deposits(id),
    action VARCHAR(50) NOT NULL, -- CREATED, STATUS_CHANGED, COMPLETED, FAILED
    previous_status instant_deposit_status,
    new_status instant_deposit_status,
    
    -- Additional context
    details JSONB,
    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100) -- service/user identifier
);

CREATE INDEX idx_instant_deposit_audit_deposit_id ON instant_deposit_audit_log(instant_deposit_id);
CREATE INDEX idx_instant_deposit_audit_created_at ON instant_deposit_audit_log(created_at DESC);
CREATE INDEX idx_instant_deposit_audit_action ON instant_deposit_audit_log(action, created_at DESC);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_instant_deposit_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for updated_at
CREATE TRIGGER update_instant_deposits_updated_at
    BEFORE UPDATE ON instant_deposits
    FOR EACH ROW
    EXECUTE FUNCTION update_instant_deposit_updated_at();

CREATE TRIGGER update_instant_deposit_fees_updated_at
    BEFORE UPDATE ON instant_deposit_fees
    FOR EACH ROW
    EXECUTE FUNCTION update_instant_deposit_updated_at();

CREATE TRIGGER update_instant_deposit_limits_updated_at
    BEFORE UPDATE ON instant_deposit_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_instant_deposit_updated_at();

-- Trigger to automatically create audit log entries
CREATE OR REPLACE FUNCTION create_instant_deposit_audit_log()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO instant_deposit_audit_log (
            instant_deposit_id, action, new_status, details, created_by
        ) VALUES (
            NEW.id, 'CREATED', NEW.status, 
            jsonb_build_object('original_amount', NEW.original_amount, 'fee_amount', NEW.fee_amount),
            'system'
        );
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' AND OLD.status != NEW.status THEN
        INSERT INTO instant_deposit_audit_log (
            instant_deposit_id, action, previous_status, new_status, 
            details, created_by
        ) VALUES (
            NEW.id, 'STATUS_CHANGED', OLD.status, NEW.status,
            jsonb_build_object(
                'failure_reason', NEW.failure_reason,
                'network_reference_id', NEW.network_reference_id,
                'network_response_code', NEW.network_response_code
            ),
            'system'
        );
        RETURN NEW;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER instant_deposit_audit_trigger
    AFTER INSERT OR UPDATE ON instant_deposits
    FOR EACH ROW
    EXECUTE FUNCTION create_instant_deposit_audit_log();

-- Comments for documentation
COMMENT ON TABLE instant_deposits IS 'Stores instant deposit records for immediate fund availability through debit card networks';
COMMENT ON COLUMN instant_deposits.ach_transfer_id IS 'Reference to the original ACH transfer being accelerated';
COMMENT ON COLUMN instant_deposits.original_amount IS 'Original ACH transfer amount before fees';
COMMENT ON COLUMN instant_deposits.fee_amount IS 'Fee charged for instant deposit service';
COMMENT ON COLUMN instant_deposits.net_amount IS 'Amount actually deposited to user wallet (original - fee)';
COMMENT ON COLUMN instant_deposits.debit_card_id IS 'Reference to user debit card used for processing';
COMMENT ON COLUMN instant_deposits.device_id IS 'Device identifier for fraud detection';
COMMENT ON COLUMN instant_deposits.ip_address IS 'IP address for fraud detection and compliance';
COMMENT ON COLUMN instant_deposits.network_reference_id IS 'Transaction reference from card network processor';

COMMENT ON TABLE instant_deposit_fees IS 'Configurable fee structure for instant deposits by user tier';
COMMENT ON TABLE instant_deposit_limits IS 'Configurable limits for instant deposits by user tier';
COMMENT ON TABLE instant_deposit_audit_log IS 'Audit trail for instant deposit transactions and status changes';