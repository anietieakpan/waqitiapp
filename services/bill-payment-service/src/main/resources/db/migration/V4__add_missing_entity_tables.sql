-- Migration V4: Add missing entity tables
-- Created: 2025-11-17
-- Description: Add tables for BillerConnection, AutoPayConfig, BillShareRequest, BillPaymentAuditLog entities

-- Table: billers (replacing bill_payee with proper mapping)
CREATE TABLE IF NOT EXISTS billers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    biller_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    logo_url VARCHAR(500),
    website_url VARCHAR(500),
    customer_service_phone VARCHAR(20),
    customer_service_email VARCHAR(255),
    supported_payment_methods TEXT[] NOT NULL DEFAULT '{}',
    supported_currencies TEXT[] NOT NULL DEFAULT '{"USD"}',
    minimum_payment_amount DECIMAL(19, 4) DEFAULT 0.01,
    maximum_payment_amount DECIMAL(19, 4),
    processing_time_days INTEGER DEFAULT 3,
    supports_direct_payment BOOLEAN DEFAULT FALSE,
    supports_negotiation BOOLEAN DEFAULT FALSE,
    api_endpoint VARCHAR(500),
    api_key_required BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    verification_status VARCHAR(20) DEFAULT 'VERIFIED',
    verified_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100)
);

-- Table: biller_connections
CREATE TABLE IF NOT EXISTS biller_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    biller_id UUID NOT NULL,
    account_number_encrypted TEXT NOT NULL,
    connection_id VARCHAR(200) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_synced_at TIMESTAMP,
    auto_import_enabled BOOLEAN DEFAULT TRUE,
    sync_frequency VARCHAR(20) DEFAULT 'DAILY',
    last_sync_status VARCHAR(20),
    last_sync_error TEXT,
    connection_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_biller_connection_biller FOREIGN KEY (biller_id) REFERENCES billers(id) ON DELETE CASCADE
);

-- Table: bills
CREATE TABLE IF NOT EXISTS bills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    biller_id UUID NOT NULL,
    biller_name VARCHAR(200) NOT NULL,
    account_number_encrypted TEXT NOT NULL,
    bill_number VARCHAR(100),
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    due_date DATE NOT NULL,
    bill_date DATE,
    issue_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    description TEXT,
    external_bill_id VARCHAR(100),
    pdf_url VARCHAR(500),
    is_recurring BOOLEAN DEFAULT FALSE,
    auto_pay_enabled BOOLEAN DEFAULT FALSE,
    minimum_amount_due DECIMAL(19, 4),
    late_fee DECIMAL(19, 4),
    paid_amount DECIMAL(19, 4),
    paid_date DATE,
    last_payment_id UUID,
    reminder_sent BOOLEAN DEFAULT FALSE,
    reminder_sent_at TIMESTAMP,
    overdue_alert_sent BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100),
    CONSTRAINT fk_bill_biller FOREIGN KEY (biller_id) REFERENCES billers(id) ON DELETE CASCADE
);

-- Table: bill_payments (matches BillPayment entity)
CREATE TABLE IF NOT EXISTS bill_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    bill_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(30) NOT NULL,
    wallet_transaction_id UUID,
    scheduled_date TIMESTAMP,
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP,
    biller_confirmation_number VARCHAR(100),
    external_payment_id VARCHAR(100),
    cashback_amount DECIMAL(19, 4),
    cashback_earned BOOLEAN DEFAULT FALSE,
    fee_amount DECIMAL(19, 4),
    payment_note TEXT,
    metadata JSONB,
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_bill_payment_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

-- Table: auto_pay_configs
CREATE TABLE IF NOT EXISTS auto_pay_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    bill_id UUID NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    max_amount DECIMAL(19, 4),
    amount_type VARCHAR(20) NOT NULL DEFAULT 'FULL_BALANCE',
    fixed_amount DECIMAL(19, 4),
    payment_timing VARCHAR(50) NOT NULL DEFAULT 'ON_DUE_DATE',
    days_before_due INTEGER DEFAULT 0,
    enable_smart_scheduling BOOLEAN DEFAULT FALSE,
    only_pay_exact_amount BOOLEAN DEFAULT FALSE,
    is_enabled BOOLEAN DEFAULT TRUE,
    last_payment_date TIMESTAMP,
    last_payment_id UUID,
    next_scheduled_date DATE,
    failure_count INTEGER DEFAULT 0,
    max_failures_before_disable INTEGER DEFAULT 3,
    notification_enabled BOOLEAN DEFAULT TRUE,
    notification_days_before INTEGER DEFAULT 3,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_auto_pay_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

