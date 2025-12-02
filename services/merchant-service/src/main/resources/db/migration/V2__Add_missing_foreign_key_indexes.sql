-- Add missing foreign key indexes for merchant-service
-- Critical for audit queries and merchant management performance

-- merchants table - audit fields
CREATE INDEX IF NOT EXISTS idx_merchants_created_by
    ON merchants(created_by);

CREATE INDEX IF NOT EXISTS idx_merchants_updated_by
    ON merchants(updated_by)
    WHERE updated_by IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_merchants_verified_by
    ON merchants(verified_by)
    WHERE verified_by IS NOT NULL;

-- merchant_transactions table - customer lookup
CREATE INDEX IF NOT EXISTS idx_merchant_transactions_customer_id
    ON merchant_transactions(customer_id)
    WHERE customer_id IS NOT NULL;

-- merchant_api_keys table - created_by audit
CREATE INDEX IF NOT EXISTS idx_merchant_api_keys_created_by
    ON merchant_api_keys(created_by)
    WHERE created_by IS NOT NULL;

COMMENT ON INDEX idx_merchants_verified_by IS
    'Audit: Track merchant verifications by specific compliance officer';
COMMENT ON INDEX idx_merchant_transactions_customer_id IS
    'Performance: Find all transactions for specific customer';

-- Analyze tables
ANALYZE merchants;
ANALYZE merchant_transactions;
ANALYZE merchant_api_keys;
