-- Business Service Initial Schema
-- Created: 2025-09-27
-- Description: Business account management and commercial banking schema

CREATE TABLE IF NOT EXISTS business_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    dba_name VARCHAR(255),
    business_type VARCHAR(50) NOT NULL,
    legal_structure VARCHAR(50) NOT NULL,
    industry VARCHAR(100) NOT NULL,
    industry_code VARCHAR(20),
    naics_code VARCHAR(10),
    sic_code VARCHAR(10),
    tax_id_encrypted VARCHAR(255) NOT NULL,
    tax_id_hash VARCHAR(128) NOT NULL,
    registration_number VARCHAR(100),
    registration_state VARCHAR(50),
    date_of_incorporation DATE,
    country_of_incorporation VARCHAR(3),
    business_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    kyb_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyb_verified_at TIMESTAMP,
    risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    website_url VARCHAR(500),
    phone_number VARCHAR(20),
    email VARCHAR(255),
    number_of_employees INTEGER,
    annual_revenue DECIMAL(18, 2),
    revenue_currency VARCHAR(3) DEFAULT 'USD',
    is_publicly_traded BOOLEAN DEFAULT FALSE,
    stock_symbol VARCHAR(20),
    stock_exchange VARCHAR(50),
    duns_number VARCHAR(20),
    relationship_manager_id VARCHAR(100),
    account_opened_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS business_owner (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    ssn_encrypted VARCHAR(255),
    ssn_hash VARCHAR(128),
    ownership_percentage DECIMAL(5, 4) NOT NULL,
    title VARCHAR(100),
    is_beneficial_owner BOOLEAN DEFAULT TRUE,
    is_control_person BOOLEAN DEFAULT FALSE,
    is_authorized_signer BOOLEAN DEFAULT FALSE,
    email VARCHAR(255),
    phone_number VARCHAR(20),
    address JSONB,
    citizenship VARCHAR(3),
    id_type VARCHAR(50),
    id_number_encrypted VARCHAR(255),
    id_number_hash VARCHAR(128),
    id_expiry_date DATE,
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_verified_at TIMESTAMP,
    pep_status BOOLEAN DEFAULT FALSE,
    sanctions_status BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_owner FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    address_type VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    is_primary BOOLEAN DEFAULT FALSE,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_address FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_banking_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) UNIQUE NOT NULL,
    account_number VARCHAR(50) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    account_subtype VARCHAR(50),
    account_name VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    current_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    available_balance DECIMAL(18, 2) NOT NULL DEFAULT 0,
    overdraft_limit DECIMAL(18, 2) DEFAULT 0,
    interest_rate DECIMAL(5, 4),
    minimum_balance DECIMAL(18, 2) DEFAULT 0,
    monthly_fee DECIMAL(10, 2) DEFAULT 0,
    transaction_fee DECIMAL(10, 2) DEFAULT 0,
    free_transactions_per_month INTEGER DEFAULT 0,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    opened_date DATE NOT NULL,
    closed_date DATE,
    closure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_banking_account FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_authorized_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authorized_user_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    account_id VARCHAR(100),
    user_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    title VARCHAR(100),
    role VARCHAR(50) NOT NULL,
    permission_level VARCHAR(20) NOT NULL,
    permissions TEXT[],
    transaction_limit DECIMAL(15, 2),
    daily_limit DECIMAL(15, 2),
    is_active BOOLEAN DEFAULT TRUE,
    added_by VARCHAR(100) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP,
    deactivated_at TIMESTAMP,
    deactivation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_authorized_user FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_category VARCHAR(50) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    file_url TEXT NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    uploaded_by VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_by VARCHAR(100),
    verified_at TIMESTAMP,
    expiry_date DATE,
    is_expired BOOLEAN DEFAULT FALSE,
    retention_period_days INTEGER,
    tags TEXT[],
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_document FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    transaction_category VARCHAR(50),
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    value_date DATE NOT NULL,
    posting_date DATE,
    description TEXT,
    reference_number VARCHAR(100),
    initiated_by VARCHAR(100),
    approved_by VARCHAR(100),
    approval_required BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    balance_after DECIMAL(18, 2),
    counterparty_name VARCHAR(255),
    counterparty_account VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_transaction FOREIGN KEY (account_id) REFERENCES business_banking_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_credit_facility (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    facility_type VARCHAR(50) NOT NULL,
    facility_name VARCHAR(255) NOT NULL,
    credit_limit DECIMAL(18, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    utilized_amount DECIMAL(18, 2) DEFAULT 0,
    available_amount DECIMAL(18, 2) NOT NULL,
    utilization_percentage DECIMAL(5, 4) DEFAULT 0,
    interest_rate DECIMAL(5, 4) NOT NULL,
    interest_rate_type VARCHAR(20) NOT NULL,
    origination_fee DECIMAL(10, 2),
    maintenance_fee DECIMAL(10, 2),
    facility_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approved_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    maturity_date DATE,
    review_date DATE,
    collateral_required BOOLEAN DEFAULT FALSE,
    collateral_value DECIMAL(18, 2),
    covenants TEXT[],
    terms_and_conditions TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_credit_facility FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    invoice_number VARCHAR(100) UNIQUE NOT NULL,
    invoice_type VARCHAR(20) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_reference VARCHAR(100),
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    payment_terms VARCHAR(50),
    subtotal DECIMAL(15, 2) NOT NULL,
    tax_amount DECIMAL(15, 2) DEFAULT 0,
    discount_amount DECIMAL(15, 2) DEFAULT 0,
    total_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    amount_paid DECIMAL(15, 2) DEFAULT 0,
    amount_outstanding DECIMAL(15, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    paid_date DATE,
    line_items JSONB,
    notes TEXT,
    payment_instructions TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_invoice FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_payroll (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    payroll_period_start DATE NOT NULL,
    payroll_period_end DATE NOT NULL,
    payment_date DATE NOT NULL,
    total_employees INTEGER NOT NULL,
    total_gross_pay DECIMAL(15, 2) NOT NULL,
    total_deductions DECIMAL(15, 2) NOT NULL,
    total_taxes DECIMAL(15, 2) NOT NULL,
    total_net_pay DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    processed_by VARCHAR(100),
    processed_at TIMESTAMP,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    disbursed BOOLEAN DEFAULT FALSE,
    disbursed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_payroll FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS business_financial_statement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id VARCHAR(100) UNIQUE NOT NULL,
    business_id VARCHAR(100) NOT NULL,
    statement_type VARCHAR(50) NOT NULL,
    fiscal_year INTEGER NOT NULL,
    fiscal_period VARCHAR(20),
    statement_date DATE NOT NULL,
    total_assets DECIMAL(18, 2),
    total_liabilities DECIMAL(18, 2),
    total_equity DECIMAL(18, 2),
    total_revenue DECIMAL(18, 2),
    total_expenses DECIMAL(18, 2),
    net_income DECIMAL(18, 2),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    statement_data JSONB NOT NULL,
    is_audited BOOLEAN DEFAULT FALSE,
    audited_by VARCHAR(255),
    audit_date DATE,
    audit_opinion TEXT,
    file_url TEXT,
    uploaded_by VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_business_financial_statement FOREIGN KEY (business_id) REFERENCES business_account(business_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_business_account_customer ON business_account(customer_id);
CREATE INDEX idx_business_account_status ON business_account(status);
CREATE INDEX idx_business_account_kyb ON business_account(kyb_status);
CREATE INDEX idx_business_account_industry ON business_account(industry);
CREATE INDEX idx_business_account_type ON business_account(business_type);
CREATE INDEX idx_business_account_name ON business_account(business_name);

CREATE INDEX idx_business_owner_business ON business_owner(business_id);
CREATE INDEX idx_business_owner_name ON business_owner(last_name, first_name);
CREATE INDEX idx_business_owner_kyc ON business_owner(kyc_status);
CREATE INDEX idx_business_owner_beneficial ON business_owner(is_beneficial_owner) WHERE is_beneficial_owner = true;

CREATE INDEX idx_business_address_business ON business_address(business_id);
CREATE INDEX idx_business_address_type ON business_address(address_type);
CREATE INDEX idx_business_address_primary ON business_address(is_primary) WHERE is_primary = true;

CREATE INDEX idx_business_banking_account_business ON business_banking_account(business_id);
CREATE INDEX idx_business_banking_account_type ON business_banking_account(account_type);
CREATE INDEX idx_business_banking_account_status ON business_banking_account(account_status);

CREATE INDEX idx_business_authorized_user_business ON business_authorized_user(business_id);
CREATE INDEX idx_business_authorized_user_account ON business_authorized_user(account_id);
CREATE INDEX idx_business_authorized_user_user ON business_authorized_user(user_id);
CREATE INDEX idx_business_authorized_user_active ON business_authorized_user(is_active) WHERE is_active = true;

CREATE INDEX idx_business_document_business ON business_document(business_id);
CREATE INDEX idx_business_document_type ON business_document(document_type);
CREATE INDEX idx_business_document_category ON business_document(document_category);
CREATE INDEX idx_business_document_expiry ON business_document(expiry_date) WHERE is_expired = false;

CREATE INDEX idx_business_transaction_account ON business_transaction(account_id);
CREATE INDEX idx_business_transaction_business ON business_transaction(business_id);
CREATE INDEX idx_business_transaction_date ON business_transaction(transaction_date DESC);
CREATE INDEX idx_business_transaction_type ON business_transaction(transaction_type);
CREATE INDEX idx_business_transaction_status ON business_transaction(status);

CREATE INDEX idx_business_credit_facility_business ON business_credit_facility(business_id);
CREATE INDEX idx_business_credit_facility_type ON business_credit_facility(facility_type);
CREATE INDEX idx_business_credit_facility_status ON business_credit_facility(facility_status);
CREATE INDEX idx_business_credit_facility_maturity ON business_credit_facility(maturity_date);

CREATE INDEX idx_business_invoice_business ON business_invoice(business_id);
CREATE INDEX idx_business_invoice_number ON business_invoice(invoice_number);
CREATE INDEX idx_business_invoice_status ON business_invoice(status);
CREATE INDEX idx_business_invoice_payment_status ON business_invoice(payment_status);
CREATE INDEX idx_business_invoice_due_date ON business_invoice(due_date);

CREATE INDEX idx_business_payroll_business ON business_payroll(business_id);
CREATE INDEX idx_business_payroll_period ON business_payroll(payroll_period_end DESC);
CREATE INDEX idx_business_payroll_status ON business_payroll(status);

CREATE INDEX idx_business_financial_statement_business ON business_financial_statement(business_id);
CREATE INDEX idx_business_financial_statement_type ON business_financial_statement(statement_type);
CREATE INDEX idx_business_financial_statement_fiscal ON business_financial_statement(fiscal_year, fiscal_period);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_business_account_updated_at BEFORE UPDATE ON business_account
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_business_owner_updated_at BEFORE UPDATE ON business_owner
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_business_address_updated_at BEFORE UPDATE ON business_address
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_business_banking_account_updated_at BEFORE UPDATE ON business_banking_account
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_business_authorized_user_updated_at BEFORE UPDATE ON business_authorized_user
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_business_credit_facility_updated_at BEFORE UPDATE ON business_credit_facility
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_business_invoice_updated_at BEFORE UPDATE ON business_invoice
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_business_payroll_updated_at BEFORE UPDATE ON business_payroll
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();