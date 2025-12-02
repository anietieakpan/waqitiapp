-- ML Service Database Schema
-- Initial schema for machine learning models, inference, and monitoring

-- Create ML Models table
CREATE TABLE ml_models (
    model_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name VARCHAR(255) NOT NULL,
    model_type VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    model_path VARCHAR(500) NOT NULL,
    model_config TEXT,
    model_metadata TEXT,
    model_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    accuracy DECIMAL(5,4),
    precision_score DECIMAL(5,4),
    recall_score DECIMAL(5,4),
    f1_score DECIMAL(5,4),
    training_dataset_size BIGINT,
    feature_count INTEGER,
    model_size_mb DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    trained_by VARCHAR(255),
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    deployed_at TIMESTAMP,
    last_prediction_at TIMESTAMP,
    prediction_count BIGINT DEFAULT 0,
    error_count BIGINT DEFAULT 0,
    UNIQUE(model_name, model_version)
);

-- Create indexes for ml_models
CREATE INDEX idx_ml_models_name ON ml_models(model_name);
CREATE INDEX idx_ml_models_type ON ml_models(model_type);
CREATE INDEX idx_ml_models_status ON ml_models(model_status);
CREATE INDEX idx_ml_models_created_at ON ml_models(created_at);

-- Create ML Predictions table
CREATE TABLE ml_predictions (
    prediction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES ml_models(model_id),
    transaction_id VARCHAR(255),
    customer_id VARCHAR(255),
    input_features TEXT NOT NULL,
    prediction_result TEXT NOT NULL,
    confidence_score DECIMAL(5,4),
    risk_score DECIMAL(5,4),
    prediction_time_ms INTEGER,
    prediction_type VARCHAR(100) NOT NULL,
    prediction_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    actual_outcome VARCHAR(100),
    is_correct BOOLEAN,
    feedback_provided BOOLEAN DEFAULT FALSE,
    feedback_timestamp TIMESTAMP,
    model_version VARCHAR(50),
    feature_importance TEXT,
    explanation TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for ml_predictions
CREATE INDEX idx_ml_predictions_model_id ON ml_predictions(model_id);
CREATE INDEX idx_ml_predictions_transaction_id ON ml_predictions(transaction_id);
CREATE INDEX idx_ml_predictions_customer_id ON ml_predictions(customer_id);
CREATE INDEX idx_ml_predictions_type ON ml_predictions(prediction_type);
CREATE INDEX idx_ml_predictions_timestamp ON ml_predictions(prediction_timestamp);
CREATE INDEX idx_ml_predictions_risk_score ON ml_predictions(risk_score);

-- Create ML Training Jobs table
CREATE TABLE ml_training_jobs (
    job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name VARCHAR(255) NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    job_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    dataset_location VARCHAR(500),
    training_config TEXT,
    hyperparameters TEXT,
    feature_columns TEXT,
    target_column VARCHAR(255),
    training_data_size BIGINT,
    validation_data_size BIGINT,
    test_data_size BIGINT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    training_duration_minutes INTEGER,
    final_accuracy DECIMAL(5,4),
    final_loss DECIMAL(8,6),
    best_epoch INTEGER,
    total_epochs INTEGER,
    model_output_path VARCHAR(500),
    metrics TEXT,
    logs TEXT,
    error_message TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for ml_training_jobs
CREATE INDEX idx_ml_training_jobs_model_name ON ml_training_jobs(model_name);
CREATE INDEX idx_ml_training_jobs_status ON ml_training_jobs(job_status);
CREATE INDEX idx_ml_training_jobs_type ON ml_training_jobs(job_type);
CREATE INDEX idx_ml_training_jobs_created_at ON ml_training_jobs(created_at);

-- Create Feature Store table
CREATE TABLE feature_store (
    feature_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    feature_name VARCHAR(255) NOT NULL,
    feature_value TEXT NOT NULL,
    feature_type VARCHAR(50) NOT NULL,
    feature_timestamp TIMESTAMP NOT NULL,
    feature_version INTEGER DEFAULT 1,
    feature_source VARCHAR(255),
    feature_description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    UNIQUE(entity_id, entity_type, feature_name, feature_timestamp)
);

-- Create indexes for feature_store
CREATE INDEX idx_feature_store_entity ON feature_store(entity_id, entity_type);
CREATE INDEX idx_feature_store_name ON feature_store(feature_name);
CREATE INDEX idx_feature_store_timestamp ON feature_store(feature_timestamp);
CREATE INDEX idx_feature_store_active ON feature_store(is_active);

-- Create Model Monitoring table
CREATE TABLE model_monitoring (
    monitoring_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES ml_models(model_id),
    monitoring_type VARCHAR(100) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(10,6) NOT NULL,
    threshold_value DECIMAL(10,6),
    is_anomaly BOOLEAN DEFAULT FALSE,
    alert_level VARCHAR(50),
    monitoring_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    time_window_minutes INTEGER,
    sample_size INTEGER,
    additional_metrics TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for model_monitoring
CREATE INDEX idx_model_monitoring_model_id ON model_monitoring(model_id);
CREATE INDEX idx_model_monitoring_type ON model_monitoring(monitoring_type);
CREATE INDEX idx_model_monitoring_timestamp ON model_monitoring(monitoring_timestamp);
CREATE INDEX idx_model_monitoring_anomaly ON model_monitoring(is_anomaly);

-- Create Fraud Detection Results table
CREATE TABLE fraud_detection_results (
    result_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    fraud_score DECIMAL(5,4) NOT NULL,
    risk_level VARCHAR(50) NOT NULL,
    fraud_indicators TEXT,
    model_version VARCHAR(50),
    detection_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processing_time_ms INTEGER,
    rules_triggered TEXT,
    confidence_level DECIMAL(5,4),
    recommendation VARCHAR(255),
    is_fraud BOOLEAN,
    actual_fraud BOOLEAN,
    false_positive BOOLEAN,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for fraud_detection_results
CREATE INDEX idx_fraud_detection_transaction ON fraud_detection_results(transaction_id);
CREATE INDEX idx_fraud_detection_customer ON fraud_detection_results(customer_id);
CREATE INDEX idx_fraud_detection_score ON fraud_detection_results(fraud_score);
CREATE INDEX idx_fraud_detection_timestamp ON fraud_detection_results(detection_timestamp);
CREATE INDEX idx_fraud_detection_is_fraud ON fraud_detection_results(is_fraud);

-- Create Model Experiments table
CREATE TABLE model_experiments (
    experiment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_name VARCHAR(255) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    experiment_type VARCHAR(100) NOT NULL,
    hypothesis TEXT,
    experiment_config TEXT,
    dataset_config TEXT,
    feature_config TEXT,
    hyperparameters TEXT,
    experiment_status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_minutes INTEGER,
    baseline_accuracy DECIMAL(5,4),
    experiment_accuracy DECIMAL(5,4),
    improvement_percent DECIMAL(5,2),
    statistical_significance DECIMAL(5,4),
    results TEXT,
    conclusions TEXT,
    next_steps TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for model_experiments
CREATE INDEX idx_model_experiments_name ON model_experiments(experiment_name);
CREATE INDEX idx_model_experiments_model ON model_experiments(model_name);
CREATE INDEX idx_model_experiments_status ON model_experiments(experiment_status);
CREATE INDEX idx_model_experiments_created_at ON model_experiments(created_at);

-- Create Model Performance Metrics table
CREATE TABLE model_performance_metrics (
    metric_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES ml_models(model_id),
    metric_date DATE NOT NULL,
    metric_hour INTEGER DEFAULT 0,
    prediction_count BIGINT DEFAULT 0,
    accuracy DECIMAL(5,4),
    precision_score DECIMAL(5,4),
    recall_score DECIMAL(5,4),
    f1_score DECIMAL(5,4),
    auc_score DECIMAL(5,4),
    average_confidence DECIMAL(5,4),
    average_processing_time_ms INTEGER,
    error_count BIGINT DEFAULT 0,
    true_positives BIGINT DEFAULT 0,
    false_positives BIGINT DEFAULT 0,
    true_negatives BIGINT DEFAULT 0,
    false_negatives BIGINT DEFAULT 0,
    data_drift_score DECIMAL(5,4),
    concept_drift_score DECIMAL(5,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(model_id, metric_date, metric_hour)
);

-- Create indexes for model_performance_metrics
CREATE INDEX idx_model_performance_model_id ON model_performance_metrics(model_id);
CREATE INDEX idx_model_performance_date ON model_performance_metrics(metric_date);
CREATE INDEX idx_model_performance_accuracy ON model_performance_metrics(accuracy);

-- Create Data Quality Metrics table
CREATE TABLE data_quality_metrics (
    quality_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_name VARCHAR(255) NOT NULL,
    metric_date DATE NOT NULL,
    metric_hour INTEGER DEFAULT 0,
    total_records BIGINT,
    valid_records BIGINT,
    invalid_records BIGINT,
    missing_values BIGINT,
    duplicate_records BIGINT,
    completeness_score DECIMAL(5,4),
    accuracy_score DECIMAL(5,4),
    consistency_score DECIMAL(5,4),
    validity_score DECIMAL(5,4),
    overall_quality_score DECIMAL(5,4),
    quality_issues TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(dataset_name, metric_date, metric_hour)
);

-- Create indexes for data_quality_metrics
CREATE INDEX idx_data_quality_dataset ON data_quality_metrics(dataset_name);
CREATE INDEX idx_data_quality_date ON data_quality_metrics(metric_date);
CREATE INDEX idx_data_quality_score ON data_quality_metrics(overall_quality_score);

-- Create Model Alerts table
CREATE TABLE model_alerts (
    alert_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID REFERENCES ml_models(model_id),
    alert_type VARCHAR(100) NOT NULL,
    alert_level VARCHAR(50) NOT NULL,
    alert_title VARCHAR(255) NOT NULL,
    alert_message TEXT NOT NULL,
    alert_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metric_name VARCHAR(255),
    metric_value DECIMAL(10,6),
    threshold_value DECIMAL(10,6),
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMP,
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for model_alerts
CREATE INDEX idx_model_alerts_model_id ON model_alerts(model_id);
CREATE INDEX idx_model_alerts_type ON model_alerts(alert_type);
CREATE INDEX idx_model_alerts_level ON model_alerts(alert_level);
CREATE INDEX idx_model_alerts_timestamp ON model_alerts(alert_timestamp);
CREATE INDEX idx_model_alerts_acknowledged ON model_alerts(is_acknowledged);
CREATE INDEX idx_model_alerts_resolved ON model_alerts(is_resolved);

-- Create Model Versions table
CREATE TABLE model_versions (
    version_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES ml_models(model_id),
    version_number VARCHAR(50) NOT NULL,
    version_description TEXT,
    model_artifact_path VARCHAR(500),
    model_checksum VARCHAR(255),
    training_dataset_version VARCHAR(50),
    feature_version VARCHAR(50),
    hyperparameters TEXT,
    training_metrics TEXT,
    validation_metrics TEXT,
    test_metrics TEXT,
    is_active BOOLEAN DEFAULT FALSE,
    is_production BOOLEAN DEFAULT FALSE,
    deployment_date TIMESTAMP,
    rollback_date TIMESTAMP,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(model_id, version_number)
);

-- Create indexes for model_versions
CREATE INDEX idx_model_versions_model_id ON model_versions(model_id);
CREATE INDEX idx_model_versions_number ON model_versions(version_number);
CREATE INDEX idx_model_versions_active ON model_versions(is_active);
CREATE INDEX idx_model_versions_production ON model_versions(is_production);
CREATE INDEX idx_model_versions_created_at ON model_versions(created_at);

-- Insert default ML models
INSERT INTO ml_models (model_name, model_type, model_version, model_path, model_status, created_at, trained_by) VALUES
('fraud_detection_v1', 'CLASSIFICATION', '1.0.0', '/app/models/fraud-detection/v1', 'ACTIVE', CURRENT_TIMESTAMP, 'system'),
('risk_scoring_v1', 'REGRESSION', '1.0.0', '/app/models/risk-scoring/v1', 'ACTIVE', CURRENT_TIMESTAMP, 'system'),
('behavioral_analysis_v1', 'CLASSIFICATION', '1.0.0', '/app/models/behavioral-analysis/v1', 'ACTIVE', CURRENT_TIMESTAMP, 'system'),
('pattern_recognition_v1', 'CLASSIFICATION', '1.0.0', '/app/models/pattern-recognition/v1', 'ACTIVE', CURRENT_TIMESTAMP, 'system'),
('transaction_anomaly_v1', 'ANOMALY_DETECTION', '1.0.0', '/app/models/transaction-anomaly/v1', 'ACTIVE', CURRENT_TIMESTAMP, 'system');

-- Insert default model versions
INSERT INTO model_versions (model_id, version_number, version_description, model_artifact_path, is_active, is_production, created_by, created_at)
SELECT model_id, '1.0.0', 'Initial production model', model_path, TRUE, TRUE, 'system', CURRENT_TIMESTAMP
FROM ml_models;

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add triggers for updated_at columns
CREATE TRIGGER update_ml_models_updated_at BEFORE UPDATE ON ml_models FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_ml_training_jobs_updated_at BEFORE UPDATE ON ml_training_jobs FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_model_experiments_updated_at BEFORE UPDATE ON model_experiments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create function to clean up old prediction records
CREATE OR REPLACE FUNCTION cleanup_old_predictions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM ml_predictions 
    WHERE prediction_timestamp < NOW() - INTERVAL '90 days';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Create function to archive old training jobs
CREATE OR REPLACE FUNCTION archive_old_training_jobs()
RETURNS INTEGER AS $$
DECLARE
    archived_count INTEGER;
BEGIN
    -- Archive completed training jobs older than 6 months
    UPDATE ml_training_jobs 
    SET job_status = 'ARCHIVED'
    WHERE job_status IN ('COMPLETED', 'FAILED') 
    AND completed_at < NOW() - INTERVAL '6 months';
    
    GET DIAGNOSTICS archived_count = ROW_COUNT;
    RETURN archived_count;
END;
$$ LANGUAGE plpgsql;

-- Add comments to tables
COMMENT ON TABLE ml_models IS 'Registry of all ML models in the system';
COMMENT ON TABLE ml_predictions IS 'Store all model predictions and their outcomes';
COMMENT ON TABLE ml_training_jobs IS 'Track model training jobs and their results';
COMMENT ON TABLE feature_store IS 'Store feature values for model training and inference';
COMMENT ON TABLE model_monitoring IS 'Monitor model performance metrics and alerts';
COMMENT ON TABLE fraud_detection_results IS 'Store fraud detection results and feedback';
COMMENT ON TABLE model_experiments IS 'Track model experiments and A/B tests';
COMMENT ON TABLE model_performance_metrics IS 'Daily/hourly performance metrics for models';
COMMENT ON TABLE data_quality_metrics IS 'Data quality metrics for datasets';
COMMENT ON TABLE model_alerts IS 'Alerts for model performance issues';
COMMENT ON TABLE model_versions IS 'Version control for ML models';