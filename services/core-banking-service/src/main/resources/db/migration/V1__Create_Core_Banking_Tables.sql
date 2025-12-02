-- Core Banking Service Database Schema
-- Creates tables for accounts, transactions, balances, and audit

-- Accounts table
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS', 'BUSINESS', 'ESCROW')),
    account_status VARCHAR(20) NOT NULL CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED')) DEFAULT 'ACTIVE',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    frozen_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    overdraft_limit DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    minimum_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    daily_withdrawal_limit DECIMAL(19,4),
    monthly_withdrawal_limit DECIMAL(19,4),
    compliance_level VARCHAR(20) NOT NULL CHECK (compliance_level IN ('BASIC', 'STANDARD', 'ENHANCED', 'PREMIUM')) DEFAULT 'BASIC',
    kyc_status VARCHAR(20) NOT NULL CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')) DEFAULT 'PENDING',
    risk_score INTEGER NOT NULL DEFAULT 0 CHECK (risk_score >= 0 AND risk_score <= 100),
    is_joint_account BOOLEAN NOT NULL DEFAULT FALSE,
    notification_preferences JSONB,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

-- Transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    original_transaction_id VARCHAR(50),
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('TRANSFER', 'PAYMENT', 'DEPOSIT', 'WITHDRAWAL', 'REVERSAL')),
    transaction_status VARCHAR(20) NOT NULL CHECK (transaction_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED')) DEFAULT 'PENDING',
    from_account_id UUID REFERENCES accounts(id),
    to_account_id UUID REFERENCES accounts(id),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    reference_number VARCHAR(100),
    external_reference VARCHAR(100),
    processing_fee DECIMAL(19,4) DEFAULT 0.0000,
    exchange_rate DECIMAL(19,8),
    settlement_date DATE,
    value_date DATE,
    channel VARCHAR(50),
    device_info JSONB,
    location_info JSONB,
    risk_score INTEGER CHECK (risk_score >= 0 AND risk_score <= 100),
    compliance_flags JSONB,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_account_different CHECK (from_account_id != to_account_id OR from_account_id IS NULL OR to_account_id IS NULL)
);

-- Balance snapshots table for historical tracking
CREATE TABLE balance_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    balance DECIMAL(19,4) NOT NULL,
    available_balance DECIMAL(19,4) NOT NULL,
    frozen_amount DECIMAL(19,4) NOT NULL,
    snapshot_date DATE NOT NULL,
    snapshot_type VARCHAR(20) NOT NULL CHECK (snapshot_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEAR_END')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, snapshot_date, snapshot_type)
);

-- Transaction audit table
CREATE TABLE transaction_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    event_type VARCHAR(50) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    user_id UUID,
    reason TEXT,
    additional_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Account holds table for freezing funds
CREATE TABLE account_holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    hold_type VARCHAR(20) NOT NULL CHECK (hold_type IN ('PAYMENT', 'COMPLIANCE', 'DISPUTE', 'MAINTENANCE')),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason TEXT NOT NULL,
    reference_id VARCHAR(100),
    expiry_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED')) DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    released_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_accounts_status ON accounts(account_status);
CREATE INDEX idx_accounts_type ON accounts(account_type);

CREATE INDEX idx_transactions_transaction_id ON transactions(transaction_id);
CREATE INDEX idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX idx_transactions_to_account ON transactions(to_account_id);
CREATE INDEX idx_transactions_status ON transactions(transaction_status);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_processed_at ON transactions(processed_at);
CREATE INDEX idx_transactions_reference ON transactions(reference_number);

CREATE INDEX idx_balance_snapshots_account_date ON balance_snapshots(account_id, snapshot_date);
CREATE INDEX idx_transaction_audit_transaction ON transaction_audit(transaction_id);
CREATE INDEX idx_transaction_audit_created_at ON transaction_audit(created_at);

CREATE INDEX idx_account_holds_account ON account_holds(account_id);
CREATE INDEX idx_account_holds_status ON account_holds(status);
CREATE INDEX idx_account_holds_expiry ON account_holds(expiry_date);

-- Functions for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_accounts_updated_at 
    BEFORE UPDATE ON accounts 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at 
    BEFORE UPDATE ON transactions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_account_holds_updated_at 
    BEFORE UPDATE ON account_holds 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();