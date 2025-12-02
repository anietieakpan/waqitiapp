-- V004__Deprecate_KYC_columns_in_user_tables.sql
-- This migration deprecates KYC-related columns in the users table
-- as KYC functionality has been moved to a dedicated KYC microservice

-- Step 1: Add comment to existing KYC columns to mark them as deprecated
COMMENT ON COLUMN users.kyc_status IS 'DEPRECATED - KYC status is now managed by the KYC microservice. This column is kept for backward compatibility only.';
COMMENT ON COLUMN users.kyc_initiated_at IS 'DEPRECATED - KYC timestamps are now managed by the KYC microservice';
COMMENT ON COLUMN users.kyc_verified_at IS 'DEPRECATED - KYC timestamps are now managed by the KYC microservice';
COMMENT ON COLUMN users.kyc_rejected_at IS 'DEPRECATED - KYC timestamps are now managed by the KYC microservice';
COMMENT ON COLUMN users.kyc_rejection_reason IS 'DEPRECATED - KYC rejection details are now managed by the KYC microservice';

-- Step 2: Drop personal information columns that were used for KYC
-- These should now be managed by the user_profiles table
ALTER TABLE users DROP COLUMN IF EXISTS first_name CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS last_name CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS date_of_birth CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS address_line1 CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS address_line2 CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS city CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS state_province CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS postal_code CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS country CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS nationality CASCADE;

-- Step 3: Drop KYC-specific timestamp columns
-- We keep kyc_status for backward compatibility but drop the timestamps
ALTER TABLE users DROP COLUMN IF EXISTS kyc_initiated_at CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS kyc_verified_at CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS kyc_rejected_at CASCADE;
ALTER TABLE users DROP COLUMN IF EXISTS kyc_rejection_reason CASCADE;

-- Step 4: Drop KYC verification table if it exists
DROP TABLE IF EXISTS kyc_verifications CASCADE;

-- Step 5: Drop KYC documents table if it exists
DROP TABLE IF EXISTS kyc_documents CASCADE;

-- Step 6: Add an index on kyc_status for performance (if not already exists)
-- This is kept since we're maintaining the column for backward compatibility
CREATE INDEX IF NOT EXISTS idx_users_kyc_status ON users(kyc_status);

-- Step 7: Create a migration log entry
CREATE TABLE IF NOT EXISTS kyc_migration_log (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    old_kyc_status VARCHAR(50),
    migration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- Step 8: Log current KYC statuses for audit trail
INSERT INTO kyc_migration_log (user_id, old_kyc_status, notes)
SELECT id, kyc_status, 'KYC functionality migrated to dedicated KYC microservice'
FROM users
WHERE kyc_status IS NOT NULL AND kyc_status != 'NOT_STARTED';

-- Add comment to the table explaining the migration
COMMENT ON TABLE kyc_migration_log IS 'Audit log for KYC data migration from user service to dedicated KYC microservice';