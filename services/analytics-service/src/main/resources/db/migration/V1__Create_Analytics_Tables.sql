-- Analytics Service Database Schema
-- Creates comprehensive analytics, reporting, and machine learning tables

-- =====================================================================
-- TRANSACTION ANALYTICS TABLE
-- =====================================================================

CREATE TABLE transaction_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    merchant_id UUID,
    analysis_date TIMESTAMP WITH TIME ZONE NOT NULL,
    period_type VARCHAR(20) NOT NULL CHECK (period_type IN (
        'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'
    )),
    transaction_category VARCHAR(100),
    currency VARCHAR(3) NOT NULL,
    
    -- Volume Metrics
    transaction_count BIGINT NOT NULL DEFAULT 0,
    successful_transactions BIGINT NOT NULL DEFAULT 0,
    failed_transactions BIGINT NOT NULL DEFAULT 0,
    pending_transactions BIGINT NOT NULL DEFAULT 0,
    
    -- Amount Metrics
    total_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    successful_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    failed_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    average_amount DECIMAL(19,4),
    median_amount DECIMAL(19,4),
    min_amount DECIMAL(19,4),
    max_amount DECIMAL(19,4),
    
    -- Fee Metrics
    total_fees DECIMAL(19,4) DEFAULT 0.0000,
    average_fee DECIMAL(19,4),
    
    -- Performance Metrics
    success_rate DECIMAL(5,4),
    average_processing_time_ms BIGINT,
    median_processing_time_ms BIGINT,
    
    -- Geographic Metrics
    unique_countries INTEGER,
    top_country VARCHAR(3),
    domestic_transactions BIGINT,
    international_transactions BIGINT,
    
    -- Device and Channel Metrics
    unique_devices INTEGER,
    mobile_transactions BIGINT,
    web_transactions BIGINT,
    api_transactions BIGINT,
    
    -- Risk and Security Metrics
    high_risk_transactions BIGINT,
    fraud_alerts BIGINT,
    aml_alerts BIGINT,
    blocked_transactions BIGINT,
    
    -- Time Pattern Metrics
    peak_hour INTEGER,
    peak_day VARCHAR(10),
    business_hours_transactions BIGINT,
    off_hours_transactions BIGINT,
    
    -- Growth Metrics
    growth_rate DECIMAL(5,4),
    period_over_period_change DECIMAL(5,4),
    
    -- Additional Metrics
    unique_users INTEGER,
    repeat_users INTEGER,
    new_users INTEGER,
    churn_rate DECIMAL(5,4),
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- USER ANALYTICS TABLE
-- =====================================================================

CREATE TABLE user_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    analysis_date TIMESTAMP WITH TIME ZONE NOT NULL,
    period_type VARCHAR(20) NOT NULL CHECK (period_type IN (
        'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'
    )),
    
    -- Transaction Metrics
    transaction_count BIGINT NOT NULL DEFAULT 0,
    successful_transactions BIGINT NOT NULL DEFAULT 0,
    failed_transactions BIGINT NOT NULL DEFAULT 0,
    total_spent DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    total_received DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    net_flow DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    average_transaction_amount DECIMAL(19,4),
    largest_transaction DECIMAL(19,4),
    
    -- Behavioral Metrics
    session_count INTEGER,
    total_session_duration_minutes INTEGER,
    average_session_duration_minutes DECIMAL(8,2),
    pages_viewed INTEGER,
    features_used JSONB,
    
    -- Engagement Metrics
    login_count INTEGER,
    days_active INTEGER,
    streak_days INTEGER,
    last_login TIMESTAMP WITH TIME ZONE,
    engagement_score DECIMAL(5,2),
    
    -- Geographic Metrics
    unique_countries INTEGER,
    primary_country VARCHAR(3),
    transaction_countries JSONB,
    
    -- Device Metrics
    unique_devices INTEGER,
    primary_device_type VARCHAR(50),
    device_usage JSONB,
    
    -- Risk Metrics
    risk_score DECIMAL(5,2),
    fraud_alerts INTEGER,
    compliance_issues INTEGER,
    velocity_violations INTEGER,
    
    -- Financial Health
    account_balance DECIMAL(19,4),
    credit_utilization DECIMAL(5,4),
    payment_reliability_score DECIMAL(5,2),
    
    -- Predictive Metrics
    churn_probability DECIMAL(5,4),
    ltv_prediction DECIMAL(19,4),
    next_transaction_prediction TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- MERCHANT ANALYTICS TABLE
