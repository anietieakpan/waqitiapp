-- Create Regulatory Reports table for compliance reporting
CREATE TABLE regulatory_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type VARCHAR(50) NOT NULL CHECK (report_type IN ('SAR', 'CTR', 'AML_ALERT', 'MONTHLY_COMPLIANCE', 'QUARTERLY_COMPLIANCE', 'ANNUAL_COMPLIANCE', 'REGULATORY_EXAMINATION', 'SELF_ASSESSMENT', 'INCIDENT_REPORT', 'SANCTIONS_SCREENING', 'KYC_SUMMARY', 'TRANSACTION_MONITORING')),
    alert_id UUID,
    user_id UUID,
    transaction_id UUID,
    report_content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'GENERATED', 'VALIDATED', 'SUBMITTED', 'ACKNOWLEDGED', 'REJECTED', 'RESUBMITTED', 'ARCHIVED', 'ERROR')),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    reporting_period TIMESTAMP WITH TIME ZONE NOT NULL,
    jurisdiction_code CHAR(2) NOT NULL,
    compliance_officer_id UUID,
    submitted_at TIMESTAMP WITH TIME ZONE,
    submission_reference VARCHAR(255),
    regulatory_authority VARCHAR(100),
    amount_reported DECIMAL(19,4),
    currency_code CHAR(3),
    report_format VARCHAR(20),
    validation_status VARCHAR(50),
    validation_errors TEXT,
    acknowledgment_received BOOLEAN,
    acknowledgment_date TIMESTAMP WITH TIME ZONE,
    acknowledgment_reference VARCHAR(255),
    follow_up_required BOOLEAN,
    follow_up_date TIMESTAMP WITH TIME ZONE,
    retention_period_years INTEGER DEFAULT 7,
    confidentiality_level VARCHAR(20) DEFAULT 'CONFIDENTIAL',
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for efficient querying and compliance tracking
CREATE INDEX idx_regulatory_reports_type ON regulatory_reports(report_type);
CREATE INDEX idx_regulatory_reports_status ON regulatory_reports(status);
CREATE INDEX idx_regulatory_reports_generated_at ON regulatory_reports(generated_at);
CREATE INDEX idx_regulatory_reports_period ON regulatory_reports(reporting_period);
CREATE INDEX idx_regulatory_reports_user_id ON regulatory_reports(user_id);
CREATE INDEX idx_regulatory_reports_alert_id ON regulatory_reports(alert_id);
CREATE INDEX idx_regulatory_reports_jurisdiction ON regulatory_reports(jurisdiction_code);
CREATE INDEX idx_regulatory_reports_officer ON regulatory_reports(compliance_officer_id);
CREATE INDEX idx_regulatory_reports_authority ON regulatory_reports(regulatory_authority);
CREATE INDEX idx_regulatory_reports_follow_up ON regulatory_reports(follow_up_required, follow_up_date) WHERE follow_up_required = true;
CREATE INDEX idx_regulatory_reports_acknowledgment ON regulatory_reports(acknowledgment_received) WHERE acknowledgment_received = true;

-- Composite indexes for reporting and analytics
CREATE INDEX idx_regulatory_reports_type_period ON regulatory_reports(report_type, reporting_period);
CREATE INDEX idx_regulatory_reports_type_status ON regulatory_reports(report_type, status);
CREATE INDEX idx_regulatory_reports_jurisdiction_type ON regulatory_reports(jurisdiction_code, report_type);
CREATE INDEX idx_regulatory_reports_period_status ON regulatory_reports(reporting_period, status);

-- Partial indexes for workflow optimization
CREATE INDEX idx_regulatory_reports_pending_submission ON regulatory_reports(generated_at) 
WHERE status IN ('GENERATED', 'VALIDATED');
CREATE INDEX idx_regulatory_reports_validation_errors ON regulatory_reports(validation_status) 
WHERE validation_errors IS NOT NULL;
CREATE INDEX idx_regulatory_reports_amount ON regulatory_reports(amount_reported) 
WHERE amount_reported IS NOT NULL;

-- Index for data retention management
CREATE INDEX idx_regulatory_reports_retention ON regulatory_reports(generated_at, retention_period_years);

-- Add comments for documentation
COMMENT ON TABLE regulatory_reports IS 'Regulatory reports for compliance and regulatory authority submission';
COMMENT ON COLUMN regulatory_reports.report_type IS 'Type of regulatory report (SAR, CTR, compliance reports, etc.)';
COMMENT ON COLUMN regulatory_reports.reporting_period IS 'Period covered by the report (monthly, quarterly, etc.)';
COMMENT ON COLUMN regulatory_reports.amount_reported IS 'Monetary amount reported (for CTR and similar reports)';
COMMENT ON COLUMN regulatory_reports.validation_status IS 'Status of automated validation checks';
COMMENT ON COLUMN regulatory_reports.retention_period_years IS 'Number of years to retain the report for compliance';
COMMENT ON COLUMN regulatory_reports.confidentiality_level IS 'Data classification level for the report';