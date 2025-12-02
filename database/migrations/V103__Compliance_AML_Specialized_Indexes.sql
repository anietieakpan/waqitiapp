-- =====================================================================
-- COMPLIANCE AND AML SPECIALIZED INDEXES
-- =====================================================================
-- Specialized indexes for Anti-Money Laundering (AML), Know Your Customer (KYC),
-- and regulatory compliance operations in the Waqiti fintech platform
-- 
-- Author: Claude Code Database Optimization
-- Created: 2025-09-28
-- Version: V103
-- 
-- REGULATORY COMPLIANCE FOCUS:
-- 1. Suspicious Activity Report (SAR) processing
-- 2. Currency Transaction Report (CTR) requirements
-- 3. High-value transaction monitoring ($10,000+ USD)
-- 4. KYC document verification workflows
-- 5. Transaction pattern analysis for AML
-- 6. Regulatory audit trail maintenance
-- =====================================================================

-- =====================================================================
-- SUSPICIOUS ACTIVITY REPORTING (SAR) INDEXES
-- =====================================================================

-- SAR filing deadline monitoring (30-day BSA requirement)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_filing_deadline_urgent
ON suspicious_activity_reports (filing_deadline, status, priority)
WHERE filing_deadline <= CURRENT_DATE + INTERVAL '7 days' 
AND status IN ('PENDING', 'IN_PROGRESS')
INCLUDE (sar_number, subject_user_id, activity_type, amount);

-- Customer SAR history for enhanced due diligence
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_customer_history
ON suspicious_activity_reports (subject_user_id, created_at DESC, status)
INCLUDE (activity_type, amount, currency, risk_score, sar_number)
WHERE status IN ('FILED', 'COMPLETED');

-- Multi-customer SAR correlation analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_correlation_analysis
ON suspicious_activity_reports (activity_type, created_at DESC, risk_score)
WHERE risk_score >= 7.0 AND status != 'CLOSED'
INCLUDE (subject_user_id, amount, currency, suspicious_patterns);

-- SAR assignment and workflow tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_workflow_tracking
ON suspicious_activity_reports (assigned_to, status, priority, created_at)
WHERE assigned_to IS NOT NULL AND status IN ('PENDING', 'UNDER_INVESTIGATION')
INCLUDE (subject_user_id, filing_deadline, investigation_notes);

-- High-risk SAR pattern detection
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sar_high_risk_patterns
ON suspicious_activity_reports USING gin(suspicious_patterns)
WHERE risk_score >= 8.0 AND status != 'CLOSED';

-- =====================================================================
-- CURRENCY TRANSACTION REPORTING (CTR) INDEXES
-- =====================================================================

-- CTR threshold monitoring ($10,000+ transactions)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_ctr_threshold
ON transactions (amount, currency, created_at DESC, source_wallet_id)
WHERE amount >= 10000 AND status = 'COMPLETED'
INCLUDE (target_wallet_id, transaction_type, reference_id);

-- Daily aggregate CTR monitoring per user
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_daily_aggregate_ctr
ON transactions (source_wallet_id, DATE(created_at), currency)
WHERE status = 'COMPLETED' AND amount > 0
INCLUDE (amount, transaction_type, target_wallet_id);

-- Payment processing CTR monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_ctr
ON payment_processing (amount, currency, created_at DESC)
WHERE amount >= 10000 AND status = 'COMPLETED'
INCLUDE (payment_id, processor_name, settlement_date);

-- Cross-border CTR tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_international_transfers_ctr
ON international_transfers (amount, from_currency, to_currency, created_at DESC)
WHERE amount >= 10000 OR (amount * exchange_rate) >= 10000
INCLUDE (sender_country, recipient_country, swift_code, purpose_code);

-- =====================================================================
-- KYC VERIFICATION AND MONITORING INDEXES
-- =====================================================================

-- KYC document expiration monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_expiration_alert
ON kyc_documents (expiry_date, user_id, document_type, status)
WHERE expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '60 days'
AND status = 'VERIFIED'
INCLUDE (document_number, verification_date, next_review_date);

