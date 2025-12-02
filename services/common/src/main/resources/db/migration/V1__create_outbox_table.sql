-- Outbox Pattern Implementation
-- Table for reliable event publishing

CREATE TABLE IF NOT EXISTS outbox_events (
    event_id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    locked_until TIMESTAMP,
    last_retry_at TIMESTAMP,
    retry_count INT DEFAULT 0,
    error_message TEXT,
    version INT DEFAULT 0,
    
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'RETRY'))
);

-- Indexes for efficient querying
CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);
CREATE INDEX idx_outbox_published_at ON outbox_events(published_at) WHERE status = 'PUBLISHED';
CREATE INDEX idx_outbox_locked_until ON outbox_events(locked_until) WHERE locked_until IS NOT NULL;
CREATE INDEX idx_outbox_retry ON outbox_events(status, retry_count) WHERE status IN ('PENDING', 'RETRY');

-- Function to automatically update version on row update
CREATE OR REPLACE FUNCTION update_outbox_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update version
CREATE TRIGGER trigger_update_outbox_version
    BEFORE UPDATE ON outbox_events
    FOR EACH ROW
    EXECUTE FUNCTION update_outbox_version();

-- Comments for documentation
COMMENT ON TABLE outbox_events IS 'Outbox pattern implementation for reliable event publishing';
COMMENT ON COLUMN outbox_events.event_id IS 'Unique identifier for the event';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'ID of the aggregate that generated the event';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Type of aggregate (e.g., Payment, Transaction, Account)';
COMMENT ON COLUMN outbox_events.event_type IS 'Type of event (e.g., PaymentCompleted, TransactionCreated)';
COMMENT ON COLUMN outbox_events.payload IS 'JSON payload of the event';
COMMENT ON COLUMN outbox_events.headers IS 'Optional headers for the event';
COMMENT ON COLUMN outbox_events.status IS 'Current status: PENDING, PUBLISHED, FAILED, RETRY';
COMMENT ON COLUMN outbox_events.locked_until IS 'Lock timestamp to prevent duplicate processing';
COMMENT ON COLUMN outbox_events.retry_count IS 'Number of retry attempts';
COMMENT ON COLUMN outbox_events.version IS 'Optimistic locking version';