-- Migration V002: Add transaction_attempts table for refactored authorization service
-- Date: 2025-10-17
-- Description: Creates transaction_attempts table to record all transaction authorization attempts

CREATE TABLE IF NOT EXISTS transaction_attempts (
    id BIGSERIAL PRIMARY KEY,
    family_member_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL CHECK (amount > 0),
    merchant_name VARCHAR(200),
    merchant_category VARCHAR(100),
    description VARCHAR(500),
    attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    authorized BOOLEAN NOT NULL DEFAULT FALSE,
    decline_reason VARCHAR(500),
    requires_parent_approval BOOLEAN NOT NULL DEFAULT FALSE,
    approval_status VARCHAR(20) DEFAULT 'NOT_REQUIRED' CHECK (approval_status IN ('PENDING', 'APPROVED', 'DECLINED', 'NOT_REQUIRED')),
    approved_by_user_id VARCHAR(50),
    approval_time TIMESTAMP,
    transaction_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transaction_attempts_member
        FOREIGN KEY (family_member_id)
        REFERENCES family_members(id)
        ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_transaction_attempts_member ON transaction_attempts(family_member_id);
CREATE INDEX idx_transaction_attempts_status ON transaction_attempts(approval_status);
CREATE INDEX idx_transaction_attempts_time ON transaction_attempts(attempt_time);
CREATE INDEX idx_transaction_attempts_authorized ON transaction_attempts(authorized);
CREATE INDEX idx_transaction_attempts_created ON transaction_attempts(created_at);

-- Add comment
COMMENT ON TABLE transaction_attempts IS 'Records all transaction authorization attempts for audit trail and fraud detection';
COMMENT ON COLUMN transaction_attempts.authorized IS 'Whether transaction was authorized (true) or declined (false)';
COMMENT ON COLUMN transaction_attempts.requires_parent_approval IS 'Whether transaction requires parent approval before processing';
COMMENT ON COLUMN transaction_attempts.approval_status IS 'Status of parent approval: PENDING, APPROVED, DECLINED, or NOT_REQUIRED';
