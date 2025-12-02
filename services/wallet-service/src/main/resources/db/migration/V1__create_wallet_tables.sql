-- Wallet Service Database Schema
-- Creates tables for digital wallet management, balances, and transactions

-- Digital wallets (enhanced)
CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    wallet_type VARCHAR(50) NOT NULL CHECK (wallet_type IN ('PERSONAL', 'BUSINESS', 'JOINT', 'SAVINGS', 'ESCROW')),
    account_type VARCHAR(50) NOT NULL,
    wallet_name VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED')) DEFAULT 'ACTIVE',
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    frozen_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    daily_limit DECIMAL(19,4),
    monthly_limit DECIMAL(19,4),
    yearly_limit DECIMAL(19,4),
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    UNIQUE(user_id, currency)
);

-- Wallet holds (temporary freezing of funds)
CREATE TABLE wallet_holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hold_id VARCHAR(50) UNIQUE NOT NULL,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    hold_type VARCHAR(20) NOT NULL CHECK (hold_type IN ('PAYMENT_AUTHORIZATION', 'COMPLIANCE_REVIEW', 'DISPUTE_RESOLUTION', 'MAINTENANCE')),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason TEXT NOT NULL,
    reference_id VARCHAR(100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'CANCELLED')) DEFAULT 'ACTIVE',
    expires_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    released_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB
);

-- Multi-currency wallet balances
CREATE TABLE wallet_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    currency VARCHAR(3) NOT NULL,
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    available_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    pending_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    frozen_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    last_transaction_id UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    UNIQUE(wallet_id, currency)
);

-- Wallet transactions (enhanced)
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    external_id VARCHAR(100),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    source_wallet_id UUID REFERENCES wallets(id),
    target_wallet_id UUID REFERENCES wallets(id),
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('CREDIT', 'DEBIT', 'HOLD', 'RELEASE', 'TRANSFER_IN', 'TRANSFER_OUT')),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance_before DECIMAL(19,4) NOT NULL,
    balance_after DECIMAL(19,4) NOT NULL,
    description TEXT,
    reference_id VARCHAR(100),
    external_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED')) DEFAULT 'PENDING',
    processing_fee DECIMAL(19,4) DEFAULT 0,
    exchange_rate DECIMAL(19,8),
    metadata JSONB,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Wallet audit trail
CREATE TABLE wallet_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    action VARCHAR(50) NOT NULL,
    performed_by UUID NOT NULL,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent TEXT,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Enhanced indexes for wallets
CREATE INDEX idx_wallets_user_id ON wallets(user_id);
CREATE INDEX idx_wallets_wallet_id ON wallets(wallet_id);
CREATE INDEX idx_wallets_external_id ON wallets(external_id);
CREATE INDEX idx_wallets_wallet_type ON wallets(wallet_type);
CREATE INDEX idx_wallets_status ON wallets(status);
CREATE INDEX idx_wallets_currency ON wallets(currency);

-- Enhanced indexes for transactions
CREATE INDEX idx_transactions_transaction_id ON transactions(transaction_id);
CREATE INDEX idx_transactions_external_id ON transactions(external_id);
CREATE INDEX idx_transactions_wallet_id ON transactions(wallet_id);
CREATE INDEX idx_transactions_source_wallet_id ON transactions(source_wallet_id);
CREATE INDEX idx_transactions_target_wallet_id ON transactions(target_wallet_id);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_reference ON transactions(reference_id);

-- Indexes for wallet holds
CREATE INDEX idx_wallet_holds_wallet_id ON wallet_holds(wallet_id);
CREATE INDEX idx_wallet_holds_status ON wallet_holds(status);
CREATE INDEX idx_wallet_holds_type ON wallet_holds(hold_type);
CREATE INDEX idx_wallet_holds_expires_at ON wallet_holds(expires_at);

-- Indexes for wallet balances
CREATE INDEX idx_wallet_balances_wallet_id ON wallet_balances(wallet_id);
CREATE INDEX idx_wallet_balances_currency ON wallet_balances(currency);

-- Indexes for wallet audit
CREATE INDEX idx_wallet_audit_wallet_id ON wallet_audit(wallet_id);
CREATE INDEX idx_wallet_audit_performed_by ON wallet_audit(performed_by);
CREATE INDEX idx_wallet_audit_created_at ON wallet_audit(created_at);

-- Function for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_wallets_updated_at 
    BEFORE UPDATE ON wallets 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transactions_updated_at 
    BEFORE UPDATE ON transactions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Function to update wallet balance after transaction
CREATE OR REPLACE FUNCTION update_wallet_balance()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'COMPLETED' THEN
        UPDATE wallets 
        SET balance = NEW.balance_after,
            available_balance = CASE 
                WHEN NEW.transaction_type IN ('CREDIT', 'TRANSFER_IN', 'RELEASE') THEN available_balance + NEW.amount
                WHEN NEW.transaction_type IN ('DEBIT', 'TRANSFER_OUT') THEN available_balance - NEW.amount
                ELSE available_balance
            END,
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE id = NEW.wallet_id;

        -- Update multi-currency balance if exists
        INSERT INTO wallet_balances (wallet_id, currency, balance, available_balance, last_transaction_id)
        VALUES (NEW.wallet_id, NEW.currency, NEW.balance_after, NEW.balance_after, NEW.id)
        ON CONFLICT (wallet_id, currency) DO UPDATE SET
            balance = NEW.balance_after,
            available_balance = wallet_balances.available_balance + 
                CASE 
                    WHEN NEW.transaction_type IN ('CREDIT', 'TRANSFER_IN', 'RELEASE') THEN NEW.amount
                    WHEN NEW.transaction_type IN ('DEBIT', 'TRANSFER_OUT') THEN -NEW.amount
                    ELSE 0
                END,
            last_transaction_id = NEW.id,
            updated_at = CURRENT_TIMESTAMP,
            version = wallet_balances.version + 1;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for wallet balance updates
CREATE TRIGGER trigger_update_wallet_balance
    AFTER INSERT OR UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_wallet_balance();