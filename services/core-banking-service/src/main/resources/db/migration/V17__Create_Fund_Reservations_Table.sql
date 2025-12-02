-- V17__Create_Fund_Reservations_Table.sql
-- PRODUCTION-GRADE Fund Reservations Table
-- Replaces in-memory reservation tracking with persistent database storage

-- Create fund_reservations table
CREATE TABLE IF NOT EXISTS fund_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(100) NOT NULL UNIQUE,
    reserved_amount DECIMAL(19,4) NOT NULL CHECK (reserved_amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'USED')),
    reservation_reason VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    reserved_by VARCHAR(100) NOT NULL,
    reserved_for_service VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    released_by VARCHAR(100),
    release_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- Create optimized indexes for performance
CREATE INDEX idx_fund_reservation_account_id ON fund_reservations(account_id);
CREATE UNIQUE INDEX idx_fund_reservation_transaction_id ON fund_reservations(transaction_id);
CREATE INDEX idx_fund_reservation_status ON fund_reservations(status);
CREATE INDEX idx_fund_reservation_expires_at ON fund_reservations(expires_at);
CREATE INDEX idx_fund_reservation_account_status ON fund_reservations(account_id, status);
CREATE INDEX idx_fund_reservation_status_expires ON fund_reservations(status, expires_at);
CREATE INDEX idx_fund_reservation_service_status ON fund_reservations(reserved_for_service, status);
CREATE INDEX idx_fund_reservation_created_at ON fund_reservations(created_at);

-- Create foreign key constraint to accounts table
ALTER TABLE fund_reservations 
ADD CONSTRAINT fk_fund_reservation_account 
FOREIGN KEY (account_id) REFERENCES accounts(account_number) 
ON DELETE CASCADE ON UPDATE CASCADE;

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_fund_reservation_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_fund_reservation_updated_at
    BEFORE UPDATE ON fund_reservations
    FOR EACH ROW
    EXECUTE FUNCTION update_fund_reservation_updated_at();

-- Create function to automatically expire old reservations
CREATE OR REPLACE FUNCTION expire_old_reservations()
RETURNS INTEGER AS $$
DECLARE
    expired_count INTEGER;
BEGIN
    UPDATE fund_reservations 
    SET status = 'EXPIRED',
        released_at = CURRENT_TIMESTAMP,
        released_by = 'SYSTEM',
        release_reason = 'AUTOMATIC_EXPIRATION',
        updated_at = CURRENT_TIMESTAMP
    WHERE status = 'ACTIVE' 
    AND expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS expired_count = ROW_COUNT;
    
    IF expired_count > 0 THEN
        INSERT INTO system_audit_log (event_type, event_description, event_data, created_at)
        VALUES ('FUND_RESERVATIONS_EXPIRED', 
                'Automatically expired fund reservations',
                jsonb_build_object('expired_count', expired_count),
                CURRENT_TIMESTAMP);
    END IF;
    
    RETURN expired_count;
END;
$$ LANGUAGE plpgsql;

-- Create view for active reservations summary
CREATE OR REPLACE VIEW active_fund_reservations_summary AS
SELECT 
    account_id,
    currency,
    COUNT(*) as active_reservations,
    SUM(reserved_amount) as total_reserved,
    MIN(created_at) as oldest_reservation,
    MAX(expires_at) as latest_expiry,
    COUNT(*) FILTER (WHERE expires_at < CURRENT_TIMESTAMP + INTERVAL '1 hour') as expiring_soon
FROM fund_reservations 
WHERE status = 'ACTIVE'
GROUP BY account_id, currency;

-- Create view for reservation statistics
CREATE OR REPLACE VIEW fund_reservation_statistics AS
SELECT 
    status,
    COUNT(*) as count,
    SUM(reserved_amount) as total_amount,
    AVG(reserved_amount) as avg_amount,
    MIN(reserved_amount) as min_amount,
    MAX(reserved_amount) as max_amount,
    currency
FROM fund_reservations 
GROUP BY status, currency
ORDER BY status, currency;

-- Add comments for documentation
COMMENT ON TABLE fund_reservations IS 'Production-grade fund reservations replacing in-memory tracking';
COMMENT ON COLUMN fund_reservations.id IS 'Unique identifier for the reservation';
COMMENT ON COLUMN fund_reservations.account_id IS 'Account from which funds are reserved';
COMMENT ON COLUMN fund_reservations.transaction_id IS 'Unique transaction ID for which funds are reserved';
COMMENT ON COLUMN fund_reservations.reserved_amount IS 'Amount of funds reserved';
COMMENT ON COLUMN fund_reservations.currency IS 'Currency of the reserved funds';
COMMENT ON COLUMN fund_reservations.status IS 'Current status: ACTIVE, RELEASED, EXPIRED, or USED';
COMMENT ON COLUMN fund_reservations.expires_at IS 'When the reservation expires automatically';
COMMENT ON COLUMN fund_reservations.version IS 'Optimistic locking version for concurrent access control';

-- Grant appropriate permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON fund_reservations TO banking_service_user;
GRANT SELECT ON active_fund_reservations_summary TO banking_service_user;
GRANT SELECT ON fund_reservation_statistics TO banking_service_user;