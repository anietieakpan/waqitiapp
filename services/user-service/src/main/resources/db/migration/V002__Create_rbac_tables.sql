-- RBAC Database Schema
-- Role-Based Access Control tables for user role management
-- Version: 1.0.0
-- Date: 2025-10-11

-- ========================================
-- ROLES TABLE
-- ========================================
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========================================
-- PERMISSIONS TABLE
-- ========================================
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========================================
-- USER_ROLES TABLE (Many-to-Many)
-- ========================================
CREATE TABLE IF NOT EXISTS user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_by VARCHAR(255),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

-- ========================================
-- ROLE_PERMISSIONS TABLE (Many-to-Many)
-- ========================================
CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);

-- ========================================
-- INDEXES
-- ========================================
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_expires_at ON user_roles(expires_at);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

-- ========================================
-- COMMENTS
-- ========================================
COMMENT ON TABLE roles IS 'System roles (USER, PREMIUM, MERCHANT, ADMIN, etc.)';
COMMENT ON TABLE permissions IS 'Fine-grained permissions (payment:read, wallet:write, etc.)';
COMMENT ON TABLE user_roles IS 'User role assignments';
COMMENT ON TABLE role_permissions IS 'Permission assignments to roles';

-- ========================================
-- INSERT DEFAULT ROLES
-- ========================================
INSERT INTO roles (id, name, description) VALUES
    ('11111111-1111-1111-1111-111111111111', 'USER', 'Regular user with basic permissions'),
    ('22222222-2222-2222-2222-222222222222', 'PREMIUM', 'Premium user with additional features'),
    ('33333333-3333-3333-3333-333333333333', 'MERCHANT', 'Merchant with business features'),
    ('44444444-4444-4444-4444-444444444444', 'COMPLIANCE_OFFICER', 'Compliance officer with regulatory powers'),
    ('55555555-5555-5555-5555-555555555555', 'FRAUD_ANALYST', 'Fraud analyst with investigation powers'),
    ('66666666-6666-6666-6666-666666666666', 'SUPPORT', 'Customer support agent'),
    ('77777777-7777-7777-7777-777777777777', 'ADMIN', 'System administrator with full access')
ON CONFLICT (name) DO NOTHING;

-- ========================================
-- INSERT DEFAULT PERMISSIONS
-- ========================================

-- Payment Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('payment:read', 'Payment Read', 'View payment information'),
    ('payment:write', 'Payment Write', 'Create and modify payments'),
    ('payment:refund', 'Payment Refund', 'Issue payment refunds'),
    ('payment:cancel', 'Payment Cancel', 'Cancel payments')
ON CONFLICT (code) DO NOTHING;

-- Wallet Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('wallet:read', 'Wallet Read', 'View wallet information'),
    ('wallet:write', 'Wallet Write', 'Modify wallet balance'),
    ('wallet:transfer', 'Wallet Transfer', 'Transfer funds between wallets'),
    ('wallet:withdraw', 'Wallet Withdraw', 'Withdraw funds from wallet')
ON CONFLICT (code) DO NOTHING;

-- User Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('user:read', 'User Read', 'View user information'),
    ('user:write', 'User Write', 'Create and modify users'),
    ('user:delete', 'User Delete', 'Delete users')
ON CONFLICT (code) DO NOTHING;

-- Transaction Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('transaction:read', 'Transaction Read', 'View transaction history'),
    ('transaction:export', 'Transaction Export', 'Export transaction data')
ON CONFLICT (code) DO NOTHING;

-- Account Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('account:read', 'Account Read', 'View account information'),
    ('account:write', 'Account Write', 'Modify account settings'),
    ('account:close', 'Account Close', 'Close accounts')
ON CONFLICT (code) DO NOTHING;

-- Profile Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('profile:read', 'Profile Read', 'View own profile'),
    ('profile:write', 'Profile Write', 'Modify own profile')
ON CONFLICT (code) DO NOTHING;

-- International Transfer Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('international:transfer', 'International Transfer', 'Make international transfers'),
    ('international:view', 'International View', 'View international transfer limits')
ON CONFLICT (code) DO NOTHING;

-- Crypto Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('crypto:trade', 'Crypto Trade', 'Trade cryptocurrencies'),
    ('crypto:transfer', 'Crypto Transfer', 'Transfer crypto assets'),
    ('crypto:view', 'Crypto View', 'View crypto portfolio')
ON CONFLICT (code) DO NOTHING;

-- Investment Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('investment:trade', 'Investment Trade', 'Trade investments'),
    ('investment:view', 'Investment View', 'View investment portfolio')
ON CONFLICT (code) DO NOTHING;

