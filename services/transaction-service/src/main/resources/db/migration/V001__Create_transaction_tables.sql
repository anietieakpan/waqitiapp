-- Create transaction types enum
CREATE TYPE transaction_type AS ENUM (
    'TRANSFER',
    'DEPOSIT',
    'WITHDRAWAL',
    'FEE',
    'REFUND',
    'REVERSAL',
    'ADJUSTMENT'
);

-- Create transaction status enum
CREATE TYPE transaction_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
    'REVERSED',
    'DISPUTED'
);

-- Create priority enum
CREATE TYPE transaction_priority AS ENUM (
    'LOW',
    'NORMAL',
    'HIGH',
    'URGENT'
);

-- Main transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type transaction_type NOT NULL,
    status transaction_status NOT NULL DEFAULT 'PENDING',
    priority transaction_priority NOT NULL DEFAULT 'NORMAL',
    
    -- Amount and currency
    amount DECIMAL(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    fees DECIMAL(19,4) DEFAULT 0,
    
    -- Wallet references
    from_wallet_id UUID,
    to_wallet_id UUID,
    
    -- User references (denormalized for performance)
    from_user_id UUID,
    to_user_id UUID,
    
    -- Transaction details
    description TEXT,
    reference VARCHAR(50) UNIQUE NOT NULL,
    external_reference VARCHAR(100),
    
    -- Saga orchestration
    saga_id UUID,
    
    -- Processing details
    processed_at TIMESTAMP WITH TIME ZONE,
    failure_reason TEXT,
    retry_count INTEGER DEFAULT 0,
    
    -- Metadata
    metadata JSONB,
    tags TEXT[],
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT chk_wallet_references CHECK (
        (type IN ('TRANSFER') AND from_wallet_id IS NOT NULL AND to_wallet_id IS NOT NULL) OR
        (type IN ('DEPOSIT') AND to_wallet_id IS NOT NULL) OR
        (type IN ('WITHDRAWAL') AND from_wallet_id IS NOT NULL) OR
        (type IN ('FEE', 'REFUND', 'REVERSAL', 'ADJUSTMENT'))
    ),
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_fees_non_negative CHECK (fees >= 0)
);

-- Transaction events/timeline table
CREATE TABLE transaction_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    event_type VARCHAR(50) NOT NULL,
    event_status VARCHAR(50) NOT NULL,
    description TEXT,
    event_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- Transaction disputes table
CREATE TABLE transaction_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    dispute_reason VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution TEXT,
    evidence_urls TEXT[],
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    resolved_by UUID,
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- Scheduled transactions table
CREATE TABLE scheduled_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_template JSONB NOT NULL,
    scheduled_date TIMESTAMP WITH TIME ZONE NOT NULL,
    frequency VARCHAR(20), -- ONCE, DAILY, WEEKLY, MONTHLY, YEARLY
    end_date TIMESTAMP WITH TIME ZONE,
    max_executions INTEGER,
    execution_count INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    next_execution TIMESTAMP WITH TIME ZONE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL
);

-- Transaction execution log for scheduled transactions
CREATE TABLE scheduled_transaction_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scheduled_transaction_id UUID NOT NULL REFERENCES scheduled_transactions(id),
    transaction_id UUID REFERENCES transactions(id),
    execution_date TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_from_wallet ON transactions(from_wallet_id);
CREATE INDEX idx_transactions_to_wallet ON transactions(to_wallet_id);
CREATE INDEX idx_transactions_from_user ON transactions(from_user_id);
CREATE INDEX idx_transactions_to_user ON transactions(to_user_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_reference ON transactions(reference);
CREATE INDEX idx_transactions_saga_id ON transactions(saga_id);

-- Composite indexes for common queries
CREATE INDEX idx_transactions_wallet_status ON transactions(from_wallet_id, status);
CREATE INDEX idx_transactions_wallet_type ON transactions(from_wallet_id, type);
CREATE INDEX idx_transactions_date_status ON transactions(created_at, status);
CREATE INDEX idx_transactions_user_date ON transactions(from_user_id, created_at);

-- Indexes for related tables
CREATE INDEX idx_transaction_events_transaction ON transaction_events(transaction_id);
CREATE INDEX idx_transaction_events_created_at ON transaction_events(created_at);
CREATE INDEX idx_transaction_disputes_transaction ON transaction_disputes(transaction_id);
CREATE INDEX idx_transaction_disputes_status ON transaction_disputes(status);
CREATE INDEX idx_scheduled_transactions_next_execution ON scheduled_transactions(next_execution);
CREATE INDEX idx_scheduled_transactions_status ON scheduled_transactions(status);

-- Add update trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_transactions_updated_at 
    BEFORE UPDATE ON transactions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transaction_disputes_updated_at 
    BEFORE UPDATE ON transaction_disputes 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_scheduled_transactions_updated_at 
    BEFORE UPDATE ON scheduled_transactions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add some initial reference data
INSERT INTO scheduled_transactions (id, transaction_template, scheduled_date, frequency, status, created_by) 
VALUES 
    (gen_random_uuid(), '{"type": "SYSTEM_MAINTENANCE"}', CURRENT_TIMESTAMP + INTERVAL '1 day', 'DAILY', 'INACTIVE', '00000000-0000-0000-0000-000000000000')
ON CONFLICT DO NOTHING;