-- Check Deposit Tables
-- Enables mobile check deposits with image processing and fraud detection

-- Create enum types for check deposits
CREATE TYPE check_deposit_status AS ENUM (
    'PENDING',              -- Initial state, images uploaded
    'IMAGE_PROCESSING',     -- Processing check images
    'MICR_VALIDATION',     -- Validating MICR data
    'AMOUNT_VERIFICATION', -- Verifying check amount
    'FRAUD_CHECK',        -- Running fraud detection
    'MANUAL_REVIEW',      -- Requires manual review
    'APPROVED',           -- Approved for deposit
    'PROCESSING',         -- Being processed by bank
    'DEPOSITED',         -- Successfully deposited
    'PARTIAL_HOLD',      -- Deposited with partial hold
    'FULL_HOLD',        -- Deposited with full hold
    'REJECTED',         -- Rejected (bad image, fraud, etc.)
    'RETURNED',        -- Returned by bank
    'CANCELLED'       -- Cancelled by user or system
);

CREATE TYPE check_hold_type AS ENUM (
    'NONE',          -- No hold
    'NEXT_DAY',     -- Next business day availability
    'TWO_DAY',      -- Two business day hold
    'FIVE_DAY',     -- Five business day hold
    'SEVEN_DAY',    -- Seven business day hold
    'EXTENDED',     -- Extended hold (case-by-case)
    'PARTIAL'       -- Partial availability
);

-- Main check deposits table
CREATE TABLE check_deposits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL,
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    status check_deposit_status NOT NULL DEFAULT 'PENDING',
    
    -- Check images
    front_image_url VARCHAR(500) NOT NULL, -- Encrypted S3 URL
    back_image_url VARCHAR(500) NOT NULL,  -- Encrypted S3 URL
    front_image_hash VARCHAR(64) NOT NULL, -- SHA-256 hash for duplicate detection
    back_image_hash VARCHAR(64) NOT NULL,  -- SHA-256 hash for duplicate detection
    
    -- MICR data (extracted from check)
    micr_routing_number VARCHAR(500), -- Encrypted
    micr_account_number VARCHAR(500), -- Encrypted
    check_number VARCHAR(20),
    micr_raw_data VARCHAR(500), -- Encrypted raw MICR line
    
    -- Check details
    payee_name VARCHAR(100),
    payor_name VARCHAR(100),
    check_date DATE,
    memo VARCHAR(255),
    
    -- Amount extraction
    extracted_amount DECIMAL(19,4),
    amount_confidence DECIMAL(5,4), -- 0-1 confidence score
    manual_review_required BOOLEAN NOT NULL DEFAULT false,
    
    -- Hold information
    hold_type check_hold_type,
    hold_release_date DATE,
    funds_available_date DATE,
    partial_availability_amount DECIMAL(19,4),
    
    -- Risk and fraud detection
    risk_score DECIMAL(5,4), -- 0-1 risk score
    fraud_indicators TEXT, -- JSON array of fraud indicators
    verification_status VARCHAR(50),
    
    -- External processing
    external_processor_id VARCHAR(100),
    external_reference_id VARCHAR(100),
    processor_response TEXT, -- JSON response from processor
    
    -- Return/rejection information
    return_code VARCHAR(10),
    return_reason VARCHAR(255),
    rejection_reason VARCHAR(500),
    
    -- Device and location info
    device_id VARCHAR(100),
    device_type VARCHAR(50),
    ip_address VARCHAR(45),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    
    -- Idempotency
    idempotency_key VARCHAR(100) UNIQUE,
    
    -- Timestamps
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processing_started_at TIMESTAMP WITH TIME ZONE,
    approved_at TIMESTAMP WITH TIME ZONE,
    deposited_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    returned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_check_user_id ON check_deposits(user_id);
CREATE INDEX idx_check_wallet_id ON check_deposits(wallet_id);
CREATE INDEX idx_check_status ON check_deposits(status);
CREATE INDEX idx_check_created_at ON check_deposits(created_at DESC);
CREATE INDEX idx_check_micr_account ON check_deposits(micr_account_number) WHERE micr_account_number IS NOT NULL;
CREATE INDEX idx_check_duplicate ON check_deposits(micr_routing_number, micr_account_number, check_number, amount) 
    WHERE status NOT IN ('REJECTED', 'CANCELLED');
