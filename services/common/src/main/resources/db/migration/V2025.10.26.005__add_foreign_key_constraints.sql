-- ============================================================================
-- V2025.10.26.005__add_foreign_key_constraints.sql
-- Add Foreign Key Constraints for Data Integrity
-- ============================================================================

-- Add foreign keys for employee_training_records
ALTER TABLE employee_training_records
    ADD CONSTRAINT fk_training_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id)
            ON DELETE CASCADE;

-- Add foreign keys for employee_security_profiles
ALTER TABLE employee_security_profiles
    ADD CONSTRAINT fk_security_profile_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id)
            ON DELETE CASCADE;

-- Add foreign keys for phishing_test_results
ALTER TABLE phishing_test_results
    ADD CONSTRAINT fk_phishing_result_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id)
            ON DELETE CASCADE;

-- Add foreign keys for assessment_results
ALTER TABLE assessment_results
    ADD CONSTRAINT fk_assessment_result_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id)
            ON DELETE CASCADE;