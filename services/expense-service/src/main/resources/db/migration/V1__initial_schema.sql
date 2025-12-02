-- Expense Service Initial Schema
-- Created: 2025-09-27
-- Description: Expense tracking, reporting, and reimbursement schema

CREATE TABLE IF NOT EXISTS expense_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id VARCHAR(100) UNIQUE NOT NULL,
    category_name VARCHAR(255) NOT NULL,
    parent_category_id VARCHAR(100),
    category_code VARCHAR(50),
    description TEXT,
    is_reimbursable BOOLEAN DEFAULT TRUE,
    requires_receipt BOOLEAN DEFAULT TRUE,
    gl_account_code VARCHAR(50),
    tax_deductible BOOLEAN DEFAULT FALSE,
    mileage_rate DECIMAL(10, 4),
    per_diem_rate DECIMAL(10, 2),
    daily_limit DECIMAL(10, 2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_parent_category FOREIGN KEY (parent_category_id) REFERENCES expense_category(category_id)
);

CREATE TABLE IF NOT EXISTS expense_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id VARCHAR(100) UNIQUE NOT NULL,
    policy_name VARCHAR(255) NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    description TEXT,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    applies_to TEXT[],
    approval_workflow JSONB NOT NULL,
    expense_limits JSONB,
    category_rules JSONB,
    receipt_requirements JSONB,
    reimbursement_rules JSONB,
    violations_handling TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS expense_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id VARCHAR(100) UNIQUE NOT NULL,
    report_number VARCHAR(100) UNIQUE NOT NULL,
    employee_id VARCHAR(100) NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    department VARCHAR(100),
    cost_center VARCHAR(50),
    report_title VARCHAR(255) NOT NULL,
    business_purpose TEXT,
    report_period_start DATE NOT NULL,
    report_period_end DATE NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    reimbursable_amount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    non_reimbursable_amount DECIMAL(15, 2) DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by VARCHAR(100),
    rejected_at TIMESTAMP,
    rejected_by VARCHAR(100),
    rejection_reason TEXT,
    paid_at TIMESTAMP,
    payment_reference VARCHAR(100),
    policy_id VARCHAR(100),
    policy_violations TEXT[],
    expense_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_policy FOREIGN KEY (policy_id) REFERENCES expense_policy(policy_id)
);

CREATE TABLE IF NOT EXISTS expense_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id VARCHAR(100) UNIQUE NOT NULL,
    report_id VARCHAR(100) NOT NULL,
    category_id VARCHAR(100) NOT NULL,
    expense_date DATE NOT NULL,
    merchant_name VARCHAR(255),
    description TEXT NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    exchange_rate DECIMAL(18, 8),
    base_currency_amount DECIMAL(15, 2),
    tax_amount DECIMAL(15, 2) DEFAULT 0,
    tip_amount DECIMAL(15, 2) DEFAULT 0,
    payment_method VARCHAR(50) NOT NULL,
    card_last_four VARCHAR(4),
    transaction_id VARCHAR(100),
    is_billable BOOLEAN DEFAULT FALSE,
    client_name VARCHAR(255),
    project_code VARCHAR(100),
    cost_center VARCHAR(50),
    gl_account_code VARCHAR(50),
    receipt_required BOOLEAN DEFAULT TRUE,
    receipt_attached BOOLEAN DEFAULT FALSE,
    receipt_urls TEXT[],
    mileage_distance DECIMAL(10, 2),
    mileage_start_location VARCHAR(255),
    mileage_end_location VARCHAR(255),
    attendees TEXT[],
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    policy_violation BOOLEAN DEFAULT FALSE,
    violation_reason TEXT,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_report FOREIGN KEY (report_id) REFERENCES expense_report(report_id) ON DELETE CASCADE,
    CONSTRAINT fk_expense_category FOREIGN KEY (category_id) REFERENCES expense_category(category_id)
);

