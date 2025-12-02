-- V4: Add Missing Account Fields
-- Adds fields for primary account tracking, external ID reference, and KYC updates

-- Add missing columns to accounts table
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS is_primary BOOLEAN DEFAULT FALSE;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS external_account_id VARCHAR(100);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS last_kyc_update TIMESTAMP;

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_external_account_id ON accounts(external_account_id) WHERE external_account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_primary_wallet ON accounts(user_id, account_type, is_primary) WHERE is_primary = TRUE;
CREATE INDEX IF NOT EXISTS idx_kyc_update ON accounts(last_kyc_update) WHERE last_kyc_update IS NOT NULL;

-- Set primary wallet for existing users (one wallet per user)
WITH ranked_wallets AS (
    SELECT account_id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at ASC) as rn
    FROM accounts
    WHERE account_type = 'USER_WALLET'
    AND user_id IS NOT NULL
)
UPDATE accounts
SET is_primary = TRUE
WHERE account_id IN (
    SELECT account_id 
    FROM ranked_wallets 
    WHERE rn = 1
);

-- Set initial KYC update date for active accounts
UPDATE accounts
SET last_kyc_update = created_at
WHERE status = 'ACTIVE'
AND last_kyc_update IS NULL;

-- Add comments explaining the fields
COMMENT ON COLUMN accounts.is_primary IS 'Indicates if this is the user''s primary account for the given type';
COMMENT ON COLUMN accounts.external_account_id IS 'Reference to external account ID (e.g., from Cyclos migration)';
COMMENT ON COLUMN accounts.last_kyc_update IS 'Timestamp of last KYC verification or update';

-- Add constraint to ensure only one primary account per user per type
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_primary_account_type 
ON accounts(user_id, account_type) 
WHERE is_primary = TRUE;