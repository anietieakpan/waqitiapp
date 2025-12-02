-- Dispute Service Initial Schema
-- Created: 2025-09-27
-- Description: Transaction dispute management and resolution schema

CREATE TABLE IF NOT EXISTS dispute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id VARCHAR(100) UNIQUE NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    account_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    dispute_type VARCHAR(50) NOT NULL,
    dispute_category VARCHAR(50) NOT NULL,
    dispute_reason VARCHAR(100) NOT NULL,
    dispute_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    original_transaction_amount DECIMAL(15, 2) NOT NULL,
    original_transaction_date DATE NOT NULL,
    merchant_name VARCHAR(255),
    merchant_id VARCHAR(100),
    dispute_status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    priority VARCHAR(20) DEFAULT 'NORMAL',
    filing_method VARCHAR(50) NOT NULL DEFAULT 'ONLINE',
    description TEXT NOT NULL,
    evidence_required TEXT[],
    is_provisional_credit_issued BOOLEAN DEFAULT FALSE,
    provisional_credit_amount DECIMAL(15, 2),
    provisional_credit_date DATE,
    provisional_credit_reversed BOOLEAN DEFAULT FALSE,
    provisional_credit_reversal_date DATE,
    assigned_to VARCHAR(100),
    assigned_at TIMESTAMP,
    investigation_started_at TIMESTAMP,
    investigation_completed_at TIMESTAMP,
    resolution VARCHAR(50),
    resolution_date DATE,
    resolution_amount DECIMAL(15, 2),
    resolution_notes TEXT,
    customer_satisfied BOOLEAN,
    satisfaction_rating INTEGER,
    feedback TEXT,
    escalated BOOLEAN DEFAULT FALSE,
    escalated_to VARCHAR(100),
    escalated_at TIMESTAMP,
    escalation_reason TEXT,
    due_date DATE NOT NULL,
    regulation_type VARCHAR(50),
    is_regulatory_dispute BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS dispute_communication (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    communication_id VARCHAR(100) UNIQUE NOT NULL,
    dispute_id VARCHAR(100) NOT NULL,
    communication_type VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    sender VARCHAR(100) NOT NULL,
    recipient VARCHAR(100) NOT NULL,
    subject VARCHAR(255),
    message TEXT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    attachments TEXT[],
    is_internal BOOLEAN DEFAULT FALSE,
    is_automated BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_communication FOREIGN KEY (dispute_id) REFERENCES dispute(dispute_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dispute_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id VARCHAR(100) UNIQUE NOT NULL,
    dispute_id VARCHAR(100) NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    description TEXT,
    file_name VARCHAR(255),
    file_url TEXT,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    uploaded_by VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified BOOLEAN DEFAULT FALSE,
    verified_by VARCHAR(100),
    verified_at TIMESTAMP,
    is_supporting BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_evidence FOREIGN KEY (dispute_id) REFERENCES dispute(dispute_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dispute_timeline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    event_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    performed_by VARCHAR(100),
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    event_data JSONB,
    is_system_generated BOOLEAN DEFAULT FALSE,
    is_customer_visible BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_timeline FOREIGN KEY (dispute_id) REFERENCES dispute(dispute_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dispute_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id VARCHAR(100) NOT NULL,
    assigned_to VARCHAR(100) NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    assignment_reason TEXT,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unassigned_at TIMESTAMP,
    unassignment_reason TEXT,
    is_current BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_assignment FOREIGN KEY (dispute_id) REFERENCES dispute(dispute_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dispute_merchant_response (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    response_id VARCHAR(100) UNIQUE NOT NULL,
    dispute_id VARCHAR(100) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    response_type VARCHAR(50) NOT NULL,
    response_action VARCHAR(50) NOT NULL,
    response_text TEXT,
    refund_amount DECIMAL(15, 2),
    refund_offered BOOLEAN DEFAULT FALSE,
    evidence_provided TEXT[],
    submitted_by VARCHAR(100) NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_by_customer BOOLEAN,
    accepted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merchant_response_dispute FOREIGN KEY (dispute_id) REFERENCES dispute(dispute_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dispute_investigation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id VARCHAR(100) UNIQUE NOT NULL,
    dispute_id VARCHAR(100) NOT NULL,
    investigator VARCHAR(100) NOT NULL,
    investigation_type VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    findings TEXT,
    recommendation VARCHAR(50),
    confidence_level VARCHAR(20),
    evidence_reviewed TEXT[],
    contacts_made TEXT[],
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_investigation FOREIGN KEY (dispute_id) REFERENCES dispute(dispute_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS dispute_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_disputes INTEGER NOT NULL DEFAULT 0,
    total_dispute_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    resolved_count INTEGER DEFAULT 0,
    pending_count INTEGER DEFAULT 0,
    escalated_count INTEGER DEFAULT 0,
    customer_favor_count INTEGER DEFAULT 0,
    merchant_favor_count INTEGER DEFAULT 0,
    split_decision_count INTEGER DEFAULT 0,
    provisional_credits_issued INTEGER DEFAULT 0,
    provisional_credits_amount DECIMAL(18, 2) DEFAULT 0,
    avg_resolution_time_days DECIMAL(10, 2),
    customer_satisfaction_avg DECIMAL(3, 2),
    by_category JSONB,
    by_type JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_dispute_period UNIQUE (period_start, period_end)
);

CREATE TABLE IF NOT EXISTS dispute_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id VARCHAR(100) UNIQUE NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    dispute_category VARCHAR(50) NOT NULL,
    subject VARCHAR(255),
    body TEXT NOT NULL,
    variables TEXT[],
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_dispute_transaction ON dispute(transaction_id);
CREATE INDEX idx_dispute_account ON dispute(account_id);
CREATE INDEX idx_dispute_customer ON dispute(customer_id);
CREATE INDEX idx_dispute_merchant ON dispute(merchant_id);
CREATE INDEX idx_dispute_status ON dispute(dispute_status);
CREATE INDEX idx_dispute_category ON dispute(dispute_category);
CREATE INDEX idx_dispute_priority ON dispute(priority);
CREATE INDEX idx_dispute_assigned ON dispute(assigned_to);
CREATE INDEX idx_dispute_due_date ON dispute(due_date);
CREATE INDEX idx_dispute_created ON dispute(created_at DESC);
CREATE INDEX idx_dispute_escalated ON dispute(escalated) WHERE escalated = true;
CREATE INDEX idx_dispute_regulatory ON dispute(is_regulatory_dispute) WHERE is_regulatory_dispute = true;

CREATE INDEX idx_dispute_comm_dispute ON dispute_communication(dispute_id);
CREATE INDEX idx_dispute_comm_type ON dispute_communication(communication_type);
CREATE INDEX idx_dispute_comm_sent ON dispute_communication(sent_at DESC);
CREATE INDEX idx_dispute_comm_internal ON dispute_communication(is_internal);

CREATE INDEX idx_dispute_evidence_dispute ON dispute_evidence(dispute_id);
CREATE INDEX idx_dispute_evidence_type ON dispute_evidence(evidence_type);
CREATE INDEX idx_dispute_evidence_uploaded ON dispute_evidence(uploaded_at DESC);
CREATE INDEX idx_dispute_evidence_verified ON dispute_evidence(verified);

CREATE INDEX idx_dispute_timeline_dispute ON dispute_timeline(dispute_id);
CREATE INDEX idx_dispute_timeline_event ON dispute_timeline(event_type);
CREATE INDEX idx_dispute_timeline_date ON dispute_timeline(event_date DESC);

CREATE INDEX idx_dispute_assignment_dispute ON dispute_assignment(dispute_id);
CREATE INDEX idx_dispute_assignment_user ON dispute_assignment(assigned_to);
CREATE INDEX idx_dispute_assignment_current ON dispute_assignment(is_current) WHERE is_current = true;

CREATE INDEX idx_merchant_response_dispute ON dispute_merchant_response(dispute_id);
CREATE INDEX idx_merchant_response_merchant ON dispute_merchant_response(merchant_id);
CREATE INDEX idx_merchant_response_submitted ON dispute_merchant_response(submitted_at DESC);

CREATE INDEX idx_dispute_investigation_dispute ON dispute_investigation(dispute_id);
CREATE INDEX idx_dispute_investigation_investigator ON dispute_investigation(investigator);
CREATE INDEX idx_dispute_investigation_status ON dispute_investigation(status);

CREATE INDEX idx_dispute_stats_period ON dispute_statistics(period_end DESC);

CREATE INDEX idx_dispute_template_type ON dispute_template(template_type);
CREATE INDEX idx_dispute_template_category ON dispute_template(dispute_category);
CREATE INDEX idx_dispute_template_active ON dispute_template(is_active) WHERE is_active = true;

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_dispute_updated_at BEFORE UPDATE ON dispute
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_response_updated_at BEFORE UPDATE ON dispute_merchant_response
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dispute_investigation_updated_at BEFORE UPDATE ON dispute_investigation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dispute_template_updated_at BEFORE UPDATE ON dispute_template
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();