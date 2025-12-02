-- ============================================================================
-- V2025.10.26.006__add_performance_indexes.sql
-- Additional Performance Indexes
-- ============================================================================

-- DLQ Events - Composite indexes for common queries
CREATE INDEX idx_dlq_service_status ON dlq_events(service_name, status);
CREATE INDEX idx_dlq_priority_status ON dlq_events(priority, status, first_failed_at);
CREATE INDEX idx_dlq_retry_schedule ON dlq_events(status, next_retry_at)
    WHERE next_retry_at IS NOT NULL;

-- Training Records - Composite indexes
CREATE INDEX idx_training_employee_status ON employee_training_records(employee_id, status);
CREATE INDEX idx_training_module_status ON employee_training_records(module_id, status);
CREATE INDEX idx_training_completed ON employee_training_records(employee_id, completed_at)
    WHERE completed_at IS NOT NULL;

-- Phishing Results - Performance indexes
CREATE INDEX idx_phishing_campaign_result ON phishing_test_results(campaign_id, result);
CREATE INDEX idx_phishing_employee_result ON phishing_test_results(employee_id, result);
CREATE INDEX idx_phishing_failed ON phishing_test_results(employee_id, result)
    WHERE result = 'FAILED';

-- Assessment Results - Composite indexes
CREATE INDEX idx_assessment_result_completed ON assessment_results(assessment_id, completed_at)
    WHERE completed_at IS NOT NULL;
CREATE INDEX idx_assessment_result_passed ON assessment_results(assessment_id, passed)
    WHERE passed IS NOT NULL;

-- Security Profiles - Risk analysis indexes
CREATE INDEX idx_security_profile_compliance ON employee_security_profiles(compliance_percentage);
CREATE INDEX idx_security_profile_overdue ON employee_security_profiles(next_training_due_at)
    WHERE next_training_due_at < CURRENT_TIMESTAMP;