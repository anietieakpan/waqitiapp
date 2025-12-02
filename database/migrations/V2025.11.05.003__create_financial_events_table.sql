-- ==================================================================================
-- Financial Events Table - Event Sourcing Implementation
-- ==================================================================================

CREATE TABLE IF NOT EXISTS financial_events (
    id VARCHAR(36) PRIMARY KEY COMMENT 'Event UUID',
    aggregate_id VARCHAR(36) NOT NULL COMMENT 'ID of aggregate root (Account, Wallet, etc.)',
    aggregate_type VARCHAR(100) NOT NULL COMMENT 'Account, Wallet, Payment, Transfer',
    event_type VARCHAR(255) NOT NULL COMMENT 'Fully qualified event class name',
    event_data JSON NOT NULL COMMENT 'Serialized event payload',
    event_metadata JSON COMMENT 'Additional context (user, timestamp, etc.)',
    sequence_number BIGINT NOT NULL COMMENT 'Sequential number within aggregate',
    occurred_at TIMESTAMP NOT NULL COMMENT 'When event occurred',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When persisted to database',
    
    INDEX idx_aggregate_id (aggregate_id),
    INDEX idx_aggregate_type (aggregate_type),
    INDEX idx_event_type (event_type),
    INDEX idx_occurred_at (occurred_at DESC),
    INDEX idx_sequence (aggregate_id, sequence_number),
    INDEX idx_type_occurred (event_type, occurred_at DESC),
    UNIQUE KEY uk_aggregate_sequence (aggregate_id, sequence_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Event store for financial domain events - Immutable append-only log';
