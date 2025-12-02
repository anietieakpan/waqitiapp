-- Migration: Add Family Guardian Approval Tables
-- Version: V16
-- Description: Add comprehensive family guardian approval system with MFA

-- Family Guardianships Table
CREATE TABLE family_guardianships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guardian_user_id UUID NOT NULL,
    dependent_user_id UUID NOT NULL,
    guardian_type VARCHAR(20) NOT NULL CHECK (guardian_type IN ('PRIMARY', 'SECONDARY', 'EMERGENCY', 'TEMPORARY')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED')),
    established_by UUID,
    relationship_type VARCHAR(50),
    legal_document_reference VARCHAR(255),
    emergency_contact BOOLEAN DEFAULT FALSE,
    financial_oversight BOOLEAN DEFAULT TRUE,
    can_approve_transactions BOOLEAN DEFAULT TRUE,
    max_approval_amount DECIMAL(15,2),
    can_modify_limits BOOLEAN DEFAULT FALSE,
    can_view_statements BOOLEAN DEFAULT TRUE,
    notification_preferences TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    FOREIGN KEY (guardian_user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (dependent_user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (guardian_user_id, dependent_user_id)
);

-- Guardianship Permissions Table
CREATE TABLE guardianship_permissions (
    guardianship_id UUID NOT NULL,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (guardianship_id, permission),
    FOREIGN KEY (guardianship_id) REFERENCES family_guardianships(id) ON DELETE CASCADE
);

-- Guardian Approval Requests Table
CREATE TABLE guardian_approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_request_id VARCHAR(255) NOT NULL UNIQUE,
    dependent_user_id UUID NOT NULL,
    action_id VARCHAR(255) NOT NULL,
    action_type VARCHAR(50) NOT NULL CHECK (action_type IN ('DELETE_ACCOUNT', 'TRANSFER_OWNERSHIP', 'CHANGE_GUARDIAN', 'LARGE_TRANSACTION', 'CHANGE_LIMITS', 'CHANGE_PROFILE', 'CHANGE_SETTINGS', 'VIEW_STATEMENTS', 'MINOR_SETTINGS')),
    risk_level VARCHAR(20) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    all_guardians_must_approve BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    rejection_reason TEXT,
    action_details TEXT,
    context_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    FOREIGN KEY (dependent_user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Required Guardians for Approval Requests
CREATE TABLE guardian_approval_required_guardians (
    approval_request_id UUID NOT NULL,
    guardian_id UUID NOT NULL,
    PRIMARY KEY (approval_request_id, guardian_id),
    FOREIGN KEY (approval_request_id) REFERENCES guardian_approval_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (guardian_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Guardian Approvals Table
CREATE TABLE guardian_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_request_id UUID NOT NULL,
    guardian_id UUID NOT NULL,
    approved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_method VARCHAR(50),
    device_info TEXT,
    ip_address INET,
    user_agent TEXT,
    version BIGINT DEFAULT 0,
    FOREIGN KEY (approval_request_id) REFERENCES guardian_approval_requests(id) ON DELETE CASCADE,
    FOREIGN KEY (guardian_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (approval_request_id, guardian_id)
);

-- Indexes for Performance

-- Family Guardianships indexes
CREATE INDEX idx_family_guardianships_guardian_user ON family_guardianships(guardian_user_id);
CREATE INDEX idx_family_guardianships_dependent_user ON family_guardianships(dependent_user_id);
CREATE INDEX idx_family_guardianships_status ON family_guardianships(status);
CREATE INDEX idx_family_guardianships_guardian_status ON family_guardianships(guardian_user_id, status);
CREATE INDEX idx_family_guardianships_dependent_status ON family_guardianships(dependent_user_id, status);
CREATE INDEX idx_family_guardianships_expires_at ON family_guardianships(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_family_guardianships_last_activity ON family_guardianships(last_activity_at);
CREATE INDEX idx_family_guardianships_guardian_type_status ON family_guardianships(guardian_type, status);
CREATE INDEX idx_family_guardianships_financial ON family_guardianships(dependent_user_id, financial_oversight, can_approve_transactions) WHERE status = 'ACTIVE';

-- Guardian Approval Requests indexes
CREATE INDEX idx_guardian_approval_requests_dependent ON guardian_approval_requests(dependent_user_id);
CREATE INDEX idx_guardian_approval_requests_status ON guardian_approval_requests(status);
CREATE INDEX idx_guardian_approval_requests_action_id ON guardian_approval_requests(action_id);
CREATE INDEX idx_guardian_approval_requests_expires_at ON guardian_approval_requests(expires_at);
CREATE INDEX idx_guardian_approval_requests_created_at ON guardian_approval_requests(created_at);
CREATE INDEX idx_guardian_approval_requests_dependent_status ON guardian_approval_requests(dependent_user_id, status);
CREATE INDEX idx_guardian_approval_requests_action_type ON guardian_approval_requests(action_type);
CREATE INDEX idx_guardian_approval_requests_risk_level ON guardian_approval_requests(risk_level);

-- Guardian Approvals indexes
CREATE INDEX idx_guardian_approvals_request ON guardian_approvals(approval_request_id);
CREATE INDEX idx_guardian_approvals_guardian ON guardian_approvals(guardian_id);
CREATE INDEX idx_guardian_approvals_approved_at ON guardian_approvals(approved_at);
CREATE INDEX idx_guardian_approvals_ip_address ON guardian_approvals(ip_address);

-- Required Guardians indexes
CREATE INDEX idx_required_guardians_guardian ON guardian_approval_required_guardians(guardian_id);

-- Permissions indexes
CREATE INDEX idx_guardianship_permissions_permission ON guardianship_permissions(permission);

-- Insert default permissions for existing guardian types
INSERT INTO guardianship_permissions (guardianship_id, permission)
SELECT id, 'ACCOUNT_MANAGEMENT' FROM family_guardianships WHERE guardian_type = 'PRIMARY' AND status = 'ACTIVE'
ON CONFLICT DO NOTHING;

INSERT INTO guardianship_permissions (guardianship_id, permission)
SELECT id, 'FINANCIAL_OVERSIGHT' FROM family_guardianships WHERE guardian_type IN ('PRIMARY', 'SECONDARY') AND status = 'ACTIVE'
ON CONFLICT DO NOTHING;

INSERT INTO guardianship_permissions (guardianship_id, permission)
SELECT id, 'BASIC_OVERSIGHT' FROM family_guardianships WHERE status = 'ACTIVE'
ON CONFLICT DO NOTHING;

-- Add constraints and triggers

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_family_guardianships_updated_at 
    BEFORE UPDATE ON family_guardianships 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_guardian_approval_requests_updated_at 
    BEFORE UPDATE ON guardian_approval_requests 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Business rule constraints
ALTER TABLE family_guardianships 
ADD CONSTRAINT chk_guardian_not_self 
CHECK (guardian_user_id != dependent_user_id);

ALTER TABLE guardian_approval_requests 
ADD CONSTRAINT chk_expires_at_future 
CHECK (expires_at > created_at);

-- Partial indexes for better performance on common queries
CREATE INDEX idx_active_guardianships 
ON family_guardianships(dependent_user_id, guardian_user_id) 
WHERE status = 'ACTIVE' AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP);

CREATE INDEX idx_pending_approval_requests 
ON guardian_approval_requests(dependent_user_id, created_at) 
WHERE status = 'PENDING';

CREATE INDEX idx_guardian_pending_requests 
ON guardian_approval_required_guardians(guardian_id);

-- Comments for documentation
COMMENT ON TABLE family_guardianships IS 'Guardian-dependent relationships for family accounts with permissions and approval workflows';
COMMENT ON TABLE guardian_approval_requests IS 'Approval requests requiring guardian consent with 2FA verification';
COMMENT ON TABLE guardian_approvals IS 'Individual guardian approvals with MFA verification context';
COMMENT ON TABLE guardianship_permissions IS 'Specific permissions granted to guardians over dependent accounts';

COMMENT ON COLUMN family_guardianships.guardian_type IS 'PRIMARY: Full control, SECONDARY: Limited access, EMERGENCY: Emergency only, TEMPORARY: Time-limited';
COMMENT ON COLUMN family_guardianships.max_approval_amount IS 'Maximum transaction amount guardian can approve (NULL = unlimited)';
COMMENT ON COLUMN guardian_approval_requests.all_guardians_must_approve IS 'TRUE for critical actions requiring all guardian approval';
COMMENT ON COLUMN guardian_approval_requests.risk_level IS 'Risk assessment level determining approval requirements';