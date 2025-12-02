-- Migration V2: Add missing tables for TransactionFee, TaxCalculation, AuditTrail, SettlementEntry
-- Created: 2025-11-05
-- Description: Additional tables to complete accounting service schema

-- Settlement Entry table for merchant settlements
CREATE TABLE IF NOT EXISTS settlement_entry (
    id VARCHAR(100) PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    gross_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    processing_fee DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_fees DECIMAL(18, 2) NOT NULL DEFAULT 0,
    taxes DECIMAL(18, 2) NOT NULL DEFAULT 0,
    net_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settlement_date DATE NOT NULL,
    settled_at TIMESTAMP,
    payout_reference VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_settlement_amounts CHECK (gross_amount >= 0 AND net_amount >= 0)
);

-- Transaction Fee table for tracking all fees
CREATE TABLE IF NOT EXISTS transaction_fee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    fee_type VARCHAR(50) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    fee_percentage DECIMAL(5, 4),
    fixed_amount DECIMAL(18, 2),
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fee_amount CHECK (amount >= 0)
);

-- Tax Calculation table for tax tracking
CREATE TABLE IF NOT EXISTS tax_calculation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    tax_type VARCHAR(50) NOT NULL,
    tax_rate DECIMAL(5, 4) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    jurisdiction VARCHAR(100),
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tax_amounts CHECK (tax_amount >= 0 AND taxable_amount >= 0)
);

-- Audit Trail table for comprehensive audit logging
CREATE TABLE IF NOT EXISTS audit_trail (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    details JSONB,
    before_state JSONB,
    after_state JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for SettlementEntry
CREATE INDEX idx_settlement_merchant ON settlement_entry(merchant_id);
CREATE INDEX idx_settlement_date ON settlement_entry(settlement_date DESC);
CREATE INDEX idx_settlement_status ON settlement_entry(status);
CREATE INDEX idx_settlement_transaction ON settlement_entry(transaction_id);
CREATE INDEX idx_settlement_created ON settlement_entry(created_at DESC);

-- Indexes for TransactionFee
CREATE INDEX idx_fee_transaction ON transaction_fee(transaction_id);
CREATE INDEX idx_fee_type ON transaction_fee(fee_type);
CREATE INDEX idx_fee_created ON transaction_fee(created_at DESC);
CREATE INDEX idx_fee_currency ON transaction_fee(currency);

-- Indexes for TaxCalculation
CREATE INDEX idx_tax_transaction ON tax_calculation(transaction_id);
CREATE INDEX idx_tax_type ON tax_calculation(tax_type);
CREATE INDEX idx_tax_jurisdiction ON tax_calculation(jurisdiction);
CREATE INDEX idx_tax_created ON tax_calculation(created_at DESC);
CREATE INDEX idx_tax_currency ON tax_calculation(currency);

-- Indexes for AuditTrail (critical for compliance and performance)
CREATE INDEX idx_audit_entity ON audit_trail(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_trail(user_id);
CREATE INDEX idx_audit_timestamp ON audit_trail(timestamp DESC);
CREATE INDEX idx_audit_action ON audit_trail(action);
CREATE INDEX idx_audit_entity_timestamp ON audit_trail(entity_type, entity_id, timestamp DESC);

-- Composite indexes for common queries
CREATE INDEX idx_settlement_merchant_date ON settlement_entry(merchant_id, settlement_date DESC);
CREATE INDEX idx_settlement_status_date ON settlement_entry(status, settlement_date DESC);
CREATE INDEX idx_fee_transaction_type ON transaction_fee(transaction_id, fee_type);
CREATE INDEX idx_tax_transaction_type ON tax_calculation(transaction_id, tax_type);

-- Partial indexes for performance (only index pending settlements)
CREATE INDEX idx_settlement_pending ON settlement_entry(settlement_date) WHERE status = 'PENDING';

-- GIN indexes for JSONB columns
CREATE INDEX idx_audit_details_gin ON audit_trail USING GIN (details);
CREATE INDEX idx_audit_before_state_gin ON audit_trail USING GIN (before_state);
CREATE INDEX idx_audit_after_state_gin ON audit_trail USING GIN (after_state);

-- Add version column to settlement_entry for optimistic locking
ALTER TABLE settlement_entry ADD COLUMN version INTEGER DEFAULT 0;

-- Comments for documentation
COMMENT ON TABLE settlement_entry IS 'Merchant settlement tracking with T+2 processing';
COMMENT ON TABLE transaction_fee IS 'Transaction fee breakdown (platform, processor, etc.)';
COMMENT ON TABLE tax_calculation IS 'Tax calculations per transaction';
COMMENT ON TABLE audit_trail IS 'Comprehensive audit log for SOX/PCI compliance';

COMMENT ON COLUMN settlement_entry.gross_amount IS 'Total transaction amount before fees';
COMMENT ON COLUMN settlement_entry.net_amount IS 'Amount to be settled to merchant';
COMMENT ON COLUMN transaction_fee.fee_type IS 'Fee type: PLATFORM, PROCESSOR, NETWORK, etc.';
COMMENT ON COLUMN tax_calculation.tax_type IS 'Tax type: SALES_TAX, VAT, etc.';
COMMENT ON COLUMN audit_trail.entity_type IS 'Entity being audited: JOURNAL_ENTRY, SETTLEMENT, etc.';
