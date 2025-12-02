-- ==============================================================================
-- V100: Production Readiness Tables
-- Created: 2025-11-10
-- Purpose: Create tables for DLQ handling, PII audit, idempotency, saga state
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. DLQ Events Table
-- Purpose: Track all dead letter queue events with full audit trail
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dlq_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(500) NOT NULL,
    business_identifier VARCHAR(255),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    recovery_strategy VARCHAR(50) NOT NULL CHECK (recovery_strategy IN (
        'RETRY_WITH_BACKOFF',
        'MANUAL_REVIEW',
        'SECURITY_ALERT',
        'COMPENSATE',
        'LOG_AND_IGNORE',
        'DEFER_TO_BATCH',
        'ESCALATE_TO_ENGINEERING'
    )),
    original_topic VARCHAR(255),
    partition INTEGER,
    offset BIGINT,
    consumer_group VARCHAR(255),
    retry_attempts INTEGER NOT NULL DEFAULT 0,
    first_failure_time TIMESTAMP,
    dlq_entry_time TIMESTAMP,
    original_event TEXT,
    headers TEXT,
    failure_reason TEXT,
    failure_stack_trace TEXT,
    metadata TEXT,
    processed_at TIMESTAMP,
    recovery_result TEXT,
    recovery_status VARCHAR(50),
    requires_manual_intervention BOOLEAN DEFAULT FALSE,
    ticket_number VARCHAR(100),
    recovery_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- DLQ indexes for efficient querying
CREATE INDEX idx_dlq_business_id ON dlq_events(business_identifier);
CREATE INDEX idx_dlq_severity ON dlq_events(severity);
CREATE INDEX idx_dlq_status ON dlq_events(recovery_status);
CREATE INDEX idx_dlq_requires_manual ON dlq_events(requires_manual_intervention) WHERE requires_manual_intervention = true;
CREATE INDEX idx_dlq_created_at ON dlq_events(created_at DESC);
CREATE INDEX idx_dlq_event_type ON dlq_events(event_type);
CREATE INDEX idx_dlq_consumer_group ON dlq_events(consumer_group);
CREATE INDEX idx_dlq_unprocessed ON dlq_events(processed_at) WHERE processed_at IS NULL;
CREATE INDEX idx_dlq_critical_unprocessed ON dlq_events(severity, processed_at)
    WHERE severity = 'CRITICAL' AND processed_at IS NULL;

COMMENT ON TABLE dlq_events IS 'Dead Letter Queue events with full recovery audit trail';
COMMENT ON COLUMN dlq_events.severity IS 'P0=CRITICAL, P1=HIGH, P2=MEDIUM, P3=LOW, P4=INFO';
COMMENT ON COLUMN dlq_events.retry_attempts IS 'Number of retry attempts (max 5)';

-- ------------------------------------------------------------------------------
-- 2. PII Access Audit Log
-- Purpose: GDPR Article 30 compliance - track all PII access
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pii_access_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    accessed_by_user_id UUID NOT NULL,
    accessed_by_username VARCHAR(255),
    accessed_by_role VARCHAR(100),
    data_fields TEXT[] NOT NULL,
    access_reason VARCHAR(500) NOT NULL,
    legal_basis VARCHAR(100) NOT NULL CHECK (legal_basis IN (
        'CONSENT',
        'CONTRACT',
        'LEGAL_OBLIGATION',
        'VITAL_INTERESTS',
        'PUBLIC_TASK',
        'LEGITIMATE_INTERESTS'
    )),
    consent_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address INET,
    user_agent TEXT,
    request_id VARCHAR(100),
    session_id VARCHAR(255),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pii_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_pii_accessor FOREIGN KEY (accessed_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- PII audit indexes
CREATE INDEX idx_pii_user_id ON pii_access_log(user_id);
CREATE INDEX idx_pii_accessed_by ON pii_access_log(accessed_by_user_id);
CREATE INDEX idx_pii_accessed_at ON pii_access_log(accessed_at DESC);
CREATE INDEX idx_pii_legal_basis ON pii_access_log(legal_basis);
CREATE INDEX idx_pii_fields ON pii_access_log USING GIN(data_fields);

COMMENT ON TABLE pii_access_log IS 'GDPR Article 30 - Records of PII processing activities';
COMMENT ON COLUMN pii_access_log.legal_basis IS 'GDPR Article 6 - Legal basis for processing';

-- ------------------------------------------------------------------------------
-- 3. Idempotency Keys
-- Purpose: Prevent duplicate financial operations
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    request_body TEXT,
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    user_id UUID,
    endpoint VARCHAR(500),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Idempotency indexes
CREATE INDEX idx_idempotency_user_id ON idempotency_keys(user_id);
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);
CREATE INDEX idx_idempotency_created_at ON idempotency_keys(created_at DESC);

COMMENT ON TABLE idempotency_keys IS 'Financial operation idempotency to prevent duplicates';
COMMENT ON COLUMN idempotency_keys.expires_at IS 'Keys expire after 24 hours';

-- ------------------------------------------------------------------------------
-- 4. Saga State
-- Purpose: Distributed transaction coordination with compensation
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS saga_states (
    saga_id VARCHAR(100) PRIMARY KEY,
    saga_type VARCHAR(50) NOT NULL CHECK (saga_type IN (
        'USER_REGISTRATION',
        'ACCOUNT_DELETION',
        'KYC_VERIFICATION',
        'PAYMENT_PROCESSING'
    )),
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'STARTED',
        'IN_PROGRESS',
        'COMPLETED',
        'COMPENSATING',
        'COMPENSATED',
        'FAILED'
    )),
    completed_steps TEXT,
    request_data TEXT NOT NULL,
    failure_reason TEXT,
    compensation_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Saga indexes
