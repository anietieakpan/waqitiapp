-- ATM Service Schema V3: ATM Withdrawals, Deposits, and Cardless Withdrawals
-- Created: 2025-11-15
-- Description: Transaction tables for ATM operations with proper financial precision

-- ATM Withdrawals Table
CREATE TABLE IF NOT EXISTS atm_withdrawals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atm_id UUID NOT NULL,
    card_id UUID,
    account_id UUID NOT NULL,

    -- Financial fields with DECIMAL(19,4) precision
    amount DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    fee_amount DECIMAL(19, 4) DEFAULT 0.0000 CHECK (fee_amount >= 0),
    total_amount DECIMAL(19, 4) NOT NULL CHECK (total_amount >= amount),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Authorization and status
    authorization_code VARCHAR(50),
    withdrawal_status VARCHAR(20) NOT NULL CHECK (withdrawal_status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED')),
    failure_reason VARCHAR(255),

    -- Timestamps
    withdrawal_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispensed_at TIMESTAMP,
    reversed_at TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Foreign keys
    CONSTRAINT fk_withdrawal_account FOREIGN KEY (account_id) REFERENCES atm_cards(account_id) ON DELETE RESTRICT
);

-- ATM Deposits Table
CREATE TABLE IF NOT EXISTS atm_deposits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atm_id UUID NOT NULL,
    card_id UUID,
    account_id UUID NOT NULL,

    -- Deposit type
    deposit_type VARCHAR(20) NOT NULL CHECK (deposit_type IN ('CASH', 'CHECK', 'MIXED')),

    -- Financial fields with DECIMAL(19,4) precision
    cash_amount DECIMAL(19, 4) DEFAULT 0.0000 CHECK (cash_amount >= 0),
    check_amount DECIMAL(19, 4) DEFAULT 0.0000 CHECK (check_amount >= 0),
    total_amount DECIMAL(19, 4) NOT NULL CHECK (total_amount >= 0),
    fee_amount DECIMAL(19, 4) DEFAULT 0.0000 CHECK (fee_amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Check information
    number_of_checks INTEGER DEFAULT 0 CHECK (number_of_checks >= 0),

    -- Status and processing
    deposit_status VARCHAR(20) NOT NULL CHECK (deposit_status IN ('PROCESSING', 'COMPLETED', 'REJECTED', 'ON_HOLD', 'PARTIALLY_PROCESSED')),
    rejection_reason VARCHAR(255),
    hold_reason VARCHAR(255),
    hold_until_date DATE,
    receipt_number VARCHAR(50),

    -- Timestamps
    deposit_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cash_processed_at TIMESTAMP,
    check_processed_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Business rules
    CONSTRAINT chk_deposit_amounts CHECK (total_amount = cash_amount + check_amount),
    CONSTRAINT chk_check_count CHECK (
        (deposit_type = 'CHECK' AND number_of_checks > 0) OR
        (deposit_type = 'CASH' AND number_of_checks = 0) OR
        (deposit_type = 'MIXED' AND number_of_checks > 0)
    )
);

-- Cardless Withdrawals Table (QR code, NFC, biometric)
CREATE TABLE IF NOT EXISTS cardless_withdrawals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,

    -- Financial fields with DECIMAL(19,4) precision
    amount DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    fee_amount DECIMAL(19, 4) DEFAULT 0.0000 CHECK (fee_amount >= 0),
    dispensed_amount DECIMAL(19, 4),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Cardless authentication
    withdrawal_code VARCHAR(32) UNIQUE NOT NULL,
    security_code VARCHAR(255) NOT NULL,  -- Encrypted
    qr_code_data TEXT,
    access_token_id UUID,

    -- Recipient information
    recipient_mobile VARCHAR(20),
    recipient_name VARCHAR(255),

    -- Status and tracking
    withdrawal_status VARCHAR(20) NOT NULL CHECK (withdrawal_status IN ('INITIATED', 'VERIFIED', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'FAILED')),
    attempt_count INTEGER DEFAULT 0 CHECK (attempt_count >= 0 AND attempt_count <= 3),
    failure_reason VARCHAR(255),

    -- ATM and transaction
    atm_id UUID,
    transaction_id VARCHAR(100),

    -- Timestamps
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    qr_generated_at TIMESTAMP,
    verified_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_expiry_future CHECK (expires_at > initiated_at),
    CONSTRAINT chk_max_attempts CHECK (attempt_count <= 3)
);

-- Indexes for performance
CREATE INDEX idx_withdrawals_account ON atm_withdrawals(account_id, withdrawal_date DESC);
CREATE INDEX idx_withdrawals_status ON atm_withdrawals(withdrawal_status, withdrawal_date DESC);
CREATE INDEX idx_withdrawals_date ON atm_withdrawals(withdrawal_date DESC);
CREATE INDEX idx_withdrawals_atm ON atm_withdrawals(atm_id, withdrawal_date DESC);

CREATE INDEX idx_deposits_account ON atm_deposits(account_id, deposit_date DESC);
CREATE INDEX idx_deposits_status ON atm_deposits(deposit_status, deposit_date DESC);
CREATE INDEX idx_deposits_date ON atm_deposits(deposit_date DESC);
CREATE INDEX idx_deposits_hold ON atm_deposits(hold_until_date) WHERE deposit_status = 'ON_HOLD';

CREATE INDEX idx_cardless_account ON cardless_withdrawals(account_id, initiated_at DESC);
CREATE INDEX idx_cardless_code ON cardless_withdrawals(withdrawal_code);
CREATE INDEX idx_cardless_status ON cardless_withdrawals(withdrawal_status);
CREATE INDEX idx_cardless_expires ON cardless_withdrawals(expires_at) WHERE withdrawal_status IN ('INITIATED', 'VERIFIED');
CREATE INDEX idx_cardless_active ON cardless_withdrawals(account_id, withdrawal_status, expires_at)
    WHERE withdrawal_status IN ('INITIATED', 'VERIFIED');

-- Update triggers
CREATE TRIGGER trg_withdrawals_updated_at BEFORE UPDATE ON atm_withdrawals
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

CREATE TRIGGER trg_deposits_updated_at BEFORE UPDATE ON atm_deposits
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

CREATE TRIGGER trg_cardless_updated_at BEFORE UPDATE ON cardless_withdrawals
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

-- Comments
COMMENT ON TABLE atm_withdrawals IS 'ATM withdrawal transactions with proper financial precision';
COMMENT ON TABLE atm_deposits IS 'ATM cash and check deposits with hold management';
COMMENT ON TABLE cardless_withdrawals IS 'Cardless ATM withdrawals using QR codes, NFC, or biometric authentication';
COMMENT ON COLUMN cardless_withdrawals.withdrawal_code IS 'Unique code for cardless withdrawal, must be kept secure';
COMMENT ON COLUMN cardless_withdrawals.expires_at IS 'Withdrawal code expires after configured timeout (typically 5-10 minutes)';
