-- Create audit severity enum
CREATE TYPE audit_severity AS ENUM (
    'LOW',
    'MEDIUM', 
    'HIGH',
    'CRITICAL'
);

-- Create compliance report type enum
CREATE TYPE compliance_report_type AS ENUM (
    'SAR',           -- Suspicious Activity Report
    'CTR',           -- Currency Transaction Report  
    'BSA',           -- Bank Secrecy Act
    'AML_SUMMARY',   -- Anti-Money Laundering Summary
    'KYC_REVIEW',    -- Know Your Customer Review
    'REGULATORY'     -- General Regulatory Report
);

-- Create compliance report status enum
CREATE TYPE compliance_report_status AS ENUM (
    'DRAFT',
    'PENDING_REVIEW',
    'APPROVED',
    'SUBMITTED',
    'REJECTED'
);

-- Main audit events table
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Event identification
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    
    -- User and session information
    user_id UUID,
    session_id VARCHAR(100),
    ip_address INET,
    user_agent TEXT,
    
    -- Event details
    severity audit_severity NOT NULL DEFAULT 'MEDIUM',
    description TEXT NOT NULL,
    event_data JSONB,
    tags TEXT[],
    
    -- Source and correlation
    source VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100),
    
    -- Integrity and chain
    integrity_hash VARCHAR(256),
    previous_event_hash VARCHAR(256),
    
    -- Timing
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Additional metadata
    metadata JSONB,
    
    -- Indexes for common searches
    CONSTRAINT idx_audit_events_entity CHECK (entity_id IS NOT NULL AND entity_type IS NOT NULL),
    CONSTRAINT idx_audit_events_event_type CHECK (event_type IS NOT NULL)
);

-- Compliance reports table
CREATE TABLE compliance_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type compliance_report_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status compliance_report_status NOT NULL DEFAULT 'DRAFT',
    
    -- Report period
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    
    -- Report content and metadata
    report_data JSONB NOT NULL,
    findings JSONB,
    recommendations TEXT[],
    
    -- File information
    file_path VARCHAR(500),
    file_size BIGINT,
    file_hash VARCHAR(256),
    
    -- Regulatory information
    regulatory_reference VARCHAR(100),
    submission_deadline DATE,
    submitted_at TIMESTAMP WITH TIME ZONE,
    submitted_by UUID,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0
);

-- Suspicious Activity Reports (SAR) table
CREATE TABLE suspicious_activity_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    compliance_report_id UUID REFERENCES compliance_reports(id),
    
    -- Transaction/Subject information
    transaction_id UUID,
    subject_user_id UUID,
    subject_name VARCHAR(255),
    subject_account VARCHAR(100),
    
    -- Suspicious activity details
    activity_type VARCHAR(100) NOT NULL,
    suspicious_patterns TEXT[] NOT NULL,
    description TEXT NOT NULL,
    amount DECIMAL(19,4),
    currency VARCHAR(3),
    
    -- Analysis
    risk_score DECIMAL(5,2),
    risk_factors TEXT[],
    investigation_notes TEXT,
    
    -- Status and workflow
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assigned_to UUID,
    
    -- Regulatory
    sar_number VARCHAR(50) UNIQUE,
    filing_deadline DATE,
    filed_at TIMESTAMP WITH TIME ZONE,
    filed_by UUID,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL
);

-- Audit trail verification table
CREATE TABLE audit_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_type VARCHAR(50) NOT NULL, -- FULL, PARTIAL, ENTITY_SPECIFIC
    
    -- Verification scope
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    
    -- Verification results
    total_events INTEGER NOT NULL,
    verified_events INTEGER NOT NULL,
    failed_events INTEGER NOT NULL,
    integrity_score DECIMAL(5,2) NOT NULL,
    
    -- Issues found
    integrity_issues JSONB,
    chain_breaks INTEGER DEFAULT 0,
    hash_mismatches INTEGER DEFAULT 0,
    
    -- Verification metadata
    verification_algorithm VARCHAR(50) NOT NULL,
    verification_duration_ms BIGINT,
    status VARCHAR(20) NOT NULL,
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- Archive log table
CREATE TABLE audit_archives (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    archive_date DATE NOT NULL,
    
    -- Archive details
    events_archived INTEGER NOT NULL,
    archive_location VARCHAR(500) NOT NULL,
    archive_format VARCHAR(20) NOT NULL DEFAULT 'JSON',
    compression_type VARCHAR(20),
    
    -- File information
    archive_size_bytes BIGINT,
    archive_hash VARCHAR(256),
    encryption_used BOOLEAN DEFAULT false,
    
    -- Retention information
    retention_period_days INTEGER NOT NULL,
    deletion_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL
);

