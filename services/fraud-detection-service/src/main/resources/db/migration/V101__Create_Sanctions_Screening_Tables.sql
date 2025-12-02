-- OFAC Sanctions Screening Database Schema
-- Version: 101 (after V100__Fraud_Detection_Query_Optimization.sql)
-- Description: Comprehensive OFAC sanctions screening implementation
-- Compliance: 31 CFR Part 501 (OFAC Regulations)
-- Created: 2025-10-01

-- =============================================================================
-- Sanctions Check Records Table
-- =============================================================================
-- Stores all sanctions screening check results with comprehensive audit trail
-- and workflow management for compliance officers

CREATE TABLE sanctions_check_records (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Entity Identification
    user_id UUID,
    business_id UUID,
    transaction_id UUID,
    entity_type VARCHAR(50) NOT NULL CHECK (entity_type IN ('USER', 'BUSINESS', 'TRANSACTION_PARTY', 'BENEFICIAL_OWNER')),

    -- Check Details
    checked_name VARCHAR(500) NOT NULL,
    date_of_birth DATE,
    nationality VARCHAR(3), -- ISO 3166 alpha-3 country code
    place_of_birth VARCHAR(255),
    identification_number VARCHAR(255),
    address TEXT,

    -- Check Source
    check_source VARCHAR(50) NOT NULL CHECK (check_source IN ('REGISTRATION', 'KYC', 'TRANSACTION', 'PERIODIC_REVIEW', 'MANUAL', 'TRIGGERED')),

    -- Results
    match_found BOOLEAN NOT NULL DEFAULT false,
    match_count INTEGER NOT NULL DEFAULT 0,
    match_score DECIMAL(5,2) CHECK (match_score >= 0 AND match_score <= 100),
    match_details JSONB, -- Array of match detail objects

    -- Lists Checked
    ofac_checked BOOLEAN NOT NULL DEFAULT false,
    eu_checked BOOLEAN NOT NULL DEFAULT false,
    un_checked BOOLEAN NOT NULL DEFAULT false,
    uk_checked BOOLEAN NOT NULL DEFAULT false,

    -- Matched Lists
    matched_ofac BOOLEAN NOT NULL DEFAULT false,
    matched_eu BOOLEAN NOT NULL DEFAULT false,
    matched_un BOOLEAN NOT NULL DEFAULT false,
    matched_uk BOOLEAN NOT NULL DEFAULT false,

    -- Risk Assessment
    risk_level VARCHAR(20) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    risk_factors JSONB,

    -- Check Status & Workflow
    check_status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED' CHECK (check_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'MANUAL_REVIEW')),
    resolution VARCHAR(50) CHECK (resolution IN ('CLEARED', 'BLOCKED', 'ESCALATED', 'FALSE_POSITIVE', 'TRUE_POSITIVE', 'PENDING_INVESTIGATION')),
    requires_manual_review BOOLEAN NOT NULL DEFAULT false,

    -- Review & Resolution
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by UUID,
    review_notes TEXT,
    escalated_at TIMESTAMP WITH TIME ZONE,
    escalated_to UUID,
    escalation_reason TEXT,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_notes TEXT,

    -- Actions Taken
    blocked BOOLEAN NOT NULL DEFAULT false,
    blocked_at TIMESTAMP WITH TIME ZONE,
    blocked_by UUID,
    blocked_reason TEXT,
    cleared BOOLEAN NOT NULL DEFAULT false,
    cleared_at TIMESTAMP WITH TIME ZONE,
    cleared_by UUID,
    cleared_reason TEXT,

    -- SAR (Suspicious Activity Report) Filing
    sar_filed BOOLEAN NOT NULL DEFAULT false,
    sar_filing_date DATE,
    sar_reference_number VARCHAR(255),
    sar_filing_notes TEXT,

    -- Compliance & Reporting
    sanctions_list_version VARCHAR(100),
    fuzzy_matching_algorithms JSONB, -- Array of algorithms used
    confidence_scores JSONB, -- Scores from each algorithm
    next_review_date DATE,
    last_reviewed_date DATE,

    -- Transaction Context (if applicable)
    transaction_amount DECIMAL(19,4),
    transaction_currency VARCHAR(3),
    transaction_type VARCHAR(50),
    transaction_parties JSONB, -- Array of parties involved

    -- Performance Metrics
    check_duration_ms BIGINT,
    lists_download_time_ms BIGINT,
    matching_time_ms BIGINT,

    -- Idempotency
    idempotency_key VARCHAR(255) UNIQUE,

    -- Audit Trail (extends BaseEntity)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0, -- Optimistic locking

    -- Additional Metadata
    metadata JSONB,

    -- Constraints
    CONSTRAINT chk_entity_reference CHECK (
        (entity_type = 'USER' AND user_id IS NOT NULL) OR
        (entity_type = 'BUSINESS' AND business_id IS NOT NULL) OR
        (entity_type = 'TRANSACTION_PARTY' AND transaction_id IS NOT NULL) OR
        (entity_type = 'BENEFICIAL_OWNER')
    ),
    CONSTRAINT chk_resolution_dates CHECK (
        resolved_at IS NULL OR resolved_at >= created_at
    ),
    CONSTRAINT chk_review_dates CHECK (
        reviewed_at IS NULL OR reviewed_at >= created_at
    )
);

