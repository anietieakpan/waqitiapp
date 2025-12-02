-- Migration V6: Fee and Tax Configuration System
-- Created: 2025-11-15
-- Description: Database-driven fee and tax configuration for dynamic pricing
-- CRITICAL: Enables fee/tax changes without code deployment

-- ============================================================================
-- FEE CONFIGURATION TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS fee_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_code VARCHAR(100) UNIQUE NOT NULL,
    fee_name VARCHAR(255) NOT NULL,
    fee_type VARCHAR(50) NOT NULL,
    fee_category VARCHAR(50) NOT NULL,
    calculation_method VARCHAR(50) NOT NULL,
    percentage_rate DECIMAL(7, 4),
    fixed_amount DECIMAL(19, 4),
    minimum_fee DECIMAL(19, 4),
    maximum_fee DECIMAL(19, 4),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    applies_to_transaction_types JSONB,
    applies_to_merchant_tiers JSONB,
    applies_to_user_segments JSONB,
    min_transaction_amount DECIMAL(19, 4),
    max_transaction_amount DECIMAL(19, 4),
    is_active BOOLEAN DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    priority INTEGER DEFAULT 0,
    description TEXT,
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0 NOT NULL,

    CONSTRAINT chk_fee_percentage CHECK (percentage_rate IS NULL OR (percentage_rate >= 0 AND percentage_rate <= 100)),
    CONSTRAINT chk_fee_fixed CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    CONSTRAINT chk_fee_min_max CHECK (minimum_fee IS NULL OR maximum_fee IS NULL OR minimum_fee <= maximum_fee),
    CONSTRAINT chk_fee_amount_range CHECK (min_transaction_amount IS NULL OR max_transaction_amount IS NULL OR min_transaction_amount <= max_transaction_amount),
    CONSTRAINT chk_fee_type CHECK (fee_type IN ('PLATFORM', 'PROCESSOR', 'NETWORK', 'GATEWAY', 'REGULATORY', 'SERVICE', 'CUSTOM')),
    CONSTRAINT chk_fee_category CHECK (fee_category IN ('TRANSACTION', 'SUBSCRIPTION', 'WITHDRAWAL', 'TRANSFER', 'CURRENCY_CONVERSION', 'LATE_PAYMENT', 'OTHER')),
    CONSTRAINT chk_calculation_method CHECK (calculation_method IN ('PERCENTAGE', 'FIXED', 'PERCENTAGE_PLUS_FIXED', 'TIERED', 'CUSTOM'))
);

-- ============================================================================
-- TAX CONFIGURATION TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS tax_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_code VARCHAR(100) UNIQUE NOT NULL,
    tax_name VARCHAR(255) NOT NULL,
    tax_type VARCHAR(50) NOT NULL,
    tax_jurisdiction VARCHAR(100) NOT NULL,
    tax_rate DECIMAL(7, 4) NOT NULL,
    applies_to_services JSONB,
    applies_to_states JSONB,
    applies_to_countries JSONB,
    tax_inclusive BOOLEAN DEFAULT FALSE,
    compound_tax BOOLEAN DEFAULT FALSE,
    min_taxable_amount DECIMAL(19, 4),
    max_taxable_amount DECIMAL(19, 4),
    exemption_categories JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    description TEXT,
    regulatory_reference VARCHAR(500),
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0 NOT NULL,

    CONSTRAINT chk_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100),
    CONSTRAINT chk_tax_amount_range CHECK (min_taxable_amount IS NULL OR max_taxable_amount IS NULL OR min_taxable_amount <= max_taxable_amount),
    CONSTRAINT chk_tax_type CHECK (tax_type IN ('SALES_TAX', 'VAT', 'GST', 'WITHHOLDING', 'EXCISE', 'CUSTOMS', 'SERVICE_TAX', 'OTHER'))
);

-- ============================================================================
-- FEE TIER CONFIGURATION (for tiered pricing)
-- ============================================================================

