-- Compliance Audit Trail Migration
-- Creates immutable audit trail for all compliance decisions with cryptographic integrity

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Main audit trail table
CREATE TABLE IF NOT EXISTS compliance_audit_trail (
    db_id BIGSERIAL PRIMARY KEY,
    id UUID NOT NULL UNIQUE DEFAULT uuid_generate_v4(),
    transaction_id VARCHAR(100) NOT NULL,
    decision_id UUID,
    decision_type VARCHAR(50),
    decision VARCHAR(50),
    risk_score INTEGER,
    action_type VARCHAR(50),
    action VARCHAR(100),
    check_type VARCHAR(50),
    check_result VARCHAR(50),
    performed_by VARCHAR(100) NOT NULL,
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,
    justification TEXT,
    context JSONB,
    review_details JSONB,
    metadata JSONB,
    
    -- Network/session info
    ip_address INET,
    user_agent TEXT,
    session_id VARCHAR(100),
    execution_time_ms BIGINT,
    
    -- Chain of custody for integrity
    previous_entry_id UUID REFERENCES compliance_audit_trail(id),
    previous_hash VARCHAR(500),
    integrity_hash VARCHAR(500) NOT NULL UNIQUE,
    
    -- Override tracking
    original_decision VARCHAR(50),
    override_decision VARCHAR(50),
    override_reason TEXT,
    approval_ticket VARCHAR(100),
    
    -- Flags
    is_critical_action BOOLEAN DEFAULT FALSE,
    requires_second_review BOOLEAN DEFAULT FALSE,
    second_review_completed BOOLEAN DEFAULT FALSE,
    second_reviewer_id VARCHAR(100),
    second_review_at TIMESTAMP,
    
    -- Error tracking
    error TEXT,
    
    -- Immutability constraints
    CONSTRAINT check_risk_score CHECK (risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 100)),
    CONSTRAINT check_performed_at CHECK (performed_at <= CURRENT_TIMESTAMP),
    CONSTRAINT check_decision_values CHECK (decision IN ('APPROVED', 'REJECTED', 'PENDING', 'SUSPICIOUS', 'BLOCKED', 'MANUAL_REVIEW')),
    CONSTRAINT check_action_values CHECK (action_type IS NULL OR action_type IN ('DECISION', 'MANUAL_REVIEW', 'SYSTEM_OVERRIDE', 'CHECK_EXECUTION'))
);

-- Rules fired for each audit entry
CREATE TABLE IF NOT EXISTS compliance_audit_rules_fired (
    audit_id UUID NOT NULL REFERENCES compliance_audit_trail(id) ON DELETE CASCADE,
    rule_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (audit_id, rule_name)
);

-- Flags raised for each audit entry
CREATE TABLE IF NOT EXISTS compliance_audit_flags_raised (
    audit_id UUID NOT NULL REFERENCES compliance_audit_trail(id) ON DELETE CASCADE,
    flag_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (audit_id, flag_name)
);

-- Audit trail summary/statistics table for reporting
CREATE TABLE IF NOT EXISTS compliance_audit_summary (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    date DATE NOT NULL,
    decision_type VARCHAR(50),
    total_entries INTEGER DEFAULT 0,
    approved_count INTEGER DEFAULT 0,
    rejected_count INTEGER DEFAULT 0,
    pending_count INTEGER DEFAULT 0,
    manual_review_count INTEGER DEFAULT 0,
    system_override_count INTEGER DEFAULT 0,
    avg_risk_score DECIMAL(5,2),
    high_risk_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_summary_date_type UNIQUE(date, decision_type)
);

-- Indexes for performance
CREATE INDEX idx_audit_transaction ON compliance_audit_trail(transaction_id);
CREATE INDEX idx_audit_performed_at ON compliance_audit_trail(performed_at);
CREATE INDEX idx_audit_performed_by ON compliance_audit_trail(performed_by);
CREATE INDEX idx_audit_decision_type ON compliance_audit_trail(decision_type);
CREATE INDEX idx_audit_risk_score ON compliance_audit_trail(risk_score) WHERE risk_score IS NOT NULL;
CREATE UNIQUE INDEX idx_audit_integrity_hash ON compliance_audit_trail(integrity_hash);
CREATE INDEX idx_audit_chain ON compliance_audit_trail(previous_entry_id) WHERE previous_entry_id IS NOT NULL;
CREATE INDEX idx_audit_critical ON compliance_audit_trail(is_critical_action) WHERE is_critical_action = TRUE;
CREATE INDEX idx_audit_pending_review ON compliance_audit_trail(requires_second_review, second_review_completed) 
    WHERE requires_second_review = TRUE AND second_review_completed = FALSE;

