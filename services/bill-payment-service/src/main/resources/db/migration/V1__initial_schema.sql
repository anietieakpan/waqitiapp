-- Bill Payment Service Initial Schema
-- Created: 2025-09-27
-- Description: Bill payment processing and payee management schema

CREATE TABLE IF NOT EXISTS bill_payee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payee_id VARCHAR(100) UNIQUE NOT NULL,
    payee_name VARCHAR(255) NOT NULL,
    payee_type VARCHAR(50) NOT NULL,
    business_category VARCHAR(100),
    merchant_id VARCHAR(100),
    tax_id VARCHAR(50),
    website_url VARCHAR(500),
    customer_service_phone VARCHAR(20),
    customer_service_email VARCHAR(255),
    billing_address JSONB,
    remittance_address JSONB,
    payment_methods TEXT[] NOT NULL,
    supported_currencies TEXT[] NOT NULL,
    processing_time_days INTEGER DEFAULT 3,
    cutoff_time TIME DEFAULT '18:00:00',
    weekend_processing BOOLEAN DEFAULT FALSE,
    holiday_processing BOOLEAN DEFAULT FALSE,
    minimum_payment_amount DECIMAL(18, 2) DEFAULT 0.01,
    maximum_payment_amount DECIMAL(18, 2),
    fee_structure JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    verified_at TIMESTAMP,
    logo_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bill_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    payee_id VARCHAR(100) NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    billing_zip_code VARCHAR(10),
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    nickname VARCHAR(100),
    auto_pay_enabled BOOLEAN DEFAULT FALSE,
    auto_pay_amount_type VARCHAR(20),
    auto_pay_amount DECIMAL(18, 2),
    auto_pay_date INTEGER,
    auto_pay_account_id VARCHAR(100),
    last_payment_date DATE,
    last_payment_amount DECIMAL(18, 2),
    current_balance DECIMAL(18, 2),
    due_date DATE,
    minimum_due DECIMAL(18, 2),
    statement_balance DECIMAL(18, 2),
    past_due_amount DECIMAL(18, 2),
    next_due_date DATE,
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    verification_attempts INTEGER DEFAULT 0,
    last_verification_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_account_payee FOREIGN KEY (payee_id) REFERENCES bill_payee(payee_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    payee_id VARCHAR(100) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_type VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_amount DECIMAL(18, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    payment_date DATE NOT NULL,
    scheduled_date DATE,
    delivery_date DATE,
    processing_date DATE,
    settlement_date DATE,
    memo VARCHAR(255),
    reference_number VARCHAR(100),
    confirmation_number VARCHAR(100),
    tracking_number VARCHAR(100),
    payment_method_details JSONB,
    fee_amount DECIMAL(18, 2) DEFAULT 0,
    delivery_method VARCHAR(50) NOT NULL,
    delivery_address JSONB,
    expedited BOOLEAN DEFAULT FALSE,
    recurring BOOLEAN DEFAULT FALSE,
    recurring_frequency VARCHAR(20),
    recurring_end_date DATE,
    next_payment_date DATE,
    auto_pay_enabled BOOLEAN DEFAULT FALSE,
    payment_source VARCHAR(100),
    external_reference VARCHAR(100),
    error_code VARCHAR(50),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_payment_account FOREIGN KEY (account_id) REFERENCES bill_account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_payment_payee FOREIGN KEY (payee_id) REFERENCES bill_payee(payee_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_payment_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    schedule_name VARCHAR(255) NOT NULL,
    schedule_type VARCHAR(50) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    payment_amount_type VARCHAR(20) NOT NULL,
    fixed_amount DECIMAL(18, 2),
    percentage_amount DECIMAL(5, 4),
    start_date DATE NOT NULL,
    end_date DATE,
    payment_day INTEGER,
    payment_time TIME DEFAULT '09:00:00',
    advance_days INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    next_payment_date DATE,
    last_payment_date DATE,
    payment_count INTEGER DEFAULT 0,
    max_payments INTEGER,
    auto_suspend_on_failure BOOLEAN DEFAULT FALSE,
    notification_enabled BOOLEAN DEFAULT TRUE,
    notification_days_before INTEGER DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_payment_schedule FOREIGN KEY (account_id) REFERENCES bill_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_statement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    payee_id VARCHAR(100) NOT NULL,
    statement_date DATE NOT NULL,
    due_date DATE NOT NULL,
    minimum_payment_due DECIMAL(18, 2) NOT NULL,
    statement_balance DECIMAL(18, 2) NOT NULL,
    previous_balance DECIMAL(18, 2) DEFAULT 0,
    payment_received DECIMAL(18, 2) DEFAULT 0,
    new_charges DECIMAL(18, 2) DEFAULT 0,
    interest_charges DECIMAL(18, 2) DEFAULT 0,
    fees DECIMAL(18, 2) DEFAULT 0,
    credits DECIMAL(18, 2) DEFAULT 0,
    past_due_amount DECIMAL(18, 2) DEFAULT 0,
    available_credit DECIMAL(18, 2),
    credit_limit DECIMAL(18, 2),
    annual_percentage_rate DECIMAL(5, 4),
    late_fee DECIMAL(18, 2) DEFAULT 0,
    statement_period_start DATE NOT NULL,
    statement_period_end DATE NOT NULL,
    payment_due_date DATE NOT NULL,
    autopay_scheduled BOOLEAN DEFAULT FALSE,
    autopay_amount DECIMAL(18, 2),
    ebill_available BOOLEAN DEFAULT FALSE,
    file_path VARCHAR(1000),
    file_size_bytes INTEGER,
    imported_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_statement_account FOREIGN KEY (account_id) REFERENCES bill_account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_statement_payee FOREIGN KEY (payee_id) REFERENCES bill_payee(payee_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_reminder (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    reminder_type VARCHAR(50) NOT NULL,
    reminder_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    due_date DATE NOT NULL,
    amount_due DECIMAL(18, 2) NOT NULL,
    days_before_due INTEGER NOT NULL,
    reminder_date DATE NOT NULL,
    delivery_method VARCHAR(50) NOT NULL,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    opened_at TIMESTAMP,
    clicked_at TIMESTAMP,
    message_content TEXT,
    delivery_status VARCHAR(20),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_reminder_account FOREIGN KEY (account_id) REFERENCES bill_account(account_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_payment_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    history_id VARCHAR(100) UNIQUE NOT NULL,
    payment_id VARCHAR(100) NOT NULL,
    status_change_from VARCHAR(20) NOT NULL,
    status_change_to VARCHAR(20) NOT NULL,
    change_reason VARCHAR(100),
    change_description TEXT,
    changed_by VARCHAR(100),
    system_reference VARCHAR(100),
    additional_data JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_payment_history FOREIGN KEY (payment_id) REFERENCES bill_payment(payment_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_payment_method (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    method_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    method_type VARCHAR(50) NOT NULL,
    method_name VARCHAR(255) NOT NULL,
    account_number_masked VARCHAR(50),
    routing_number VARCHAR(20),
    bank_name VARCHAR(255),
    account_type VARCHAR(50),
    expiry_date DATE,
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    verification_amount_1 DECIMAL(10, 2),
    verification_amount_2 DECIMAL(10, 2),
    verification_attempts INTEGER DEFAULT 0,
    last_used_at TIMESTAMP,
    usage_count INTEGER DEFAULT 0,
    billing_address JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bill_recurring_payment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recurring_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    payment_method_id VARCHAR(100) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    amount_type VARCHAR(20) NOT NULL,
    fixed_amount DECIMAL(18, 2),
    start_date DATE NOT NULL,
    end_date DATE,
    next_payment_date DATE NOT NULL,
    last_payment_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    payment_count INTEGER DEFAULT 0,
    max_payments INTEGER,
    total_amount_paid DECIMAL(18, 2) DEFAULT 0,
    failure_count INTEGER DEFAULT 0,
    max_failures INTEGER DEFAULT 3,
    auto_suspend_on_failure BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_recurring_payment_account FOREIGN KEY (account_id) REFERENCES bill_account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_recurring_payment_method FOREIGN KEY (payment_method_id) REFERENCES bill_payment_method(method_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_payment_fee (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fee_id VARCHAR(100) UNIQUE NOT NULL,
    payee_id VARCHAR(100) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    delivery_method VARCHAR(50) NOT NULL,
    fee_type VARCHAR(50) NOT NULL,
    fee_structure VARCHAR(20) NOT NULL,
    fixed_fee DECIMAL(18, 2) DEFAULT 0,
    percentage_fee DECIMAL(5, 4) DEFAULT 0,
    minimum_fee DECIMAL(18, 2) DEFAULT 0,
    maximum_fee DECIMAL(18, 2),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    expedited_fee DECIMAL(18, 2) DEFAULT 0,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_payment_fee FOREIGN KEY (payee_id) REFERENCES bill_payee(payee_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_payment_notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    payment_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    delivery_method VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    message_content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    opened_at TIMESTAMP,
    clicked_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_payment_notification FOREIGN KEY (payment_id) REFERENCES bill_payment(payment_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bill_payment_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_payments INTEGER DEFAULT 0,
    successful_payments INTEGER DEFAULT 0,
    failed_payments INTEGER DEFAULT 0,
    total_payment_volume DECIMAL(18, 2) DEFAULT 0,
    avg_payment_amount DECIMAL(18, 2),
    total_fees_collected DECIMAL(18, 2) DEFAULT 0,
    unique_customers INTEGER DEFAULT 0,
    unique_payees INTEGER DEFAULT 0,
    by_payment_method JSONB,
    by_delivery_method JSONB,
    by_payee_category JSONB,
    payment_success_rate DECIMAL(5, 4),
    avg_processing_time_hours DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bill_payment_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_payments BIGINT DEFAULT 0,
    payment_volume DECIMAL(18, 2) DEFAULT 0,
    unique_customers BIGINT DEFAULT 0,
    active_payees INTEGER DEFAULT 0,
    avg_payment_amount DECIMAL(18, 2),
    success_rate DECIMAL(5, 4),
    on_time_payment_rate DECIMAL(5, 4),
    autopay_adoption_rate DECIMAL(5, 4),
    by_payment_type JSONB,
    by_frequency JSONB,
    top_payee_categories JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_bill_payment_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_bill_payee_type ON bill_payee(payee_type);
CREATE INDEX idx_bill_payee_category ON bill_payee(business_category);
CREATE INDEX idx_bill_payee_active ON bill_payee(is_active) WHERE is_active = true;
CREATE INDEX idx_bill_payee_verification ON bill_payee(verification_status);

CREATE INDEX idx_bill_account_customer ON bill_account(customer_id);
CREATE INDEX idx_bill_account_payee ON bill_account(payee_id);
CREATE INDEX idx_bill_account_status ON bill_account(account_status);
CREATE INDEX idx_bill_account_autopay ON bill_account(auto_pay_enabled) WHERE auto_pay_enabled = true;
CREATE INDEX idx_bill_account_due_date ON bill_account(due_date);

CREATE INDEX idx_bill_payment_customer ON bill_payment(customer_id);
CREATE INDEX idx_bill_payment_account ON bill_payment(account_id);
CREATE INDEX idx_bill_payment_payee ON bill_payment(payee_id);
CREATE INDEX idx_bill_payment_status ON bill_payment(payment_status);
CREATE INDEX idx_bill_payment_date ON bill_payment(payment_date DESC);
CREATE INDEX idx_bill_payment_scheduled ON bill_payment(scheduled_date);
CREATE INDEX idx_bill_payment_recurring ON bill_payment(recurring) WHERE recurring = true;

CREATE INDEX idx_bill_payment_schedule_customer ON bill_payment_schedule(customer_id);
CREATE INDEX idx_bill_payment_schedule_account ON bill_payment_schedule(account_id);
CREATE INDEX idx_bill_payment_schedule_active ON bill_payment_schedule(is_active) WHERE is_active = true;
CREATE INDEX idx_bill_payment_schedule_next ON bill_payment_schedule(next_payment_date);

CREATE INDEX idx_bill_statement_account ON bill_statement(account_id);
CREATE INDEX idx_bill_statement_customer ON bill_statement(customer_id);
CREATE INDEX idx_bill_statement_payee ON bill_statement(payee_id);
CREATE INDEX idx_bill_statement_due_date ON bill_statement(due_date);
CREATE INDEX idx_bill_statement_date ON bill_statement(statement_date DESC);

CREATE INDEX idx_bill_reminder_customer ON bill_reminder(customer_id);
CREATE INDEX idx_bill_reminder_account ON bill_reminder(account_id);
CREATE INDEX idx_bill_reminder_status ON bill_reminder(reminder_status);
CREATE INDEX idx_bill_reminder_date ON bill_reminder(reminder_date);

CREATE INDEX idx_bill_payment_history_payment ON bill_payment_history(payment_id);
CREATE INDEX idx_bill_payment_history_timestamp ON bill_payment_history(timestamp DESC);

CREATE INDEX idx_bill_payment_method_customer ON bill_payment_method(customer_id);
CREATE INDEX idx_bill_payment_method_type ON bill_payment_method(method_type);
CREATE INDEX idx_bill_payment_method_primary ON bill_payment_method(is_primary) WHERE is_primary = true;
CREATE INDEX idx_bill_payment_method_active ON bill_payment_method(is_active) WHERE is_active = true;

CREATE INDEX idx_bill_recurring_payment_customer ON bill_recurring_payment(customer_id);
CREATE INDEX idx_bill_recurring_payment_account ON bill_recurring_payment(account_id);
CREATE INDEX idx_bill_recurring_payment_active ON bill_recurring_payment(is_active) WHERE is_active = true;
CREATE INDEX idx_bill_recurring_payment_next ON bill_recurring_payment(next_payment_date);

CREATE INDEX idx_bill_payment_fee_payee ON bill_payment_fee(payee_id);
CREATE INDEX idx_bill_payment_fee_method ON bill_payment_fee(payment_method);
CREATE INDEX idx_bill_payment_fee_active ON bill_payment_fee(is_active) WHERE is_active = true;

CREATE INDEX idx_bill_payment_notification_customer ON bill_payment_notification(customer_id);
CREATE INDEX idx_bill_payment_notification_payment ON bill_payment_notification(payment_id);
CREATE INDEX idx_bill_payment_notification_status ON bill_payment_notification(status);

CREATE INDEX idx_bill_payment_analytics_period ON bill_payment_analytics(period_end DESC);
CREATE INDEX idx_bill_payment_statistics_period ON bill_payment_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_bill_payee_updated_at BEFORE UPDATE ON bill_payee
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_account_updated_at BEFORE UPDATE ON bill_account
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_payment_updated_at BEFORE UPDATE ON bill_payment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_payment_schedule_updated_at BEFORE UPDATE ON bill_payment_schedule
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_payment_method_updated_at BEFORE UPDATE ON bill_payment_method
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_recurring_payment_updated_at BEFORE UPDATE ON bill_recurring_payment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_payment_fee_updated_at BEFORE UPDATE ON bill_payment_fee
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();