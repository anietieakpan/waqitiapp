-- Create scheduled payments table
CREATE TABLE scheduled_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    recipient_type VARCHAR(50) NOT NULL CHECK (recipient_type IN ('USER', 'MERCHANT', 'BILL_PAYEE')),
    recipient_name VARCHAR(255) NOT NULL,
    recipient_account VARCHAR(100),
    
    -- Payment Details
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    description TEXT,
    payment_method VARCHAR(50) NOT NULL CHECK (payment_method IN ('WALLET', 'BANK_ACCOUNT', 'CARD')),
    payment_method_id VARCHAR(100),
    
    -- Schedule Configuration
    schedule_type VARCHAR(50) NOT NULL CHECK (schedule_type IN ('RECURRING', 'ONE_TIME')),
    recurrence_pattern VARCHAR(50) NOT NULL CHECK (recurrence_pattern IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUALLY', 'CUSTOM')),
    recurrence_interval INTEGER,
    start_date DATE NOT NULL,
    end_date DATE,
    next_payment_date DATE NOT NULL,
    preferred_time TIME,
    
    -- Execution Details
    total_payments INTEGER,
    completed_payments INTEGER NOT NULL DEFAULT 0,
    failed_payments INTEGER NOT NULL DEFAULT 0,
    last_payment_date TIMESTAMP,
    last_payment_status VARCHAR(50),
    last_payment_id UUID,
    
    -- Notification Settings
    send_reminder BOOLEAN NOT NULL DEFAULT true,
    reminder_days_before INTEGER DEFAULT 1,
    notify_on_success BOOLEAN NOT NULL DEFAULT true,
    notify_on_failure BOOLEAN NOT NULL DEFAULT true,
    
    -- Status
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED')),
    pause_reason TEXT,
    cancellation_reason TEXT,
    
    -- Metadata
    metadata JSONB,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paused_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    
    -- Constraints
    CONSTRAINT valid_dates CHECK (end_date IS NULL OR end_date > start_date),
    CONSTRAINT valid_recurrence_interval CHECK (
        (recurrence_pattern != 'CUSTOM') OR 
        (recurrence_pattern = 'CUSTOM' AND recurrence_interval IS NOT NULL AND recurrence_interval > 0)
    ),
    CONSTRAINT valid_reminder_days CHECK (reminder_days_before >= 0 AND reminder_days_before <= 30),
    CONSTRAINT valid_total_payments CHECK (total_payments IS NULL OR total_payments > 0),
    CONSTRAINT valid_recurring_limit CHECK (
        schedule_type != 'RECURRING' OR end_date IS NOT NULL OR total_payments IS NOT NULL
    )
);

-- Indexes for performance
CREATE INDEX idx_scheduled_payments_user_id ON scheduled_payments(user_id);
CREATE INDEX idx_scheduled_payments_recipient_id ON scheduled_payments(recipient_id);
CREATE INDEX idx_scheduled_payments_status ON scheduled_payments(status);
CREATE INDEX idx_scheduled_payments_next_payment_date ON scheduled_payments(next_payment_date);
CREATE INDEX idx_scheduled_payments_due_payments ON scheduled_payments(status, next_payment_date, preferred_time) 
    WHERE status = 'ACTIVE';
CREATE INDEX idx_scheduled_payments_reminders ON scheduled_payments(status, send_reminder, next_payment_date) 
    WHERE status = 'ACTIVE' AND send_reminder = true;

-- Comments
COMMENT ON TABLE scheduled_payments IS 'Stores scheduled and recurring payment configurations';
COMMENT ON COLUMN scheduled_payments.schedule_type IS 'RECURRING for repeated payments, ONE_TIME for future single payment';
COMMENT ON COLUMN scheduled_payments.recurrence_pattern IS 'Frequency of recurring payments';
COMMENT ON COLUMN scheduled_payments.recurrence_interval IS 'Custom interval in days for CUSTOM recurrence pattern';
COMMENT ON COLUMN scheduled_payments.next_payment_date IS 'Date when the next payment should be processed';
COMMENT ON COLUMN scheduled_payments.preferred_time IS 'Preferred time of day for payment execution';
COMMENT ON COLUMN scheduled_payments.total_payments IS 'Maximum number of payments for recurring schedule';
COMMENT ON COLUMN scheduled_payments.reminder_days_before IS 'Days before payment date to send reminder';