-- Table: bill_reminders
CREATE TABLE IF NOT EXISTS bill_reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    bill_id UUID NOT NULL,
    reminder_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_date TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    delivery_method VARCHAR(50) NOT NULL DEFAULT 'EMAIL',
    delivery_status VARCHAR(20),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bill_reminder_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

-- Table: bill_share_requests
CREATE TABLE IF NOT EXISTS bill_share_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id UUID NOT NULL,
    organizer_user_id VARCHAR(100) NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    split_type VARCHAR(20) NOT NULL DEFAULT 'EQUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    due_date DATE,
    participants_count INTEGER DEFAULT 0,
    paid_participants_count INTEGER DEFAULT 0,
    total_collected DECIMAL(19, 4) DEFAULT 0,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    CONSTRAINT fk_bill_share_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

-- Table: bill_share_participants
CREATE TABLE IF NOT EXISTS bill_share_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    share_request_id UUID NOT NULL,
    participant_user_id VARCHAR(100) NOT NULL,
    participant_email VARCHAR(255),
    participant_name VARCHAR(255),
    owed_amount DECIMAL(19, 4) NOT NULL,
    paid_amount DECIMAL(19, 4) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    split_percentage DECIMAL(5, 2),
    invited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP,
    paid_at TIMESTAMP,
    payment_id UUID,
    payment_due_date DATE,
    reminders_sent INTEGER DEFAULT 0,
    last_reminder_sent_at TIMESTAMP,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_share_participant_request FOREIGN KEY (share_request_id) REFERENCES bill_share_requests(id) ON DELETE CASCADE
);

-- Table: bill_payment_audit_logs
CREATE TABLE IF NOT EXISTS bill_payment_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id VARCHAR(100),
    changes JSONB,
    previous_value JSONB,
    new_value JSONB,
    metadata JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance

-- Biller indexes
CREATE INDEX idx_billers_category ON billers(category);
CREATE INDEX idx_billers_status ON billers(status);
CREATE INDEX idx_billers_active ON billers(is_active) WHERE is_active = true;
CREATE INDEX idx_billers_name ON billers(name);

-- Biller connection indexes
CREATE INDEX idx_biller_connections_user ON biller_connections(user_id);
CREATE INDEX idx_biller_connections_biller ON biller_connections(biller_id);
CREATE INDEX idx_biller_connections_status ON biller_connections(status);
CREATE INDEX idx_biller_connections_user_status ON biller_connections(user_id, status);

-- Bill indexes
CREATE INDEX idx_bills_user ON bills(user_id);
CREATE INDEX idx_bills_biller ON bills(biller_id);
CREATE INDEX idx_bills_status ON bills(status);
CREATE INDEX idx_bills_due_date ON bills(due_date);
CREATE INDEX idx_bills_user_status ON bills(user_id, status);
CREATE INDEX idx_bills_user_due_date ON bills(user_id, due_date);
CREATE INDEX idx_bills_category ON bills(category);

-- Bill payment indexes
CREATE INDEX idx_bill_payments_user ON bill_payments(user_id);
CREATE INDEX idx_bill_payments_bill ON bill_payments(bill_id);
CREATE INDEX idx_bill_payments_status ON bill_payments(status);
CREATE INDEX idx_bill_payments_idempotency ON bill_payments(idempotency_key);
CREATE INDEX idx_bill_payments_scheduled ON bill_payments(scheduled_date) WHERE scheduled_date IS NOT NULL;
CREATE INDEX idx_bill_payments_retry ON bill_payments(next_retry_at) WHERE next_retry_at IS NOT NULL;
CREATE INDEX idx_bill_payments_user_status ON bill_payments(user_id, status);
CREATE INDEX idx_bill_payments_created ON bill_payments(created_at DESC);

-- Auto-pay indexes
CREATE INDEX idx_auto_pay_user ON auto_pay_configs(user_id);
CREATE INDEX idx_auto_pay_bill ON auto_pay_configs(bill_id);
CREATE INDEX idx_auto_pay_enabled ON auto_pay_configs(is_enabled) WHERE is_enabled = true;
CREATE INDEX idx_auto_pay_next_scheduled ON auto_pay_configs(next_scheduled_date) WHERE is_enabled = true;

