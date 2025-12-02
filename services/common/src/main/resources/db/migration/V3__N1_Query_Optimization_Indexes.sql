-- N+1 Query Optimization Indexes
-- These indexes specifically target common N+1 query patterns

-- User service optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_profiles_user_id_covering 
ON user_profiles (user_id) INCLUDE (first_name, last_name, preferred_language, preferred_currency);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mfa_configurations_user_id_covering 
ON mfa_configurations (user_id) INCLUDE (method, is_enabled, created_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_verification_tokens_user_id_type 
ON verification_tokens (user_id, type) WHERE is_used = false;

-- Merchant service optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_payment_methods_merchant_id_covering 
ON merchant_payment_methods (merchant_id) INCLUDE (type, account_number, is_default, is_active) 
WHERE is_active = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_transactions_merchant_id_date 
ON merchant_transactions (merchant_id, created_at DESC) INCLUDE (amount, status, currency);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_verifications_merchant_id_covering 
ON merchant_verifications (merchant_id) INCLUDE (document_type, status, submitted_at);

-- Transaction service optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_items_transaction_id_covering 
ON transaction_items (transaction_id) INCLUDE (item_name, quantity, price, total);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_id_date_covering 
ON transactions (user_id, created_at DESC) INCLUDE (amount, status, merchant_id, currency);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_merchant_id_date_covering 
ON transactions (merchant_id, created_at DESC) INCLUDE (amount, status, user_id, currency);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_status_created_at 
ON transactions (status, created_at) INCLUDE (id, user_id, merchant_id, amount);

-- Payment service optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_id_covering 
ON payment_methods (user_id) INCLUDE (type, last_four, is_default, is_active) 
WHERE is_active = true;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_attempts_payment_id_covering 
ON payment_attempts (payment_id) INCLUDE (attempt_number, status, created_at, failure_reason);

-- Notification service optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_id_unread 
ON notifications (user_id, is_read, created_at DESC) WHERE is_read = false;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_preferences_user_id_covering 
ON notification_preferences (user_id) INCLUDE (email_enabled, sms_enabled, push_enabled);

-- Wallet service optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_transactions_wallet_id_date 
ON wallet_transactions (wallet_id, created_at DESC) INCLUDE (type, amount, balance_after, status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_id_covering 
ON wallets (user_id) INCLUDE (currency, balance, is_primary, status);

-- Audit service optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_entity_id_type 
ON audit_events (entity_id, entity_type, timestamp DESC) INCLUDE (event_type, user_id);

-- Geographic search optimization for merchants
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_location_gist 
ON merchants USING GIST (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)) 
WHERE status = 'ACTIVE' AND latitude IS NOT NULL AND longitude IS NOT NULL;

-- Full-text search optimizations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_fts 
ON merchants USING GIN (to_tsvector('english', business_name || ' ' || business_category || ' ' || COALESCE(business_description, '')));

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_fts 
ON users USING GIN (to_tsvector('english', username || ' ' || email));

-- Trigram indexes for fuzzy matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_business_name_trgm 
ON merchants USING GIN (business_name gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_search_trgm 
ON users USING GIN ((username || ' ' || email) gin_trgm_ops);

-- Multi-column indexes for common query patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_status_date 
ON transactions (user_id, status, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_merchant_status_date 
ON transactions (merchant_id, status, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_payouts_merchant_status_date 
ON merchant_payouts (merchant_id, status, requested_at DESC);

-- Partial indexes for active records only
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_active_merchants_category 
ON merchants (business_category) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_active_users_created_at 
ON users (created_at DESC) WHERE status = 'ACTIVE';

-- Covering indexes for common read patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transaction_summary_covering 
ON transactions (user_id, created_at) 
INCLUDE (amount, status, currency, merchant_id) 
WHERE status IN ('COMPLETED', 'FAILED');

-- Foreign key optimization indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_devices_user_id 
ON user_devices (user_id) INCLUDE (device_type, is_trusted, last_used_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_webhooks_merchant_id 
ON merchant_webhooks (merchant_id) INCLUDE (url, events, is_active) WHERE is_active = true;

-- Time-based partitioning preparation indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_timestamp_hash 
ON audit_events (date_trunc('day', timestamp), entity_type);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_created_date 
ON notifications (date_trunc('day', created_at), user_id);

-- Comments for maintenance
COMMENT ON INDEX idx_user_profiles_user_id_covering IS 'Optimizes user profile lookups in UserService.getUserById()';
COMMENT ON INDEX idx_merchant_payment_methods_merchant_id_covering IS 'Optimizes payment method loading in MerchantService';
COMMENT ON INDEX idx_transaction_items_transaction_id_covering IS 'Prevents N+1 queries when loading transaction items';
COMMENT ON INDEX idx_merchants_fts IS 'Full-text search index for merchant search functionality';
COMMENT ON INDEX idx_merchants_location_gist IS 'Geographic proximity search for merchants';

-- Analyze tables after index creation
ANALYZE users;
ANALYZE user_profiles;
ANALYZE merchants;
ANALYZE transactions;
ANALYZE transaction_items;
ANALYZE merchant_payment_methods;
ANALYZE notifications;