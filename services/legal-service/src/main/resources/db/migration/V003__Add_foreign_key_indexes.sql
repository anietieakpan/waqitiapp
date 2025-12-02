-- Add Foreign Key Indexes for Performance
-- Service: legal-service
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
-- Total Indexes: 6

CREATE INDEX IF NOT EXISTS idx_legal_cases_user_id ON legal_cases(user_id);
CREATE INDEX IF NOT EXISTS idx_legal_cases_assigned_to ON legal_cases(assigned_to);
CREATE INDEX IF NOT EXISTS idx_subpoenas_case_id ON subpoenas(case_id);
CREATE INDEX IF NOT EXISTS idx_subpoenas_served_by ON subpoenas(served_by);
CREATE INDEX IF NOT EXISTS idx_legal_documents_case_id ON legal_documents(case_id);
CREATE INDEX IF NOT EXISTS idx_legal_documents_uploaded_by ON legal_documents(uploaded_by);

-- Index comments for documentation
COMMENT ON INDEX idx_legal_cases_user_id IS 'Foreign key index for legal_cases.user_id - Performance optimization';
COMMENT ON INDEX idx_legal_cases_assigned_to IS 'Foreign key index for legal_cases.assigned_to - Performance optimization';
COMMENT ON INDEX idx_subpoenas_case_id IS 'Foreign key index for subpoenas.case_id - Performance optimization';
COMMENT ON INDEX idx_subpoenas_served_by IS 'Foreign key index for subpoenas.served_by - Performance optimization';
COMMENT ON INDEX idx_legal_documents_case_id IS 'Foreign key index for legal_documents.case_id - Performance optimization';
COMMENT ON INDEX idx_legal_documents_uploaded_by IS 'Foreign key index for legal_documents.uploaded_by - Performance optimization';
