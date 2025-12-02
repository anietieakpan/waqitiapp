-- Create instant_transfers table for real-time payment processing
-- Supports FedNow, RTP, Zelle, and internal instant transfers

CREATE TABLE IF NOT EXISTS instant_transfers (
    id UUID PRIMARY KEY,
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description VARCHAR(500),
    transfer_method VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(255),
    fraud_score DECIMAL(5, 2),
    risk_level VARCHAR(50),
    reservation_id VARCHAR(255),
    network_transaction_id VARCHAR(255),
    network_response TEXT,
    failure_reason VARCHAR(1000),
    cancellation_reason VARCHAR(500),
    processing_time BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    canceled_at TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_instant_transfers_sender_id ON instant_transfers(sender_id);
CREATE INDEX idx_instant_transfers_recipient_id ON instant_transfers(recipient_id);
CREATE INDEX idx_instant_transfers_status ON instant_transfers(status);
CREATE INDEX idx_instant_transfers_created_at ON instant_transfers(created_at DESC);
CREATE INDEX idx_instant_transfers_transfer_method ON instant_transfers(transfer_method);
CREATE INDEX idx_instant_transfers_network_transaction_id ON instant_transfers(network_transaction_id);
CREATE INDEX idx_instant_transfers_correlation_id ON instant_transfers(correlation_id);

-- Composite indexes for common queries
CREATE INDEX idx_instant_transfers_sender_status ON instant_transfers(sender_id, status);
CREATE INDEX idx_instant_transfers_recipient_status ON instant_transfers(recipient_id, status);

-- Add comments for documentation
COMMENT ON TABLE instant_transfers IS 'Stores instant transfer transactions for real-time payment processing';
COMMENT ON COLUMN instant_transfers.id IS 'Unique identifier for the instant transfer';
COMMENT ON COLUMN instant_transfers.sender_id IS 'UUID of the user sending the transfer';
COMMENT ON COLUMN instant_transfers.recipient_id IS 'UUID of the user receiving the transfer';
COMMENT ON COLUMN instant_transfers.amount IS 'Transfer amount in the specified currency';
COMMENT ON COLUMN instant_transfers.currency IS 'ISO 4217 currency code';
COMMENT ON COLUMN instant_transfers.description IS 'Optional description of the transfer';
COMMENT ON COLUMN instant_transfers.transfer_method IS 'Method used: FEDNOW, RTP, ZELLE, INTERNAL';
COMMENT ON COLUMN instant_transfers.status IS 'Current status from PaymentStatus enum';
COMMENT ON COLUMN instant_transfers.correlation_id IS 'Correlation ID for distributed tracing';
COMMENT ON COLUMN instant_transfers.fraud_score IS 'Fraud detection score (0-100)';
COMMENT ON COLUMN instant_transfers.risk_level IS 'Risk level: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN instant_transfers.reservation_id IS 'Fund reservation identifier';
COMMENT ON COLUMN instant_transfers.network_transaction_id IS 'External network transaction ID';
COMMENT ON COLUMN instant_transfers.network_response IS 'Full response from payment network';
COMMENT ON COLUMN instant_transfers.failure_reason IS 'Reason for failure if applicable';
COMMENT ON COLUMN instant_transfers.cancellation_reason IS 'Reason for cancellation if applicable';
COMMENT ON COLUMN instant_transfers.processing_time IS 'Processing time in milliseconds';
COMMENT ON COLUMN instant_transfers.created_at IS 'Timestamp when transfer was created';
COMMENT ON COLUMN instant_transfers.processing_started_at IS 'Timestamp when processing began';
COMMENT ON COLUMN instant_transfers.completed_at IS 'Timestamp when transfer completed';
COMMENT ON COLUMN instant_transfers.canceled_at IS 'Timestamp when transfer was canceled';
COMMENT ON COLUMN instant_transfers.updated_at IS 'Last update timestamp';