-- Bill Payment Service - Initial Database Schema
-- PostgreSQL 15+
-- Created for production-ready bill payment system

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- For text search optimization

-- ============================================================
-- 1. BILLERS TABLE
-- ============================================================
CREATE TABLE billers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    display_name VARCHAR(200),
    logo_url VARCHAR(500),
    category VARCHAR(50) NOT NULL,
    description TEXT,
    website_url VARCHAR(500),
    customer_service_phone VARCHAR(20),
    customer_service_email VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    external_biller_id VARCHAR(100) UNIQUE,
    supports_auto_pay BOOLEAN NOT NULL DEFAULT FALSE,
    supports_direct_payment BOOLEAN NOT NULL DEFAULT FALSE,
    supports_bill_import BOOLEAN NOT NULL DEFAULT FALSE,
    supports_ebill BOOLEAN NOT NULL DEFAULT FALSE,
    average_processing_time_hours INTEGER,
    processing_cutoff_time VARCHAR(10),
    payment_fee_percentage DECIMAL(5, 4),
    payment_fee_fixed DECIMAL(19, 4),
    minimum_payment_amount DECIMAL(19, 4),
    maximum_payment_amount DECIMAL(19, 4),
    country_code VARCHAR(3) DEFAULT 'USA',
    state_code VARCHAR(10),
    city VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_biller_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'UNDER_REVIEW', 'TESTING'))
);

-- Billers indexes
CREATE INDEX idx_biller_name ON billers(name);
CREATE INDEX idx_biller_category ON billers(category);
CREATE INDEX idx_biller_status ON billers(status);
CREATE INDEX idx_biller_external_id ON billers(external_biller_id);
CREATE INDEX idx_biller_country_state ON billers(country_code, state_code);
CREATE INDEX idx_biller_name_trgm ON billers USING gin(name gin_trgm_ops);

-- ============================================================
-- 2. BILLS TABLE
-- ============================================================
CREATE TABLE bills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL,
    biller_id UUID NOT NULL REFERENCES billers(id),
    biller_name VARCHAR(200) NOT NULL,
    account_number VARCHAR(100) NOT NULL,
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
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    auto_pay_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_amount_due DECIMAL(19, 4),
    late_fee DECIMAL(19, 4),
    paid_amount DECIMAL(19, 4),
    paid_date DATE,
    last_payment_id UUID,
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_sent_at TIMESTAMP,
    overdue_alert_sent BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255),
    CONSTRAINT chk_bill_status CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'SCHEDULED', 'PROCESSING', 'FAILED', 'DISPUTED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT chk_bill_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_paid_amount_valid CHECK (paid_amount IS NULL OR paid_amount >= 0)
);

-- Bills indexes
CREATE INDEX idx_bill_user_id ON bills(user_id);
CREATE INDEX idx_bill_biller_id ON bills(biller_id);
CREATE INDEX idx_bill_account_number ON bills(account_number);
CREATE INDEX idx_bill_due_date ON bills(due_date);
CREATE INDEX idx_bill_status ON bills(status);
CREATE INDEX idx_bill_user_status ON bills(user_id, status);
CREATE INDEX idx_bill_user_category ON bills(user_id, category);
CREATE INDEX idx_bill_external_id ON bills(external_bill_id);
CREATE INDEX idx_bill_recurring ON bills(is_recurring) WHERE is_recurring = true;
CREATE INDEX idx_bill_autopay ON bills(auto_pay_enabled) WHERE auto_pay_enabled = true;
CREATE INDEX idx_bill_overdue ON bills(status) WHERE status = 'OVERDUE';

-- ============================================================
-- 3. BILLER_CONNECTIONS TABLE
-- ============================================================
CREATE TABLE biller_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL,
    biller_id UUID NOT NULL REFERENCES billers(id),
    account_number VARCHAR(100) NOT NULL,
    account_holder_name VARCHAR(200),
    nickname VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP,
    auto_import_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_import_at TIMESTAMP,
    last_import_status VARCHAR(50),
    next_import_at TIMESTAMP,
    import_frequency_days INTEGER DEFAULT 7,
    connection_failed_count INTEGER DEFAULT 0,
    last_connection_error VARCHAR(500),
    credentials_encrypted TEXT,
    external_connection_id VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255),
    CONSTRAINT uk_user_biller_account UNIQUE (user_id, biller_id, account_number),
    CONSTRAINT chk_connection_status CHECK (status IN ('ACTIVE', 'PENDING_VERIFICATION', 'REAUTH_REQUIRED', 'SUSPENDED', 'DISCONNECTED', 'ERROR'))
);

