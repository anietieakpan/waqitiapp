-- KYC Document Verification Tables
-- This migration creates tables for document verification workflow

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Document verifications table
CREATE TABLE IF NOT EXISTS document_verifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    document_key VARCHAR(500),
    quality_score DECIMAL(3,2),
    authenticity_score DECIMAL(3,2),
    data_match_score DECIMAL(3,2),
    fraud_score DECIMAL(3,2),
    final_score DECIMAL(3,2),
    extracted_data JSONB,
    metadata JSONB,
    decision_reason VARCHAR(500),
    reviewer_id UUID,
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    expires_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    CONSTRAINT check_scores CHECK (
        (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 1)) AND
        (authenticity_score IS NULL OR (authenticity_score >= 0 AND authenticity_score <= 1)) AND
        (data_match_score IS NULL OR (data_match_score >= 0 AND data_match_score <= 1)) AND
        (fraud_score IS NULL OR (fraud_score >= 0 AND fraud_score <= 1)) AND
        (final_score IS NULL OR (final_score >= 0 AND final_score <= 1))
    )
);

-- Indexes for document_verifications
CREATE INDEX idx_doc_verification_user ON document_verifications(user_id);
CREATE INDEX idx_doc_verification_status ON document_verifications(status);
CREATE INDEX idx_doc_verification_created ON document_verifications(created_at);
CREATE INDEX idx_doc_verification_type ON document_verifications(document_type);
CREATE INDEX idx_doc_verification_reviewer ON document_verifications(reviewer_id);
CREATE INDEX idx_doc_verification_expires ON document_verifications(expires_at);
CREATE INDEX idx_doc_verification_fraud_score ON document_verifications(fraud_score) WHERE fraud_score > 0.5;

-- Verification documents table (actual document storage metadata)
CREATE TABLE IF NOT EXISTS verification_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_key VARCHAR(500) NOT NULL UNIQUE,
    file_name VARCHAR(255),
    file_size BIGINT,
    content_type VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    checksum VARCHAR(64),
    encryption_key_id VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,
    expires_at TIMESTAMP,
    archived_at TIMESTAMP,
    last_accessed_at TIMESTAMP,
    scheduled_deletion_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Indexes for verification_documents
CREATE INDEX idx_ver_doc_user ON verification_documents(user_id);
CREATE INDEX idx_ver_doc_type ON verification_documents(document_type);
CREATE INDEX idx_ver_doc_status ON verification_documents(status);
CREATE UNIQUE INDEX idx_ver_doc_key ON verification_documents(document_key);
CREATE INDEX idx_ver_doc_uploaded ON verification_documents(uploaded_at);
CREATE INDEX idx_ver_doc_expires ON verification_documents(expires_at);
CREATE INDEX idx_ver_doc_scheduled_deletion ON verification_documents(scheduled_deletion_at) 
    WHERE scheduled_deletion_at IS NOT NULL;

-- Document verification audit log
CREATE TABLE IF NOT EXISTS document_verification_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    verification_id UUID NOT NULL REFERENCES document_verifications(id),
    action VARCHAR(50) NOT NULL,
    performed_by UUID,
    old_status VARCHAR(30),
    new_status VARCHAR(30),
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for audit log
CREATE INDEX idx_doc_audit_verification ON document_verification_audit(verification_id);
CREATE INDEX idx_doc_audit_performed_by ON document_verification_audit(performed_by);
CREATE INDEX idx_doc_audit_created ON document_verification_audit(created_at);

-- Document verification statistics table (for reporting)
CREATE TABLE IF NOT EXISTS document_verification_stats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    date DATE NOT NULL,
    document_type VARCHAR(50),
    total_submissions INTEGER DEFAULT 0,
    total_verified INTEGER DEFAULT 0,
    total_rejected INTEGER DEFAULT 0,
    total_pending_review INTEGER DEFAULT 0,
    total_failed INTEGER DEFAULT 0,
    avg_processing_time_seconds DECIMAL(10,2),
    avg_quality_score DECIMAL(3,2),
    avg_authenticity_score DECIMAL(3,2),
    avg_fraud_score DECIMAL(3,2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_stats_date_type UNIQUE(date, document_type)
);

-- Index for statistics
CREATE INDEX idx_doc_stats_date ON document_verification_stats(date);
CREATE INDEX idx_doc_stats_type ON document_verification_stats(document_type);

-- Suspicious patterns table (for fraud detection)
CREATE TABLE IF NOT EXISTS document_suspicious_patterns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    pattern_type VARCHAR(50) NOT NULL,
    pattern_details JSONB NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    verification_ids UUID[] NOT NULL,
    first_detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    occurrence_count INTEGER DEFAULT 1,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by UUID,
    resolution_notes TEXT
);

