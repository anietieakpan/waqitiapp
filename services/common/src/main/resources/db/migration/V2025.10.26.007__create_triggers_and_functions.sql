-- ============================================================================
-- V2025.10.26.007__create_triggers_and_functions.sql
-- Database Triggers for Automatic Updates
-- ============================================================================

-- Function to update timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$ language 'plpgsql';

-- Apply update timestamp triggers
CREATE TRIGGER update_dlq_events_updated_at BEFORE UPDATE ON dlq_events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_training_modules_updated_at BEFORE UPDATE ON security_training_modules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_training_records_updated_at BEFORE UPDATE ON employee_training_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_security_profiles_updated_at BEFORE UPDATE ON employee_security_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_phishing_campaigns_updated_at BEFORE UPDATE ON phishing_simulation_campaigns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_phishing_results_updated_at BEFORE UPDATE ON phishing_test_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_assessments_updated_at BEFORE UPDATE ON quarterly_security_assessments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_assessment_results_updated_at BEFORE UPDATE ON assessment_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_employees_updated_at BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to validate quarter value
CREATE OR REPLACE FUNCTION validate_quarter()
RETURNS TRIGGER AS $
BEGIN
    IF NEW.quarter < 1 OR NEW.quarter > 4 THEN
        RAISE EXCEPTION 'Quarter must be between 1 and 4';
END IF;
RETURN NEW;
END;
$ language 'plpgsql';

CREATE TRIGGER validate_assessment_quarter BEFORE INSERT OR UPDATE ON quarterly_security_assessments
                                                                FOR EACH ROW EXECUTE FUNCTION validate_quarter();