-- =============================================================================
-- Indexes for Performance Optimization
-- =============================================================================

-- Entity lookups
CREATE INDEX idx_sanctions_user_id ON sanctions_check_records(user_id) WHERE deleted = false;
CREATE INDEX idx_sanctions_business_id ON sanctions_check_records(business_id) WHERE deleted = false;
CREATE INDEX idx_sanctions_transaction_id ON sanctions_check_records(transaction_id) WHERE deleted = false;
CREATE INDEX idx_sanctions_entity_type ON sanctions_check_records(entity_type) WHERE deleted = false;

-- Workflow & Status
CREATE INDEX idx_sanctions_check_status ON sanctions_check_records(check_status) WHERE deleted = false;
CREATE INDEX idx_sanctions_resolution ON sanctions_check_records(resolution) WHERE deleted = false;
CREATE INDEX idx_sanctions_manual_review ON sanctions_check_records(requires_manual_review, check_status, risk_level DESC) WHERE requires_manual_review = true AND deleted = false;

-- Risk Assessment
CREATE INDEX idx_sanctions_risk_level ON sanctions_check_records(risk_level, created_at DESC) WHERE deleted = false;
CREATE INDEX idx_sanctions_match_found ON sanctions_check_records(match_found, risk_level) WHERE match_found = true AND deleted = false;
CREATE INDEX idx_sanctions_match_score ON sanctions_check_records(match_score DESC) WHERE match_score IS NOT NULL AND deleted = false;

-- Actions & Blocking
CREATE INDEX idx_sanctions_blocked ON sanctions_check_records(blocked, blocked_at DESC) WHERE blocked = true AND deleted = false;
CREATE INDEX idx_sanctions_cleared ON sanctions_check_records(cleared, cleared_at DESC) WHERE cleared = true AND deleted = false;

-- Compliance & Reporting
CREATE INDEX idx_sanctions_sar_filed ON sanctions_check_records(sar_filed, sar_filing_date) WHERE sar_filed = true AND deleted = false;
CREATE INDEX idx_sanctions_next_review ON sanctions_check_records(next_review_date) WHERE next_review_date IS NOT NULL AND deleted = false;
CREATE INDEX idx_sanctions_list_version ON sanctions_check_records(sanctions_list_version) WHERE deleted = false;

-- Temporal Queries
CREATE INDEX idx_sanctions_created_at ON sanctions_check_records(created_at DESC) WHERE deleted = false;
CREATE INDEX idx_sanctions_reviewed_at ON sanctions_check_records(reviewed_at DESC) WHERE reviewed_at IS NOT NULL AND deleted = false;
CREATE INDEX idx_sanctions_resolved_at ON sanctions_check_records(resolved_at DESC) WHERE resolved_at IS NOT NULL AND deleted = false;

-- High-Risk Unresolved (Critical Queries)
CREATE INDEX idx_sanctions_high_risk_unresolved ON sanctions_check_records(risk_level, resolution, created_at DESC)
    WHERE risk_level IN ('HIGH', 'CRITICAL') AND resolution IS NULL AND deleted = false;

-- Daily Statistics (Reporting)
CREATE INDEX idx_sanctions_daily_stats ON sanctions_check_records(DATE(created_at), check_status, risk_level) WHERE deleted = false;

-- Name Search (Full Text Search)
CREATE INDEX idx_sanctions_checked_name_trgm ON sanctions_check_records USING gin(checked_name gin_trgm_ops) WHERE deleted = false;

-- JSONB Indexes for Fast Queries
CREATE INDEX idx_sanctions_match_details ON sanctions_check_records USING gin(match_details) WHERE match_details IS NOT NULL;
CREATE INDEX idx_sanctions_risk_factors ON sanctions_check_records USING gin(risk_factors) WHERE risk_factors IS NOT NULL;

-- Composite Indexes for Complex Queries
CREATE INDEX idx_sanctions_user_status_date ON sanctions_check_records(user_id, check_status, created_at DESC) WHERE deleted = false;
CREATE INDEX idx_sanctions_source_status ON sanctions_check_records(check_source, check_status, created_at DESC) WHERE deleted = false;

