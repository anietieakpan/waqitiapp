-- V3: Add Interest Calculation Support
-- Adds fields required for automated interest calculation

-- Add interest calculation related columns to accounts table
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS interest_calculation_method VARCHAR(30);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_interest_calculation_date DATE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_interest_credited_amount DECIMAL(19,4);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS minimum_balance_for_interest DECIMAL(19,4);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS minimum_interest_amount DECIMAL(19,4);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS maximum_interest_amount DECIMAL(19,4);

-- Add indexes for interest calculation performance
CREATE INDEX IF NOT EXISTS idx_interest_eligible_accounts 
ON accounts(status, account_type, interest_rate, last_interest_calculation_date)
WHERE status = 'ACTIVE' AND interest_rate > 0;

-- Create interest calculation audit table
CREATE TABLE IF NOT EXISTS interest_calculations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    calculation_date DATE NOT NULL,
    average_balance DECIMAL(19,4) NOT NULL,
    interest_rate DECIMAL(8,6) NOT NULL,
    calculation_method VARCHAR(30) NOT NULL,
    interest_amount DECIMAL(19,4) NOT NULL,
    transaction_id UUID REFERENCES transactions(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    error_message TEXT,
    CONSTRAINT uk_account_calculation_date UNIQUE(account_id, calculation_date)
);

-- Create index for audit queries
CREATE INDEX idx_interest_calc_account_date ON interest_calculations(account_id, calculation_date DESC);
CREATE INDEX idx_interest_calc_status ON interest_calculations(status, created_at);

-- Update existing savings accounts to have default interest calculation method
UPDATE accounts 
SET interest_calculation_method = 'DAILY_BALANCE'
WHERE account_type IN ('USER_SAVINGS', 'SAVINGS', 'FIXED_DEPOSIT', 'MONEY_MARKET')
AND interest_calculation_method IS NULL
AND interest_rate IS NOT NULL 
AND interest_rate > 0;

-- Add comment explaining the fields
COMMENT ON COLUMN accounts.interest_calculation_method IS 'Method for calculating interest: DAILY_BALANCE, MONTHLY_COMPOUND, QUARTERLY_COMPOUND';
COMMENT ON COLUMN accounts.last_interest_calculation_date IS 'Last date when interest was calculated for this account';
COMMENT ON COLUMN accounts.last_interest_credited_amount IS 'Amount of interest credited in the last calculation';
COMMENT ON COLUMN accounts.minimum_balance_for_interest IS 'Minimum balance required to earn interest';
COMMENT ON COLUMN accounts.minimum_interest_amount IS 'Minimum interest amount to credit (below this, no interest is applied)';
COMMENT ON COLUMN accounts.maximum_interest_amount IS 'Maximum interest amount per calculation period';