-- Biller connections indexes
CREATE INDEX idx_connection_user_id ON biller_connections(user_id);
CREATE INDEX idx_connection_biller_id ON biller_connections(biller_id);
CREATE INDEX idx_connection_status ON biller_connections(status);
CREATE INDEX idx_connection_user_biller ON biller_connections(user_id, biller_id);
CREATE INDEX idx_connection_auto_import ON biller_connections(auto_import_enabled, next_import_at)
    WHERE auto_import_enabled = true AND status = 'ACTIVE';

-- ============================================================
-- 4. BILL_PAYMENTS TABLE
-- ============================================================
CREATE TABLE bill_payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL,
    bill_id UUID NOT NULL REFERENCES bills(id),
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
    cashback_earned BOOLEAN NOT NULL DEFAULT FALSE,
    fee_amount DECIMAL(19, 4),
    payment_note TEXT,
    metadata JSONB,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'SCHEDULED', 'PROCESSING', 'COMPLETED', 'FAILED', 'REJECTED', 'CANCELLED', 'REFUNDED', 'UNDER_REVIEW')),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('WALLET', 'BANK_ACCOUNT', 'DEBIT_CARD', 'CREDIT_CARD', 'CHECK', 'CASH')),
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0 AND retry_count <= max_retries)
);

-- Bill payments indexes
CREATE INDEX idx_payment_user_id ON bill_payments(user_id);
CREATE INDEX idx_payment_bill_id ON bill_payments(bill_id);
CREATE INDEX idx_payment_status ON bill_payments(status);
CREATE INDEX idx_payment_scheduled_date ON bill_payments(scheduled_date);
CREATE INDEX idx_payment_user_status ON bill_payments(user_id, status);
CREATE INDEX idx_payment_external_id ON bill_payments(external_payment_id);
CREATE INDEX idx_payment_idempotency_key ON bill_payments(idempotency_key);
CREATE INDEX idx_payment_retry ON bill_payments(status, retry_count, next_retry_at)
    WHERE status = 'FAILED' AND retry_count < max_retries;

-- ============================================================
-- 5. AUTO_PAY_CONFIGS TABLE
-- ============================================================
CREATE TABLE auto_pay_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL,
    bill_id UUID REFERENCES bills(id),
    biller_id UUID NOT NULL REFERENCES billers(id),
    biller_connection_id UUID NOT NULL REFERENCES biller_connections(id),
    payment_method VARCHAR(30) NOT NULL,
    payment_amount_type VARCHAR(30) NOT NULL,
    fixed_amount DECIMAL(19, 4),
    days_before_due_date INTEGER DEFAULT 3,
    minimum_amount_threshold DECIMAL(19, 4),
    maximum_amount_threshold DECIMAL(19, 4),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    last_payment_id UUID,
    last_payment_date TIMESTAMP,
    last_payment_amount DECIMAL(19, 4),
    last_payment_status VARCHAR(30),
    next_payment_date TIMESTAMP,
    failure_count INTEGER DEFAULT 0,
    last_failure_reason VARCHAR(500),
    notify_before_payment BOOLEAN NOT NULL DEFAULT TRUE,
    notification_hours_before INTEGER DEFAULT 24,
    suspend_on_failure BOOLEAN NOT NULL DEFAULT TRUE,
    max_failure_count INTEGER DEFAULT 3,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(255),
    CONSTRAINT uk_user_bill_autopay UNIQUE (user_id, bill_id),
    CONSTRAINT chk_autopay_status CHECK (status IN ('ACTIVE', 'PAUSED', 'SUSPENDED', 'CANCELLED', 'PENDING')),
    CONSTRAINT chk_autopay_amount_type CHECK (payment_amount_type IN ('FULL_AMOUNT', 'MINIMUM_DUE', 'FIXED_AMOUNT')),
    CONSTRAINT chk_autopay_payment_method CHECK (payment_method IN ('WALLET', 'BANK_ACCOUNT', 'DEBIT_CARD', 'CREDIT_CARD', 'CHECK', 'CASH'))
);

