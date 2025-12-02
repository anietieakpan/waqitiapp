-- =====================================================================
-- Idempotency Keys Table Migration
-- Version: V100
-- Description: Creates idempotency_keys table for preventing duplicate
--              financial transactions
-- Author: Waqiti Platform Engineering
-- Date: 2025-10-16
-- =====================================================================

-- Create idempotency_keys table
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    request_body_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status_code INTEGER,
    response_body TEXT,
    response_headers TEXT,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    client_ip VARCHAR(45),
    user_agent VARCHAR(500),
    transaction_id VARCHAR(100),
    service_name VARCHAR(100) NOT NULL,

    -- Constraints
    CONSTRAINT ck_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_idempotency_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_idempotency_http_method CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE'))
);

-- Create indexes for performance
CREATE INDEX idx_idempotency_key ON idempotency_keys(idempotency_key);
CREATE INDEX idx_idempotency_user_id ON idempotency_keys(user_id);
CREATE INDEX idx_idempotency_created_at ON idempotency_keys(created_at);
CREATE INDEX idx_idempotency_status ON idempotency_keys(status);
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);
CREATE INDEX idx_idempotency_service_name ON idempotency_keys(service_name);

-- Create composite indexes for common queries
CREATE INDEX idx_idempotency_user_status ON idempotency_keys(user_id, status);
CREATE INDEX idx_idempotency_key_status ON idempotency_keys(idempotency_key, status);

-- Create partial index for active processing requests
CREATE INDEX idx_idempotency_processing ON idempotency_keys(user_id, status)
    WHERE status = 'PROCESSING';

-- Add comments for documentation
COMMENT ON TABLE idempotency_keys IS 'Stores idempotency keys to prevent duplicate financial transactions. Critical for PCI DSS compliance.';
COMMENT ON COLUMN idempotency_keys.idempotency_key IS 'Unique idempotency key provided by client (UUID recommended)';
COMMENT ON COLUMN idempotency_keys.request_body_hash IS 'SHA-256 hash of request body for validation';
COMMENT ON COLUMN idempotency_keys.status IS 'Processing status: PROCESSING, COMPLETED, FAILED, EXPIRED';
COMMENT ON COLUMN idempotency_keys.response_body IS 'Cached response body for replay on retry';
COMMENT ON COLUMN idempotency_keys.retry_count IS 'Number of times this request was retried';
COMMENT ON COLUMN idempotency_keys.expires_at IS 'Expiration timestamp (typically 24 hours from creation)';
COMMENT ON COLUMN idempotency_keys.transaction_id IS 'Associated financial transaction ID';

-- Grant permissions
GRANT SELECT, INSERT, UPDATE ON idempotency_keys TO waqiti_app_user;
GRANT USAGE, SELECT ON SEQUENCE idempotency_keys_id_seq TO waqiti_app_user;
