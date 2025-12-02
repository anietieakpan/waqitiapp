-- Fix Critical Table Name Mismatch
-- Issue: Entity expects 'users' table but V1 migration created 'auth_user'
-- Solution: Rename auth_user to users for JPA compatibility
-- Date: 2025-10-18
-- Author: Waqiti DevOps Team

-- Rename the main user table to match JPA entity mapping
ALTER TABLE auth_user RENAME TO users;

-- Update foreign key constraints that reference the renamed table
ALTER TABLE auth_session
    DROP CONSTRAINT fk_auth_user;

ALTER TABLE auth_session
    ADD CONSTRAINT fk_auth_session_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_role
    DROP CONSTRAINT fk_user_role_user;

ALTER TABLE user_role
    ADD CONSTRAINT fk_user_role_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE password_reset_token
    DROP CONSTRAINT fk_password_reset_user;

ALTER TABLE password_reset_token
    ADD CONSTRAINT fk_password_reset_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Add missing columns from User entity that weren't in original migration
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS two_factor_method VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_password_change_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS locale VARCHAR(10) DEFAULT 'en_US';
ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC';
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Rename columns to match entity exactly
ALTER TABLE users RENAME COLUMN password_last_changed_at TO last_password_change_at;
ALTER TABLE users RENAME COLUMN mfa_enabled TO two_factor_enabled;
ALTER TABLE users RENAME COLUMN mfa_secret TO two_factor_secret;

-- Remove columns not in entity
ALTER TABLE users DROP COLUMN IF EXISTS password_salt;
ALTER TABLE users DROP COLUMN IF EXISTS password_algorithm;
ALTER TABLE users DROP COLUMN IF EXISTS must_change_password;
ALTER TABLE users DROP COLUMN IF EXISTS email_verification_token;
ALTER TABLE users DROP COLUMN IF EXISTS email_verification_sent_at;
ALTER TABLE users DROP COLUMN IF EXISTS mfa_backup_codes;
ALTER TABLE users DROP COLUMN IF EXISTS totp_enabled;
ALTER TABLE users DROP COLUMN IF EXISTS biometric_enabled;
ALTER TABLE users DROP COLUMN IF EXISTS security_questions;
ALTER TABLE users DROP COLUMN IF EXISTS last_login_device;

-- Add proper indexes matching User entity
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_created ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_users_deleted_flag ON users(deleted) WHERE deleted = FALSE;

-- Drop duplicate indexes with wrong names
DROP INDEX IF EXISTS idx_auth_user_email;
DROP INDEX IF EXISTS idx_auth_user_username;
DROP INDEX IF EXISTS idx_auth_user_status;
DROP INDEX IF EXISTS idx_auth_user_deleted;

-- Rename user_role table to user_roles (matching entity @JoinTable name)
ALTER TABLE user_role RENAME TO user_roles;

-- Update join table column names if needed
-- (Already correct: user_id and role_id)

-- Update indexes on renamed table
DROP INDEX IF EXISTS idx_user_role_user;
DROP INDEX IF EXISTS idx_user_role_role;

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- Add comment for documentation
COMMENT ON TABLE users IS 'Enterprise-grade user authentication table. Renamed from auth_user to match JPA @Table(name = "users") annotation. Critical for application startup.';
