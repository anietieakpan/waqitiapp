-- Migration: Create Batch Payment Tables
-- Description: Creates tables for batch payment processing
-- Author: System
-- Date: 2025-10-05

-- Create batch_payments table
CREATE TABLE IF NOT EXISTS batch_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id VARCHAR(100) NOT NULL UNIQUE,
    batch_type VARCHAR(50) NOT NULL,
    merchant_id VARCHAR(50),
    currency CHAR(3) NOT NULL,
    total_amount DECIMAL(19, 4),
    payment_count INTEGER,
    processed_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    success_rate DECIMAL(5, 2),
    processing_mode VARCHAR(50),
    priority VARCHAR(50),
    auto_settle BOOLEAN DEFAULT FALSE,
    settlement_date TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    validation_result TEXT,
    fraud_screening_result TEXT,
    processing_result TEXT,
    requested_by VARCHAR(255),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT batch_payments_pkey PRIMARY KEY (id),
    CONSTRAINT batch_payments_batch_id_unique UNIQUE (batch_id)
);

-- Create indexes for performance
CREATE INDEX idx_batch_payments_batch_id ON batch_payments(batch_id);
CREATE INDEX idx_batch_payments_merchant_id ON batch_payments(merchant_id);
CREATE INDEX idx_batch_payments_status ON batch_payments(status);
CREATE INDEX idx_batch_payments_created_at ON batch_payments(created_at);
CREATE INDEX idx_batch_payments_completed_at ON batch_payments(completed_at);

-- Add comments
COMMENT ON TABLE batch_payments IS 'Stores batch payment processing records for high-volume transactions';
COMMENT ON COLUMN batch_payments.batch_id IS 'Unique identifier for the batch';
COMMENT ON COLUMN batch_payments.batch_type IS 'Type: STANDARD, EXPRESS, BULK, PAYROLL, SETTLEMENT, MERCHANT_PAYOUT';
COMMENT ON COLUMN batch_payments.status IS 'Status: PENDING, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED';
COMMENT ON COLUMN batch_payments.success_rate IS 'Percentage of successfully processed payments';
COMMENT ON COLUMN batch_payments.validation_result IS 'JSON result of batch validation';
COMMENT ON COLUMN batch_payments.fraud_screening_result IS 'JSON result of fraud screening';
COMMENT ON COLUMN batch_payments.processing_result IS 'JSON result of batch processing';