-- =====================================================================

CREATE TABLE merchant_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    analysis_date TIMESTAMP WITH TIME ZONE NOT NULL,
    period_type VARCHAR(20) NOT NULL CHECK (period_type IN (
        'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'
    )),
    
    -- Transaction Metrics
    transaction_count BIGINT NOT NULL DEFAULT 0,
    transaction_volume DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    average_transaction_amount DECIMAL(19,4),
    refund_count BIGINT DEFAULT 0,
    refund_amount DECIMAL(19,4) DEFAULT 0.0000,
    chargeback_count BIGINT DEFAULT 0,
    chargeback_amount DECIMAL(19,4) DEFAULT 0.0000,
    
    -- Customer Metrics
    unique_customers INTEGER,
    new_customers INTEGER,
    returning_customers INTEGER,
    customer_retention_rate DECIMAL(5,4),
    
    -- Performance Metrics
    success_rate DECIMAL(5,4),
    average_settlement_time_hours DECIMAL(8,2),
    uptime_percentage DECIMAL(5,4),
    
    -- Revenue Metrics
    gross_revenue DECIMAL(19,4),
    net_revenue DECIMAL(19,4),
    commission_earned DECIMAL(19,4),
    fees_charged DECIMAL(19,4),
    
    -- Geographic Distribution
    countries_served INTEGER,
    top_country VARCHAR(3),
    geographic_distribution JSONB,
    
    -- Risk Metrics
    fraud_rate DECIMAL(5,4),
    dispute_rate DECIMAL(5,4),
    compliance_score DECIMAL(5,2),
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- REAL TIME METRICS TABLE
-- =====================================================================

CREATE TABLE real_time_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(19,4) NOT NULL,
    metric_unit VARCHAR(20),
    dimensions JSONB,
    aggregation_window_minutes INTEGER DEFAULT 5,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- BUSINESS INTELLIGENCE REPORTS TABLE
-- =====================================================================

CREATE TABLE business_intelligence_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id VARCHAR(50) UNIQUE NOT NULL,
    report_name VARCHAR(255) NOT NULL,
    report_type VARCHAR(50) NOT NULL CHECK (report_type IN (
        'EXECUTIVE_SUMMARY', 'FINANCIAL_PERFORMANCE', 'USER_ENGAGEMENT',
        'TRANSACTION_ANALYSIS', 'RISK_ASSESSMENT', 'GROWTH_METRICS',
        'OPERATIONAL_METRICS', 'COMPLIANCE_REPORT', 'FRAUD_ANALYSIS'
    )),
    report_period VARCHAR(20) NOT NULL,
    report_period_start DATE NOT NULL,
    report_period_end DATE NOT NULL,
    
    -- Report Content
    executive_summary TEXT,
    key_metrics JSONB NOT NULL,
    detailed_analysis JSONB,
    charts_data JSONB,
    recommendations JSONB,
    
    -- Report Metadata
    data_sources JSONB,
    methodology TEXT,
    limitations TEXT,
    confidence_level DECIMAL(5,4),
    
    -- Report Status
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'DRAFT', 'GENERATING', 'COMPLETED', 'PUBLISHED', 'ARCHIVED'
    )) DEFAULT 'DRAFT',
    generated_by UUID,
    approved_by UUID,
    published_at TIMESTAMP WITH TIME ZONE,
    
    -- Distribution
    distribution_list JSONB,
    access_level VARCHAR(20) DEFAULT 'INTERNAL',
    download_count INTEGER DEFAULT 0,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- ML MODEL REGISTRY TABLE
-- =====================================================================

