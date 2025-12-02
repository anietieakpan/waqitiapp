-- Create account_limits table for managing wallet transaction limits and velocity controls
CREATE TABLE IF NOT EXISTS account_limits (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    daily_transaction_limit DECIMAL(19, 4) DEFAULT 10000.00,
    monthly_transaction_limit DECIMAL(19, 4) DEFAULT 50000.00,
    single_transaction_limit DECIMAL(19, 4) DEFAULT 5000.00,
    withdrawal_limit DECIMAL(19, 4) DEFAULT 5000.00,
    deposit_limit DECIMAL(19, 4) DEFAULT 10000.00,
    account_balance_limit DECIMAL(19, 4) DEFAULT 100000.00,
    velocity_limit INTEGER DEFAULT 10,
    velocity_time_window VARCHAR(10) DEFAULT '1H',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    update_reason VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_account_limits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create index on user_id for faster lookups
CREATE INDEX idx_account_limits_user_id ON account_limits(user_id);

-- Create index on is_active for filtering active limits
CREATE INDEX idx_account_limits_active ON account_limits(is_active);

-- Add comments for documentation
COMMENT ON TABLE account_limits IS 'Stores transaction limits and velocity controls for wallet accounts';
COMMENT ON COLUMN account_limits.user_id IS 'User ID associated with these account limits';
COMMENT ON COLUMN account_limits.daily_transaction_limit IS 'Maximum total transaction amount per day';
COMMENT ON COLUMN account_limits.monthly_transaction_limit IS 'Maximum total transaction amount per month';
COMMENT ON COLUMN account_limits.single_transaction_limit IS 'Maximum amount for a single transaction';
COMMENT ON COLUMN account_limits.withdrawal_limit IS 'Maximum withdrawal amount';
COMMENT ON COLUMN account_limits.deposit_limit IS 'Maximum deposit amount';
COMMENT ON COLUMN account_limits.account_balance_limit IS 'Maximum account balance allowed';
COMMENT ON COLUMN account_limits.velocity_limit IS 'Maximum number of transactions within time window';
COMMENT ON COLUMN account_limits.velocity_time_window IS 'Time window for velocity limit (e.g., 1H, 24H, 1D)';
COMMENT ON COLUMN account_limits.version IS 'Optimistic locking version field';
