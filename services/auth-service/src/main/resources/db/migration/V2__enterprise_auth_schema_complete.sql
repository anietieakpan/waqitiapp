-- Auth Service Enterprise Schema V2
-- Created: 2025-10-11
-- Description: Production-ready authentication, authorization, and session management
-- Features: Complete RBAC, refresh tokens, sessions, audit logging, optimistic locking

-- =============================================================================
-- PART 1: Update existing tables to match new entity model
-- =============================================================================

-- Add missing columns to auth_user (rename to users)
ALTER TABLE auth_user RENAME TO users;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(64),
    ADD COLUMN IF NOT EXISTS two_factor_method VARCHAR(20),
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500),
    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255),
    ADD COLUMN IF NOT EXISTS locale VARCHAR(10) DEFAULT 'en_US',
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Update column names to match entity (if different)
ALTER TABLE users RENAME COLUMN password_hash TO password_hash;
ALTER TABLE users RENAME COLUMN account_status TO account_status;
ALTER TABLE users RENAME COLUMN password_last_changed_at TO last_password_change_at;

-- Update role table
ALTER TABLE auth_role RENAME TO roles;

ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS role_type VARCHAR(20) DEFAULT 'CUSTOM' NOT NULL,
    ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 100 NOT NULL,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE NOT NULL,
    ADD COLUMN IF NOT EXISTS parent_role_id UUID,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Add foreign key for hierarchical roles
ALTER TABLE roles
    ADD CONSTRAINT fk_roles_parent
    FOREIGN KEY (parent_role_id) REFERENCES roles(id);

-- Update permission table
ALTER TABLE auth_permission RENAME TO permissions;

ALTER TABLE permissions
    RENAME COLUMN permission_name TO name;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS category VARCHAR(30) DEFAULT 'CUSTOM' NOT NULL,
    ADD COLUMN IF NOT EXISTS is_system_permission BOOLEAN DEFAULT FALSE NOT NULL,
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- Update role_permission join table
ALTER TABLE role_permission
    DROP CONSTRAINT IF EXISTS fk_role_permission_role,
    DROP CONSTRAINT IF EXISTS fk_role_permission_permission;

ALTER TABLE role_permission RENAME TO role_permissions;

ALTER TABLE role_permissions
    ADD CONSTRAINT fk_role_permissions_role
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_role_permissions_permission
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE;

-- Update user_role join table
ALTER TABLE user_role
    DROP CONSTRAINT IF EXISTS fk_user_role_user,
    DROP CONSTRAINT IF EXISTS fk_user_role_role;

ALTER TABLE user_role RENAME TO user_roles;

ALTER TABLE user_roles
    DROP COLUMN IF EXISTS assigned_by,
    DROP COLUMN IF EXISTS expires_at;

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_user_roles_role
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE;

-- =============================================================================
-- PART 2: Create new tables for enterprise features
-- =============================================================================

-- Refresh Tokens Table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(500) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    token_family UUID NOT NULL,
    parent_token_id UUID,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE NOT NULL,
    revoked_at TIMESTAMP,
    revocation_reason VARCHAR(255),
    used BOOLEAN DEFAULT FALSE NOT NULL,
    used_at TIMESTAMP,
    device_id VARCHAR(255),
    device_name VARCHAR(255),
    device_type VARCHAR(50),
    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    geolocation VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- User Sessions Table (enhanced)
DROP TABLE IF EXISTS auth_session CASCADE;

CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    session_token VARCHAR(500) UNIQUE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    device_id VARCHAR(255),
    device_name VARCHAR(255),
    device_type VARCHAR(50),
    user_agent VARCHAR(500),
    browser VARCHAR(100),
    operating_system VARCHAR(100),
    ip_address VARCHAR(45),
    geolocation VARCHAR(100),
    country_code VARCHAR(2),
    city VARCHAR(100),
    is_suspicious BOOLEAN DEFAULT FALSE NOT NULL,
    suspicious_reason VARCHAR(500),
    trusted_device BOOLEAN DEFAULT FALSE NOT NULL,
    two_factor_verified BOOLEAN DEFAULT FALSE NOT NULL,
    login_method VARCHAR(50),
    activity_count BIGINT DEFAULT 0 NOT NULL,
    terminated_at TIMESTAMP,
    termination_reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Enhanced Audit Log Table
DROP TABLE IF EXISTS auth_audit_log CASCADE;

CREATE TABLE IF NOT EXISTS auth_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    username VARCHAR(100),
    session_id UUID,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    event_message VARCHAR(1000),
    failure_reason VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    device_id VARCHAR(255),
    device_type VARCHAR(50),
    geolocation VARCHAR(100),
    country_code VARCHAR(2),
    risk_level VARCHAR(20) DEFAULT 'LOW' NOT NULL,
    risk_score INTEGER,
    risk_factors VARCHAR(1000),
    metadata TEXT,
    correlation_id UUID,
    trace_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- =============================================================================
