-- Create BNPL Applications Table
-- Stores buy now pay later application details

CREATE TABLE bnpl_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    merchant_id UUID,
    merchant_name VARCHAR(255),
    order_id VARCHAR(100),
    
    -- Application Details
    application_number VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    
    -- Purchase Information
    purchase_amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    down_payment DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    financed_amount DECIMAL(15,2) NOT NULL,
    
    -- Terms
    installment_count INTEGER NOT NULL,
    installment_amount DECIMAL(15,2) NOT NULL,
    interest_rate DECIMAL(5,4) DEFAULT 0.0000,
    total_amount DECIMAL(15,2) NOT NULL,
    
    -- Dates
    application_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_date TIMESTAMP WITH TIME ZONE,
    first_payment_date DATE,
    final_payment_date DATE,
    
    -- Risk Assessment
    credit_score INTEGER,
    risk_tier VARCHAR(20),
    risk_factors JSONB,
    
    -- Decision
    decision VARCHAR(50),
    decision_reason TEXT,
    decision_date TIMESTAMP WITH TIME ZONE,
    decision_by VARCHAR(100),
    
    -- Metadata
    application_source VARCHAR(50),
    device_fingerprint VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_purchase_amount CHECK (purchase_amount > 0),
    CONSTRAINT check_financed_amount CHECK (financed_amount >= 0),
    CONSTRAINT check_installment_count CHECK (installment_count > 0),
    CONSTRAINT check_installment_amount CHECK (installment_amount > 0),
    CONSTRAINT check_total_amount CHECK (total_amount >= financed_amount)
);

-- Create indexes
CREATE INDEX idx_bnpl_applications_user_id ON bnpl_applications(user_id);
CREATE INDEX idx_bnpl_applications_merchant_id ON bnpl_applications(merchant_id);
CREATE INDEX idx_bnpl_applications_status ON bnpl_applications(status);
CREATE INDEX idx_bnpl_applications_application_date ON bnpl_applications(application_date);
CREATE INDEX idx_bnpl_applications_order_id ON bnpl_applications(order_id);
CREATE UNIQUE INDEX idx_bnpl_applications_number ON bnpl_applications(application_number);

-- Create trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_bnpl_applications_updated_at 
    BEFORE UPDATE ON bnpl_applications 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();