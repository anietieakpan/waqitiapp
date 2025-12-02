-- ACH Transfer Tables
-- Enables bank account deposits and withdrawals via ACH network

-- Create enum types for ACH transfers
CREATE TYPE ach_transfer_status AS ENUM (
    'PENDING',      -- Initial state
    'PROCESSING',   -- Sent to bank for processing
    'COMPLETED',    -- Successfully completed
    'FAILED',       -- Failed to process
    'CANCELLED',    -- Cancelled by user
    'RETURNED',     -- Returned by bank
    'REVERSED'      -- Reversed due to return/dispute
);

CREATE TYPE transfer_direction AS ENUM (
    'DEPOSIT',      -- From bank account to wallet
    'WITHDRAWAL'    -- From wallet to bank account
);

CREATE TYPE bank_account_type AS ENUM (
    'CHECKING',
    'SAVINGS'
);

-- Main ACH transfers table
CREATE TABLE ach_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL,
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    direction transfer_direction NOT NULL,
    status ach_transfer_status NOT NULL DEFAULT 'PENDING',
    
    -- Encrypted bank account details
    routing_number VARCHAR(500) NOT NULL, -- Encrypted
    account_number VARCHAR(500) NOT NULL, -- Encrypted
    account_holder_name VARCHAR(100) NOT NULL,
    account_type bank_account_type NOT NULL,
    
    -- Transfer details
    description VARCHAR(255),
    external_reference_id VARCHAR(100), -- Reference from banking partner
    idempotency_key VARCHAR(100) UNIQUE,
    
    -- Status tracking
    failure_reason VARCHAR(500),
    return_code VARCHAR(10),
    return_reason VARCHAR(255),
    
    -- Timestamps
    processing_started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    returned_at TIMESTAMP WITH TIME ZONE,
    expected_completion_date DATE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_ach_user_id ON ach_transfers(user_id);