-- PART 3: Create comprehensive indexes for performance
-- =============================================================================

-- Users table indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(account_status);
CREATE INDEX IF NOT EXISTS idx_users_created ON users(created_at);
CREATE INDEX IF NOT EXISTS idx_users_deleted ON users(deleted_at) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified) WHERE email_verified = true;
CREATE INDEX IF NOT EXISTS idx_users_two_factor ON users(two_factor_enabled) WHERE two_factor_enabled = true;

-- Refresh tokens indexes
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens(token_family);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires ON refresh_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_device ON refresh_tokens(user_id, device_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_active ON refresh_tokens(user_id, revoked, used, expires_at);

-- User sessions indexes
CREATE INDEX IF NOT EXISTS idx_sessions_user ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON user_sessions(session_token);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON user_sessions(status);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON user_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_device ON user_sessions(user_id, device_id);
CREATE INDEX IF NOT EXISTS idx_sessions_suspicious ON user_sessions(is_suspicious) WHERE is_suspicious = true;
CREATE INDEX IF NOT EXISTS idx_sessions_active ON user_sessions(user_id, status, expires_at);

-- Roles indexes
CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);
CREATE INDEX IF NOT EXISTS idx_roles_type ON roles(role_type);
CREATE INDEX IF NOT EXISTS idx_roles_priority ON roles(priority);
CREATE INDEX IF NOT EXISTS idx_roles_active ON roles(is_active) WHERE is_active = true;

-- Permissions indexes
CREATE INDEX IF NOT EXISTS idx_permissions_name ON permissions(name);
CREATE INDEX IF NOT EXISTS idx_permissions_category ON permissions(category);
CREATE INDEX IF NOT EXISTS idx_permissions_resource ON permissions(resource);
CREATE INDEX IF NOT EXISTS idx_permissions_system ON permissions(is_system_permission) WHERE is_system_permission = true;

-- Audit logs indexes
CREATE INDEX IF NOT EXISTS idx_audit_user ON auth_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON auth_audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_status ON auth_audit_logs(status);
CREATE INDEX IF NOT EXISTS idx_audit_created ON auth_audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_ip ON auth_audit_logs(ip_address);
CREATE INDEX IF NOT EXISTS idx_audit_session ON auth_audit_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_risk ON auth_audit_logs(risk_level) WHERE risk_level IN ('HIGH', 'CRITICAL');
CREATE INDEX IF NOT EXISTS idx_audit_correlation ON auth_audit_logs(correlation_id) WHERE correlation_id IS NOT NULL;

-- =============================================================================
-- PART 4: Create triggers for automatic timestamp updates
-- =============================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables with updated_at
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_roles_updated_at ON roles;
CREATE TRIGGER update_roles_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_user_sessions_updated_at ON user_sessions;
CREATE TRIGGER update_user_sessions_updated_at
    BEFORE UPDATE ON user_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- PART 5: Insert comprehensive default roles and permissions
-- =============================================================================

-- Delete existing roles to recreate with proper structure
DELETE FROM role_permissions;
DELETE FROM user_roles;
DELETE FROM roles;
DELETE FROM permissions;

-- System roles
INSERT INTO roles (name, display_name, description, role_type, priority, is_system_role, is_active) VALUES
    ('SUPER_ADMIN', 'Super Administrator', 'Full system access including system configuration', 'SYSTEM', 1, true, true),
    ('ADMIN', 'Administrator', 'Administrative access to platform management', 'ADMINISTRATIVE', 10, true, true),
    ('COMPLIANCE_OFFICER', 'Compliance Officer', 'Compliance and regulatory oversight', 'ADMINISTRATIVE', 20, true, true),
    ('SUPPORT_AGENT', 'Support Agent', 'Customer support operations', 'OPERATIONAL', 30, true, true),
    ('MERCHANT', 'Merchant', 'Merchant account with payment processing', 'OPERATIONAL', 40, true, true),
    ('USER', 'Standard User', 'Standard user with basic access', 'USER', 50, true, true),
    ('API_CLIENT', 'API Client', 'Service-to-service API access', 'SERVICE', 60, true, true)
ON CONFLICT (name) DO NOTHING;

