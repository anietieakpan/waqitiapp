- ============================================================================
-- V2025.10.26.004__create_quarterly_assessment_tables.sql
-- PCI DSS REQ 12.6.3 - Quarterly Security Assessment Tables
-- ============================================================================

-- Quarterly Security Assessments
CREATE TABLE IF NOT EXISTS quarterly_security_assessments (
                                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_name VARCHAR(255) NOT NULL,
    quarter INTEGER NOT NULL CHECK (quarter BETWEEN 1 AND 4),
    year INTEGER NOT NULL,
    assessment_type VARCHAR(50) NOT NULL,
    target_roles JSONB,
    available_from TIMESTAMP NOT NULL,
    available_until TIMESTAMP NOT NULL,
    total_questions INTEGER NOT NULL,
    passing_score_percentage INTEGER NOT NULL DEFAULT 80,
    time_limit_minutes INTEGER,
    questions JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_quarter_year_type UNIQUE (quarter, year, assessment_type)
    );

CREATE INDEX idx_assessment_quarter_year ON quarterly_security_assessments(quarter, year);
CREATE INDEX idx_assessment_status ON quarterly_security_assessments(status);

-- Assessment Results
CREATE TABLE IF NOT EXISTS assessment_results (
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id UUID NOT NULL REFERENCES quarterly_security_assessments(id),
    employee_id UUID NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    time_taken_minutes INTEGER,
    score_percentage INTEGER,
    passed BOOLEAN,
    requires_remediation BOOLEAN DEFAULT false,
    answers_data JSONB,
    feedback_provided TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_assessment_result_assessment ON assessment_results(assessment_id);
CREATE INDEX idx_assessment_result_employee ON assessment_results(employee_id);