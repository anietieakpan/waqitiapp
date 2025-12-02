-- Create crypto addresses table
CREATE TABLE crypto_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES crypto_wallets(id) ON DELETE CASCADE,
    address VARCHAR(255) NOT NULL,
    derivation_path VARCHAR(255) NOT NULL,
    public_key TEXT NOT NULL,
    address_index INTEGER NOT NULL,
    address_type VARCHAR(20) NOT NULL DEFAULT 'RECEIVING',
    label VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    used_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_crypto_addresses_address UNIQUE (address),
    CONSTRAINT uk_crypto_addresses_wallet_index UNIQUE (wallet_id, address_index),
    CONSTRAINT chk_crypto_addresses_type CHECK (address_type IN ('RECEIVING', 'CHANGE')),
    CONSTRAINT chk_crypto_addresses_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'USED')),
    CONSTRAINT chk_crypto_addresses_index CHECK (address_index >= 0)
);

-- Create indexes
CREATE INDEX idx_crypto_addresses_wallet_id ON crypto_addresses(wallet_id);
CREATE INDEX idx_crypto_addresses_address ON crypto_addresses(address);
CREATE INDEX idx_crypto_addresses_status ON crypto_addresses(status);
CREATE INDEX idx_crypto_addresses_type ON crypto_addresses(address_type);
CREATE INDEX idx_crypto_addresses_created_at ON crypto_addresses(created_at);
CREATE INDEX idx_crypto_addresses_derivation_path ON crypto_addresses(derivation_path);