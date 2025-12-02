-- Performance optimization indexes for high-traffic queries
-- This migration adds specialized indexes for common query patterns

-- =====================================================================
-- COMPOSITE INDEXES FOR COMMON QUERY PATTERNS
-- =====================================================================

-- User search queries (admin dashboard)
CREATE INDEX IF NOT EXISTS idx_users_status_created_at ON users(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_kyc_status_updated_at ON users(kyc_status, updated_at DESC);

-- User authentication and lookup patterns
CREATE INDEX IF NOT EXISTS idx_users_email_status ON users(email, status) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_users_username_status ON users(username, status) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_users_phone_status ON users(phone_number, status) WHERE status = 'ACTIVE';

-- Full-text search optimization for user search
CREATE INDEX IF NOT EXISTS idx_users_search_gin ON users USING gin(to_tsvector('english', username || ' ' || email));

-- Risk assessment queries
CREATE INDEX IF NOT EXISTS idx_user_risk_level_created_at ON user_risk_assessments(user_id, risk_level, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_risk_high_critical ON user_risk_assessments(user_id, created_at DESC) 
    WHERE risk_level IN ('HIGH', 'CRITICAL');

-- Device security queries
CREATE INDEX IF NOT EXISTS idx_user_devices_active_last_used ON user_devices(user_id, last_used_at DESC) 
    WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_user_devices_fingerprint_active ON user_devices(device_fingerprint) 
    WHERE status = 'ACTIVE';

-- MFA queries
CREATE INDEX IF NOT EXISTS idx_mfa_user_active_method ON mfa_configurations(user_id, method) 
    WHERE status = 'ACTIVE';

-- Token revocation cleanup queries
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_cleanup ON revoked_tokens(expires_at) 
    WHERE expires_at < NOW();

-- =====================================================================
-- PARTIAL INDEXES FOR SPECIFIC CONDITIONS
-- =====================================================================

-- Active users only (most common queries)
CREATE INDEX IF NOT EXISTS idx_users_active_created_at ON users(created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_users_active_updated_at ON users(updated_at DESC) WHERE status = 'ACTIVE';

-- Pending KYC users (for compliance monitoring)
CREATE INDEX IF NOT EXISTS idx_users_pending_kyc ON users(created_at DESC) 
    WHERE kyc_status IN ('NOT_STARTED', 'IN_PROGRESS', 'PENDING_REVIEW');

-- Suspended/Closed users (for admin queries)
CREATE INDEX IF NOT EXISTS idx_users_inactive ON users(status, updated_at DESC) 
    WHERE status IN ('SUSPENDED', 'CLOSED');

-- High-risk users (for monitoring)
CREATE INDEX IF NOT EXISTS idx_users_high_risk ON user_risk_assessments(user_id, created_at DESC) 
    WHERE risk_level IN ('HIGH', 'CRITICAL');

-- =====================================================================
-- COVERING INDEXES FOR FREQUENTLY ACCESSED COLUMNS
-- =====================================================================

-- User list queries with basic info
CREATE INDEX IF NOT EXISTS idx_users_list_covering ON users(status, created_at DESC) 
    INCLUDE (id, username, email, phone_number, updated_at);

-- User profile queries
CREATE INDEX IF NOT EXISTS idx_user_profiles_covering ON user_profiles(user_id) 
    INCLUDE (first_name, last_name, date_of_birth, created_at);

-- =====================================================================
-- BTREE INDEXES FOR RANGE QUERIES
-- =====================================================================

-- Date range queries
CREATE INDEX IF NOT EXISTS idx_users_created_at_range ON users(created_at) WHERE created_at >= '2023-01-01';
CREATE INDEX IF NOT EXISTS idx_users_last_activity ON users(last_activity_at DESC) WHERE last_activity_at IS NOT NULL;

-- =====================================================================
-- FUNCTIONAL INDEXES
-- =====================================================================

-- Case-insensitive email search
CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users(lower(email));
CREATE INDEX IF NOT EXISTS idx_users_username_lower ON users(lower(username));

-- Fraud risk score ranges
CREATE INDEX IF NOT EXISTS idx_users_fraud_risk_score ON users(fraud_risk_score DESC) 
    WHERE fraud_risk_score > 0.0;

-- =====================================================================
-- TRIGRAM INDEXES FOR FUZZY SEARCH
-- =====================================================================

-- Enable pg_trgm extension if not already enabled
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Trigram indexes for fuzzy user search
CREATE INDEX IF NOT EXISTS idx_users_username_trgm ON users USING gin(username gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_email_trgm ON users USING gin(email gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_user_profiles_name_trgm ON user_profiles USING gin((first_name || ' ' || last_name) gin_trgm_ops);

-- =====================================================================
-- QUERY-SPECIFIC OPTIMIZATIONS
-- =====================================================================

-- Optimize the getAllUsersForAdmin query
CREATE INDEX IF NOT EXISTS idx_users_admin_search ON users(status, created_at DESC, updated_at DESC) 
    INCLUDE (id, username, email, phone_number, kyc_status);

-- Optimize user authentication flows
CREATE INDEX IF NOT EXISTS idx_users_auth_flow ON users(email, status, password_hash) 
    WHERE status = 'ACTIVE';

-- Optimize KYC verification queries
CREATE INDEX IF NOT EXISTS idx_users_kyc_verification ON users(kyc_status, updated_at DESC) 
    INCLUDE (id, username, email);

-- =====================================================================
-- MAINTENANCE AND MONITORING INDEXES
-- =====================================================================

-- Index for finding users that need cleanup
CREATE INDEX IF NOT EXISTS idx_users_cleanup ON users(status, updated_at) 
    WHERE status = 'CLOSED' AND updated_at < NOW() - INTERVAL '1 year';

-- Index for inactive users
CREATE INDEX IF NOT EXISTS idx_users_inactive_cleanup ON users(last_activity_at) 
    WHERE last_activity_at < NOW() - INTERVAL '6 months' AND status = 'ACTIVE';

-- Index for expired tokens cleanup
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expired ON revoked_tokens(expires_at) 
    WHERE expires_at < NOW();

-- =====================================================================
-- STATISTICS AND QUERY PLANNER HINTS
-- =====================================================================

-- Update statistics for better query planning
ANALYZE users;
ANALYZE user_profiles;
ANALYZE kyc_documents;
ANALYZE user_risk_assessments;
ANALYZE user_devices;
ANALYZE mfa_configurations;
ANALYZE revoked_tokens;

-- Set statistics target for heavily queried columns
ALTER TABLE users ALTER COLUMN status SET STATISTICS 1000;
ALTER TABLE users ALTER COLUMN kyc_status SET STATISTICS 1000;
ALTER TABLE users ALTER COLUMN email SET STATISTICS 1000;
ALTER TABLE users ALTER COLUMN username SET STATISTICS 1000;
ALTER TABLE users ALTER COLUMN created_at SET STATISTICS 1000;

-- =====================================================================
-- COMMENTS FOR MAINTENANCE
-- =====================================================================

COMMENT ON INDEX idx_users_status_created_at IS 'Optimizes admin user listing by status';
COMMENT ON INDEX idx_users_search_gin IS 'Full-text search index for user search functionality';
COMMENT ON INDEX idx_users_active_created_at IS 'Partial index for active users only';
COMMENT ON INDEX idx_users_list_covering IS 'Covering index for user list API endpoints';
COMMENT ON INDEX idx_users_email_lower IS 'Case-insensitive email lookups';
COMMENT ON INDEX idx_users_username_trgm IS 'Trigram index for fuzzy username search';
COMMENT ON INDEX idx_users_admin_search IS 'Optimized for getAllUsersForAdmin query';