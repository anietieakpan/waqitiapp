-- Create crypto price history table
CREATE TABLE crypto_price_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    currency VARCHAR(20) NOT NULL,
    price DECIMAL(12,8) NOT NULL,
    volume_24h DECIMAL(20,2),
    market_cap DECIMAL(20,2),
    change_24h DECIMAL(12,8),
    change_percent_24h DECIMAL(8,4),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    
    CONSTRAINT chk_crypto_price_history_currency CHECK (currency IN ('BITCOIN', 'ETHEREUM', 'LITECOIN', 'USDC', 'USDT')),
    CONSTRAINT chk_crypto_price_history_price CHECK (price > 0)
);

-- Create indexes
CREATE INDEX idx_crypto_price_history_currency ON crypto_price_history(currency);
CREATE INDEX idx_crypto_price_history_timestamp ON crypto_price_history(timestamp);
CREATE INDEX idx_crypto_price_history_currency_timestamp ON crypto_price_history(currency, timestamp);

-- Create hypertable for time-series data (if using TimescaleDB)
-- SELECT create_hypertable('crypto_price_history', 'timestamp', if_not_exists => TRUE);

-- Create current prices view
CREATE OR REPLACE VIEW crypto_current_prices AS
SELECT DISTINCT ON (currency)
    currency,
    price,
    volume_24h,
    market_cap,
    change_24h,
    change_percent_24h,
    timestamp,
    source
FROM crypto_price_history
ORDER BY currency, timestamp DESC;