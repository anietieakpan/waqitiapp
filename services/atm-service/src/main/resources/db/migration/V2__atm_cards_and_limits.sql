-- ATM Service Schema V2: ATM Cards and Withdrawal/Deposit Limits
-- Created: 2025-11-15
-- Description: Core tables for ATM card management and transaction limits
-- PCI-DSS Compliance: Card data encryption required, PIN must be hashed

-- ATM Cards Table (PCI-DSS Scope)
CREATE TABLE IF NOT EXISTS atm_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    card_number VARCHAR(16) UNIQUE NOT NULL,  -- Encrypted at application layer
    card_holder_name VARCHAR(255) NOT NULL,
    card_type VARCHAR(20) NOT NULL CHECK (card_type IN ('DEBIT', 'CREDIT', 'PREPAID')),
    pin_hash VARCHAR(255) NOT NULL,  -- BCrypt hashed, NEVER plaintext
    card_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (card_status IN ('ACTIVE', 'BLOCKED', 'EXPIRED', 'SUSPENDED', 'CANCELLED')),

    -- Security tracking
    failed_pin_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_pin_attempts >= 0 AND failed_pin_attempts <= 10),
    temporary_pin BOOLEAN NOT NULL DEFAULT FALSE,

    -- Card lifecycle dates
    issued_date TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    pin_changed_at TIMESTAMP,
    pin_reset_at TIMESTAMP,

    -- Block/unblock tracking
    blocked_at TIMESTAMP,
    block_reason VARCHAR(255),
    unblocked_at TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,  -- Optimistic locking

    -- Constraints
    CONSTRAINT chk_expiry_future CHECK (expiry_date > created_at),
    CONSTRAINT chk_pin_attempts_range CHECK (failed_pin_attempts BETWEEN 0 AND 10)
);

-- Withdrawal Limits Table
CREATE TABLE IF NOT EXISTS withdrawal_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,

    -- Limits with proper financial precision (DECIMAL 19,4)
    daily_limit DECIMAL(19, 4) NOT NULL DEFAULT 1000.0000 CHECK (daily_limit >= 0),
    monthly_limit DECIMAL(19, 4) NOT NULL DEFAULT 10000.0000 CHECK (monthly_limit >= 0),
    single_transaction_limit DECIMAL(19, 4) NOT NULL DEFAULT 500.0000 CHECK (single_transaction_limit >= 0),

    -- Limit type (ATM, POS, ONLINE, etc.)
    limit_type VARCHAR(20) NOT NULL DEFAULT 'ATM',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,

    -- Business rules
    CONSTRAINT chk_single_lte_daily CHECK (single_transaction_limit <= daily_limit),
    CONSTRAINT chk_daily_lte_monthly CHECK (daily_limit <= monthly_limit),
    CONSTRAINT uk_account_limit_type UNIQUE (account_id, limit_type, currency_code)
);

-- Deposit Limits Table
CREATE TABLE IF NOT EXISTS deposit_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,

    -- Limits with proper financial precision (DECIMAL 19,4)
    daily_limit DECIMAL(19, 4) NOT NULL DEFAULT 10000.0000 CHECK (daily_limit >= 0),
    monthly_limit DECIMAL(19, 4) NOT NULL DEFAULT 100000.0000 CHECK (monthly_limit >= 0),
    single_transaction_limit DECIMAL(19, 4) NOT NULL DEFAULT 5000.0000 CHECK (single_transaction_limit >= 0),

    -- Limit type
    limit_type VARCHAR(20) NOT NULL DEFAULT 'ATM',
    deposit_type VARCHAR(20) NOT NULL DEFAULT 'CASH' CHECK (deposit_type IN ('CASH', 'CHECK', 'MIXED')),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,

    -- Business rules
    CONSTRAINT chk_deposit_single_lte_daily CHECK (single_transaction_limit <= daily_limit),
    CONSTRAINT chk_deposit_daily_lte_monthly CHECK (daily_limit <= monthly_limit),
    CONSTRAINT uk_deposit_account_limit UNIQUE (account_id, limit_type, deposit_type, currency_code)
);

-- Indexes for performance
CREATE INDEX idx_atm_cards_account ON atm_cards(account_id) WHERE card_status = 'ACTIVE';
CREATE INDEX idx_atm_cards_status ON atm_cards(card_status);
CREATE INDEX idx_atm_cards_expiry ON atm_cards(expiry_date) WHERE card_status = 'ACTIVE';
CREATE INDEX idx_atm_cards_blocked ON atm_cards(blocked_at) WHERE card_status = 'BLOCKED';

CREATE INDEX idx_withdrawal_limits_account ON withdrawal_limits(account_id);
CREATE INDEX idx_deposit_limits_account ON deposit_limits(account_id);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_cards_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_atm_cards_updated_at BEFORE UPDATE ON atm_cards
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

CREATE TRIGGER trg_withdrawal_limits_updated_at BEFORE UPDATE ON withdrawal_limits
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

CREATE TRIGGER trg_deposit_limits_updated_at BEFORE UPDATE ON deposit_limits
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

-- Comments for documentation
COMMENT ON TABLE atm_cards IS 'PCI-DSS scoped table: Stores ATM card information with encrypted card numbers and hashed PINs';
COMMENT ON COLUMN atm_cards.card_number IS 'SECURITY: Must be encrypted at application layer before storage';
COMMENT ON COLUMN atm_cards.pin_hash IS 'SECURITY: BCrypt hashed PIN, minimum 12 rounds, NEVER store plaintext';
COMMENT ON COLUMN atm_cards.failed_pin_attempts IS 'Auto-block card after 3 failed attempts';

COMMENT ON TABLE withdrawal_limits IS 'ATM withdrawal limits per account with multi-currency support';
COMMENT ON TABLE deposit_limits IS 'ATM deposit limits per account with support for cash and check deposits';
