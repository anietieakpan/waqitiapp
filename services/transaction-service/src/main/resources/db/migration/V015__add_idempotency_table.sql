-- Transaction Service - Add Idempotency Table
-- Migration: V015
-- Created: 2025-10-11
-- Purpose: Prevent duplicate transaction processing with idempotency keys
-- Impact: Critical for financial integrity - prevents duplicate charges

-- =============================================================================
-- PART 1: Create idempotency keys table
-- =============================================================================

CREATE TABLE IF NOT EXISTS transaction_idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    request_hash VARCHAR(64) NOT NULL, -- SHA-256 hash of request payload
    transaction_id UUID, -- Set after successful processing
    request_method VARCHAR(10) NOT NULL, -- POST, PUT, etc.
    request_path VARCHAR(500) NOT NULL,
    request_payload JSONB,
    response_status_code INT,
    response_payload JSONB,
    client_id UUID, -- API client or user who made the request
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    processing_status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    -- PENDING: Initial state
    -- PROCESSING: Currently being processed
    -- COMPLETED: Successfully processed
    -- FAILED: Processing failed
    -- EXPIRED: Idempotency key expired
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL, -- Typically 24 hours from creation
    attempt_count INT DEFAULT 1 NOT NULL,
    error_message TEXT,
    lock_acquired_at TIMESTAMP,
    lock_owner VARCHAR(255), -- Instance ID that acquired the lock
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_idempotency_key ON transaction_idempotency_keys(idempotency_key);
CREATE INDEX idx_idempotency_transaction ON transaction_idempotency_keys(transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX idx_idempotency_client ON transaction_idempotency_keys(client_id);
CREATE INDEX idx_idempotency_status ON transaction_idempotency_keys(processing_status);
CREATE INDEX idx_idempotency_expires ON transaction_idempotency_keys(expires_at);
CREATE INDEX idx_idempotency_created ON transaction_idempotency_keys(created_at DESC);
CREATE INDEX idx_idempotency_hash ON transaction_idempotency_keys(request_hash);

-- Partial index for active (non-expired) keys
CREATE INDEX idx_idempotency_active ON transaction_idempotency_keys(idempotency_key, processing_status)
WHERE expires_at > CURRENT_TIMESTAMP;

COMMENT ON TABLE transaction_idempotency_keys IS 'Stores idempotency keys to prevent duplicate transaction processing';
COMMENT ON COLUMN transaction_idempotency_keys.idempotency_key IS 'Unique key provided by client (UUID or custom string)';
COMMENT ON COLUMN transaction_idempotency_keys.request_hash IS 'SHA-256 hash of request body for conflict detection';
COMMENT ON COLUMN transaction_idempotency_keys.expires_at IS 'Idempotency guarantee expires after this time (typically 24 hours)';

-- =============================================================================
-- PART 2: Create duplicate transaction attempts log
-- =============================================================================

CREATE TABLE IF NOT EXISTS transaction_duplicate_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    original_transaction_id UUID,
    client_id UUID,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_payload JSONB,
    conflict_type VARCHAR(50) NOT NULL,
    -- EXACT_MATCH: Same request, return original response
    -- HASH_MISMATCH: Same key, different payload (error)
    -- RETRY_SUCCESS: Successful retry of failed request
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_duplicate_idempotency_key
        FOREIGN KEY (idempotency_key_id)
        REFERENCES transaction_idempotency_keys(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_duplicate_attempts_key_id ON transaction_duplicate_attempts(idempotency_key_id);
CREATE INDEX idx_duplicate_attempts_key ON transaction_duplicate_attempts(idempotency_key);
CREATE INDEX idx_duplicate_attempts_transaction ON transaction_duplicate_attempts(original_transaction_id);
CREATE INDEX idx_duplicate_attempts_created ON transaction_duplicate_attempts(attempted_at DESC);

COMMENT ON TABLE transaction_duplicate_attempts IS 'Audit log of duplicate transaction attempts detected via idempotency keys';

-- =============================================================================
-- PART 3: Create idempotency check function
-- =============================================================================

CREATE OR REPLACE FUNCTION check_idempotency_key(
    p_idempotency_key VARCHAR(255),
    p_request_hash VARCHAR(64),
    p_client_id UUID,
    p_request_method VARCHAR(10),
    p_request_path VARCHAR(500),
    p_request_payload JSONB DEFAULT NULL,
    p_ip_address VARCHAR(45) DEFAULT NULL,
    p_user_agent VARCHAR(500) DEFAULT NULL,
    p_ttl_hours INT DEFAULT 24
)
RETURNS TABLE(
    status VARCHAR(50),
    existing_transaction_id UUID,
    response_payload JSONB,
    response_status_code INT,
    message TEXT
) AS $$
DECLARE
    existing_record RECORD;
    new_id UUID;
    lock_timeout INTERVAL := '5 seconds'::INTERVAL;
BEGIN
    -- Try to acquire advisory lock on idempotency key
    -- This prevents race conditions during check
    IF NOT pg_try_advisory_xact_lock(hashtext(p_idempotency_key)) THEN
        RETURN QUERY SELECT
            'LOCKED'::VARCHAR,
            NULL::UUID,
            NULL::JSONB,
            NULL::INT,
            'Idempotency key is currently being processed by another request'::TEXT;
        RETURN;
    END IF;

    -- Check for existing idempotency key
    SELECT * INTO existing_record
    FROM transaction_idempotency_keys
    WHERE idempotency_key = p_idempotency_key
    AND expires_at > CURRENT_TIMESTAMP;

    IF FOUND THEN
        -- Idempotency key exists
        IF existing_record.processing_status = 'COMPLETED' THEN
            -- Check if request hash matches (conflict detection)
            IF existing_record.request_hash = p_request_hash THEN
                -- Exact match - return cached response
                UPDATE transaction_idempotency_keys
                SET last_seen_at = CURRENT_TIMESTAMP,
                    attempt_count = attempt_count + 1
                WHERE id = existing_record.id;

                -- Log duplicate attempt
                INSERT INTO transaction_duplicate_attempts (
                    idempotency_key_id, idempotency_key, request_hash,
                    original_transaction_id, client_id, ip_address,
                    user_agent, request_payload, conflict_type
                ) VALUES (
                    existing_record.id, p_idempotency_key, p_request_hash,
                    existing_record.transaction_id, p_client_id, p_ip_address,
                    p_user_agent, p_request_payload, 'EXACT_MATCH'
                );

                RETURN QUERY SELECT
                    'DUPLICATE'::VARCHAR,
                    existing_record.transaction_id,
                    existing_record.response_payload,
                    existing_record.response_status_code,
                    'Request already processed. Returning cached response.'::TEXT;
                RETURN;
            ELSE
                -- Hash mismatch - same key, different request
                INSERT INTO transaction_duplicate_attempts (
                    idempotency_key_id, idempotency_key, request_hash,
                    original_transaction_id, client_id, ip_address,
                    user_agent, request_payload, conflict_type
                ) VALUES (
                    existing_record.id, p_idempotency_key, p_request_hash,
                    existing_record.transaction_id, p_client_id, p_ip_address,
                    p_user_agent, p_request_payload, 'HASH_MISMATCH'
                );

                RETURN QUERY SELECT
                    'CONFLICT'::VARCHAR,
                    NULL::UUID,
                    NULL::JSONB,
                    409::INT,
                    'Idempotency key reused with different request payload. This is not allowed.'::TEXT;
                RETURN;
            END IF;
        ELSIF existing_record.processing_status = 'PROCESSING' THEN
            -- Request is currently being processed
            RETURN QUERY SELECT
                'PROCESSING'::VARCHAR,
                NULL::UUID,
                NULL::JSONB,
                NULL::INT,
                'Request is currently being processed. Please wait.'::TEXT;
            RETURN;
        ELSIF existing_record.processing_status = 'FAILED' THEN
            -- Previous attempt failed - allow retry
            UPDATE transaction_idempotency_keys
            SET processing_status = 'PROCESSING',
                last_seen_at = CURRENT_TIMESTAMP,
                attempt_count = attempt_count + 1,
                lock_acquired_at = CURRENT_TIMESTAMP
            WHERE id = existing_record.id;

            RETURN QUERY SELECT
                'RETRY'::VARCHAR,
                NULL::UUID,
                NULL::JSONB,
                NULL::INT,
                'Previous attempt failed. Retrying...'::TEXT;
            RETURN;
        END IF;
    END IF;

    -- No existing record - create new idempotency key
    new_id := gen_random_uuid();

    INSERT INTO transaction_idempotency_keys (
        id, idempotency_key, request_hash, request_method, request_path,
        request_payload, client_id, ip_address, user_agent,
        processing_status, expires_at, lock_acquired_at
    ) VALUES (
        new_id, p_idempotency_key, p_request_hash, p_request_method,
        p_request_path, p_request_payload, p_client_id, p_ip_address,
        p_user_agent, 'PROCESSING',
        CURRENT_TIMESTAMP + (p_ttl_hours || ' hours')::INTERVAL,
        CURRENT_TIMESTAMP
    );

    RETURN QUERY SELECT
        'NEW'::VARCHAR,
        NULL::UUID,
        NULL::JSONB,
        NULL::INT,
        'New idempotency key created. Proceed with processing.'::TEXT;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION check_idempotency_key IS 'Checks idempotency key and returns status. Call before processing transaction.';

-- =============================================================================
-- PART 4: Create function to record successful transaction
-- =============================================================================

CREATE OR REPLACE FUNCTION complete_idempotency_key(
    p_idempotency_key VARCHAR(255),
    p_transaction_id UUID,
    p_response_status_code INT,
    p_response_payload JSONB
)
RETURNS void AS $$
BEGIN
    UPDATE transaction_idempotency_keys
    SET processing_status = 'COMPLETED',
        transaction_id = p_transaction_id,
        response_status_code = p_response_status_code,
        response_payload = p_response_payload,
        completed_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    WHERE idempotency_key = p_idempotency_key
    AND processing_status = 'PROCESSING';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Idempotency key % not found or not in PROCESSING state', p_idempotency_key;
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION complete_idempotency_key IS 'Marks idempotency key as completed after successful transaction processing';

-- =============================================================================
-- PART 5: Create function to record failed transaction
-- =============================================================================

CREATE OR REPLACE FUNCTION fail_idempotency_key(
    p_idempotency_key VARCHAR(255),
    p_error_message TEXT,
    p_response_status_code INT DEFAULT 500,
    p_response_payload JSONB DEFAULT NULL
)
RETURNS void AS $$
BEGIN
    UPDATE transaction_idempotency_keys
    SET processing_status = 'FAILED',
        error_message = p_error_message,
        response_status_code = p_response_status_code,
        response_payload = p_response_payload,
        updated_at = CURRENT_TIMESTAMP
    WHERE idempotency_key = p_idempotency_key
    AND processing_status = 'PROCESSING';

    IF NOT FOUND THEN
        RAISE WARNING 'Idempotency key % not found or not in PROCESSING state', p_idempotency_key;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- PART 6: Create cleanup function for expired keys
-- =============================================================================

CREATE OR REPLACE FUNCTION cleanup_expired_idempotency_keys()
RETURNS INT AS $$
DECLARE
    deleted_count INT;
BEGIN
    -- Delete expired idempotency keys (older than 7 days by default)
    WITH deleted AS (
        DELETE FROM transaction_idempotency_keys
        WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '7 days'
        RETURNING *
    )
    SELECT COUNT(*) INTO deleted_count FROM deleted;

    RAISE NOTICE 'Deleted % expired idempotency keys', deleted_count;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_idempotency_keys IS 'Removes expired idempotency keys. Run daily via cron.';

-- Schedule via cron (execute by ops):
-- SELECT cron.schedule('cleanup-idempotency', '0 2 * * *', 'SELECT cleanup_expired_idempotency_keys()');

-- =============================================================================
-- PART 7: Create trigger for updated_at
-- =============================================================================

CREATE OR REPLACE FUNCTION update_idempotency_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_idempotency_updated_at
    BEFORE UPDATE ON transaction_idempotency_keys
    FOR EACH ROW
    EXECUTE FUNCTION update_idempotency_updated_at();

-- =============================================================================
-- PART 8: Add idempotency_key column to transactions table
-- =============================================================================

-- Add column to main transactions table for reference
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'transactions') THEN
        ALTER TABLE transactions
            ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);

        CREATE INDEX IF NOT EXISTS idx_transactions_idempotency_key
            ON transactions(idempotency_key)
            WHERE idempotency_key IS NOT NULL;

        COMMENT ON COLUMN transactions.idempotency_key IS 'Client-provided idempotency key for duplicate prevention';

        RAISE NOTICE 'Idempotency key column added to transactions table';
    END IF;
