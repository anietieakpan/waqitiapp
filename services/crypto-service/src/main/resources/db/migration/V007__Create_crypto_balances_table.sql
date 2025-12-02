-- Create crypto balances table
CREATE TABLE crypto_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES crypto_wallets(id) ON DELETE CASCADE,
    currency VARCHAR(20) NOT NULL,
    available_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    pending_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    staked_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    total_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    reserved_balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_crypto_balances_wallet_id UNIQUE (wallet_id),
    CONSTRAINT chk_crypto_balances_currency CHECK (currency IN ('BITCOIN', 'ETHEREUM', 'LITECOIN', 'USDC', 'USDT')),
    CONSTRAINT chk_crypto_balances_available CHECK (available_balance >= 0),
    CONSTRAINT chk_crypto_balances_pending CHECK (pending_balance >= 0),
    CONSTRAINT chk_crypto_balances_staked CHECK (staked_balance >= 0),
    CONSTRAINT chk_crypto_balances_reserved CHECK (reserved_balance >= 0)
);

-- Create indexes
CREATE INDEX idx_crypto_balances_wallet_id ON crypto_balances(wallet_id);
CREATE INDEX idx_crypto_balances_currency ON crypto_balances(currency);
CREATE INDEX idx_crypto_balances_last_updated ON crypto_balances(last_updated);

-- Add trigger to update total_balance automatically
CREATE OR REPLACE FUNCTION update_crypto_total_balance()
RETURNS TRIGGER AS $$
BEGIN
    NEW.total_balance = NEW.available_balance + NEW.pending_balance + NEW.staked_balance;
    NEW.last_updated = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_crypto_balances_total
    BEFORE INSERT OR UPDATE ON crypto_balances
    FOR EACH ROW
    EXECUTE FUNCTION update_crypto_total_balance();