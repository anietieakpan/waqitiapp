-- ==================================================================================
-- Event Snapshots Table - Event Sourcing Optimization
-- ==================================================================================

CREATE TABLE IF NOT EXISTS event_snapshots (
    id VARCHAR(36) PRIMARY KEY COMMENT 'Snapshot UUID',
    aggregate_id VARCHAR(36) NOT NULL COMMENT 'ID of aggregate root',
    aggregate_type VARCHAR(100) NOT NULL COMMENT 'Account, Wallet, Payment, Transfer',
    snapshot_data JSON NOT NULL COMMENT 'Serialized aggregate state',
    sequence_number BIGINT NOT NULL COMMENT 'Last event sequence included in snapshot',
    snapshot_timestamp TIMESTAMP NOT NULL COMMENT 'When snapshot was created',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_aggregate_id (aggregate_id),
    INDEX idx_aggregate_type (aggregate_type),
    INDEX idx_timestamp (snapshot_timestamp DESC),
    INDEX idx_aggregate_sequence (aggregate_id, sequence_number DESC),
    UNIQUE KEY uk_aggregate_snapshot (aggregate_id, sequence_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Event sourcing snapshots for performance optimization';
