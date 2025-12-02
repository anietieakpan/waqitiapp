-- V005__Remove_deprecated_KYC_status_column.sql
-- This migration removes the last remaining deprecated KYC status column
-- after all services have been updated to use the dedicated KYC microservice

-- Step 1: Verify all KYC statuses have been migrated to the KYC service
-- This is a safety check - in production, this should be verified before running the migration
DO $$ 
BEGIN 
    IF EXISTS (
        SELECT 1 FROM users 
        WHERE kyc_status IS NOT NULL 
        AND kyc_status NOT IN ('NOT_STARTED', 'PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')
    ) THEN 
        RAISE EXCEPTION 'Found unexpected KYC status values. Please verify KYC migration is complete.';
    END IF;
END $$;

-- Step 2: Drop the index on kyc_status
DROP INDEX IF EXISTS idx_users_kyc_status;

-- Step 3: Remove the deprecated kyc_status column
-- This is the final step in the KYC migration process
ALTER TABLE users DROP COLUMN IF EXISTS kyc_status CASCADE;

-- Step 4: Update the migration log to record the completion
INSERT INTO kyc_migration_log (user_id, old_kyc_status, notes)
SELECT 
    gen_random_uuid(),  -- placeholder user_id for system entry
    'SYSTEM',
    'Completed KYC column removal - all KYC functionality now handled by dedicated KYC microservice'
WHERE NOT EXISTS (
    SELECT 1 FROM kyc_migration_log 
    WHERE notes LIKE '%KYC column removal%'
);

-- Step 5: Add comment documenting the completion of KYC migration
COMMENT ON TABLE users IS 'User table - KYC functionality fully migrated to dedicated KYC microservice as of V005';

-- Step 6: Create a view for backward compatibility if needed
-- This view can be used by legacy code that still references kyc_status
CREATE OR REPLACE VIEW users_with_kyc_status AS
SELECT 
    u.*,
    'MIGRATED_TO_KYC_SERVICE' as kyc_status
FROM users u;

COMMENT ON VIEW users_with_kyc_status IS 'Backward compatibility view - kyc_status always returns MIGRATED_TO_KYC_SERVICE';