-- Indexes for suspicious patterns
CREATE INDEX idx_suspicious_user ON document_suspicious_patterns(user_id);
CREATE INDEX idx_suspicious_type ON document_suspicious_patterns(pattern_type);
CREATE INDEX idx_suspicious_risk ON document_suspicious_patterns(risk_level);
CREATE INDEX idx_suspicious_resolved ON document_suspicious_patterns(resolved);

-- Function to update document verification statistics
CREATE OR REPLACE FUNCTION update_document_verification_stats()
RETURNS TRIGGER AS $$
BEGIN
    -- Update statistics when verification is completed
    IF (NEW.status IN ('VERIFIED', 'REJECTED', 'FAILED') AND 
        (OLD.status IS NULL OR OLD.status NOT IN ('VERIFIED', 'REJECTED', 'FAILED'))) THEN
        
        INSERT INTO document_verification_stats (
            date,
            document_type,
            total_submissions,
            total_verified,
            total_rejected,
            total_failed
        ) VALUES (
            CURRENT_DATE,
            NEW.document_type,
            1,
            CASE WHEN NEW.status = 'VERIFIED' THEN 1 ELSE 0 END,
            CASE WHEN NEW.status = 'REJECTED' THEN 1 ELSE 0 END,
            CASE WHEN NEW.status = 'FAILED' THEN 1 ELSE 0 END
        )
        ON CONFLICT (date, document_type) DO UPDATE SET
            total_submissions = document_verification_stats.total_submissions + 1,
            total_verified = document_verification_stats.total_verified + 
                CASE WHEN NEW.status = 'VERIFIED' THEN 1 ELSE 0 END,
            total_rejected = document_verification_stats.total_rejected + 
                CASE WHEN NEW.status = 'REJECTED' THEN 1 ELSE 0 END,
            total_failed = document_verification_stats.total_failed + 
                CASE WHEN NEW.status = 'FAILED' THEN 1 ELSE 0 END,
            updated_at = CURRENT_TIMESTAMP;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for statistics update
CREATE TRIGGER trigger_update_document_stats
    AFTER INSERT OR UPDATE ON document_verifications
    FOR EACH ROW
    EXECUTE FUNCTION update_document_verification_stats();

-- Function to detect suspicious patterns
CREATE OR REPLACE FUNCTION detect_document_suspicious_patterns()
RETURNS TRIGGER AS $$
DECLARE
    recent_failure_count INTEGER;
    high_fraud_count INTEGER;
BEGIN
    -- Only check on rejected or high fraud score verifications
    IF NEW.status = 'REJECTED' OR (NEW.fraud_score IS NOT NULL AND NEW.fraud_score > 0.7) THEN
        
        -- Check for multiple recent failures
        SELECT COUNT(*) INTO recent_failure_count
        FROM document_verifications
        WHERE user_id = NEW.user_id
            AND status IN ('REJECTED', 'FAILED')
            AND created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours';
        
        IF recent_failure_count >= 3 THEN
            INSERT INTO document_suspicious_patterns (
                user_id,
                pattern_type,
                pattern_details,
                risk_level,
                verification_ids
            ) VALUES (
                NEW.user_id,
                'MULTIPLE_FAILURES',
                jsonb_build_object(
                    'failure_count', recent_failure_count,
                    'period_hours', 24
                ),
                'HIGH',
                ARRAY[NEW.id]
            )
            ON CONFLICT DO NOTHING;
        END IF;
        
        -- Check for high fraud scores
        SELECT COUNT(*) INTO high_fraud_count
        FROM document_verifications
        WHERE user_id = NEW.user_id
            AND fraud_score > 0.7
            AND created_at > CURRENT_TIMESTAMP - INTERVAL '7 days';
        
        IF high_fraud_count >= 2 THEN
            INSERT INTO document_suspicious_patterns (
                user_id,
                pattern_type,
                pattern_details,
                risk_level,
                verification_ids
            ) VALUES (
                NEW.user_id,
                'HIGH_FRAUD_SCORE',
                jsonb_build_object(
                    'fraud_count', high_fraud_count,
                    'avg_fraud_score', NEW.fraud_score
                ),
                'CRITICAL',
                ARRAY[NEW.id]
            )
            ON CONFLICT DO NOTHING;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for suspicious pattern detection
CREATE TRIGGER trigger_detect_suspicious_patterns
    AFTER INSERT OR UPDATE ON document_verifications
    FOR EACH ROW
    EXECUTE FUNCTION detect_document_suspicious_patterns();

-- Add comments for documentation
COMMENT ON TABLE document_verifications IS 'Stores KYC document verification processes and results';
COMMENT ON TABLE verification_documents IS 'Stores metadata for uploaded verification documents';
COMMENT ON TABLE document_verification_audit IS 'Audit trail for document verification actions';
COMMENT ON TABLE document_verification_stats IS 'Aggregated statistics for document verifications';
COMMENT ON TABLE document_suspicious_patterns IS 'Detected suspicious patterns in document verifications';

-- Grant permissions (adjust based on your database users)
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO kyc_service;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO kyc_service;