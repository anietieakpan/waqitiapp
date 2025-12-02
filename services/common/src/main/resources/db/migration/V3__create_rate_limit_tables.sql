-- Rate Limit and API Quota Management Tables

-- Table for API rate limit configurations
CREATE TABLE IF NOT EXISTS rate_limit_configurations (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    tier VARCHAR(50) NOT NULL DEFAULT 'BASIC',
    requests_per_minute INT NOT NULL DEFAULT 60,
    requests_per_hour INT NOT NULL DEFAULT 1000,
    requests_per_day INT NOT NULL DEFAULT 10000,
    requests_per_month INT NOT NULL DEFAULT 100000,
    burst_capacity INT DEFAULT 10,
    custom_limits JSONB,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    UNIQUE(client_id, api_key)
);

-- Table for API quota usage tracking
CREATE TABLE IF NOT EXISTS api_quota_usage (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    endpoint VARCHAR(500),
    daily_limit BIGINT NOT NULL,
    monthly_limit BIGINT NOT NULL,
    daily_used BIGINT DEFAULT 0,
    monthly_used BIGINT DEFAULT 0,
    last_reset_date DATE,
    last_reset_month VARCHAR(7), -- YYYY-MM format
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(client_id, api_key, endpoint)
);

-- Table for rate limit violations
CREATE TABLE IF NOT EXISTS rate_limit_violations (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    api_key VARCHAR(255),
    endpoint VARCHAR(500),
    ip_address VARCHAR(45),
    violation_type VARCHAR(50), -- RATE_LIMIT, QUOTA_EXCEEDED, BURST_EXCEEDED
    violation_count INT DEFAULT 1,
    blocked_until TIMESTAMP,
    reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table for dynamic rate limit adjustments
CREATE TABLE IF NOT EXISTS rate_limit_adjustments (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    endpoint VARCHAR(500),
    adjustment_type VARCHAR(50), -- MULTIPLIER, ABSOLUTE, PERCENTAGE
    adjustment_value DECIMAL(10,2) NOT NULL,
    original_limit BIGINT,
    adjusted_limit BIGINT,
    reason TEXT,
    expires_at TIMESTAMP NOT NULL,
    applied_by VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table for rate limit metrics
CREATE TABLE IF NOT EXISTS rate_limit_metrics (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    endpoint VARCHAR(500),
    tier VARCHAR(50),
    time_window TIMESTAMP NOT NULL,
    total_requests BIGINT DEFAULT 0,
    allowed_requests BIGINT DEFAULT 0,
    denied_requests BIGINT DEFAULT 0,
    average_response_time_ms DECIMAL(10,2),
    p95_response_time_ms DECIMAL(10,2),
    p99_response_time_ms DECIMAL(10,2),
    error_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table for API tier definitions
CREATE TABLE IF NOT EXISTS api_tiers (
    id VARCHAR(36) PRIMARY KEY,
    tier_name VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100),
    description TEXT,
    requests_per_minute INT NOT NULL,
    requests_per_hour INT NOT NULL,
    requests_per_day INT NOT NULL,
    requests_per_month INT NOT NULL,
    burst_capacity INT DEFAULT 10,
    concurrent_requests INT DEFAULT 10,
    features JSONB, -- Additional features like priority support, SLA, etc.
    price_per_month DECIMAL(10,2),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table for client tier assignments
CREATE TABLE IF NOT EXISTS client_tier_assignments (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    tier_id VARCHAR(36) NOT NULL REFERENCES api_tiers(id),
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP,
    assigned_by VARCHAR(255),
    reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tier_id) REFERENCES api_tiers(id) ON DELETE RESTRICT
);

-- Table for sliding window rate limit tracking
CREATE TABLE IF NOT EXISTS sliding_window_requests (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    endpoint VARCHAR(500),
    request_timestamp TIMESTAMP NOT NULL,
    window_id VARCHAR(100), -- Identifier for the sliding window
    request_metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Table for rate limit whitelists
CREATE TABLE IF NOT EXISTS rate_limit_whitelists (
    id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(255),
    ip_address VARCHAR(45),
    api_key VARCHAR(255),
    whitelist_type VARCHAR(50), -- CLIENT, IP, API_KEY
    reason TEXT,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_rate_limit_config_client ON rate_limit_configurations(client_id);
CREATE INDEX idx_rate_limit_config_tier ON rate_limit_configurations(tier);
CREATE INDEX idx_api_quota_client ON api_quota_usage(client_id, api_key);
CREATE INDEX idx_violations_client ON rate_limit_violations(client_id, created_at);
CREATE INDEX idx_violations_ip ON rate_limit_violations(ip_address, created_at);
CREATE INDEX idx_adjustments_client ON rate_limit_adjustments(client_id, is_active);
CREATE INDEX idx_adjustments_expires ON rate_limit_adjustments(expires_at);
CREATE INDEX idx_metrics_client_window ON rate_limit_metrics(client_id, time_window);
CREATE INDEX idx_tier_assignments_client ON client_tier_assignments(client_id, effective_from);
CREATE INDEX idx_sliding_window_client ON sliding_window_requests(client_id, request_timestamp);
CREATE INDEX idx_whitelist_active ON rate_limit_whitelists(is_active, whitelist_type);

-- Insert default tier definitions
INSERT INTO api_tiers (id, tier_name, display_name, description, 
    requests_per_minute, requests_per_hour, requests_per_day, requests_per_month, 
    burst_capacity, concurrent_requests, features, price_per_month) 
VALUES 
    (gen_random_uuid()::text, 'FREE', 'Free Tier', 'Basic API access for development and testing', 
     10, 100, 1000, 10000, 5, 2, 
     '{"support": "community", "sla": "none", "priority": 0}'::jsonb, 0.00),
    
    (gen_random_uuid()::text, 'BASIC', 'Basic Tier', 'Standard API access for small applications', 
     60, 1000, 10000, 100000, 10, 5, 
     '{"support": "email", "sla": "99%", "priority": 1}'::jsonb, 29.99),
    
    (gen_random_uuid()::text, 'PREMIUM', 'Premium Tier', 'Enhanced API access for production applications', 
     300, 5000, 50000, 500000, 50, 20, 
     '{"support": "priority", "sla": "99.9%", "priority": 2}'::jsonb, 99.99),
    
    (gen_random_uuid()::text, 'ENTERPRISE', 'Enterprise Tier', 'Unlimited API access with dedicated support', 
     1000, 20000, 200000, 2000000, 100, 50, 
     '{"support": "dedicated", "sla": "99.99%", "priority": 3}'::jsonb, 499.99);

-- Function to clean up old sliding window entries
CREATE OR REPLACE FUNCTION cleanup_sliding_window_requests() 
RETURNS void AS $$
BEGIN
    DELETE FROM sliding_window_requests 
    WHERE request_timestamp < NOW() - INTERVAL '1 hour';
END;
$$ LANGUAGE plpgsql;

-- Function to reset daily quotas
CREATE OR REPLACE FUNCTION reset_daily_quotas() 
RETURNS void AS $$
BEGIN
    UPDATE api_quota_usage 
    SET daily_used = 0, 
        last_reset_date = CURRENT_DATE,
        updated_at = CURRENT_TIMESTAMP
    WHERE last_reset_date < CURRENT_DATE;
END;
$$ LANGUAGE plpgsql;

-- Function to reset monthly quotas
CREATE OR REPLACE FUNCTION reset_monthly_quotas() 
RETURNS void AS $$
BEGIN
    UPDATE api_quota_usage 
    SET monthly_used = 0, 
        daily_used = 0,
        last_reset_month = TO_CHAR(CURRENT_DATE, 'YYYY-MM'),
        updated_at = CURRENT_TIMESTAMP
    WHERE last_reset_month < TO_CHAR(CURRENT_DATE, 'YYYY-MM');
END;
$$ LANGUAGE plpgsql;