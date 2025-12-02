-- Report Definition Table
CREATE TABLE report_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id UUID UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    template_path VARCHAR(500),
    parameters JSONB,
    is_active BOOLEAN DEFAULT true,
    requires_approval BOOLEAN DEFAULT false,
    retention_days INTEGER DEFAULT 90,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255)
);

-- Report Execution Table
CREATE TABLE report_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type VARCHAR(100) NOT NULL,
    report_definition_id UUID REFERENCES report_definitions(id),
    status VARCHAR(50) NOT NULL,
    parameters JSONB,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    requested_by VARCHAR(255) NOT NULL,
    execution_time_ms BIGINT,
    file_path VARCHAR(500),
    file_name VARCHAR(255),
    file_size_bytes BIGINT,
    error_message TEXT,
    metadata JSONB,
    CONSTRAINT execution_status_check CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

-- Report Schedule Table
CREATE TABLE report_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id UUID UNIQUE NOT NULL,
    report_definition_id UUID REFERENCES report_definitions(id),
    schedule_name VARCHAR(255) NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    cron_expression VARCHAR(255),
    next_execution TIMESTAMP WITH TIME ZONE,
    last_executed TIMESTAMP WITH TIME ZONE,
    last_execution_status VARCHAR(50),
    last_execution_error TEXT,
    is_active BOOLEAN DEFAULT true,
    recipients TEXT[],
    email_notification BOOLEAN DEFAULT true,
    output_format VARCHAR(20) DEFAULT 'PDF',
    parameters JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    CONSTRAINT schedule_frequency_check CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'CUSTOM')),
    CONSTRAINT output_format_check CHECK (output_format IN ('PDF', 'EXCEL', 'CSV', 'JSON'))
);

-- Financial Data Table (for caching aggregated data)
CREATE TABLE financial_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_type VARCHAR(100) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    aggregated_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(data_type, period_start, period_end, currency)
);

-- Indexes for performance
CREATE INDEX idx_report_executions_report_type ON report_executions(report_type);
CREATE INDEX idx_report_executions_status ON report_executions(status);
CREATE INDEX idx_report_executions_requested_by ON report_executions(requested_by);
CREATE INDEX idx_report_executions_started_at ON report_executions(started_at DESC);

CREATE INDEX idx_report_schedules_active ON report_schedules(is_active);
CREATE INDEX idx_report_schedules_next_execution ON report_schedules(next_execution);
CREATE INDEX idx_report_schedules_frequency ON report_schedules(frequency);

CREATE INDEX idx_financial_data_type_period ON financial_data(data_type, period_start, period_end);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_report_definitions_updated_at BEFORE UPDATE ON report_definitions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_report_schedules_updated_at BEFORE UPDATE ON report_schedules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_financial_data_updated_at BEFORE UPDATE ON financial_data
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();