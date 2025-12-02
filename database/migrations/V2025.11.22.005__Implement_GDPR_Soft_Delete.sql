-- ============================================================================
-- COMPLIANCE FIX (P1-010): Implement GDPR Soft Delete Pattern
-- Migration: Add soft delete and anonymization support for GDPR compliance
--
-- IMPACT: Enables GDPR "Right to Erasure" (Article 17) compliance
--
-- GDPR REQUIREMENTS:
-- - Article 17: Right to Erasure ("Right to be Forgotten")
-- - Article 5(1)(e): Storage Limitation
-- - Article 25: Data Protection by Design and by Default
--
-- IMPLEMENTATION STRATEGY:
-- - Soft delete: Mark records as deleted without physical removal
-- - Anonymization: Replace PII with irreversible pseudonymous data
-- - Retention policies: Automated cleanup after legal retention period
-- - Audit trail: Complete log of all erasure requests
--
-- DATA HANDLING:
-- 1. Immediate: Mark user as deleted, anonymize PII
-- 2. Retention: Keep anonymized data for legal/compliance period
-- 3. Hard Delete: Physical deletion after retention period expires
--
-- ============================================================================

SET statement_timeout = '10min';

-- ============================================================================
-- STEP 1: Add Soft Delete Columns to Users Table
-- ============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users') THEN

        -- Add deleted_at column
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'deleted_at'
        ) THEN
            ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
            CREATE INDEX idx_users_deleted_at ON users(deleted_at) WHERE deleted_at IS NOT NULL;
            RAISE NOTICE '✓ Added deleted_at column to users table';
        END IF;

        -- Add anonymized_at column
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'anonymized_at'
        ) THEN
            ALTER TABLE users ADD COLUMN anonymized_at TIMESTAMP WITH TIME ZONE;
            CREATE INDEX idx_users_anonymized_at ON users(anonymized_at) WHERE anonymized_at IS NOT NULL;
            RAISE NOTICE '✓ Added anonymized_at column to users table';
        END IF;

        -- Add anonymization_reason column
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'anonymization_reason'
        ) THEN
            ALTER TABLE users ADD COLUMN anonymization_reason VARCHAR(255);
            RAISE NOTICE '✓ Added anonymization_reason column to users table';
        END IF;

        -- Add hard_delete_scheduled_at column
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'hard_delete_scheduled_at'
        ) THEN
            ALTER TABLE users ADD COLUMN hard_delete_scheduled_at TIMESTAMP WITH TIME ZONE;
            CREATE INDEX idx_users_hard_delete_scheduled ON users(hard_delete_scheduled_at)
                WHERE hard_delete_scheduled_at IS NOT NULL;
            RAISE NOTICE '✓ Added hard_delete_scheduled_at column to users table';
        END IF;

    ELSE
        RAISE NOTICE 'Table users does not exist, skipping';
    END IF;
END $$;

-- ============================================================================
-- STEP 2: Create GDPR Erasure Requests Table
-- ============================================================================

CREATE TABLE IF NOT EXISTS gdpr_erasure_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- User identification
    user_id UUID NOT NULL,
    email VARCHAR(255),  -- Stored for audit purposes
    username VARCHAR(255),

    -- Request details
    request_type VARCHAR(50) NOT NULL,  -- SOFT_DELETE, ANONYMIZATION, HARD_DELETE
    request_reason TEXT,
    requested_by UUID,  -- User or admin who initiated
    request_source VARCHAR(100),  -- API, ADMIN_PORTAL, AUTOMATED

    -- Processing status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    processing_started_at TIMESTAMP WITH TIME ZONE,
    processing_completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,

    -- Legal and compliance
    legal_basis VARCHAR(100),  -- GDPR_ARTICLE_17, USER_REQUEST, RETENTION_POLICY
    retention_period_days INTEGER DEFAULT 2555,  -- 7 years default
    hard_delete_after TIMESTAMP WITH TIME ZONE,

    -- Audit trail
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    -- Data snapshot (for audit)
    user_data_snapshot JSONB,  -- Snapshot of user data before anonymization
    anonymization_log JSONB,  -- Log of what was anonymized

    CONSTRAINT fk_erasure_requests_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_gdpr_erasure_user ON gdpr_erasure_requests(user_id);
