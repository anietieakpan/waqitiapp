-- Create account_locks table for tracking account security locks
CREATE TABLE IF NOT EXISTS account_locks (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(100),
    account_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36),
    lock_type VARCHAR(50) NOT NULL,
    lock_reason VARCHAR(100) NOT NULL,
    lock_description VARCHAR(1000),
    status VARCHAR(50) NOT NULL DEFAULT 'INITIATED',
    triggered_by VARCHAR(100),
    source_system VARCHAR(100),
    ip_address VARCHAR(45),
    device_id VARCHAR(100),
    failed_attempts INTEGER,
    account_locked BOOLEAN DEFAULT FALSE,
    sessions_terminated INTEGER DEFAULT 0,
    tokens_revoked INTEGER DEFAULT 0,
    api_keys_invalidated INTEGER DEFAULT 0,
    transactions_blocked INTEGER DEFAULT 0,
    password_reset_required BOOLEAN DEFAULT FALSE,
    mfa_required BOOLEAN DEFAULT FALSE,
    related_accounts_count INTEGER DEFAULT 0,
    related_accounts_locked BOOLEAN DEFAULT FALSE,
    scheduled_unlock_at TIMESTAMP,
    lock_duration_minutes BIGINT,
    locked_at TIMESTAMP,
    activated_at TIMESTAMP,
    unlocked_at TIMESTAMP,
    upgraded_at TIMESTAMP,
    security_action_error VARCHAR(500),
    operation_error VARCHAR(500),
    related_accounts_error VARCHAR(500),
    failure_reason VARCHAR(500),
    correlation_id VARCHAR(100),
    processing_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Create indexes for performance
CREATE INDEX idx_account_lock_account_id ON account_locks(account_id);
CREATE INDEX idx_account_lock_status ON account_locks(status);
CREATE INDEX idx_account_lock_event_id ON account_locks(event_id);
CREATE INDEX idx_account_lock_user_id ON account_locks(user_id);
CREATE INDEX idx_account_lock_correlation_id ON account_locks(correlation_id);
CREATE INDEX idx_account_lock_created_at ON account_locks(created_at);
CREATE INDEX idx_account_lock_scheduled_unlock ON account_locks(scheduled_unlock_at);
CREATE INDEX idx_account_lock_lock_type ON account_locks(lock_type);
CREATE INDEX idx_account_lock_lock_reason ON account_locks(lock_reason);

-- Create composite index for active locks
CREATE INDEX idx_account_lock_active_lookup ON account_locks(account_id, status, locked_at);

-- Add comments for documentation
COMMENT ON TABLE account_locks IS 'Tracks all account security locks including temporary and permanent restrictions';
COMMENT ON COLUMN account_locks.lock_type IS 'Type of lock: TEMPORARY, PERMANENT, CONDITIONAL, SOFT, HARD';
COMMENT ON COLUMN account_locks.lock_reason IS 'Reason for lock with severity level';
COMMENT ON COLUMN account_locks.status IS 'Current status of the lock';
COMMENT ON COLUMN account_locks.sessions_terminated IS 'Number of sessions terminated during lock';
COMMENT ON COLUMN account_locks.tokens_revoked IS 'Number of tokens revoked during lock';
COMMENT ON COLUMN account_locks.version IS 'Optimistic locking version field';
