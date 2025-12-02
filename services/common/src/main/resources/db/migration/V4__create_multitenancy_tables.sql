-- P2P Multi-Tenancy Tables for Regional, Partner, and Segment Management

-- Main tenants table (regions, partners, segments)
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id VARCHAR(50) PRIMARY KEY,
    tenant_name VARCHAR(255) NOT NULL,
    tenant_type VARCHAR(50) NOT NULL, -- REGION, PARTNER, SEGMENT, WHITELABEL, NETWORK
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    currency VARCHAR(3),
    timezone VARCHAR(50),
    language VARCHAR(10),
    region VARCHAR(50),
    regulatory_body VARCHAR(100),
    configuration JSONB,
    compliance_requirements JSONB,
    fee_structures JSONB,
    transaction_limits JSONB,
    supported_payment_methods TEXT[],
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    UNIQUE(tenant_name)
);

-- User-tenant membership table (users can belong to multiple tenants)
CREATE TABLE IF NOT EXISTS user_tenant_memberships (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    membership_type VARCHAR(50) NOT NULL, -- PRIMARY, SECONDARY, TEMPORARY, GUEST
    membership_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    tenant_specific_data JSONB, -- Tenant-specific user settings
    kyc_status VARCHAR(50),
    kyc_level VARCHAR(50),
    transaction_limit_override JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE RESTRICT
);

-- Cross-tenant transfer rules
CREATE TABLE IF NOT EXISTS cross_tenant_rules (
    id VARCHAR(36) PRIMARY KEY,
    from_tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    to_tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    is_allowed BOOLEAN DEFAULT true,
    fee_multiplier DECIMAL(5,2) DEFAULT 1.0, -- Additional fee for cross-tenant
    minimum_kyc_level VARCHAR(50),
    compliance_level VARCHAR(50), -- STANDARD, ENHANCED, STRICT
    requires_approval BOOLEAN DEFAULT false,
    daily_limit DECIMAL(20,2),
    monthly_limit DECIMAL(20,2),
    excluded_payment_methods TEXT[],
    metadata JSONB,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(from_tenant_id, to_tenant_id)
);

-- Tenant-specific configurations
CREATE TABLE IF NOT EXISTS tenant_configurations (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    config_type VARCHAR(50), -- STRING, NUMBER, BOOLEAN, JSON
    is_encrypted BOOLEAN DEFAULT false,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, config_key)
);

