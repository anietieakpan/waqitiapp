-- IP Whitelisting Implementation Tables
-- For managing allowed IP addresses and ranges

-- IP Whitelist entries
CREATE TABLE IF NOT EXISTS ip_whitelist (
    id BIGSERIAL PRIMARY KEY,
    ip_address INET NOT NULL,
    ip_range_start INET,
    ip_range_end INET,
    description VARCHAR(255),
    entity_type VARCHAR(50) NOT NULL, -- USER, SERVICE, ADMIN, API_CLIENT
    entity_id VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    
    CONSTRAINT chk_entity_type CHECK (entity_type IN ('USER', 'SERVICE', 'ADMIN', 'API_CLIENT', 'GLOBAL'))
);

-- IP Access logs for audit
CREATE TABLE IF NOT EXISTS ip_access_logs (
    id BIGSERIAL PRIMARY KEY,
    ip_address INET NOT NULL,
    user_id VARCHAR(255),
    access_type VARCHAR(50) NOT NULL, -- ALLOWED, BLOCKED, SUSPICIOUS
    endpoint VARCHAR(500),
    http_method VARCHAR(10),
    user_agent TEXT,
    country_code VARCHAR(2),
    city VARCHAR(100),
    is_whitelisted BOOLEAN DEFAULT FALSE,
    access_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_access_type CHECK (access_type IN ('ALLOWED', 'BLOCKED', 'SUSPICIOUS'))
);

-- Blocked IP addresses (blacklist)
CREATE TABLE IF NOT EXISTS ip_blacklist (
    id BIGSERIAL PRIMARY KEY,
    ip_address INET NOT NULL UNIQUE,
    reason VARCHAR(500) NOT NULL,
    blocked_until TIMESTAMP,
    permanent_block BOOLEAN DEFAULT FALSE,
    threat_level VARCHAR(20), -- LOW, MEDIUM, HIGH, CRITICAL
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT chk_threat_level CHECK (threat_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- IP-based rate limiting rules
CREATE TABLE IF NOT EXISTS ip_rate_limits (
    id BIGSERIAL PRIMARY KEY,
    ip_pattern VARCHAR(100) NOT NULL, -- Can be CIDR notation
    max_requests_per_minute INT NOT NULL,
    max_requests_per_hour INT NOT NULL,
    max_requests_per_day INT NOT NULL,
    applies_to VARCHAR(50) NOT NULL, -- ALL, API, WEB, MOBILE
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_ip_whitelist_address ON ip_whitelist(ip_address) WHERE is_active = TRUE;
CREATE INDEX idx_ip_whitelist_range ON ip_whitelist(ip_range_start, ip_range_end) WHERE is_active = TRUE;
CREATE INDEX idx_ip_whitelist_entity ON ip_whitelist(entity_type, entity_id) WHERE is_active = TRUE;
CREATE INDEX idx_ip_whitelist_expires ON ip_whitelist(expires_at) WHERE expires_at IS NOT NULL;

CREATE INDEX idx_ip_access_logs_address ON ip_access_logs(ip_address);
CREATE INDEX idx_ip_access_logs_user ON ip_access_logs(user_id);
CREATE INDEX idx_ip_access_logs_timestamp ON ip_access_logs(access_timestamp);
CREATE INDEX idx_ip_access_logs_type ON ip_access_logs(access_type);

CREATE INDEX idx_ip_blacklist_address ON ip_blacklist(ip_address);
CREATE INDEX idx_ip_blacklist_blocked_until ON ip_blacklist(blocked_until) WHERE permanent_block = FALSE;

-- Function to check if IP is in range
CREATE OR REPLACE FUNCTION is_ip_in_whitelist(check_ip INET)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM ip_whitelist
        WHERE is_active = TRUE
        AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
        AND (
            ip_address = check_ip
            OR (ip_range_start IS NOT NULL AND ip_range_end IS NOT NULL 
                AND check_ip >= ip_range_start AND check_ip <= ip_range_end)
        )
    );
END;
$$ LANGUAGE plpgsql;

-- Function to check if IP is blacklisted
CREATE OR REPLACE FUNCTION is_ip_blacklisted(check_ip INET)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM ip_blacklist
        WHERE ip_address = check_ip
        AND (permanent_block = TRUE OR blocked_until > CURRENT_TIMESTAMP)
    );
END;
$$ LANGUAGE plpgsql;

-- Trigger to update updated_at
CREATE OR REPLACE FUNCTION update_ip_whitelist_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_ip_whitelist_updated_at
    BEFORE UPDATE ON ip_whitelist
    FOR EACH ROW
    EXECUTE FUNCTION update_ip_whitelist_updated_at();

-- Comments for documentation
COMMENT ON TABLE ip_whitelist IS 'Stores whitelisted IP addresses and ranges for access control';
COMMENT ON TABLE ip_access_logs IS 'Audit log of all IP-based access attempts';
COMMENT ON TABLE ip_blacklist IS 'Blocked IP addresses with threat information';
COMMENT ON TABLE ip_rate_limits IS 'IP-based rate limiting rules';
COMMENT ON FUNCTION is_ip_in_whitelist IS 'Checks if an IP address is whitelisted';
COMMENT ON FUNCTION is_ip_blacklisted IS 'Checks if an IP address is blacklisted';