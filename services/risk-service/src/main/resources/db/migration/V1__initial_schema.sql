-- Risk Service Initial Schema
-- Created: 2025-09-27
-- Description: Risk assessment, scoring, and management schema

CREATE TABLE IF NOT EXISTS risk_assessment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id VARCHAR(100) UNIQUE NOT NULL,
    assessment_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    assessment_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    risk_score DECIMAL(5, 4) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    risk_category VARCHAR(50),
    assessment_method VARCHAR(50) NOT NULL,
    factors_analyzed JSONB NOT NULL,
    risk_factors JSONB,
    mitigating_factors JSONB,
    model_version VARCHAR(20),
    confidence_level DECIMAL(5, 4),
    valid_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP,
    is_current BOOLEAN DEFAULT TRUE,
    assessed_by VARCHAR(100),
    review_required BOOLEAN DEFAULT FALSE,
    review_date DATE,
    reviewed_by VARCHAR(100),
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id VARCHAR(100) UNIQUE NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    current_risk_score DECIMAL(5, 4) NOT NULL,
    current_risk_level VARCHAR(20) NOT NULL,
    risk_appetite VARCHAR(20),
    risk_tolerance DECIMAL(5, 4),
    risk_categories JSONB,
    historical_scores JSONB,
    trend VARCHAR(20),
    last_assessment_date TIMESTAMP,
    next_assessment_due DATE,
    assessment_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    is_monitored BOOLEAN DEFAULT TRUE,
    monitoring_level VARCHAR(20) DEFAULT 'STANDARD',
    alerts_enabled BOOLEAN DEFAULT TRUE,
    last_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_entity_profile UNIQUE (entity_type, entity_id)
);