-- Tenant fee structures
CREATE TABLE IF NOT EXISTS tenant_fee_structures (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    transaction_type VARCHAR(100) NOT NULL, -- P2P_TRANSFER, CROSS_TENANT_TRANSFER, etc.
    fee_type VARCHAR(50) NOT NULL, -- FIXED, PERCENTAGE, TIERED
    fee_value DECIMAL(10,4) NOT NULL,
    min_fee DECIMAL(20,2),
    max_fee DECIMAL(20,2),
    currency VARCHAR(3),
    tier_config JSONB, -- For tiered pricing
    effective_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tenant transaction limits
CREATE TABLE IF NOT EXISTS tenant_transaction_limits (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    user_type VARCHAR(50), -- BASIC, VERIFIED, PREMIUM
    transaction_type VARCHAR(100),
    daily_limit DECIMAL(20,2),
    weekly_limit DECIMAL(20,2),
    monthly_limit DECIMAL(20,2),
    per_transaction_limit DECIMAL(20,2),
    daily_count_limit INT,
    currency VARCHAR(3),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cross-tenant transfer history
CREATE TABLE IF NOT EXISTS cross_tenant_transfers (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) UNIQUE NOT NULL,
    from_user_id VARCHAR(255) NOT NULL,
    from_tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    to_user_id VARCHAR(255) NOT NULL,
    to_tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    source_amount DECIMAL(20,2) NOT NULL,
    source_currency VARCHAR(3) NOT NULL,
    destination_amount DECIMAL(20,2) NOT NULL,
    destination_currency VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(15,6),
    source_fee DECIMAL(20,2),
    destination_fee DECIMAL(20,2),
    total_fees DECIMAL(20,2),
    status VARCHAR(50) NOT NULL, -- PENDING, COMPLETED, FAILED, REVERSED
    compliance_status VARCHAR(50),
    compliance_checks JSONB,
    reference VARCHAR(255),
    metadata JSONB,
    initiated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    reversed_at TIMESTAMP
);

-- Tenant compliance requirements
CREATE TABLE IF NOT EXISTS tenant_compliance_requirements (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    requirement_type VARCHAR(100) NOT NULL, -- KYC, AML, SANCTIONS, REPORTING
    requirement_details JSONB NOT NULL,
    threshold_amount DECIMAL(20,2),
    frequency VARCHAR(50), -- ALWAYS, DAILY, WEEKLY, MONTHLY
    is_mandatory BOOLEAN DEFAULT true,
    regulatory_reference VARCHAR(255),
    effective_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tenant statistics and metrics
CREATE TABLE IF NOT EXISTS tenant_metrics (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    metric_date DATE NOT NULL,
    total_users BIGINT DEFAULT 0,
    active_users BIGINT DEFAULT 0,
    new_users BIGINT DEFAULT 0,
    total_transactions BIGINT DEFAULT 0,
    transaction_volume DECIMAL(30,2) DEFAULT 0,
    cross_tenant_transfers BIGINT DEFAULT 0,
    cross_tenant_volume DECIMAL(30,2) DEFAULT 0,
    average_transaction_size DECIMAL(20,2),
    compliance_rate DECIMAL(5,2), -- Percentage
    kyc_completion_rate DECIMAL(5,2),
    fee_revenue DECIMAL(20,2),
    top_corridors JSONB, -- Top transfer corridors
    payment_method_distribution JSONB,
    hourly_distribution JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, metric_date)
);

-- Exchange rates for cross-tenant transfers
CREATE TABLE IF NOT EXISTS tenant_exchange_rates (
    id VARCHAR(36) PRIMARY KEY,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(15,6) NOT NULL,
    inverse_rate DECIMAL(15,6),
    spread_percentage DECIMAL(5,2) DEFAULT 0,
    provider VARCHAR(100),
    valid_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(from_currency, to_currency, valid_from)
);

-- Partner tenant configurations (for organizations)
CREATE TABLE IF NOT EXISTS partner_tenant_configs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    organization_name VARCHAR(255) NOT NULL,
    organization_type VARCHAR(100), -- UNIVERSITY, COMPANY, NGO, GOVERNMENT
    domain_whitelist TEXT[], -- Allowed email domains
    ip_whitelist TEXT[], -- Allowed IP addresses
    custom_branding JSONB, -- Logo, colors, etc.
    subsidy_percentage DECIMAL(5,2), -- Fee subsidy for members
    bulk_transfer_enabled BOOLEAN DEFAULT false,
    api_access_enabled BOOLEAN DEFAULT false,
    webhook_url VARCHAR(500),
    admin_users TEXT[],
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Regional tenant configurations
CREATE TABLE IF NOT EXISTS regional_tenant_configs (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    country_code VARCHAR(2) NOT NULL,
    country_name VARCHAR(100) NOT NULL,
    central_bank VARCHAR(255),
    regulatory_framework VARCHAR(255),
    national_id_format VARCHAR(100),
    phone_number_format VARCHAR(100),
    bank_code_format VARCHAR(100),
    tax_configuration JSONB,
    holidays JSONB, -- National holidays
    business_hours JSONB,
    emergency_contacts JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_user_memberships_user ON user_tenant_memberships(user_id);
CREATE INDEX idx_user_memberships_tenant ON user_tenant_memberships(tenant_id);
CREATE INDEX idx_user_memberships_status ON user_tenant_memberships(membership_status);
CREATE INDEX idx_cross_tenant_rules_active ON cross_tenant_rules(from_tenant_id, to_tenant_id, is_active);
CREATE INDEX idx_cross_tenant_transfers_users ON cross_tenant_transfers(from_user_id, to_user_id);
CREATE INDEX idx_cross_tenant_transfers_tenants ON cross_tenant_transfers(from_tenant_id, to_tenant_id);
CREATE INDEX idx_cross_tenant_transfers_status ON cross_tenant_transfers(status, initiated_at);
CREATE INDEX idx_tenant_metrics_date ON tenant_metrics(tenant_id, metric_date);
CREATE INDEX idx_exchange_rates_active ON tenant_exchange_rates(from_currency, to_currency, is_active);
CREATE INDEX idx_tenant_configs_active ON tenant_configurations(tenant_id, is_active);
CREATE INDEX idx_tenant_fees_active ON tenant_fee_structures(tenant_id, transaction_type, is_active);

-- Insert default tenants
INSERT INTO tenants (tenant_id, tenant_name, tenant_type, status, currency, timezone, language, region, configuration) 
VALUES 
    ('GLOBAL', 'Global Network', 'NETWORK', 'ACTIVE', 'USD', 'UTC', 'en', 'GLOBAL', 
     '{"features": {"p2p_enabled": true, "cross_border_enabled": true}}'::jsonb),
    
    ('NG', 'Nigeria', 'REGION', 'ACTIVE', 'NGN', 'Africa/Lagos', 'en', 'WEST_AFRICA',
     '{"features": {"ussd_enabled": true, "bank_transfer_enabled": true}}'::jsonb),
    
    ('KE', 'Kenya', 'REGION', 'ACTIVE', 'KES', 'Africa/Nairobi', 'en', 'EAST_AFRICA',
     '{"features": {"mpesa_enabled": true, "bank_transfer_enabled": true}}'::jsonb),
    
    ('US', 'United States', 'REGION', 'ACTIVE', 'USD', 'America/New_York', 'en', 'NORTH_AMERICA',
     '{"features": {"ach_enabled": true, "wire_enabled": true, "card_enabled": true}}'::jsonb)
ON CONFLICT (tenant_id) DO NOTHING;

-- Insert default cross-tenant rules
INSERT INTO cross_tenant_rules (id, from_tenant_id, to_tenant_id, is_allowed, fee_multiplier, compliance_level)
VALUES
    (gen_random_uuid()::text, 'NG', 'KE', true, 1.5, 'STANDARD'),
    (gen_random_uuid()::text, 'KE', 'NG', true, 1.5, 'STANDARD'),
    (gen_random_uuid()::text, 'NG', 'US', true, 2.0, 'ENHANCED'),
    (gen_random_uuid()::text, 'US', 'NG', true, 2.0, 'ENHANCED'),
    (gen_random_uuid()::text, 'KE', 'US', true, 2.0, 'ENHANCED'),
    (gen_random_uuid()::text, 'US', 'KE', true, 2.0, 'ENHANCED')
ON CONFLICT (from_tenant_id, to_tenant_id) DO NOTHING;

-- Function to calculate tenant metrics
CREATE OR REPLACE FUNCTION calculate_tenant_metrics(p_tenant_id VARCHAR, p_date DATE)
RETURNS void AS $$
BEGIN
    INSERT INTO tenant_metrics (
        id, tenant_id, metric_date, total_users, active_users, 
        total_transactions, transaction_volume, cross_tenant_transfers
    )
    SELECT 
        gen_random_uuid()::text,
        p_tenant_id,
        p_date,
        COUNT(DISTINCT utm.user_id),
        COUNT(DISTINCT CASE WHEN utm.membership_status = 'ACTIVE' THEN utm.user_id END),
        COUNT(ctt.id),
        COALESCE(SUM(ctt.source_amount), 0),
        COUNT(CASE WHEN ctt.from_tenant_id != ctt.to_tenant_id THEN 1 END)
    FROM user_tenant_memberships utm
    LEFT JOIN cross_tenant_transfers ctt ON 
        (ctt.from_tenant_id = p_tenant_id OR ctt.to_tenant_id = p_tenant_id)
        AND DATE(ctt.initiated_at) = p_date
    WHERE utm.tenant_id = p_tenant_id
    ON CONFLICT (tenant_id, metric_date) DO UPDATE
    SET 
        total_users = EXCLUDED.total_users,
        active_users = EXCLUDED.active_users,
        total_transactions = EXCLUDED.total_transactions,
        transaction_volume = EXCLUDED.transaction_volume,
        cross_tenant_transfers = EXCLUDED.cross_tenant_transfers,
        updated_at = CURRENT_TIMESTAMP;
END;
$$ LANGUAGE plpgsql;

-- Function to check cross-tenant transfer eligibility
CREATE OR REPLACE FUNCTION check_cross_tenant_eligibility(
    p_from_tenant VARCHAR,
    p_to_tenant VARCHAR,
    p_amount DECIMAL
)
RETURNS TABLE(
    is_allowed BOOLEAN,
    fee_multiplier DECIMAL,
    compliance_level VARCHAR,
    requires_approval BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        ctr.is_allowed,
        ctr.fee_multiplier,
        ctr.compliance_level,
        ctr.requires_approval OR (p_amount > ctr.daily_limit) AS requires_approval
    FROM cross_tenant_rules ctr
    WHERE ctr.from_tenant_id = p_from_tenant
    AND ctr.to_tenant_id = p_to_tenant
    AND ctr.is_active = true;
END;
$$ LANGUAGE plpgsql;