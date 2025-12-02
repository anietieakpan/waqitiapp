-- Create BNPL Installments Table
-- Stores individual installment payment schedule

CREATE TABLE bnpl_installments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES bnpl_applications(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    
    -- Installment Details
    installment_number INTEGER NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    principal_amount DECIMAL(15,2) NOT NULL,
    interest_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    fee_amount DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    
    -- Due Date and Status
    due_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    
    -- Payment Information
    payment_date TIMESTAMP WITH TIME ZONE,
    payment_amount DECIMAL(15,2),
    payment_method VARCHAR(50),
    payment_reference VARCHAR(100),
    transaction_id UUID,
    
    -- Late Payment
    days_late INTEGER DEFAULT 0,
    late_fee_amount DECIMAL(15,2) DEFAULT 0.00,
    late_fee_applied_date TIMESTAMP WITH TIME ZONE,
    
    -- Retry Information
    retry_count INTEGER DEFAULT 0,
    next_retry_date TIMESTAMP WITH TIME ZONE,
    last_retry_date TIMESTAMP WITH TIME ZONE,
    
    -- Collections
    collection_status VARCHAR(50),
    collection_assigned_date TIMESTAMP WITH TIME ZONE,
    collection_notes TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_installment_amount CHECK (amount > 0),
    CONSTRAINT check_principal_amount CHECK (principal_amount >= 0),
    CONSTRAINT check_interest_amount CHECK (interest_amount >= 0),
    CONSTRAINT check_fee_amount CHECK (fee_amount >= 0),
    CONSTRAINT check_installment_number CHECK (installment_number > 0),
    CONSTRAINT check_days_late CHECK (days_late >= 0),
    CONSTRAINT unique_application_installment UNIQUE (application_id, installment_number)
);

-- Create indexes
CREATE INDEX idx_bnpl_installments_application_id ON bnpl_installments(application_id);
CREATE INDEX idx_bnpl_installments_user_id ON bnpl_installments(user_id);
CREATE INDEX idx_bnpl_installments_due_date ON bnpl_installments(due_date);
CREATE INDEX idx_bnpl_installments_status ON bnpl_installments(status);
CREATE INDEX idx_bnpl_installments_payment_date ON bnpl_installments(payment_date);
CREATE INDEX idx_bnpl_installments_collection_status ON bnpl_installments(collection_status);
CREATE INDEX idx_bnpl_installments_next_retry ON bnpl_installments(next_retry_date) WHERE next_retry_date IS NOT NULL;

-- Create trigger for updated_at
CREATE TRIGGER update_bnpl_installments_updated_at 
    BEFORE UPDATE ON bnpl_installments 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();