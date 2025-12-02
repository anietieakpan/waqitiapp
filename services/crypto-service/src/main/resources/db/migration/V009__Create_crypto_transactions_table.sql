-- Create crypto transactions table
CREATE TABLE crypto_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    wallet_id UUID REFERENCES crypto_wallets(id),
    transaction_type VARCHAR(20) NOT NULL,
    currency VARCHAR(20) NOT NULL,
    amount DECIMAL(36,18) NOT NULL,
    usd_value DECIMAL(12,2),
    fee DECIMAL(36,18) NOT NULL DEFAULT 0,
    price DECIMAL(12,2),
    from_address VARCHAR(255),
    to_address VARCHAR(255),
    memo TEXT,
    tx_hash VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    confirmations INTEGER DEFAULT 0,
    block_number BIGINT,
    block_hash VARCHAR(255),
    risk_score DECIMAL(5,2),
    risk_level VARCHAR(20),
    approval_required BOOLEAN DEFAULT FALSE,
    review_required BOOLEAN DEFAULT FALSE,
    scheduled_for TIMESTAMP WITH TIME ZONE,
    broadcasted_at TIMESTAMP WITH TIME ZONE,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_crypto_transactions_type CHECK (transaction_type IN ('BUY', 'SELL', 'SEND', 'RECEIVE', 'CONVERT', 'STAKE', 'UNSTAKE')),
    CONSTRAINT chk_crypto_transactions_currency CHECK (currency IN ('BITCOIN', 'ETHEREUM', 'LITECOIN', 'USDC', 'USDT')),
    CONSTRAINT chk_crypto_transactions_status CHECK (status IN ('PENDING', 'PENDING_DELAY', 'PENDING_APPROVAL', 'PENDING_REVIEW', 'BROADCASTED', 'CONFIRMED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_crypto_transactions_risk_level CHECK (risk_level IN ('MINIMAL', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_crypto_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_crypto_transactions_confirmations CHECK (confirmations >= 0),
    CONSTRAINT chk_crypto_transactions_risk_score CHECK (risk_score >= 0 AND risk_score <= 100)
);

-- Create indexes
CREATE INDEX idx_crypto_transactions_user_id ON crypto_transactions(user_id);
CREATE INDEX idx_crypto_transactions_wallet_id ON crypto_transactions(wallet_id);
CREATE INDEX idx_crypto_transactions_type ON crypto_transactions(transaction_type);
CREATE INDEX idx_crypto_transactions_currency ON crypto_transactions(currency);
CREATE INDEX idx_crypto_transactions_status ON crypto_transactions(status);
CREATE INDEX idx_crypto_transactions_tx_hash ON crypto_transactions(tx_hash);
CREATE INDEX idx_crypto_transactions_created_at ON crypto_transactions(created_at);
CREATE INDEX idx_crypto_transactions_completed_at ON crypto_transactions(completed_at);
CREATE INDEX idx_crypto_transactions_risk_level ON crypto_transactions(risk_level);
CREATE INDEX idx_crypto_transactions_approval_required ON crypto_transactions(approval_required) WHERE approval_required = TRUE;
CREATE INDEX idx_crypto_transactions_review_required ON crypto_transactions(review_required) WHERE review_required = TRUE;
CREATE INDEX idx_crypto_transactions_scheduled ON crypto_transactions(scheduled_for) WHERE scheduled_for IS NOT NULL;

-- Add updated_at trigger
CREATE TRIGGER update_crypto_transactions_updated_at
    BEFORE UPDATE ON crypto_transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();