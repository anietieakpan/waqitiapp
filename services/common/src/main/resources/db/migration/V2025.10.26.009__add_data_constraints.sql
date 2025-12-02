-- ============================================================================
-- V2025.10.26.009__add_data_constraints.sql
-- Additional Data Validation Constraints
-- ============================================================================

-- Ensure score percentages are valid
ALTER TABLE employee_training_records
    ADD CONSTRAINT chk_training_score_range
        CHECK (score_percentage IS NULL OR (score_percentage >= 0 AND score_percentage <= 100));

ALTER TABLE assessment_results
    ADD CONSTRAINT chk_assessment_score_range
        CHECK (score_percentage IS NULL OR (score_percentage >= 0 AND score_percentage <= 100));

-- Ensure compliance percentages are valid
ALTER TABLE employee_security_profiles
    ADD CONSTRAINT chk_compliance_range
        CHECK (compliance_percentage >= 0 AND compliance_percentage <= 100);

ALTER TABLE employee_security_profiles
    ADD CONSTRAINT chk_phishing_success_range
        CHECK (phishing_success_rate_percentage >= 0 AND phishing_success_rate_percentage <= 100);

-- Ensure risk score is valid
ALTER TABLE employee_security_profiles
    ADD CONSTRAINT chk_risk_score_range
        CHECK (risk_score >= 0 AND risk_score <= 100);

-- Ensure attempt counts are non-negative
ALTER TABLE employee_training_records
    ADD CONSTRAINT chk_attempts_non_negative
        CHECK (attempts >= 0 AND max_attempts_allowed > 0);

-- Ensure time taken is non-negative
ALTER TABLE assessment_results
    ADD CONSTRAINT chk_time_taken_non_negative
        CHECK (time_taken_minutes IS NULL OR time_taken_minutes >= 0);

-- Ensure retry counts are non-negative
ALTER TABLE dlq_events
    ADD CONSTRAINT chk_retry_count_non_negative
        CHECK (retry_count >= 0 AND max_retry_count > 0);

-- Ensure campaign statistics are non-negative
ALTER TABLE phishing_simulation_campaigns
    ADD CONSTRAINT chk_campaign_stats_non_negative
        CHECK (total_targeted >= 0 AND total_delivered >= 0 AND
               total_opened >= 0 AND total_clicked >= 0 AND
               total_submitted_data >= 0 AND total_reported >= 0);

-- Ensure logical date ordering
ALTER TABLE phishing_simulation_campaigns
    ADD CONSTRAINT chk_campaign_dates
        CHECK (scheduled_start < scheduled_end);

ALTER TABLE quarterly_security_assessments
    ADD CONSTRAINT chk_assessment_dates
        CHECK (available_from < available_until);