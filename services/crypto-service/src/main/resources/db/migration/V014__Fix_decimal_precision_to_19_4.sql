-- Fix DECIMAL(12,2) to DECIMAL(19,4) for USD value fields in Crypto service
-- Priority: HIGH - Critical for accurate USD valuation of crypto holdings
-- Impact: Prevents precision loss in large crypto holdings' USD values
-- Note: Crypto amounts (DECIMAL 36,18) are already correct for blockchain precision

-- crypto_transactions table - Fix USD value and price fields
ALTER TABLE crypto_transactions
    ALTER COLUMN usd_value TYPE DECIMAL(19,4),
    ALTER COLUMN price TYPE DECIMAL(19,4);

-- crypto_price_history table - Fix market cap and volume fields (large numbers)
ALTER TABLE crypto_price_history
    ALTER COLUMN volume_24h TYPE DECIMAL(25,4),
    ALTER COLUMN market_cap TYPE DECIMAL(25,4);

-- Add comments for documentation
COMMENT ON COLUMN crypto_transactions.usd_value IS 'USD value with 4 decimal precision for accurate portfolio valuation';
COMMENT ON COLUMN crypto_transactions.price IS 'Price with 4 decimal precision for accurate cost basis tracking';
COMMENT ON COLUMN crypto_price_history.volume_24h IS 'Volume with 4 decimal precision (25,4 for large market values)';
COMMENT ON COLUMN crypto_price_history.market_cap IS 'Market cap with 4 decimal precision (25,4 for large market values)';

-- Analyze tables to update statistics
ANALYZE crypto_transactions;
ANALYZE crypto_price_history;
