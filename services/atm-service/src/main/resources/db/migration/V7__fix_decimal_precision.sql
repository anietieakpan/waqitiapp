-- ATM Service Schema V7: Fix Financial Precision (DECIMAL 15,2 → 19,4)
-- Created: 2025-11-15
-- Description: Update all money columns from DECIMAL(15,2) to DECIMAL(19,4) for proper financial precision

-- Fix atm_device table
ALTER TABLE atm_device
    ALTER COLUMN cash_available TYPE DECIMAL(19, 4),
    ALTER COLUMN cash_capacity TYPE DECIMAL(19, 4);

-- Fix atm_transactions table (from V1)
ALTER TABLE atm_transactions
    ALTER COLUMN amount TYPE DECIMAL(19, 4),
    ALTER COLUMN fee TYPE DECIMAL(19, 4);

-- Fix atm_cash_cassette table
ALTER TABLE atm_cash_cassette
    ALTER COLUMN denomination TYPE DECIMAL(19, 4);

-- Update default values to maintain precision
ALTER TABLE atm_device ALTER COLUMN cash_available SET DEFAULT 0.0000;
ALTER TABLE atm_transactions ALTER COLUMN fee SET DEFAULT 0.0000;

COMMENT ON COLUMN atm_device.cash_available IS 'DECIMAL(19,4) for proper financial precision';
COMMENT ON COLUMN atm_device.cash_capacity IS 'DECIMAL(19,4) for proper financial precision';
COMMENT ON COLUMN atm_transactions.amount IS 'DECIMAL(19,4) for proper financial precision';
COMMENT ON COLUMN atm_transactions.fee IS 'DECIMAL(19,4) for proper financial precision';
