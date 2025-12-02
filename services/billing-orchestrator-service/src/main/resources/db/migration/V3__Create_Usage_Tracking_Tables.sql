-- V3__Create_Usage_Tracking_Tables.sql
-- Usage-Based Billing Schema
-- Author: Waqiti Billing Team
-- Date: 2025-10-18
--
-- PURPOSE: Consumption-based billing for metered services (API calls, storage, bandwidth, compute)
--
-- BUSINESS MODELS SUPPORTED:
-- - Pay-as-you-go billing
-- - Tiered pricing (volume discounts)
-- - Committed usage discounts
-- - Overage charges
--
-- TYPICAL VOLUMES:
-- - 10M-100M usage records per month
-- - Real-time ingestion (1000+ records/sec)
-- - Billing aggregation daily/monthly

-- ==================== CREATE TABLES ====================

CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    subscription_id UUID,
    billing_cycle_id UUID,

    -- Metric details
    metric_name VARCHAR(100) NOT NULL,
    metric_category VARCHAR(50) CHECK (metric_category IN (
        'API_USAGE', 'STORAGE', 'BANDWIDTH', 'COMPUTE',
        'TRANSACTIONS', 'MESSAGING', 'DATABASE', 'CUSTOM'
    )),

    quantity DECIMAL(19, 4) NOT NULL,
    unit VARCHAR(50),

    -- Pricing
    unit_price DECIMAL(19, 4),
    total_amount DECIMAL(19, 4),
    currency CHAR(3) DEFAULT 'USD',

    -- Timing
    usage_timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    billing_period_start TIMESTAMP,
    billing_period_end TIMESTAMP,

    -- Status tracking
    status VARCHAR(20) DEFAULT 'RECORDED' CHECK (status IN (
        'RECORDED', 'VALIDATED', 'AGGREGATED', 'BILLED', 'DISPUTED'
    )),

    billed BOOLEAN DEFAULT FALSE,
    billed_at TIMESTAMP,
    invoice_id UUID,

    -- Idempotency (CRITICAL - prevents duplicate charges)
    idempotency_key VARCHAR(255) UNIQUE,

    -- Metadata
    source VARCHAR(100),        -- e.g., "api-gateway", "storage-service"
    resource_id VARCHAR(255),   -- e.g., API endpoint, storage bucket
    tags TEXT,                  -- JSON metadata

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,

    -- Foreign keys
    CONSTRAINT fk_usage_account FOREIGN KEY (account_id)
        REFERENCES billing_cycles(account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_usage_billing_cycle FOREIGN KEY (billing_cycle_id)
        REFERENCES billing_cycles(id) ON DELETE SET NULL
);

-- ==================== CREATE INDEXES ====================

-- Core query indexes
CREATE INDEX idx_usage_account ON usage_records(account_id);
CREATE INDEX idx_usage_subscription ON usage_records(subscription_id) WHERE subscription_id IS NOT NULL;
CREATE INDEX idx_usage_timestamp ON usage_records(usage_timestamp DESC);

-- Billing aggregation indexes (CRITICAL FOR PERFORMANCE)
CREATE INDEX idx_usage_unbilled
    ON usage_records(account_id, billed, usage_timestamp)
    WHERE billed = FALSE;

CREATE INDEX idx_usage_metric_period
    ON usage_records(account_id, metric_name, usage_timestamp)
    WHERE billed = FALSE;

CREATE INDEX idx_usage_billing_period
    ON usage_records(billing_period_start, billing_period_end, account_id)
    WHERE billing_period_start IS NOT NULL;

