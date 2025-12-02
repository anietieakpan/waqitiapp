-- Create enums for reconciliation
CREATE TYPE reconciliation_type AS ENUM (
    'BANK_RECONCILIATION',
    'WALLET_RECONCILIATION',
    'TRANSACTION_RECONCILIATION',
    'INTER_SERVICE_RECONCILIATION',
    'PAYMENT_GATEWAY_RECONCILIATION',
    'MANUAL_RECONCILIATION'
);

CREATE TYPE reconciliation_status AS ENUM (
    'INITIATED',
    'IN_PROGRESS',
    'COMPLETED',
    'FAILED',
    'CANCELLED',
    'PENDING_REVIEW'
);

CREATE TYPE reconciliation_item_type AS ENUM (
    'BANK_STATEMENT',
    'GL_TRANSACTION',
    'WALLET_TRANSACTION',
    'PAYMENT_GATEWAY',
    'EXTERNAL_SYSTEM',
    'ADJUSTMENT'
);

CREATE TYPE reconciliation_item_status AS ENUM (
    'UNMATCHED',
    'MATCHED',
    'PARTIALLY_MATCHED',
    'DISPUTED',
    'RESOLVED',
    'IGNORED'
);

CREATE TYPE match_status AS ENUM (
    'EXACT_MATCH',
    'FUZZY_MATCH',
    'MANUAL_MATCH',
    'SUGGESTED_MATCH',
    'NO_MATCH'
);

CREATE TYPE discrepancy_type AS ENUM (
    'AMOUNT_MISMATCH',
    'DATE_MISMATCH',
    'REFERENCE_MISMATCH',
    'MISSING_IN_SOURCE',
    'MISSING_IN_TARGET',
    'DUPLICATE_ENTRY',
    'CURRENCY_MISMATCH',
    'STATUS_MISMATCH'
);

CREATE TYPE discrepancy_severity AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH',
    'CRITICAL'
);

CREATE TYPE discrepancy_status AS ENUM (
    'OPEN',
    'UNDER_INVESTIGATION',
    'RESOLVED',
    'ESCALATED',
    'CLOSED'
);

-- Main reconciliation table
CREATE TABLE reconciliations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type reconciliation_type NOT NULL,
    status reconciliation_status NOT NULL DEFAULT 'INITIATED',
    
    -- Period and scope
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    source VARCHAR(100) NOT NULL,
    target VARCHAR(100) NOT NULL,
    
    -- Statistics
    total_source_items INTEGER DEFAULT 0,
    total_target_items INTEGER DEFAULT 0,
    matched_items INTEGER DEFAULT 0,
    unmatched_source_items INTEGER DEFAULT 0,
    unmatched_target_items INTEGER DEFAULT 0,
    discrepancy_count INTEGER DEFAULT 0,
    
    -- Amounts
    total_source_amount DECIMAL(19,4) DEFAULT 0,
    total_target_amount DECIMAL(19,4) DEFAULT 0,
    matched_amount DECIMAL(19,4) DEFAULT 0,
    discrepancy_amount DECIMAL(19,4) DEFAULT 0,
    
    -- Performance metrics
    match_rate DECIMAL(5,2),
    accuracy_rate DECIMAL(5,2),
    processing_time_ms BIGINT,
    
    -- Additional fields
    reference VARCHAR(100) UNIQUE,
    description TEXT,
    metadata JSONB,
    
    -- Completion details
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_by UUID,
    completion_notes TEXT,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0
);

-- Reconciliation items table
CREATE TABLE reconciliation_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID REFERENCES reconciliations(id),
    item_type reconciliation_item_type NOT NULL,
    status reconciliation_item_status NOT NULL DEFAULT 'UNMATCHED',
    
    -- Item identification
    source VARCHAR(100) NOT NULL,
    reference VARCHAR(200),
    external_reference VARCHAR(200),
    
    -- Transaction details
    date DATE NOT NULL,
    value_date DATE,
    description TEXT,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Matching information
    matched_item_id UUID,
    match_confidence DECIMAL(5,2),
    match_date TIMESTAMP WITH TIME ZONE,
    match_type match_status,
    
    -- Additional data
    metadata JSONB,
    tags TEXT[],
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Matching records table
CREATE TABLE reconciliation_matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID NOT NULL REFERENCES reconciliations(id),
    
    -- Match details
    match_status match_status NOT NULL,
    match_confidence DECIMAL(5,2) NOT NULL,
    
    -- Source items (can be one-to-many)
    source_item_ids UUID[] NOT NULL,
    source_total_amount DECIMAL(19,4) NOT NULL,
    
    -- Target items (can be one-to-many)
    target_item_ids UUID[] NOT NULL,
    target_total_amount DECIMAL(19,4) NOT NULL,
    
    -- Match criteria used
    match_criteria JSONB,
    match_algorithm VARCHAR(50),
    
    -- Manual matching
    is_manual BOOLEAN DEFAULT false,
    manual_match_reason TEXT,
    matched_by UUID,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- Discrepancies table
CREATE TABLE reconciliation_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID NOT NULL REFERENCES reconciliations(id),
    match_id UUID REFERENCES reconciliation_matches(id),
    
    -- Discrepancy details
    type discrepancy_type NOT NULL,
    severity discrepancy_severity NOT NULL,
    status discrepancy_status NOT NULL DEFAULT 'OPEN',
    
    -- Items involved
    source_item_id UUID REFERENCES reconciliation_items(id),
    target_item_id UUID REFERENCES reconciliation_items(id),
    
    -- Discrepancy values
    source_value TEXT,
    target_value TEXT,
    difference_amount DECIMAL(19,4),
    
    -- Investigation
    description TEXT,
    root_cause TEXT,
    impact_assessment TEXT,
    
    -- Resolution
    resolution_action TEXT,
    resolution_notes TEXT,
    resolved_by UUID,
    resolved_at TIMESTAMP WITH TIME ZONE,
    
    -- Escalation
    escalated BOOLEAN DEFAULT false,
    escalated_to UUID,
    escalated_at TIMESTAMP WITH TIME ZONE,
    escalation_reason TEXT,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- Reconciliation rules table
