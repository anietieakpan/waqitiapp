-- Create Credit Assessments Table
-- Stores credit scoring and risk assessment data

CREATE TABLE credit_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    application_id UUID REFERENCES bnpl_applications(id) ON DELETE SET NULL,
    
    -- Assessment Details
    assessment_type VARCHAR(50) NOT NULL, -- INITIAL, PERIODIC, AD_HOC
    assessment_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Credit Score
    credit_score INTEGER,
    credit_score_source VARCHAR(50), -- INTERNAL, EXPERIAN, EQUIFAX, TRANSUNION
    score_date DATE,
    score_version VARCHAR(20),
    
    -- Income Analysis
    monthly_income DECIMAL(15,2),
    income_source VARCHAR(100),
    income_verified BOOLEAN DEFAULT FALSE,
    employment_status VARCHAR(50),
    employment_length_months INTEGER,
    
    -- Financial Profile
    bank_balance DECIMAL(15,2),
    average_monthly_balance DECIMAL(15,2),
    transaction_volume_monthly INTEGER,
    overdraft_incidents_6m INTEGER DEFAULT 0,
    
    -- Debt Analysis
    total_debt DECIMAL(15,2) DEFAULT 0.00,
    monthly_debt_payments DECIMAL(15,2) DEFAULT 0.00,
    debt_to_income_ratio DECIMAL(5,4),
    credit_utilization_ratio DECIMAL(5,4),
    
    -- Payment History
    on_time_payments_percentage DECIMAL(5,2),
    late_payments_30d INTEGER DEFAULT 0,
    late_payments_60d INTEGER DEFAULT 0,
    late_payments_90d INTEGER DEFAULT 0,
    charge_offs INTEGER DEFAULT 0,
    
    -- Risk Factors
    risk_tier VARCHAR(20), -- LOW, MEDIUM, HIGH, VERY_HIGH
    risk_score INTEGER,
    risk_factors JSONB,
    
    -- Alternative Data
    social_score INTEGER,
    digital_footprint_score INTEGER,
    behavioral_score INTEGER,
    
    -- Decision Support
    recommended_limit DECIMAL(15,2),
    recommended_terms JSONB,
    assessment_notes TEXT,
    
    -- Validity
    valid_until TIMESTAMP WITH TIME ZONE,
    superseded_by UUID REFERENCES credit_assessments(id),
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_credit_score CHECK (credit_score IS NULL OR (credit_score >= 300 AND credit_score <= 850)),
    CONSTRAINT check_risk_score CHECK (risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 100)),
    CONSTRAINT check_monthly_income CHECK (monthly_income IS NULL OR monthly_income >= 0),
    CONSTRAINT check_debt_to_income CHECK (debt_to_income_ratio IS NULL OR (debt_to_income_ratio >= 0 AND debt_to_income_ratio <= 10))
);

-- Create indexes
CREATE INDEX idx_credit_assessments_user_id ON credit_assessments(user_id);
CREATE INDEX idx_credit_assessments_application_id ON credit_assessments(application_id);
CREATE INDEX idx_credit_assessments_assessment_date ON credit_assessments(assessment_date);
CREATE INDEX idx_credit_assessments_risk_tier ON credit_assessments(risk_tier);
CREATE INDEX idx_credit_assessments_is_active ON credit_assessments(is_active);
CREATE INDEX idx_credit_assessments_valid_until ON credit_assessments(valid_until);

-- Create trigger for updated_at
CREATE TRIGGER update_credit_assessments_updated_at 
    BEFORE UPDATE ON credit_assessments 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();