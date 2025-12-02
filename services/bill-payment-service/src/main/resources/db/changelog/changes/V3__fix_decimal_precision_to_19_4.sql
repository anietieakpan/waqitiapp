-- Fix DECIMAL(18,2) and DECIMAL(10,2) to DECIMAL(19,4) for all financial amounts in Bill Payment service
-- Priority: HIGH - Critical for accurate bill payment processing and fee calculations
-- Impact: Prevents precision loss in payment amounts and statement balances

-- bill_payee table - Fix payment amount limits
ALTER TABLE bill_payee
    ALTER COLUMN minimum_payment_amount TYPE DECIMAL(19,4),
    ALTER COLUMN maximum_payment_amount TYPE DECIMAL(19,4);

-- bill_account table - Fix all balance and payment fields
ALTER TABLE bill_account
    ALTER COLUMN auto_pay_amount TYPE DECIMAL(19,4),
    ALTER COLUMN last_payment_amount TYPE DECIMAL(19,4),
    ALTER COLUMN current_balance TYPE DECIMAL(19,4),
    ALTER COLUMN minimum_due TYPE DECIMAL(19,4),
    ALTER COLUMN statement_balance TYPE DECIMAL(19,4),
    ALTER COLUMN past_due_amount TYPE DECIMAL(19,4);

-- bill_payment table - Fix payment amount and fee fields
ALTER TABLE bill_payment
    ALTER COLUMN payment_amount TYPE DECIMAL(19,4),
    ALTER COLUMN fee_amount TYPE DECIMAL(19,4);

-- bill_payment_schedule table - Fix amount fields
ALTER TABLE bill_payment_schedule
    ALTER COLUMN fixed_amount TYPE DECIMAL(19,4);

-- bill_statement table - Fix all statement amount fields
ALTER TABLE bill_statement
    ALTER COLUMN minimum_payment_due TYPE DECIMAL(19,4),
    ALTER COLUMN statement_balance TYPE DECIMAL(19,4),
    ALTER COLUMN previous_balance TYPE DECIMAL(19,4),
    ALTER COLUMN payment_received TYPE DECIMAL(19,4),
    ALTER COLUMN new_charges TYPE DECIMAL(19,4),
    ALTER COLUMN interest_charges TYPE DECIMAL(19,4),
    ALTER COLUMN fees TYPE DECIMAL(19,4),
    ALTER COLUMN credits TYPE DECIMAL(19,4),
    ALTER COLUMN past_due_amount TYPE DECIMAL(19,4),
    ALTER COLUMN available_credit TYPE DECIMAL(19,4),
    ALTER COLUMN credit_limit TYPE DECIMAL(19,4),
    ALTER COLUMN late_fee TYPE DECIMAL(19,4),
    ALTER COLUMN autopay_amount TYPE DECIMAL(19,4);

-- bill_reminder table - Fix amount due field
ALTER TABLE bill_reminder
    ALTER COLUMN amount_due TYPE DECIMAL(19,4);

-- bill_payment_method table - Fix verification amount fields
ALTER TABLE bill_payment_method
    ALTER COLUMN verification_amount_1 TYPE DECIMAL(19,4),
    ALTER COLUMN verification_amount_2 TYPE DECIMAL(19,4);

-- bill_recurring_payment table - Fix amount fields
ALTER TABLE bill_recurring_payment
    ALTER COLUMN fixed_amount TYPE DECIMAL(19,4),
    ALTER COLUMN total_amount_paid TYPE DECIMAL(19,4);

-- bill_payment_fee table - Fix all fee fields
ALTER TABLE bill_payment_fee
    ALTER COLUMN fixed_fee TYPE DECIMAL(19,4),
    ALTER COLUMN minimum_fee TYPE DECIMAL(19,4),
    ALTER COLUMN maximum_fee TYPE DECIMAL(19,4),
    ALTER COLUMN expedited_fee TYPE DECIMAL(19,4);

-- bill_payment_analytics table - Fix volume and amount fields
ALTER TABLE bill_payment_analytics
    ALTER COLUMN total_payment_volume TYPE DECIMAL(19,4),
    ALTER COLUMN avg_payment_amount TYPE DECIMAL(19,4),
    ALTER COLUMN total_fees_collected TYPE DECIMAL(19,4),
    ALTER COLUMN avg_processing_time_hours TYPE DECIMAL(19,4);

-- bill_payment_statistics table - Fix payment volume and average fields
ALTER TABLE bill_payment_statistics
    ALTER COLUMN payment_volume TYPE DECIMAL(19,4),
    ALTER COLUMN avg_payment_amount TYPE DECIMAL(19,4);

-- Add comments for documentation
COMMENT ON COLUMN bill_payment.payment_amount IS 'Payment amount with 4 decimal precision for accurate bill payment processing';
COMMENT ON COLUMN bill_statement.statement_balance IS 'Statement balance with 4 decimal precision for accurate billing';
COMMENT ON COLUMN bill_payment_fee.fixed_fee IS 'Fee amount with 4 decimal precision for accurate fee calculation';
COMMENT ON COLUMN bill_payment_analytics.total_payment_volume IS 'Payment volume with 4 decimal precision for accurate analytics';

-- Analyze tables to update statistics
ANALYZE bill_payee;
ANALYZE bill_account;
ANALYZE bill_payment;
ANALYZE bill_payment_schedule;
ANALYZE bill_statement;
ANALYZE bill_reminder;
ANALYZE bill_payment_method;
ANALYZE bill_recurring_payment;
ANALYZE bill_payment_fee;
ANALYZE bill_payment_analytics;
ANALYZE bill_payment_statistics;