-- Auto-pay configs indexes
CREATE INDEX idx_autopay_user_id ON auto_pay_configs(user_id);
CREATE INDEX idx_autopay_bill_id ON auto_pay_configs(bill_id);
CREATE INDEX idx_autopay_biller_id ON auto_pay_configs(biller_id);
CREATE INDEX idx_autopay_status ON auto_pay_configs(status);
CREATE INDEX idx_autopay_next_payment ON auto_pay_configs(next_payment_date)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- ============================================================
-- 6. BILL_REMINDERS TABLE
-- ============================================================
CREATE TABLE bill_reminders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(255) NOT NULL,
    bill_id UUID NOT NULL REFERENCES bills(id),
    reminder_type VARCHAR(30) NOT NULL,
    scheduled_send_time TIMESTAMP NOT NULL,
    actual_send_time TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    notification_channel VARCHAR(30),
    notification_id UUID,
    message_content TEXT,
    retry_count INTEGER DEFAULT 0,
    failure_reason VARCHAR(500),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reminder_type CHECK (reminder_type IN ('DUE_IN_7_DAYS', 'DUE_IN_3_DAYS', 'DUE_TOMORROW', 'DUE_TODAY', 'OVERDUE', 'CUSTOM')),
    CONSTRAINT chk_reminder_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED'))
);

-- Bill reminders indexes
CREATE INDEX idx_reminder_user_id ON bill_reminders(user_id);
CREATE INDEX idx_reminder_bill_id ON bill_reminders(bill_id);
CREATE INDEX idx_reminder_scheduled ON bill_reminders(scheduled_send_time);
CREATE INDEX idx_reminder_status ON bill_reminders(status);
CREATE INDEX idx_reminder_pending ON bill_reminders(status, scheduled_send_time)
    WHERE status = 'PENDING';

-- ============================================================
-- 7. BILL_SHARE_REQUESTS TABLE
-- ============================================================
CREATE TABLE bill_share_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    bill_id UUID NOT NULL REFERENCES bills(id),
    creator_user_id VARCHAR(255) NOT NULL,
    participant_user_id VARCHAR(255) NOT NULL,
    share_amount DECIMAL(19, 4) NOT NULL,
    share_percentage DECIMAL(5, 2),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    invitation_message TEXT,
    accepted_at TIMESTAMP,
    rejected_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    paid_at TIMESTAMP,
    payment_id UUID,
    reminder_count INTEGER DEFAULT 0,
    last_reminder_sent_at TIMESTAMP,
    expires_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_share_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'PAID', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_share_amount_positive CHECK (share_amount > 0)
);

-- Bill share requests indexes
CREATE INDEX idx_share_bill_id ON bill_share_requests(bill_id);
CREATE INDEX idx_share_creator ON bill_share_requests(creator_user_id);
CREATE INDEX idx_share_participant ON bill_share_requests(participant_user_id);
CREATE INDEX idx_share_status ON bill_share_requests(status);
CREATE INDEX idx_share_pending_expired ON bill_share_requests(status, expires_at)
    WHERE status = 'PENDING';

-- ============================================================
-- 8. BILL_PAYMENT_AUDIT_LOGS TABLE
-- ============================================================
CREATE TABLE bill_payment_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id VARCHAR(255),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    previous_state JSONB,
    new_state JSONB,
    changes JSONB,
    reason VARCHAR(500),
    metadata JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(100),
    session_id VARCHAR(100)
);

-- Audit logs indexes
CREATE INDEX idx_audit_entity_type ON bill_payment_audit_logs(entity_type);
CREATE INDEX idx_audit_entity_id ON bill_payment_audit_logs(entity_id);
CREATE INDEX idx_audit_user_id ON bill_payment_audit_logs(user_id);
CREATE INDEX idx_audit_action ON bill_payment_audit_logs(action);
CREATE INDEX idx_audit_timestamp ON bill_payment_audit_logs(timestamp);
CREATE INDEX idx_audit_entity_action ON bill_payment_audit_logs(entity_type, entity_id, action);
CREATE INDEX idx_audit_correlation_id ON bill_payment_audit_logs(correlation_id);

-- ============================================================
-- TRIGGERS FOR UPDATED_AT TIMESTAMPS
-- ============================================================

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to all tables with updated_at
CREATE TRIGGER update_billers_updated_at BEFORE UPDATE ON billers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bills_updated_at BEFORE UPDATE ON bills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_biller_connections_updated_at BEFORE UPDATE ON biller_connections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_payments_updated_at BEFORE UPDATE ON bill_payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auto_pay_configs_updated_at BEFORE UPDATE ON auto_pay_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_reminders_updated_at BEFORE UPDATE ON bill_reminders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bill_share_requests_updated_at BEFORE UPDATE ON bill_share_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- MATERIALIZED VIEWS FOR ANALYTICS
-- ============================================================

