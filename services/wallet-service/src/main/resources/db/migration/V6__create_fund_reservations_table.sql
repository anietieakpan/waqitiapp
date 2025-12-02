-- CRITICAL SECURITY FIX: Create fund_reservations table for persistent storage
-- This fixes the double-spending vulnerability where reservations were stored in memory (@Transient)
-- and lost on service restart, allowing the same funds to be spent twice

CREATE TABLE fund_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL,
    transaction_id UUID NOT NULL UNIQUE,
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NULL,
    released_at TIMESTAMP WITH TIME ZONE NULL,
    reason VARCHAR(500) NULL,
    idempotency_key VARCHAR(255) UNIQUE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_fund_reservations_wallet 
        FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE,
    
    CONSTRAINT chk_fund_reservations_status 
        CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
        
    CONSTRAINT chk_fund_reservations_expires_after_created
        CHECK (expires_at > created_at),
        
    CONSTRAINT chk_fund_reservations_confirmed_state
        CHECK ((status = 'CONFIRMED' AND confirmed_at IS NOT NULL) OR 
               (status != 'CONFIRMED' AND confirmed_at IS NULL)),
               
    CONSTRAINT chk_fund_reservations_released_state
        CHECK ((status IN ('RELEASED', 'EXPIRED') AND released_at IS NOT NULL) OR 
               (status NOT IN ('RELEASED', 'EXPIRED') AND released_at IS NULL))
);

-- Critical indexes for performance and security
CREATE INDEX idx_fund_reservation_wallet_id ON fund_reservations(wallet_id);
CREATE INDEX idx_fund_reservation_transaction_id ON fund_reservations(transaction_id);
CREATE INDEX idx_fund_reservation_expires_at ON fund_reservations(expires_at);
CREATE INDEX idx_fund_reservation_status ON fund_reservations(status);
CREATE INDEX idx_fund_reservation_created_at ON fund_reservations(created_at);

-- Index for cleanup operations
CREATE INDEX idx_fund_reservation_cleanup 
    ON fund_reservations(status, expires_at) 
    WHERE status = 'ACTIVE';

-- Index for wallet balance calculations
CREATE INDEX idx_fund_reservation_wallet_active 
    ON fund_reservations(wallet_id, status, amount) 
    WHERE status = 'ACTIVE';

-- Security audit: Add comments explaining the security fix
COMMENT ON TABLE fund_reservations IS 
    'SECURITY FIX: Persistent fund reservations to prevent double-spending. 
     Previously stored in @Transient fields, now persisted to survive service restarts.';

COMMENT ON COLUMN fund_reservations.wallet_id IS 'Wallet that owns the reserved funds';
COMMENT ON COLUMN fund_reservations.transaction_id IS 'Unique transaction identifier';
COMMENT ON COLUMN fund_reservations.amount IS 'Amount of funds reserved (must be positive)';
COMMENT ON COLUMN fund_reservations.status IS 'Reservation status: ACTIVE, CONFIRMED, RELEASED, EXPIRED';
COMMENT ON COLUMN fund_reservations.expires_at IS 'When this reservation expires (default: 5 minutes)';
COMMENT ON COLUMN fund_reservations.idempotency_key IS 'For preventing duplicate reservations';

-- Grant permissions for the service account
-- GRANT SELECT, INSERT, UPDATE, DELETE ON fund_reservations TO wallet_service_user;