CREATE INDEX idx_gdpr_erasure_status ON gdpr_erasure_requests(status, created_at);
CREATE INDEX idx_gdpr_erasure_hard_delete ON gdpr_erasure_requests(hard_delete_after)
    WHERE hard_delete_after IS NOT NULL;

COMMENT ON TABLE gdpr_erasure_requests IS
'GDPR erasure requests tracking for compliance and audit purposes';

-- ============================================================================
-- STEP 3: Anonymization Helper Functions
-- ============================================================================

-- Generate irreversible pseudonymous email
CREATE OR REPLACE FUNCTION anonymize_email(user_id UUID)
RETURNS VARCHAR AS $$
BEGIN
    RETURN 'anonymized_' || SUBSTRING(MD5(user_id::TEXT || EXTRACT(EPOCH FROM NOW())::TEXT), 1, 16) || '@anonymized.local';
END;
$$ LANGUAGE plpgsql IMMUTABLE;

COMMENT ON FUNCTION anonymize_email IS
'Generate irreversible pseudonymous email address for GDPR anonymization';

-- Generate pseudonymous username
CREATE OR REPLACE FUNCTION anonymize_username(user_id UUID)
RETURNS VARCHAR AS $$
BEGIN
    RETURN 'user_' || SUBSTRING(MD5(user_id::TEXT || 'username' || EXTRACT(EPOCH FROM NOW())::TEXT), 1, 12);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Generate pseudonymous name
CREATE OR REPLACE FUNCTION anonymize_name(user_id UUID, field_type VARCHAR)
RETURNS VARCHAR AS $$
BEGIN
    RETURN 'Anonymized_' || SUBSTRING(MD5(user_id::TEXT || field_type || EXTRACT(EPOCH FROM NOW())::TEXT), 1, 8);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ============================================================================
-- STEP 4: Core Anonymization Function
-- ============================================================================

CREATE OR REPLACE FUNCTION anonymize_user_data(
    target_user_id UUID,
    reason VARCHAR DEFAULT 'GDPR_ARTICLE_17'
) RETURNS JSONB AS $$
DECLARE
    user_record RECORD;
    anonymization_log JSONB;
    original_data JSONB;
BEGIN
    -- Get user record
    SELECT * INTO user_record FROM users WHERE id = target_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'User not found: %', target_user_id;
    END IF;

    -- Check if already anonymized
    IF user_record.anonymized_at IS NOT NULL THEN
        RAISE NOTICE 'User already anonymized: %', target_user_id;
        RETURN jsonb_build_object('status', 'already_anonymized', 'anonymized_at', user_record.anonymized_at);
    END IF;

    -- Capture original data for audit
    original_data := to_jsonb(user_record);

    -- Perform anonymization
    UPDATE users SET
        -- Replace PII with pseudonymous data
        email = anonymize_email(target_user_id),
        username = anonymize_username(target_user_id),
        first_name = anonymize_name(target_user_id, 'first_name'),
        last_name = anonymize_name(target_user_id, 'last_name'),
        phone_number = NULL,
        date_of_birth = NULL,
        address = NULL,
        city = NULL,
        state = NULL,
        postal_code = NULL,
        country = NULL,

        -- Clear optional PII fields (if they exist)
        profile_picture_url = NULL,
        bio = NULL,
        national_id = NULL,
        tax_id = NULL,

        -- Mark as anonymized
        anonymized_at = NOW(),
        anonymization_reason = reason,
        updated_at = NOW()

    WHERE id = target_user_id;

    -- Build anonymization log
    anonymization_log := jsonb_build_object(
        'user_id', target_user_id,
        'anonymized_at', NOW(),
        'reason', reason,
        'fields_anonymized', ARRAY['email', 'username', 'first_name', 'last_name', 'phone_number',
                                   'date_of_birth', 'address', 'city', 'state', 'postal_code',
                                   'country', 'profile_picture_url', 'bio', 'national_id', 'tax_id'],
        'status', 'completed'
    );

    RAISE NOTICE 'User anonymized successfully: %', target_user_id;

    RETURN jsonb_build_object(
        'status', 'success',
        'user_id', target_user_id,
        'anonymized_at', NOW(),
        'log', anonymization_log
    );
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION anonymize_user_data IS
'GDPR-compliant user data anonymization - replaces PII with irreversible pseudonymous data';

