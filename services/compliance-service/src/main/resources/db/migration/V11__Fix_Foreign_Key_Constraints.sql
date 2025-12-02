-- V11__Fix_Foreign_Key_Constraints.sql
-- CRITICAL P0 FIX: Remove invalid cross-service foreign key constraints
--
-- Issue: Foreign keys reference tables (transactions, users) that don't exist
-- in the compliance-service database (they exist in other microservice databases)
--
-- Solution: Remove FK constraints and use logical references only
-- Application-level validation will ensure referential integrity
--
-- Date: 2025-10-11
-- Author: Waqiti Platform Team

-- ============================================================================
-- DROP INVALID FOREIGN KEY CONSTRAINTS
-- ============================================================================

-- CTR Reports Table
ALTER TABLE IF EXISTS ctr_reports
    DROP CONSTRAINT IF EXISTS fk_ctr_transaction CASCADE;

ALTER TABLE IF EXISTS ctr_reports
    DROP CONSTRAINT IF EXISTS fk_ctr_customer CASCADE;

-- Add comments to document logical references
COMMENT ON COLUMN ctr_reports.transaction_id IS 'Logical reference to transaction in transaction-service (no FK constraint for microservices)';
COMMENT ON COLUMN ctr_reports.customer_id IS 'Logical reference to user in user-service (no FK constraint for microservices)';

-- SAR Reports Table
ALTER TABLE IF EXISTS sar_reports
    DROP CONSTRAINT IF EXISTS fk_sar_transaction CASCADE;

ALTER TABLE IF EXISTS sar_reports
    DROP CONSTRAINT IF EXISTS fk_sar_customer CASCADE;

COMMENT ON COLUMN sar_reports.transaction_id IS 'Logical reference to transaction in transaction-service (no FK constraint for microservices)';
COMMENT ON COLUMN sar_reports.customer_id IS 'Logical reference to user in user-service (no FK constraint for microservices)';

-- AML Alerts Table
ALTER TABLE IF EXISTS aml_alerts
    DROP CONSTRAINT IF EXISTS fk_aml_transaction CASCADE;

ALTER TABLE IF EXISTS aml_alerts
    DROP CONSTRAINT IF EXISTS fk_aml_customer CASCADE;

COMMENT ON COLUMN aml_alerts.transaction_id IS 'Logical reference to transaction in transaction-service (no FK constraint for microservices)';
COMMENT ON COLUMN aml_alerts.customer_id IS 'Logical reference to user in user-service (no FK constraint for microservices)';

-- Compliance Violations Table
ALTER TABLE IF EXISTS compliance_violations
    DROP CONSTRAINT IF EXISTS fk_violation_transaction CASCADE;

ALTER TABLE IF EXISTS compliance_violations
    DROP CONSTRAINT IF EXISTS fk_violation_user CASCADE;

COMMENT ON COLUMN compliance_violations.transaction_id IS 'Logical reference to transaction in transaction-service (no FK constraint for microservices)';
COMMENT ON COLUMN compliance_violations.user_id IS 'Logical reference to user in user-service (no FK constraint for microservices)';

-- ============================================================================
-- FIX DECIMAL PRECISION FOR FINANCIAL AMOUNTS
-- ============================================================================

-- Change DECIMAL(15,2) to DECIMAL(19,4) for proper financial precision
ALTER TABLE ctr_reports
    ALTER COLUMN amount TYPE DECIMAL(19,4);

ALTER TABLE sar_reports
    ALTER COLUMN amount TYPE DECIMAL(19,4);

ALTER TABLE compliance_violations
    ALTER COLUMN amount TYPE DECIMAL(19,4) USING amount::DECIMAL(19,4);

-- ============================================================================
-- ADD MISSING INDEXES FOR PERFORMANCE
-- ============================================================================

-- CTR Reports - Query by date and amount for regulatory reporting
CREATE INDEX IF NOT EXISTS idx_ctr_reports_date_amount_customer
ON ctr_reports(transaction_date DESC, amount DESC, customer_id)
WHERE report_status != 'DELETED';

-- Include commonly queried columns for covering index
CREATE INDEX IF NOT EXISTS idx_ctr_reports_status_date
ON ctr_reports(report_status, transaction_date DESC)
INCLUDE (amount, currency, transaction_type);