CREATE TABLE IF NOT EXISTS risk_factor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    factor_id VARCHAR(100) UNIQUE NOT NULL,
    factor_name VARCHAR(255) NOT NULL,
    factor_category VARCHAR(50) NOT NULL,
    factor_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    weight DECIMAL(5, 4) NOT NULL,
    calculation_method TEXT NOT NULL,
    threshold_low DECIMAL(10, 4),
    threshold_medium DECIMAL(10, 4),
    threshold_high DECIMAL(10, 4),
    is_active BOOLEAN DEFAULT TRUE,
    applies_to TEXT[],
    data_sources TEXT[],
    update_frequency VARCHAR(20),
    version INTEGER DEFAULT 1,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_model (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id VARCHAR(100) UNIQUE NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    model_version VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    risk_factors TEXT[] NOT NULL,
    factor_weights JSONB NOT NULL,
    scoring_formula TEXT NOT NULL,
    thresholds JSONB NOT NULL,
    training_date DATE,
    training_data_size INTEGER,
    accuracy_score DECIMAL(5, 4),
    precision_score DECIMAL(5, 4),
    recall_score DECIMAL(5, 4),
    is_active BOOLEAN DEFAULT FALSE,
    is_production BOOLEAN DEFAULT FALSE,
    activated_at TIMESTAMP,
    deactivated_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    risk_score DECIMAL(5, 4) NOT NULL,
    previous_risk_score DECIMAL(5, 4),
    risk_level VARCHAR(20) NOT NULL,
    triggered_factors TEXT[],
    alert_message TEXT NOT NULL,
    alert_data JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(100),
    assigned_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    acknowledged_at TIMESTAMP,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    false_positive BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    limit_id VARCHAR(100) UNIQUE NOT NULL,
    limit_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100),
    limit_name VARCHAR(255) NOT NULL,
    limit_category VARCHAR(50) NOT NULL,
    limit_value DECIMAL(18, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    time_period VARCHAR(20),
    current_utilization DECIMAL(18, 2) DEFAULT 0,
    utilization_percentage DECIMAL(5, 4) DEFAULT 0,
    warning_threshold DECIMAL(5, 4) DEFAULT 0.8000,
    is_breached BOOLEAN DEFAULT FALSE,
    breach_count INTEGER DEFAULT 0,
    last_breach_date TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_limit_breach (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    breach_id VARCHAR(100) UNIQUE NOT NULL,
    limit_id VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    breach_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    limit_value DECIMAL(18, 2) NOT NULL,
    actual_value DECIMAL(18, 2) NOT NULL,
    breach_amount DECIMAL(18, 2) NOT NULL,
    breach_percentage DECIMAL(5, 4),
    severity VARCHAR(20) NOT NULL,
    notification_sent BOOLEAN DEFAULT FALSE,
    escalated BOOLEAN DEFAULT FALSE,
    escalated_to VARCHAR(100),
    escalated_at TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    resolution_action TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_risk_limit_breach FOREIGN KEY (limit_id) REFERENCES risk_limit(limit_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS risk_mitigation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mitigation_id VARCHAR(100) UNIQUE NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    risk_type VARCHAR(50) NOT NULL,
    mitigation_strategy VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    actions TEXT[] NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    effectiveness_rating VARCHAR(20),
    cost_estimate DECIMAL(15, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    assigned_to VARCHAR(100),
    assigned_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    review_date DATE,
    reviewed_by VARCHAR(100),
    review_notes TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_incident (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id VARCHAR(100) UNIQUE NOT NULL,
    incident_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    incident_date TIMESTAMP NOT NULL,
    discovered_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    description TEXT NOT NULL,
    root_cause TEXT,
    impact_assessment TEXT,
    financial_impact DECIMAL(18, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    affected_customers INTEGER DEFAULT 0,
    regulatory_reporting_required BOOLEAN DEFAULT FALSE,
    regulatory_reference VARCHAR(100),
    assigned_to VARCHAR(100),
    assigned_at TIMESTAMP,
    resolved_date TIMESTAMP,
    resolved_by VARCHAR(100),
    resolution_summary TEXT,
    lessons_learned TEXT,
    preventive_actions TEXT[],
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_assessments INTEGER NOT NULL DEFAULT 0,
    avg_risk_score DECIMAL(5, 4),
    high_risk_count INTEGER DEFAULT 0,
    medium_risk_count INTEGER DEFAULT 0,
    low_risk_count INTEGER DEFAULT 0,
    total_alerts INTEGER DEFAULT 0,
    total_limit_breaches INTEGER DEFAULT 0,
    total_incidents INTEGER DEFAULT 0,
    total_mitigations INTEGER DEFAULT 0,
    by_entity_type JSONB,
    by_risk_category JSONB,
    by_alert_type JSONB,
    trend_analysis JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_risk_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_risk_assessment_entity ON risk_assessment(entity_type, entity_id);
CREATE INDEX idx_risk_assessment_type ON risk_assessment(assessment_type);
CREATE INDEX idx_risk_assessment_date ON risk_assessment(assessment_date DESC);
CREATE INDEX idx_risk_assessment_score ON risk_assessment(risk_score DESC);
CREATE INDEX idx_risk_assessment_level ON risk_assessment(risk_level);
CREATE INDEX idx_risk_assessment_current ON risk_assessment(is_current) WHERE is_current = true;

CREATE INDEX idx_risk_profile_entity ON risk_profile(entity_type, entity_id);
CREATE INDEX idx_risk_profile_level ON risk_profile(current_risk_level);
CREATE INDEX idx_risk_profile_score ON risk_profile(current_risk_score DESC);
CREATE INDEX idx_risk_profile_due ON risk_profile(next_assessment_due) WHERE next_assessment_due IS NOT NULL;
CREATE INDEX idx_risk_profile_monitored ON risk_profile(is_monitored) WHERE is_monitored = true;

CREATE INDEX idx_risk_factor_category ON risk_factor(factor_category);
CREATE INDEX idx_risk_factor_type ON risk_factor(factor_type);
CREATE INDEX idx_risk_factor_active ON risk_factor(is_active) WHERE is_active = true;

CREATE INDEX idx_risk_model_type ON risk_model(model_type);
CREATE INDEX idx_risk_model_version ON risk_model(model_version);
CREATE INDEX idx_risk_model_active ON risk_model(is_active) WHERE is_active = true;
CREATE INDEX idx_risk_model_production ON risk_model(is_production) WHERE is_production = true;

CREATE INDEX idx_risk_alert_entity ON risk_alert(entity_type, entity_id);
CREATE INDEX idx_risk_alert_type ON risk_alert(alert_type);
CREATE INDEX idx_risk_alert_severity ON risk_alert(severity);
CREATE INDEX idx_risk_alert_status ON risk_alert(status);
CREATE INDEX idx_risk_alert_assigned ON risk_alert(assigned_to);
CREATE INDEX idx_risk_alert_created ON risk_alert(created_at DESC);
CREATE INDEX idx_risk_alert_open ON risk_alert(status) WHERE status = 'OPEN';

CREATE INDEX idx_risk_limit_entity ON risk_limit(entity_type, entity_id);
CREATE INDEX idx_risk_limit_type ON risk_limit(limit_type);
CREATE INDEX idx_risk_limit_category ON risk_limit(limit_category);
CREATE INDEX idx_risk_limit_breached ON risk_limit(is_breached) WHERE is_breached = true;
CREATE INDEX idx_risk_limit_active ON risk_limit(is_active) WHERE is_active = true;

CREATE INDEX idx_risk_limit_breach_limit ON risk_limit_breach(limit_id);
CREATE INDEX idx_risk_limit_breach_entity ON risk_limit_breach(entity_type, entity_id);
CREATE INDEX idx_risk_limit_breach_date ON risk_limit_breach(breach_date DESC);
CREATE INDEX idx_risk_limit_breach_unresolved ON risk_limit_breach(resolved) WHERE resolved = false;

CREATE INDEX idx_risk_mitigation_entity ON risk_mitigation(entity_type, entity_id);
CREATE INDEX idx_risk_mitigation_type ON risk_mitigation(risk_type);
CREATE INDEX idx_risk_mitigation_status ON risk_mitigation(status);
CREATE INDEX idx_risk_mitigation_priority ON risk_mitigation(priority);
CREATE INDEX idx_risk_mitigation_assigned ON risk_mitigation(assigned_to);

CREATE INDEX idx_risk_incident_entity ON risk_incident(entity_type, entity_id);
CREATE INDEX idx_risk_incident_type ON risk_incident(incident_type);
CREATE INDEX idx_risk_incident_severity ON risk_incident(severity);
CREATE INDEX idx_risk_incident_status ON risk_incident(status);
CREATE INDEX idx_risk_incident_date ON risk_incident(incident_date DESC);
CREATE INDEX idx_risk_incident_open ON risk_incident(status) WHERE status = 'OPEN';

CREATE INDEX idx_risk_stats_period ON risk_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_risk_assessment_updated_at BEFORE UPDATE ON risk_assessment
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_profile_updated_at BEFORE UPDATE ON risk_profile
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_factor_updated_at BEFORE UPDATE ON risk_factor
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_model_updated_at BEFORE UPDATE ON risk_model
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_alert_updated_at BEFORE UPDATE ON risk_alert
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_limit_updated_at BEFORE UPDATE ON risk_limit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_mitigation_updated_at BEFORE UPDATE ON risk_mitigation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_risk_incident_updated_at BEFORE UPDATE ON risk_incident
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();