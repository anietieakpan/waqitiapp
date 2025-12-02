-- Migration: Add Account Settings 2FA Fields
-- Version: V15
-- Description: Add fields for account settings 2FA, notification settings, and privacy settings

-- Add pending email and phone fields to users table
ALTER TABLE users 
ADD COLUMN pending_email TEXT,
ADD COLUMN pending_phone_number TEXT;

-- Add notification settings to user_profiles table
ALTER TABLE user_profiles 
ADD COLUMN timezone VARCHAR(50),
ADD COLUMN email_notifications BOOLEAN DEFAULT TRUE,
ADD COLUMN sms_notifications BOOLEAN DEFAULT TRUE,
ADD COLUMN push_notifications BOOLEAN DEFAULT TRUE,
ADD COLUMN transaction_notifications BOOLEAN DEFAULT TRUE,
ADD COLUMN security_notifications BOOLEAN DEFAULT TRUE,
ADD COLUMN marketing_notifications BOOLEAN DEFAULT FALSE;

-- Add privacy settings to user_profiles table
ALTER TABLE user_profiles 
ADD COLUMN profile_visibility VARCHAR(20) DEFAULT 'PRIVATE',
ADD COLUMN allow_data_sharing BOOLEAN DEFAULT FALSE,
ADD COLUMN allow_analytics_tracking BOOLEAN DEFAULT TRUE,
ADD COLUMN allow_third_party_integrations BOOLEAN DEFAULT FALSE,
ADD COLUMN allow_location_tracking BOOLEAN DEFAULT FALSE,
ADD COLUMN show_transaction_history BOOLEAN DEFAULT TRUE,
ADD COLUMN data_retention_preference VARCHAR(50) DEFAULT 'DEFAULT';

-- Update MFA configurations table to support comprehensive 2FA
-- First, create new columns
ALTER TABLE mfa_configurations 
ADD COLUMN sms_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN email_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN totp_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN backup_codes_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN totp_secret TEXT;

-- Make userId unique
ALTER TABLE mfa_configurations 
ADD CONSTRAINT uk_mfa_configurations_user_id UNIQUE (user_id);

-- Create backup codes table
CREATE TABLE mfa_backup_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mfa_config_id UUID NOT NULL,
    backup_code VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (mfa_config_id) REFERENCES mfa_configurations(id) ON DELETE CASCADE
);

-- Create index for backup codes lookup
CREATE INDEX idx_mfa_backup_codes_config_id ON mfa_backup_codes(mfa_config_id);
CREATE INDEX idx_mfa_backup_codes_code ON mfa_backup_codes(backup_code);

-- Migrate existing MFA data to new structure
-- Update existing records to set the appropriate method flag
UPDATE mfa_configurations 
SET sms_enabled = TRUE 
WHERE method = 'SMS' AND enabled = TRUE;

UPDATE mfa_configurations 
SET email_enabled = TRUE 
WHERE method = 'EMAIL' AND enabled = TRUE;

UPDATE mfa_configurations 
SET totp_enabled = TRUE 
WHERE method = 'TOTP' AND enabled = TRUE;

UPDATE mfa_configurations 
SET totp_secret = secret 
WHERE method = 'TOTP' AND secret IS NOT NULL;

-- Drop old columns after migration
ALTER TABLE mfa_configurations 
DROP COLUMN method,
DROP COLUMN secret,
DROP COLUMN verified;

-- Add indexes for performance
CREATE INDEX idx_users_pending_email ON users(pending_email) WHERE pending_email IS NOT NULL;
CREATE INDEX idx_users_pending_phone ON users(pending_phone_number) WHERE pending_phone_number IS NOT NULL;
CREATE INDEX idx_user_profiles_notifications ON user_profiles(email_notifications, sms_notifications, push_notifications);
CREATE INDEX idx_user_profiles_privacy ON user_profiles(profile_visibility, allow_data_sharing);
CREATE INDEX idx_mfa_configurations_methods ON mfa_configurations(sms_enabled, email_enabled, totp_enabled, backup_codes_enabled);

-- Add comments for documentation
COMMENT ON COLUMN users.pending_email IS 'Email address pending verification during change process';
COMMENT ON COLUMN users.pending_phone_number IS 'Phone number pending verification during change process';
COMMENT ON COLUMN user_profiles.timezone IS 'User preferred timezone for notifications and UI';
COMMENT ON COLUMN user_profiles.profile_visibility IS 'PUBLIC, FRIENDS_ONLY, or PRIVATE';
COMMENT ON COLUMN user_profiles.data_retention_preference IS 'User preference for data retention period';
COMMENT ON TABLE mfa_backup_codes IS 'Backup codes for MFA recovery';