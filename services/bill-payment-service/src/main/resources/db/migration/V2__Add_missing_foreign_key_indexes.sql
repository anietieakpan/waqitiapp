-- Add missing foreign key indexes for bill-payment-service
-- Critical for customer lookup and payment processing performance

-- bill_account table - customer lookup
CREATE INDEX IF NOT EXISTS idx_bill_account_customer_id_fk
    ON bill_account(customer_id);

-- bill_payment table - customer and created_by lookup
CREATE INDEX IF NOT EXISTS idx_bill_payment_customer_id_fk
    ON bill_payment(customer_id);

CREATE INDEX IF NOT EXISTS idx_bill_payment_created_by
    ON bill_payment(created_by)
    WHERE created_by IS NOT NULL;

-- bill_payment_schedule table - customer lookup
CREATE INDEX IF NOT EXISTS idx_bill_payment_schedule_customer_id_fk
    ON bill_payment_schedule(customer_id);

-- bill_statement table - customer lookup
CREATE INDEX IF NOT EXISTS idx_bill_statement_customer_id_fk
    ON bill_statement(customer_id);

-- bill_reminder table - customer lookup
CREATE INDEX IF NOT EXISTS idx_bill_reminder_customer_id_fk
    ON bill_reminder(customer_id);

-- bill_payment_history table - changed_by audit
CREATE INDEX IF NOT EXISTS idx_bill_payment_history_changed_by
    ON bill_payment_history(changed_by)
    WHERE changed_by IS NOT NULL;

-- bill_payment_method table - customer lookup
CREATE INDEX IF NOT EXISTS idx_bill_payment_method_customer_id_fk
    ON bill_payment_method(customer_id);

-- bill_recurring_payment table - customer and payment method lookup
CREATE INDEX IF NOT EXISTS idx_bill_recurring_payment_customer_id_fk
    ON bill_recurring_payment(customer_id);

CREATE INDEX IF NOT EXISTS idx_bill_recurring_payment_payment_method_id
    ON bill_recurring_payment(payment_method_id);

-- bill_payment_notification table - customer lookup
CREATE INDEX IF NOT EXISTS idx_bill_payment_notification_customer_id_fk
    ON bill_payment_notification(customer_id);

COMMENT ON INDEX idx_bill_payment_customer_id_fk IS
    'Performance: Find all payments for specific customer';
COMMENT ON INDEX idx_bill_payment_created_by IS
    'Audit: Track payments created by specific user';
COMMENT ON INDEX idx_bill_recurring_payment_payment_method_id IS
    'Performance: Find all recurring payments using specific payment method';

-- Analyze tables
ANALYZE bill_account;
ANALYZE bill_payment;
ANALYZE bill_payment_schedule;
ANALYZE bill_statement;
ANALYZE bill_reminder;
ANALYZE bill_payment_history;
ANALYZE bill_payment_method;
ANALYZE bill_recurring_payment;
ANALYZE bill_payment_notification;
