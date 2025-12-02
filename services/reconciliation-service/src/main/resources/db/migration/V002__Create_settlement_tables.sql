-- Settlement Tables for Daily Settlement Reconciliation
-- CRITICAL: Supports SettlementEventConsumer for payment provider settlement processing
-- 
-- These tables track daily settlements from payment providers (Stripe, PayPal, Adyen, etc.)
-- and reconcile them against expected transaction totals

-- Settlement status enum
CREATE TYPE settlement_status AS ENUM (
    'PENDING_RECONCILIATION',
    'RECONCILED',
    'DISCREPANCY_DETECTED',
    'PENDING_INVESTIGATION',
    'INVESTIGATION_COMPLETE',
    'RESOLVED',
    'DISPUTED'
);

-- Main settlements table
CREATE TABLE settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_settlement_id VARCHAR(100) UNIQUE NOT NULL,
    payment_provider VARCHAR(100) NOT NULL,
    batch_id VARCHAR(100),
    
    -- Settlement amounts
    settlement_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    net_settlement_amount DECIMAL(19,4),
    
    -- Fee and adjustment details
    provider_fees DECIMAL(19,4) DEFAULT 0,
    refund_amount DECIMAL(19,4) DEFAULT 0,
    chargeback_amount DECIMAL(19,4) DEFAULT 0,
    adjustment_amount DECIMAL(19,4) DEFAULT 0,
    
    -- Settlement dates
    settlement_date DATE NOT NULL,
    expected_settlement_date DATE,
    period_start_date DATE,
    period_end_date DATE,
    
    -- Transaction counts
    transaction_count INTEGER NOT NULL DEFAULT 0,
    refund_count INTEGER DEFAULT 0,
    chargeback_count INTEGER DEFAULT 0,
    
    -- Reconciliation details
    status settlement_status NOT NULL DEFAULT 'PENDING_RECONCILIATION',
    reconciliation_id UUID,
    reconciled_at TIMESTAMP WITH TIME ZONE,
    
    -- Discrepancy tracking
    discrepancy_count INTEGER DEFAULT 0,
    discrepancy_amount DECIMAL(19,4) DEFAULT 0,
    investigation_notes TEXT,
    
    -- Banking details
    beneficiary_account VARCHAR(100),
    beneficiary_bank VARCHAR(200),
    settlement_reference VARCHAR(200),
    
    -- Additional metadata
    settlement_type VARCHAR(50) DEFAULT 'STANDARD',
    automatic_settlement BOOLEAN DEFAULT TRUE,
    requires_investigation BOOLEAN DEFAULT FALSE,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT chk_settlement_amount_positive CHECK (settlement_amount > 0),
    CONSTRAINT chk_transaction_count_positive CHECK (transaction_count > 0),
    CONSTRAINT chk_valid_dates CHECK (settlement_date >= period_start_date),
    CONSTRAINT chk_settlement_date_order CHECK (period_start_date <= period_end_date)
);

-- Settlement instructions table (for treasury/finance team)
CREATE TABLE settlement_instructions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    
    -- Instruction details
    instruction_type VARCHAR(50) NOT NULL,
    instruction_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_EXECUTION',
    
    -- Transfer details
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_provider VARCHAR(100) NOT NULL,
    
    -- Beneficiary information
    beneficiary_account VARCHAR(100),
    beneficiary_bank VARCHAR(200),
    beneficiary_name VARCHAR(200),
    
    -- Reference information
    reference VARCHAR(200),
    internal_reference VARCHAR(200),
    
    -- Timing
    due_date DATE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE,
    
    -- Execution details
    executed_by UUID,
    execution_notes TEXT,
    confirmation_number VARCHAR(100),
    
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_instruction_amount_positive CHECK (amount > 0)
);