-- View: User bill statistics
CREATE MATERIALIZED VIEW user_bill_statistics AS
SELECT
    user_id,
    COUNT(*) as total_bills,
    COUNT(*) FILTER (WHERE status = 'UNPAID') as unpaid_count,
    COUNT(*) FILTER (WHERE status = 'PAID') as paid_count,
    COUNT(*) FILTER (WHERE status = 'OVERDUE') as overdue_count,
    SUM(amount) FILTER (WHERE status = 'UNPAID') as total_unpaid_amount,
    SUM(amount) FILTER (WHERE status = 'PAID') as total_paid_amount,
    AVG(amount) as average_bill_amount,
    MAX(due_date) FILTER (WHERE status = 'UNPAID') as next_due_date
FROM bills
WHERE deleted_at IS NULL
GROUP BY user_id;

CREATE UNIQUE INDEX idx_user_bill_stats ON user_bill_statistics(user_id);

-- View: Biller performance statistics
CREATE MATERIALIZED VIEW biller_performance_statistics AS
SELECT
    b.id as biller_id,
    b.name as biller_name,
    COUNT(DISTINCT bc.id) as total_connections,
    COUNT(DISTINCT bills.id) as total_bills,
    COUNT(DISTINCT bp.id) as total_payments,
    COUNT(DISTINCT bp.id) FILTER (WHERE bp.status = 'COMPLETED') as successful_payments,
    COUNT(DISTINCT bp.id) FILTER (WHERE bp.status = 'FAILED') as failed_payments,
    AVG(bp.amount) FILTER (WHERE bp.status = 'COMPLETED') as avg_payment_amount,
    SUM(bp.amount) FILTER (WHERE bp.status = 'COMPLETED') as total_payment_volume
FROM billers b
LEFT JOIN biller_connections bc ON b.id = bc.biller_id
LEFT JOIN bills ON b.id = bills.biller_id
LEFT JOIN bill_payments bp ON bills.id = bp.bill_id
WHERE b.deleted_at IS NULL
GROUP BY b.id, b.name;

CREATE UNIQUE INDEX idx_biller_perf_stats ON biller_performance_statistics(biller_id);

-- ============================================================
-- INITIAL DATA SEED (COMMON BILLERS)
-- ============================================================

-- Insert sample major US utility billers
INSERT INTO billers (name, display_name, category, status, supports_direct_payment, supports_bill_import, country_code) VALUES
('PG&E', 'Pacific Gas and Electric', 'UTILITIES_ELECTRICITY', 'ACTIVE', true, true, 'USA'),
('Southern California Edison', 'SCE', 'UTILITIES_ELECTRICITY', 'ACTIVE', true, true, 'USA'),
('ConEdison', 'Con Edison', 'UTILITIES_ELECTRICITY', 'ACTIVE', true, true, 'USA'),
('AT&T', 'AT&T', 'TELECOM_MOBILE', 'ACTIVE', true, true, 'USA'),
('Verizon', 'Verizon Wireless', 'TELECOM_MOBILE', 'ACTIVE', true, true, 'USA'),
('T-Mobile', 'T-Mobile', 'TELECOM_MOBILE', 'ACTIVE', true, true, 'USA'),
('Comcast', 'Comcast Xfinity', 'TELECOM_INTERNET', 'ACTIVE', true, true, 'USA'),
('Spectrum', 'Spectrum', 'TELECOM_INTERNET', 'ACTIVE', true, true, 'USA');

-- ============================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================

COMMENT ON TABLE billers IS 'External billers (utility companies, service providers) that issue bills';
COMMENT ON TABLE bills IS 'Bills from external billers that users can pay';
COMMENT ON TABLE biller_connections IS 'User connections to external biller accounts for bill import';
COMMENT ON TABLE bill_payments IS 'Payment transactions for bills';
COMMENT ON TABLE auto_pay_configs IS 'Automatic payment configurations for recurring bills';
COMMENT ON TABLE bill_reminders IS 'Reminders sent to users about upcoming/overdue bills';
COMMENT ON TABLE bill_share_requests IS 'Requests to split bills between multiple users';
COMMENT ON TABLE bill_payment_audit_logs IS 'Comprehensive audit trail for all bill payment operations';

-- ============================================================
-- GRANT PERMISSIONS (Adjust based on your user setup)
-- ============================================================

-- Grant permissions to application user (adjust username as needed)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO bill_payment_app_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bill_payment_app_user;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO bill_payment_app_user;

-- ============================================================
-- END OF MIGRATION
-- ============================================================
