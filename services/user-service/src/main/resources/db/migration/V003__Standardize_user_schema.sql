-- Standardize User Service Database Schema
-- This migration standardizes all user-related tables to follow Waqiti patterns

-- =====================================================================
-- USERS TABLE STANDARDIZATION
-- =====================================================================

-- Add missing audit and version columns to users table
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are using TIMESTAMP WITH TIME ZONE
ALTER TABLE users 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add business rule constraints
ALTER TABLE users 
ADD CONSTRAINT check_user_status 
CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED'));

ALTER TABLE users 
ADD CONSTRAINT check_kyc_status 
CHECK (kyc_status IN ('NOT_STARTED', 'IN_PROGRESS', 'PENDING_REVIEW', 'APPROVED', 'REJECTED'));

-- =====================================================================
-- USER PROFILES TABLE STANDARDIZATION
-- =====================================================================

-- Standardize user_profiles table
ALTER TABLE user_profiles 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE user_profiles 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_user_profiles_updated_at 
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================================
-- KYC DOCUMENTS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize kyc_documents table
ALTER TABLE kyc_documents 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE kyc_documents 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_kyc_documents_updated_at 
    BEFORE UPDATE ON kyc_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE kyc_documents 
ADD CONSTRAINT check_document_type 
CHECK (document_type IN ('PASSPORT', 'DRIVERS_LICENSE', 'NATIONAL_ID', 'UTILITY_BILL', 'BANK_STATEMENT'));

ALTER TABLE kyc_documents 
ADD CONSTRAINT check_verification_status 
CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'));

-- =====================================================================
-- USER RISK ASSESSMENTS TABLE STANDARDIZATION  
-- =====================================================================

-- Standardize user_risk_assessments table
ALTER TABLE user_risk_assessments 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE user_risk_assessments 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_user_risk_assessments_updated_at 
    BEFORE UPDATE ON user_risk_assessments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE user_risk_assessments 
ADD CONSTRAINT check_risk_level 
CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

-- =====================================================================
-- USER DEVICES TABLE STANDARDIZATION
-- =====================================================================

-- Standardize user_devices table
ALTER TABLE user_devices 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE user_devices 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN last_used_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_user_devices_updated_at 
    BEFORE UPDATE ON user_devices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add constraints
ALTER TABLE user_devices 
ADD CONSTRAINT check_device_status 
CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'EXPIRED'));

-- =====================================================================
-- MFA CONFIGURATIONS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize mfa_configurations table
ALTER TABLE mfa_configurations 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE mfa_configurations 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_mfa_configurations_updated_at 
    BEFORE UPDATE ON mfa_configurations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_trigger();

-- Add constraints
ALTER TABLE mfa_configurations 
ADD CONSTRAINT check_mfa_method 
CHECK (method IN ('TOTP', 'SMS', 'EMAIL', 'BACKUP_CODES'));

ALTER TABLE mfa_configurations 
ADD CONSTRAINT check_mfa_status 
CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'));

-- =====================================================================
-- REVOKED TOKENS TABLE STANDARDIZATION
-- =====================================================================

-- Standardize revoked_tokens table
ALTER TABLE revoked_tokens 
ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255),
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Ensure timestamp columns are standardized
ALTER TABLE revoked_tokens 
ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE,
ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE;

-- Add updated_at trigger
CREATE TRIGGER update_revoked_tokens_updated_at 
    BEFORE UPDATE ON revoked_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================================
-- STANDARDIZED INDEXES
-- =====================================================================

-- Users table indexes
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_kyc_status ON users(kyc_status);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_external_id ON users(external_id);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_users_updated_at ON users(updated_at);

-- User profiles indexes
CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_created_at ON user_profiles(created_at);

-- KYC documents indexes
CREATE INDEX IF NOT EXISTS idx_kyc_documents_user_id ON kyc_documents(user_id);
CREATE INDEX IF NOT EXISTS idx_kyc_documents_type ON kyc_documents(document_type);
CREATE INDEX IF NOT EXISTS idx_kyc_documents_status ON kyc_documents(verification_status);
CREATE INDEX IF NOT EXISTS idx_kyc_documents_created_at ON kyc_documents(created_at);

-- Risk assessments indexes
CREATE INDEX IF NOT EXISTS idx_user_risk_assessments_user_id ON user_risk_assessments(user_id);
CREATE INDEX IF NOT EXISTS idx_user_risk_assessments_level ON user_risk_assessments(risk_level);
CREATE INDEX IF NOT EXISTS idx_user_risk_assessments_created_at ON user_risk_assessments(created_at);

-- User devices indexes
CREATE INDEX IF NOT EXISTS idx_user_devices_user_id ON user_devices(user_id);
CREATE INDEX IF NOT EXISTS idx_user_devices_status ON user_devices(status);
CREATE INDEX IF NOT EXISTS idx_user_devices_fingerprint ON user_devices(device_fingerprint);
CREATE INDEX IF NOT EXISTS idx_user_devices_last_used ON user_devices(last_used_at);

-- MFA configurations indexes
CREATE INDEX IF NOT EXISTS idx_mfa_configurations_user_id ON mfa_configurations(user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_configurations_method ON mfa_configurations(method);
CREATE INDEX IF NOT EXISTS idx_mfa_configurations_status ON mfa_configurations(status);

-- Revoked tokens indexes
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_user_id ON revoked_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_created_at ON revoked_tokens(created_at);

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON TABLE users IS 'Core user accounts and authentication information';
COMMENT ON TABLE user_profiles IS 'Extended user profile information';
COMMENT ON TABLE kyc_documents IS 'Know Your Customer document verification';
COMMENT ON TABLE user_risk_assessments IS 'User risk assessment and scoring';
COMMENT ON TABLE user_devices IS 'Registered user devices for security tracking';
COMMENT ON TABLE mfa_configurations IS 'Multi-factor authentication configurations';
COMMENT ON TABLE revoked_tokens IS 'Revoked JWT tokens for security';

-- =====================================================================
-- CLEANUP OLD/DUPLICATE INDEXES
-- =====================================================================

-- Remove any duplicate or non-standard indexes that might exist
-- (This would be customized based on actual existing indexes)