CREATE TABLE ml_model_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    model_type VARCHAR(50) NOT NULL CHECK (model_type IN (
        'FRAUD_DETECTION', 'CHURN_PREDICTION', 'LTV_PREDICTION',
        'RECOMMENDATION', 'ANOMALY_DETECTION', 'FORECASTING',
        'CLUSTERING', 'CLASSIFICATION', 'REGRESSION'
    )),
    
    -- Model Metadata
    algorithm VARCHAR(100),
    framework VARCHAR(50),
    parameters JSONB,
    hyperparameters JSONB,
    
    -- Training Information
    training_data_size BIGINT,
    training_duration_minutes INTEGER,
    training_start_time TIMESTAMP WITH TIME ZONE,
    training_end_time TIMESTAMP WITH TIME ZONE,
    
    -- Performance Metrics
    accuracy DECIMAL(5,4),
    precision_score DECIMAL(5,4),
    recall DECIMAL(5,4),
    f1_score DECIMAL(5,4),
    auc_score DECIMAL(5,4),
    mae DECIMAL(10,4), -- Mean Absolute Error
    mse DECIMAL(10,4), -- Mean Squared Error
    rmse DECIMAL(10,4), -- Root Mean Squared Error
    
    -- Feature Information
    feature_count INTEGER,
    feature_names JSONB,
    feature_importance JSONB,
    
    -- Model Status
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'TRAINING', 'TESTING', 'VALIDATED', 'DEPLOYED', 'RETIRED'
    )) DEFAULT 'TRAINING',
    deployment_date TIMESTAMP WITH TIME ZONE,
    retirement_date TIMESTAMP WITH TIME ZONE,
    
    -- Model Artifacts
    model_path VARCHAR(500),
    model_size_mb DECIMAL(10,2),
    checksum VARCHAR(255),
    
    -- Monitoring
    prediction_count BIGINT DEFAULT 0,
    average_inference_time_ms DECIMAL(8,2),
    last_prediction_time TIMESTAMP WITH TIME ZONE,
    drift_detected BOOLEAN DEFAULT FALSE,
    drift_score DECIMAL(5,4),
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- DATA QUALITY METRICS TABLE
-- =====================================================================

CREATE TABLE data_quality_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source VARCHAR(100) NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    column_name VARCHAR(100),
    metric_date DATE NOT NULL,
    
    -- Completeness Metrics
    total_records BIGINT NOT NULL,
    null_count BIGINT NOT NULL,
    completeness_rate DECIMAL(5,4) NOT NULL,
    
    -- Accuracy Metrics
    invalid_count BIGINT DEFAULT 0,
    accuracy_rate DECIMAL(5,4),
    
    -- Consistency Metrics
    duplicate_count BIGINT DEFAULT 0,
    consistency_score DECIMAL(5,4),
    
    -- Timeliness Metrics
    latest_record_timestamp TIMESTAMP WITH TIME ZONE,
    data_freshness_hours DECIMAL(8,2),
    
    -- Validity Metrics
    format_violations BIGINT DEFAULT 0,
    range_violations BIGINT DEFAULT 0,
    validity_score DECIMAL(5,4),
    
    -- Statistical Profile
    min_value VARCHAR(255),
    max_value VARCHAR(255),
    avg_value DECIMAL(19,4),
    median_value DECIMAL(19,4),
    std_deviation DECIMAL(19,4),
    
    -- Issues Detected
    issues_detected JSONB,
    severity_level VARCHAR(20) CHECK (severity_level IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- ANOMALY DETECTION RESULTS TABLE
-- =====================================================================

CREATE TABLE anomaly_detection_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id UUID UNIQUE NOT NULL,
    user_id UUID,
    transaction_id UUID,
    entity_type VARCHAR(50) NOT NULL, -- USER, TRANSACTION, MERCHANT, etc.
    entity_id UUID NOT NULL,
    
    -- Anomaly Information
    anomaly_type VARCHAR(50) NOT NULL,
    anomaly_score DECIMAL(5,4) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    )),
    confidence DECIMAL(5,4) NOT NULL,
    
    -- Detection Details
    algorithm_used VARCHAR(100),
    features_analyzed JSONB,
    baseline_data JSONB,
    anomaly_explanation TEXT,
    contributing_factors JSONB,
    
    -- Context Information
    detection_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    analysis_window_start TIMESTAMP WITH TIME ZONE,
    analysis_window_end TIMESTAMP WITH TIME ZONE,
    
    -- Action Taken
    action_required BOOLEAN DEFAULT FALSE,
    action_taken VARCHAR(100),
    action_timestamp TIMESTAMP WITH TIME ZONE,
    action_by UUID,
    
    -- Follow-up
    investigation_status VARCHAR(20) DEFAULT 'PENDING',
    false_positive BOOLEAN,
    feedback_provided BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- CUSTOMER SEGMENTATION TABLE
-- =====================================================================

