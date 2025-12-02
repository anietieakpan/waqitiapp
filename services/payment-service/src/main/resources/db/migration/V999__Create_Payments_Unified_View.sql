-- =====================================================================
-- PAYMENTS UNIFIED VIEW FOR AUTHORIZATION
-- =====================================================================
-- This view consolidates payment data from multiple tables to provide
-- a unified interface for authorization checks via ResourceOwnershipService.
--
-- Purpose: Enable payment access validation across all payment types
-- Created: October 11, 2025
-- Version: 1.0
-- =====================================================================

-- Drop existing view if it exists
DROP VIEW IF EXISTS payments CASCADE;

-- Create unified payments view
CREATE OR REPLACE VIEW payments AS
-- Payment Requests (user-to-user payment requests)
SELECT
    pr.id,
    pr.requestor_id AS sender_id,
    pr.recipient_id,
    pr.amount,
    pr.currency,
    pr.status,
    pr.description,
    pr.reference_number AS external_reference,
    pr.transaction_id,
    pr.created_at,
    pr.updated_at,
    'PAYMENT_REQUEST'::VARCHAR(50) AS payment_type,
    pr.expiry_date AS expires_at,
    NULL::UUID AS source_wallet_id,
    NULL::VARCHAR(20) AS frequency
FROM payment_requests pr

UNION ALL

-- Scheduled Payments (recurring payments)
SELECT
    sp.id,
    sp.sender_id,
    sp.recipient_id,
    sp.amount,
    sp.currency,
    sp.status,
    sp.description,
    NULL AS external_reference,
    NULL AS transaction_id,
    sp.created_at,
    sp.updated_at,
    'SCHEDULED_PAYMENT'::VARCHAR(50) AS payment_type,
    sp.end_date::TIMESTAMP AS expires_at,
    sp.source_wallet_id,
    sp.frequency
FROM scheduled_payments sp

UNION ALL

-- Split Payments (group expense splitting)
SELECT
    spl.id,
    spl.organizer_id AS sender_id,
    NULL::UUID AS recipient_id,  -- Split payments have multiple recipients
    spl.total_amount AS amount,
    spl.currency,
    spl.status,
    spl.description,
    NULL AS external_reference,
    NULL AS transaction_id,
    spl.created_at,
    spl.updated_at,
    'SPLIT_PAYMENT'::VARCHAR(50) AS payment_type,
    spl.expiry_date AS expires_at,
    NULL::UUID AS source_wallet_id,
    NULL::VARCHAR(20) AS frequency
FROM split_payments spl;

-- =====================================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================================

-- Note: Views cannot have direct indexes, but indexes on underlying tables
-- will be used by the query planner. Ensure these indexes exist:

-- Already exist from V1__create_payment_tables.sql:
-- - idx_payment_requests_requestor_id
-- - idx_payment_requests_recipient_id
-- - idx_payment_requests_status
-- - idx_scheduled_payments_sender_id
-- - idx_scheduled_payments_recipient_id
-- - idx_scheduled_payments_status
-- - idx_split_payments_organizer_id
-- - idx_split_payments_status

-- =====================================================================
-- AUTHORIZATION HELPER FUNCTION
-- =====================================================================

-- Function to check if a user has access to a payment
CREATE OR REPLACE FUNCTION check_payment_access(
    p_user_id UUID,
    p_payment_id UUID
) RETURNS BOOLEAN AS $$
DECLARE
    has_access BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM payments
        WHERE id = p_payment_id
        AND (sender_id = p_user_id OR recipient_id = p_user_id)
    ) INTO has_access;

    -- For split payments, also check if user is a participant
    IF NOT has_access THEN
        SELECT EXISTS (
            SELECT 1 FROM split_payment_participants spp
            WHERE spp.split_payment_id = p_payment_id
            AND spp.user_id = p_user_id
        ) INTO has_access;
    END IF;

    RETURN has_access;
END;
$$ LANGUAGE plpgsql STABLE;

-- =====================================================================
-- PAYMENT ACCESS MATERIALIZED VIEW (OPTIONAL - FOR PERFORMANCE)
-- =====================================================================

