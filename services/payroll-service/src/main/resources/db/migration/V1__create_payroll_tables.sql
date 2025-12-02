-- Payroll Service Database Schema
-- Version: 1.0
-- Description: Create payroll_batches and payroll_payments tables with indexes

-- ============================================
-- Table: payroll_batches
-- Description: Stores payroll batch metadata
-- ============================================
CREATE TABLE IF NOT EXISTS payroll_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_batch_id VARCHAR(50) NOT NULL,
    company_id VARCHAR(50) NOT NULL,
    pay_period DATE NOT NULL,
    payroll_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,

    -- Employee counts
    total_employees INTEGER NOT NULL DEFAULT 0,
    successful_payments INTEGER NOT NULL DEFAULT 0,
    failed_payments INTEGER NOT NULL DEFAULT 0,
    pending_payments INTEGER NOT NULL DEFAULT 0,

    -- Financial amounts
    gross_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    total_deductions DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    total_tax_withheld DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    net_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,

    -- Processing metadata
    processing_started_at TIMESTAMP,
    processing_completed_at TIMESTAMP,
    estimated_completion_time TIMESTAMP,
    actual_processing_time_ms BIGINT,

    -- Approval workflow
    requires_approval BOOLEAN NOT NULL DEFAULT false,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,

    -- Compliance
    compliance_violations INTEGER NOT NULL DEFAULT 0,
    compliance_status VARCHAR(50),

    -- Correlation and tracking
    correlation_id VARCHAR(100) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,

    -- Metadata (JSONB for extensibility)
    metadata JSONB DEFAULT '{}'::jsonb,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT uk_payroll_batch_id_company UNIQUE (payroll_batch_id, company_id),
    CONSTRAINT chk_total_employees CHECK (total_employees >= 0),
    CONSTRAINT chk_successful_payments CHECK (successful_payments >= 0),
    CONSTRAINT chk_failed_payments CHECK (failed_payments >= 0),
    CONSTRAINT chk_gross_amount CHECK (gross_amount >= 0),
    CONSTRAINT chk_net_amount CHECK (net_amount >= 0)
);

-- Indexes for payroll_batches
CREATE INDEX idx_payroll_batches_company_id ON payroll_batches(company_id);
CREATE INDEX idx_payroll_batches_status ON payroll_batches(status);
CREATE INDEX idx_payroll_batches_pay_period ON payroll_batches(pay_period);
CREATE INDEX idx_payroll_batches_company_pay_period ON payroll_batches(company_id, pay_period);
CREATE INDEX idx_payroll_batches_created_at ON payroll_batches(created_at);
CREATE INDEX idx_payroll_batches_correlation_id ON payroll_batches(correlation_id);

-- Comments for payroll_batches
COMMENT ON TABLE payroll_batches IS 'Stores payroll batch processing records';
COMMENT ON COLUMN payroll_batches.payroll_batch_id IS 'Unique batch identifier (e.g., PB-COMP01-ABC123)';
COMMENT ON COLUMN payroll_batches.correlation_id IS 'Correlation ID for distributed tracing';
COMMENT ON COLUMN payroll_batches.metadata IS 'Extensible metadata in JSON format';
COMMENT ON COLUMN payroll_batches.version IS 'Optimistic locking version';

-- ============================================
-- Table: payroll_payments
-- Description: Stores individual employee payments
-- ============================================
CREATE TABLE IF NOT EXISTS payroll_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES payroll_batches(id) ON DELETE CASCADE,
    payroll_batch_id VARCHAR(50) NOT NULL,
    company_id VARCHAR(50) NOT NULL,
    employee_id VARCHAR(50) NOT NULL,

    -- Employee information
    employee_name VARCHAR(255),
    employee_ssn VARCHAR(11), -- Encrypted: XXX-XX-XXXX
    job_title VARCHAR(255),
    department VARCHAR(255),

    -- Pay period
    pay_period DATE NOT NULL,
    pay_date DATE,

    -- Salary calculation
    hourly_rate DECIMAL(10, 2),
    hours_worked DECIMAL(10, 2),
    base_pay DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    overtime_pay DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    bonus_pay DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    commission_pay DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    gross_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,

    -- Tax withholdings
    federal_tax DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    state_tax DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    local_tax DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    social_security_tax DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    medicare_tax DECIMAL(19, 2) NOT NULL DEFAULT 0.00,

    -- Deductions
    health_insurance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    retirement_401k DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    other_deductions DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    total_deductions DECIMAL(19, 2) NOT NULL DEFAULT 0.00,

    -- Final amount
    net_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,

    -- Bank transfer details
    payment_method VARCHAR(50), -- ACH, WIRE, CHECK
    bank_name VARCHAR(255),
    routing_number VARCHAR(9), -- Encrypted
    account_number VARCHAR(17), -- Encrypted
    account_type VARCHAR(20), -- CHECKING, SAVINGS

    -- Transfer tracking
    transfer_id VARCHAR(100),
    transaction_id VARCHAR(100),
    settlement_date DATE,

    -- Payment status
    status VARCHAR(50) NOT NULL,
    status_reason TEXT,

    -- Retry tracking
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_retry_at TIMESTAMP,

    -- Metadata (JSONB for extensibility)
    metadata JSONB DEFAULT '{}'::jsonb,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_gross_amount_positive CHECK (gross_amount >= 0),
    CONSTRAINT chk_net_amount_positive CHECK (net_amount >= 0),
    CONSTRAINT chk_hours_worked CHECK (hours_worked IS NULL OR hours_worked >= 0)
);

-- Indexes for payroll_payments
CREATE INDEX idx_payroll_payments_batch_id ON payroll_payments(batch_id);
CREATE INDEX idx_payroll_payments_employee_id ON payroll_payments(employee_id);
CREATE INDEX idx_payroll_payments_company_id ON payroll_payments(company_id);
CREATE INDEX idx_payroll_payments_pay_period ON payroll_payments(pay_period);
CREATE INDEX idx_payroll_payments_status ON payroll_payments(status);
CREATE INDEX idx_payroll_payments_company_pay_period ON payroll_payments(company_id, pay_period);
CREATE INDEX idx_payroll_payments_employee_pay_period ON payroll_payments(employee_id, pay_period);
CREATE INDEX idx_payroll_payments_transaction_id ON payroll_payments(transaction_id) WHERE transaction_id IS NOT NULL;

-- Comments for payroll_payments
COMMENT ON TABLE payroll_payments IS 'Stores individual employee payroll payment records';
COMMENT ON COLUMN payroll_payments.employee_ssn IS 'Encrypted SSN in format XXX-XX-XXXX';
COMMENT ON COLUMN payroll_payments.routing_number IS 'Encrypted ABA routing number';
COMMENT ON COLUMN payroll_payments.account_number IS 'Encrypted bank account number';
COMMENT ON COLUMN payroll_payments.metadata IS 'Extensible metadata in JSON format';
COMMENT ON COLUMN payroll_payments.version IS 'Optimistic locking version';

-- ============================================
-- Trigger: Update updated_at timestamp
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_payroll_batches_updated_at
    BEFORE UPDATE ON payroll_batches
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payroll_payments_updated_at
    BEFORE UPDATE ON payroll_payments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- Grant permissions (adjust as needed)
-- ============================================
-- GRANT SELECT, INSERT, UPDATE, DELETE ON payroll_batches TO payroll_service_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON payroll_payments TO payroll_service_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO payroll_service_user;