CREATE TABLE customer_segmentation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    segmentation_id VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    segment_name VARCHAR(100) NOT NULL,
    segment_description TEXT,
    
    -- Segmentation Criteria
    segmentation_algorithm VARCHAR(100),
    features_used JSONB,
    segment_characteristics JSONB,
    
    -- Segment Metrics
    confidence_score DECIMAL(5,4),
    segment_size INTEGER,
    segment_value DECIMAL(19,4),
    
    -- Behavioral Patterns
    typical_transaction_amount DECIMAL(19,4),
    transaction_frequency DECIMAL(8,2),
    preferred_channels JSONB,
    geographic_distribution JSONB,
    
    -- Business Value
    ltv_estimate DECIMAL(19,4),
    profitability_score DECIMAL(5,2),
    churn_risk DECIMAL(5,4),
    
    -- Recommendations
    marketing_recommendations JSONB,
    product_recommendations JSONB,
    retention_strategies JSONB,
    
    -- Validity
    valid_from DATE NOT NULL,
    valid_to DATE,
    is_active BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- PREDICTIVE ANALYTICS RESULTS TABLE
-- =====================================================================

CREATE TABLE predictive_analytics_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prediction_id UUID UNIQUE NOT NULL,
    user_id UUID,
    prediction_type VARCHAR(50) NOT NULL CHECK (prediction_type IN (
        'CHURN', 'LTV', 'FRAUD', 'TRANSACTION_VOLUME', 'REVENUE', 'DEFAULT'
    )),
    
    -- Prediction Details
    predicted_value DECIMAL(19,4),
    predicted_probability DECIMAL(5,4),
    confidence_interval_lower DECIMAL(19,4),
    confidence_interval_upper DECIMAL(19,4),
    confidence_level DECIMAL(5,4),
    
    -- Model Information
    model_name VARCHAR(100),
    model_version VARCHAR(50),
    features_used JSONB,
    feature_importance JSONB,
    
    -- Prediction Context
    prediction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    prediction_horizon_days INTEGER,
    target_date DATE,
    
    -- Validation
    actual_value DECIMAL(19,4),
    actual_outcome BOOLEAN,
    prediction_accuracy DECIMAL(5,4),
    validation_date TIMESTAMP WITH TIME ZONE,
    
    -- Business Impact
    recommended_actions JSONB,
    business_impact_estimate DECIMAL(19,4),
    action_taken BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================================

-- Transaction Analytics Indexes
CREATE INDEX idx_transaction_analytics_user_id ON transaction_analytics(user_id);
CREATE INDEX idx_transaction_analytics_merchant_id ON transaction_analytics(merchant_id);
CREATE INDEX idx_transaction_analytics_date_period ON transaction_analytics(analysis_date, period_type);
CREATE INDEX idx_transaction_analytics_category ON transaction_analytics(transaction_category);
CREATE INDEX idx_transaction_analytics_currency ON transaction_analytics(currency);

-- User Analytics Indexes
CREATE INDEX idx_user_analytics_user_id ON user_analytics(user_id);
CREATE INDEX idx_user_analytics_date_period ON user_analytics(analysis_date, period_type);
CREATE INDEX idx_user_analytics_engagement_score ON user_analytics(engagement_score);
CREATE INDEX idx_user_analytics_risk_score ON user_analytics(risk_score);

-- Merchant Analytics Indexes
CREATE INDEX idx_merchant_analytics_merchant_id ON merchant_analytics(merchant_id);
CREATE INDEX idx_merchant_analytics_date_period ON merchant_analytics(analysis_date, period_type);
CREATE INDEX idx_merchant_analytics_revenue ON merchant_analytics(gross_revenue);

-- Real Time Metrics Indexes
CREATE INDEX idx_real_time_metrics_timestamp ON real_time_metrics(metric_timestamp);
CREATE INDEX idx_real_time_metrics_type_name ON real_time_metrics(metric_type, metric_name);
CREATE INDEX idx_real_time_metrics_type_timestamp ON real_time_metrics(metric_type, metric_timestamp);

-- BI Reports Indexes
CREATE INDEX idx_bi_reports_type ON business_intelligence_reports(report_type);
CREATE INDEX idx_bi_reports_period ON business_intelligence_reports(report_period_start, report_period_end);
CREATE INDEX idx_bi_reports_status ON business_intelligence_reports(status);
CREATE INDEX idx_bi_reports_published_at ON business_intelligence_reports(published_at);

-- ML Model Registry Indexes
CREATE INDEX idx_ml_models_name_version ON ml_model_registry(model_name, model_version);
CREATE INDEX idx_ml_models_type ON ml_model_registry(model_type);
CREATE INDEX idx_ml_models_status ON ml_model_registry(status);
CREATE INDEX idx_ml_models_accuracy ON ml_model_registry(accuracy);

