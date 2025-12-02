-- ATM Service Schema V5: EMV Transactions and Cardless Infrastructure
-- Created: 2025-11-15

-- EMV Chip Transactions Table
CREATE TABLE IF NOT EXISTS emv_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    card_id UUID NOT NULL,
    account_id UUID NOT NULL,
    atm_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- EMV data (encrypted)
    emv_data TEXT,
    application_id VARCHAR(32),
    terminal_verification_results VARCHAR(10),
    transaction_certificate TEXT,
    issuer_authentication_data TEXT,
    cryptogram_type VARCHAR(20) CHECK (cryptogram_type IN ('ARQC', 'TC', 'AAC')),

    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ATM Access Tokens Table (for cardless withdrawals)
CREATE TABLE IF NOT EXISTS atm_access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    atm_location_id UUID,

    access_code VARCHAR(255) NOT NULL UNIQUE,  -- Encrypted
    qr_code_data TEXT,

    amount DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    atm_fee DECIMAL(19, 4) DEFAULT 0.0000 CHECK (atm_fee >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    token_status VARCHAR(20) NOT NULL CHECK (token_status IN ('ACTIVE', 'USED', 'EXPIRED', 'CANCELLED', 'FAILED')),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    failed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_token_expires CHECK (expires_at > created_at)
);

-- ATM Locations Table
CREATE TABLE IF NOT EXISTS atm_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(50) NOT NULL,
    zip_code VARCHAR(10),
    country_code VARCHAR(3) NOT NULL,

    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),

    network VARCHAR(50) NOT NULL,  -- VISA, MASTERCARD, LOCAL
    bank_name VARCHAR(255),

    base_fee DECIMAL(19, 4) DEFAULT 0.0000 CHECK (base_fee >= 0),
    emergency_access_fee DECIMAL(19, 4) DEFAULT 0.0000,

    is_operational BOOLEAN DEFAULT TRUE,
    supports_cash_withdrawal BOOLEAN DEFAULT TRUE,
    supports_cash_deposit BOOLEAN DEFAULT FALSE,
    supports_cardless_access BOOLEAN DEFAULT FALSE,
    supports_nfc_access BOOLEAN DEFAULT FALSE,
    supports_biometric_auth BOOLEAN DEFAULT FALSE,
    supports_emergency_access BOOLEAN DEFAULT FALSE,
    requires_emergency_verification BOOLEAN DEFAULT TRUE,
    has_24hour_access BOOLEAN DEFAULT FALSE,
    has_disability_access BOOLEAN DEFAULT FALSE,

    operating_hours TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- User ATM Preferences Table
CREATE TABLE IF NOT EXISTS user_atm_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,

    prefer_fee_free_atms BOOLEAN DEFAULT TRUE,
    max_acceptable_fee DECIMAL(19, 4) DEFAULT 2.50,
    preferred_networks TEXT[],
    favorite_atms UUID[],

    enable_biometric_auth BOOLEAN DEFAULT FALSE,
    enable_location_based_suggestions BOOLEAN DEFAULT TRUE,

    notification_preferences JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ATM Network Partners Table
CREATE TABLE IF NOT EXISTS atm_network_partners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    network_name VARCHAR(100) NOT NULL UNIQUE,
    network_type VARCHAR(20) CHECK (network_type IN ('VISA', 'MASTERCARD', 'ALLPOINT', 'MONEYPASS', 'LOCAL', 'OTHER')),

    is_fee_free BOOLEAN DEFAULT FALSE,
    standard_fee DECIMAL(19, 4) DEFAULT 0.0000,

    api_endpoint TEXT,
    api_key_ref VARCHAR(255),  -- Vault reference

    is_enabled BOOLEAN DEFAULT TRUE,
    timeout_seconds INTEGER DEFAULT 30,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Indexes
CREATE INDEX idx_emv_transaction ON emv_transactions(transaction_id);
CREATE INDEX idx_emv_account ON emv_transactions(account_id, transaction_date DESC);

CREATE INDEX idx_access_tokens_user ON atm_access_tokens(user_id, token_status);
CREATE INDEX idx_access_tokens_active ON atm_access_tokens(user_id, token_status, expires_at) WHERE token_status = 'ACTIVE';
CREATE INDEX idx_access_tokens_code ON atm_access_tokens(access_code);

CREATE INDEX idx_atm_locations_city ON atm_locations(city, state) WHERE is_operational = TRUE;
CREATE INDEX idx_atm_locations_network ON atm_locations(network) WHERE is_operational = TRUE;
CREATE INDEX idx_atm_locations_coords ON atm_locations(latitude, longitude) WHERE is_operational = TRUE;

CREATE INDEX idx_user_prefs_user ON user_atm_preferences(user_id);

CREATE INDEX idx_network_partners_name ON atm_network_partners(network_name) WHERE is_enabled = TRUE;

-- Update triggers
CREATE TRIGGER trg_emv_updated_at BEFORE UPDATE ON emv_transactions FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();
CREATE TRIGGER trg_tokens_updated_at BEFORE UPDATE ON atm_access_tokens FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();
CREATE TRIGGER trg_locations_updated_at BEFORE UPDATE ON atm_locations FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();
CREATE TRIGGER trg_prefs_updated_at BEFORE UPDATE ON user_atm_preferences FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();
CREATE TRIGGER trg_partners_updated_at BEFORE UPDATE ON atm_network_partners FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();
