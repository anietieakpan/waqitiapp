-- Merged from billing-service on $(date)
-- Billing Service Initial Schema
-- Created: 2025-09-27
-- Description: Core billing and subscription management schema

CREATE TABLE IF NOT EXISTS billing_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id VARCHAR(100) UNIQUE NOT NULL,
    plan_name VARCHAR(255) NOT NULL,
    plan_type VARCHAR(50) NOT NULL,
    description TEXT,
    billing_frequency VARCHAR(20) NOT NULL,
    price DECIMAL(18, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    setup_fee DECIMAL(18, 2) DEFAULT 0,
    trial_period_days INTEGER DEFAULT 0,
    billing_cycle_anchor VARCHAR(20) DEFAULT 'SUBSCRIPTION_START',
    proration_behavior VARCHAR(50) DEFAULT 'CREATE_PRORATIONS',
    usage_type VARCHAR(20) DEFAULT 'LICENSED',
    aggregate_usage VARCHAR(20),
    billing_scheme VARCHAR(20) DEFAULT 'PER_UNIT',
    tiers JSONB,
    transform_usage JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    is_public BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_subscription (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    plan_id VARCHAR(100) NOT NULL,
    subscription_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    billing_cycle_anchor DATE,
    current_period_start DATE NOT NULL,
    current_period_end DATE NOT NULL,
    trial_start DATE,
    trial_end DATE,
    cancel_at_period_end BOOLEAN DEFAULT FALSE,
    canceled_at TIMESTAMP,
    cancellation_reason VARCHAR(100),
    ended_at TIMESTAMP,
    quantity INTEGER DEFAULT 1,
    discount_id VARCHAR(100),
    tax_percentage DECIMAL(5, 4) DEFAULT 0,
    collection_method VARCHAR(20) DEFAULT 'CHARGE_AUTOMATICALLY',
    days_until_due INTEGER,
    default_payment_method VARCHAR(100),
    default_source VARCHAR(100),
    latest_invoice_id VARCHAR(100),
    pending_setup_intent VARCHAR(100),
    pending_update JSONB,
    schedule_id VARCHAR(100),
    start_date DATE NOT NULL,
    application_fee_percent DECIMAL(5, 4),
    billing_thresholds JSONB,
    pause_collection JSONB,
    pending_invoice_item_interval JSONB,
    automatic_tax JSONB,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_subscription_plan FOREIGN KEY (plan_id) REFERENCES billing_plan(plan_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_invoice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    subscription_id VARCHAR(100),
    invoice_number VARCHAR(100) UNIQUE NOT NULL,
    invoice_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    billing_reason VARCHAR(50),
    collection_method VARCHAR(20) DEFAULT 'CHARGE_AUTOMATICALLY',
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal DECIMAL(18, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(18, 2) DEFAULT 0,
    discount_amount DECIMAL(18, 2) DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    amount_due DECIMAL(18, 2) NOT NULL DEFAULT 0,
    amount_paid DECIMAL(18, 2) DEFAULT 0,
    amount_remaining DECIMAL(18, 2) NOT NULL DEFAULT 0,
    application_fee_amount DECIMAL(18, 2) DEFAULT 0,
    attempt_count INTEGER DEFAULT 0,
    attempted BOOLEAN DEFAULT FALSE,
    auto_advance BOOLEAN DEFAULT TRUE,
    charge_id VARCHAR(100),
    created_at_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date DATE,
    ending_balance DECIMAL(18, 2),
    footer TEXT,
    hosted_invoice_url VARCHAR(1000),
    invoice_pdf VARCHAR(1000),
    last_finalization_error TEXT,
    lines_total_count INTEGER DEFAULT 0,
    next_payment_attempt TIMESTAMP,
    paid BOOLEAN DEFAULT FALSE,
    paid_at TIMESTAMP,
    payment_intent_id VARCHAR(100),
    period_end DATE,
    period_start DATE,
    post_payment_credit_notes_amount DECIMAL(18, 2) DEFAULT 0,
    pre_payment_credit_notes_amount DECIMAL(18, 2) DEFAULT 0,
    receipt_number VARCHAR(100),
    starting_balance DECIMAL(18, 2) DEFAULT 0,
    statement_descriptor VARCHAR(100),
    status_transitions JSONB,
    webhooks_delivered_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_invoice_subscription FOREIGN KEY (subscription_id) REFERENCES billing_subscription(subscription_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS billing_invoice_line_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    line_item_id VARCHAR(100) UNIQUE NOT NULL,
    invoice_id VARCHAR(100) NOT NULL,
    subscription_id VARCHAR(100),
    plan_id VARCHAR(100),
    line_type VARCHAR(20) NOT NULL DEFAULT 'SUBSCRIPTION',
    amount DECIMAL(18, 2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    discountable BOOLEAN DEFAULT TRUE,
    discounts JSONB,
    livemode BOOLEAN DEFAULT TRUE,
    period_start DATE,
    period_end DATE,
    price_id VARCHAR(100),
    proration BOOLEAN DEFAULT FALSE,
    proration_details JSONB,
    quantity INTEGER DEFAULT 1,
    subscription_item VARCHAR(100),
    tax_amounts JSONB,
    tax_rates JSONB,
    unit_amount DECIMAL(18, 2),
    unit_amount_excluding_tax DECIMAL(18, 2),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_invoice_line_item FOREIGN KEY (invoice_id) REFERENCES billing_invoice(invoice_id) ON DELETE CASCADE,
    CONSTRAINT fk_billing_invoice_line_item_subscription FOREIGN KEY (subscription_id) REFERENCES billing_subscription(subscription_id) ON DELETE SET NULL,
    CONSTRAINT fk_billing_invoice_line_item_plan FOREIGN KEY (plan_id) REFERENCES billing_plan(plan_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS billing_payment_method (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_method_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    payment_method_type VARCHAR(50) NOT NULL,
    billing_details JSONB,
    card JSONB,
    sepa_debit JSONB,
    acss_debit JSONB,
    afterpay_clearpay JSONB,
    alipay JSONB,
    au_becs_debit JSONB,
    bacs_debit JSONB,
    bancontact JSONB,
    boleto JSONB,
    eps JSONB,
    fpx JSONB,
    giropay JSONB,
    grabpay JSONB,
    ideal JSONB,
    interac_present JSONB,
    klarna JSONB,
    konbini JSONB,
    oxxo JSONB,
    p24 JSONB,
    sofort JSONB,
    wechat_pay JSONB,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    livemode BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_payment_intent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_intent_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    invoice_id VARCHAR(100),
    amount DECIMAL(18, 2) NOT NULL,
    amount_capturable DECIMAL(18, 2) DEFAULT 0,
    amount_received DECIMAL(18, 2) DEFAULT 0,
    application VARCHAR(100),
    application_fee_amount DECIMAL(18, 2),
    automatic_payment_methods JSONB,
    canceled_at TIMESTAMP,
    cancellation_reason VARCHAR(100),
    capture_method VARCHAR(20) DEFAULT 'AUTOMATIC',
    charges JSONB,
    client_secret VARCHAR(255),
    confirmation_method VARCHAR(20) DEFAULT 'AUTOMATIC',
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    last_payment_error JSONB,
    livemode BOOLEAN DEFAULT TRUE,
    next_action JSONB,
    on_behalf_of VARCHAR(100),
    payment_method VARCHAR(100),
    payment_method_options JSONB,
    payment_method_types TEXT[],
    processing JSONB,
    receipt_email VARCHAR(255),
    review VARCHAR(100),
    setup_future_usage VARCHAR(20),
    shipping JSONB,
    source VARCHAR(100),
    statement_descriptor VARCHAR(100),
    statement_descriptor_suffix VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'REQUIRES_PAYMENT_METHOD',
    transfer_data JSONB,
    transfer_group VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_payment_intent_invoice FOREIGN KEY (invoice_id) REFERENCES billing_invoice(invoice_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS billing_discount (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discount_id VARCHAR(100) UNIQUE NOT NULL,
    coupon_id VARCHAR(100) NOT NULL,
    customer_id UUID,
    subscription_id VARCHAR(100),
    invoice_id VARCHAR(100),
    checkout_session VARCHAR(100),
    end_timestamp TIMESTAMP,
    promotion_code VARCHAR(100),
    start_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_coupon (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id VARCHAR(100) UNIQUE NOT NULL,
    coupon_name VARCHAR(255) NOT NULL,
    amount_off DECIMAL(18, 2),
    currency_code VARCHAR(3),
    duration VARCHAR(20) NOT NULL,
    duration_in_months INTEGER,
    max_redemptions INTEGER,
    percent_off DECIMAL(5, 2),
    redeem_by TIMESTAMP,
    times_redeemed INTEGER DEFAULT 0,
    valid BOOLEAN DEFAULT TRUE,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    livemode BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_usage_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usage_record_id VARCHAR(100) UNIQUE NOT NULL,
    subscription_item_id VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action VARCHAR(20) DEFAULT 'INCREMENT',
    livemode BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_credit_note (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_note_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    invoice_id VARCHAR(100) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    customer_balance_transaction VARCHAR(100),
    discount_amount DECIMAL(18, 2) DEFAULT 0,
    discount_amounts JSONB,
    lines JSONB,
    livemode BOOLEAN DEFAULT TRUE,
    memo TEXT,
    metadata JSONB,
    credit_note_number VARCHAR(100) UNIQUE NOT NULL,
    out_of_band_amount DECIMAL(18, 2),
    pdf VARCHAR(1000),
    reason VARCHAR(50),
    refund_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    subtotal DECIMAL(18, 2) NOT NULL,
    subtotal_excluding_tax DECIMAL(18, 2),
    tax_amounts JSONB,
    total DECIMAL(18, 2) NOT NULL,
    total_excluding_tax DECIMAL(18, 2),
    voided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_billing_credit_note_invoice FOREIGN KEY (invoice_id) REFERENCES billing_invoice(invoice_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS billing_tax_rate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_rate_id VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    jurisdiction VARCHAR(100),
    percentage DECIMAL(5, 4) NOT NULL,
    inclusive BOOLEAN DEFAULT FALSE,
    active BOOLEAN DEFAULT TRUE,
    country VARCHAR(2),
    state VARCHAR(100),
    tax_type VARCHAR(50),
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    livemode BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_webhook_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(100) UNIQUE NOT NULL,
    api_version VARCHAR(20),
    created_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_data JSONB NOT NULL,
    livemode BOOLEAN DEFAULT TRUE,
    pending_webhooks INTEGER DEFAULT 0,
    request_id VARCHAR(100),
    event_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100),
    object_type VARCHAR(50),
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_revenue DECIMAL(18, 2) DEFAULT 0,
    recurring_revenue DECIMAL(18, 2) DEFAULT 0,
    one_time_revenue DECIMAL(18, 2) DEFAULT 0,
    total_subscriptions INTEGER DEFAULT 0,
    new_subscriptions INTEGER DEFAULT 0,
    canceled_subscriptions INTEGER DEFAULT 0,
    churned_subscriptions INTEGER DEFAULT 0,
    upgraded_subscriptions INTEGER DEFAULT 0,
    downgraded_subscriptions INTEGER DEFAULT 0,
    mrr DECIMAL(18, 2) DEFAULT 0,
    arr DECIMAL(18, 2) DEFAULT 0,
    churn_rate DECIMAL(5, 4) DEFAULT 0,
    ltv DECIMAL(18, 2) DEFAULT 0,
    arpu DECIMAL(18, 2) DEFAULT 0,
    by_plan JSONB,
    by_customer_segment JSONB,
    payment_success_rate DECIMAL(5, 4),
    dunning_success_rate DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billing_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_revenue DECIMAL(18, 2) DEFAULT 0,
    total_invoices INTEGER DEFAULT 0,
    paid_invoices INTEGER DEFAULT 0,
    active_subscriptions INTEGER DEFAULT 0,
    total_customers INTEGER DEFAULT 0,
    new_customers INTEGER DEFAULT 0,
    churned_customers INTEGER DEFAULT 0,
    avg_revenue_per_user DECIMAL(18, 2),
    payment_success_rate DECIMAL(5, 4),
    by_plan_type JSONB,
    by_billing_frequency JSONB,
    revenue_trends JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_billing_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_billing_plan_type ON billing_plan(plan_type);
CREATE INDEX idx_billing_plan_frequency ON billing_plan(billing_frequency);
CREATE INDEX idx_billing_plan_active ON billing_plan(is_active) WHERE is_active = true;
CREATE INDEX idx_billing_plan_public ON billing_plan(is_public) WHERE is_public = true;

CREATE INDEX idx_billing_subscription_customer ON billing_subscription(customer_id);
CREATE INDEX idx_billing_subscription_plan ON billing_subscription(plan_id);
CREATE INDEX idx_billing_subscription_status ON billing_subscription(subscription_status);
CREATE INDEX idx_billing_subscription_period_end ON billing_subscription(current_period_end);
CREATE INDEX idx_billing_subscription_trial_end ON billing_subscription(trial_end);

CREATE INDEX idx_billing_invoice_customer ON billing_invoice(customer_id);
CREATE INDEX idx_billing_invoice_subscription ON billing_invoice(subscription_id);
CREATE INDEX idx_billing_invoice_status ON billing_invoice(invoice_status);
CREATE INDEX idx_billing_invoice_due_date ON billing_invoice(due_date);
CREATE INDEX idx_billing_invoice_created ON billing_invoice(created_at_timestamp DESC);

CREATE INDEX idx_billing_invoice_line_item_invoice ON billing_invoice_line_item(invoice_id);
CREATE INDEX idx_billing_invoice_line_item_subscription ON billing_invoice_line_item(subscription_id);
CREATE INDEX idx_billing_invoice_line_item_plan ON billing_invoice_line_item(plan_id);

CREATE INDEX idx_billing_payment_method_customer ON billing_payment_method(customer_id);
CREATE INDEX idx_billing_payment_method_type ON billing_payment_method(payment_method_type);

CREATE INDEX idx_billing_payment_intent_customer ON billing_payment_intent(customer_id);
CREATE INDEX idx_billing_payment_intent_invoice ON billing_payment_intent(invoice_id);
CREATE INDEX idx_billing_payment_intent_status ON billing_payment_intent(status);

CREATE INDEX idx_billing_discount_customer ON billing_discount(customer_id);
CREATE INDEX idx_billing_discount_subscription ON billing_discount(subscription_id);
CREATE INDEX idx_billing_discount_coupon ON billing_discount(coupon_id);

CREATE INDEX idx_billing_coupon_valid ON billing_coupon(valid) WHERE valid = true;
CREATE INDEX idx_billing_coupon_redeem_by ON billing_coupon(redeem_by);

CREATE INDEX idx_billing_usage_record_subscription_item ON billing_usage_record(subscription_item_id);
CREATE INDEX idx_billing_usage_record_timestamp ON billing_usage_record(timestamp DESC);

CREATE INDEX idx_billing_credit_note_customer ON billing_credit_note(customer_id);
CREATE INDEX idx_billing_credit_note_invoice ON billing_credit_note(invoice_id);
CREATE INDEX idx_billing_credit_note_status ON billing_credit_note(status);

CREATE INDEX idx_billing_tax_rate_active ON billing_tax_rate(active) WHERE active = true;
CREATE INDEX idx_billing_tax_rate_country ON billing_tax_rate(country);
CREATE INDEX idx_billing_tax_rate_jurisdiction ON billing_tax_rate(jurisdiction);

CREATE INDEX idx_billing_webhook_event_type ON billing_webhook_event(event_type);
CREATE INDEX idx_billing_webhook_event_processed ON billing_webhook_event(processed) WHERE processed = false;
CREATE INDEX idx_billing_webhook_event_created ON billing_webhook_event(created_timestamp DESC);

CREATE INDEX idx_billing_analytics_period ON billing_analytics(period_end DESC);
CREATE INDEX idx_billing_statistics_period ON billing_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_billing_plan_updated_at BEFORE UPDATE ON billing_plan
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_subscription_updated_at BEFORE UPDATE ON billing_subscription
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_invoice_updated_at BEFORE UPDATE ON billing_invoice
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_payment_intent_updated_at BEFORE UPDATE ON billing_payment_intent
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_coupon_updated_at BEFORE UPDATE ON billing_coupon
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_billing_tax_rate_updated_at BEFORE UPDATE ON billing_tax_rate
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();