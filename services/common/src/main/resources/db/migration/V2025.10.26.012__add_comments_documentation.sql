-- Copy existing data if migrating
-- INSERT INTO security_awareness_audit_logs_partitioned SELECT * FROM security_awareness_audit_logs;

-- ============================================================================
-- V2025.10.26.012__add_comments_documentation.sql
-- Add Table and Column Comments for Documentation
-- ============================================================================

COMMENT ON TABLE dlq_events IS 'Dead Letter Queue events for failed message processing';
COMMENT ON TABLE security_training_modules IS 'PCI DSS REQ 12.6.1 - Security awareness training modules';
COMMENT ON TABLE employee_training_records IS 'PCI DSS REQ 12.6.2 - Employee training completion records';
COMMENT ON TABLE employee_security_profiles IS 'Employee security awareness profiles and risk scores';
COMMENT ON TABLE phishing_simulation_campaigns IS 'PCI DSS REQ 12.6.3.1 - Phishing simulation campaigns';
COMMENT ON TABLE phishing_test_results IS 'Results of individual phishing simulation tests';
COMMENT ON TABLE quarterly_security_assessments IS 'PCI DSS REQ 12.6.3 - Quarterly security knowledge assessments';
COMMENT ON TABLE assessment_results IS 'Employee results for quarterly security assessments';
COMMENT ON TABLE security_awareness_audit_logs IS 'Audit trail for security awareness program activities';

COMMENT ON COLUMN employee_security_profiles.risk_score IS 'Calculated risk score (0-100) based on training compliance and phishing performance';
COMMENT ON COLUMN employee_security_profiles.risk_level IS 'Risk classification: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN employee_security_profiles.compliance_percentage IS 'Percentage of assigned training modules completed';
COMMENT ON COLUMN phishing_test_results.remedial_training_required IS 'Flag indicating if employee failed phishing test and requires additional training';