-- Idempotency index (UNIQUE constraint for duplicate prevention)
CREATE UNIQUE INDEX idx_usage_idempotency
    ON usage_records(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Status tracking
CREATE INDEX idx_usage_status ON usage_records(status, usage_timestamp);

-- Invoice reconciliation
CREATE INDEX idx_usage_invoice ON usage_records(invoice_id) WHERE invoice_id IS NOT NULL;

-- Metric analysis
CREATE INDEX idx_usage_metric_category ON usage_records(metric_category, usage_timestamp);

-- ==================== CREATE TRIGGERS ====================

-- Auto-update timestamp
CREATE OR REPLACE FUNCTION update_usage_records_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_usage_records_timestamp
    BEFORE UPDATE ON usage_records
    FOR EACH ROW
    EXECUTE FUNCTION update_usage_records_updated_at();

-- Auto-calculate total_amount
CREATE OR REPLACE FUNCTION calculate_usage_total_amount()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.quantity IS NOT NULL AND NEW.unit_price IS NOT NULL THEN
        NEW.total_amount = NEW.quantity * NEW.unit_price;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_calculate_usage_amount
    BEFORE INSERT OR UPDATE ON usage_records
    FOR EACH ROW
    EXECUTE FUNCTION calculate_usage_total_amount();

-- ==================== ANALYTICS VIEWS ====================

-- Usage summary by metric
CREATE OR REPLACE VIEW v_usage_summary AS
SELECT
    metric_name,
    metric_category,
    DATE_TRUNC('day', usage_timestamp) AS usage_date,
    COUNT(*) AS record_count,
    SUM(quantity) AS total_quantity,
    SUM(total_amount) AS total_amount,
    AVG(unit_price) AS avg_unit_price,
    COUNT(DISTINCT account_id) AS unique_accounts
FROM usage_records
WHERE usage_timestamp >= NOW() - INTERVAL '30 days'
GROUP BY metric_name, metric_category, DATE_TRUNC('day', usage_timestamp)
ORDER BY usage_date DESC, total_amount DESC;

-- Unbilled usage by account
CREATE OR REPLACE VIEW v_unbilled_usage AS
SELECT
    account_id,
    customer_id,
    COUNT(*) AS unbilled_records,
    SUM(total_amount) AS unbilled_amount,
    MIN(usage_timestamp) AS oldest_unbilled,
    MAX(usage_timestamp) AS latest_unbilled
FROM usage_records
WHERE billed = FALSE
GROUP BY account_id, customer_id
HAVING SUM(total_amount) > 0
ORDER BY unbilled_amount DESC;

-- Top consumers by metric
CREATE OR REPLACE VIEW v_top_usage_consumers AS
SELECT
    metric_name,
    account_id,
    SUM(quantity) AS total_usage,
    SUM(total_amount) AS total_spend,
    COUNT(*) AS usage_count,
    MAX(usage_timestamp) AS last_usage
FROM usage_records
WHERE usage_timestamp >= NOW() - INTERVAL '30 days'
GROUP BY metric_name, account_id
ORDER BY total_amount DESC
LIMIT 100;

-- Usage trends (daily aggregation)
CREATE OR REPLACE VIEW v_usage_trends AS
SELECT
    DATE_TRUNC('day', usage_timestamp) AS usage_date,
    metric_category,
    COUNT(*) AS total_records,
    SUM(quantity) AS total_quantity,
    SUM(total_amount) AS total_revenue,
    COUNT(DISTINCT account_id) AS active_accounts
FROM usage_records
WHERE usage_timestamp >= NOW() - INTERVAL '90 days'
GROUP BY DATE_TRUNC('day', usage_timestamp), metric_category
ORDER BY usage_date DESC, total_revenue DESC;

-- ==================== PARTITIONING (OPTIONAL - for high volume) ====================

-- Uncomment below for time-series partitioning (recommended for 100M+ records)
/*
-- Drop existing table and recreate as partitioned
DROP TABLE IF EXISTS usage_records CASCADE;

CREATE TABLE usage_records (
    -- ... same schema as above ...
) PARTITION BY RANGE (usage_timestamp);

-- Create monthly partitions (example for 2025)
CREATE TABLE usage_records_2025_01 PARTITION OF usage_records
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE usage_records_2025_02 PARTITION OF usage_records
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- ... create more partitions ...

-- Create default partition for future dates
CREATE TABLE usage_records_default PARTITION OF usage_records DEFAULT;
*/

-- ==================== COMMENTS ====================

COMMENT ON TABLE usage_records IS 'Consumption-based billing records for metered services';
COMMENT ON COLUMN usage_records.idempotency_key IS 'Unique key to prevent duplicate charges from retries (CRITICAL)';
COMMENT ON COLUMN usage_records.total_amount IS 'Auto-calculated: quantity * unit_price';
COMMENT ON COLUMN usage_records.billed IS 'TRUE when usage has been invoiced';
COMMENT ON COLUMN usage_records.metric_category IS 'Groups metrics for analytics (API_USAGE, STORAGE, BANDWIDTH, etc.)';
COMMENT ON VIEW v_unbilled_usage IS 'Real-time view of unbilled usage by account (for billing runs)';
COMMENT ON VIEW v_top_usage_consumers IS 'Top 100 consumers by revenue (last 30 days)';

-- ==================== SAMPLE DATA (OPTIONAL - FOR TESTING) ====================

-- Uncomment below to insert sample usage records for testing
/*
INSERT INTO usage_records (
    account_id,
    customer_id,
    metric_name,
    metric_category,
    quantity,
    unit,
    unit_price,
    currency,
    usage_timestamp,
    idempotency_key,
    source
) VALUES
(
    gen_random_uuid(),
    gen_random_uuid(),
    'api_calls',
    'API_USAGE',
    1000.0000,
    'requests',
    0.0010,
    'USD',
    NOW(),
    'test-' || gen_random_uuid()::text,
    'api-gateway'
);
*/
