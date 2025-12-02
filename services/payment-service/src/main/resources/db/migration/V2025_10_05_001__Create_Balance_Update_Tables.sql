-- Migration: Create Balance Update Tables
-- Description: Creates tables for tracking balance updates across accounts
-- Author: System
-- Date: 2025-10-05

-- Create balance_updates table
CREATE TABLE IF NOT EXISTS balance_updates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(100),
    original_transaction_id VARCHAR(100),
    update_type VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 4),
    previous_balance DECIMAL(19, 4),
    new_balance DECIMAL(19, 4),
    available_balance DECIMAL(19, 4),
    description VARCHAR(500),
    adjustment_reason VARCHAR(255),
    hold_reason VARCHAR(255),
    reversal_reason VARCHAR(255),
    fee_type VARCHAR(100),
    interest_rate DECIMAL(5, 2),
    processed_at TIMESTAMP,
    correlation_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Indexes for performance
    CONSTRAINT balance_updates_pkey PRIMARY KEY (id)
);

-- Create indexes
CREATE INDEX idx_balance_updates_account_id ON balance_updates(account_id);
CREATE INDEX idx_balance_updates_transaction_id ON balance_updates(transaction_id);
CREATE INDEX idx_balance_updates_update_type ON balance_updates(update_type);
CREATE INDEX idx_balance_updates_processed_at ON balance_updates(processed_at);
CREATE INDEX idx_balance_updates_correlation_id ON balance_updates(correlation_id);

-- Add comments
COMMENT ON TABLE balance_updates IS 'Tracks all balance update operations for auditing and reconciliation';
COMMENT ON COLUMN balance_updates.account_id IS 'Reference to the account being updated';
COMMENT ON COLUMN balance_updates.update_type IS 'Type of update: CREDIT, DEBIT, ADJUSTMENT, HOLD, RELEASE_HOLD, REVERSAL, INTEREST_ACCRUAL, FEE_DEDUCTION';
COMMENT ON COLUMN balance_updates.correlation_id IS 'Correlation ID for tracing related operations';