CREATE TABLE reconciliation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(100) NOT NULL UNIQUE,
    rule_type VARCHAR(50) NOT NULL,
    description TEXT,
    
    -- Rule configuration
    matching_criteria JSONB NOT NULL,
    tolerance_amount DECIMAL(19,4),
    tolerance_percentage DECIMAL(5,2),
    
    -- Rule application
    applicable_types reconciliation_type[],
    priority INTEGER DEFAULT 100,
    enabled BOOLEAN DEFAULT true,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0
);

-- Reconciliation adjustments table
CREATE TABLE reconciliation_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID NOT NULL REFERENCES reconciliations(id),
    discrepancy_id UUID REFERENCES reconciliation_discrepancies(id),
    
    -- Adjustment details
    adjustment_type VARCHAR(50) NOT NULL,
    adjustment_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- GL posting
    journal_entry_ref VARCHAR(100),
    posted_date DATE,
    posted BOOLEAN DEFAULT false,
    
    -- Justification
    reason TEXT NOT NULL,
    supporting_documents TEXT[],
    
    -- Approval
    requires_approval BOOLEAN DEFAULT true,
    approved_by UUID,
    approved_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL
);

-- Import/Export logs table
CREATE TABLE reconciliation_import_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID REFERENCES reconciliations(id),
    
    -- Import details
    import_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    file_format VARCHAR(20),
    
    -- Processing results
    total_records INTEGER,
    successful_records INTEGER,
    failed_records INTEGER,
    error_details JSONB,
    
    -- Status
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit fields
    created_by UUID NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_reconciliations_type ON reconciliations(type);
CREATE INDEX idx_reconciliations_status ON reconciliations(status);
CREATE INDEX idx_reconciliations_dates ON reconciliations(start_date, end_date);
CREATE INDEX idx_reconciliations_created_at ON reconciliations(created_at);

CREATE INDEX idx_reconciliation_items_reconciliation ON reconciliation_items(reconciliation_id);
CREATE INDEX idx_reconciliation_items_status ON reconciliation_items(status);
CREATE INDEX idx_reconciliation_items_reference ON reconciliation_items(reference);
CREATE INDEX idx_reconciliation_items_date ON reconciliation_items(date);
CREATE INDEX idx_reconciliation_items_amount ON reconciliation_items(amount);
CREATE INDEX idx_reconciliation_items_matched ON reconciliation_items(matched_item_id);

CREATE INDEX idx_reconciliation_matches_reconciliation ON reconciliation_matches(reconciliation_id);
CREATE INDEX idx_reconciliation_matches_confidence ON reconciliation_matches(match_confidence);
CREATE INDEX idx_reconciliation_matches_manual ON reconciliation_matches(is_manual);

CREATE INDEX idx_discrepancies_reconciliation ON reconciliation_discrepancies(reconciliation_id);
CREATE INDEX idx_discrepancies_type ON reconciliation_discrepancies(type);
CREATE INDEX idx_discrepancies_severity ON reconciliation_discrepancies(severity);
CREATE INDEX idx_discrepancies_status ON reconciliation_discrepancies(status);

CREATE INDEX idx_adjustments_reconciliation ON reconciliation_adjustments(reconciliation_id);
CREATE INDEX idx_adjustments_posted ON reconciliation_adjustments(posted);

-- GIN indexes for JSONB columns
CREATE INDEX idx_reconciliations_metadata ON reconciliations USING GIN(metadata);
CREATE INDEX idx_reconciliation_items_metadata ON reconciliation_items USING GIN(metadata);
CREATE INDEX idx_reconciliation_matches_criteria ON reconciliation_matches USING GIN(match_criteria);
CREATE INDEX idx_reconciliation_rules_criteria ON reconciliation_rules USING GIN(matching_criteria);

-- Update triggers
CREATE TRIGGER update_reconciliations_updated_at 
    BEFORE UPDATE ON reconciliations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reconciliation_items_updated_at 
    BEFORE UPDATE ON reconciliation_items 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_discrepancies_updated_at 
    BEFORE UPDATE ON reconciliation_discrepancies 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reconciliation_rules_updated_at 
    BEFORE UPDATE ON reconciliation_rules 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Default rules
INSERT INTO reconciliation_rules (id, rule_name, rule_type, description, matching_criteria, tolerance_amount, priority, created_by)
VALUES 
    (gen_random_uuid(), 'Exact Amount Match', 'EXACT_MATCH', 'Match transactions with exact amount', 
     '{"field": "amount", "operator": "equals"}', 0.00, 100, '00000000-0000-0000-0000-000000000000'),
    (gen_random_uuid(), 'Reference Match', 'REFERENCE_MATCH', 'Match by reference number', 
     '{"field": "reference", "operator": "equals"}', null, 90, '00000000-0000-0000-0000-000000000000'),
    (gen_random_uuid(), 'Fuzzy Amount Match', 'FUZZY_MATCH', 'Match with small tolerance', 
     '{"field": "amount", "operator": "within_tolerance"}', 0.01, 80, '00000000-0000-0000-0000-000000000000');