-- Enhanced due diligence (EDD) trigger monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_edd_monitoring
ON users (kyc_status, status, created_at)
WHERE kyc_status IN ('ENHANCED_REQUIRED', 'UNDER_REVIEW') AND status = 'ACTIVE'
INCLUDE (username, email, external_id, risk_rating);

-- KYC verification queue optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_verification_queue
ON kyc_verifications (status, priority, submission_date)
WHERE status IN ('PENDING', 'UNDER_REVIEW')
INCLUDE (user_id, verification_level, assigned_officer, deadline)
ORDER BY priority DESC, submission_date ASC;

-- KYC document type coverage analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_document_coverage
ON kyc_documents (user_id, document_type, status)
WHERE status = 'VERIFIED'
INCLUDE (verification_date, expiry_date, document_number);

-- =====================================================================
-- AML TRANSACTION PATTERN ANALYSIS INDEXES
-- =====================================================================

-- Structuring detection (amounts just below reporting thresholds)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_structuring_detection
ON transactions (source_wallet_id, amount, created_at)
WHERE amount BETWEEN 9000 AND 9999 AND status = 'COMPLETED'
INCLUDE (target_wallet_id, transaction_type, description);

-- Rapid succession transaction monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_rapid_succession
ON transactions (source_wallet_id, created_at DESC)
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour' AND status = 'COMPLETED'
INCLUDE (amount, target_wallet_id, transaction_type);

-- Cross-account transfer pattern analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_cross_account_patterns
ON transactions (source_wallet_id, target_wallet_id, created_at DESC)
WHERE source_wallet_id != target_wallet_id AND status = 'COMPLETED'
INCLUDE (amount, currency, transaction_type, reference_id);

-- Round number transaction analysis (potential laundering indicator)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_round_amounts
ON transactions (amount, created_at DESC)
WHERE (amount % 1000 = 0 OR amount % 500 = 0) AND amount >= 1000 AND status = 'COMPLETED'
INCLUDE (source_wallet_id, target_wallet_id, transaction_type);

-- Dormant account reactivation monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_dormant_reactivation
ON wallets (user_id, updated_at, status)
WHERE updated_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
AND status = 'ACTIVE'
INCLUDE (balance, currency, wallet_type, previous_status);

-- =====================================================================
-- COMPLIANCE AUDIT TRAIL INDEXES
-- =====================================================================

-- Regulatory audit event tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_regulatory_trail
ON audit_events (entity_type, event_type, timestamp DESC, severity)
WHERE entity_type IN ('USER', 'TRANSACTION', 'PAYMENT', 'WALLET')
AND severity IN ('HIGH', 'CRITICAL')
INCLUDE (entity_id, user_id, description, correlation_id);

-- Compliance officer action tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_compliance_actions
ON audit_events (user_id, event_type, timestamp DESC)
WHERE event_type LIKE '%COMPLIANCE%' OR event_type LIKE '%SAR%' OR event_type LIKE '%KYC%'
INCLUDE (entity_type, entity_id, description, ip_address);

-- Data integrity verification for compliance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_verifications_compliance
ON audit_verifications (entity_type, integrity_score, created_at DESC)
WHERE integrity_score < 100.0 OR failed_events > 0
INCLUDE (entity_id, period_start, period_end, verification_type);

-- Compliance report generation tracking
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_reports_tracking
ON compliance_reports (report_type, status, period_end DESC)
WHERE report_type IN ('SAR', 'CTR', 'BSA', 'AML_SUMMARY')
INCLUDE (period_start, submission_deadline, created_by, file_path);

-- =====================================================================
-- GEOGRAPHIC AND CROSS-BORDER COMPLIANCE
-- =====================================================================

-- OFAC sanctions screening optimization
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_sanctions_screening
ON users (country, status, kyc_status)
WHERE status = 'ACTIVE'
INCLUDE (username, email, full_name, risk_rating);

-- Cross-border transaction monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_cross_border
ON transactions (metadata, amount, created_at DESC)
WHERE metadata ? 'country' AND amount >= 3000 AND status = 'COMPLETED'
INCLUDE (source_wallet_id, target_wallet_id, currency);

