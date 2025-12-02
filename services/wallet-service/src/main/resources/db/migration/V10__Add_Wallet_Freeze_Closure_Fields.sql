-- Migration V10: Add Wallet Freeze and Closure Tracking Fields
-- Date: October 9, 2025
-- Description: Adds fields to track wallet freezes and closures for account deactivation events

-- Add frozen reason field
ALTER TABLE wallets
ADD COLUMN frozen_reason VARCHAR(500);

-- Add frozen timestamp
ALTER TABLE wallets
ADD COLUMN frozen_at TIMESTAMP;

-- Add closed by field (tracks who closed the wallet)
ALTER TABLE wallets
ADD COLUMN closed_by UUID;

-- Add closed timestamp
ALTER TABLE wallets
ADD COLUMN closed_at TIMESTAMP;

-- Create indexes for efficient querying
CREATE INDEX idx_wallets_frozen_at ON wallets(frozen_at) WHERE frozen_at IS NOT NULL;
CREATE INDEX idx_wallets_closed_at ON wallets(closed_at) WHERE closed_at IS NOT NULL;
CREATE INDEX idx_wallets_closed_by ON wallets(closed_by) WHERE closed_by IS NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN wallets.frozen_reason IS 'Reason for wallet freeze (e.g., account deactivation, security review)';
COMMENT ON COLUMN wallets.frozen_at IS 'Timestamp when wallet was frozen';
COMMENT ON COLUMN wallets.closed_by IS 'UUID of user/admin who closed the wallet';
COMMENT ON COLUMN wallets.closed_at IS 'Timestamp when wallet was permanently closed';