-- Event aggregations for analytics (materialized view)
CREATE TABLE audit_event_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    summary_date DATE NOT NULL,
    entity_type VARCHAR(100),
    event_type VARCHAR(100),
    severity audit_severity,
    
    -- Counts
    event_count INTEGER NOT NULL DEFAULT 0,
    unique_users INTEGER DEFAULT 0,
    unique_entities INTEGER DEFAULT 0,
    
    -- Timing
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(summary_date, entity_type, event_type, severity)
);

-- Indexes for performance
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_events_user ON audit_events(user_id);
CREATE INDEX idx_audit_events_timestamp ON audit_events(timestamp);
CREATE INDEX idx_audit_events_event_type ON audit_events(event_type);
CREATE INDEX idx_audit_events_severity ON audit_events(severity);
CREATE INDEX idx_audit_events_correlation ON audit_events(correlation_id);
CREATE INDEX idx_audit_events_source ON audit_events(source);

-- Composite indexes for common queries
CREATE INDEX idx_audit_events_entity_timestamp ON audit_events(entity_type, entity_id, timestamp);
CREATE INDEX idx_audit_events_user_timestamp ON audit_events(user_id, timestamp);
CREATE INDEX idx_audit_events_type_timestamp ON audit_events(event_type, timestamp);

-- GIN indexes for JSONB columns
CREATE INDEX idx_audit_events_event_data ON audit_events USING GIN(event_data);
CREATE INDEX idx_audit_events_metadata ON audit_events USING GIN(metadata);
CREATE INDEX idx_audit_events_tags ON audit_events USING GIN(tags);

-- Indexes for compliance reports
CREATE INDEX idx_compliance_reports_type ON compliance_reports(report_type);
CREATE INDEX idx_compliance_reports_status ON compliance_reports(status);
CREATE INDEX idx_compliance_reports_period ON compliance_reports(period_start, period_end);
CREATE INDEX idx_compliance_reports_created_at ON compliance_reports(created_at);

-- Indexes for SARs
CREATE INDEX idx_sar_transaction ON suspicious_activity_reports(transaction_id);
CREATE INDEX idx_sar_user ON suspicious_activity_reports(subject_user_id);
CREATE INDEX idx_sar_status ON suspicious_activity_reports(status);
CREATE INDEX idx_sar_created_at ON suspicious_activity_reports(created_at);

-- Indexes for verifications
CREATE INDEX idx_audit_verifications_entity ON audit_verifications(entity_type, entity_id);
CREATE INDEX idx_audit_verifications_period ON audit_verifications(period_start, period_end);
CREATE INDEX idx_audit_verifications_created_at ON audit_verifications(created_at);

-- Partitioning for audit_events (by month)
-- Note: This would typically be set up programmatically
-- CREATE TABLE audit_events_y2024m01 PARTITION OF audit_events
-- FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- Update triggers
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_compliance_reports_updated_at 
    BEFORE UPDATE ON compliance_reports 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_suspicious_activity_reports_updated_at 
    BEFORE UPDATE ON suspicious_activity_reports 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to aggregate daily events
CREATE OR REPLACE FUNCTION aggregate_daily_events()
RETURNS VOID AS $$
BEGIN
    INSERT INTO audit_event_summary (summary_date, entity_type, event_type, severity, event_count, unique_users, unique_entities)
    SELECT 
        DATE(timestamp) as summary_date,
        entity_type,
        event_type,
        severity,
        COUNT(*) as event_count,
        COUNT(DISTINCT user_id) as unique_users,
        COUNT(DISTINCT entity_id) as unique_entities
    FROM audit_events 
    WHERE DATE(timestamp) = CURRENT_DATE - INTERVAL '1 day'
    GROUP BY DATE(timestamp), entity_type, event_type, severity
    ON CONFLICT (summary_date, entity_type, event_type, severity) 
    DO UPDATE SET 
        event_count = EXCLUDED.event_count,
        unique_users = EXCLUDED.unique_users,
        unique_entities = EXCLUDED.unique_entities;
END;
$$ language 'plpgsql';

-- Initial data
INSERT INTO compliance_reports (
    id, report_type, title, description, status, period_start, period_end, 
    report_data, created_by
) VALUES (
    gen_random_uuid(),
    'AML_SUMMARY',
    'Initial AML Summary Report',
    'Initial setup report for AML compliance tracking',
    'DRAFT',
    CURRENT_DATE - INTERVAL '30 days',
    CURRENT_DATE,
    '{"setup": true, "initial": true}',
    '00000000-0000-0000-0000-000000000000'
) ON CONFLICT DO NOTHING;