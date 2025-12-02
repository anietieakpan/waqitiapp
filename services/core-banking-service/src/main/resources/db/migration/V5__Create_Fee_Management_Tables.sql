-- V5: Create Fee Management Tables
-- Creates comprehensive fee management system with schedules and tiers

-- Create fee_schedules table
CREATE TABLE fee_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    fee_type VARCHAR(30) NOT NULL,
    calculation_method VARCHAR(30) NOT NULL,
    base_amount DECIMAL(19,4),
    percentage_rate DECIMAL(8,6),
    minimum_fee DECIMAL(19,4),
    maximum_fee DECIMAL(19,4),
    currency VARCHAR(3) NOT NULL,
    effective_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    applies_to_account_types TEXT,
    applies_to_transaction_types TEXT,
    conditions TEXT,
    free_transactions_per_period INTEGER,
    period_type VARCHAR(20),
    waiver_conditions TEXT,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create fee_tiers table for tiered pricing
CREATE TABLE fee_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_schedule_id UUID NOT NULL REFERENCES fee_schedules(id) ON DELETE CASCADE,
    tier_order INTEGER NOT NULL,
    tier_name VARCHAR(100),
    range_from DECIMAL(19,4),
    range_to DECIMAL(19,4),
    fee_amount DECIMAL(19,4),
    fee_percentage DECIMAL(8,6),
    free_quantity INTEGER,
    description VARCHAR(500),
    CONSTRAINT uk_fee_schedule_tier_order UNIQUE(fee_schedule_id, tier_order)
);

-- Create indexes for performance
CREATE INDEX idx_fee_schedule_name ON fee_schedules(name);
CREATE INDEX idx_fee_schedule_status ON fee_schedules(status);
CREATE INDEX idx_fee_schedule_type ON fee_schedules(fee_type);
CREATE INDEX idx_fee_schedule_effective_date ON fee_schedules(effective_date);
CREATE INDEX idx_fee_schedule_currency ON fee_schedules(currency);
CREATE INDEX idx_fee_schedule_created_by ON fee_schedules(created_by);

CREATE INDEX idx_fee_tier_schedule ON fee_tiers(fee_schedule_id);
CREATE INDEX idx_fee_tier_order ON fee_tiers(fee_schedule_id, tier_order);

-- Create fee calculation audit table
CREATE TABLE fee_calculations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    transaction_id UUID,
    fee_schedule_id UUID NOT NULL REFERENCES fee_schedules(id),
    calculation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    original_amount DECIMAL(19,4),
    calculated_fee DECIMAL(19,4) NOT NULL,
    applied_fee DECIMAL(19,4) NOT NULL,
    waiver_reason VARCHAR(200),
    fee_transaction_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'CALCULATED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fee_calc_account ON fee_calculations(account_id);
CREATE INDEX idx_fee_calc_transaction ON fee_calculations(transaction_id);
CREATE INDEX idx_fee_calc_schedule ON fee_calculations(fee_schedule_id);
CREATE INDEX idx_fee_calc_date ON fee_calculations(calculation_date);

-- Insert default fee schedules

-- Standard Transaction Fee Schedule
INSERT INTO fee_schedules (
    name, description, fee_type, calculation_method, 
    base_amount, currency, effective_date, status,
    applies_to_account_types, applies_to_transaction_types,
    free_transactions_per_period, period_type
) VALUES (
    'Standard P2P Transaction Fee',
    'Standard fee for peer-to-peer transfers',
    'TRANSACTION_FEE',
    'FLAT_FEE',
    1.00,
    'USD',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    '["USER_WALLET", "USER_SAVINGS"]',
    '["P2P_TRANSFER"]',
    5,
    'MONTHLY'
);

-- Premium Account Fee Schedule (no fees for high balance)
INSERT INTO fee_schedules (
    name, description, fee_type, calculation_method,
    base_amount, currency, effective_date, status,
    applies_to_account_types,
    waiver_conditions
) VALUES (
    'Premium Account - No Transaction Fees',
    'Premium accounts with balance-based fee waiver',
    'TRANSACTION_FEE',
    'FLAT_FEE',
    0.00,
    'USD',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    '["USER_SAVINGS"]',
    '{"minimum_balance": 10000.00, "waiver_type": "high_balance"}'
);

