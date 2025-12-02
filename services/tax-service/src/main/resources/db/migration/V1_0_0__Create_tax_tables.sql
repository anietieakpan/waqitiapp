-- Tax Service Database Migration V1.0.0
-- Creates all tables for comprehensive tax filing system

-- Tax Returns table - main tax return records
CREATE TABLE tax_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    tax_year INTEGER NOT NULL,
    filing_status VARCHAR(30) NOT NULL CHECK (filing_status IN ('SINGLE', 'MARRIED_FILING_JOINTLY', 'MARRIED_FILING_SEPARATELY', 'HEAD_OF_HOUSEHOLD', 'QUALIFYING_WIDOW')),
    
    -- Personal Information (embedded)
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    ssn VARCHAR(11) NOT NULL,
    date_of_birth DATE,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(2) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255) NOT NULL,
    
    -- Tax Calculation Fields
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'READY_TO_FILE', 'FILED', 'ACCEPTED', 'REJECTED', 'AMENDED')),
    estimated_refund DECIMAL(19,2),
    estimated_tax DECIMAL(19,2),
    adjusted_gross_income DECIMAL(19,2),
    total_income DECIMAL(19,2),
    federal_tax DECIMAL(19,2),
    state_tax DECIMAL(19,2),
    capital_gains DECIMAL(19,2) DEFAULT 0,
    deductions DECIMAL(19,2),
    tax_credits DECIMAL(19,2),
    total_withholdings DECIMAL(19,2),
    
    -- Flags and Options
    is_premium BOOLEAN DEFAULT false,
    include_crypto BOOLEAN DEFAULT false,
    include_investments BOOLEAN DEFAULT false,
    is_irs_authorized BOOLEAN DEFAULT false,
    is_state_return_required BOOLEAN DEFAULT true,
    
    -- Filing Information
    irs_confirmation_number VARCHAR(50),
    state_confirmation_number VARCHAR(50),
    refund_received BOOLEAN DEFAULT false,
    refund_received_date TIMESTAMP WITH TIME ZONE,
    filed_at TIMESTAMP WITH TIME ZONE,
    
    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_tax_returns_user_year UNIQUE (user_id, tax_year)
);

-- Indexes for tax_returns
CREATE INDEX idx_tax_returns_user_id ON tax_returns(user_id);
CREATE INDEX idx_tax_returns_tax_year ON tax_returns(tax_year);
CREATE INDEX idx_tax_returns_status ON tax_returns(status);
CREATE INDEX idx_tax_returns_filed_at ON tax_returns(filed_at);
CREATE INDEX idx_tax_returns_refund_tracking ON tax_returns(status, estimated_refund, refund_received) WHERE status = 'FILED' AND estimated_refund > 0;

-- Tax Documents table - stores W-2, 1099, and other tax documents
CREATE TABLE tax_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_return_id UUID NOT NULL REFERENCES tax_returns(id) ON DELETE CASCADE,
    
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('W2', 'FORM_1099', 'FORM_1099K', 'FORM_1099B', 'FORM_1099INT', 'FORM_1099DIV', 'FORM_8949', 'SCHEDULE_C', 'SCHEDULE_D', 'OTHER')),
    document_name VARCHAR(255) NOT NULL,
    document_data TEXT, -- Encrypted JSON data
    
    tax_year INTEGER NOT NULL,
    issuer_name VARCHAR(255),
    issuer_tin VARCHAR(20),
    recipient_tin VARCHAR(20),
    form_id VARCHAR(100), -- External form ID
    
    is_verified BOOLEAN NOT NULL DEFAULT false,
    verification_status VARCHAR(100),
    source VARCHAR(50), -- 'IRS', 'EMPLOYER', 'MANUAL', etc.
    
    -- File storage
    file_path VARCHAR(500),
    file_size BIGINT,
    checksum VARCHAR(128),
    
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for tax_documents
CREATE INDEX idx_tax_documents_return_id ON tax_documents(tax_return_id);
CREATE INDEX idx_tax_documents_type ON tax_documents(document_type);
CREATE INDEX idx_tax_documents_tax_year ON tax_documents(tax_year);
CREATE INDEX idx_tax_documents_verification ON tax_documents(is_verified);
CREATE INDEX idx_tax_documents_source ON tax_documents(source);
CREATE INDEX idx_tax_documents_form_id ON tax_documents(form_id);
CREATE UNIQUE INDEX idx_tax_documents_checksum ON tax_documents(checksum, tax_return_id) WHERE checksum IS NOT NULL;