-- Data Quality Indexes
CREATE INDEX idx_data_quality_source_table ON data_quality_metrics(data_source, table_name);
CREATE INDEX idx_data_quality_date ON data_quality_metrics(metric_date);
CREATE INDEX idx_data_quality_completeness ON data_quality_metrics(completeness_rate);

-- Anomaly Detection Indexes
CREATE INDEX idx_anomaly_user_id ON anomaly_detection_results(user_id);
CREATE INDEX idx_anomaly_transaction_id ON anomaly_detection_results(transaction_id);
CREATE INDEX idx_anomaly_entity ON anomaly_detection_results(entity_type, entity_id);
CREATE INDEX idx_anomaly_type_score ON anomaly_detection_results(anomaly_type, anomaly_score);
CREATE INDEX idx_anomaly_timestamp ON anomaly_detection_results(detection_timestamp);

-- Customer Segmentation Indexes
CREATE INDEX idx_segmentation_user_id ON customer_segmentation(user_id);
CREATE INDEX idx_segmentation_name ON customer_segmentation(segment_name);
CREATE INDEX idx_segmentation_active ON customer_segmentation(is_active);
CREATE INDEX idx_segmentation_value ON customer_segmentation(segment_value);

-- Predictive Analytics Indexes
CREATE INDEX idx_predictions_user_id ON predictive_analytics_results(user_id);
CREATE INDEX idx_predictions_type ON predictive_analytics_results(prediction_type);
CREATE INDEX idx_predictions_date ON predictive_analytics_results(prediction_date);
CREATE INDEX idx_predictions_model ON predictive_analytics_results(model_name, model_version);

-- =====================================================================
-- UNIQUE CONSTRAINTS
-- =====================================================================

-- Ensure unique analytics per user/merchant/period
CREATE UNIQUE INDEX idx_transaction_analytics_unique 
ON transaction_analytics(COALESCE(user_id, '00000000-0000-0000-0000-000000000000'), 
                         COALESCE(merchant_id, '00000000-0000-0000-0000-000000000000'), 
                         analysis_date, period_type, COALESCE(transaction_category, ''));

CREATE UNIQUE INDEX idx_user_analytics_unique 
ON user_analytics(user_id, analysis_date, period_type);

CREATE UNIQUE INDEX idx_merchant_analytics_unique 
ON merchant_analytics(merchant_id, analysis_date, period_type);

-- Ensure unique model versions
CREATE UNIQUE INDEX idx_ml_models_name_version_unique 
ON ml_model_registry(model_name, model_version);

-- =====================================================================
-- TRIGGERS FOR AUTOMATIC UPDATES
-- =====================================================================

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to relevant tables
CREATE TRIGGER update_transaction_analytics_updated_at 
    BEFORE UPDATE ON transaction_analytics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_analytics_updated_at 
    BEFORE UPDATE ON user_analytics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_analytics_updated_at 
    BEFORE UPDATE ON merchant_analytics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bi_reports_updated_at 
    BEFORE UPDATE ON business_intelligence_reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ml_models_updated_at 
    BEFORE UPDATE ON ml_model_registry
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_segmentation_updated_at 
    BEFORE UPDATE ON customer_segmentation
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_predictions_updated_at 
    BEFORE UPDATE ON predictive_analytics_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_anomaly_updated_at 
    BEFORE UPDATE ON anomaly_detection_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================================
-- PARTITIONING FOR LARGE TABLES
-- =====================================================================

-- Partition real_time_metrics by month for better performance
-- CREATE TABLE real_time_metrics_y2024m01 PARTITION OF real_time_metrics
-- FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON TABLE transaction_analytics IS 'Aggregated transaction analytics data for reporting and insights';
COMMENT ON TABLE user_analytics IS 'User behavior and engagement analytics';
COMMENT ON TABLE merchant_analytics IS 'Merchant performance and business metrics';
COMMENT ON TABLE real_time_metrics IS 'Real-time operational and business metrics';
COMMENT ON TABLE business_intelligence_reports IS 'Generated business intelligence reports';
COMMENT ON TABLE ml_model_registry IS 'Machine learning model metadata and performance tracking';
COMMENT ON TABLE data_quality_metrics IS 'Data quality monitoring and profiling results';
COMMENT ON TABLE anomaly_detection_results IS 'Anomaly detection analysis results';
COMMENT ON TABLE customer_segmentation IS 'Customer segmentation analysis and clusters';
COMMENT ON TABLE predictive_analytics_results IS 'Predictive model results and validation';