CREATE TABLE IF NOT EXISTS expense_receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id VARCHAR(100) UNIQUE NOT NULL,
    expense_id VARCHAR(100) NOT NULL,
    receipt_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    file_url TEXT NOT NULL,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(100) NOT NULL,
    ocr_processed BOOLEAN DEFAULT FALSE,
    ocr_data JSONB,
    extracted_amount DECIMAL(15, 2),
    extracted_date DATE,
    extracted_merchant VARCHAR(255),
    verified BOOLEAN DEFAULT FALSE,
    verified_by VARCHAR(100),
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_receipt FOREIGN KEY (expense_id) REFERENCES expense_item(expense_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expense_approval (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_id VARCHAR(100) UNIQUE NOT NULL,
    report_id VARCHAR(100) NOT NULL,
    approval_level INTEGER NOT NULL,
    approver_id VARCHAR(100) NOT NULL,
    approver_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    decision VARCHAR(20),
    comments TEXT,
    signature TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_approval FOREIGN KEY (report_id) REFERENCES expense_report(report_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expense_reimbursement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reimbursement_id VARCHAR(100) UNIQUE NOT NULL,
    report_id VARCHAR(100) NOT NULL,
    employee_id VARCHAR(100) NOT NULL,
    reimbursement_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_date DATE,
    payment_reference VARCHAR(100),
    transaction_id VARCHAR(100),
    bank_account_encrypted VARCHAR(255),
    processing_fee DECIMAL(10, 2) DEFAULT 0,
    net_payment_amount DECIMAL(15, 2) NOT NULL,
    payment_initiated_by VARCHAR(100),
    payment_initiated_at TIMESTAMP,
    payment_completed_at TIMESTAMP,
    payment_failed_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_reimbursement FOREIGN KEY (report_id) REFERENCES expense_report(report_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expense_advance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    advance_id VARCHAR(100) UNIQUE NOT NULL,
    employee_id VARCHAR(100) NOT NULL,
    advance_amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    purpose TEXT NOT NULL,
    requested_date DATE NOT NULL,
    required_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    approval_notes TEXT,
    disbursed_amount DECIMAL(15, 2),
    disbursed_at TIMESTAMP,
    disbursement_reference VARCHAR(100),
    settlement_due_date DATE,
    settled_amount DECIMAL(15, 2) DEFAULT 0,
    settled_at TIMESTAMP,
    balance_amount DECIMAL(15, 2),
    overdue BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS expense_mileage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mileage_id VARCHAR(100) UNIQUE NOT NULL,
    expense_id VARCHAR(100) NOT NULL,
    travel_date DATE NOT NULL,
    start_location VARCHAR(255) NOT NULL,
    end_location VARCHAR(255) NOT NULL,
    start_odometer DECIMAL(10, 2),
    end_odometer DECIMAL(10, 2),
    total_distance DECIMAL(10, 2) NOT NULL,
    distance_unit VARCHAR(10) NOT NULL DEFAULT 'miles',
    vehicle_type VARCHAR(50),
    license_plate VARCHAR(20),
    purpose TEXT NOT NULL,
    mileage_rate DECIMAL(10, 4) NOT NULL,
    calculated_amount DECIMAL(15, 2) NOT NULL,
    route_map_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_mileage FOREIGN KEY (expense_id) REFERENCES expense_item(expense_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expense_per_diem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    per_diem_id VARCHAR(100) UNIQUE NOT NULL,
    expense_id VARCHAR(100) NOT NULL,
    travel_date DATE NOT NULL,
    location_city VARCHAR(100) NOT NULL,
    location_country VARCHAR(3) NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    per_diem_rate DECIMAL(10, 2) NOT NULL,
    actual_amount DECIMAL(10, 2),
    claimed_amount DECIMAL(10, 2) NOT NULL,
    is_full_day BOOLEAN DEFAULT TRUE,
    percentage_claimed DECIMAL(5, 4) DEFAULT 1.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_expense_per_diem FOREIGN KEY (expense_id) REFERENCES expense_item(expense_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expense_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    department VARCHAR(100),
    total_reports INTEGER NOT NULL DEFAULT 0,
    approved_reports INTEGER DEFAULT 0,
    rejected_reports INTEGER DEFAULT 0,
    pending_reports INTEGER DEFAULT 0,
    total_expenses INTEGER DEFAULT 0,
    total_amount DECIMAL(18, 2) DEFAULT 0,
    reimbursed_amount DECIMAL(18, 2) DEFAULT 0,
    pending_reimbursement DECIMAL(18, 2) DEFAULT 0,
    by_category JSONB,
    by_employee JSONB,
    policy_violations INTEGER DEFAULT 0,
    avg_processing_time_days DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_expense_period_dept UNIQUE (period_start, period_end, department)
);

-- Indexes for performance
CREATE INDEX idx_expense_category_parent ON expense_category(parent_category_id);
CREATE INDEX idx_expense_category_active ON expense_category(is_active) WHERE is_active = true;

CREATE INDEX idx_expense_policy_active ON expense_policy(is_active) WHERE is_active = true;
CREATE INDEX idx_expense_policy_effective ON expense_policy(effective_date, expiry_date);

CREATE INDEX idx_expense_report_employee ON expense_report(employee_id);
CREATE INDEX idx_expense_report_department ON expense_report(department);
CREATE INDEX idx_expense_report_status ON expense_report(status);
CREATE INDEX idx_expense_report_submitted ON expense_report(submitted_at DESC);
CREATE INDEX idx_expense_report_period ON expense_report(report_period_end DESC);

CREATE INDEX idx_expense_item_report ON expense_item(report_id);
CREATE INDEX idx_expense_item_category ON expense_item(category_id);
CREATE INDEX idx_expense_item_date ON expense_item(expense_date DESC);
CREATE INDEX idx_expense_item_status ON expense_item(status);
CREATE INDEX idx_expense_item_billable ON expense_item(is_billable) WHERE is_billable = true;
CREATE INDEX idx_expense_item_violation ON expense_item(policy_violation) WHERE policy_violation = true;

CREATE INDEX idx_expense_receipt_expense ON expense_receipt(expense_id);
CREATE INDEX idx_expense_receipt_uploaded ON expense_receipt(uploaded_at DESC);
CREATE INDEX idx_expense_receipt_ocr ON expense_receipt(ocr_processed);

CREATE INDEX idx_expense_approval_report ON expense_approval(report_id);
CREATE INDEX idx_expense_approval_approver ON expense_approval(approver_id);
CREATE INDEX idx_expense_approval_status ON expense_approval(status);
CREATE INDEX idx_expense_approval_pending ON expense_approval(status) WHERE status = 'PENDING';

CREATE INDEX idx_expense_reimbursement_report ON expense_reimbursement(report_id);
CREATE INDEX idx_expense_reimbursement_employee ON expense_reimbursement(employee_id);
CREATE INDEX idx_expense_reimbursement_status ON expense_reimbursement(payment_status);
CREATE INDEX idx_expense_reimbursement_date ON expense_reimbursement(payment_date);

CREATE INDEX idx_expense_advance_employee ON expense_advance(employee_id);
CREATE INDEX idx_expense_advance_status ON expense_advance(status);
CREATE INDEX idx_expense_advance_overdue ON expense_advance(overdue) WHERE overdue = true;

CREATE INDEX idx_expense_mileage_expense ON expense_mileage(expense_id);
CREATE INDEX idx_expense_mileage_date ON expense_mileage(travel_date DESC);

CREATE INDEX idx_expense_per_diem_expense ON expense_per_diem(expense_id);
CREATE INDEX idx_expense_per_diem_date ON expense_per_diem(travel_date DESC);

CREATE INDEX idx_expense_statistics_period ON expense_statistics(period_end DESC);
CREATE INDEX idx_expense_statistics_department ON expense_statistics(department);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_expense_category_updated_at BEFORE UPDATE ON expense_category
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_expense_policy_updated_at BEFORE UPDATE ON expense_policy
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_expense_report_updated_at BEFORE UPDATE ON expense_report
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_expense_item_updated_at BEFORE UPDATE ON expense_item
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_expense_reimbursement_updated_at BEFORE UPDATE ON expense_reimbursement
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_expense_advance_updated_at BEFORE UPDATE ON expense_advance
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();