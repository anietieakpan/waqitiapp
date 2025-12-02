-- ============================================================================
-- PCI DSS REQ 12.6 - Security Awareness Program Database Schema
-- ============================================================================
-- Requirements:
-- 12.6.1: Educate personnel upon hire and at least annually
-- 12.6.2: Require personnel to acknowledge understanding
-- 12.6.3: Training for personnel with security responsibilities at least annually
-- 12.6.3.1: Training on threat and vulnerability landscape
-- ============================================================================

-- Training Modules Table
CREATE TABLE IF NOT EXISTS security_training_modules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_code VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    content_type VARCHAR(50) NOT NULL, -- VIDEO, QUIZ, INTERACTIVE, DOCUMENT
    content_url TEXT,
    duration_minutes INTEGER NOT NULL,
    difficulty_level VARCHAR(20) NOT NULL, -- BASIC, INTERMEDIATE, ADVANCED
    is_mandatory BOOLEAN NOT NULL DEFAULT false,
    passing_score_percentage INTEGER DEFAULT 80,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,

    -- PCI DSS specific fields
    pci_requirement VARCHAR(20), -- e.g., "12.6.1", "12.6.3"
    target_roles TEXT[], -- ['DEVELOPER', 'SECURITY_ADMIN', 'DBA', 'ALL']

    CONSTRAINT chk_passing_score CHECK (passing_score_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_duration CHECK (duration_minutes > 0)
);

CREATE INDEX idx_training_modules_active ON security_training_modules(is_active) WHERE is_active = true;
CREATE INDEX idx_training_modules_mandatory ON security_training_modules(is_mandatory) WHERE is_mandatory = true;
CREATE INDEX idx_training_modules_role ON security_training_modules USING GIN(target_roles);

-- Employee Training Records
CREATE TABLE IF NOT EXISTS employee_training_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    module_id UUID NOT NULL REFERENCES security_training_modules(id),

    -- Training session details
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE,

    -- Results
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED', -- NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED
    score_percentage INTEGER,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts_allowed INTEGER DEFAULT 3,

    -- Acknowledgment (PCI DSS REQ 12.6.2)
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    acknowledgment_ip_address INET,
    acknowledgment_signature TEXT, -- Digital signature or checkbox confirmation

    -- Certificate
    certificate_url TEXT,
    certificate_issued_at TIMESTAMP WITH TIME ZONE,
    certificate_expires_at TIMESTAMP WITH TIME ZONE,

    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_score CHECK (score_percentage IS NULL OR (score_percentage BETWEEN 0 AND 100)),
    CONSTRAINT chk_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND score_percentage IS NOT NULL) OR
        (status != 'COMPLETED')
    ),
    CONSTRAINT uk_employee_module_attempt UNIQUE (employee_id, module_id, started_at)
);

CREATE INDEX idx_training_records_employee ON employee_training_records(employee_id);
CREATE INDEX idx_training_records_module ON employee_training_records(module_id);
CREATE INDEX idx_training_records_status ON employee_training_records(status);
CREATE INDEX idx_training_records_incomplete ON employee_training_records(employee_id, status)
    WHERE status IN ('NOT_STARTED', 'IN_PROGRESS');
CREATE INDEX idx_training_records_expiring ON employee_training_records(employee_id, certificate_expires_at)
    WHERE certificate_expires_at IS NOT NULL;

-- Phishing Simulation Campaigns (PCI DSS REQ 12.6.3.1)
CREATE TABLE IF NOT EXISTS phishing_simulation_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_name VARCHAR(255) NOT NULL,
    description TEXT,

    -- Campaign settings
    template_type VARCHAR(50) NOT NULL, -- CREDENTIAL_HARVESTING, MALWARE_ATTACHMENT, SOCIAL_ENGINEERING
    difficulty_level VARCHAR(20) NOT NULL, -- EASY, MEDIUM, HARD
    target_audience TEXT[], -- ['ALL', 'DEVELOPERS', 'FINANCE', 'CUSTOMER_SUPPORT']

    -- Schedule
    scheduled_start TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_start TIMESTAMP WITH TIME ZONE,
    actual_end TIMESTAMP WITH TIME ZONE,

    -- Results
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    total_targeted INTEGER NOT NULL DEFAULT 0,
    total_delivered INTEGER NOT NULL DEFAULT 0,
    total_opened INTEGER NOT NULL DEFAULT 0,
    total_clicked INTEGER NOT NULL DEFAULT 0,
    total_submitted_data INTEGER NOT NULL DEFAULT 0, -- Most critical - entered credentials
    total_reported INTEGER NOT NULL DEFAULT 0, -- Employees who reported the phishing attempt

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_campaign_dates CHECK (scheduled_end > scheduled_start)
);

CREATE INDEX idx_phishing_campaigns_status ON phishing_simulation_campaigns(status);
CREATE INDEX idx_phishing_campaigns_schedule ON phishing_simulation_campaigns(scheduled_start, scheduled_end);