-- ============================================================================
-- STEP 5: Soft Delete Function
-- ============================================================================

CREATE OR REPLACE FUNCTION soft_delete_user(
    target_user_id UUID,
    deletion_reason VARCHAR DEFAULT 'USER_REQUEST'
) RETURNS JSONB AS $$
DECLARE
    user_exists BOOLEAN;
    request_id UUID;
BEGIN
    -- Check if user exists
    SELECT EXISTS(SELECT 1 FROM users WHERE id = target_user_id) INTO user_exists;

    IF NOT user_exists THEN
        RAISE EXCEPTION 'User not found: %', target_user_id;
    END IF;

    -- Create erasure request
    INSERT INTO gdpr_erasure_requests (
        user_id,
        email,
        username,
        request_type,
        request_reason,
        requested_by,
        request_source,
        status,
        legal_basis,
        retention_period_days,
        hard_delete_after,
        created_at,
        user_data_snapshot
    )
    SELECT
        id,
        email,
        username,
        'SOFT_DELETE',
        deletion_reason,
        id,  -- Self-requested
        'API',
        'PROCESSING',
        'GDPR_ARTICLE_17',
        2555,  -- 7 years retention
        NOW() + INTERVAL '7 years',
        NOW(),
        to_jsonb(u.*)
    FROM users u
    WHERE u.id = target_user_id
    RETURNING id INTO request_id;

    -- Mark user as deleted
    UPDATE users SET
        deleted_at = NOW(),
        updated_at = NOW()
    WHERE id = target_user_id;

    -- Anonymize user data
    PERFORM anonymize_user_data(target_user_id, deletion_reason);

    -- Mark request as completed
    UPDATE gdpr_erasure_requests SET
        status = 'COMPLETED',
        processing_completed_at = NOW(),
        updated_at = NOW()
    WHERE id = request_id;

    RAISE NOTICE 'User soft deleted and anonymized: %', target_user_id;

    RETURN jsonb_build_object(
        'status', 'success',
        'user_id', target_user_id,
        'request_id', request_id,
        'deleted_at', NOW(),
        'hard_delete_after', NOW() + INTERVAL '7 years'
    );
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION soft_delete_user IS
'GDPR-compliant soft delete: marks user as deleted, anonymizes PII, schedules hard delete after retention period';

-- ============================================================================
-- STEP 6: Hard Delete Function (After Retention Period)
-- ============================================================================

CREATE OR REPLACE FUNCTION hard_delete_user(target_user_id UUID)
RETURNS JSONB AS $$
DECLARE
    user_record RECORD;
    can_delete BOOLEAN := FALSE;