-- Wire transfer OFAC compliance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wire_transfers_ofac
ON wire_transfers (recipient_country, sender_country, amount, created_at DESC)
WHERE amount >= 3000
INCLUDE (recipient_name, sender_name, purpose_code, swift_code);

-- =====================================================================
-- HIGH-RISK CUSTOMER MONITORING
-- =====================================================================

-- Politically Exposed Person (PEP) monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_pep_monitoring
ON users (risk_rating, kyc_status, status)
WHERE risk_rating IN ('HIGH', 'VERY_HIGH') AND status = 'ACTIVE'
INCLUDE (username, email, country, pep_status, occupation);

-- High-risk jurisdiction monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_high_risk_jurisdiction
ON transactions (metadata, amount, created_at DESC)
WHERE metadata ? 'high_risk_country' AND status = 'COMPLETED'
INCLUDE (source_wallet_id, target_wallet_id, currency, amount);

-- Enhanced monitoring trigger events
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_enhanced_monitoring
ON users (enhanced_monitoring_flag, monitoring_start_date, status)
WHERE enhanced_monitoring_flag = true AND status = 'ACTIVE'
INCLUDE (username, monitoring_reason, assigned_officer, review_date);

-- =====================================================================
-- TRANSACTION VELOCITY AND LIMITS MONITORING
-- =====================================================================

-- Daily transaction velocity monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_daily_velocity
ON transactions (source_wallet_id, DATE(created_at))
WHERE status = 'COMPLETED' AND created_at >= CURRENT_DATE - INTERVAL '7 days'
INCLUDE (amount, currency, transaction_type, target_wallet_id);

-- Weekly transaction pattern analysis
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_weekly_patterns
ON transactions (source_wallet_id, DATE_TRUNC('week', created_at))
WHERE status = 'COMPLETED' AND created_at >= CURRENT_DATE - INTERVAL '30 days'
INCLUDE (amount, currency, transaction_type);

-- Monthly aggregation for regulatory reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_monthly_reporting
ON transactions (source_wallet_id, DATE_TRUNC('month', created_at), currency)
WHERE status = 'COMPLETED'
INCLUDE (amount, transaction_type, target_wallet_id);

-- =====================================================================
-- SPECIALIZED COMPLIANCE FUNCTIONS
-- =====================================================================