-- Individual Phishing Test Results
CREATE TABLE IF NOT EXISTS phishing_test_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES phishing_simulation_campaigns(id),
    employee_id UUID NOT NULL,

    -- Email tracking
    email_sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    email_delivered BOOLEAN NOT NULL DEFAULT false,
    email_opened_at TIMESTAMP WITH TIME ZONE,

    -- Link tracking
    link_clicked_at TIMESTAMP WITH TIME ZONE,
    link_clicked_ip_address INET,
    link_clicked_user_agent TEXT,

    -- Data submission (credential harvesting)
    data_submitted_at TIMESTAMP WITH TIME ZONE,
    data_submitted_ip_address INET,

    -- Reporting (positive behavior)
    reported_at TIMESTAMP WITH TIME ZONE,
    reported_via VARCHAR(50), -- EMAIL, PHONE, SECURITY_PORTAL

    -- Result
    result VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PASSED, FAILED, REPORTED
    remedial_training_required BOOLEAN NOT NULL DEFAULT false,
    remedial_training_completed_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_phishing_campaign_employee UNIQUE (campaign_id, employee_id)
);

CREATE INDEX idx_phishing_results_campaign ON phishing_test_results(campaign_id);
CREATE INDEX idx_phishing_results_employee ON phishing_test_results(employee_id);
CREATE INDEX idx_phishing_results_failed ON phishing_test_results(employee_id, result) WHERE result = 'FAILED';
CREATE INDEX idx_phishing_results_remedial ON phishing_test_results(employee_id, remedial_training_required)
    WHERE remedial_training_required = true AND remedial_training_completed_at IS NULL;

-- Quarterly Security Assessments (PCI DSS REQ 12.6.3)
CREATE TABLE IF NOT EXISTS quarterly_security_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_name VARCHAR(255) NOT NULL,
    quarter INTEGER NOT NULL, -- 1, 2, 3, 4
    year INTEGER NOT NULL,

    -- Assessment details
    assessment_type VARCHAR(50) NOT NULL, -- KNOWLEDGE_CHECK, VULNERABILITY_AWARENESS, THREAT_LANDSCAPE
    target_roles TEXT[] NOT NULL,

    -- Schedule
    available_from TIMESTAMP WITH TIME ZONE NOT NULL,
    available_until TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Content
    total_questions INTEGER NOT NULL,
    passing_score_percentage INTEGER NOT NULL DEFAULT 80,
    time_limit_minutes INTEGER,

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, ACTIVE, CLOSED

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_quarter CHECK (quarter BETWEEN 1 AND 4),
    CONSTRAINT chk_year CHECK (year >= 2024),
    CONSTRAINT chk_assessment_dates CHECK (available_until > available_from),
    CONSTRAINT uk_assessment_quarter_year UNIQUE (quarter, year, assessment_type)
);

CREATE INDEX idx_assessments_quarter_year ON quarterly_security_assessments(year, quarter);
CREATE INDEX idx_assessments_status ON quarterly_security_assessments(status);

-- Assessment Results
CREATE TABLE IF NOT EXISTS assessment_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id UUID NOT NULL REFERENCES quarterly_security_assessments(id),
    employee_id UUID NOT NULL,

    -- Attempt details
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    time_taken_minutes INTEGER,

    -- Results
    score_percentage INTEGER,
    passed BOOLEAN,
    answers_data JSONB, -- Detailed question-by-question results

    -- Follow-up
    feedback_provided TEXT,
    requires_remediation BOOLEAN NOT NULL DEFAULT false,
    remediation_completed_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_assessment_score CHECK (score_percentage IS NULL OR (score_percentage BETWEEN 0 AND 100)),
    CONSTRAINT uk_assessment_employee UNIQUE (assessment_id, employee_id)
);

CREATE INDEX idx_assessment_results_assessment ON assessment_results(assessment_id);
CREATE INDEX idx_assessment_results_employee ON assessment_results(employee_id);
CREATE INDEX idx_assessment_results_failed ON assessment_results(employee_id, passed) WHERE passed = false;

-- Security Incident Reporting Training
CREATE TABLE IF NOT EXISTS incident_reporting_drills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drill_name VARCHAR(255) NOT NULL,
    description TEXT,

    -- Scenario
    scenario_type VARCHAR(50) NOT NULL, -- DATA_BREACH, RANSOMWARE, SOCIAL_ENGINEERING, PHYSICAL_SECURITY
    scenario_description TEXT NOT NULL,
    expected_actions TEXT[] NOT NULL, -- List of expected reporting/response actions

    -- Schedule
    scheduled_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_deadline TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,

    CONSTRAINT chk_drill_deadline CHECK (completion_deadline > scheduled_date)
);

CREATE INDEX idx_incident_drills_date ON incident_reporting_drills(scheduled_date);
CREATE INDEX idx_incident_drills_status ON incident_reporting_drills(status);

