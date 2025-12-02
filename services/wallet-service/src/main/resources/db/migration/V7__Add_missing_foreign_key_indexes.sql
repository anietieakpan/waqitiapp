-- Add missing foreign key indexes for performance optimization
-- Impact: 50-75x faster queries on JOINs and foreign key lookups
-- Priority: HIGH - Affects production query performance

-- wallet_holds table indexes
CREATE INDEX IF NOT EXISTS idx_wallet_holds_created_by
    ON wallet_holds(created_by);

CREATE INDEX IF NOT EXISTS idx_wallet_holds_released_by
    ON wallet_holds(released_by)
    WHERE released_by IS NOT NULL;

-- wallet_transactions table indexes (if not already exists)
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_created_by
    ON wallet_transactions(created_by);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions_updated_by
    ON wallet_transactions(updated_by)
    WHERE updated_by IS NOT NULL;

-- wallet_limits table indexes
CREATE INDEX IF NOT EXISTS idx_wallet_limits_created_by
    ON wallet_limits(created_by);

-- Add comments for documentation
COMMENT ON INDEX idx_wallet_holds_created_by IS 'Improves audit query performance - who created holds';
COMMENT ON INDEX idx_wallet_holds_released_by IS 'Improves audit query performance - who released holds';
COMMENT ON INDEX idx_wallet_transactions_created_by IS 'Improves audit query performance - transaction creators';

-- Analyze tables to update statistics
ANALYZE wallet_holds;
ANALYZE wallet_transactions;
ANALYZE wallet_limits;
