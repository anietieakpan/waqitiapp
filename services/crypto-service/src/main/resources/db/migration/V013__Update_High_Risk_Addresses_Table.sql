-- Update High Risk Addresses Table
-- Add missing columns for enhanced address analysis

-- Add new columns if they don't exist
ALTER TABLE high_risk_addresses 
    ADD COLUMN IF NOT EXISTS transaction_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS high_frequency BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_privacy_coin_interaction BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_from_high_risk_jurisdiction BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS first_seen_date TIMESTAMP;

-- Add indexes for new columns
CREATE INDEX IF NOT EXISTS idx_high_risk_high_frequency 
    ON high_risk_addresses(high_frequency) 
    WHERE high_frequency = TRUE;

CREATE INDEX IF NOT EXISTS idx_high_risk_jurisdiction 
    ON high_risk_addresses(is_from_high_risk_jurisdiction) 
    WHERE is_from_high_risk_jurisdiction = TRUE;

CREATE INDEX IF NOT EXISTS idx_high_risk_privacy_coin 
    ON high_risk_addresses(has_privacy_coin_interaction) 
    WHERE has_privacy_coin_interaction = TRUE;

CREATE INDEX IF NOT EXISTS idx_high_risk_first_seen 
    ON high_risk_addresses(first_seen_date DESC NULLS LAST);

-- Update existing records with default values for new columns
UPDATE high_risk_addresses 
SET 
    transaction_count = 0,
    high_frequency = FALSE,
    has_privacy_coin_interaction = FALSE,
    is_from_high_risk_jurisdiction = FALSE,
    first_seen_date = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE transaction_count IS NULL;

-- Add check constraint for risk level
ALTER TABLE high_risk_addresses
    DROP CONSTRAINT IF EXISTS chk_risk_level_range;

ALTER TABLE high_risk_addresses
    ADD CONSTRAINT chk_risk_level_range 
    CHECK (risk_level BETWEEN 1 AND 10);

-- Add check constraint for transaction count
ALTER TABLE high_risk_addresses
    ADD CONSTRAINT IF NOT EXISTS chk_transaction_count_non_negative 
    CHECK (transaction_count >= 0);

-- Comments for new columns
COMMENT ON COLUMN high_risk_addresses.transaction_count IS 'Total number of transactions observed for this address';
COMMENT ON COLUMN high_risk_addresses.high_frequency IS 'TRUE if address shows high transaction frequency patterns';
COMMENT ON COLUMN high_risk_addresses.has_privacy_coin_interaction IS 'TRUE if address has interacted with privacy coins (Monero, Zcash, etc.)';
COMMENT ON COLUMN high_risk_addresses.is_from_high_risk_jurisdiction IS 'TRUE if address is associated with high-risk jurisdictions (FATF list)';
COMMENT ON COLUMN high_risk_addresses.first_seen_date IS 'Date when this address was first observed on the blockchain';