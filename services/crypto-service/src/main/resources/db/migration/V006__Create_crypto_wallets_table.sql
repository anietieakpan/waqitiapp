-- Create crypto wallets table
CREATE TABLE crypto_wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    currency VARCHAR(20) NOT NULL,
    wallet_type VARCHAR(20) NOT NULL DEFAULT 'MULTISIG_HD',
    derivation_path VARCHAR(255) NOT NULL,
    public_key TEXT NOT NULL,
    encrypted_private_key TEXT NOT NULL,
    kms_key_id VARCHAR(255) NOT NULL,
    encryption_context JSONB,
    multi_sig_address VARCHAR(255) NOT NULL UNIQUE,
    redeem_script TEXT,
    required_signatures INTEGER NOT NULL DEFAULT 2,
    total_keys INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_crypto_wallets_user_currency UNIQUE (user_id, currency),
    CONSTRAINT chk_crypto_wallets_currency CHECK (currency IN ('BITCOIN', 'ETHEREUM', 'LITECOIN', 'USDC', 'USDT')),
    CONSTRAINT chk_crypto_wallets_wallet_type CHECK (wallet_type IN ('HD', 'MULTISIG_HD', 'HARDWARE')),
    CONSTRAINT chk_crypto_wallets_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT chk_crypto_wallets_signatures CHECK (required_signatures <= total_keys AND required_signatures > 0)
);

-- Create indexes
CREATE INDEX idx_crypto_wallets_user_id ON crypto_wallets(user_id);
CREATE INDEX idx_crypto_wallets_currency ON crypto_wallets(currency);
CREATE INDEX idx_crypto_wallets_status ON crypto_wallets(status);
CREATE INDEX idx_crypto_wallets_multi_sig_address ON crypto_wallets(multi_sig_address);
CREATE INDEX idx_crypto_wallets_created_at ON crypto_wallets(created_at);

-- Add updated_at trigger
CREATE TRIGGER update_crypto_wallets_updated_at
    BEFORE UPDATE ON crypto_wallets
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();