-- Tax Estimates table - stores calculation results and estimates
CREATE TABLE tax_estimates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_return_id UUID NOT NULL REFERENCES tax_returns(id) ON DELETE CASCADE,
    
    -- Income and Deductions
    total_income DECIMAL(19,2) NOT NULL,
    adjusted_gross_income DECIMAL(19,2) NOT NULL,
    deductions DECIMAL(19,2) NOT NULL,
    taxable_income DECIMAL(19,2) NOT NULL,
    
    -- Tax Calculations
    federal_tax DECIMAL(19,2) NOT NULL,
    state_tax DECIMAL(19,2) NOT NULL,
    tax_credits DECIMAL(19,2) NOT NULL,
    total_tax DECIMAL(19,2) NOT NULL,
    total_withheld DECIMAL(19,2) NOT NULL,
    
    -- Results
    estimated_refund DECIMAL(19,2) NOT NULL,
    amount_owed DECIMAL(19,2) NOT NULL,
    effective_tax_rate DECIMAL(5,4),
    marginal_tax_rate DECIMAL(5,4),
    
    -- Optimization
    potential_savings DECIMAL(19,2),
    optimization_suggestions TEXT, -- JSON array
    
    -- Metadata
    calculation_method VARCHAR(50),
    confidence_score DECIMAL(3,2), -- 0.00 to 1.00
    is_current BOOLEAN NOT NULL DEFAULT true,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP WITH TIME ZONE
);

-- Indexes for tax_estimates
CREATE INDEX idx_tax_estimates_return_id ON tax_estimates(tax_return_id);
CREATE INDEX idx_tax_estimates_current ON tax_estimates(tax_return_id, is_current);
CREATE INDEX idx_tax_estimates_calculated_at ON tax_estimates(calculated_at);
CREATE INDEX idx_tax_estimates_confidence ON tax_estimates(confidence_score);

-- Tax Forms table - stores generated tax forms (1040, Schedule D, etc.)
CREATE TABLE tax_forms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_return_id UUID NOT NULL REFERENCES tax_returns(id) ON DELETE CASCADE,
    
    form_type VARCHAR(30) NOT NULL CHECK (form_type IN ('FORM_1040', 'FORM_1040_SR', 'SCHEDULE_A', 'SCHEDULE_B', 'SCHEDULE_C', 'SCHEDULE_D', 'SCHEDULE_E', 'FORM_8949', 'FORM_8938', 'FORM_3800', 'FORM_1116', 'SCHEDULE_SE', 'FORM_4868', 'FORM_1040X', 'STATE_RETURN')),
    form_number VARCHAR(20) NOT NULL,
    form_name VARCHAR(255) NOT NULL,
    form_data TEXT NOT NULL, -- JSON representation
    
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'VALIDATED', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'AMENDED')),
    version VARCHAR(10),
    tax_year INTEGER NOT NULL,
    sequence_number INTEGER,
    is_final BOOLEAN NOT NULL DEFAULT false,
    
    validation_errors TEXT, -- JSON array
    checksum VARCHAR(128),
    
    -- Generated files
    pdf_path VARCHAR(500),
    xml_data TEXT, -- IRS XML format
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for tax_forms
CREATE INDEX idx_tax_forms_return_id ON tax_forms(tax_return_id);
CREATE INDEX idx_tax_forms_form_type ON tax_forms(form_type);
CREATE INDEX idx_tax_forms_status ON tax_forms(status);
CREATE INDEX idx_tax_forms_final ON tax_forms(tax_return_id, form_type, is_final);
CREATE INDEX idx_tax_forms_submitted ON tax_forms(submitted_at);

-- Update triggers for last_modified timestamps
CREATE OR REPLACE FUNCTION update_last_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_modified = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply update triggers
CREATE TRIGGER update_tax_returns_last_modified BEFORE UPDATE ON tax_returns FOR EACH ROW EXECUTE FUNCTION update_last_modified_column();
CREATE TRIGGER update_tax_forms_updated_at BEFORE UPDATE ON tax_forms FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE tax_returns IS 'Main tax return records with personal info and calculation results';
COMMENT ON TABLE tax_documents IS 'Tax documents like W-2, 1099 forms with encrypted storage';
COMMENT ON TABLE tax_estimates IS 'Tax calculation estimates with optimization suggestions';
COMMENT ON TABLE tax_forms IS 'Generated tax forms in JSON format with PDF and XML exports';

COMMENT ON COLUMN tax_returns.ssn IS 'Encrypted Social Security Number';
COMMENT ON COLUMN tax_documents.document_data IS 'Encrypted JSON containing form data';
COMMENT ON COLUMN tax_estimates.optimization_suggestions IS 'JSON array of tax optimization recommendations';
COMMENT ON COLUMN tax_forms.form_data IS 'JSON representation of form fields and values';
COMMENT ON COLUMN tax_forms.xml_data IS 'IRS XML format for electronic filing';