CREATE INDEX idx_saga_status ON saga_states(status);
CREATE INDEX idx_saga_type ON saga_states(saga_type);
CREATE INDEX idx_saga_created_at ON saga_states(created_at DESC);
CREATE INDEX idx_saga_active ON saga_states(status) WHERE status IN ('STARTED', 'IN_PROGRESS', 'COMPENSATING');

COMMENT ON TABLE saga_states IS 'Saga pattern for distributed transactions with compensation';
COMMENT ON COLUMN saga_states.completed_steps IS 'JSON array of completed saga steps';

-- ------------------------------------------------------------------------------
-- 5. GDPR Manual Interventions
-- Purpose: Track failed GDPR operations requiring manual processing
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS gdpr_manual_interventions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number VARCHAR(100) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    operation_type VARCHAR(50) NOT NULL CHECK (operation_type IN (
        'WALLET_ANONYMIZATION_FAILED',
        'PAYMENT_ANONYMIZATION_FAILED',
        'TRANSACTION_ANONYMIZATION_FAILED',
        'DATA_EXPORT_FAILED',
        'DATA_DELETION_FAILED'
    )),
    description TEXT NOT NULL,
    failure_reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
        'PENDING',
        'IN_PROGRESS',
        'RESOLVED',
        'ESCALATED'
    )),
    sla_deadline TIMESTAMP NOT NULL,
    assigned_to VARCHAR(255),
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_gdpr_intervention_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- GDPR intervention indexes
CREATE INDEX idx_gdpr_ticket ON gdpr_manual_interventions(ticket_number);
CREATE INDEX idx_gdpr_user_id ON gdpr_manual_interventions(user_id);
CREATE INDEX idx_gdpr_status ON gdpr_manual_interventions(status);
CREATE INDEX idx_gdpr_sla ON gdpr_manual_interventions(sla_deadline) WHERE status = 'PENDING';
CREATE INDEX idx_gdpr_created_at ON gdpr_manual_interventions(created_at DESC);

COMMENT ON TABLE gdpr_manual_interventions IS 'GDPR operations requiring manual intervention within 30-day SLA';

-- ------------------------------------------------------------------------------
-- 6. Password Upgrade Tracking
-- Purpose: Track password hash upgrades from BCrypt 12 to 14 rounds
-- ------------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_upgraded_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash_version INTEGER NOT NULL DEFAULT 12;

CREATE INDEX IF NOT EXISTS idx_users_password_upgrade ON users(password_hash_version, password_upgraded_at);

COMMENT ON COLUMN users.password_upgraded_at IS 'Timestamp when password was upgraded to stronger hash';
COMMENT ON COLUMN users.password_hash_version IS 'BCrypt rounds: 12 (old) or 14 (new, NIST recommended)';

-- ------------------------------------------------------------------------------
-- 7. Session Tracking Enhancement
-- Purpose: Support concurrent session limits
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_active_sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL,
    device_fingerprint VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Session indexes
CREATE INDEX idx_active_sessions_user ON user_active_sessions(user_id, last_accessed_at DESC);
CREATE INDEX idx_active_sessions_expires ON user_active_sessions(expires_at);

COMMENT ON TABLE user_active_sessions IS 'Track active user sessions for concurrent login limits';

-- ------------------------------------------------------------------------------
-- 8. Health Check Metadata
-- Purpose: Store health check results for monitoring
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS health_check_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('UP', 'DOWN', 'DEGRADED')),
    response_time_ms INTEGER,
    error_message TEXT,
    metadata TEXT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_health_check_type ON health_check_results(check_type, checked_at DESC);
CREATE INDEX idx_health_check_status ON health_check_results(status, checked_at DESC);

COMMENT ON TABLE health_check_results IS 'Historical health check results for trend analysis';

-- ------------------------------------------------------------------------------
-- 8. Verification Tokens Table
-- Purpose: Store email and phone verification tokens with expiry
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL CHECK (type IN (
        'EMAIL_VERIFICATION',
        'PHONE_VERIFICATION',
        'PASSWORD_RESET',
        'TWO_FACTOR_AUTH'
    )),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    is_used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_verification_token ON verification_tokens(token);
CREATE INDEX idx_verification_user_id ON verification_tokens(user_id);
CREATE INDEX idx_verification_type ON verification_tokens(type);
CREATE INDEX idx_verification_expires_at ON verification_tokens(expires_at);

COMMENT ON TABLE verification_tokens IS 'Email and phone verification tokens with automatic expiry';

-- ==============================================================================
-- Data Retention Policies
-- ==============================================================================

COMMENT ON TABLE dlq_events IS $$
Retention: 90 days for processed events, unlimited for unprocessed
Cleanup: Automated job runs daily at 2 AM
$$;

COMMENT ON TABLE pii_access_log IS $$
Retention: 7 years (GDPR Article 30)
Cleanup: No automatic deletion - regulatory requirement
$$;

COMMENT ON TABLE idempotency_keys IS $$
Retention: 24 hours
Cleanup: Automated job deletes expired keys hourly
$$;

COMMENT ON TABLE saga_states IS $$
Retention: 90 days for completed sagas
Cleanup: Automated job runs weekly
$$;

COMMENT ON TABLE verification_tokens IS $$
Retention: 7 days after expiration
Cleanup: Automated job deletes expired tokens daily
$$;

-- ==============================================================================
-- Migration Complete
-- Tables Created: 8 (dlq_events, pii_access_log, idempotency_keys, saga_states,
--                    gdpr_manual_interventions, user_active_sessions,
--                    health_check_results, verification_tokens)
-- Indexes Created: 35+
-- ==============================================================================