-- Settlement discrepancies table
CREATE TABLE settlement_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    
    -- Discrepancy details
    discrepancy_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    
    -- Financial impact
    expected_amount DECIMAL(19,4),
    actual_amount DECIMAL(19,4),
    difference_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    
    -- Investigation
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    assigned_to UUID,
    resolution_notes TEXT,
    resolved_at TIMESTAMP WITH TIME ZONE,
    
    -- Root cause
    root_cause VARCHAR(200),
    corrective_action TEXT,
    
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Settlement reconciliation details table
CREATE TABLE settlement_reconciliation_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE CASCADE,
    reconciliation_id UUID,
    
    -- Expected vs Actual comparison
    expected_transaction_count INTEGER NOT NULL,
    actual_transaction_count INTEGER NOT NULL,
    transaction_count_difference INTEGER,
    
    expected_amount DECIMAL(19,4) NOT NULL,
    actual_amount DECIMAL(19,4) NOT NULL,
    amount_difference DECIMAL(19,4),
    
    expected_fees DECIMAL(19,4),
    actual_fees DECIMAL(19,4),
    fee_difference DECIMAL(19,4),
    
    -- Reconciliation results
    is_balanced BOOLEAN NOT NULL DEFAULT FALSE,
    match_rate DECIMAL(5,2),
    confidence_score DECIMAL(5,2),
    
    -- Transaction breakdown
    matched_transactions INTEGER DEFAULT 0,
    unmatched_transactions INTEGER DEFAULT 0,
    disputed_transactions INTEGER DEFAULT 0,
    
    -- Reconciliation metadata
    reconciliation_method VARCHAR(50),
    reconciliation_notes TEXT,
    reconciled_by UUID,
    reconciled_at TIMESTAMP WITH TIME ZONE,
    
    metadata JSONB,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Processed settlement events table (idempotency)
CREATE TABLE processed_settlement_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_settlement_id VARCHAR(100) UNIQUE NOT NULL,
    internal_settlement_id UUID REFERENCES settlements(id),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    
    payment_provider VARCHAR(100) NOT NULL,
    settlement_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    
    metadata JSONB
);

-- Indexes for settlements
CREATE INDEX idx_settlements_external_id ON settlements(external_settlement_id);
CREATE INDEX idx_settlements_provider ON settlements(payment_provider);
CREATE INDEX idx_settlements_status ON settlements(status);
CREATE INDEX idx_settlements_settlement_date ON settlements(settlement_date);
CREATE INDEX idx_settlements_batch_id ON settlements(batch_id);
CREATE INDEX idx_settlements_reconciliation_id ON settlements(reconciliation_id);
CREATE INDEX idx_settlements_requires_investigation ON settlements(requires_investigation) WHERE requires_investigation = TRUE;
CREATE INDEX idx_settlements_priority ON settlements(priority);

-- Composite indexes for common queries
CREATE INDEX idx_settlements_provider_date ON settlements(payment_provider, settlement_date DESC);
CREATE INDEX idx_settlements_status_date ON settlements(status, settlement_date DESC);
CREATE INDEX idx_settlements_provider_status ON settlements(payment_provider, status);

-- Indexes for settlement instructions
CREATE INDEX idx_settlement_instructions_settlement_id ON settlement_instructions(settlement_id);
CREATE INDEX idx_settlement_instructions_status ON settlement_instructions(instruction_status);
CREATE INDEX idx_settlement_instructions_due_date ON settlement_instructions(due_date);
CREATE INDEX idx_settlement_instructions_provider ON settlement_instructions(payment_provider);

-- Indexes for settlement discrepancies
CREATE INDEX idx_settlement_discrepancies_settlement_id ON settlement_discrepancies(settlement_id);
CREATE INDEX idx_settlement_discrepancies_status ON settlement_discrepancies(status);
CREATE INDEX idx_settlement_discrepancies_severity ON settlement_discrepancies(severity);
CREATE INDEX idx_settlement_discrepancies_assigned_to ON settlement_discrepancies(assigned_to);

-- Indexes for settlement reconciliation details
CREATE INDEX idx_settlement_recon_details_settlement_id ON settlement_reconciliation_details(settlement_id);
CREATE INDEX idx_settlement_recon_details_reconciliation_id ON settlement_reconciliation_details(reconciliation_id);
CREATE INDEX idx_settlement_recon_details_balanced ON settlement_reconciliation_details(is_balanced);

-- Indexes for processed events
CREATE INDEX idx_processed_settlement_events_external_id ON processed_settlement_events(external_settlement_id);
CREATE INDEX idx_processed_settlement_events_event_id ON processed_settlement_events(event_id);
CREATE INDEX idx_processed_settlement_events_provider ON processed_settlement_events(payment_provider);
CREATE INDEX idx_processed_settlement_events_processed_at ON processed_settlement_events(processed_at);

-- Automatic timestamp update function
CREATE OR REPLACE FUNCTION update_settlement_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    IF TG_TABLE_NAME = 'settlements' THEN
        NEW.version = OLD.version + 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for automatic timestamp updates
