-- Bank Integration Service Initial Schema
-- Created: 2025-09-27
-- Description: Banking partner integration and connectivity schema

CREATE TABLE IF NOT EXISTS bank_partner (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id VARCHAR(100) UNIQUE NOT NULL,
    partner_name VARCHAR(255) NOT NULL,
    partner_type VARCHAR(50) NOT NULL,
    swift_code VARCHAR(11),
    routing_number VARCHAR(20),
    country_code VARCHAR(3) NOT NULL,
    currency_codes TEXT[] NOT NULL,
    connection_type VARCHAR(50) NOT NULL,
    api_version VARCHAR(20),
    api_base_url VARCHAR(500),
    test_mode BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    supported_services TEXT[] NOT NULL,
    rate_limits JSONB,
    sla_metrics JSONB,
    contact_information JSONB,
    certification_details JSONB,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bank_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    connection_name VARCHAR(255) NOT NULL,
    connection_type VARCHAR(50) NOT NULL,
    connection_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    endpoint_url VARCHAR(500) NOT NULL,
    timeout_seconds INTEGER DEFAULT 30,
    retry_attempts INTEGER DEFAULT 3,
    circuit_breaker_enabled BOOLEAN DEFAULT TRUE,
    authentication_type VARCHAR(50) NOT NULL,
    credentials_config JSONB NOT NULL,
    tls_version VARCHAR(10) DEFAULT '1.3',
    certificate_path VARCHAR(500),
    last_health_check TIMESTAMP,
    health_check_status VARCHAR(20),
    consecutive_failures INTEGER DEFAULT 0,
    last_successful_call TIMESTAMP,
    total_requests INTEGER DEFAULT 0,
    successful_requests INTEGER DEFAULT 0,
    failed_requests INTEGER DEFAULT 0,
    avg_response_time_ms DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_connection FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    internal_reference VARCHAR(100) NOT NULL,
    external_reference VARCHAR(100),
    bank_reference VARCHAR(100),
    currency_code VARCHAR(3) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    fee_amount DECIMAL(18, 2) DEFAULT 0,
    net_amount DECIMAL(18, 2) NOT NULL,
    value_date DATE,
    settlement_date DATE,
    originator_account VARCHAR(100),
    beneficiary_account VARCHAR(100),
    transaction_details JSONB,
    routing_information JSONB,
    compliance_data JSONB,
    processing_time_ms INTEGER,
    error_code VARCHAR(20),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    idempotency_key VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_transaction_partner FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_transaction_connection FOREIGN KEY (connection_id) REFERENCES bank_connection(connection_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    balance DECIMAL(18, 2) DEFAULT 0,
    available_balance DECIMAL(18, 2) DEFAULT 0,
    overdraft_limit DECIMAL(18, 2) DEFAULT 0,
    interest_rate DECIMAL(5, 4) DEFAULT 0,
    minimum_balance DECIMAL(18, 2) DEFAULT 0,
    maximum_balance DECIMAL(18, 2),
    daily_limit DECIMAL(18, 2),
    monthly_limit DECIMAL(18, 2),
    account_holder_name VARCHAR(255) NOT NULL,
    account_holder_type VARCHAR(50) NOT NULL,
    branch_code VARCHAR(20),
    branch_name VARCHAR(255),
    iban VARCHAR(34),
    sort_code VARCHAR(10),
    last_statement_date DATE,
    next_statement_date DATE,
    is_reconciled BOOLEAN DEFAULT FALSE,
    last_reconciliation_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_account FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_statement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    statement_date DATE NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    opening_balance DECIMAL(18, 2) NOT NULL,
    closing_balance DECIMAL(18, 2) NOT NULL,
    total_credits DECIMAL(18, 2) DEFAULT 0,
    total_debits DECIMAL(18, 2) DEFAULT 0,
    transaction_count INTEGER DEFAULT 0,
    statement_format VARCHAR(20) NOT NULL,
    file_path VARCHAR(1000),
    file_size_bytes INTEGER,
    checksum VARCHAR(64),
    imported_at TIMESTAMP,
    processed_at TIMESTAMP,
    reconciliation_status VARCHAR(20) DEFAULT 'PENDING',
    reconciled_at TIMESTAMP,
    reconciled_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_statement_account FOREIGN KEY (account_id) REFERENCES bank_account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_statement_partner FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_reconciliation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id VARCHAR(100) UNIQUE NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    statement_id VARCHAR(100) NOT NULL,
    reconciliation_date DATE NOT NULL,
    reconciliation_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    book_balance DECIMAL(18, 2) NOT NULL,
    bank_balance DECIMAL(18, 2) NOT NULL,
    difference_amount DECIMAL(18, 2) DEFAULT 0,
    matched_transactions INTEGER DEFAULT 0,
    unmatched_book_items INTEGER DEFAULT 0,
    unmatched_bank_items INTEGER DEFAULT 0,
    reconciliation_items JSONB,
    adjustments JSONB,
    outstanding_items JSONB,
    reconciled_by VARCHAR(100) NOT NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_reconciliation_account FOREIGN KEY (account_id) REFERENCES bank_account(account_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_reconciliation_statement FOREIGN KEY (statement_id) REFERENCES bank_statement(statement_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_webhook (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    webhook_type VARCHAR(50) NOT NULL,
    event_types TEXT[] NOT NULL,
    endpoint_url VARCHAR(1000) NOT NULL,
    secret_key_encrypted VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    verification_token VARCHAR(255),
    signature_method VARCHAR(50) DEFAULT 'HMAC-SHA256',
    retry_policy JSONB,
    last_triggered_at TIMESTAMP,
    total_events INTEGER DEFAULT 0,
    successful_deliveries INTEGER DEFAULT 0,
    failed_deliveries INTEGER DEFAULT 0,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_webhook FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_api_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100) NOT NULL,
    request_type VARCHAR(50) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    endpoint_path VARCHAR(500) NOT NULL,
    request_headers JSONB,
    request_body JSONB,
    response_status INTEGER,
    response_headers JSONB,
    response_body JSONB,
    processing_time_ms INTEGER,
    request_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_timestamp TIMESTAMP,
    idempotency_key VARCHAR(100),
    correlation_id VARCHAR(100),
    error_code VARCHAR(20),
    error_message TEXT,
    retry_attempt INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_api_request_partner FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_api_request_connection FOREIGN KEY (connection_id) REFERENCES bank_connection(connection_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_rate_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100) NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_duration_seconds INTEGER NOT NULL,
    request_count INTEGER DEFAULT 0,
    limit_exceeded BOOLEAN DEFAULT FALSE,
    reset_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_rate_limit_partner FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_rate_limit_connection FOREIGN KEY (connection_id) REFERENCES bank_connection(connection_id) ON DELETE CASCADE,
    CONSTRAINT unique_partner_connection_window UNIQUE (partner_id, connection_id, window_start)
);

CREATE TABLE IF NOT EXISTS bank_error_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    error_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    connection_id VARCHAR(100),
    transaction_id VARCHAR(100),
    error_type VARCHAR(50) NOT NULL,
    error_code VARCHAR(20),
    error_message TEXT NOT NULL,
    error_details JSONB,
    severity VARCHAR(20) NOT NULL,
    retry_eligible BOOLEAN DEFAULT TRUE,
    resolution_status VARCHAR(20) DEFAULT 'OPEN',
    resolution_notes TEXT,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_error_log_partner FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    config_name VARCHAR(255) NOT NULL,
    config_value JSONB NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    is_active BOOLEAN DEFAULT TRUE,
    is_encrypted BOOLEAN DEFAULT FALSE,
    description TEXT,
    last_modified_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_configuration FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_compliance_check (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id VARCHAR(100) UNIQUE NOT NULL,
    transaction_id VARCHAR(100) NOT NULL,
    partner_id VARCHAR(100) NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    check_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    risk_score INTEGER DEFAULT 0,
    compliance_rules TEXT[],
    violations JSONB,
    sanctions_screening BOOLEAN DEFAULT FALSE,
    aml_status VARCHAR(20),
    fraud_indicators JSONB,
    regulatory_flags TEXT[],
    review_required BOOLEAN DEFAULT FALSE,
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    approval_status VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bank_compliance_check FOREIGN KEY (transaction_id) REFERENCES bank_transaction(transaction_id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_compliance_check_partner FOREIGN KEY (partner_id) REFERENCES bank_partner(partner_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bank_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    partner_id VARCHAR(100),
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_transactions INTEGER DEFAULT 0,
    successful_transactions INTEGER DEFAULT 0,
    failed_transactions INTEGER DEFAULT 0,
    total_volume DECIMAL(18, 2) DEFAULT 0,
    avg_transaction_amount DECIMAL(18, 2),
    avg_processing_time_ms DECIMAL(10, 2),
    uptime_percentage DECIMAL(5, 4),
    error_rate DECIMAL(5, 4),
    by_transaction_type JSONB,
    by_status JSONB,
    performance_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bank_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_partners INTEGER DEFAULT 0,
    active_connections INTEGER DEFAULT 0,
    total_transactions BIGINT DEFAULT 0,
    transaction_volume DECIMAL(18, 2) DEFAULT 0,
    avg_processing_time_ms DECIMAL(10, 2),
    success_rate DECIMAL(5, 4),
    reconciliation_rate DECIMAL(5, 4),
    by_partner JSONB,
    by_currency JSONB,
    top_error_codes JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_bank_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_bank_partner_type ON bank_partner(partner_type);
CREATE INDEX idx_bank_partner_country ON bank_partner(country_code);
CREATE INDEX idx_bank_partner_active ON bank_partner(is_active) WHERE is_active = true;

CREATE INDEX idx_bank_connection_partner ON bank_connection(partner_id);
CREATE INDEX idx_bank_connection_status ON bank_connection(connection_status);
CREATE INDEX idx_bank_connection_type ON bank_connection(connection_type);

CREATE INDEX idx_bank_transaction_partner ON bank_transaction(partner_id);
CREATE INDEX idx_bank_transaction_connection ON bank_transaction(connection_id);
CREATE INDEX idx_bank_transaction_type ON bank_transaction(transaction_type);
CREATE INDEX idx_bank_transaction_status ON bank_transaction(status);
CREATE INDEX idx_bank_transaction_reference ON bank_transaction(internal_reference);
CREATE INDEX idx_bank_transaction_created ON bank_transaction(created_at DESC);

CREATE INDEX idx_bank_account_partner ON bank_account(partner_id);
CREATE INDEX idx_bank_account_number ON bank_account(account_number);
CREATE INDEX idx_bank_account_type ON bank_account(account_type);
CREATE INDEX idx_bank_account_status ON bank_account(account_status);

CREATE INDEX idx_bank_statement_account ON bank_statement(account_id);
CREATE INDEX idx_bank_statement_partner ON bank_statement(partner_id);
CREATE INDEX idx_bank_statement_date ON bank_statement(statement_date DESC);
CREATE INDEX idx_bank_statement_reconciliation ON bank_statement(reconciliation_status);

CREATE INDEX idx_bank_reconciliation_account ON bank_reconciliation(account_id);
CREATE INDEX idx_bank_reconciliation_statement ON bank_reconciliation(statement_id);
CREATE INDEX idx_bank_reconciliation_status ON bank_reconciliation(status);
CREATE INDEX idx_bank_reconciliation_date ON bank_reconciliation(reconciliation_date DESC);

CREATE INDEX idx_bank_webhook_partner ON bank_webhook(partner_id);
CREATE INDEX idx_bank_webhook_type ON bank_webhook(webhook_type);
CREATE INDEX idx_bank_webhook_active ON bank_webhook(is_active) WHERE is_active = true;

CREATE INDEX idx_bank_api_request_partner ON bank_api_request(partner_id);
CREATE INDEX idx_bank_api_request_connection ON bank_api_request(connection_id);
CREATE INDEX idx_bank_api_request_type ON bank_api_request(request_type);
CREATE INDEX idx_bank_api_request_timestamp ON bank_api_request(request_timestamp DESC);

CREATE INDEX idx_bank_rate_limit_partner ON bank_rate_limit(partner_id);
CREATE INDEX idx_bank_rate_limit_connection ON bank_rate_limit(connection_id);
CREATE INDEX idx_bank_rate_limit_reset ON bank_rate_limit(reset_at);

CREATE INDEX idx_bank_error_log_partner ON bank_error_log(partner_id);
CREATE INDEX idx_bank_error_log_type ON bank_error_log(error_type);
CREATE INDEX idx_bank_error_log_severity ON bank_error_log(severity);
CREATE INDEX idx_bank_error_log_occurred ON bank_error_log(occurred_at DESC);

CREATE INDEX idx_bank_configuration_partner ON bank_configuration(partner_id);
CREATE INDEX idx_bank_configuration_type ON bank_configuration(config_type);
CREATE INDEX idx_bank_configuration_active ON bank_configuration(is_active) WHERE is_active = true;

CREATE INDEX idx_bank_compliance_check_transaction ON bank_compliance_check(transaction_id);
CREATE INDEX idx_bank_compliance_check_partner ON bank_compliance_check(partner_id);
CREATE INDEX idx_bank_compliance_check_status ON bank_compliance_check(check_status);
CREATE INDEX idx_bank_compliance_check_risk ON bank_compliance_check(risk_score DESC);

CREATE INDEX idx_bank_analytics_partner ON bank_analytics(partner_id);
CREATE INDEX idx_bank_analytics_period ON bank_analytics(period_end DESC);

CREATE INDEX idx_bank_statistics_period ON bank_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_bank_partner_updated_at BEFORE UPDATE ON bank_partner
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_connection_updated_at BEFORE UPDATE ON bank_connection
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_transaction_updated_at BEFORE UPDATE ON bank_transaction
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_account_updated_at BEFORE UPDATE ON bank_account
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_reconciliation_updated_at BEFORE UPDATE ON bank_reconciliation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_webhook_updated_at BEFORE UPDATE ON bank_webhook
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_configuration_updated_at BEFORE UPDATE ON bank_configuration
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_compliance_check_updated_at BEFORE UPDATE ON bank_compliance_check
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();