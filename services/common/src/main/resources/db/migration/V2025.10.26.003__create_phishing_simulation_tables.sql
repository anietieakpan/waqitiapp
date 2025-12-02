- ============================================================================
-- V2025.10.26.003__create_phishing_simulation_tables.sql
-- PCI DSS REQ 12.6.3.1 - Phishing Simulation Tables
-- ============================================================================

-- Phishing Simulation Campaigns
CREATE TABLE IF NOT EXISTS phishing_simulation_campaigns (
                                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_name VARCHAR(255) NOT NULL,
    description TEXT,
    template_type VARCHAR(50) NOT NULL,
    difficulty_level VARCHAR(20),
    target_audience JSONB,
    scheduled_start TIMESTAMP NOT NULL,
    scheduled_end TIMESTAMP NOT NULL,
    actual_start TIMESTAMP,
    actual_end TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    total_targeted INTEGER DEFAULT 0,
    total_delivered INTEGER DEFAULT 0,
    total_opened INTEGER DEFAULT 0,
    total_clicked INTEGER DEFAULT 0,
    total_submitted_data INTEGER DEFAULT 0,
    total_reported INTEGER DEFAULT 0,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_phishing_status ON phishing_simulation_campaigns(status);
CREATE INDEX idx_phishing_scheduled ON phishing_simulation_campaigns(scheduled_start, scheduled_end);

-- Phishing Test Results
CREATE TABLE IF NOT EXISTS phishing_test_results (
                                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES phishing_simulation_campaigns(id),
    employee_id UUID NOT NULL,
    tracking_token VARCHAR(255) NOT NULL UNIQUE,
    email_sent_at TIMESTAMP,
    email_delivered BOOLEAN DEFAULT false,
    email_opened_at TIMESTAMP,
    link_clicked_at TIMESTAMP,
    link_clicked_ip_address VARCHAR(45),
    link_clicked_user_agent TEXT,
    data_submitted_at TIMESTAMP,
    data_submitted_ip_address VARCHAR(45),
    reported_at TIMESTAMP,
    reported_via VARCHAR(50),
    result VARCHAR(50) DEFAULT 'PENDING',
    remedial_training_required BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

CREATE INDEX idx_phishing_result_campaign ON phishing_test_results(campaign_id);
CREATE INDEX idx_phishing_result_employee ON phishing_test_results(employee_id);
CREATE UNIQUE INDEX idx_phishing_result_token ON phishing_test_results(tracking_token);