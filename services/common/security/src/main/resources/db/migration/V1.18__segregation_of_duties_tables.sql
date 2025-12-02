-- Segregation of Duties (SoD) Database Schema
-- SOX Compliance - Sarbanes-Oxley Act Section 404

-- Transaction Actions Audit Table
-- Records every action performed on a transaction for SoD validation
CREATE TABLE IF NOT EXISTS transaction_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    user_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    session_id VARCHAR(255),
    metadata JSONB,

    CONSTRAINT fk_transaction_actions_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
);

-- Indexes for fast lookup
CREATE INDEX idx_transaction_actions_transaction_id
    ON transaction_actions(transaction_id);

CREATE INDEX idx_transaction_actions_user_id
    ON transaction_actions(user_id);

CREATE INDEX idx_transaction_actions_performed_at
    ON transaction_actions(performed_at);

CREATE INDEX idx_transaction_actions_action
    ON transaction_actions(action);

-- Composite index for common query pattern
CREATE INDEX idx_transaction_actions_transaction_user
    ON transaction_actions(transaction_id, user_id);

-- ============================================================================

-- Maker-Checker Records Table
-- Tracks maker-checker pairs for dual authorization
CREATE TABLE IF NOT EXISTS maker_checker_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL UNIQUE,
    maker_id UUID NOT NULL,
    checker_id UUID NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    comments TEXT,

    CONSTRAINT fk_maker_checker_maker
        FOREIGN KEY (maker_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_maker_checker_checker
        FOREIGN KEY (checker_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    -- Ensure maker and checker are different users
    CONSTRAINT chk_maker_checker_different
        CHECK (maker_id != checker_id)
);

-- Indexes
CREATE INDEX idx_maker_checker_transaction_id
    ON maker_checker_records(transaction_id);

CREATE INDEX idx_maker_checker_maker_id
    ON maker_checker_records(maker_id);

CREATE INDEX idx_maker_checker_checker_id
    ON maker_checker_records(checker_id);

-- ============================================================================

-- SoD Violations Table
-- Records all segregation of duties violations for compliance reporting
CREATE TABLE IF NOT EXISTS sod_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    violation_type VARCHAR(100) NOT NULL,
    action_1 VARCHAR(100),
    action_2 VARCHAR(100),
    description TEXT NOT NULL,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Resolution tracking
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by UUID,
    resolution TEXT,

    -- Severity and impact
    severity VARCHAR(20) DEFAULT 'HIGH',
    business_impact TEXT,

    -- Related entities
    transaction_id UUID,
    resource_type VARCHAR(100),
    resource_id UUID,

    -- Metadata
    metadata JSONB,

    CONSTRAINT fk_sod_violations_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_sod_violations_resolved_by
        FOREIGN KEY (resolved_by)
        REFERENCES users(id)
        ON DELETE RESTRICT
);

-- Indexes for reporting and analysis
CREATE INDEX idx_sod_violations_user_id
    ON sod_violations(user_id);

CREATE INDEX idx_sod_violations_detected_at
    ON sod_violations(detected_at);

CREATE INDEX idx_sod_violations_resolved
    ON sod_violations(resolved);

CREATE INDEX idx_sod_violations_violation_type
    ON sod_violations(violation_type);

CREATE INDEX idx_sod_violations_severity
    ON sod_violations(severity);

-- Composite index for unresolved critical violations
CREATE INDEX idx_sod_violations_unresolved_critical
    ON sod_violations(resolved, severity, detected_at)
    WHERE resolved = FALSE AND severity = 'CRITICAL';

-- ============================================================================

-- Role Incompatibility Matrix Table
-- Defines which roles are incompatible (cannot be held by same user)
CREATE TABLE IF NOT EXISTS role_incompatibility_matrix (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_1_id UUID NOT NULL,
    role_2_id UUID NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    active BOOLEAN DEFAULT TRUE,

    CONSTRAINT fk_role_incompatibility_role1
        FOREIGN KEY (role_1_id)
        REFERENCES roles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_role_incompatibility_role2
        FOREIGN KEY (role_2_id)
        REFERENCES roles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_role_incompatibility_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    -- Prevent duplicate entries
    CONSTRAINT uk_role_incompatibility
        UNIQUE (role_1_id, role_2_id)
);

-- Indexes
CREATE INDEX idx_role_incompatibility_role1
    ON role_incompatibility_matrix(role_1_id);

