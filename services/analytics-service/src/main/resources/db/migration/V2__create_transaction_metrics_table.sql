-- Create Transaction Metrics table for business analytics
CREATE TABLE transaction_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date DATE NOT NULL UNIQUE,
    total_transactions BIGINT NOT NULL DEFAULT 0,
    total_volume DECIMAL(19,4) NOT NULL DEFAULT 0,
    successful_transactions BIGINT NOT NULL DEFAULT 0,
    failed_transactions BIGINT NOT NULL DEFAULT 0,
    average_transaction_amount DECIMAL(19,4),
    peak_hour_volume DECIMAL(19,4),
    peak_hour INTEGER CHECK (peak_hour BETWEEN 0 AND 23),
    unique_users BIGINT NOT NULL DEFAULT 0,
    new_users BIGINT NOT NULL DEFAULT 0,
    active_wallets BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for efficient querying and reporting
CREATE UNIQUE INDEX idx_transaction_metrics_date ON transaction_metrics(date);
CREATE INDEX idx_transaction_metrics_created_at ON transaction_metrics(created_at);
CREATE INDEX idx_transaction_metrics_total_volume ON transaction_metrics(total_volume);
CREATE INDEX idx_transaction_metrics_total_transactions ON transaction_metrics(total_transactions);
CREATE INDEX idx_transaction_metrics_success_rate ON transaction_metrics(successful_transactions, total_transactions);

-- Partial indexes for specific analysis
CREATE INDEX idx_transaction_metrics_high_volume ON transaction_metrics(date, total_volume) 
WHERE total_volume > 100000;
CREATE INDEX idx_transaction_metrics_high_activity ON transaction_metrics(date, total_transactions) 
WHERE total_transactions > 1000;

-- Add comments for documentation
COMMENT ON TABLE transaction_metrics IS 'Daily aggregated transaction metrics for business analytics';
COMMENT ON COLUMN transaction_metrics.date IS 'Date for which metrics are calculated (one record per day)';
COMMENT ON COLUMN transaction_metrics.total_volume IS 'Total transaction volume in base currency for the day';
COMMENT ON COLUMN transaction_metrics.peak_hour IS 'Hour of day (0-23) with highest transaction volume';
COMMENT ON COLUMN transaction_metrics.peak_hour_volume IS 'Transaction volume during the peak hour';
COMMENT ON COLUMN transaction_metrics.average_transaction_amount IS 'Average amount per successful transaction';
COMMENT ON COLUMN transaction_metrics.unique_users IS 'Count of unique users who made transactions';
COMMENT ON COLUMN transaction_metrics.new_users IS 'Count of users who made their first transaction';
COMMENT ON COLUMN transaction_metrics.active_wallets IS 'Count of unique wallets used in transactions';