CREATE TABLE IF NOT EXISTS fee_tier (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_configuration_id UUID NOT NULL,
    tier_level INTEGER NOT NULL,
    tier_name VARCHAR(100),
    min_amount DECIMAL(19, 4) NOT NULL,
    max_amount DECIMAL(19, 4),
    percentage_rate DECIMAL(7, 4),
    fixed_amount DECIMAL(19, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fee_tier_config FOREIGN KEY (fee_configuration_id) REFERENCES fee_configuration(id) ON DELETE CASCADE,
    CONSTRAINT chk_tier_min_max CHECK (max_amount IS NULL OR min_amount <= max_amount),
    CONSTRAINT unique_fee_tier UNIQUE (fee_configuration_id, tier_level)
);

-- ============================================================================
-- INDEXES FOR PERFORMANCE
-- ============================================================================

-- Fee configuration indexes
CREATE INDEX idx_fee_config_active ON fee_configuration(is_active, effective_from, effective_to) WHERE is_active = TRUE;
CREATE INDEX idx_fee_config_type_category ON fee_configuration(fee_type, fee_category) WHERE is_active = TRUE;
CREATE INDEX idx_fee_config_currency ON fee_configuration(currency) WHERE is_active = TRUE;
CREATE INDEX idx_fee_config_effective ON fee_configuration(effective_from, effective_to);
CREATE INDEX idx_fee_config_priority ON fee_configuration(priority DESC) WHERE is_active = TRUE;

-- Tax configuration indexes
CREATE INDEX idx_tax_config_active ON tax_configuration(is_active, effective_from, effective_to) WHERE is_active = TRUE;
CREATE INDEX idx_tax_config_jurisdiction ON tax_configuration(tax_jurisdiction) WHERE is_active = TRUE;
CREATE INDEX idx_tax_config_type ON tax_configuration(tax_type) WHERE is_active = TRUE;
CREATE INDEX idx_tax_config_effective ON tax_configuration(effective_from, effective_to);

-- Fee tier indexes
CREATE INDEX idx_fee_tier_config ON fee_tier(fee_configuration_id, tier_level);

-- GIN indexes for JSONB queries
CREATE INDEX idx_fee_config_transaction_types_gin ON fee_configuration USING GIN (applies_to_transaction_types);
CREATE INDEX idx_fee_config_merchant_tiers_gin ON fee_configuration USING GIN (applies_to_merchant_tiers);
CREATE INDEX idx_tax_config_services_gin ON tax_configuration USING GIN (applies_to_services);
CREATE INDEX idx_tax_config_countries_gin ON tax_configuration USING GIN (applies_to_countries);

-- ============================================================================
-- INSERT DEFAULT FEE CONFIGURATIONS
-- ============================================================================

-- Platform fee (2.9% + $0.30) - Standard Stripe-like pricing
INSERT INTO fee_configuration (
    fee_code, fee_name, fee_type, fee_category, calculation_method,
    percentage_rate, fixed_amount, minimum_fee, maximum_fee, currency,
    effective_from, created_by, description
) VALUES (
    'PLATFORM_STANDARD',
    'Standard Platform Fee',
    'PLATFORM',
    'TRANSACTION',
    'PERCENTAGE_PLUS_FIXED',
    2.9000, -- 2.9%
    0.3000, -- $0.30
    0.3000, -- Minimum $0.30
    NULL,   -- No maximum
    'USD',
    CURRENT_DATE,
    'SYSTEM',
    'Standard platform transaction fee'
);

-- Processor fee (passed through from payment processor)
INSERT INTO fee_configuration (
    fee_code, fee_name, fee_type, fee_category, calculation_method,
    percentage_rate, fixed_amount, currency,
    effective_from, created_by, description
) VALUES (
    'PROCESSOR_STANDARD',
    'Payment Processor Fee',
    'PROCESSOR',
    'TRANSACTION',
    'PERCENTAGE',
    0.5000, -- 0.5%
    NULL,
    'USD',
    CURRENT_DATE,
    'SYSTEM',
    'Payment processor pass-through fee'
);

-- International transaction fee
INSERT INTO fee_configuration (
    fee_code, fee_name, fee_type, fee_category, calculation_method,
    percentage_rate, currency,
    effective_from, created_by, description
) VALUES (
    'INTERNATIONAL_FEE',
    'International Transaction Fee',
    'NETWORK',
    'CURRENCY_CONVERSION',
    'PERCENTAGE',
    3.0000, -- 3%
    'USD',
    CURRENT_DATE,
    'SYSTEM',
    'Fee for international/currency conversion transactions'
);

-- ============================================================================
-- INSERT DEFAULT TAX CONFIGURATIONS
-- ============================================================================

-- US Sales Tax (example - would need to be configured per state)
INSERT INTO tax_configuration (
    tax_code, tax_name, tax_type, tax_jurisdiction, tax_rate,
    effective_from, created_by, description
) VALUES (
    'US_SALES_TAX_CA',
    'California Sales Tax',
    'SALES_TAX',
    'US-CA',
    7.2500, -- 7.25%
    CURRENT_DATE,
    'SYSTEM',
    'California state sales tax'
);

-- EU VAT (example)
INSERT INTO tax_configuration (
    tax_code, tax_name, tax_type, tax_jurisdiction, tax_rate,
    tax_inclusive, effective_from, created_by, description
) VALUES (
    'EU_VAT_STANDARD',
    'EU Standard VAT',
    'VAT',
    'EU',
    20.0000, -- 20%
    TRUE, -- VAT is typically inclusive
    CURRENT_DATE,
    'SYSTEM',
    'Standard EU VAT rate'
);

-- ============================================================================
-- TRIGGERS FOR AUTO-UPDATE
-- ============================================================================

CREATE OR REPLACE FUNCTION update_fee_tax_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fee_configuration_updated_at
    BEFORE UPDATE ON fee_configuration
    FOR EACH ROW
    EXECUTE FUNCTION update_fee_tax_updated_at();

CREATE TRIGGER tax_configuration_updated_at
    BEFORE UPDATE ON tax_configuration
    FOR EACH ROW
    EXECUTE FUNCTION update_fee_tax_updated_at();

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE fee_configuration IS 'Dynamic fee configuration for flexible pricing without code deployment';
COMMENT ON TABLE tax_configuration IS 'Tax configuration for multi-jurisdiction compliance';
COMMENT ON TABLE fee_tier IS 'Tiered pricing structure for volume-based discounts';

COMMENT ON COLUMN fee_configuration.calculation_method IS 'PERCENTAGE, FIXED, PERCENTAGE_PLUS_FIXED, TIERED, CUSTOM';
COMMENT ON COLUMN fee_configuration.priority IS 'Higher priority fees are applied first (for conflict resolution)';
COMMENT ON COLUMN tax_configuration.tax_inclusive IS 'TRUE if tax is included in the price, FALSE if added on top';
COMMENT ON COLUMN tax_configuration.compound_tax IS 'TRUE if this tax is calculated on top of other taxes';

-- Migration completed successfully
-- Fee and tax configuration system is now operational