-- Bill reminder indexes
CREATE INDEX idx_bill_reminders_user ON bill_reminders(user_id);
CREATE INDEX idx_bill_reminders_bill ON bill_reminders(bill_id);
CREATE INDEX idx_bill_reminders_status ON bill_reminders(status);
CREATE INDEX idx_bill_reminders_scheduled ON bill_reminders(scheduled_date);

-- Bill share indexes
CREATE INDEX idx_bill_shares_bill ON bill_share_requests(bill_id);
CREATE INDEX idx_bill_shares_organizer ON bill_share_requests(organizer_user_id);
CREATE INDEX idx_bill_shares_status ON bill_share_requests(status);
CREATE INDEX idx_bill_share_participants_request ON bill_share_participants(share_request_id);
CREATE INDEX idx_bill_share_participants_user ON bill_share_participants(participant_user_id);
CREATE INDEX idx_bill_share_participants_status ON bill_share_participants(status);

-- Audit log indexes
CREATE INDEX idx_audit_logs_entity ON bill_payment_audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_user ON bill_payment_audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON bill_payment_audit_logs(action);
CREATE INDEX idx_audit_logs_timestamp ON bill_payment_audit_logs(timestamp DESC);

-- Update timestamp triggers
CREATE TRIGGER update_billers_updated_at BEFORE UPDATE ON billers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_biller_connections_updated_at BEFORE UPDATE ON biller_connections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bills_updated_at BEFORE UPDATE ON bills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_payments_updated_at BEFORE UPDATE ON bill_payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auto_pay_configs_updated_at BEFORE UPDATE ON auto_pay_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_reminders_updated_at BEFORE UPDATE ON bill_reminders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_share_requests_updated_at BEFORE UPDATE ON bill_share_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_share_participants_updated_at BEFORE UPDATE ON bill_share_participants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Constraints and checks
ALTER TABLE bill_payments
    ADD CONSTRAINT check_amount_positive CHECK (amount > 0),
    ADD CONSTRAINT check_retry_count CHECK (retry_count >= 0),
    ADD CONSTRAINT check_retry_count_max CHECK (retry_count <= max_retries);

ALTER TABLE bills
    ADD CONSTRAINT check_bill_amount_positive CHECK (amount > 0);

ALTER TABLE auto_pay_configs
    ADD CONSTRAINT check_autopay_amount CHECK (max_amount IS NULL OR max_amount > 0),
    ADD CONSTRAINT check_autopay_fixed_amount CHECK (fixed_amount IS NULL OR fixed_amount > 0);

ALTER TABLE bill_share_requests
    ADD CONSTRAINT check_share_amount_positive CHECK (total_amount > 0);

ALTER TABLE bill_share_participants
    ADD CONSTRAINT check_participant_amount CHECK (owed_amount > 0),
    ADD CONSTRAINT check_participant_paid CHECK (paid_amount >= 0 AND paid_amount <= owed_amount);

-- Comments for documentation
COMMENT ON TABLE billers IS 'Biller organizations (utilities, service providers, etc.)';
COMMENT ON TABLE biller_connections IS 'User connections to external biller accounts for auto-import';
COMMENT ON TABLE bills IS 'Bill records for users to pay';
COMMENT ON TABLE bill_payments IS 'Payment transactions for bills';
COMMENT ON TABLE auto_pay_configs IS 'Auto-pay configurations for recurring bill payments';
COMMENT ON TABLE bill_reminders IS 'Bill payment reminders and notifications';
COMMENT ON TABLE bill_share_requests IS 'Bill splitting/sharing requests';
COMMENT ON TABLE bill_share_participants IS 'Participants in bill sharing';
COMMENT ON TABLE bill_payment_audit_logs IS 'Audit trail for all bill payment operations';

COMMENT ON COLUMN bill_payments.idempotency_key IS 'Prevents duplicate payment processing';
COMMENT ON COLUMN bill_payments.wallet_transaction_id IS 'Reference to wallet debit transaction';
COMMENT ON COLUMN bill_payments.retry_count IS 'Number of retry attempts for failed payments';
COMMENT ON COLUMN bills.account_number_encrypted IS 'Encrypted account number (PII)';
COMMENT ON COLUMN biller_connections.account_number_encrypted IS 'Encrypted biller account number (PII)';
