-- Critical Performance Indexes for High-Traffic Tables
-- Created to address performance bottlenecks identified in forensic audit

-- =====================================================
-- TRANSACTION TABLE INDEXES
-- =====================================================

-- Index for transaction lookups by status and date
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_status_date
ON transactions(status, transaction_date DESC)
WHERE status IN ('PENDING', 'PROCESSING', 'FAILED');

-- Index for account-based transaction queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_account_lookup
ON transactions(source_account_id, transaction_date DESC)
INCLUDE (target_account_id, amount, currency, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_target_account
ON transactions(target_account_id, transaction_date DESC)
INCLUDE (source_account_id, amount, currency, status);

-- Index for transaction reconciliation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reconciliation
ON transactions(external_reference_id)
WHERE external_reference_id IS NOT NULL AND status = 'COMPLETED';

-- =====================================================
-- PAYMENT TABLE INDEXES
-- =====================================================

-- Index for payment status monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_monitoring
ON payments(status, created_at DESC)
WHERE status IN ('PENDING', 'PROCESSING', 'SCHEDULED');

-- Index for merchant payment queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_merchant_lookup
ON payments(merchant_id, payment_date DESC, status)
WHERE merchant_id IS NOT NULL;

-- Index for payment method analytics
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_method_analytics
ON payments(payment_method, created_at DESC)
INCLUDE (amount, currency, status);

-- =====================================================
-- WALLET TABLE INDEXES  
-- =====================================================

-- Index for wallet balance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_currency
ON wallets(user_id, currency, status)
INCLUDE (balance, available_balance, reserved_balance);

-- Index for wallet transaction history
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_history
ON wallet_transactions(wallet_id, created_at DESC)
INCLUDE (amount, transaction_type, status);

-- Index for pending wallet operations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_operations_pending
ON wallet_operations(status, scheduled_at)
WHERE status = 'PENDING' AND scheduled_at IS NOT NULL;

-- =====================================================
-- USER TABLE INDEXES
-- =====================================================

-- Index for user authentication
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_auth_lookup
ON users(email, status)
WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- Index for user phone verification
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_phone_lookup
ON users(phone_number, phone_verified)
WHERE phone_number IS NOT NULL;

-- Index for user KYC status queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_kyc_status
ON users(kyc_status, updated_at DESC)
WHERE kyc_status IN ('PENDING', 'IN_REVIEW', 'REJECTED');

-- =====================================================
-- ACCOUNT TABLE INDEXES
-- =====================================================

-- Index for account balance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_balance_lookup
ON accounts(account_number, status)
INCLUDE (balance, available_balance, currency);

-- Index for account type filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_type_status
ON accounts(account_type, status, created_at DESC)
WHERE status = 'ACTIVE';

-- Index for dormant account monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_dormancy_check
ON accounts(last_activity_date)
WHERE status = 'ACTIVE' AND last_activity_date < NOW() - INTERVAL '90 days';

-- =====================================================
-- NOTIFICATION TABLE INDEXES
-- =====================================================

-- Index for pending notifications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_pending_delivery
ON notifications(status, scheduled_at)
WHERE status IN ('PENDING', 'RETRY') AND scheduled_at <= NOW();

-- Index for user notification history
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_history
ON notifications(user_id, created_at DESC)
INCLUDE (type, status, read_at);

-- =====================================================
-- AUDIT LOG INDEXES
-- =====================================================

-- Index for audit trail queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_entity_lookup
ON audit_logs(entity_type, entity_id, created_at DESC)
INCLUDE (action, user_id);

-- Index for compliance reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_compliance
ON audit_logs(action, created_at DESC)
WHERE action IN ('LARGE_TRANSFER', 'SUSPICIOUS_ACTIVITY', 'AML_FLAG');

-- =====================================================
-- CRYPTO TRANSACTION INDEXES
-- =====================================================

-- Index for blockchain confirmation monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_transactions_confirmations
ON crypto_transactions(blockchain, confirmation_count, created_at DESC)
WHERE status = 'PENDING' AND confirmation_count < required_confirmations;

-- Index for crypto wallet lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_wallets_address
ON crypto_wallets(address, currency, status)
WHERE status = 'ACTIVE';

-- =====================================================
-- FRAUD DETECTION INDEXES
-- =====================================================

-- Index for real-time fraud monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_events_monitoring
ON fraud_events(status, risk_score DESC, created_at DESC)
WHERE status IN ('PENDING_REVIEW', 'HIGH_RISK');

-- Index for user fraud history
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_events_user_history
ON fraud_events(user_id, created_at DESC)
INCLUDE (event_type, risk_score, status);

-- =====================================================
-- MERCHANT INDEXES
-- =====================================================

-- Index for merchant settlement queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_settlements_pending
ON merchant_settlements(merchant_id, status, settlement_date)
WHERE status IN ('PENDING', 'PROCESSING');

-- Index for merchant transaction volume
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_transactions_volume
ON merchant_transactions(merchant_id, transaction_date DESC)
INCLUDE (amount, status);

-- =====================================================
-- RECURRING PAYMENT INDEXES
-- =====================================================

-- Index for scheduled payment execution
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_recurring_payments_schedule
ON recurring_payments(next_execution_date, status)
WHERE status = 'ACTIVE' AND next_execution_date <= NOW() + INTERVAL '1 day';

-- Index for payment retry operations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_retries_pending
ON payment_retries(retry_at, status)
WHERE status = 'PENDING' AND retry_at <= NOW();

-- =====================================================
-- KYC VERIFICATION INDEXES
-- =====================================================

-- Index for KYC queue processing
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_verifications_queue
ON kyc_verifications(status, priority DESC, created_at)
WHERE status IN ('PENDING', 'IN_REVIEW');

-- Index for document verification lookups
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_verification
ON kyc_documents(user_id, document_type, status)
WHERE status != 'EXPIRED';

-- =====================================================
-- INTERNATIONAL TRANSFER INDEXES
-- =====================================================

-- Index for SWIFT transfer tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_international_transfers_swift
ON international_transfers(swift_code, status, created_at DESC)
WHERE swift_code IS NOT NULL;

-- Index for compliance screening
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_international_transfers_screening
ON international_transfers(screening_status, created_at DESC)
WHERE screening_status IN ('PENDING', 'FLAGGED');

-- =====================================================
-- PARTIAL INDEXES FOR SOFT DELETES
-- =====================================================

-- Optimize queries that filter out soft-deleted records
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_active
ON users(id) WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_active
ON accounts(id) WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_active
ON wallets(id) WHERE deleted_at IS NULL;

-- =====================================================
-- COMPOSITE INDEXES FOR COMPLEX QUERIES
-- =====================================================

-- Index for transaction reporting with date ranges
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reporting
ON transactions(user_id, transaction_date, status)
INCLUDE (amount, currency, transaction_type);

-- Index for balance history tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_balance_history_lookup
ON balance_history(account_id, recorded_at DESC)
INCLUDE (balance, available_balance);

-- =====================================================
-- JSON/JSONB INDEXES FOR METADATA
-- =====================================================

-- GIN index for transaction metadata searches
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_metadata_gin
ON transactions USING GIN (metadata)
WHERE metadata IS NOT NULL;

-- GIN index for user preferences
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_preferences_gin
ON users USING GIN (preferences)
WHERE preferences IS NOT NULL;

-- =====================================================
-- TEXT SEARCH INDEXES
-- =====================================================

-- Full text search on transaction descriptions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_description_fts
ON transactions USING GIN (to_tsvector('english', description))
WHERE description IS NOT NULL;

-- Full text search on merchant names
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_name_fts
ON merchants USING GIN (to_tsvector('english', name));

-- =====================================================
-- PERFORMANCE MONITORING
-- =====================================================

-- Create statistics for better query planning
ANALYZE transactions;
ANALYZE payments;
ANALYZE wallets;
ANALYZE wallet_transactions;
ANALYZE users;
ANALYZE accounts;
ANALYZE notifications;
ANALYZE audit_logs;
ANALYZE crypto_transactions;
ANALYZE fraud_events;
ANALYZE merchant_settlements;
ANALYZE recurring_payments;
ANALYZE kyc_verifications;
ANALYZE international_transfers;

-- =====================================================
-- INDEX MAINTENANCE COMMENT
-- =====================================================
COMMENT ON SCHEMA public IS 'Performance indexes added to address critical bottlenecks identified in forensic audit. These indexes target the most frequent query patterns and should significantly improve response times for high-traffic operations.';