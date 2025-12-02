-- Create User Metrics table for user analytics
CREATE TABLE user_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date DATE NOT NULL,
    user_id UUID NOT NULL,
    transaction_count BIGINT NOT NULL DEFAULT 0,
    transaction_volume DECIMAL(19,4) NOT NULL DEFAULT 0,
    successful_transactions BIGINT NOT NULL DEFAULT 0,
    failed_transactions BIGINT NOT NULL DEFAULT 0,
    average_transaction_amount DECIMAL(19,4),
    first_transaction_at TIMESTAMP WITH TIME ZONE,
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    unique_recipients BIGINT DEFAULT 0,
    wallet_count INTEGER DEFAULT 0,
    payment_methods_used INTEGER DEFAULT 0,
    countries_transacted TEXT[], -- Array of country codes
    peak_hour INTEGER CHECK (peak_hour BETWEEN 0 AND 23),
    risk_score INTEGER CHECK (risk_score BETWEEN 1 AND 100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(date, user_id)
);

-- Create indexes for efficient querying and user analytics
CREATE UNIQUE INDEX idx_user_metrics_date_user ON user_metrics(date, user_id);
CREATE INDEX idx_user_metrics_user_id ON user_metrics(user_id);
CREATE INDEX idx_user_metrics_date ON user_metrics(date);
CREATE INDEX idx_user_metrics_created_at ON user_metrics(created_at);
CREATE INDEX idx_user_metrics_transaction_volume ON user_metrics(transaction_volume);
CREATE INDEX idx_user_metrics_transaction_count ON user_metrics(transaction_count);
CREATE INDEX idx_user_metrics_risk_score ON user_metrics(risk_score) WHERE risk_score > 70;
CREATE INDEX idx_user_metrics_first_transaction ON user_metrics(first_transaction_at);

-- Composite indexes for user behavior analysis
CREATE INDEX idx_user_metrics_user_volume ON user_metrics(user_id, transaction_volume);
CREATE INDEX idx_user_metrics_user_count ON user_metrics(user_id, transaction_count);
CREATE INDEX idx_user_metrics_user_date ON user_metrics(user_id, date);

-- Partial indexes for specific analysis
CREATE INDEX idx_user_metrics_high_volume_users ON user_metrics(user_id, date) 
WHERE transaction_volume > 10000;
CREATE INDEX idx_user_metrics_high_activity_users ON user_metrics(user_id, date) 
WHERE transaction_count > 50;
CREATE INDEX idx_user_metrics_new_users ON user_metrics(date, user_id) 
WHERE first_transaction_at IS NOT NULL;

-- GIN index for country array queries
CREATE INDEX idx_user_metrics_countries_gin ON user_metrics USING GIN (countries_transacted);

-- Add comments for documentation
COMMENT ON TABLE user_metrics IS 'Daily user-level transaction metrics for user behavior analytics';
COMMENT ON COLUMN user_metrics.date IS 'Date for which user metrics are calculated';
COMMENT ON COLUMN user_metrics.user_id IS 'User for whom metrics are calculated';
COMMENT ON COLUMN user_metrics.unique_recipients IS 'Number of unique recipients user sent money to';
COMMENT ON COLUMN user_metrics.wallet_count IS 'Number of different wallets user used';
COMMENT ON COLUMN user_metrics.payment_methods_used IS 'Number of different payment methods used';
COMMENT ON COLUMN user_metrics.countries_transacted IS 'Array of country codes where user made transactions';
COMMENT ON COLUMN user_metrics.peak_hour IS 'Hour of day when user was most active';
COMMENT ON COLUMN user_metrics.risk_score IS 'Calculated risk score based on user behavior patterns';