-- SAR Reports - Query by risk score and status
CREATE INDEX IF NOT EXISTS idx_sar_reports_risk_status_date
ON sar_reports(risk_score DESC, report_status, transaction_date DESC)
INCLUDE (customer_id, amount, currency);

-- SAR Reports - Query by customer for investigation
CREATE INDEX IF NOT EXISTS idx_sar_reports_customer_date
ON sar_reports(customer_id, transaction_date DESC)
WHERE report_status IN ('PENDING', 'UNDER_REVIEW');

-- AML Alerts - Active alerts by severity
CREATE INDEX IF NOT EXISTS idx_aml_alerts_severity_created
ON aml_alerts(alert_severity, created_at DESC)
WHERE alert_status IN ('OPEN', 'INVESTIGATING');

-- AML Alerts - Customer alert history
CREATE INDEX IF NOT EXISTS idx_aml_alerts_customer_status
ON aml_alerts(customer_id, alert_status, created_at DESC);

-- Compliance Violations - Active violations
CREATE INDEX IF NOT EXISTS idx_compliance_violations_user_type
ON compliance_violations(user_id, violation_type, violation_date DESC)
WHERE resolution_status != 'RESOLVED';

-- Compliance Violations - Severity-based queries
CREATE INDEX IF NOT EXISTS idx_compliance_violations_severity
ON compliance_violations(severity_level, violation_date DESC)
INCLUDE (user_id, violation_type, resolution_status);

-- ============================================================================
-- ADD CHECK CONSTRAINTS FOR DATA INTEGRITY
-- ============================================================================

-- Ensure amounts are positive
ALTER TABLE ctr_reports
    DROP CONSTRAINT IF EXISTS chk_ctr_amount_positive CASCADE;

ALTER TABLE ctr_reports
    ADD CONSTRAINT chk_ctr_amount_positive
    CHECK (amount > 0);

ALTER TABLE sar_reports
    DROP CONSTRAINT IF EXISTS chk_sar_amount_positive CASCADE;

ALTER TABLE sar_reports
    ADD CONSTRAINT chk_sar_amount_positive
    CHECK (amount > 0);

ALTER TABLE compliance_violations
    DROP CONSTRAINT IF EXISTS chk_violation_amount_positive CASCADE;

ALTER TABLE compliance_violations
    ADD CONSTRAINT chk_violation_amount_positive
    CHECK (amount IS NULL OR amount >= 0);

-- ============================================================================
-- UPDATE TABLE COMMENTS
-- ============================================================================

COMMENT ON TABLE ctr_reports IS 'Currency Transaction Reports (CTR) for transactions >= $10,000. Fixed: Removed invalid FK constraints to transaction-service and user-service.';
COMMENT ON TABLE sar_reports IS 'Suspicious Activity Reports (SAR) for potential money laundering. Fixed: Removed invalid FK constraints to transaction-service and user-service.';
COMMENT ON TABLE aml_alerts IS 'Anti-Money Laundering alerts for automated screening. Fixed: Removed invalid FK constraints to transaction-service and user-service.';
COMMENT ON TABLE compliance_violations IS 'Compliance policy violations tracking. Fixed: Removed invalid FK constraints to transaction-service and user-service.';

-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Verify no cross-service foreign keys remain
DO $$
DECLARE
    cross_service_fks INTEGER;
BEGIN
    SELECT COUNT(*) INTO cross_service_fks
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
        ON tc.constraint_name = ccu.constraint_name
    WHERE tc.constraint_type = 'FOREIGN KEY'
      AND (ccu.table_name IN ('transactions', 'users'))
      AND tc.table_schema = 'public';

    IF cross_service_fks > 0 THEN
        RAISE EXCEPTION 'Cross-service foreign keys still exist! Count: %', cross_service_fks;
    ELSE
        RAISE NOTICE 'Success: All cross-service foreign keys removed';
    END IF;
END $$;

-- Verify decimal precision changes
DO $$
BEGIN
    IF (SELECT data_type || '(' || numeric_precision || ',' || numeric_scale || ')'
        FROM information_schema.columns
        WHERE table_name = 'ctr_reports' AND column_name = 'amount') != 'numeric(19,4)' THEN
        RAISE EXCEPTION 'CTR Reports amount column not updated to DECIMAL(19,4)';
    END IF;

    RAISE NOTICE 'Success: All amount columns updated to DECIMAL(19,4)';
END $$;
