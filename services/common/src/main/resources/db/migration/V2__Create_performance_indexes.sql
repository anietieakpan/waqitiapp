-- Performance Optimization Indexes for Waqiti Platform
-- Phase 1: Critical Performance Indexes

-- User search optimization with trigram matching
-- Enables fast full-text search across username and email
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_search_trgm 
ON users USING gin((username || ' ' || email) gin_trgm_ops);

-- User lookup optimization - most common queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_active 
ON users(email) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_phone_active 
ON users(phone_number) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_external_id 
ON users(external_id) WHERE external_id IS NOT NULL;

-- Notification performance optimization
-- Composite index for user notification queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_read_created 
ON notifications(user_id, read, created_at DESC);

-- Delivery status optimization with covering index
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_delivery_status_type 
ON notifications(delivery_status, type) INCLUDE (created_at, user_id);

-- Notification cleanup optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_expires_unread 
ON notifications(expires_at, read) WHERE expires_at IS NOT NULL AND read = false;

-- Category and reference lookup optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_category_user 
ON notifications(category, user_id, created_at DESC) WHERE category IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_reference_id 
ON notifications(reference_id) WHERE reference_id IS NOT NULL;

-- Transaction performance optimization
-- Primary transaction queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_status_created 
ON transactions(user_id, status, created_at DESC);

-- Payment-specific transaction queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_payment_id 
ON transactions(payment_id) WHERE payment_id IS NOT NULL;

-- Transaction type and amount filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_type_amount_date 
ON transactions(transaction_type, amount, created_at DESC);

-- Fraud detection queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_high_value 
ON transactions(amount, created_at, user_id) WHERE amount > 1000;

-- Analytics time-series optimization
-- Transaction metrics date-based queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_metrics_date_year_month 
ON transaction_metrics(EXTRACT(YEAR FROM date), EXTRACT(MONTH FROM date), date);

-- Daily metrics lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_metrics_date_volume 
ON transaction_metrics(date, total_volume DESC);

-- Success rate calculations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_metrics_success_rate 
ON transaction_metrics(date) INCLUDE (total_transactions, successful_transactions);

-- Payment performance optimization
-- Payment status and user queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_user_status_created 
ON payments(user_id, status, created_at DESC) 
WHERE status IN ('COMPLETED', 'PENDING', 'FAILED');

-- Merchant payment queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_merchant_status 
ON payments(merchant_id, status, created_at DESC) WHERE merchant_id IS NOT NULL;

-- Payment method optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_method_status 
ON payments(payment_method, status, created_at DESC);

-- Refund and capture queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_original_payment 
ON payments(original_payment_id) WHERE original_payment_id IS NOT NULL;

-- Wallet performance optimization
-- Balance queries by user and currency
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_balances_user_currency 
ON wallet_balances(user_id, currency_code, updated_at DESC);

-- Active wallet queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_status 
ON wallets(user_id, status) WHERE status = 'ACTIVE';

-- Wallet transaction history
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_wallet_date 
ON wallet_transactions(wallet_id, created_at DESC);

-- Social feed performance optimization
-- User feed queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_social_feed_user_visibility 
ON social_feed(user_id, visibility, created_at DESC);

-- Activity type filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_social_feed_activity_type 
ON social_feed(activity_type, created_at DESC, visibility);

-- Following/followers optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_social_connections_user_status 
ON social_connections(user_id, status) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_social_connections_connected_user 
ON social_connections(connected_user_id, status) WHERE status = 'ACTIVE';

-- KYC performance optimization
-- KYC status and verification queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_user_status 
ON kyc_documents(user_id, status, created_at DESC);

-- Document type lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_type_status 
ON kyc_documents(document_type, status, created_at DESC);

-- Verification processing queue
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_pending 
ON kyc_documents(created_at ASC) WHERE status = 'PENDING';

-- Merchant performance optimization
-- Merchant status and verification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_status_verification 
ON merchants(status, verification_status, created_at DESC);

-- Business category optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_category_status 
ON merchants(business_category, status) WHERE status = 'ACTIVE';

-- Security and compliance optimization
-- Security events by user and type
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_events_user_type_date 
ON security_events(user_id, event_type, created_at DESC);

