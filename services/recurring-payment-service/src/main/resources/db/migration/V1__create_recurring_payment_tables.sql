-- Create recurring payments table
CREATE TABLE recurring_payments (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    recipient_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description TEXT,
    frequency VARCHAR(20) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP,
    next_execution_date TIMESTAMP,
    last_execution_date TIMESTAMP,
    max_occurrences INTEGER,
    day_of_month INTEGER,
    day_of_week VARCHAR(20),
    monthly_pattern VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    total_executions INTEGER DEFAULT 0,
    successful_executions INTEGER DEFAULT 0,
    failed_executions INTEGER DEFAULT 0,
    total_amount_paid DECIMAL(19, 4) DEFAULT 0,
    consecutive_failures INTEGER DEFAULT 0,
    last_failure_date TIMESTAMP,
    last_failure_reason TEXT,
    reminder_enabled BOOLEAN DEFAULT FALSE,
    reminder_days TEXT,
    auto_retry BOOLEAN DEFAULT TRUE,
    max_retry_attempts INTEGER DEFAULT 3,
    payment_method VARCHAR(50),
    payment_method_id VARCHAR(36),
    failure_action VARCHAR(20),
    tags TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    paused_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancellation_reason TEXT,
    version BIGINT DEFAULT 0,
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_dates CHECK (end_date IS NULL OR end_date > start_date),
    CONSTRAINT chk_max_occurrences CHECK (max_occurrences IS NULL OR max_occurrences > 0)
);

-- Create indexes
CREATE INDEX idx_recurring_payments_user_id ON recurring_payments(user_id);
CREATE INDEX idx_recurring_payments_recipient_id ON recurring_payments(recipient_id);
CREATE INDEX idx_recurring_payments_status ON recurring_payments(status);
CREATE INDEX idx_recurring_payments_next_execution ON recurring_payments(next_execution_date);
CREATE INDEX idx_recurring_payments_user_status ON recurring_payments(user_id, status);
CREATE INDEX idx_recurring_payments_execution_window ON recurring_payments(status, next_execution_date);

-- Create recurring executions table
CREATE TABLE recurring_executions (
    id VARCHAR(36) PRIMARY KEY,
    recurring_payment_id VARCHAR(36) NOT NULL,
    scheduled_date TIMESTAMP NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    trigger VARCHAR(20) NOT NULL,
    payment_id VARCHAR(36),
    transaction_id VARCHAR(36),
    failure_reason TEXT,
    attempt_count INTEGER DEFAULT 0,
    retry_at TIMESTAMP,
    processing_time_ms BIGINT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recurring_payment FOREIGN KEY (recurring_payment_id) 
        REFERENCES recurring_payments(id) ON DELETE CASCADE
);

-- Create indexes for executions
CREATE INDEX idx_executions_recurring_payment_id ON recurring_executions(recurring_payment_id);
CREATE INDEX idx_executions_status ON recurring_executions(status);
CREATE INDEX idx_executions_executed_at ON recurring_executions(executed_at);
CREATE INDEX idx_executions_retry_at ON recurring_executions(retry_at);
CREATE INDEX idx_executions_payment_status ON recurring_executions(recurring_payment_id, status);

-- Create recurring templates table
CREATE TABLE recurring_templates (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    default_recipient_id VARCHAR(36),
    default_amount DECIMAL(19, 4),
    currency VARCHAR(3) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    day_of_month INTEGER,
    day_of_week VARCHAR(20),
    monthly_pattern VARCHAR(20),
    reminder_enabled BOOLEAN DEFAULT FALSE,
    reminder_days TEXT,
    auto_retry BOOLEAN DEFAULT TRUE,
    max_retry_attempts INTEGER DEFAULT 3,
    payment_method VARCHAR(50),
    failure_action VARCHAR(20),
    tags TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Create indexes for templates
CREATE INDEX idx_templates_user_id ON recurring_templates(user_id);
CREATE INDEX idx_templates_category ON recurring_templates(category);
CREATE INDEX idx_templates_user_active ON recurring_templates(user_id, is_active);

-- Create audit log table
CREATE TABLE recurring_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    recurring_payment_id VARCHAR(36),
    user_id VARCHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    action_details JSONB,
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT
);

-- Create index for audit log
CREATE INDEX idx_audit_log_recurring_payment ON recurring_audit_log(recurring_payment_id);
CREATE INDEX idx_audit_log_user ON recurring_audit_log(user_id);
CREATE INDEX idx_audit_log_performed_at ON recurring_audit_log(performed_at);

-- Create scheduled lock table for distributed scheduling
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);