-- Drill Participation
CREATE TABLE IF NOT EXISTS incident_drill_participation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    drill_id UUID NOT NULL REFERENCES incident_reporting_drills(id),
    employee_id UUID NOT NULL,

    -- Participation
    notified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    response_time_minutes INTEGER,

    -- Response quality
    actions_taken TEXT[],
    correct_actions_count INTEGER NOT NULL DEFAULT 0,
    total_expected_actions INTEGER NOT NULL,
    performance_score_percentage INTEGER,

    -- Feedback
    feedback TEXT,
    requires_additional_training BOOLEAN NOT NULL DEFAULT false,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_drill_employee UNIQUE (drill_id, employee_id)
);

CREATE INDEX idx_drill_participation_drill ON incident_drill_participation(drill_id);
CREATE INDEX idx_drill_participation_employee ON incident_drill_participation(employee_id);

-- Employee Security Awareness Profile (Aggregate View)
CREATE TABLE IF NOT EXISTS employee_security_profiles (
    employee_id UUID PRIMARY KEY,

    -- Training compliance
    total_modules_assigned INTEGER NOT NULL DEFAULT 0,
    total_modules_completed INTEGER NOT NULL DEFAULT 0,
    compliance_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    last_training_completed_at TIMESTAMP WITH TIME ZONE,
    next_training_due_at TIMESTAMP WITH TIME ZONE,

    -- Phishing simulation performance
    total_phishing_tests INTEGER NOT NULL DEFAULT 0,
    phishing_tests_failed INTEGER NOT NULL DEFAULT 0,
    phishing_success_rate_percentage DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    last_phishing_test_at TIMESTAMP WITH TIME ZONE,

    -- Assessment performance
    total_assessments_completed INTEGER NOT NULL DEFAULT 0,
    average_assessment_score DECIMAL(5,2),
    last_assessment_at TIMESTAMP WITH TIME ZONE,

    -- Overall risk score (higher = more risk)
    risk_score INTEGER NOT NULL DEFAULT 0, -- 0-100
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW', -- LOW, MEDIUM, HIGH, CRITICAL

    -- Metadata
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_compliance_percentage CHECK (compliance_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_phishing_rate CHECK (phishing_success_rate_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_risk_score CHECK (risk_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_security_profiles_compliance ON employee_security_profiles(compliance_percentage);
CREATE INDEX idx_security_profiles_risk ON employee_security_profiles(risk_level, risk_score);
CREATE INDEX idx_security_profiles_overdue ON employee_security_profiles(next_training_due_at)
    WHERE next_training_due_at < NOW();

-- Audit Trail for Compliance Reporting
CREATE TABLE IF NOT EXISTS security_awareness_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL, -- MODULE, TRAINING_RECORD, CAMPAIGN, ASSESSMENT
    entity_id UUID NOT NULL,
    employee_id UUID,

    -- Event details
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    event_data JSONB,
    ip_address INET,
    user_agent TEXT,

    -- Compliance fields
    pci_requirement VARCHAR(20),
    compliance_status VARCHAR(50),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_awareness_audit_event_type ON security_awareness_audit_log(event_type);
CREATE INDEX idx_awareness_audit_entity ON security_awareness_audit_log(entity_type, entity_id);
CREATE INDEX idx_awareness_audit_employee ON security_awareness_audit_log(employee_id);
CREATE INDEX idx_awareness_audit_timestamp ON security_awareness_audit_log(event_timestamp DESC);
CREATE INDEX idx_awareness_audit_pci ON security_awareness_audit_log(pci_requirement) WHERE pci_requirement IS NOT NULL;

-- Materialized View for Compliance Reporting (PCI DSS Audits)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_pci_training_compliance AS
SELECT
    e.employee_id,
    e.compliance_percentage,
    e.risk_level,
    COUNT(DISTINCT tm.id) FILTER (WHERE tm.pci_requirement IS NOT NULL) as pci_modules_assigned,
    COUNT(DISTINCT etr.module_id) FILTER (WHERE etr.status = 'COMPLETED' AND tm.pci_requirement IS NOT NULL) as pci_modules_completed,
    MAX(etr.completed_at) FILTER (WHERE tm.pci_requirement IS NOT NULL) as last_pci_training_date,
    CASE
        WHEN MAX(etr.completed_at) FILTER (WHERE tm.pci_requirement IS NOT NULL) < NOW() - INTERVAL '365 days'
        THEN true
        ELSE false
    END as annual_training_overdue
FROM employee_security_profiles e
LEFT JOIN employee_training_records etr ON e.employee_id = etr.employee_id
LEFT JOIN security_training_modules tm ON etr.module_id = tm.id
GROUP BY e.employee_id, e.compliance_percentage, e.risk_level;

CREATE UNIQUE INDEX idx_mv_pci_compliance_employee ON mv_pci_training_compliance(employee_id);
CREATE INDEX idx_mv_pci_compliance_overdue ON mv_pci_training_compliance(annual_training_overdue)
    WHERE annual_training_overdue = true;

COMMENT ON MATERIALIZED VIEW mv_pci_training_compliance IS 'PCI DSS REQ 12.6 - Annual training compliance tracking for audit reporting';