CREATE INDEX idx_ach_wallet_id ON ach_transfers(wallet_id);
CREATE INDEX idx_ach_status ON ach_transfers(status);
CREATE INDEX idx_ach_created_at ON ach_transfers(created_at DESC);
CREATE INDEX idx_ach_external_ref ON ach_transfers(external_reference_id) WHERE external_reference_id IS NOT NULL;
CREATE INDEX idx_ach_idempotency ON ach_transfers(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_ach_user_created ON ach_transfers(user_id, created_at DESC);
CREATE INDEX idx_ach_processing ON ach_transfers(status, processing_started_at) 
    WHERE status = 'PROCESSING';

-- ACH return codes lookup table
CREATE TABLE ach_return_codes (
    code VARCHAR(10) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL, -- insufficient_funds, account_closed, invalid_account, etc.
    is_retryable BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insert common ACH return codes
INSERT INTO ach_return_codes (code, description, category, is_retryable) VALUES
('R01', 'Insufficient Funds', 'insufficient_funds', true),
('R02', 'Account Closed', 'account_closed', false),
('R03', 'No Account/Unable to Locate Account', 'invalid_account', false),
('R04', 'Invalid Account Number', 'invalid_account', false),
('R05', 'Unauthorized Debit to Consumer Account', 'authorization', false),
('R06', 'Returned per ODFI Request', 'bank_request', false),
('R07', 'Authorization Revoked by Customer', 'authorization', false),
('R08', 'Payment Stopped', 'stop_payment', false),
('R09', 'Uncollected Funds', 'insufficient_funds', true),
('R10', 'Customer Advises Not Authorized', 'authorization', false),
('R11', 'Check Truncation Entry Return', 'check_issue', false),
('R12', 'Account Sold to Another DFI', 'account_issue', false),
('R13', 'Invalid ACH Routing Number', 'invalid_routing', false),
('R14', 'Representative Payee Deceased', 'account_issue', false),
('R15', 'Beneficiary or Account Holder Deceased', 'account_issue', false),
('R16', 'Account Frozen', 'account_issue', true),
('R17', 'File Record Edit Criteria', 'technical', false),
('R20', 'Non-Transaction Account', 'account_type', false),
('R21', 'Invalid Company Identification', 'authorization', false),
('R22', 'Invalid Individual ID Number', 'authorization', false),
('R23', 'Credit Entry Refused by Receiver', 'refused', false),
('R24', 'Duplicate Entry', 'duplicate', false),
('R29', 'Corporate Customer Advises Not Authorized', 'authorization', false),
('R31', 'Permissible Return Entry', 'bank_request', false),
('R33', 'Return of XCK Entry', 'check_issue', false);

-- ACH webhook events log
CREATE TABLE ach_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID REFERENCES ach_transfers(id),
    event_type VARCHAR(50) NOT NULL,
    webhook_payload JSONB NOT NULL,
    signature VARCHAR(255),
    processed BOOLEAN DEFAULT false,
    processed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ach_webhook_transfer ON ach_webhook_events(transfer_id);
CREATE INDEX idx_ach_webhook_processed ON ach_webhook_events(processed, created_at);

-- Bank account verification table
CREATE TABLE bank_account_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    routing_number_encrypted VARCHAR(500) NOT NULL,
    account_number_encrypted VARCHAR(500) NOT NULL,
    account_holder_name VARCHAR(100) NOT NULL,
    account_type bank_account_type NOT NULL,
    
    -- Micro-deposit verification
    micro_deposit_1 DECIMAL(3,2),
    micro_deposit_2 DECIMAL(3,2),
    verification_attempts INTEGER DEFAULT 0,
    verified_at TIMESTAMP WITH TIME ZONE,
    
    -- Plaid or other instant verification
    external_verification_id VARCHAR(100),
    verification_method VARCHAR(50), -- micro_deposits, instant, manual
    
    -- Status
    is_verified BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bank_verification_user ON bank_account_verifications(user_id);
CREATE INDEX idx_bank_verification_active ON bank_account_verifications(user_id, is_active, is_verified);

-- ACH transfer limits configuration
CREATE TABLE ach_transfer_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_tier VARCHAR(50) NOT NULL, -- BASIC, STANDARD, PREMIUM, ENTERPRISE
    daily_limit DECIMAL(19,4) NOT NULL,
    single_transaction_limit DECIMAL(19,4) NOT NULL,
    monthly_limit DECIMAL(19,4) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insert default limits
INSERT INTO ach_transfer_limits (user_tier, daily_limit, single_transaction_limit, monthly_limit) VALUES
('BASIC', 1000.00, 500.00, 5000.00),
('STANDARD', 5000.00, 2500.00, 25000.00),
('PREMIUM', 10000.00, 5000.00, 50000.00),
('ENTERPRISE', 50000.00, 10000.00, 250000.00);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_ach_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for updated_at
CREATE TRIGGER update_ach_transfers_updated_at
    BEFORE UPDATE ON ach_transfers
    FOR EACH ROW
    EXECUTE FUNCTION update_ach_updated_at();

CREATE TRIGGER update_bank_verifications_updated_at
    BEFORE UPDATE ON bank_account_verifications
    FOR EACH ROW
    EXECUTE FUNCTION update_ach_updated_at();

CREATE TRIGGER update_ach_limits_updated_at
    BEFORE UPDATE ON ach_transfer_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_ach_updated_at();

-- Comments for documentation
COMMENT ON TABLE ach_transfers IS 'Stores ACH transfer records for bank account deposits and withdrawals';
COMMENT ON COLUMN ach_transfers.routing_number IS 'Encrypted bank routing number';
COMMENT ON COLUMN ach_transfers.account_number IS 'Encrypted bank account number';
COMMENT ON COLUMN ach_transfers.idempotency_key IS 'Unique key to prevent duplicate transfers';
COMMENT ON COLUMN ach_transfers.external_reference_id IS 'Reference ID from banking partner API';

COMMENT ON TABLE ach_return_codes IS 'Lookup table for ACH return reason codes';
COMMENT ON TABLE ach_webhook_events IS 'Log of webhook events received from banking partners';
COMMENT ON TABLE bank_account_verifications IS 'Verified bank accounts for ACH transfers';
COMMENT ON TABLE ach_transfer_limits IS 'Configurable limits for ACH transfers by user tier';