-- ========================================
-- SAGA STATE PERSISTENCE TABLES
-- Critical for crash recovery and saga resumption
-- ========================================

-- Main saga execution tracking
CREATE TABLE IF NOT EXISTS saga_executions (
    saga_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPENSATING', 'COMPLETED', 'COMPENSATED', 'FAILED', 'TIMEOUT')),
    current_step INT NOT NULL DEFAULT 0,
    total_steps INT NOT NULL,
    payload TEXT,
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 3,
    timeout_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT check_current_step CHECK (current_step >= 0 AND current_step <= total_steps),
    CONSTRAINT check_total_steps CHECK (total_steps > 0),
    CONSTRAINT check_retry_count CHECK (retry_count >= 0),
    CONSTRAINT check_max_retries CHECK (max_retries >= 0)
);

-- Individual saga step execution tracking
CREATE TABLE IF NOT EXISTS saga_step_executions (
    step_execution_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_id UUID NOT NULL REFERENCES saga_executions(saga_id) ON DELETE CASCADE,
    step_number INT NOT NULL,
    step_name VARCHAR(200) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED', 'SKIPPED')),
    input_data TEXT,
    output_data TEXT,
    error_message TEXT,
    compensation_data TEXT,
    idempotency_key VARCHAR(255),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    compensated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT unique_saga_step UNIQUE (saga_id, step_number),
    CONSTRAINT check_step_number CHECK (step_number > 0),
    CONSTRAINT check_idempotency_key_unique UNIQUE (idempotency_key)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_saga_status ON saga_executions(status);
CREATE INDEX IF NOT EXISTS idx_saga_transaction_id ON saga_executions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_saga_created_at ON saga_executions(created_at);
CREATE INDEX IF NOT EXISTS idx_saga_last_updated ON saga_executions(last_updated_at);
CREATE INDEX IF NOT EXISTS idx_saga_timeout ON saga_executions(timeout_at) WHERE status IN ('RUNNING', 'COMPENSATING');
CREATE INDEX IF NOT EXISTS idx_saga_type_status ON saga_executions(saga_type, status);

CREATE INDEX IF NOT EXISTS idx_step_saga_id ON saga_step_executions(saga_id);
CREATE INDEX IF NOT EXISTS idx_step_status ON saga_step_executions(status);
CREATE INDEX IF NOT EXISTS idx_step_idempotency ON saga_step_executions(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Trigger to update last_updated_at on saga execution changes
CREATE OR REPLACE FUNCTION update_saga_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER saga_update_timestamp
    BEFORE UPDATE ON saga_executions
    FOR EACH ROW
    EXECUTE FUNCTION update_saga_last_updated();

-- Comments for documentation
COMMENT ON TABLE saga_executions IS 'Persistent saga execution state for crash recovery';
COMMENT ON TABLE saga_step_executions IS 'Individual saga step execution tracking for precise recovery';
COMMENT ON COLUMN saga_executions.timeout_at IS 'Absolute timeout timestamp for saga execution';
COMMENT ON COLUMN saga_step_executions.idempotency_key IS 'Ensures step is executed exactly once even on retry';
COMMENT ON COLUMN saga_step_executions.compensation_data IS 'Data required to rollback this step';
