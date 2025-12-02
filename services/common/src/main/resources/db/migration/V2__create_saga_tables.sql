-- Saga Pattern Implementation Tables
-- For managing distributed transaction state

-- Main saga state table
CREATE TABLE IF NOT EXISTS saga_states (
    saga_id VARCHAR(36) PRIMARY KEY,
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    saga_data JSONB NOT NULL,
    error_message TEXT,
    version INT DEFAULT 0,
    
    CONSTRAINT chk_saga_status CHECK (status IN (
        'RUNNING', 'COMPLETED', 'FAILED', 'COMPENSATING', 
        'COMPENSATED', 'COMPENSATION_FAILED', 'CANCELLED', 'TIMED_OUT'
    ))
);

-- Saga step states table
CREATE TABLE IF NOT EXISTS saga_step_states (
    step_id VARCHAR(36) PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL REFERENCES saga_states(saga_id) ON DELETE CASCADE,
    step_name VARCHAR(100) NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    result_data JSONB,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    compensation_data JSONB,
    
    CONSTRAINT chk_step_status CHECK (status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'COMPENSATED', 'SKIPPED'
    ))
);

-- Saga timeout tracking
CREATE TABLE IF NOT EXISTS saga_timeouts (
    timeout_id VARCHAR(36) PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL REFERENCES saga_states(saga_id) ON DELETE CASCADE,
    timeout_at TIMESTAMP NOT NULL,
    is_processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Saga choreography events (for choreography pattern)
CREATE TABLE IF NOT EXISTS saga_choreography_events (
    event_id VARCHAR(36) PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source_service VARCHAR(100) NOT NULL,
    target_service VARCHAR(100),
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    
    CONSTRAINT chk_choreography_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

-- Indexes for performance
CREATE INDEX idx_saga_states_type ON saga_states(saga_type);
CREATE INDEX idx_saga_states_status ON saga_states(status);
CREATE INDEX idx_saga_states_created ON saga_states(created_at);
CREATE INDEX idx_saga_step_states_saga ON saga_step_states(saga_id);
CREATE INDEX idx_saga_step_states_status ON saga_step_states(status);
CREATE INDEX idx_saga_timeouts_timeout ON saga_timeouts(timeout_at) WHERE is_processed = FALSE;
CREATE INDEX idx_saga_choreography_saga ON saga_choreography_events(saga_id);
CREATE INDEX idx_saga_choreography_status ON saga_choreography_events(status, created_at);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_saga_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for automatic timestamp update
CREATE TRIGGER trigger_update_saga_updated_at
    BEFORE UPDATE ON saga_states
    FOR EACH ROW
    EXECUTE FUNCTION update_saga_updated_at();

-- Comments for documentation
COMMENT ON TABLE saga_states IS 'Stores the state of distributed sagas for transaction management';
COMMENT ON TABLE saga_step_states IS 'Tracks individual step execution within sagas';
COMMENT ON TABLE saga_timeouts IS 'Manages timeout scheduling for saga executions';
COMMENT ON TABLE saga_choreography_events IS 'Event store for choreography-based saga coordination';