-- Wire Transfer Fee Schedule
INSERT INTO fee_schedules (
    name, description, fee_type, calculation_method,
    base_amount, minimum_fee, maximum_fee, currency, 
    effective_date, status,
    applies_to_transaction_types
) VALUES (
    'Wire Transfer Fee',
    'Fee for wire transfers',
    'WIRE_TRANSFER_FEE',
    'FLAT_FEE',
    25.00,
    15.00,
    50.00,
    'USD',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    '["WIRE_TRANSFER"]'
);

-- International Transaction Fee
INSERT INTO fee_schedules (
    name, description, fee_type, calculation_method,
    percentage_rate, minimum_fee, maximum_fee, currency,
    effective_date, status,
    applies_to_transaction_types
) VALUES (
    'International Transaction Fee',
    'Percentage fee for international transactions',
    'INTERNATIONAL_FEE',
    'PERCENTAGE',
    3.00,
    2.00,
    25.00,
    'USD',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    '["INTERNATIONAL_TRANSFER", "CARD_PAYMENT"]'
);

-- Monthly Maintenance Fee Schedule
INSERT INTO fee_schedules (
    name, description, fee_type, calculation_method,
    base_amount, currency, effective_date, status,
    applies_to_account_types, period_type,
    waiver_conditions
) VALUES (
    'Monthly Maintenance Fee',
    'Monthly account maintenance fee',
    'MAINTENANCE_FEE',
    'FLAT_FEE',
    5.00,
    'USD',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    '["USER_WALLET"]',
    'MONTHLY',
    '{"minimum_balance": 1000.00, "waiver_type": "minimum_balance"}'
);

-- Overdraft Fee Schedule with tiers
INSERT INTO fee_schedules (
    name, description, fee_type, calculation_method,
    currency, effective_date, status,
    applies_to_account_types
) VALUES (
    'Overdraft Fee - Tiered',
    'Tiered overdraft fees based on overdraft amount',
    'OVERDRAFT_FEE',
    'TIERED',
    'USD',
    CURRENT_TIMESTAMP,
    'ACTIVE',
    '["USER_WALLET", "USER_SAVINGS"]'
);

-- Get the ID of the overdraft fee schedule for tier insertion
-- Insert fee tiers for overdraft fee
INSERT INTO fee_tiers (fee_schedule_id, tier_order, tier_name, range_from, range_to, fee_amount, description)
SELECT id, 1, 'Small Overdraft', 0.01, 100.00, 25.00, 'Overdraft up to $100'
FROM fee_schedules WHERE name = 'Overdraft Fee - Tiered';

INSERT INTO fee_tiers (fee_schedule_id, tier_order, tier_name, range_from, range_to, fee_amount, description)
SELECT id, 2, 'Medium Overdraft', 100.01, 500.00, 35.00, 'Overdraft $100.01 to $500'
FROM fee_schedules WHERE name = 'Overdraft Fee - Tiered';

INSERT INTO fee_tiers (fee_schedule_id, tier_order, tier_name, range_from, range_to, fee_amount, description)
SELECT id, 3, 'Large Overdraft', 500.01, NULL, 50.00, 'Overdraft over $500'
FROM fee_schedules WHERE name = 'Overdraft Fee - Tiered';

-- Add comments for documentation
COMMENT ON TABLE fee_schedules IS 'Fee schedules defining how fees are calculated for different account and transaction types';
COMMENT ON TABLE fee_tiers IS 'Tiered pricing structure for fee schedules using tiered calculation method';
COMMENT ON TABLE fee_calculations IS 'Audit trail of all fee calculations and applications';

COMMENT ON COLUMN fee_schedules.fee_type IS 'Type of fee: TRANSACTION_FEE, MAINTENANCE_FEE, OVERDRAFT_FEE, etc.';
COMMENT ON COLUMN fee_schedules.calculation_method IS 'How fee is calculated: FLAT_FEE, PERCENTAGE, TIERED, etc.';
COMMENT ON COLUMN fee_schedules.applies_to_account_types IS 'JSON array of account types this schedule applies to';
COMMENT ON COLUMN fee_schedules.applies_to_transaction_types IS 'JSON array of transaction types this schedule applies to';
COMMENT ON COLUMN fee_schedules.waiver_conditions IS 'JSON object defining conditions for fee waivers';
COMMENT ON COLUMN fee_schedules.free_transactions_per_period IS 'Number of free transactions per period before fees apply';