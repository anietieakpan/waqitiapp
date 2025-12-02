-- Auth Service Initial Schema
-- Created: 2025-09-27
-- Description: Authentication, authorization, and session management schema

CREATE TABLE IF NOT EXISTS auth_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_salt VARCHAR(100) NOT NULL,
    password_algorithm VARCHAR(50) NOT NULL DEFAULT 'BCRYPT',
    password_last_changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    password_expires_at TIMESTAMP,
    must_change_password BOOLEAN DEFAULT FALSE,
    failed_login_attempts INTEGER DEFAULT 0,
    account_locked_until TIMESTAMP,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified BOOLEAN DEFAULT FALSE,
    email_verification_token VARCHAR(255),
    email_verification_sent_at TIMESTAMP,
    phone_number VARCHAR(20),
    phone_verified BOOLEAN DEFAULT FALSE,
    mfa_enabled BOOLEAN DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    mfa_backup_codes TEXT[],
    totp_enabled BOOLEAN DEFAULT FALSE,
    biometric_enabled BOOLEAN DEFAULT FALSE,
    security_questions JSONB,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),
    last_login_device VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    access_token_hash VARCHAR(255) NOT NULL,
    refresh_token_hash VARCHAR(255),
    device_fingerprint VARCHAR(255),
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    country_code VARCHAR(3),
    city VARCHAR(100),
    auth_level VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    is_mfa_verified BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    revoked_at TIMESTAMP,
    revoked_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_system_role BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS auth_permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    permission_name VARCHAR(100) UNIQUE NOT NULL,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    expires_at TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES auth_permission(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    event_type VARCHAR(50) NOT NULL,
    event_action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50),
    resource_id VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS password_reset_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_auth_user_email ON auth_user(email);
CREATE INDEX idx_auth_user_username ON auth_user(username);
CREATE INDEX idx_auth_user_status ON auth_user(account_status);
CREATE INDEX idx_auth_user_deleted ON auth_user(deleted_at) WHERE deleted_at IS NULL;

CREATE INDEX idx_auth_session_user ON auth_session(user_id);
CREATE INDEX idx_auth_session_token ON auth_session(session_id);
CREATE INDEX idx_auth_session_active ON auth_session(is_active, expires_at);
CREATE INDEX idx_auth_session_ip ON auth_session(ip_address);

CREATE INDEX idx_user_role_user ON user_role(user_id);
CREATE INDEX idx_user_role_role ON user_role(role_id);

CREATE INDEX idx_auth_audit_user ON auth_audit_log(user_id);
CREATE INDEX idx_auth_audit_event ON auth_audit_log(event_type, event_action);
CREATE INDEX idx_auth_audit_created ON auth_audit_log(created_at DESC);

CREATE INDEX idx_password_reset_user ON password_reset_token(user_id);
CREATE INDEX idx_password_reset_token ON password_reset_token(token_hash);
CREATE INDEX idx_password_reset_expires ON password_reset_token(expires_at);

-- Insert default roles
INSERT INTO auth_role (role_name, description, is_system_role) VALUES
    ('ROLE_ADMIN', 'System administrator with full access', true),
    ('ROLE_USER', 'Standard user with basic access', true),
    ('ROLE_COMPLIANCE_OFFICER', 'Compliance and regulatory oversight', true),
    ('ROLE_AUDITOR', 'Read-only access for auditing', true),
    ('ROLE_SUPPORT', 'Customer support representative', true)
ON CONFLICT (role_name) DO NOTHING;

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_auth_user_updated_at BEFORE UPDATE ON auth_user
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auth_role_updated_at BEFORE UPDATE ON auth_role
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();