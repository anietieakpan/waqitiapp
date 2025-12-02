-- Customer Service Initial Schema
-- Created: 2025-09-27
-- Description: Customer profile, contact, and relationship management schema

CREATE TABLE IF NOT EXISTS customer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(100) UNIQUE NOT NULL,
    customer_type VARCHAR(20) NOT NULL,
    customer_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    customer_segment VARCHAR(50),
    preferred_language VARCHAR(10) DEFAULT 'en',
    preferred_currency VARCHAR(3) DEFAULT 'USD',
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_verified_at TIMESTAMP,
    aml_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    aml_verified_at TIMESTAMP,
    relationship_manager_id VARCHAR(100),
    onboarding_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    onboarding_channel VARCHAR(50),
    last_login_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    tags TEXT[],
    notes TEXT,
    is_pep BOOLEAN DEFAULT FALSE,
    is_sanctioned BOOLEAN DEFAULT FALSE,
    is_blocked BOOLEAN DEFAULT FALSE,
    blocked_reason TEXT,
    blocked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_individual (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(100) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20),
    nationality VARCHAR(3),
    country_of_residence VARCHAR(3),
    tax_id_encrypted VARCHAR(255),
    tax_id_hash VARCHAR(128),
    ssn_encrypted VARCHAR(255),
    ssn_hash VARCHAR(128),
    employment_status VARCHAR(50),
    occupation VARCHAR(100),
    employer_name VARCHAR(255),
    annual_income DECIMAL(15, 2),
    income_currency VARCHAR(3) DEFAULT 'USD',
    marital_status VARCHAR(20),
    education_level VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_individual FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_business (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(100) UNIQUE NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    industry VARCHAR(100) NOT NULL,
    industry_code VARCHAR(20),
    registration_number VARCHAR(100),
    tax_id_encrypted VARCHAR(255) NOT NULL,
    tax_id_hash VARCHAR(128) NOT NULL,
    date_of_incorporation DATE,
    country_of_incorporation VARCHAR(3),
    number_of_employees INTEGER,
    annual_revenue DECIMAL(18, 2),
    revenue_currency VARCHAR(3) DEFAULT 'USD',
    website_url VARCHAR(500),
    is_publicly_traded BOOLEAN DEFAULT FALSE,
    stock_symbol VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_business FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    contact_type VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    phone_number VARCHAR(20),
    country_code VARCHAR(5),
    extension VARCHAR(10),
    is_primary BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    verification_method VARCHAR(50),
    is_marketing_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_contact FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    address_type VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    address_line3 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50),
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    is_primary BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    verified_by VARCHAR(100),
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_address FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_identification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identification_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    id_type VARCHAR(50) NOT NULL,
    id_number_encrypted VARCHAR(255) NOT NULL,
    id_number_hash VARCHAR(128) NOT NULL,
    issuing_country VARCHAR(3) NOT NULL,
    issuing_authority VARCHAR(255),
    issue_date DATE,
    expiry_date DATE,
    is_expired BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    verified_by VARCHAR(100),
    document_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_identification FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_relationship (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    relationship_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    related_customer_id VARCHAR(100) NOT NULL,
    relationship_type VARCHAR(50) NOT NULL,
    relationship_status VARCHAR(20) DEFAULT 'ACTIVE',
    ownership_percentage DECIMAL(5, 4),
    authority_level VARCHAR(50),
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_relationship FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    CONSTRAINT fk_related_customer FOREIGN KEY (related_customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE,
    CONSTRAINT different_customers CHECK (customer_id != related_customer_id)
);

CREATE TABLE IF NOT EXISTS customer_preference (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(100) UNIQUE NOT NULL,
    notification_email BOOLEAN DEFAULT TRUE,
    notification_sms BOOLEAN DEFAULT TRUE,
    notification_push BOOLEAN DEFAULT TRUE,
    notification_in_app BOOLEAN DEFAULT TRUE,
    marketing_email BOOLEAN DEFAULT FALSE,
    marketing_sms BOOLEAN DEFAULT FALSE,
    statement_delivery VARCHAR(20) DEFAULT 'EMAIL',
    statement_frequency VARCHAR(20) DEFAULT 'MONTHLY',
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    two_factor_method VARCHAR(20),
    biometric_enabled BOOLEAN DEFAULT FALSE,
    session_timeout_minutes INTEGER DEFAULT 15,
    preferences JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_preference FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    file_url TEXT NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    uploaded_by VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_sensitive BOOLEAN DEFAULT FALSE,
    encryption_algorithm VARCHAR(50),
    retention_period_days INTEGER,
    expiry_date DATE,
    tags TEXT[],
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_document FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    note_type VARCHAR(50) NOT NULL,
    subject VARCHAR(255),
    note TEXT NOT NULL,
    is_internal BOOLEAN DEFAULT TRUE,
    is_alert BOOLEAN DEFAULT FALSE,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_note FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS customer_interaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interaction_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    interaction_type VARCHAR(50) NOT NULL,
    interaction_channel VARCHAR(50) NOT NULL,
    interaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_seconds INTEGER,
    subject VARCHAR(255),
    summary TEXT,
    sentiment VARCHAR(20),
    outcome VARCHAR(50),
    handled_by VARCHAR(100) NOT NULL,
    follow_up_required BOOLEAN DEFAULT FALSE,
    follow_up_date DATE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_customer_interaction FOREIGN KEY (customer_id) REFERENCES customer(customer_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_customer_type ON customer(customer_type);
CREATE INDEX idx_customer_status ON customer(customer_status);
CREATE INDEX idx_customer_risk ON customer(risk_level);
CREATE INDEX idx_customer_segment ON customer(customer_segment);
CREATE INDEX idx_customer_kyc ON customer(kyc_status);
CREATE INDEX idx_customer_blocked ON customer(is_blocked) WHERE is_blocked = true;
CREATE INDEX idx_customer_relationship_manager ON customer(relationship_manager_id);

CREATE INDEX idx_customer_individual_customer ON customer_individual(customer_id);
CREATE INDEX idx_customer_individual_name ON customer_individual(last_name, first_name);
CREATE INDEX idx_customer_individual_dob ON customer_individual(date_of_birth);

CREATE INDEX idx_customer_business_customer ON customer_business(customer_id);
CREATE INDEX idx_customer_business_name ON customer_business(business_name);
CREATE INDEX idx_customer_business_industry ON customer_business(industry);

CREATE INDEX idx_customer_contact_customer ON customer_contact(customer_id);
CREATE INDEX idx_customer_contact_email ON customer_contact(email);
CREATE INDEX idx_customer_contact_phone ON customer_contact(phone_number);
CREATE INDEX idx_customer_contact_primary ON customer_contact(is_primary) WHERE is_primary = true;

CREATE INDEX idx_customer_address_customer ON customer_address(customer_id);
CREATE INDEX idx_customer_address_country ON customer_address(country_code);
CREATE INDEX idx_customer_address_primary ON customer_address(is_primary) WHERE is_primary = true;

CREATE INDEX idx_customer_identification_customer ON customer_identification(customer_id);
CREATE INDEX idx_customer_identification_type ON customer_identification(id_type);
CREATE INDEX idx_customer_identification_expiry ON customer_identification(expiry_date) WHERE is_expired = false;

CREATE INDEX idx_customer_relationship_customer ON customer_relationship(customer_id);
CREATE INDEX idx_customer_relationship_related ON customer_relationship(related_customer_id);
CREATE INDEX idx_customer_relationship_type ON customer_relationship(relationship_type);

CREATE INDEX idx_customer_preference_customer ON customer_preference(customer_id);

CREATE INDEX idx_customer_document_customer ON customer_document(customer_id);
CREATE INDEX idx_customer_document_type ON customer_document(document_type);
CREATE INDEX idx_customer_document_uploaded ON customer_document(uploaded_at DESC);

CREATE INDEX idx_customer_note_customer ON customer_note(customer_id);
CREATE INDEX idx_customer_note_type ON customer_note(note_type);
CREATE INDEX idx_customer_note_alert ON customer_note(is_alert) WHERE is_alert = true;

CREATE INDEX idx_customer_interaction_customer ON customer_interaction(customer_id);
CREATE INDEX idx_customer_interaction_type ON customer_interaction(interaction_type);
CREATE INDEX idx_customer_interaction_channel ON customer_interaction(interaction_channel);
CREATE INDEX idx_customer_interaction_date ON customer_interaction(interaction_date DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_customer_updated_at BEFORE UPDATE ON customer
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_individual_updated_at BEFORE UPDATE ON customer_individual
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_business_updated_at BEFORE UPDATE ON customer_business
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_contact_updated_at BEFORE UPDATE ON customer_contact
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_address_updated_at BEFORE UPDATE ON customer_address
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_identification_updated_at BEFORE UPDATE ON customer_identification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_relationship_updated_at BEFORE UPDATE ON customer_relationship
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_preference_updated_at BEFORE UPDATE ON customer_preference
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customer_note_updated_at BEFORE UPDATE ON customer_note
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();