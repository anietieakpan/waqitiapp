-- ============================================================================
-- V2025.10.26.001__create_dlq_recovery_tables.sql
-- Dead Letter Queue Recovery Tables
-- ============================================================================

CREATE TABLE IF NOT EXISTS dlq_events (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL UNIQUE,
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    original_topic VARCHAR(255) NOT NULL,
    payload JSONB,
    error_message TEXT,
    stack_trace TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_count INTEGER NOT NULL DEFAULT 5,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    first_failed_at TIMESTAMP NOT NULL,
    last_retry_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_dlq_event_id ON dlq_events(event_id);
CREATE INDEX idx_dlq_service_name ON dlq_events(service_name);
CREATE INDEX idx_dlq_status ON dlq_events(status);
CREATE INDEX idx_dlq_retry_count ON dlq_events(retry_count);
CREATE INDEX idx_dlq_first_failed_at ON dlq_events(first_failed_at);
CREATE INDEX idx_dlq_priority ON dlq_events(priority);

-- Manual Review Cases Table
CREATE TABLE IF NOT EXISTS dlq_manual_review_cases (
                                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id VARCHAR(255) NOT NULL UNIQUE,
    event_id VARCHAR(255) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB,
    error_message TEXT,
    retry_count INTEGER,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    assigned_to UUID,
    resolution TEXT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_manual_review_status ON dlq_manual_review_cases(status);
CREATE INDEX idx_manual_review_assigned ON dlq_manual_review_cases(assigned_to);
CREATE INDEX idx_manual_review_priority ON dlq_manual_review_cases(priority);

-- Dead Storage Archive Table
CREATE TABLE IF NOT EXISTS dlq_dead_storage (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL UNIQUE,
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB,
    original_topic VARCHAR(255),
    error_message TEXT,
    retry_count INTEGER,
    first_failed_at TIMESTAMP,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT
    );

CREATE INDEX idx_dead_storage_service ON dlq_dead_storage(service_name);
CREATE INDEX idx_dead_storage_archived ON dlq_dead_storage(archived_at);

-- Recovery Attempts Audit Log
CREATE TABLE IF NOT EXISTS dlq_recovery_attempts (
                                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dlq_event_id UUID NOT NULL REFERENCES dlq_events(id),
    recovery_action VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempted_by VARCHAR(100) DEFAULT 'SYSTEM',
    execution_time_ms BIGINT
    );

CREATE INDEX idx_recovery_attempts_event ON dlq_recovery_attempts(dlq_event_id);
CREATE INDEX idx_recovery_attempts_attempted ON dlq_recovery_attempts(attempted_at);