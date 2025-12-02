-- Security Awareness Module - Complete Schema

-- Employees Table
CREATE TABLE IF NOT EXISTS employees (
                                         employee_id UUID PRIMARY KEY,
                                         email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    department VARCHAR(100),
    job_title VARCHAR(150),
    manager_email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hire_date TIMESTAMP,
    last_training_date TIMESTAMP,
    training_due_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_employee_email ON employees(email);
CREATE INDEX idx_employee_department ON employees(department);
CREATE INDEX idx_employee_status ON employees(status);

-- Employee Security Profiles Table
CREATE TABLE IF NOT EXISTS employee_security_profiles (
                                                          profile_id UUID PRIMARY KEY,
                                                          employee_id UUID NOT NULL UNIQUE REFERENCES employees(employee_id) ON DELETE CASCADE,
    risk_score DECIMAL(5,2) DEFAULT 0.00,
    risk_level VARCHAR(20) DEFAULT 'LOW',
    training_modules_completed INTEGER DEFAULT 0,
    training_modules_total INTEGER DEFAULT 0,
    phishing_tests_passed INTEGER DEFAULT 0,
    phishing_tests_failed INTEGER DEFAULT 0,
    assessments_completed INTEGER DEFAULT 0,
    average_assessment_score DECIMAL(5,2) DEFAULT 0.00,
    last_assessment_date TIMESTAMP,
    next_assessment_due TIMESTAMP,
    compliance_status VARCHAR(50) DEFAULT 'NOT_COMPLIANT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_security_profile_employee ON employee_security_profiles(employee_id);
CREATE INDEX idx_security_profile_risk_score ON employee_security_profiles(risk_score);

-- Security Training Modules Table
CREATE TABLE IF NOT EXISTS security_training_modules (
                                                         module_id UUID PRIMARY KEY,
                                                         title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    duration_minutes INTEGER,
    mandatory BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
    );

-- Employee Training Records Table
CREATE TABLE IF NOT EXISTS employee_training_records (
                                                         record_id UUID PRIMARY KEY,
                                                         employee_id UUID NOT NULL REFERENCES employees(employee_id) ON DELETE CASCADE,
    module_id UUID NOT NULL REFERENCES security_training_modules(module_id),
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_minutes INTEGER,
    score INTEGER,
    passed BOOLEAN,
    certificate_issued BOOLEAN DEFAULT false,
    certificate_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_training_record_employee ON employee_training_records(employee_id);
CREATE INDEX idx_training_record_module ON employee_training_records(module_id);
CREATE INDEX idx_training_record_status ON employee_training_records(status);

-- Phishing Simulation Campaigns Table
CREATE TABLE IF NOT EXISTS phishing_simulation_campaigns (
                                                             campaign_id UUID PRIMARY KEY,
                                                             campaign_name VARCHAR(255) NOT NULL,
    description TEXT,
    template_type VARCHAR(50) NOT NULL,
    subject_line VARCHAR(500),
    sender_email VARCHAR(255),
    sender_name VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    scheduled_date TIMESTAMP,
    started_date TIMESTAMP,
    completed_date TIMESTAMP,
    target_employees_count INTEGER DEFAULT 0,
    emails_sent INTEGER DEFAULT 0,
    emails_opened INTEGER DEFAULT 0,
    links_clicked INTEGER DEFAULT 0,
    data_submitted INTEGER DEFAULT 0,
    reported_as_phishing INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_campaign_status ON phishing_simulation_campaigns(status);
CREATE INDEX idx_campaign_scheduled ON phishing_simulation_campaigns(scheduled_date);

-- Phishing Test Results Table
CREATE TABLE IF NOT EXISTS phishing_test_results (
                                                     result_id UUID PRIMARY KEY,
                                                     campaign_id UUID NOT NULL REFERENCES phishing_simulation_campaigns(campaign_id),
    employee_id UUID NOT NULL REFERENCES employees(employee_id),

    email_sent BOOLEAN DEFAULT false,
    email_opened BOOLEAN DEFAULT false,
    link_clicked BOOLEAN DEFAULT false,
    data_submitted BOOLEAN DEFAULT false,
    reported_as_phishing BOOLEAN DEFAULT false,
    action_taken VARCHAR(50),
    time_to_click_minutes INTEGER,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_at TIMESTAMP,
    clicked_at TIMESTAMP,
    submitted_at TIMESTAMP,
    reported_at TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_test_result_employee ON phishing_test_results(employee_id);
CREATE INDEX idx_test_result_campaign ON phishing_test_results(campaign_id);
CREATE INDEX idx_test_result_action ON phishing_test_results(action_taken);

-- Quarterly Security Assessments Table
CREATE TABLE IF NOT EXISTS quarterly_security_assessments (
                                                              assessment_id UUID PRIMARY KEY,
                                                              title VARCHAR(255) NOT NULL,
    description TEXT,
    quarter INTEGER NOT NULL,
    year INTEGER NOT NULL,
    assessment_type VARCHAR(50) NOT NULL,
    questions JSONB,
    total_questions INTEGER DEFAULT 0,
    passing_score INTEGER DEFAULT 70,
    time_limit_minutes INTEGER DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    available_from TIMESTAMP,
    available_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_assessment_quarter ON quarterly_security_assessments(quarter, year);
CREATE INDEX idx_assessment_status ON quarterly_security_assessments(status);

-- Assessment Results Table
CREATE TABLE IF NOT EXISTS assessment_results (
                                                  result_id UUID PRIMARY KEY,
                                                  assessment_id UUID NOT NULL REFERENCES quarterly_security_assessments(assessment_id),
    employee_id UUID NOT NULL REFERENCES employees(employee_id),
    attempt_number INTEGER DEFAULT 1,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_minutes INTEGER,
    score DECIMAL(5,2),
    passed BOOLEAN,
    answers JSONB,
    questions_answered INTEGER DEFAULT 0,
    correct_answers INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_result_employee ON assessment_results(employee_id);
CREATE INDEX idx_result_assessment ON assessment_results(assessment_id);
CREATE INDEX idx_result_completed ON assessment_results(completed_at);

-- Security Awareness Audit Logs Table
CREATE TABLE IF NOT EXISTS security_awareness_audit_logs (
                                                             audit_id UUID PRIMARY KEY,
                                                             employee_id UUID,
                                                             employee_email VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    description TEXT,
    entity_type VARCHAR(100),
    entity_id UUID,
    ip_address VARCHAR(45),
    user_agent TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_audit_employee ON security_awareness_audit_logs(employee_id);
CREATE INDEX idx_audit_event_type ON security_awareness_audit_logs(event_type);
CREATE INDEX idx_audit_timestamp ON security_awareness_audit_logs(timestamp);

-- DLQ Recovery Tables
CREATE TABLE IF NOT EXISTS dlq_recovery_attempts (
                                                     attempt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count INTEGER DEFAULT 0
    );

CREATE INDEX idx_dlq_recovery_event ON dlq_recovery_attempts(event_id);
CREATE INDEX idx_dlq_recovery_service ON dlq_recovery_attempts(service_name);

CREATE TABLE IF NOT EXISTS dlq_manual_review_cases (
                                                       case_id VARCHAR(255) PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_to VARCHAR(255),
    resolved_at TIMESTAMP,
    resolution_notes TEXT
    );

CREATE INDEX idx_manual_review_status ON dlq_manual_review_cases(status);
CREATE INDEX idx_manual_review_priority ON dlq_manual_review_cases(priority);

CREATE TABLE IF NOT EXISTS dlq_dead_storage (
                                                storage_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT,
    original_topic VARCHAR(255),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    first_failed_at TIMESTAMP,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT
    );

CREATE INDEX idx_dead_storage_event ON dlq_dead_storage(event_id);
CREATE INDEX idx_dead_storage_archived ON dlq_dead_storage(archived_at);

-- Comments for documentation
COMMENT ON TABLE employees IS 'Employees participating in security awareness training';
COMMENT ON TABLE employee_security_profiles IS 'Security risk profiles and metrics for employees';
COMMENT ON TABLE security_training_modules IS 'Available security training modules';
COMMENT ON TABLE employee_training_records IS 'Individual employee training completion records';
COMMENT ON TABLE phishing_simulation_campaigns IS 'Phishing simulation campaigns for security testing';
COMMENT ON TABLE phishing_test_results IS 'Individual employee responses to phishing simulations';
COMMENT ON TABLE quarterly_security_assessments IS 'Quarterly security knowledge assessments';
COMMENT ON TABLE assessment_results IS 'Employee assessment attempt results';
COMMENT ON TABLE security_awareness_audit_logs IS 'Audit trail for all security awareness activities';