-- Indexes for summary table
CREATE INDEX idx_audit_summary_date ON compliance_audit_summary(date);
CREATE INDEX idx_audit_summary_type ON compliance_audit_summary(decision_type);

-- Function to prevent updates to audit trail (immutability)
CREATE OR REPLACE FUNCTION prevent_audit_updates()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Audit trail entries are immutable and cannot be updated';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Audit trail entries cannot be deleted';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to enforce immutability
CREATE TRIGGER trigger_prevent_audit_updates
    BEFORE UPDATE OR DELETE ON compliance_audit_trail
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_updates();

-- Function to update audit summary statistics
CREATE OR REPLACE FUNCTION update_audit_summary()
RETURNS TRIGGER AS $$
BEGIN
    -- Update daily statistics when new audit entry is created
    INSERT INTO compliance_audit_summary (
        date,
        decision_type,
        total_entries,
        approved_count,
        rejected_count,
        pending_count,
        manual_review_count,
        system_override_count,
        high_risk_count
    ) VALUES (
        CURRENT_DATE,
        COALESCE(NEW.decision_type, 'UNKNOWN'),
        1,
        CASE WHEN NEW.decision = 'APPROVED' THEN 1 ELSE 0 END,
        CASE WHEN NEW.decision = 'REJECTED' THEN 1 ELSE 0 END,
        CASE WHEN NEW.decision = 'PENDING' THEN 1 ELSE 0 END,
        CASE WHEN NEW.action_type = 'MANUAL_REVIEW' THEN 1 ELSE 0 END,
        CASE WHEN NEW.action_type = 'SYSTEM_OVERRIDE' THEN 1 ELSE 0 END,
        CASE WHEN NEW.risk_score > 80 THEN 1 ELSE 0 END
    )
    ON CONFLICT (date, decision_type) DO UPDATE SET
        total_entries = compliance_audit_summary.total_entries + 1,
        approved_count = compliance_audit_summary.approved_count + 
            CASE WHEN NEW.decision = 'APPROVED' THEN 1 ELSE 0 END,
        rejected_count = compliance_audit_summary.rejected_count + 
            CASE WHEN NEW.decision = 'REJECTED' THEN 1 ELSE 0 END,
        pending_count = compliance_audit_summary.pending_count + 
            CASE WHEN NEW.decision = 'PENDING' THEN 1 ELSE 0 END,
        manual_review_count = compliance_audit_summary.manual_review_count + 
            CASE WHEN NEW.action_type = 'MANUAL_REVIEW' THEN 1 ELSE 0 END,
        system_override_count = compliance_audit_summary.system_override_count + 
            CASE WHEN NEW.action_type = 'SYSTEM_OVERRIDE' THEN 1 ELSE 0 END,
        high_risk_count = compliance_audit_summary.high_risk_count + 
            CASE WHEN NEW.risk_score > 80 THEN 1 ELSE 0 END,
        updated_at = CURRENT_TIMESTAMP;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to update summary statistics
CREATE TRIGGER trigger_update_audit_summary
    AFTER INSERT ON compliance_audit_trail
    FOR EACH ROW
    EXECUTE FUNCTION update_audit_summary();

-- Function to validate chain integrity
CREATE OR REPLACE FUNCTION validate_chain_integrity()
RETURNS TRIGGER AS $$
DECLARE
    prev_entry RECORD;
BEGIN
    -- If this entry references a previous entry, validate the chain
    IF NEW.previous_entry_id IS NOT NULL THEN
        SELECT * INTO prev_entry 
        FROM compliance_audit_trail 
        WHERE id = NEW.previous_entry_id;
        
        -- Check if previous entry exists
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Referenced previous entry % does not exist', NEW.previous_entry_id;
        END IF;
        
        -- Validate previous hash matches
        IF prev_entry.integrity_hash != NEW.previous_hash THEN
            RAISE EXCEPTION 'Chain integrity violation: previous hash mismatch';
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to validate chain integrity
CREATE TRIGGER trigger_validate_chain_integrity
    BEFORE INSERT ON compliance_audit_trail
    FOR EACH ROW
    EXECUTE FUNCTION validate_chain_integrity();

-- Function to detect suspicious patterns
CREATE OR REPLACE FUNCTION detect_suspicious_patterns()
RETURNS TRIGGER AS $$
DECLARE
    recent_override_count INTEGER;
    recent_high_risk_count INTEGER;