END $$;

-- =============================================================================
-- VERIFICATION QUERIES
-- =============================================================================

-- Check idempotency table created
-- SELECT table_name FROM information_schema.tables
-- WHERE table_name = 'transaction_idempotency_keys';

-- Test idempotency key check (example)
-- SELECT * FROM check_idempotency_key(
--     'test-key-12345',
--     'abcdef1234567890', -- SHA-256 hash
--     '550e8400-e29b-41d4-a716-446655440000'::UUID, -- client_id
--     'POST',
--     '/api/v1/transactions',
--     '{"amount": 100, "currency": "USD"}'::JSONB
-- );

-- View statistics
-- SELECT
--     processing_status,
--     COUNT(*) as count,
--     AVG(attempt_count) as avg_attempts
-- FROM transaction_idempotency_keys
-- WHERE created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
-- GROUP BY processing_status;

-- Find duplicate attempts
-- SELECT
--     i.idempotency_key,
--     i.transaction_id,
--     COUNT(d.id) as duplicate_attempts
-- FROM transaction_idempotency_keys i
-- LEFT JOIN transaction_duplicate_attempts d ON i.id = d.idempotency_key_id
-- WHERE i.created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
-- GROUP BY i.id, i.idempotency_key, i.transaction_id
-- HAVING COUNT(d.id) > 0
-- ORDER BY COUNT(d.id) DESC;