-- Materialized view for faster authorization checks
-- Refresh this periodically (e.g., every 5 minutes) for near-real-time data
CREATE MATERIALIZED VIEW IF NOT EXISTS payment_access_cache AS
SELECT
    p.id AS payment_id,
    p.sender_id AS user_id,
    'SENDER'::VARCHAR(20) AS access_type,
    p.payment_type,
    p.status,
    p.created_at
FROM payments p
WHERE p.sender_id IS NOT NULL

UNION ALL

SELECT
    p.id AS payment_id,
    p.recipient_id AS user_id,
    'RECIPIENT'::VARCHAR(20) AS access_type,
    p.payment_type,
    p.status,
    p.created_at
FROM payments p
WHERE p.recipient_id IS NOT NULL

UNION ALL

SELECT
    spp.split_payment_id AS payment_id,
    spp.user_id,
    'PARTICIPANT'::VARCHAR(20) AS access_type,
    'SPLIT_PAYMENT'::VARCHAR(50) AS payment_type,
    spl.status,
    spp.created_at
FROM split_payment_participants spp
JOIN split_payments spl ON spp.split_payment_id = spl.id;

-- Create indexes on the materialized view
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_access_cache_pk
ON payment_access_cache(payment_id, user_id, access_type);

CREATE INDEX IF NOT EXISTS idx_payment_access_cache_user
ON payment_access_cache(user_id);

CREATE INDEX IF NOT EXISTS idx_payment_access_cache_payment
ON payment_access_cache(payment_id);

CREATE INDEX IF NOT EXISTS idx_payment_access_cache_status
ON payment_access_cache(status);

-- =====================================================================
-- REFRESH FUNCTION FOR MATERIALIZED VIEW
-- =====================================================================

CREATE OR REPLACE FUNCTION refresh_payment_access_cache()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY payment_access_cache;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- VALIDATION QUERIES (FOR TESTING)
-- =====================================================================

-- Test query 1: Get all payments for a user
-- SELECT * FROM payments WHERE sender_id = 'user-uuid' OR recipient_id = 'user-uuid';

-- Test query 2: Check if user can access a specific payment
-- SELECT check_payment_access('user-uuid', 'payment-uuid');

-- Test query 3: Get payment access info from cache
-- SELECT * FROM payment_access_cache WHERE user_id = 'user-uuid';

-- Test query 4: Count payments by type
-- SELECT payment_type, COUNT(*) FROM payments GROUP BY payment_type;

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON VIEW payments IS 'Unified view of all payment types for authorization and reporting. Combines payment_requests, scheduled_payments, and split_payments into a single queryable interface.';

COMMENT ON FUNCTION check_payment_access(UUID, UUID) IS 'Checks if a user has access to a specific payment (as sender, recipient, or participant)';

COMMENT ON MATERIALIZED VIEW payment_access_cache IS 'Cached payment access relationships for faster authorization checks. Refresh periodically using refresh_payment_access_cache()';

-- =====================================================================
-- USAGE NOTES
-- =====================================================================

-- 1. The payments view provides a unified interface for all payment types
-- 2. Use check_payment_access() function for quick authorization checks
-- 3. The payment_access_cache materialized view provides faster lookups
-- 4. Refresh the cache periodically (recommended: every 5 minutes)
--    - Manual: SELECT refresh_payment_access_cache();
--    - Automated: Set up a cron job or scheduler
-- 5. For real-time data, query the payments view directly
-- 6. For high-performance auth checks, use payment_access_cache

-- =====================================================================
-- PERFORMANCE CHARACTERISTICS
-- =====================================================================

-- payments VIEW:
--   - Real-time data (no caching)
--   - Slightly slower due to UNION ALL operations
--   - Best for: Real-time queries, data accuracy, administrative tasks
--   - Expected latency: 10-50ms depending on data volume

-- payment_access_cache MATERIALIZED VIEW:
--   - Cached data (up to 5 minutes stale)
--   - Very fast lookups via direct indexes
--   - Best for: High-frequency authorization checks, API endpoints
--   - Expected latency: <5ms for indexed lookups

-- RECOMMENDATION:
--   - Use payment_access_cache for ResourceOwnershipService queries
--   - Use payments view for admin dashboards and reporting
--   - Refresh cache every 5 minutes for good balance of performance/freshness