-- Merchant Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('merchant:dashboard', 'Merchant Dashboard', 'Access merchant dashboard'),
    ('merchant:analytics', 'Merchant Analytics', 'View merchant analytics'),
    ('merchant:refund', 'Merchant Refund', 'Issue merchant refunds')
ON CONFLICT (code) DO NOTHING;

-- Compliance Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('compliance:read', 'Compliance Read', 'View compliance data'),
    ('compliance:write', 'Compliance Write', 'Modify compliance settings'),
    ('compliance:sar:file', 'File SAR', 'File Suspicious Activity Reports'),
    ('compliance:account:freeze', 'Freeze Account', 'Freeze user accounts'),
    ('compliance:monitoring:enhanced', 'Enhanced Monitoring', 'Enable enhanced monitoring')
ON CONFLICT (code) DO NOTHING;

-- Fraud Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('fraud:read', 'Fraud Read', 'View fraud alerts'),
    ('fraud:write', 'Fraud Write', 'Manage fraud cases'),
    ('fraud:account:block', 'Block Account', 'Block accounts for fraud'),
    ('fraud:transaction:review', 'Review Transaction', 'Review flagged transactions')
ON CONFLICT (code) DO NOTHING;

-- Support Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('support:user:view', 'View User', 'View user details'),
    ('support:transaction:view', 'View Transaction', 'View transaction details'),
    ('support:ticket:create', 'Create Ticket', 'Create support tickets'),
    ('support:ticket:resolve', 'Resolve Ticket', 'Resolve support tickets')
ON CONFLICT (code) DO NOTHING;

-- Admin Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('admin:*', 'Admin All', 'Full administrative access'),
    ('admin:users', 'Manage Users', 'Manage all users'),
    ('admin:config', 'System Config', 'Modify system configuration'),
    ('admin:logs', 'View Logs', 'View system logs')
ON CONFLICT (code) DO NOTHING;

-- Audit Permissions
INSERT INTO permissions (code, name, description) VALUES
    ('audit:read', 'Audit Read', 'View audit logs'),
    ('audit:export', 'Audit Export', 'Export audit data')
ON CONFLICT (code) DO NOTHING;

-- ========================================
-- MAP PERMISSIONS TO ROLES
-- ========================================

-- USER Role Permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'USER' AND p.code IN (
    'payment:read', 'payment:write', 'payment:cancel',
    'wallet:read', 'wallet:write', 'wallet:transfer', 'wallet:withdraw',
    'transaction:read', 'transaction:export',
    'account:read', 'account:write',
    'profile:read', 'profile:write',
    'user:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- PREMIUM Role Permissions (inherits USER + additional)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PREMIUM' AND p.code IN (
    'payment:read', 'payment:write', 'payment:cancel',
    'wallet:read', 'wallet:write', 'wallet:transfer', 'wallet:withdraw',
    'transaction:read', 'transaction:export',
    'account:read', 'account:write',
    'profile:read', 'profile:write',
    'user:read',
    'international:transfer', 'international:view',
    'crypto:trade', 'crypto:transfer', 'crypto:view',
    'investment:trade', 'investment:view'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MERCHANT Role Permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MERCHANT' AND p.code IN (
    'payment:read', 'payment:write',
    'wallet:read', 'wallet:write',
    'transaction:read', 'transaction:export',
    'account:read', 'account:write',
    'profile:read', 'profile:write',
    'merchant:dashboard', 'merchant:analytics', 'merchant:refund'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- COMPLIANCE_OFFICER Role Permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPLIANCE_OFFICER' AND p.code IN (
    'user:read', 'account:read', 'transaction:read',
    'payment:read', 'wallet:read',
    'compliance:read', 'compliance:write',
    'compliance:sar:file', 'compliance:account:freeze',
    'compliance:monitoring:enhanced',
    'audit:read', 'audit:export'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- FRAUD_ANALYST Role Permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'FRAUD_ANALYST' AND p.code IN (
    'user:read', 'account:read', 'transaction:read',
    'payment:read', 'wallet:read',
    'fraud:read', 'fraud:write',
    'fraud:account:block', 'fraud:transaction:review',
    'audit:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SUPPORT Role Permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPPORT' AND p.code IN (
    'support:user:view', 'support:transaction:view',
    'support:ticket:create', 'support:ticket:resolve',
    'user:read', 'account:read', 'transaction:read'
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ADMIN Role Permissions (all permissions)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ========================================
-- TRIGGER FOR updated_at
-- ========================================
CREATE OR REPLACE FUNCTION update_rbac_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_roles_updated_at BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION update_rbac_updated_at_column();

CREATE TRIGGER update_permissions_updated_at BEFORE UPDATE ON permissions
    FOR EACH ROW EXECUTE FUNCTION update_rbac_updated_at_column();