-- Suspicious activity monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_security_events_severity 
ON security_events(severity, created_at DESC) WHERE severity IN ('HIGH', 'CRITICAL');

-- AML alerts optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_status_created 
ON aml_alerts(status, created_at DESC);

-- Compliance reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_reports_period 
ON compliance_reports(report_period, report_type, created_at DESC);

-- Investment performance optimization
-- Portfolio queries by user
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_portfolios_user 
ON investment_portfolios(user_id, status, created_at DESC);

-- Holdings by portfolio and symbol
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_holdings_portfolio_symbol 
ON investment_holdings(portfolio_id, symbol, updated_at DESC);

-- Order history optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_orders_user_status 
ON investment_orders(user_id, status, created_at DESC);

-- Crypto service optimization
-- Crypto wallet addresses
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_addresses_user_currency 
ON crypto_addresses(user_id, currency_code, created_at DESC);

-- Crypto transaction monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_transactions_address_status 
ON crypto_transactions(from_address, status, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_transactions_to_address 
ON crypto_transactions(to_address, created_at DESC);

-- Blockchain confirmation tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_transactions_confirmations 
ON crypto_transactions(confirmations, status) WHERE status = 'PENDING';

-- Audit trail optimization
-- Audit events by entity and action
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_entity_action 
ON audit_events(entity_type, action, created_at DESC);

-- User action audit trail
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_user_date 
ON audit_events(user_id, created_at DESC) WHERE user_id IS NOT NULL;

-- Entity-specific audit queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_entity_id 
ON audit_events(entity_id, entity_type, created_at DESC);

-- Notification preferences optimization
-- User preference lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_preferences_user_type 
ON notification_preferences(user_id, notification_type);

-- Device token management
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_device_tokens_user_active 
ON device_tokens(user_id, is_active, updated_at DESC) WHERE is_active = true;

-- Push notification optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_push_notifications_status_scheduled 
ON push_notifications(status, scheduled_at) WHERE status IN ('PENDING', 'SCHEDULED');

-- Recurring payment optimization
-- Active recurring payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_recurring_payments_user_status 
ON recurring_payments(user_id, status, next_execution_date) WHERE status = 'ACTIVE';

-- Execution scheduling
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_recurring_payments_next_execution 
ON recurring_payments(next_execution_date, status) WHERE status = 'ACTIVE';

-- Execution history
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_recurring_executions_payment_date 
ON recurring_executions(recurring_payment_id, executed_at DESC);

-- BNPL optimization
-- BNPL applications by user and status
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_bnpl_applications_user_status 
ON bnpl_applications(user_id, status, created_at DESC);

-- Installment tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_bnpl_installments_application_due 
ON bnpl_installments(application_id, due_date, status);

-- Overdue installments monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_bnpl_installments_overdue 
ON bnpl_installments(due_date, status) WHERE status = 'PENDING' AND due_date < CURRENT_DATE;

-- Create statistics for query planner optimization
ANALYZE users;
ANALYZE notifications;
ANALYZE transactions;
ANALYZE transaction_metrics;
ANALYZE payments;
ANALYZE wallet_balances;
ANALYZE wallets;
ANALYZE social_feed;
ANALYZE kyc_documents;
ANALYZE merchants;
ANALYZE security_events;
ANALYZE investment_portfolios;
ANALYZE crypto_transactions;
ANALYZE audit_events;
ANALYZE recurring_payments;
ANALYZE bnpl_applications;

-- Comments for documentation
COMMENT ON INDEX idx_users_search_trgm IS 'Trigram index for fast user search across username and email';
COMMENT ON INDEX idx_notifications_user_read_created IS 'Composite index for user notification queries with read status';
COMMENT ON INDEX idx_transactions_user_status_created IS 'Primary index for user transaction history queries';
COMMENT ON INDEX idx_transaction_metrics_date_year_month IS 'Optimized index for analytics date-based aggregations';
COMMENT ON INDEX idx_payments_user_status_created IS 'Primary index for user payment queries';
COMMENT ON INDEX idx_social_feed_user_visibility IS 'Index for user social feed queries with visibility filtering';