CREATE INDEX idx_role_incompatibility_role2
    ON role_incompatibility_matrix(role_2_id);

CREATE INDEX idx_role_incompatibility_active
    ON role_incompatibility_matrix(active)
    WHERE active = TRUE;

-- ============================================================================

-- Dual Authorization Requirements Table
-- Defines which actions require dual authorization
CREATE TABLE IF NOT EXISTS dual_authorization_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type VARCHAR(100) NOT NULL UNIQUE,
    required BOOLEAN DEFAULT TRUE,
    minimum_approvers INT DEFAULT 2,
    required_approval_roles TEXT[], -- Array of role names
    amount_threshold DECIMAL(19, 4), -- Threshold amount for requirement
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN DEFAULT TRUE
);

-- Indexes
CREATE INDEX idx_dual_auth_action_type
    ON dual_authorization_requirements(action_type);

CREATE INDEX idx_dual_auth_active
    ON dual_authorization_requirements(active)
    WHERE active = TRUE;

-- ============================================================================

-- Insert default dual authorization requirements
INSERT INTO dual_authorization_requirements (action_type, required, minimum_approvers, description, amount_threshold)
VALUES
    ('PAYMENT_APPROVE', TRUE, 2, 'Payment approval requires dual authorization', 10000.00),
    ('TRANSFER_APPROVE', TRUE, 2, 'Transfer approval requires dual authorization', 10000.00),
    ('LIMIT_INCREASE', TRUE, 2, 'Credit limit increase requires dual authorization', NULL),
    ('ACCOUNT_FREEZE', TRUE, 2, 'Account freeze requires dual authorization', NULL),
    ('ACCOUNT_UNFREEZE', TRUE, 2, 'Account unfreeze requires dual authorization', NULL),
    ('TRANSACTION_VOID', TRUE, 2, 'Transaction void requires dual authorization', 5000.00),
    ('LARGE_WITHDRAWAL', TRUE, 2, 'Large withdrawal requires dual authorization', 10000.00),
    ('REFUND_LARGE', TRUE, 2, 'Large refund requires dual authorization', 5000.00),
    ('ROLE_ASSIGNMENT_ADMIN', TRUE, 2, 'Admin role assignment requires dual authorization', NULL),
    ('PERMISSION_GRANT_HIGH', TRUE, 2, 'High-privilege permission grant requires dual authorization', NULL)
ON CONFLICT (action_type) DO NOTHING;

-- ============================================================================

-- Comments for documentation
COMMENT ON TABLE transaction_actions IS 'Audit trail of all actions performed on transactions for SoD compliance';
COMMENT ON TABLE maker_checker_records IS 'Maker-checker pairs for dual authorization compliance';
COMMENT ON TABLE sod_violations IS 'Historical record of segregation of duties violations';
COMMENT ON TABLE role_incompatibility_matrix IS 'Defines incompatible role combinations that violate SoD';
COMMENT ON TABLE dual_authorization_requirements IS 'Configuration for actions requiring dual authorization';

COMMENT ON COLUMN transaction_actions.action IS 'Action performed (e.g., PAYMENT_CREATE, PAYMENT_APPROVE)';
COMMENT ON COLUMN transaction_actions.metadata IS 'Additional context about the action';

COMMENT ON COLUMN sod_violations.violation_type IS 'Type of SoD violation (e.g., ROLE_ASSIGNMENT, TRANSACTION_ACTION)';
COMMENT ON COLUMN sod_violations.severity IS 'Severity: CRITICAL, HIGH, MEDIUM, LOW';
COMMENT ON COLUMN sod_violations.resolved IS 'Whether the violation has been resolved/addressed';

-- ============================================================================

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_dual_auth_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_dual_auth_updated_at
    BEFORE UPDATE ON dual_authorization_requirements
    FOR EACH ROW
    EXECUTE FUNCTION update_dual_auth_updated_at();

-- ============================================================================

-- Grant permissions (adjust as needed for your environment)
-- GRANT SELECT, INSERT ON transaction_actions TO app_user;
-- GRANT SELECT, INSERT ON maker_checker_records TO app_user;
-- GRANT SELECT, INSERT, UPDATE ON sod_violations TO app_user;
-- GRANT SELECT ON role_incompatibility_matrix TO app_user;
-- GRANT SELECT ON dual_authorization_requirements TO app_user;
