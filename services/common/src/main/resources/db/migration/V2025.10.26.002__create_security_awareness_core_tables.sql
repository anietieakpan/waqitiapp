-- ============================================================================
-- V2025.10.26.002__create_security_awareness_core_tables.sql
-- PCI DSS REQ 12.6 - Security Awareness Program Tables
-- ============================================================================

-- Security Training Modules
CREATE TABLE IF NOT EXISTS security_training_modules (
                                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_code VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    pci_requirement VARCHAR(20),
    is_mandatory BOOLEAN NOT NULL DEFAULT true,
    target_roles JSONB,
    estimated_duration_minutes INTEGER,
    passing_score_percentage INTEGER NOT NULL DEFAULT 80,
    content_url VARCHAR(500),
    content_sections JSONB,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_training_module_code ON security_training_modules(module_code);
CREATE INDEX idx_training_module_active ON security_training_modules(is_active);

-- Employee Training Records
CREATE TABLE IF NOT EXISTS employee_training_records (
                                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    module_id UUID NOT NULL REFERENCES security_training_modules(id),
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts_allowed INTEGER NOT NULL DEFAULT 3,
    score_percentage INTEGER,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    acknowledgment_signature TEXT,
    acknowledgment_ip_address VARCHAR(45),
    certificate_url VARCHAR(500),
    certificate_issued_at TIMESTAMP,
    certificate_expires_at TIMESTAMP,
    last_accessed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT uk_employee_module_record UNIQUE (employee_id, module_id, created_at)
    );

CREATE INDEX idx_training_employee ON employee_training_records(employee_id);
CREATE INDEX idx_training_module ON employee_training_records(module_id);
CREATE INDEX idx_training_status ON employee_training_records(status);

-- Employee Security Profiles
CREATE TABLE IF NOT EXISTS employee_security_profiles (
                                                          employee_id UUID PRIMARY KEY,
                                                          total_modules_assigned INTEGER DEFAULT 0,
                                                          total_modules_completed INTEGER DEFAULT 0,
                                                          compliance_percentage DECIMAL(5,2) DEFAULT 0.00,
    total_phishing_tests INTEGER DEFAULT 0,
    phishing_tests_failed INTEGER DEFAULT 0,
    phishing_success_rate_percentage DECIMAL(5,2) DEFAULT 100.00,
    risk_score INTEGER DEFAULT 50,
    risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    last_training_completed_at TIMESTAMP,
    next_training_due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_security_profile_risk ON employee_security_profiles(risk_level);
CREATE INDEX idx_security_profile_due ON employee_security_profiles(next_training_due_at);

-- Employees (Simplified table if not exists)
CREATE TABLE IF NOT EXISTS employees (
                                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    roles JSONB,
    is_active BOOLEAN NOT NULL DEFAULT true,
    hire_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_employee_email ON employees(email);
CREATE INDEX idx_employee_active ON employees(is_active);

-- Security Awareness Audit Logs
CREATE TABLE IF NOT EXISTS security_awareness_audit_logs (
                                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    employee_id UUID,
    pci_requirement VARCHAR(20),
    compliance_status VARCHAR(50),
    event_data JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX idx_audit_event_type ON security_awareness_audit_logs(event_type);
CREATE INDEX idx_audit_employee ON security_awareness_audit_logs(employee_id);
CREATE INDEX idx_audit_pci_requirement ON security_awareness_audit_logs(pci_requirement);
CREATE INDEX idx_audit_timestamp ON security_awareness_audit_logs(timestamp);