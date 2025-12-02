-- Create Real-time Metrics table for live dashboard data
CREATE TABLE real_time_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_type VARCHAR(100) NOT NULL,
    metric_key VARCHAR(255) NOT NULL,
    metric_value DECIMAL(19,4) NOT NULL,
    metric_count BIGINT DEFAULT 1,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    hour_bucket TIMESTAMP WITH TIME ZONE NOT NULL, -- Rounded to nearest hour
    day_bucket DATE NOT NULL, -- Date bucket
    user_id UUID,
    transaction_id UUID,
    metadata JSONB,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes for real-time querying
CREATE INDEX idx_real_time_metrics_type ON real_time_metrics(metric_type);
CREATE INDEX idx_real_time_metrics_key ON real_time_metrics(metric_key);
CREATE INDEX idx_real_time_metrics_timestamp ON real_time_metrics(timestamp);
CREATE INDEX idx_real_time_metrics_hour_bucket ON real_time_metrics(hour_bucket);
CREATE INDEX idx_real_time_metrics_day_bucket ON real_time_metrics(day_bucket);
CREATE INDEX idx_real_time_metrics_user_id ON real_time_metrics(user_id);
CREATE INDEX idx_real_time_metrics_expires_at ON real_time_metrics(expires_at) WHERE expires_at IS NOT NULL;

-- Composite indexes for real-time aggregation
CREATE INDEX idx_real_time_metrics_type_hour ON real_time_metrics(metric_type, hour_bucket);
CREATE INDEX idx_real_time_metrics_key_hour ON real_time_metrics(metric_key, hour_bucket);
CREATE INDEX idx_real_time_metrics_type_day ON real_time_metrics(metric_type, day_bucket);
CREATE INDEX idx_real_time_metrics_user_hour ON real_time_metrics(user_id, hour_bucket) WHERE user_id IS NOT NULL;

-- JSONB index for metadata queries
CREATE INDEX idx_real_time_metrics_metadata_gin ON real_time_metrics USING GIN (metadata);

-- Partial indexes for specific metric types
CREATE INDEX idx_real_time_metrics_transactions ON real_time_metrics(timestamp, metric_value) 
WHERE metric_type = 'TRANSACTION';
CREATE INDEX idx_real_time_metrics_volume ON real_time_metrics(timestamp, metric_value) 
WHERE metric_type = 'VOLUME';
CREATE INDEX idx_real_time_metrics_users ON real_time_metrics(timestamp, metric_count) 
WHERE metric_type = 'ACTIVE_USERS';

-- Add comments for documentation
COMMENT ON TABLE real_time_metrics IS 'Real-time metrics for live dashboard and monitoring';
COMMENT ON COLUMN real_time_metrics.metric_type IS 'Type of metric (TRANSACTION, VOLUME, ACTIVE_USERS, etc.)';
COMMENT ON COLUMN real_time_metrics.metric_key IS 'Specific metric identifier (e.g., transactions_per_minute)';
COMMENT ON COLUMN real_time_metrics.metric_value IS 'Numeric value of the metric';
COMMENT ON COLUMN real_time_metrics.metric_count IS 'Count associated with the metric (for averaging)';
COMMENT ON COLUMN real_time_metrics.hour_bucket IS 'Timestamp rounded to the nearest hour for aggregation';
COMMENT ON COLUMN real_time_metrics.day_bucket IS 'Date for daily aggregation';
COMMENT ON COLUMN real_time_metrics.metadata IS 'Additional metric metadata in JSON format';
COMMENT ON COLUMN real_time_metrics.expires_at IS 'Optional expiration timestamp for temporary metrics';

-- Create function to clean up expired metrics
CREATE OR REPLACE FUNCTION cleanup_expired_real_time_metrics()
RETURNS void AS $$
BEGIN
    DELETE FROM real_time_metrics 
    WHERE expires_at IS NOT NULL AND expires_at < NOW();
END;
$$ LANGUAGE plpgsql;