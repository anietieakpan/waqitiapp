-- Create Business Intelligence Reports table for analytical reports
CREATE TABLE business_intelligence_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_name VARCHAR(255) NOT NULL,
    report_type VARCHAR(100) NOT NULL CHECK (report_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'ANNUAL', 'CUSTOM', 'EXECUTIVE_SUMMARY', 'RISK_ANALYSIS', 'PERFORMANCE_KPI', 'USER_BEHAVIOR', 'FRAUD_SUMMARY')),
    report_period_start DATE NOT NULL,
    report_period_end DATE NOT NULL,
    report_content TEXT NOT NULL,
    report_format VARCHAR(20) NOT NULL CHECK (report_format IN ('JSON', 'HTML', 'PDF', 'CSV', 'EXCEL')),
    generated_by UUID,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL CHECK (status IN ('GENERATING', 'COMPLETED', 'FAILED', 'ARCHIVED')) DEFAULT 'GENERATING',
    file_path VARCHAR(500),
    file_size_bytes BIGINT,
    execution_time_ms INTEGER,
    parameters JSONB,
    summary_data JSONB,
    error_message TEXT,
    viewed_count INTEGER DEFAULT 0,
    last_viewed_at TIMESTAMP WITH TIME ZONE,
    shared_with UUID[],
    retention_days INTEGER DEFAULT 90,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for efficient querying and report management
CREATE INDEX idx_bi_reports_type ON business_intelligence_reports(report_type);
CREATE INDEX idx_bi_reports_period_start ON business_intelligence_reports(report_period_start);
CREATE INDEX idx_bi_reports_period_end ON business_intelligence_reports(report_period_end);
CREATE INDEX idx_bi_reports_generated_at ON business_intelligence_reports(generated_at);
CREATE INDEX idx_bi_reports_generated_by ON business_intelligence_reports(generated_by);
CREATE INDEX idx_bi_reports_status ON business_intelligence_reports(status);
CREATE INDEX idx_bi_reports_retention ON business_intelligence_reports(generated_at, retention_days);

-- Composite indexes for report analytics
CREATE INDEX idx_bi_reports_type_period ON business_intelligence_reports(report_type, report_period_start, report_period_end);
CREATE INDEX idx_bi_reports_type_status ON business_intelligence_reports(report_type, status);
CREATE INDEX idx_bi_reports_period_status ON business_intelligence_reports(report_period_start, status);

-- JSONB indexes for parameters and summary data
CREATE INDEX idx_bi_reports_parameters_gin ON business_intelligence_reports USING GIN (parameters);
CREATE INDEX idx_bi_reports_summary_gin ON business_intelligence_reports USING GIN (summary_data);

-- Partial indexes for active reports
CREATE INDEX idx_bi_reports_active ON business_intelligence_reports(generated_at) 
WHERE status IN ('GENERATING', 'COMPLETED');
CREATE INDEX idx_bi_reports_failed ON business_intelligence_reports(generated_at, error_message) 
WHERE status = 'FAILED';

-- GIN index for shared_with array
CREATE INDEX idx_bi_reports_shared_gin ON business_intelligence_reports USING GIN (shared_with);

-- Add comments for documentation
COMMENT ON TABLE business_intelligence_reports IS 'Business intelligence reports for analytics and insights';
COMMENT ON COLUMN business_intelligence_reports.report_name IS 'Human-readable name for the report';
COMMENT ON COLUMN business_intelligence_reports.report_type IS 'Category and frequency of the report';
COMMENT ON COLUMN business_intelligence_reports.report_content IS 'Main content of the report (JSON, HTML, etc.)';
COMMENT ON COLUMN business_intelligence_reports.report_format IS 'Output format of the report';
COMMENT ON COLUMN business_intelligence_reports.file_path IS 'Path to generated report file (if saved to disk)';
COMMENT ON COLUMN business_intelligence_reports.execution_time_ms IS 'Time taken to generate the report in milliseconds';
COMMENT ON COLUMN business_intelligence_reports.parameters IS 'JSON parameters used to generate the report';
COMMENT ON COLUMN business_intelligence_reports.summary_data IS 'JSON summary of key metrics and findings';
COMMENT ON COLUMN business_intelligence_reports.shared_with IS 'Array of user IDs who have access to this report';
COMMENT ON COLUMN business_intelligence_reports.retention_days IS 'Number of days to retain the report before archival';

-- Create function to archive old reports
CREATE OR REPLACE FUNCTION archive_old_bi_reports()
RETURNS void AS $$
BEGIN
    UPDATE business_intelligence_reports 
    SET status = 'ARCHIVED'
    WHERE status != 'ARCHIVED' 
    AND generated_at < NOW() - INTERVAL '1 day' * retention_days;
END;
$$ LANGUAGE plpgsql;