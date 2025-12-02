-- Fraud Service Initial Schema
-- Created: 2025-09-27
-- Description: Fraud detection case management and investigation schema

CREATE TABLE IF NOT EXISTS fraud_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id VARCHAR(100) UNIQUE NOT NULL,
    case_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    customer_id UUID NOT NULL,
    account_id UUID,
    transaction_id VARCHAR(100),
    amount DECIMAL(15, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    fraud_type VARCHAR(50) NOT NULL,
    detection_method VARCHAR(50) NOT NULL,
    detection_source VARCHAR(50) NOT NULL,
    risk_score DECIMAL(5, 4) NOT NULL,
    confidence_level DECIMAL(5, 4),
    triggered_rules TEXT[],
    indicators JSONB,
    description TEXT NOT NULL,
    assigned_to VARCHAR(100),
    assigned_at TIMESTAMP,
    priority VARCHAR(20) DEFAULT 'NORMAL',
    due_date DATE,
    investigation_started_at TIMESTAMP,
    investigation_completed_at TIMESTAMP,
    resolution VARCHAR(50),
    resolution_date DATE,
    resolution_notes TEXT,
    actions_taken TEXT[],
    false_positive BOOLEAN DEFAULT FALSE,
    true_fraud BOOLEAN DEFAULT FALSE,
    amount_recovered DECIMAL(15, 2),
    amount_written_off DECIMAL(15, 2),
    law_enforcement_notified BOOLEAN DEFAULT FALSE,
    law_enforcement_case_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fraud_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    case_id VARCHAR(100),
    alert_type VARCHAR(50) NOT NULL,
    alert_source VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    customer_id UUID NOT NULL,
    account_id UUID,
    transaction_id VARCHAR(100),
    risk_score DECIMAL(5, 4) NOT NULL,
    alert_data JSONB NOT NULL,
    triggered_rules TEXT[],
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    escalated BOOLEAN DEFAULT FALSE,
    escalated_at TIMESTAMP,
    dismissed BOOLEAN DEFAULT FALSE,
    dismissed_reason TEXT,
    converted_to_case BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fraud_alert_case FOREIGN KEY (case_id) REFERENCES fraud_case(case_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS fraud_investigation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    investigation_id VARCHAR(100) UNIQUE NOT NULL,
    case_id VARCHAR(100) NOT NULL,
    investigator VARCHAR(100) NOT NULL,
    investigation_type VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    findings TEXT,
    evidence_collected TEXT[],
    witness_statements TEXT[],
    recommendation VARCHAR(50),
    confidence_level VARCHAR(20),
    hours_spent DECIMAL(10, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fraud_investigation FOREIGN KEY (case_id) REFERENCES fraud_case(case_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS fraud_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evidence_id VARCHAR(100) UNIQUE NOT NULL,
    case_id VARCHAR(100) NOT NULL,
    investigation_id VARCHAR(100),
    evidence_type VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    description TEXT,
    file_name VARCHAR(255),
    file_url TEXT,
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    hash VARCHAR(128),
    collected_by VARCHAR(100) NOT NULL,
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    chain_of_custody JSONB,
    is_admissible BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fraud_evidence_case FOREIGN KEY (case_id) REFERENCES fraud_case(case_id) ON DELETE CASCADE,
    CONSTRAINT fk_fraud_evidence_investigation FOREIGN KEY (investigation_id) REFERENCES fraud_investigation(investigation_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS fraud_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id VARCHAR(100) UNIQUE NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    conditions JSONB NOT NULL,
    risk_weight DECIMAL(5, 4) NOT NULL,
    action VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    false_positive_rate DECIMAL(5, 4),
    true_positive_rate DECIMAL(5, 4),
    times_triggered INTEGER DEFAULT 0,
    last_triggered_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS fraud_pattern (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern_id VARCHAR(100) UNIQUE NOT NULL,
    pattern_name VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    indicators JSONB NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    frequency_observed INTEGER DEFAULT 0,
    last_observed_at TIMESTAMP,
    geographic_prevalence TEXT[],
    seasonal_factors JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fraud_blacklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id VARCHAR(100) UNIQUE NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    value_encrypted VARCHAR(255) NOT NULL,
    value_hash VARCHAR(128) NOT NULL,
    reason TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    source VARCHAR(50) NOT NULL,
    added_by VARCHAR(100) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    times_matched INTEGER DEFAULT 0,
    last_matched_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fraud_whitelist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id VARCHAR(100) UNIQUE NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    value_encrypted VARCHAR(255) NOT NULL,
    value_hash VARCHAR(128) NOT NULL,
    reason TEXT NOT NULL,
    added_by VARCHAR(100) NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    times_matched INTEGER DEFAULT 0,
    last_matched_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fraud_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_cases INTEGER NOT NULL DEFAULT 0,
    total_alerts INTEGER NOT NULL DEFAULT 0,
    confirmed_fraud_cases INTEGER DEFAULT 0,
    false_positive_cases INTEGER DEFAULT 0,
    total_fraud_amount DECIMAL(18, 2) DEFAULT 0,
    amount_prevented DECIMAL(18, 2) DEFAULT 0,
    amount_recovered DECIMAL(18, 2) DEFAULT 0,
    amount_written_off DECIMAL(18, 2) DEFAULT 0,
    avg_detection_time_hours DECIMAL(10, 2),
    avg_resolution_time_hours DECIMAL(10, 2),
    by_fraud_type JSONB,
    by_detection_method JSONB,
    top_triggered_rules JSONB,
    false_positive_rate DECIMAL(5, 4),
    true_positive_rate DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_fraud_period UNIQUE (period_start, period_end)
);

CREATE TABLE IF NOT EXISTS fraud_customer_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID UNIQUE NOT NULL,
    risk_score DECIMAL(5, 4) NOT NULL DEFAULT 0.5000,
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    total_fraud_cases INTEGER DEFAULT 0,
    total_fraud_alerts INTEGER DEFAULT 0,
    confirmed_fraud_count INTEGER DEFAULT 0,
    false_positive_count INTEGER DEFAULT 0,
    last_fraud_date DATE,
    behavioral_indicators JSONB,
    suspicious_patterns TEXT[],
    trusted_devices TEXT[],
    trusted_locations TEXT[],
    account_age_days INTEGER,
    transaction_velocity JSONB,
    is_monitored BOOLEAN DEFAULT FALSE,
    monitoring_reason TEXT,
    monitoring_started_at TIMESTAMP,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_fraud_case_customer ON fraud_case(customer_id);
CREATE INDEX idx_fraud_case_account ON fraud_case(account_id);
CREATE INDEX idx_fraud_case_transaction ON fraud_case(transaction_id);
CREATE INDEX idx_fraud_case_status ON fraud_case(status);
CREATE INDEX idx_fraud_case_severity ON fraud_case(severity);
CREATE INDEX idx_fraud_case_assigned ON fraud_case(assigned_to);
CREATE INDEX idx_fraud_case_type ON fraud_case(fraud_type);
CREATE INDEX idx_fraud_case_risk ON fraud_case(risk_score DESC);
CREATE INDEX idx_fraud_case_created ON fraud_case(created_at DESC);
CREATE INDEX idx_fraud_case_open ON fraud_case(status, priority) WHERE status IN ('OPEN', 'IN_PROGRESS');

CREATE INDEX idx_fraud_alert_customer ON fraud_alert(customer_id);
CREATE INDEX idx_fraud_alert_case ON fraud_alert(case_id);
CREATE INDEX idx_fraud_alert_status ON fraud_alert(status);
CREATE INDEX idx_fraud_alert_severity ON fraud_alert(severity);
CREATE INDEX idx_fraud_alert_risk ON fraud_alert(risk_score DESC);
CREATE INDEX idx_fraud_alert_created ON fraud_alert(created_at DESC);
CREATE INDEX idx_fraud_alert_new ON fraud_alert(status) WHERE status = 'NEW';

CREATE INDEX idx_fraud_investigation_case ON fraud_investigation(case_id);
CREATE INDEX idx_fraud_investigation_investigator ON fraud_investigation(investigator);
CREATE INDEX idx_fraud_investigation_status ON fraud_investigation(status);
CREATE INDEX idx_fraud_investigation_started ON fraud_investigation(started_at DESC);

CREATE INDEX idx_fraud_evidence_case ON fraud_evidence(case_id);
CREATE INDEX idx_fraud_evidence_investigation ON fraud_evidence(investigation_id);
CREATE INDEX idx_fraud_evidence_type ON fraud_evidence(evidence_type);
CREATE INDEX idx_fraud_evidence_collected ON fraud_evidence(collected_at DESC);

CREATE INDEX idx_fraud_rule_type ON fraud_rule(rule_type);
CREATE INDEX idx_fraud_rule_category ON fraud_rule(category);
CREATE INDEX idx_fraud_rule_active ON fraud_rule(is_active) WHERE is_active = true;
CREATE INDEX idx_fraud_rule_priority ON fraud_rule(priority DESC);

CREATE INDEX idx_fraud_pattern_type ON fraud_pattern(pattern_type);
CREATE INDEX idx_fraud_pattern_risk ON fraud_pattern(risk_level);
CREATE INDEX idx_fraud_pattern_active ON fraud_pattern(is_active) WHERE is_active = true;

CREATE INDEX idx_fraud_blacklist_type ON fraud_blacklist(entry_type);
CREATE INDEX idx_fraud_blacklist_hash ON fraud_blacklist(value_hash);
CREATE INDEX idx_fraud_blacklist_active ON fraud_blacklist(is_active) WHERE is_active = true;

CREATE INDEX idx_fraud_whitelist_type ON fraud_whitelist(entry_type);
CREATE INDEX idx_fraud_whitelist_hash ON fraud_whitelist(value_hash);
CREATE INDEX idx_fraud_whitelist_active ON fraud_whitelist(is_active) WHERE is_active = true;

CREATE INDEX idx_fraud_stats_period ON fraud_statistics(period_end DESC);

CREATE INDEX idx_fraud_customer_profile_risk ON fraud_customer_profile(risk_level, risk_score DESC);
CREATE INDEX idx_fraud_customer_profile_monitored ON fraud_customer_profile(is_monitored) WHERE is_monitored = true;

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_fraud_case_updated_at BEFORE UPDATE ON fraud_case
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_alert_updated_at BEFORE UPDATE ON fraud_alert
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_investigation_updated_at BEFORE UPDATE ON fraud_investigation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_rule_updated_at BEFORE UPDATE ON fraud_rule
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_pattern_updated_at BEFORE UPDATE ON fraud_pattern
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_blacklist_updated_at BEFORE UPDATE ON fraud_blacklist
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_whitelist_updated_at BEFORE UPDATE ON fraud_whitelist
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_customer_profile_updated_at BEFORE UPDATE ON fraud_customer_profile
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();