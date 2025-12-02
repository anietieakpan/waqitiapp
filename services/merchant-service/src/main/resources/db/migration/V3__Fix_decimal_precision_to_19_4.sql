-- Fix DECIMAL(15,2) to DECIMAL(19,4) for all financial amounts in Merchant service
-- Priority: HIGH - Critical for merchant volume tracking and settlement accuracy
-- Impact: Prevents precision loss in high-volume merchant transactions

-- merchants table - Fix volume and limit fields
ALTER TABLE merchants
    ALTER COLUMN monthly_volume_limit TYPE DECIMAL(19,4),
    ALTER COLUMN current_monthly_volume TYPE DECIMAL(19,4),
    ALTER COLUMN total_processed_volume TYPE DECIMAL(19,4);

-- merchant_payment_settings table - Fix all limit and fee fields
ALTER TABLE merchant_payment_settings
    ALTER COLUMN minimum_settlement_amount TYPE DECIMAL(19,4),
    ALTER COLUMN fixed_fee_per_transaction TYPE DECIMAL(19,4),
    ALTER COLUMN chargeback_fee TYPE DECIMAL(19,4),
    ALTER COLUMN single_transaction_limit TYPE DECIMAL(19,4),
    ALTER COLUMN daily_transaction_limit TYPE DECIMAL(19,4),
    ALTER COLUMN monthly_transaction_limit TYPE DECIMAL(19,4);

-- merchant_transactions table - Fix all amount and fee fields
ALTER TABLE merchant_transactions
    ALTER COLUMN amount TYPE DECIMAL(19,4),
    ALTER COLUMN merchant_fee TYPE DECIMAL(19,4),
    ALTER COLUMN gateway_fee TYPE DECIMAL(19,4),
    ALTER COLUMN net_amount TYPE DECIMAL(19,4);

-- settlement_batches table - Fix all settlement amounts
ALTER TABLE settlement_batches
    ALTER COLUMN total_amount TYPE DECIMAL(19,4),
    ALTER COLUMN total_fees TYPE DECIMAL(19,4),
    ALTER COLUMN net_amount TYPE DECIMAL(19,4);

-- Add comments for documentation
COMMENT ON COLUMN merchants.total_processed_volume IS 'Total processed volume with 4 decimal precision for accurate merchant analytics';
COMMENT ON COLUMN merchant_transactions.amount IS 'Transaction amount with 4 decimal precision for accurate settlement calculation';
COMMENT ON COLUMN merchant_transactions.net_amount IS 'Net amount with 4 decimal precision to prevent fee calculation errors';
COMMENT ON COLUMN settlement_batches.net_amount IS 'Settlement net amount with 4 decimal precision for accurate payouts';

-- Analyze tables to update statistics
ANALYZE merchants;
ANALYZE merchant_payment_settings;
ANALYZE merchant_transactions;
ANALYZE settlement_batches;