-- Function to identify potential structuring patterns
CREATE OR REPLACE FUNCTION detect_structuring_patterns(
    user_wallet_id UUID,
    days_back INTEGER DEFAULT 30
)
RETURNS TABLE(
    transaction_date DATE,
    transaction_count BIGINT,
    total_amount NUMERIC,
    avg_amount NUMERIC,
    suspicious_indicator TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        DATE(t.created_at) as transaction_date,
        COUNT(*) as transaction_count,
        SUM(t.amount) as total_amount,
        AVG(t.amount) as avg_amount,
        CASE 
            WHEN COUNT(*) > 5 AND AVG(t.amount) BETWEEN 9000 AND 9999 THEN 'HIGH_RISK_STRUCTURING'
            WHEN COUNT(*) > 3 AND AVG(t.amount) BETWEEN 4900 AND 5100 THEN 'MODERATE_RISK_STRUCTURING'
            WHEN SUM(t.amount) > 50000 AND COUNT(*) > 10 THEN 'HIGH_VOLUME_ACTIVITY'
            ELSE 'NORMAL_PATTERN'
        END as suspicious_indicator
    FROM transactions t
    WHERE t.source_wallet_id = user_wallet_id
    AND t.created_at >= CURRENT_DATE - INTERVAL '1 day' * days_back
    AND t.status = 'COMPLETED'
    GROUP BY DATE(t.created_at)
    HAVING COUNT(*) > 2
    ORDER BY transaction_date DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to generate CTR candidate transactions
CREATE OR REPLACE FUNCTION generate_ctr_candidates(
    report_date DATE DEFAULT CURRENT_DATE
)
RETURNS TABLE(
    wallet_id UUID,
    user_id UUID,
    total_amount NUMERIC,
    transaction_count BIGINT,
    currencies TEXT[],
    ctr_required BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        w.id as wallet_id,
        w.user_id,
        SUM(t.amount) as total_amount,
        COUNT(*) as transaction_count,
        ARRAY_AGG(DISTINCT t.currency) as currencies,
        (SUM(t.amount) > 10000) as ctr_required
    FROM wallets w
    JOIN transactions t ON w.id = t.source_wallet_id
    WHERE DATE(t.created_at) = report_date
    AND t.status = 'COMPLETED'
    GROUP BY w.id, w.user_id
    HAVING SUM(t.amount) > 10000
    ORDER BY SUM(t.amount) DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to check KYC document completeness
CREATE OR REPLACE FUNCTION check_kyc_completeness(
    check_user_id UUID
)
RETURNS TABLE(
    user_id UUID,
    required_documents TEXT[],
    completed_documents TEXT[],
    missing_documents TEXT[],
    compliance_score NUMERIC,
    next_review_date DATE
) AS $$
DECLARE
    required_docs TEXT[] := ARRAY['GOVERNMENT_ID', 'PROOF_OF_ADDRESS', 'INCOME_VERIFICATION'];
    completed_docs TEXT[];
    missing_docs TEXT[];
BEGIN
    -- Get completed documents
    SELECT ARRAY_AGG(document_type) INTO completed_docs
    FROM kyc_documents 
    WHERE user_id = check_user_id AND status = 'VERIFIED';
    
    -- Calculate missing documents
    SELECT ARRAY(
        SELECT unnest(required_docs)
        EXCEPT
        SELECT unnest(COALESCE(completed_docs, ARRAY[]::TEXT[]))
    ) INTO missing_docs;
    
    RETURN QUERY
    SELECT 
        check_user_id,
        required_docs,
        COALESCE(completed_docs, ARRAY[]::TEXT[]),
        COALESCE(missing_docs, ARRAY[]::TEXT[]),
        ROUND((ARRAY_LENGTH(completed_docs, 1)::NUMERIC / ARRAY_LENGTH(required_docs, 1)::NUMERIC) * 100, 2),
        CURRENT_DATE + INTERVAL '1 year';
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- COMPLIANCE MONITORING VIEWS
-- =====================================================================

-- View for daily SAR filing status
CREATE OR REPLACE VIEW v_daily_sar_filing_status AS
SELECT 
    DATE(created_at) as report_date,
    COUNT(*) as total_sars,
    COUNT(*) FILTER (WHERE status = 'FILED') as filed_sars,
    COUNT(*) FILTER (WHERE status = 'PENDING') as pending_sars,
    COUNT(*) FILTER (WHERE filing_deadline <= CURRENT_DATE) as overdue_sars,
    ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'FILED') / COUNT(*), 2) as filing_rate
FROM suspicious_activity_reports
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY report_date DESC;

-- View for CTR threshold monitoring
CREATE OR REPLACE VIEW v_ctr_threshold_monitoring AS
SELECT 
    DATE(created_at) as transaction_date,
    source_wallet_id,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    MAX(amount) as largest_amount,
    BOOL_OR(amount >= 10000) as ctr_threshold_reached
FROM transactions
WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
AND status = 'COMPLETED'
GROUP BY DATE(created_at), source_wallet_id
HAVING SUM(amount) >= 5000  -- Monitor transactions approaching CTR threshold
ORDER BY transaction_date DESC, total_amount DESC;

-- View for KYC expiration alerts
CREATE OR REPLACE VIEW v_kyc_expiration_alerts AS
SELECT 
    u.user_id,
    u.username,
    u.email,
    k.document_type,
    k.expiry_date,
    CURRENT_DATE - k.expiry_date as days_expired,
    CASE 
        WHEN k.expiry_date < CURRENT_DATE THEN 'EXPIRED'
        WHEN k.expiry_date <= CURRENT_DATE + INTERVAL '30 days' THEN 'EXPIRING_SOON'
        ELSE 'VALID'
    END as status
FROM users u
JOIN kyc_documents k ON u.id = k.user_id
WHERE k.status = 'VERIFIED'
AND k.expiry_date <= CURRENT_DATE + INTERVAL '60 days'
ORDER BY k.expiry_date ASC;

-- =====================================================================
-- PERFORMANCE OPTIMIZATION
-- =====================================================================