CREATE TRIGGER trigger_settlements_updated_at
    BEFORE UPDATE ON settlements
    FOR EACH ROW EXECUTE FUNCTION update_settlement_updated_at();

CREATE TRIGGER trigger_settlement_instructions_updated_at
    BEFORE UPDATE ON settlement_instructions
    FOR EACH ROW EXECUTE FUNCTION update_settlement_updated_at();

CREATE TRIGGER trigger_settlement_discrepancies_updated_at
    BEFORE UPDATE ON settlement_discrepancies
    FOR EACH ROW EXECUTE FUNCTION update_settlement_updated_at();

CREATE TRIGGER trigger_settlement_recon_details_updated_at
    BEFORE UPDATE ON settlement_reconciliation_details
    FOR EACH ROW EXECUTE FUNCTION update_settlement_updated_at();

-- Function to check for existing settlement by external ID
CREATE OR REPLACE FUNCTION settlement_exists(p_external_settlement_id VARCHAR)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM processed_settlement_events 
        WHERE external_settlement_id = p_external_settlement_id
    );
END;
$$ LANGUAGE plpgsql;

-- Function to mark settlement event as processed
CREATE OR REPLACE FUNCTION mark_settlement_processed(
    p_external_settlement_id VARCHAR,
    p_internal_settlement_id UUID,
    p_event_id VARCHAR,
    p_provider VARCHAR,
    p_amount DECIMAL,
    p_currency VARCHAR
)
RETURNS VOID AS $$
BEGIN
    INSERT INTO processed_settlement_events (
        external_settlement_id,
        internal_settlement_id,
        event_id,
        payment_provider,
        settlement_amount,
        currency,
        processed_at,
        processing_status
    ) VALUES (
        p_external_settlement_id,
        p_internal_settlement_id,
        p_event_id,
        p_provider,
        p_amount,
        p_currency,
        CURRENT_TIMESTAMP,
        'SUCCESS'
    )
    ON CONFLICT (external_settlement_id) DO NOTHING;
END;
$$ LANGUAGE plpgsql;

-- Add helpful comments
COMMENT ON TABLE settlements IS 
'CRITICAL TABLE: Tracks daily settlements from payment providers.
Supports SettlementEventConsumer for automated reconciliation of provider settlements.';

COMMENT ON TABLE settlement_instructions IS 
'Settlement instructions for treasury team to execute fund transfers.
Generated automatically when settlements are reconciled successfully.';

COMMENT ON TABLE settlement_discrepancies IS 
'Tracks all discrepancies found during settlement reconciliation.
Critical for fraud detection and financial accuracy.';

COMMENT ON TABLE processed_settlement_events IS 
'Idempotency table for settlement event processing.
Prevents duplicate processing of settlement events from Kafka.';

COMMENT ON FUNCTION settlement_exists(VARCHAR) IS 
'Fast check for settlement existence by external ID. Used by event consumer.';

COMMENT ON FUNCTION mark_settlement_processed IS 
'Marks a settlement event as processed. Called by SettlementEventConsumer.';

-- Materialized view for settlement summary statistics (optional - for dashboards)
CREATE MATERIALIZED VIEW settlement_summary_stats AS
SELECT 
    payment_provider,
    DATE_TRUNC('month', settlement_date) AS month,
    status,
    COUNT(*) AS settlement_count,
    SUM(settlement_amount) AS total_settlement_amount,
    SUM(transaction_count) AS total_transactions,
    SUM(discrepancy_count) AS total_discrepancies,
    SUM(discrepancy_amount) AS total_discrepancy_amount,
    AVG(CASE WHEN reconciled_at IS NOT NULL 
        THEN EXTRACT(EPOCH FROM (reconciled_at - created_at))/3600 
        ELSE NULL END) AS avg_reconciliation_hours
FROM settlements
GROUP BY payment_provider, DATE_TRUNC('month', settlement_date), status;

-- Index on materialized view
CREATE UNIQUE INDEX idx_settlement_summary_stats ON settlement_summary_stats(payment_provider, month, status);

-- Refresh function for materialized view
CREATE OR REPLACE FUNCTION refresh_settlement_summary_stats()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY settlement_summary_stats;
END;
$$ LANGUAGE plpgsql;

COMMENT ON MATERIALIZED VIEW settlement_summary_stats IS 
'Summary statistics for settlement reconciliation.
Refresh daily or after significant settlement processing.
Run: SELECT refresh_settlement_summary_stats();';