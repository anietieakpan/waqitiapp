-- AR Payment Service Initial Schema
-- Created: 2025-09-27
-- Description: Accounts receivable and payment processing schema

CREATE TABLE IF NOT EXISTS ar_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    account_number VARCHAR(50) UNIQUE NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    credit_limit DECIMAL(18, 2) DEFAULT 0,
    available_credit DECIMAL(18, 2) DEFAULT 0,
    outstanding_balance DECIMAL(18, 2) DEFAULT 0,
    aging_bucket VARCHAR(20) DEFAULT 'CURRENT',
    payment_terms VARCHAR(50) DEFAULT 'NET_30',
    interest_rate DECIMAL(5, 4) DEFAULT 0,
    late_fee_rate DECIMAL(5, 4) DEFAULT 0,
    billing_cycle VARCHAR(20) DEFAULT 'MONTHLY',
    next_billing_date DATE,
    last_payment_date DATE,
    last_statement_date DATE,
    is_frozen BOOLEAN DEFAULT FALSE,
    frozen_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ar_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    invoice_number VARCHAR(100) UNIQUE NOT NULL,
    invoice_type VARCHAR(50) NOT NULL,
    invoice_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal_amount DECIMAL(18, 2) NOT NULL,
    tax_amount DECIMAL(18, 2) DEFAULT 0,
    discount_amount DECIMAL(18, 2) DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL,
    paid_amount DECIMAL(18, 2) DEFAULT 0,
    outstanding_amount DECIMAL(18, 2) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    payment_terms VARCHAR(50) DEFAULT 'NET_30',
    reference_number VARCHAR(100),
    description TEXT,
    notes TEXT,
    billing_address JSONB,
    shipping_address JSONB,
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_invoice_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_invoice_line_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_item_id VARCHAR(100) UNIQUE NOT NULL,
    invoice_id VARCHAR(100) NOT NULL,
    line_number INTEGER NOT NULL,
    product_id VARCHAR(100),
    service_id VARCHAR(100),
    description TEXT NOT NULL,
    quantity DECIMAL(10, 4) NOT NULL DEFAULT 1,
    unit_price DECIMAL(18, 2) NOT NULL,
    discount_percentage DECIMAL(5, 4) DEFAULT 0,
    discount_amount DECIMAL(18, 2) DEFAULT 0,
    tax_percentage DECIMAL(5, 4) DEFAULT 0,
    tax_amount DECIMAL(18, 2) DEFAULT 0,
    line_total DECIMAL(18, 2) NOT NULL,
    gl_account_code VARCHAR(50),
    cost_center VARCHAR(50),
    project_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_invoice_line_item FOREIGN KEY (invoice_id) REFERENCES ar_invoice(invoice_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_type VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    payment_amount DECIMAL(18, 2) NOT NULL,
    fee_amount DECIMAL(18, 2) DEFAULT 0,
    net_amount DECIMAL(18, 2) NOT NULL,
    payment_date DATE NOT NULL,
    value_date DATE,
    reference_number VARCHAR(100),
    confirmation_number VARCHAR(100),
    payment_instrument JSONB,
    bank_details JSONB,
    processing_fee DECIMAL(18, 2) DEFAULT 0,
    exchange_rate DECIMAL(10, 6) DEFAULT 1,
    base_currency_amount DECIMAL(18, 2),
    payment_source VARCHAR(100),
    notes TEXT,
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_payment_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_payment_allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    allocation_id VARCHAR(100) UNIQUE NOT NULL,
    payment_id VARCHAR(100) NOT NULL,
    invoice_id VARCHAR(100) NOT NULL,
    allocation_amount DECIMAL(18, 2) NOT NULL,
    allocation_date DATE NOT NULL,
    allocation_type VARCHAR(50) DEFAULT 'PAYMENT',
    write_off_reason VARCHAR(100),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_payment_allocation_payment FOREIGN KEY (payment_id) REFERENCES ar_payment(payment_id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_payment_allocation_invoice FOREIGN KEY (invoice_id) REFERENCES ar_invoice(invoice_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_credit_memo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_memo_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    credit_memo_number VARCHAR(100) UNIQUE NOT NULL,
    related_invoice_id VARCHAR(100),
    credit_type VARCHAR(50) NOT NULL,
    credit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    credit_amount DECIMAL(18, 2) NOT NULL,
    used_amount DECIMAL(18, 2) DEFAULT 0,
    remaining_amount DECIMAL(18, 2) NOT NULL,
    issue_date DATE NOT NULL,
    expiry_date DATE,
    reason VARCHAR(100) NOT NULL,
    description TEXT,
    approval_status VARCHAR(20) DEFAULT 'PENDING',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_credit_memo_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_statement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    statement_number VARCHAR(100) UNIQUE NOT NULL,
    statement_period VARCHAR(20) NOT NULL,
    statement_date DATE NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    opening_balance DECIMAL(18, 2) NOT NULL,
    closing_balance DECIMAL(18, 2) NOT NULL,
    total_charges DECIMAL(18, 2) DEFAULT 0,
    total_payments DECIMAL(18, 2) DEFAULT 0,
    total_credits DECIMAL(18, 2) DEFAULT 0,
    past_due_amount DECIMAL(18, 2) DEFAULT 0,
    minimum_payment_due DECIMAL(18, 2) DEFAULT 0,
    payment_due_date DATE,
    statement_status VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    sent_date DATE,
    delivery_method VARCHAR(50),
    file_path VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_statement_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_aging_bucket (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_id VARCHAR(100) UNIQUE NOT NULL,
    bucket_name VARCHAR(255) NOT NULL,
    days_from INTEGER NOT NULL,
    days_to INTEGER,
    bucket_order INTEGER NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    collection_priority INTEGER DEFAULT 100,
    automatic_actions JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ar_collection_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    invoice_id VARCHAR(100),
    activity_type VARCHAR(50) NOT NULL,
    activity_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    priority VARCHAR(20) DEFAULT 'NORMAL',
    subject VARCHAR(255),
    description TEXT NOT NULL,
    outcome VARCHAR(100),
    next_action VARCHAR(100),
    follow_up_date DATE,
    assigned_to VARCHAR(100),
    completed_by VARCHAR(100),
    completed_at TIMESTAMP,
    contact_method VARCHAR(50),
    contact_details JSONB,
    result_data JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_collection_activity_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_dunning_letter (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    letter_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    letter_template_id VARCHAR(100) NOT NULL,
    letter_type VARCHAR(50) NOT NULL,
    letter_level INTEGER NOT NULL,
    letter_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount_due DECIMAL(18, 2) NOT NULL,
    overdue_amount DECIMAL(18, 2) NOT NULL,
    days_overdue INTEGER NOT NULL,
    generated_date DATE NOT NULL,
    sent_date DATE,
    delivery_method VARCHAR(50),
    response_deadline DATE,
    escalation_date DATE,
    file_path VARCHAR(1000),
    tracking_number VARCHAR(100),
    delivery_status VARCHAR(20),
    response_received BOOLEAN DEFAULT FALSE,
    response_date DATE,
    response_details TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_dunning_letter_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_dispute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    invoice_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    dispute_type VARCHAR(50) NOT NULL,
    dispute_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    dispute_amount DECIMAL(18, 2) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    supporting_documents TEXT[],
    priority VARCHAR(20) DEFAULT 'NORMAL',
    raised_date DATE NOT NULL,
    assigned_to VARCHAR(100),
    resolution_deadline DATE,
    resolved_date DATE,
    resolution VARCHAR(100),
    resolution_amount DECIMAL(18, 2),
    resolution_notes TEXT,
    customer_satisfaction_rating INTEGER,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_dispute_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_dispute_invoice FOREIGN KEY (invoice_id) REFERENCES ar_invoice(invoice_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_write_off (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    write_off_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    invoice_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    write_off_type VARCHAR(50) NOT NULL,
    write_off_amount DECIMAL(18, 2) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    justification TEXT NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    gl_account_code VARCHAR(50),
    recovery_potential VARCHAR(20),
    recovery_notes TEXT,
    write_off_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ar_write_off_account FOREIGN KEY (account_id) REFERENCES ar_account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_write_off_invoice FOREIGN KEY (invoice_id) REFERENCES ar_invoice(invoice_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ar_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_receivables DECIMAL(18, 2) DEFAULT 0,
    current_receivables DECIMAL(18, 2) DEFAULT 0,
    overdue_receivables DECIMAL(18, 2) DEFAULT 0,
    bad_debt_provision DECIMAL(18, 2) DEFAULT 0,
    collection_rate DECIMAL(5, 4) DEFAULT 0,
    days_sales_outstanding DECIMAL(10, 2) DEFAULT 0,
    aging_analysis JSONB,
    payment_trends JSONB,
    collection_efficiency JSONB,
    dispute_metrics JSONB,
    write_off_analysis JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ar_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_invoices INTEGER DEFAULT 0,
    total_payments INTEGER DEFAULT 0,
    total_receivables DECIMAL(18, 2) DEFAULT 0,
    collected_amount DECIMAL(18, 2) DEFAULT 0,
    overdue_amount DECIMAL(18, 2) DEFAULT 0,
    avg_days_to_payment DECIMAL(10, 2),
    collection_success_rate DECIMAL(5, 4),
    dispute_resolution_rate DECIMAL(5, 4),
    by_aging_bucket JSONB,
    by_payment_method JSONB,
    top_delinquent_accounts JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_ar_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_ar_account_customer ON ar_account(customer_id);
CREATE INDEX idx_ar_account_status ON ar_account(account_status);
CREATE INDEX idx_ar_account_aging ON ar_account(aging_bucket);
CREATE INDEX idx_ar_account_balance ON ar_account(outstanding_balance DESC);

CREATE INDEX idx_ar_invoice_account ON ar_invoice(account_id);
CREATE INDEX idx_ar_invoice_customer ON ar_invoice(customer_id);
CREATE INDEX idx_ar_invoice_status ON ar_invoice(invoice_status);
CREATE INDEX idx_ar_invoice_due_date ON ar_invoice(due_date);
CREATE INDEX idx_ar_invoice_outstanding ON ar_invoice(outstanding_amount DESC);

CREATE INDEX idx_ar_invoice_line_item_invoice ON ar_invoice_line_item(invoice_id);
CREATE INDEX idx_ar_invoice_line_item_product ON ar_invoice_line_item(product_id);

CREATE INDEX idx_ar_payment_account ON ar_payment(account_id);
CREATE INDEX idx_ar_payment_customer ON ar_payment(customer_id);
CREATE INDEX idx_ar_payment_status ON ar_payment(payment_status);
CREATE INDEX idx_ar_payment_date ON ar_payment(payment_date DESC);

CREATE INDEX idx_ar_payment_allocation_payment ON ar_payment_allocation(payment_id);
CREATE INDEX idx_ar_payment_allocation_invoice ON ar_payment_allocation(invoice_id);

CREATE INDEX idx_ar_credit_memo_account ON ar_credit_memo(account_id);
CREATE INDEX idx_ar_credit_memo_invoice ON ar_credit_memo(related_invoice_id);
CREATE INDEX idx_ar_credit_memo_status ON ar_credit_memo(credit_status);

CREATE INDEX idx_ar_statement_account ON ar_statement(account_id);
CREATE INDEX idx_ar_statement_date ON ar_statement(statement_date DESC);
CREATE INDEX idx_ar_statement_status ON ar_statement(statement_status);

CREATE INDEX idx_ar_aging_bucket_order ON ar_aging_bucket(bucket_order);
CREATE INDEX idx_ar_aging_bucket_active ON ar_aging_bucket(is_active) WHERE is_active = true;

CREATE INDEX idx_ar_collection_activity_account ON ar_collection_activity(account_id);
CREATE INDEX idx_ar_collection_activity_type ON ar_collection_activity(activity_type);
CREATE INDEX idx_ar_collection_activity_assigned ON ar_collection_activity(assigned_to);
CREATE INDEX idx_ar_collection_activity_follow_up ON ar_collection_activity(follow_up_date);

CREATE INDEX idx_ar_dunning_letter_account ON ar_dunning_letter(account_id);
CREATE INDEX idx_ar_dunning_letter_level ON ar_dunning_letter(letter_level);
CREATE INDEX idx_ar_dunning_letter_status ON ar_dunning_letter(letter_status);

CREATE INDEX idx_ar_dispute_account ON ar_dispute(account_id);
CREATE INDEX idx_ar_dispute_invoice ON ar_dispute(invoice_id);
CREATE INDEX idx_ar_dispute_status ON ar_dispute(dispute_status);
CREATE INDEX idx_ar_dispute_assigned ON ar_dispute(assigned_to);

CREATE INDEX idx_ar_write_off_account ON ar_write_off(account_id);
CREATE INDEX idx_ar_write_off_invoice ON ar_write_off(invoice_id);
CREATE INDEX idx_ar_write_off_approval ON ar_write_off(approval_status);

CREATE INDEX idx_ar_analytics_period ON ar_analytics(period_end DESC);
CREATE INDEX idx_ar_statistics_period ON ar_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_ar_account_updated_at BEFORE UPDATE ON ar_account
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ar_invoice_updated_at BEFORE UPDATE ON ar_invoice
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ar_payment_updated_at BEFORE UPDATE ON ar_payment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ar_credit_memo_updated_at BEFORE UPDATE ON ar_credit_memo
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ar_aging_bucket_updated_at BEFORE UPDATE ON ar_aging_bucket
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ar_collection_activity_updated_at BEFORE UPDATE ON ar_collection_activity
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ar_dunning_letter_updated_at BEFORE UPDATE ON ar_dunning_letter
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ar_dispute_updated_at BEFORE UPDATE ON ar_dispute
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();