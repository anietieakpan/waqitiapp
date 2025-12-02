-- Create offline payments table
CREATE TABLE IF NOT EXISTS offline_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(500),
    device_id VARCHAR(255) NOT NULL,
    client_timestamp TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    offline_signature VARCHAR(255) NOT NULL,
    qr_code VARCHAR(1000),
    bluetooth_token VARCHAR(255),
    nfc_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    synced_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    sync_attempts INTEGER DEFAULT 0,
    sync_error VARCHAR(1000),
    online_payment_id VARCHAR(255),
    recipient_verification_data VARCHAR(1000),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_status CHECK (status IN ('PENDING_SYNC', 'ACCEPTED_OFFLINE', 'SYNCING', 
        'SYNCED', 'SYNC_FAILED', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'REJECTED'))
);

-- Create indexes
CREATE INDEX idx_offline_payments_sender_status ON offline_payments(sender_id, status);
CREATE INDEX idx_offline_payments_recipient_status ON offline_payments(recipient_id, status);
CREATE INDEX idx_offline_payments_created_at ON offline_payments(created_at);
CREATE INDEX idx_offline_payments_status ON offline_payments(status);
CREATE INDEX idx_offline_payments_device_id ON offline_payments(device_id);

-- Create index for sync queries
CREATE INDEX idx_offline_payments_sync ON offline_payments(status, sync_attempts) 
    WHERE status IN ('PENDING_SYNC', 'SYNC_FAILED');

-- Comments
COMMENT ON TABLE offline_payments IS 'Stores offline P2P payments that sync when connectivity is restored';
COMMENT ON COLUMN offline_payments.offline_signature IS 'Cryptographic signature for offline verification';
COMMENT ON COLUMN offline_payments.qr_code IS 'QR code data for offline transfer';
COMMENT ON COLUMN offline_payments.bluetooth_token IS 'Token for Bluetooth proximity transfer';
COMMENT ON COLUMN offline_payments.nfc_data IS 'Data for NFC transfer';
COMMENT ON COLUMN offline_payments.sync_attempts IS 'Number of times sync was attempted';
COMMENT ON COLUMN offline_payments.online_payment_id IS 'ID of the payment after successful sync';