-- Idempotency Index
CREATE UNIQUE INDEX idx_sanctions_idempotency_key ON sanctions_check_records(idempotency_key) WHERE idempotency_key IS NOT NULL AND deleted = false;

-- =============================================================================
-- Comments for Documentation
-- =============================================================================

COMMENT ON TABLE sanctions_check_records IS 'OFAC and international sanctions screening records with comprehensive audit trail and compliance workflow';

COMMENT ON COLUMN sanctions_check_records.entity_type IS 'Type of entity being screened: USER, BUSINESS, TRANSACTION_PARTY, BENEFICIAL_OWNER';
COMMENT ON COLUMN sanctions_check_records.check_source IS 'Source triggering the check: REGISTRATION, KYC, TRANSACTION, PERIODIC_REVIEW, MANUAL, TRIGGERED';
COMMENT ON COLUMN sanctions_check_records.match_score IS 'Overall match confidence score (0-100) from fuzzy matching algorithms';
COMMENT ON COLUMN sanctions_check_records.match_details IS 'JSONB array of detailed match information including list name, matched entry, algorithm scores';
COMMENT ON COLUMN sanctions_check_records.risk_level IS 'Assessed risk level: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN sanctions_check_records.resolution IS 'Manual review resolution: CLEARED, BLOCKED, ESCALATED, FALSE_POSITIVE, TRUE_POSITIVE, PENDING_INVESTIGATION';
COMMENT ON COLUMN sanctions_check_records.sar_filed IS 'Whether a Suspicious Activity Report was filed with FinCEN';
COMMENT ON COLUMN sanctions_check_records.sanctions_list_version IS 'Version of sanctions lists used for screening (for audit trail)';
COMMENT ON COLUMN sanctions_check_records.next_review_date IS 'Date when periodic re-screening is required';
COMMENT ON COLUMN sanctions_check_records.idempotency_key IS 'Unique key for duplicate prevention (hash of screening request)';

-- =============================================================================
-- Triggers for Automatic Timestamp Updates
-- =============================================================================

CREATE OR REPLACE FUNCTION update_sanctions_check_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sanctions_check_updated_at
    BEFORE UPDATE ON sanctions_check_records
    FOR EACH ROW
    EXECUTE FUNCTION update_sanctions_check_timestamp();

-- =============================================================================
-- Trigger for Automatic Blocked Status Update
-- =============================================================================

CREATE OR REPLACE FUNCTION update_sanctions_blocked_status()
RETURNS TRIGGER AS $$
BEGIN
    -- Automatically set blocked timestamp and enforce mutual exclusivity
    IF NEW.blocked = true AND OLD.blocked = false THEN
        NEW.blocked_at = CURRENT_TIMESTAMP;
        NEW.cleared = false;
        NEW.cleared_at = NULL;
    END IF;

    -- Automatically set cleared timestamp and enforce mutual exclusivity
    IF NEW.cleared = true AND OLD.cleared = false THEN
        NEW.cleared_at = CURRENT_TIMESTAMP;
        NEW.blocked = false;
        NEW.blocked_at = NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_sanctions_blocked_status
    BEFORE UPDATE ON sanctions_check_records
    FOR EACH ROW
    WHEN (OLD.blocked IS DISTINCT FROM NEW.blocked OR OLD.cleared IS DISTINCT FROM NEW.cleared)
    EXECUTE FUNCTION update_sanctions_blocked_status();

-- =============================================================================
-- Grant Permissions
-- =============================================================================

-- Grant appropriate permissions to fraud-detection service user
-- GRANT SELECT, INSERT, UPDATE ON sanctions_check_records TO fraud_detection_service;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO fraud_detection_service;

-- =============================================================================
-- Initial Data / Configuration (if needed)
-- =============================================================================

-- No initial data required - sanctions lists are fetched dynamically from OFAC API

-- =============================================================================
-- Performance Validation Queries
-- =============================================================================

-- Verify indexes are being used (run after data population):
-- EXPLAIN ANALYZE SELECT * FROM sanctions_check_records WHERE user_id = 'some-uuid' AND deleted = false;
-- EXPLAIN ANALYZE SELECT * FROM sanctions_check_records WHERE requires_manual_review = true ORDER BY risk_level DESC, created_at ASC;
-- EXPLAIN ANALYZE SELECT * FROM sanctions_check_records WHERE risk_level IN ('HIGH', 'CRITICAL') AND resolution IS NULL AND deleted = false;

-- =============================================================================
-- Migration Complete
-- =============================================================================