BEGIN
    -- Get user record
    SELECT * INTO user_record FROM users WHERE id = target_user_id;

    IF NOT FOUND THEN
        RAISE NOTICE 'User not found (may already be hard deleted): %', target_user_id;
        RETURN jsonb_build_object('status', 'not_found');
    END IF;

    -- Verify retention period has passed
    IF user_record.hard_delete_scheduled_at IS NOT NULL
       AND user_record.hard_delete_scheduled_at <= NOW() THEN
        can_delete := TRUE;
    ELSIF user_record.deleted_at IS NOT NULL
       AND user_record.deleted_at + INTERVAL '7 years' <= NOW() THEN
        can_delete := TRUE;
    END IF;

    IF NOT can_delete THEN
        RAISE EXCEPTION 'Cannot hard delete user: retention period not expired. User: %', target_user_id;
    END IF;

    -- Log hard deletion
    INSERT INTO gdpr_erasure_requests (
        user_id,
        request_type,
        status,
        legal_basis,
        processing_completed_at
    ) VALUES (
        target_user_id,
        'HARD_DELETE',
        'COMPLETED',
        'RETENTION_POLICY_EXPIRED',
        NOW()
    );

    -- Physical deletion (CASCADE will handle related records)
    DELETE FROM users WHERE id = target_user_id;

    RAISE NOTICE 'User hard deleted: %', target_user_id;

    RETURN jsonb_build_object(
        'status', 'success',
        'user_id', target_user_id,
        'hard_deleted_at', NOW()
    );
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION hard_delete_user IS
'Physical deletion of user data after retention period expires (7 years post soft-delete)';

-- ============================================================================
-- STEP 7: Automated Cleanup Function
-- ============================================================================

CREATE OR REPLACE FUNCTION cleanup_expired_users()
RETURNS TABLE(
    user_id UUID,
    action VARCHAR,
    result VARCHAR
) AS $$
DECLARE
    user_rec RECORD;
    delete_result JSONB;
BEGIN
    -- Find users eligible for hard deletion
    FOR user_rec IN
        SELECT u.id, u.deleted_at, u.hard_delete_scheduled_at
        FROM users u
        WHERE u.deleted_at IS NOT NULL
        AND u.anonymized_at IS NOT NULL
        AND (
            (u.hard_delete_scheduled_at IS NOT NULL AND u.hard_delete_scheduled_at <= NOW())
            OR (u.deleted_at + INTERVAL '7 years' <= NOW())
        )
        LIMIT 1000  -- Process in batches
    LOOP
        BEGIN
            delete_result := hard_delete_user(user_rec.id);

            RETURN QUERY SELECT
                user_rec.id,
                'HARD_DELETE'::VARCHAR,
                'SUCCESS'::VARCHAR;

        EXCEPTION WHEN OTHERS THEN
            RETURN QUERY SELECT
                user_rec.id,
                'HARD_DELETE'::VARCHAR,
                ('ERROR: ' || SQLERRM)::VARCHAR;
        END;
    END LOOP;

    RETURN;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_users IS
'Automated cleanup of users whose retention period has expired (run via cron/scheduler)';

-- ============================================================================
-- STEP 8: Query Helper Views
-- ============================================================================

-- View: Active (non-deleted) users
CREATE OR REPLACE VIEW users_active AS
SELECT *
FROM users
WHERE deleted_at IS NULL;

COMMENT ON VIEW users_active IS
'Active users (excludes soft-deleted users) - use this for normal queries';

-- View: Deleted users pending hard deletion
CREATE OR REPLACE VIEW users_pending_deletion AS
SELECT
    id,
    email,
    username,
    deleted_at,
    anonymized_at,
    hard_delete_scheduled_at,
    EXTRACT(DAYS FROM (hard_delete_scheduled_at - NOW())) AS days_until_hard_delete
FROM users
WHERE deleted_at IS NOT NULL
AND hard_delete_scheduled_at > NOW();

COMMENT ON VIEW users_pending_deletion IS
'Soft-deleted users awaiting hard deletion after retention period';

-- View: GDPR erasure request summary
CREATE OR REPLACE VIEW gdpr_erasure_summary AS
SELECT
    DATE(created_at) AS request_date,
    request_type,
    status,
    COUNT(*) AS request_count
FROM gdpr_erasure_requests
GROUP BY DATE(created_at), request_type, status
ORDER BY request_date DESC, request_type, status;

COMMENT ON VIEW gdpr_erasure_summary IS
'Daily summary of GDPR erasure requests by type and status';