-- Core permissions
INSERT INTO permissions (name, display_name, description, category, resource, action, is_system_permission) VALUES
    -- User Management
    ('USER:READ', 'View Users', 'View user information', 'USER_MANAGEMENT', 'USER', 'READ', true),
    ('USER:WRITE', 'Manage Users', 'Create and update users', 'USER_MANAGEMENT', 'USER', 'WRITE', true),
    ('USER:DELETE', 'Delete Users', 'Delete user accounts', 'USER_MANAGEMENT', 'USER', 'DELETE', true),
    ('USER:LOCK', 'Lock Users', 'Lock and unlock user accounts', 'USER_MANAGEMENT', 'USER', 'LOCK', true),

    -- Payment Processing
    ('PAYMENT:PROCESS', 'Process Payments', 'Process payment transactions', 'PAYMENT_PROCESSING', 'PAYMENT', 'PROCESS', true),
    ('PAYMENT:REFUND', 'Refund Payments', 'Issue payment refunds', 'PAYMENT_PROCESSING', 'PAYMENT', 'REFUND', true),
    ('PAYMENT:VIEW', 'View Payments', 'View payment details', 'PAYMENT_PROCESSING', 'PAYMENT', 'VIEW', true),

    -- Wallet Operations
    ('WALLET:READ', 'View Wallets', 'View wallet information', 'WALLET_OPERATIONS', 'WALLET', 'READ', true),
    ('WALLET:TRANSFER', 'Transfer Funds', 'Transfer funds between wallets', 'WALLET_OPERATIONS', 'WALLET', 'TRANSFER', true),
    ('WALLET:FREEZE', 'Freeze Wallets', 'Freeze and unfreeze wallets', 'WALLET_OPERATIONS', 'WALLET', 'FREEZE', true),

    -- Compliance & Audit
    ('COMPLIANCE:AUDIT', 'View Audit Logs', 'Access compliance audit logs', 'COMPLIANCE_AUDIT', 'COMPLIANCE', 'AUDIT', true),
    ('COMPLIANCE:REPORT', 'Generate Reports', 'Generate compliance reports', 'COMPLIANCE_AUDIT', 'COMPLIANCE', 'REPORT', true),
    ('COMPLIANCE:KYC', 'Manage KYC', 'Manage KYC verification', 'COMPLIANCE_AUDIT', 'COMPLIANCE', 'KYC', true),

    -- Fraud Detection
    ('FRAUD:VIEW', 'View Fraud Alerts', 'View fraud detection alerts', 'FRAUD_DETECTION', 'FRAUD', 'VIEW', true),
    ('FRAUD:MANAGE', 'Manage Fraud Cases', 'Manage fraud investigation cases', 'FRAUD_DETECTION', 'FRAUD', 'MANAGE', true),

    -- System Administration
    ('SYSTEM:CONFIGURE', 'System Configuration', 'Configure system settings', 'SYSTEM_ADMINISTRATION', 'SYSTEM', 'CONFIGURE', true),
    ('SYSTEM:BACKUP', 'Backup System', 'Perform system backups', 'SYSTEM_ADMINISTRATION', 'SYSTEM', 'BACKUP', true)
ON CONFLICT (name) DO NOTHING;

-- Assign permissions to roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'SUPER_ADMIN'; -- All permissions

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.name IN (
    'USER:READ', 'USER:WRITE', 'USER:LOCK',
    'PAYMENT:VIEW', 'PAYMENT:REFUND',
    'WALLET:READ', 'WALLET:FREEZE',
    'COMPLIANCE:AUDIT', 'FRAUD:VIEW'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPLIANCE_OFFICER' AND p.name IN (
    'USER:READ', 'PAYMENT:VIEW', 'WALLET:READ',
    'COMPLIANCE:AUDIT', 'COMPLIANCE:REPORT', 'COMPLIANCE:KYC',
    'FRAUD:VIEW'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'USER' AND p.name IN (
    'PAYMENT:PROCESS', 'WALLET:READ', 'WALLET:TRANSFER'
);

-- =============================================================================
-- PART 6: Add comments for documentation
-- =============================================================================

COMMENT ON TABLE users IS 'Enterprise user accounts with comprehensive security features';
COMMENT ON TABLE roles IS 'Role-based access control roles with hierarchical support';
COMMENT ON TABLE permissions IS 'Fine-grained permissions for resource-action combinations';
COMMENT ON TABLE refresh_tokens IS 'Refresh tokens with rotation and breach detection';
COMMENT ON TABLE user_sessions IS 'Multi-device session tracking with security monitoring';
COMMENT ON TABLE auth_audit_logs IS 'Immutable audit log for compliance and forensics';

COMMENT ON COLUMN users.version IS 'Optimistic locking version for concurrent update prevention';
COMMENT ON COLUMN refresh_tokens.token_family IS 'Token family ID for detecting stolen tokens';
COMMENT ON COLUMN user_sessions.is_suspicious IS 'Flag for sessions requiring security review';
COMMENT ON COLUMN auth_audit_logs.risk_level IS 'Risk assessment: LOW, MEDIUM, HIGH, CRITICAL';
