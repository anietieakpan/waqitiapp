-- Create Suspicious Activity Reports table for regulatory compliance
CREATE TABLE suspicious_activity_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_number VARCHAR(100) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    transaction_id UUID,
    suspicious_activity TEXT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_REVIEW', 'UNDER_REVIEW', 'APPROVED_FOR_SUBMISSION', 'SUBMITTED', 'ACKNOWLEDGED', 'FOLLOW_UP_REQUIRED', 'CLOSED', 'REJECTED')),
    filed_date TIMESTAMP WITH TIME ZONE NOT NULL,
    reporting_institution VARCHAR(255) NOT NULL,
    jurisdiction_code CHAR(2) NOT NULL,
    requires_immediate_attention BOOLEAN NOT NULL DEFAULT false,
    reviewed_by UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_notes TEXT,
    submitted_at TIMESTAMP WITH TIME ZONE,
    submission_reference VARCHAR(255),
    regulatory_authority VARCHAR(100),
    priority_level INTEGER CHECK (priority_level BETWEEN 1 AND 5),
    investigation_summary TEXT,
    supporting_documentation TEXT,
    follow_up_required BOOLEAN,
    follow_up_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for efficient querying and compliance reporting
CREATE INDEX idx_sar_user_id ON suspicious_activity_reports(user_id);
CREATE INDEX idx_sar_transaction_id ON suspicious_activity_reports(transaction_id);
CREATE UNIQUE INDEX idx_sar_report_number ON suspicious_activity_reports(report_number);
CREATE INDEX idx_sar_status ON suspicious_activity_reports(status);
CREATE INDEX idx_sar_filed_date ON suspicious_activity_reports(filed_date);
CREATE INDEX idx_sar_jurisdiction ON suspicious_activity_reports(jurisdiction_code);
CREATE INDEX idx_sar_immediate_attention ON suspicious_activity_reports(requires_immediate_attention) WHERE requires_immediate_attention = true;
CREATE INDEX idx_sar_priority ON suspicious_activity_reports(priority_level) WHERE priority_level >= 4;
CREATE INDEX idx_sar_follow_up ON suspicious_activity_reports(follow_up_required, follow_up_date) WHERE follow_up_required = true;
CREATE INDEX idx_sar_reviewed_by ON suspicious_activity_reports(reviewed_by) WHERE reviewed_by IS NOT NULL;
CREATE INDEX idx_sar_regulatory_authority ON suspicious_activity_reports(regulatory_authority);

-- Composite indexes for reporting and workflow
CREATE INDEX idx_sar_status_filed ON suspicious_activity_reports(status, filed_date);
CREATE INDEX idx_sar_user_filed ON suspicious_activity_reports(user_id, filed_date);
CREATE INDEX idx_sar_jurisdiction_status ON suspicious_activity_reports(jurisdiction_code, status);

-- Partial indexes for workflow optimization
CREATE INDEX idx_sar_pending_review ON suspicious_activity_reports(filed_date) WHERE status = 'PENDING_REVIEW';
CREATE INDEX idx_sar_submitted ON suspicious_activity_reports(submitted_at) WHERE status = 'SUBMITTED';

-- Add comments for documentation
COMMENT ON TABLE suspicious_activity_reports IS 'Suspicious Activity Reports (SARs) for regulatory compliance';
COMMENT ON COLUMN suspicious_activity_reports.report_number IS 'Unique SAR identifier for regulatory tracking';
COMMENT ON COLUMN suspicious_activity_reports.requires_immediate_attention IS 'High-priority SARs requiring urgent attention';
COMMENT ON COLUMN suspicious_activity_reports.priority_level IS 'Priority level from 1 (low) to 5 (critical)';
COMMENT ON COLUMN suspicious_activity_reports.regulatory_authority IS 'Regulatory body receiving the report (e.g., FinCEN, FCA)';
COMMENT ON COLUMN suspicious_activity_reports.submission_reference IS 'Reference number from regulatory authority after submission';