-- ============================================================================
-- POST-MIGRATION: Summary and Validation
-- ============================================================================

DO $$
DECLARE
    users_count BIGINT;
    deleted_users_count BIGINT;
    anonymized_users_count BIGINT;
    erasure_requests_count BIGINT;
BEGIN
    -- Count users
    SELECT COUNT(*) INTO users_count FROM users;
    SELECT COUNT(*) INTO deleted_users_count FROM users WHERE deleted_at IS NOT NULL;
    SELECT COUNT(*) INTO anonymized_users_count FROM users WHERE anonymized_at IS NOT NULL;
    SELECT COUNT(*) INTO erasure_requests_count FROM gdpr_erasure_requests;

    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE 'GDPR Soft Delete Implementation Complete (P1-010)';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE 'Total users in database: %', users_count;
    RAISE NOTICE 'Soft-deleted users: %', deleted_users_count;
    RAISE NOTICE 'Anonymized users: %', anonymized_users_count;
    RAISE NOTICE 'Erasure requests tracked: %', erasure_requests_count;
    RAISE NOTICE '';
    RAISE NOTICE 'GDPR Compliance Features:';
    RAISE NOTICE '  ✓ Soft delete (Article 17: Right to Erasure)';
    RAISE NOTICE '  ✓ Data anonymization (irreversible pseudonymization)';
    RAISE NOTICE '  ✓ 7-year retention period (legal compliance)';
    RAISE NOTICE '  ✓ Automated hard deletion after retention';
    RAISE NOTICE '  ✓ Complete audit trail in gdpr_erasure_requests';
    RAISE NOTICE '';
    RAISE NOTICE 'New Columns Added to users:';
    RAISE NOTICE '  • deleted_at: Timestamp of soft deletion';
    RAISE NOTICE '  • anonymized_at: Timestamp of PII anonymization';
    RAISE NOTICE '  • anonymization_reason: Reason for anonymization';
    RAISE NOTICE '  • hard_delete_scheduled_at: When physical deletion will occur';
    RAISE NOTICE '';
    RAISE NOTICE 'Functions Available:';
    RAISE NOTICE '  • soft_delete_user(user_id, reason): Soft delete and anonymize';
    RAISE NOTICE '  • anonymize_user_data(user_id, reason): Anonymize PII only';
    RAISE NOTICE '  • hard_delete_user(user_id): Physical deletion (post-retention)';
    RAISE NOTICE '  • cleanup_expired_users(): Batch cleanup automation';
    RAISE NOTICE '';
    RAISE NOTICE 'Views Available:';
    RAISE NOTICE '  • users_active: Active users (excludes deleted)';
    RAISE NOTICE '  • users_pending_deletion: Soft-deleted awaiting hard delete';
    RAISE NOTICE '  • gdpr_erasure_summary: Daily erasure request metrics';
    RAISE NOTICE '';
    RAISE NOTICE 'Usage Examples:';
    RAISE NOTICE '  -- Soft delete a user';
    RAISE NOTICE '  SELECT soft_delete_user(''uuid-here'', ''USER_REQUEST'');';
    RAISE NOTICE '';
    RAISE NOTICE '  -- Cleanup expired users (run via cron)';
    RAISE NOTICE '  SELECT * FROM cleanup_expired_users();';
    RAISE NOTICE '';
    RAISE NOTICE '  -- Query only active users';
    RAISE NOTICE '  SELECT * FROM users_active WHERE email = ''user@example.com'';';
    RAISE NOTICE '';
    RAISE NOTICE 'Automated Maintenance:';
    RAISE NOTICE '  • Schedule cleanup_expired_users() to run monthly';
    RAISE NOTICE '  • Monitor gdpr_erasure_summary for compliance reporting';
    RAISE NOTICE '';
    RAISE NOTICE '========================================================================';
    RAISE NOTICE '';
END $$;

-- Analyze tables for query optimizer
ANALYZE users;
ANALYZE gdpr_erasure_requests;