-- Update statistics for compliance tables
ANALYZE suspicious_activity_reports;
ANALYZE kyc_documents;
ANALYZE kyc_verifications;
ANALYZE compliance_reports;
ANALYZE audit_events;
ANALYZE audit_verifications;

-- Set appropriate fillfactor for compliance tables
ALTER TABLE suspicious_activity_reports SET (fillfactor = 95);
ALTER TABLE kyc_documents SET (fillfactor = 90);
ALTER TABLE compliance_reports SET (fillfactor = 95);
ALTER TABLE audit_events SET (fillfactor = 95);

-- Grant necessary permissions
GRANT EXECUTE ON FUNCTION detect_structuring_patterns(UUID, INTEGER) TO waqiti_app;
GRANT EXECUTE ON FUNCTION generate_ctr_candidates(DATE) TO waqiti_app;
GRANT EXECUTE ON FUNCTION check_kyc_completeness(UUID) TO waqiti_app;

GRANT SELECT ON v_daily_sar_filing_status TO waqiti_app;
GRANT SELECT ON v_ctr_threshold_monitoring TO waqiti_app;
GRANT SELECT ON v_kyc_expiration_alerts TO waqiti_app;

-- =====================================================================
-- COMPLETION SUMMARY
-- =====================================================================

DO $$
DECLARE
    compliance_indexes INTEGER;
    compliance_functions INTEGER;
    compliance_views INTEGER;
BEGIN
    SELECT COUNT(*) INTO compliance_indexes 
    FROM pg_indexes 
    WHERE schemaname = 'public' 
    AND (indexname LIKE '%sar_%' OR indexname LIKE '%ctr_%' OR indexname LIKE '%kyc_%' 
         OR indexname LIKE '%compliance_%' OR indexname LIKE '%aml_%');
    
    SELECT COUNT(*) INTO compliance_functions
    FROM pg_proc p
    JOIN pg_namespace n ON p.pronamespace = n.oid
    WHERE n.nspname = 'public'
    AND (p.proname LIKE '%structuring%' OR p.proname LIKE '%ctr_%' OR p.proname LIKE '%kyc_%');
    
    SELECT COUNT(*) INTO compliance_views
    FROM pg_views 
    WHERE schemaname = 'public'
    AND (viewname LIKE '%sar_%' OR viewname LIKE '%ctr_%' OR viewname LIKE '%kyc_%');
    
    RAISE NOTICE '========================================================';
    RAISE NOTICE 'COMPLIANCE AND AML SPECIALIZED INDEXES COMPLETE';
    RAISE NOTICE '========================================================';
    RAISE NOTICE 'Compliance-specific indexes created: %', compliance_indexes;
    RAISE NOTICE 'Compliance monitoring functions: %', compliance_functions;
    RAISE NOTICE 'Compliance monitoring views: %', compliance_views;
    RAISE NOTICE '';
    RAISE NOTICE 'REGULATORY COMPLIANCE AREAS OPTIMIZED:';
    RAISE NOTICE '✓ Suspicious Activity Report (SAR) processing';
    RAISE NOTICE '✓ Currency Transaction Report (CTR) monitoring';
    RAISE NOTICE '✓ KYC document verification and expiration';
    RAISE NOTICE '✓ AML transaction pattern detection';
    RAISE NOTICE '✓ Structuring and layering detection';
    RAISE NOTICE '✓ Cross-border transaction monitoring';
    RAISE NOTICE '✓ High-risk customer screening';
    RAISE NOTICE '✓ Regulatory audit trail maintenance';
    RAISE NOTICE '';
    RAISE NOTICE 'COMPLIANCE FUNCTIONS AVAILABLE:';
    RAISE NOTICE '• detect_structuring_patterns(wallet_id, days)';
    RAISE NOTICE '• generate_ctr_candidates(date)';
    RAISE NOTICE '• check_kyc_completeness(user_id)';
    RAISE NOTICE '';
    RAISE NOTICE 'MONITORING VIEWS CREATED:';
    RAISE NOTICE '• v_daily_sar_filing_status';
    RAISE NOTICE '• v_ctr_threshold_monitoring';
    RAISE NOTICE '• v_kyc_expiration_alerts';
    RAISE NOTICE '========================================================';
END $$;