BEGIN
    -- Only check for system overrides and high-risk decisions
    IF NEW.action_type = 'SYSTEM_OVERRIDE' OR (NEW.risk_score IS NOT NULL AND NEW.risk_score > 90) THEN
        
        -- Check for multiple recent overrides by same user
        IF NEW.action_type = 'SYSTEM_OVERRIDE' THEN
            SELECT COUNT(*) INTO recent_override_count
            FROM compliance_audit_trail
            WHERE performed_by = NEW.performed_by
                AND action_type = 'SYSTEM_OVERRIDE'
                AND performed_at > CURRENT_TIMESTAMP - INTERVAL '24 hours';
            
            -- Alert if more than 3 overrides in 24 hours
            IF recent_override_count >= 3 THEN
                -- Insert alert record (this would typically publish to a monitoring system)
                INSERT INTO compliance_alerts (
                    alert_type,
                    alert_message,
                    user_id,
                    created_at
                ) VALUES (
                    'EXCESSIVE_OVERRIDES',
                    'User ' || NEW.performed_by || ' has performed ' || recent_override_count || ' overrides in 24 hours',
                    NEW.performed_by,
                    CURRENT_TIMESTAMP
                ) ON CONFLICT DO NOTHING;
            END IF;
        END IF;
        
        -- Check for high-risk pattern
        IF NEW.risk_score > 90 THEN
            SELECT COUNT(*) INTO recent_high_risk_count
            FROM compliance_audit_trail
            WHERE transaction_id = NEW.transaction_id
                AND risk_score > 90
                AND performed_at > CURRENT_TIMESTAMP - INTERVAL '1 hour';
            
            IF recent_high_risk_count >= 2 THEN
                INSERT INTO compliance_alerts (
                    alert_type,
                    alert_message,
                    transaction_id,
                    created_at
                ) VALUES (
                    'REPEATED_HIGH_RISK',
                    'Transaction ' || NEW.transaction_id || ' has multiple high-risk decisions',
                    NEW.transaction_id,
                    CURRENT_TIMESTAMP
                ) ON CONFLICT DO NOTHING;
            END IF;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for suspicious pattern detection
CREATE TRIGGER trigger_detect_suspicious_patterns
    AFTER INSERT ON compliance_audit_trail
    FOR EACH ROW
    EXECUTE FUNCTION detect_suspicious_patterns();

-- Compliance alerts table (for the pattern detection function)
CREATE TABLE IF NOT EXISTS compliance_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_type VARCHAR(50) NOT NULL,
    alert_message TEXT NOT NULL,
    transaction_id VARCHAR(100),
    user_id VARCHAR(100),
    resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    
    CONSTRAINT unique_alert_per_transaction_type UNIQUE(transaction_id, alert_type, DATE(created_at))
);

CREATE INDEX idx_alerts_type ON compliance_alerts(alert_type);
CREATE INDEX idx_alerts_resolved ON compliance_alerts(resolved) WHERE resolved = FALSE;
CREATE INDEX idx_alerts_created ON compliance_alerts(created_at);

-- Function to clean up old audit entries (for data retention)
CREATE OR REPLACE FUNCTION cleanup_old_audit_entries()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    retention_date DATE;
BEGIN
    -- Calculate retention date (default 7 years as per compliance requirements)
    retention_date := CURRENT_DATE - INTERVAL '7 years';
    
    -- Archive entries older than retention period to separate table
    INSERT INTO compliance_audit_archive 
    SELECT * FROM compliance_audit_trail 
    WHERE performed_at::date < retention_date;
    
    -- Delete from main table
    DELETE FROM compliance_audit_trail 
    WHERE performed_at::date < retention_date;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Archive table for old audit entries
CREATE TABLE IF NOT EXISTS compliance_audit_archive (
    LIKE compliance_audit_trail INCLUDING ALL
);

-- Add comments for documentation
COMMENT ON TABLE compliance_audit_trail IS 'Immutable audit trail for all compliance decisions with cryptographic integrity';
COMMENT ON COLUMN compliance_audit_trail.integrity_hash IS 'HMAC-SHA256 hash for tamper detection';
COMMENT ON COLUMN compliance_audit_trail.previous_hash IS 'Hash of previous entry for chain integrity';
COMMENT ON TABLE compliance_audit_summary IS 'Daily statistics summary for compliance audit trail';
COMMENT ON TABLE compliance_alerts IS 'Automated alerts for suspicious compliance patterns';

-- Grant permissions (adjust based on your database users)
-- GRANT SELECT, INSERT ON compliance_audit_trail TO compliance_service;
-- GRANT SELECT, INSERT, UPDATE ON compliance_audit_summary TO compliance_service;
-- GRANT SELECT ON compliance_audit_rules_fired, compliance_audit_flags_raised TO compliance_service;
-- GRANT SELECT, INSERT, UPDATE ON compliance_alerts TO compliance_service;