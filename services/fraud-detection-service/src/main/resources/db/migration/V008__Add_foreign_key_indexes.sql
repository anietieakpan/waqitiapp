-- Add Foreign Key Indexes for Performance
-- Service: fraud-detection-service
-- Date: 2025-10-18
-- Description: Add missing indexes on foreign key columns to prevent table scans
-- Impact: Significantly improves query performance and prevents deadlocks
-- Priority: HIGH - Production Performance Issue
--
-- Problem:
-- PostgreSQL does NOT automatically create indexes on foreign key columns.
-- Without these indexes:
-- - JOINs perform full table scans
-- - DELETE/UPDATE on parent table scans entire child table
-- - High risk of deadlocks under concurrent load
-- - Slow query performance at scale
--
-- Solution:
-- Create indexes on all foreign key columns
-- Performance improvement: 10-100x faster for FK constraint checks
--
-- Total Indexes: 7

CREATE INDEX IF NOT EXISTS idx_fraud_alerts_transaction_id ON fraud_alerts(transaction_id);
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_user_id ON fraud_alerts(user_id);
CREATE INDEX IF NOT EXISTS idx_fraud_cases_alert_id ON fraud_cases(alert_id);
CREATE INDEX IF NOT EXISTS idx_fraud_cases_assigned_to ON fraud_cases(assigned_to);
CREATE INDEX IF NOT EXISTS idx_fraud_investigation_case_id ON fraud_investigations(case_id);
CREATE INDEX IF NOT EXISTS idx_fraud_investigation_investigator_id ON fraud_investigations(investigator_id);
CREATE INDEX IF NOT EXISTS idx_device_fingerprints_user_id ON device_fingerprints(user_id);

-- Index comments for documentation
COMMENT ON INDEX idx_fraud_alerts_transaction_id IS 'Foreign key index for fraud_alerts.transaction_id - Performance optimization';
COMMENT ON INDEX idx_fraud_alerts_user_id IS 'Foreign key index for fraud_alerts.user_id - Performance optimization';
COMMENT ON INDEX idx_fraud_cases_alert_id IS 'Foreign key index for fraud_cases.alert_id - Performance optimization';
COMMENT ON INDEX idx_fraud_cases_assigned_to IS 'Foreign key index for fraud_cases.assigned_to - Performance optimization';
COMMENT ON INDEX idx_fraud_investigation_case_id IS 'Foreign key index for fraud_investigations.case_id - Performance optimization';
COMMENT ON INDEX idx_fraud_investigation_investigator_id IS 'Foreign key index for fraud_investigations.investigator_id - Performance optimization';
COMMENT ON INDEX idx_device_fingerprints_user_id IS 'Foreign key index for device_fingerprints.user_id - Performance optimization';