CREATE INDEX idx_check_idempotency ON check_deposits(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_check_external_ref ON check_deposits(external_reference_id) WHERE external_reference_id IS NOT NULL;
CREATE INDEX idx_check_manual_review ON check_deposits(manual_review_required, status) 
    WHERE manual_review_required = true AND status = 'MANUAL_REVIEW';
CREATE INDEX idx_check_holds ON check_deposits(hold_release_date, status) 
    WHERE status IN ('PARTIAL_HOLD', 'FULL_HOLD');
CREATE INDEX idx_check_risk ON check_deposits(risk_score DESC) 
    WHERE risk_score IS NOT NULL AND status IN ('PENDING', 'IMAGE_PROCESSING', 'FRAUD_CHECK');

-- Check return codes lookup table
CREATE TABLE check_return_codes (
    code VARCHAR(10) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    is_retryable BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insert common check return codes
INSERT INTO check_return_codes (code, description, category, is_retryable) VALUES
('R01', 'Insufficient Funds', 'insufficient_funds', false),
('R02', 'Account Closed', 'account_closed', false),
('R03', 'No Account', 'invalid_account', false),
('R04', 'Invalid Account Number', 'invalid_account', false),
('R06', 'Returned per ODFI Request', 'bank_request', false),
('R07', 'Authorization Revoked', 'authorization', false),
('R08', 'Payment Stopped', 'stop_payment', false),
('R09', 'Uncollected Funds', 'insufficient_funds', false),
('R10', 'Customer Advises Not Authorized', 'authorization', false),
('R11', 'Check Truncation Return', 'technical', false),
('R12', 'Account Sold to Another Institution', 'account_issue', false),
('R13', 'Invalid Routing Number', 'invalid_routing', false),
('R14', 'Representative Payee Deceased', 'account_issue', false),
('R15', 'Beneficiary Deceased', 'account_issue', false),
('R16', 'Account Frozen', 'account_issue', false),
('R17', 'File Record Edit Criteria', 'technical', false),
('R20', 'Non-Transaction Account', 'account_type', false),
('R29', 'Corporate Customer Advises Not Authorized', 'authorization', false),
('R31', 'Permissible Return', 'bank_request', false),
('R40', 'Non-Participant in Check Truncation', 'technical', false),
('R41', 'Invalid Check Number', 'invalid_check', false),
('R42', 'Routing Number Check Digit Error', 'invalid_routing', false),
('R43', 'Invalid Account Number Check Digit', 'invalid_account', false),
('R44', 'Invalid Individual ID Number', 'invalid_id', false),
('R45', 'Invalid Individual Name', 'invalid_name', false),
('R46', 'Invalid Representative Payee Indicator', 'invalid_payee', false),
('R47', 'Duplicate Presentment', 'duplicate', false),
('R50', 'State Law Affecting Acceptance', 'legal', false),
('R51', 'Item Related to RCK Entry', 'rck_related', false),
('R52', 'Stop Payment on Item', 'stop_payment', false),
('R53', 'Item and ACH Entry Presented', 'duplicate', false);

-- Check deposit limits configuration
CREATE TABLE check_deposit_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_tier VARCHAR(50) NOT NULL,
    daily_limit DECIMAL(19,4) NOT NULL,
    monthly_limit DECIMAL(19,4) NOT NULL,
    single_check_limit DECIMAL(19,4) NOT NULL,
    new_user_limit DECIMAL(19,4) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insert default limits
INSERT INTO check_deposit_limits (user_tier, daily_limit, monthly_limit, single_check_limit, new_user_limit) VALUES
('BASIC', 1000.00, 5000.00, 500.00, 200.00),
('STANDARD', 5000.00, 20000.00, 2500.00, 500.00),
('PREMIUM', 10000.00, 50000.00, 5000.00, 1000.00),
('ENTERPRISE', 50000.00, 200000.00, 25000.00, 5000.00);

-- Check hold policies configuration
CREATE TABLE check_hold_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_name VARCHAR(100) NOT NULL,
    description TEXT,
    
    -- Conditions
    min_amount DECIMAL(19,4),
    max_amount DECIMAL(19,4),
    min_account_age_days INTEGER,
    min_successful_deposits INTEGER,
    max_risk_score DECIMAL(5,4),
    
    -- Hold rules
    hold_type check_hold_type NOT NULL,
    immediate_availability_amount DECIMAL(19,4),
    immediate_availability_percentage DECIMAL(5,4),
    
    priority INTEGER NOT NULL DEFAULT 0, -- Higher priority policies are evaluated first
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insert default hold policies
INSERT INTO check_hold_policies (policy_name, description, min_amount, max_amount, min_account_age_days, 
    min_successful_deposits, max_risk_score, hold_type, immediate_availability_amount, priority) VALUES
('New Customer Small', 'New customers, small checks', 0, 200, 0, 0, 1.0, 'TWO_DAY', 200.00, 10),
('New Customer Large', 'New customers, large checks', 200.01, 100000, 0, 0, 1.0, 'FIVE_DAY', 200.00, 10),
('Established Small', 'Established customers, small checks', 0, 1000, 30, 5, 0.5, 'NEXT_DAY', 1000.00, 20),
('Established Medium', 'Established customers, medium checks', 1000.01, 5000, 30, 5, 0.5, 'TWO_DAY', 200.00, 20),
('Established Large', 'Established customers, large checks', 5000.01, 100000, 30, 5, 0.5, 'FIVE_DAY', 200.00, 20),
('VIP Customer', 'VIP customers, any amount', 0, 100000, 180, 20, 0.3, 'NONE', 100000.00, 30),
('High Risk', 'High risk deposits', 0, 100000, 0, 0, 0.7, 'SEVEN_DAY', 0.00, 5);

-- Check image metadata table
CREATE TABLE check_image_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_deposit_id UUID REFERENCES check_deposits(id),
    image_type VARCHAR(10) NOT NULL, -- 'front' or 'back'
    
    -- Image properties
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    size_bytes INTEGER NOT NULL,
    format VARCHAR(10) NOT NULL,
    
    -- Quality metrics
    contrast_ratio DECIMAL(5,4),
    blur_score DECIMAL(5,4),
    skew_angle DECIMAL(5,2),
    brightness_score DECIMAL(5,4),
    
    -- Processing results
    ocr_confidence DECIMAL(5,4),
    processing_time_ms INTEGER,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_check_image_deposit ON check_image_metadata(check_deposit_id);

-- Check deposit audit log
CREATE TABLE check_deposit_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_deposit_id UUID REFERENCES check_deposits(id),
    action VARCHAR(50) NOT NULL,
    performed_by UUID,
    performed_by_role VARCHAR(50),
    old_status check_deposit_status,
    new_status check_deposit_status,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_check_audit_deposit ON check_deposit_audit_log(check_deposit_id);
CREATE INDEX idx_check_audit_created ON check_deposit_audit_log(created_at DESC);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_check_deposit_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for updated_at
CREATE TRIGGER update_check_deposits_updated_at
    BEFORE UPDATE ON check_deposits
    FOR EACH ROW
    EXECUTE FUNCTION update_check_deposit_updated_at();

CREATE TRIGGER update_check_deposit_limits_updated_at
    BEFORE UPDATE ON check_deposit_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_check_deposit_updated_at();

CREATE TRIGGER update_check_hold_policies_updated_at
    BEFORE UPDATE ON check_hold_policies
    FOR EACH ROW
    EXECUTE FUNCTION update_check_deposit_updated_at();

-- Comments for documentation
COMMENT ON TABLE check_deposits IS 'Stores mobile check deposit records with image processing and fraud detection';
COMMENT ON COLUMN check_deposits.front_image_url IS 'Encrypted URL to front check image in secure storage';
COMMENT ON COLUMN check_deposits.back_image_url IS 'Encrypted URL to back check image in secure storage';
COMMENT ON COLUMN check_deposits.front_image_hash IS 'SHA-256 hash of front image for duplicate detection';
COMMENT ON COLUMN check_deposits.back_image_hash IS 'SHA-256 hash of back image for duplicate detection';
COMMENT ON COLUMN check_deposits.micr_routing_number IS 'Encrypted routing number extracted from MICR line';
COMMENT ON COLUMN check_deposits.micr_account_number IS 'Encrypted account number extracted from MICR line';
COMMENT ON COLUMN check_deposits.risk_score IS 'Fraud risk score from 0 (low risk) to 1 (high risk)';
COMMENT ON COLUMN check_deposits.fraud_indicators IS 'JSON array of detected fraud indicators';

COMMENT ON TABLE check_return_codes IS 'Lookup table for check return reason codes';
COMMENT ON TABLE check_deposit_limits IS 'Configurable limits for check deposits by user tier';
COMMENT ON TABLE check_hold_policies IS 'Configurable hold policies based on various criteria';
COMMENT ON TABLE check_image_metadata IS 'Metadata and quality metrics for check images';
COMMENT ON TABLE check_deposit_audit_log IS 'Audit trail for check deposit status changes and manual actions';