-- Predictive Scaling Service Database Schema
-- Advanced ML-powered auto-scaling with comprehensive analytics and cost optimization

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable JSONB operations
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- Enable advanced statistical functions
CREATE EXTENSION IF NOT EXISTS "tablefunc";

-- Scaling Predictions Table
CREATE TABLE scaling_predictions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    prediction_id VARCHAR(50) UNIQUE NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    namespace VARCHAR(100),
    prediction_type VARCHAR(30) NOT NULL,
    time_horizon_minutes INTEGER NOT NULL,
    predicted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    model_version VARCHAR(20),
    model_accuracy DOUBLE PRECISION,
    confidence_score DOUBLE PRECISION NOT NULL,
    prediction_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    
    -- Current state
    current_instances INTEGER NOT NULL,
    current_cpu_utilization DOUBLE PRECISION,
    current_memory_utilization DOUBLE PRECISION,
    current_request_rate DOUBLE PRECISION,
    current_response_time DOUBLE PRECISION,
    
    -- Predicted state
    predicted_instances INTEGER NOT NULL,
    predicted_cpu_utilization DOUBLE PRECISION,
    predicted_memory_utilization DOUBLE PRECISION,
    predicted_request_rate DOUBLE PRECISION,
    predicted_response_time DOUBLE PRECISION,
    predicted_load_score DOUBLE PRECISION,
    
    -- Scaling recommendation
    scaling_action VARCHAR(20),
    recommended_instances INTEGER,
    scaling_magnitude DOUBLE PRECISION,
    scaling_urgency VARCHAR(20),
    scaling_reason TEXT,
    
    -- Features and patterns
    input_features JSONB,
    temporal_features JSONB,
    historical_patterns JSONB,
    prediction_intervals JSONB,
    uncertainty_score DOUBLE PRECISION,
    prediction_variance DOUBLE PRECISION,
    
    -- Cost impact
    current_cost_per_hour DECIMAL(19,4),
    predicted_cost_per_hour DECIMAL(19,4),
    cost_impact DECIMAL(19,4),
    cost_efficiency_score DOUBLE PRECISION,
    
    -- Performance impact
    performance_impact_score DOUBLE PRECISION,
    sla_compliance_probability DOUBLE PRECISION,
    availability_impact DOUBLE PRECISION,
    
    -- External factors
    external_factors JSONB,
    weather_impact BOOLEAN,
    business_event_impact BOOLEAN,
    seasonal_factor DOUBLE PRECISION,
    
    -- Validation
    actual_instances INTEGER,
    actual_cpu_utilization DOUBLE PRECISION,
    actual_memory_utilization DOUBLE PRECISION,
    prediction_accuracy DOUBLE PRECISION,
    mae_instances DOUBLE PRECISION,
    mape_utilization DOUBLE PRECISION,
    validated_at TIMESTAMP,
    
    -- Metadata
    model_metadata JSONB,
    prediction_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Scaling Actions Table
CREATE TABLE scaling_actions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    action_id VARCHAR(50) UNIQUE NOT NULL,
    prediction_id VARCHAR(50),
    service_name VARCHAR(100) NOT NULL,
    namespace VARCHAR(100),
    resource_type VARCHAR(30) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    execution_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL,
    
    -- Current state
    current_replicas INTEGER NOT NULL,
    current_cpu_request VARCHAR(20),
    current_memory_request VARCHAR(20),
    current_cpu_limit VARCHAR(20),
    current_memory_limit VARCHAR(20),
    
    -- Target state
    target_replicas INTEGER NOT NULL,
    target_cpu_request VARCHAR(20),
    target_memory_request VARCHAR(20),
    target_cpu_limit VARCHAR(20),
    target_memory_limit VARCHAR(20),
    
    -- Scaling parameters
    scaling_factor DOUBLE PRECISION,
    scaling_step_size INTEGER,
    max_surge INTEGER,
    max_unavailable INTEGER,
    
    -- Timing
    scheduled_at TIMESTAMP,
    execute_after TIMESTAMP,
    execute_before TIMESTAMP,
    cooldown_period_minutes INTEGER,
    rollback_timeout_minutes INTEGER,
    
    -- Execution details
    initiated_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    duration_seconds BIGINT,
    
    -- Execution context
    executor_type VARCHAR(30),
    execution_method VARCHAR(30),
    execution_parameters JSONB,
    kubernetes_spec JSONB,
    
    -- Safety and validation
    dry_run_performed BOOLEAN DEFAULT false,
    safety_checks_passed BOOLEAN DEFAULT false,
    rollback_plan_ready BOOLEAN DEFAULT false,
    safety_constraints JSONB,
    rollback_plan JSONB,
    
    -- Cost impact
    current_cost_per_hour DECIMAL(19,4),
    target_cost_per_hour DECIMAL(19,4),
    cost_impact_per_hour DECIMAL(19,4),
    estimated_savings DECIMAL(19,4),
    
    -- Performance impact
    performance_impact_score DOUBLE PRECISION,
    availability_risk_score DOUBLE PRECISION,
    sla_compliance_risk DOUBLE PRECISION,
    
    -- Monitoring
    pre_action_metrics_captured BOOLEAN DEFAULT false,
    post_action_metrics_captured BOOLEAN DEFAULT false,
    pre_action_metrics JSONB,
    post_action_metrics JSONB,
    action_effectiveness_score DOUBLE PRECISION,
    
    -- Error handling
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    error_message TEXT,
    error_code VARCHAR(50),
    error_details JSONB,
    
    -- Approval and governance
    requires_approval BOOLEAN DEFAULT false,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    approval_reason TEXT,
    
    -- Audit
    triggered_by VARCHAR(100),
    automation_level VARCHAR(20),
    action_metadata JSONB,
    execution_logs JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Metrics Collections Table
CREATE TABLE metrics_collections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    collection_id VARCHAR(50) UNIQUE NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    namespace VARCHAR(100),
    metric_type VARCHAR(30) NOT NULL,
    collection_source VARCHAR(30) NOT NULL,
    collection_interval_seconds INTEGER NOT NULL,
    aggregation_window_minutes INTEGER NOT NULL,
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    time_range_start TIMESTAMP NOT NULL,
    time_range_end TIMESTAMP NOT NULL,
    
    -- Infrastructure metrics
    cpu_utilization_avg DOUBLE PRECISION,
    cpu_utilization_max DOUBLE PRECISION,
    cpu_utilization_p95 DOUBLE PRECISION,
    memory_utilization_avg DOUBLE PRECISION,
    memory_utilization_max DOUBLE PRECISION,
    memory_utilization_p95 DOUBLE PRECISION,
    disk_io_utilization DOUBLE PRECISION,
    network_io_utilization DOUBLE PRECISION,
    pod_count INTEGER,
    running_pods INTEGER,
    pending_pods INTEGER,
    failed_pods INTEGER,
    node_count INTEGER,
    available_cpu_cores DOUBLE PRECISION,
    available_memory_gb DOUBLE PRECISION,
    
    -- Application metrics
    request_rate_per_second DOUBLE PRECISION,
    response_time_avg_ms DOUBLE PRECISION,
    response_time_p95_ms DOUBLE PRECISION,
    response_time_p99_ms DOUBLE PRECISION,
    error_rate_percentage DOUBLE PRECISION,
    active_connections INTEGER,
    queue_depth INTEGER,
    throughput_ops_per_second DOUBLE PRECISION,
    concurrent_users INTEGER,
    session_count INTEGER,
    
    -- Business metrics
    transaction_volume BIGINT,
    transaction_value_total DOUBLE PRECISION,
    user_activity_score DOUBLE PRECISION,
    payment_processing_rate DOUBLE PRECISION,
    api_call_volume BIGINT,
    successful_transactions BIGINT,
    failed_transactions BIGINT,
    
    -- Cost metrics
    cpu_cost_per_hour DOUBLE PRECISION,
    memory_cost_per_hour DOUBLE PRECISION,
    storage_cost_per_hour DOUBLE PRECISION,
    network_cost_per_hour DOUBLE PRECISION,
    total_cost_per_hour DOUBLE PRECISION,
    cost_per_transaction DOUBLE PRECISION,
    cost_efficiency_score DOUBLE PRECISION,
    
    -- Quality metrics
    availability_percentage DOUBLE PRECISION,
    reliability_score DOUBLE PRECISION,
    performance_score DOUBLE PRECISION,
    sla_compliance_score DOUBLE PRECISION,
    customer_satisfaction_score DOUBLE PRECISION,
    
    -- Detailed data
    detailed_metrics JSONB,
    time_series_data JSONB,
    percentile_data JSONB,
    histogram_data JSONB,
    
    -- Anomaly detection
    anomaly_score DOUBLE PRECISION,
    anomaly_detected BOOLEAN DEFAULT false,
    anomaly_details JSONB,
    
    -- Features for ML
    derived_features JSONB,
    temporal_features JSONB,
    statistical_features JSONB,
    
    -- Data quality
    data_quality_score DOUBLE PRECISION,
    missing_data_percentage DOUBLE PRECISION,
    data_completeness DOUBLE PRECISION,
    collection_errors INTEGER DEFAULT 0,
    collection_metadata JSONB,
    
    -- External context
    external_factors JSONB,
    weather_condition VARCHAR(50),
    is_holiday BOOLEAN DEFAULT false,
    is_weekend BOOLEAN DEFAULT false,
    is_business_hours BOOLEAN DEFAULT true,
    promotional_event_active BOOLEAN DEFAULT false,
    system_maintenance_window BOOLEAN DEFAULT false,
    
    -- Retention
    retention_days INTEGER DEFAULT 90,
    archived BOOLEAN DEFAULT false,
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- ML Models Table
CREATE TABLE ml_models (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    model_id VARCHAR(50) UNIQUE NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    model_type VARCHAR(30) NOT NULL,
    model_version VARCHAR(20) NOT NULL,
    framework VARCHAR(30) NOT NULL,
    algorithm VARCHAR(50) NOT NULL,
    model_status VARCHAR(20) NOT NULL DEFAULT 'TRAINING',
    model_purpose TEXT,
    
    -- Model files
    model_file_path VARCHAR(500),
    model_artifact_url VARCHAR(500),
    model_format VARCHAR(30),
    model_size_bytes BIGINT,
    model_checksum VARCHAR(100),
    
    -- Training configuration
    training_config JSONB,
    hyperparameters JSONB,
    input_features INTEGER,
    output_dimensions INTEGER,
    feature_names JSONB,
    feature_importance JSONB,
    target_variables JSONB,
    
    -- Training data
    training_data_size BIGINT,
    training_data_start_date TIMESTAMP,
    training_data_end_date TIMESTAMP,
    training_samples BIGINT,
    validation_samples BIGINT,
    test_samples BIGINT,
    data_preprocessing JSONB,
    
    -- Training process
    training_started_at TIMESTAMP,
    training_completed_at TIMESTAMP,
    training_duration_seconds BIGINT,
    training_epochs INTEGER,
    training_iterations BIGINT,
    early_stopping_epoch INTEGER,
    training_history JSONB,
    convergence_achieved BOOLEAN DEFAULT false,
    
    -- Performance metrics
    training_accuracy DOUBLE PRECISION,
    validation_accuracy DOUBLE PRECISION,
    test_accuracy DOUBLE PRECISION,
    training_loss DOUBLE PRECISION,
    validation_loss DOUBLE PRECISION,
    test_loss DOUBLE PRECISION,
    mae DOUBLE PRECISION,
    mse DOUBLE PRECISION,
    rmse DOUBLE PRECISION,
    mape DOUBLE PRECISION,
    r2_score DOUBLE PRECISION,
    precision_score DOUBLE PRECISION,
    recall_score DOUBLE PRECISION,
    f1_score DOUBLE PRECISION,
    auc_score DOUBLE PRECISION,
    confusion_matrix JSONB,
    performance_metrics JSONB,
    
    -- Validation
    cross_validation_score DOUBLE PRECISION,
    cross_validation_std DOUBLE PRECISION,
    holdout_validation_score DOUBLE PRECISION,
    validation_results JSONB,
    overfitting_score DOUBLE PRECISION,
    generalization_score DOUBLE PRECISION,
    
    -- Deployment
    deployed_at TIMESTAMP,
    deployment_environment VARCHAR(50),
    serving_endpoint VARCHAR(500),
    prediction_count BIGINT DEFAULT 0,
    successful_predictions BIGINT DEFAULT 0,
    failed_predictions BIGINT DEFAULT 0,
    average_prediction_time_ms DOUBLE PRECISION,
    last_prediction_at TIMESTAMP,
    
    -- Monitoring
    model_drift_score DOUBLE PRECISION,
    data_drift_score DOUBLE PRECISION,
    performance_degradation DOUBLE PRECISION,
    last_monitored_at TIMESTAMP,
    drift_detection_results JSONB,
    requires_retraining BOOLEAN DEFAULT false,
    retraining_threshold DOUBLE PRECISION DEFAULT 0.8,
    
    -- Lifecycle
    model_lifecycle_stage VARCHAR(30) DEFAULT 'DEVELOPMENT',
    predecessor_model_id VARCHAR(50),
    successor_model_id VARCHAR(50),
    retirement_scheduled_at TIMESTAMP,
    retired_at TIMESTAMP,
    retirement_reason TEXT,
    
    -- Metadata
    created_by VARCHAR(100),
    model_description TEXT,
    model_tags JSONB,
    model_metadata JSONB,
    experiment_tracking JSONB,
    
    -- Governance
    model_approval_status VARCHAR(30) DEFAULT 'PENDING',
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,
    compliance_checked BOOLEAN DEFAULT false,
    compliance_results JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Scaling Events Table (for audit and analysis)
CREATE TABLE scaling_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id VARCHAR(50) UNIQUE NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    namespace VARCHAR(100),
    prediction_id VARCHAR(50),
    action_id VARCHAR(50),
    
    -- Event details
    from_replicas INTEGER,
    to_replicas INTEGER,
    scaling_delta INTEGER,
    scaling_percentage DOUBLE PRECISION,
    trigger_reason TEXT,
    execution_duration_seconds BIGINT,
    
    -- Performance impact
    performance_before JSONB,
    performance_after JSONB,
    performance_improvement DOUBLE PRECISION,
    
    -- Cost impact
    cost_before DECIMAL(19,4),
    cost_after DECIMAL(19,4),
    cost_savings DECIMAL(19,4),
    
    -- Success metrics
    success BOOLEAN,
    error_code VARCHAR(50),
    error_message TEXT,
    
    -- Context
    business_context JSONB,
    external_factors JSONB,
    model_confidence DOUBLE PRECISION,
    automation_level VARCHAR(20),
    
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Cost Optimization Analysis Table
CREATE TABLE cost_optimization_analysis (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    analysis_id VARCHAR(50) UNIQUE NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    namespace VARCHAR(100),
    analysis_type VARCHAR(30) NOT NULL,
    analysis_period_days INTEGER NOT NULL,
    analysis_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Current state
    current_replicas INTEGER,
    current_cpu_request VARCHAR(20),
    current_memory_request VARCHAR(20),
    current_cpu_limit VARCHAR(20),
    current_memory_limit VARCHAR(20),
    current_cost_per_hour DECIMAL(19,4),
    current_efficiency_score DOUBLE PRECISION,
    
    -- Optimization opportunities
    recommended_replicas INTEGER,
    recommended_cpu_request VARCHAR(20),
    recommended_memory_request VARCHAR(20),
    recommended_cpu_limit VARCHAR(20),
    recommended_memory_limit VARCHAR(20),
    optimized_cost_per_hour DECIMAL(19,4),
    potential_savings_per_hour DECIMAL(19,4),
    potential_savings_per_month DECIMAL(19,4),
    optimization_confidence DOUBLE PRECISION,
    
    -- Utilization analysis
    avg_cpu_utilization DOUBLE PRECISION,
    avg_memory_utilization DOUBLE PRECISION,
    p95_cpu_utilization DOUBLE PRECISION,
    p95_memory_utilization DOUBLE PRECISION,
    waste_percentage DOUBLE PRECISION,
    overprovisioning_factor DOUBLE PRECISION,
    
    -- Recommendations
    optimization_type VARCHAR(30),
    recommendation_priority VARCHAR(20),
    implementation_complexity VARCHAR(20),
    risk_assessment VARCHAR(20),
    estimated_effort_hours INTEGER,
    
    -- Right-sizing details
    rightsizing_recommendations JSONB,
    spot_instance_opportunities JSONB,
    reserved_instance_recommendations JSONB,
    
    -- Analysis metadata
    analysis_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Anomaly Detection Results Table
CREATE TABLE anomaly_detection_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    anomaly_id VARCHAR(50) UNIQUE NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    namespace VARCHAR(100),
    anomaly_type VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    detection_method VARCHAR(30) NOT NULL,
    anomaly_score DOUBLE PRECISION NOT NULL,
    confidence_score DOUBLE PRECISION NOT NULL,
    
    -- Anomaly details
    metric_name VARCHAR(100),
    anomaly_value DOUBLE PRECISION,
    expected_value DOUBLE PRECISION,
    deviation_percentage DOUBLE PRECISION,
    anomaly_description TEXT,
    
    -- Detection context
    detection_window_minutes INTEGER,
    baseline_period_hours INTEGER,
    detection_threshold DOUBLE PRECISION,
    statistical_significance DOUBLE PRECISION,
    
    -- Time context
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    anomaly_start_time TIMESTAMP,
    anomaly_end_time TIMESTAMP,
    duration_minutes INTEGER,
    
    -- Impact assessment
    business_impact VARCHAR(20),
    performance_impact DOUBLE PRECISION,
    cost_impact DECIMAL(19,4),
    sla_breach_risk DOUBLE PRECISION,
    
    -- Response
    response_triggered BOOLEAN DEFAULT false,
    response_type VARCHAR(30),
    scaling_action_id VARCHAR(50),
    resolution_status VARCHAR(20) DEFAULT 'OPEN',
    resolved_at TIMESTAMP,
    
    -- Analysis data
    anomaly_data JSONB,
    model_explanation JSONB,
    feature_contributions JSONB,
    
    -- Metadata
    detection_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Service Configuration Table
CREATE TABLE service_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_name VARCHAR(100) NOT NULL,
    namespace VARCHAR(100) NOT NULL,
    
    -- Scaling configuration
    min_replicas INTEGER NOT NULL DEFAULT 1,
    max_replicas INTEGER NOT NULL DEFAULT 10,
    target_cpu_utilization INTEGER DEFAULT 70,
    target_memory_utilization INTEGER DEFAULT 80,
    scale_up_cooldown_seconds INTEGER DEFAULT 300,
    scale_down_cooldown_seconds INTEGER DEFAULT 600,
    
    -- Prediction configuration
    prediction_enabled BOOLEAN DEFAULT true,
    prediction_horizon_minutes INTEGER DEFAULT 30,
    prediction_confidence_threshold DOUBLE PRECISION DEFAULT 0.7,
    model_id VARCHAR(50),
    
    -- Cost optimization
    cost_optimization_enabled BOOLEAN DEFAULT true,
    cost_efficiency_target DOUBLE PRECISION DEFAULT 0.8,
    max_cost_per_hour DECIMAL(19,4),
    
    -- Performance constraints
    max_response_time_ms INTEGER DEFAULT 2000,
    max_error_rate_percentage DOUBLE PRECISION DEFAULT 5.0,
    min_availability_percentage DOUBLE PRECISION DEFAULT 99.9,
    
    -- Business rules
    business_hours_start INTEGER DEFAULT 8,
    business_hours_end INTEGER DEFAULT 18,
    peak_hours_multiplier DOUBLE PRECISION DEFAULT 1.5,
    off_hours_scale_down BOOLEAN DEFAULT true,
    
    -- Safety constraints
    max_scale_up_percentage DOUBLE PRECISION DEFAULT 50,
    max_scale_down_percentage DOUBLE PRECISION DEFAULT 25,
    circuit_breaker_enabled BOOLEAN DEFAULT true,
    emergency_scale_up_enabled BOOLEAN DEFAULT true,
    
    -- Monitoring
    metrics_collection_enabled BOOLEAN DEFAULT true,
    anomaly_detection_enabled BOOLEAN DEFAULT true,
    alerting_enabled BOOLEAN DEFAULT true,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    UNIQUE(service_name, namespace)
);

-- Foreign Key Constraints
ALTER TABLE scaling_actions ADD CONSTRAINT fk_scaling_actions_prediction_id 
    FOREIGN KEY (prediction_id) REFERENCES scaling_predictions(prediction_id);

ALTER TABLE scaling_events ADD CONSTRAINT fk_scaling_events_prediction_id 
    FOREIGN KEY (prediction_id) REFERENCES scaling_predictions(prediction_id);

ALTER TABLE scaling_events ADD CONSTRAINT fk_scaling_events_action_id 
    FOREIGN KEY (action_id) REFERENCES scaling_actions(action_id);

-- Indexes for Performance
CREATE INDEX idx_scaling_predictions_service_namespace ON scaling_predictions(service_name, namespace);
CREATE INDEX idx_scaling_predictions_status ON scaling_predictions(prediction_status);
CREATE INDEX idx_scaling_predictions_predicted_at ON scaling_predictions(predicted_at);
CREATE INDEX idx_scaling_predictions_valid_until ON scaling_predictions(valid_until);
CREATE INDEX idx_scaling_predictions_confidence ON scaling_predictions(confidence_score);

CREATE INDEX idx_scaling_actions_service_namespace ON scaling_actions(service_name, namespace);
CREATE INDEX idx_scaling_actions_status ON scaling_actions(execution_status);
CREATE INDEX idx_scaling_actions_scheduled_at ON scaling_actions(scheduled_at);
CREATE INDEX idx_scaling_actions_priority ON scaling_actions(priority);

CREATE INDEX idx_metrics_collections_service_namespace ON metrics_collections(service_name, namespace);
CREATE INDEX idx_metrics_collections_collected_at ON metrics_collections(collected_at);
CREATE INDEX idx_metrics_collections_type ON metrics_collections(metric_type);
CREATE INDEX idx_metrics_collections_source ON metrics_collections(collection_source);

CREATE INDEX idx_ml_models_status ON ml_models(model_status);
CREATE INDEX idx_ml_models_type ON ml_models(model_type);
CREATE INDEX idx_ml_models_accuracy ON ml_models(test_accuracy);
CREATE INDEX idx_ml_models_deployed_at ON ml_models(deployed_at);

CREATE INDEX idx_scaling_events_service_namespace ON scaling_events(service_name, namespace);
CREATE INDEX idx_scaling_events_occurred_at ON scaling_events(occurred_at);
CREATE INDEX idx_scaling_events_event_type ON scaling_events(event_type);

CREATE INDEX idx_cost_optimization_service_namespace ON cost_optimization_analysis(service_name, namespace);
CREATE INDEX idx_cost_optimization_analysis_date ON cost_optimization_analysis(analysis_date);
CREATE INDEX idx_cost_optimization_potential_savings ON cost_optimization_analysis(potential_savings_per_hour);

CREATE INDEX idx_anomaly_detection_service_namespace ON anomaly_detection_results(service_name, namespace);
CREATE INDEX idx_anomaly_detection_detected_at ON anomaly_detection_results(detected_at);
CREATE INDEX idx_anomaly_detection_severity ON anomaly_detection_results(severity);
CREATE INDEX idx_anomaly_detection_resolution_status ON anomaly_detection_results(resolution_status);

CREATE INDEX idx_service_configurations_service_namespace ON service_configurations(service_name, namespace);

-- JSONB Indexes for better query performance
CREATE INDEX idx_scaling_predictions_input_features_gin ON scaling_predictions USING gin(input_features);
CREATE INDEX idx_scaling_predictions_temporal_features_gin ON scaling_predictions USING gin(temporal_features);
CREATE INDEX idx_scaling_actions_execution_parameters_gin ON scaling_actions USING gin(execution_parameters);
CREATE INDEX idx_metrics_collections_detailed_metrics_gin ON metrics_collections USING gin(detailed_metrics);
CREATE INDEX idx_ml_models_hyperparameters_gin ON ml_models USING gin(hyperparameters);

-- Partial Indexes for common queries
CREATE INDEX idx_scaling_predictions_active ON scaling_predictions(service_name, namespace, predicted_at) 
    WHERE prediction_status = 'ACTIVE';

CREATE INDEX idx_scaling_actions_pending ON scaling_actions(service_name, namespace, scheduled_at) 
    WHERE execution_status IN ('PENDING', 'SCHEDULED', 'APPROVED');

CREATE INDEX idx_metrics_collections_recent ON metrics_collections(service_name, namespace, collected_at) 
    WHERE collected_at > CURRENT_TIMESTAMP - INTERVAL '24 hours';

CREATE INDEX idx_anomaly_detection_open ON anomaly_detection_results(service_name, namespace, detected_at) 
    WHERE resolution_status = 'OPEN';

-- Functions for automatic timestamp updates
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_scaling_predictions_updated_at BEFORE UPDATE ON scaling_predictions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_scaling_actions_updated_at BEFORE UPDATE ON scaling_actions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_metrics_collections_updated_at BEFORE UPDATE ON metrics_collections 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ml_models_updated_at BEFORE UPDATE ON ml_models 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cost_optimization_analysis_updated_at BEFORE UPDATE ON cost_optimization_analysis 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_anomaly_detection_results_updated_at BEFORE UPDATE ON anomaly_detection_results 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_service_configurations_updated_at BEFORE UPDATE ON service_configurations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Views for common queries

-- Active Scaling Recommendations View
CREATE VIEW active_scaling_recommendations AS
SELECT 
    sp.prediction_id,
    sp.service_name,
    sp.namespace,
    sp.scaling_action,
    sp.recommended_instances,
    sp.current_instances,
    sp.confidence_score,
    sp.scaling_urgency,
    sp.cost_impact,
    sp.predicted_at,
    sp.valid_until,
    CASE 
        WHEN sp.scaling_urgency = 'IMMEDIATE' THEN 1
        WHEN sp.scaling_urgency = 'HIGH' THEN 2
        WHEN sp.scaling_urgency = 'MEDIUM' THEN 3
        ELSE 4
    END as priority_order
FROM scaling_predictions sp
WHERE sp.prediction_status = 'ACTIVE'
AND sp.valid_until > CURRENT_TIMESTAMP
AND sp.confidence_score >= 0.7
AND sp.scaling_action != 'MAINTAIN'
ORDER BY priority_order, sp.confidence_score DESC;

-- Service Health Dashboard View
CREATE VIEW service_health_dashboard AS
SELECT 
    sc.service_name,
    sc.namespace,
    sc.min_replicas,
    sc.max_replicas,
    COALESCE(mc.pod_count, 0) as current_replicas,
    COALESCE(mc.cpu_utilization_avg, 0) as cpu_utilization,
    COALESCE(mc.memory_utilization_avg, 0) as memory_utilization,
    COALESCE(mc.response_time_p95_ms, 0) as response_time_p95,
    COALESCE(mc.error_rate_percentage, 0) as error_rate,
    COALESCE(mc.availability_percentage, 100) as availability,
    COALESCE(mc.total_cost_per_hour, 0) as cost_per_hour,
    sp.prediction_id as latest_prediction_id,
    sp.scaling_action as recommended_action,
    sp.confidence_score as prediction_confidence,
    COUNT(CASE WHEN ad.severity IN ('HIGH', 'CRITICAL') THEN 1 END) as critical_anomalies
FROM service_configurations sc
LEFT JOIN LATERAL (
    SELECT * FROM metrics_collections 
    WHERE service_name = sc.service_name 
    AND namespace = sc.namespace 
    ORDER BY collected_at DESC 
    LIMIT 1
) mc ON true
LEFT JOIN LATERAL (
    SELECT * FROM scaling_predictions 
    WHERE service_name = sc.service_name 
    AND namespace = sc.namespace 
    AND prediction_status = 'ACTIVE'
    ORDER BY predicted_at DESC 
    LIMIT 1
) sp ON true
LEFT JOIN anomaly_detection_results ad ON ad.service_name = sc.service_name 
    AND ad.namespace = sc.namespace 
    AND ad.detected_at > CURRENT_TIMESTAMP - INTERVAL '1 hour'
    AND ad.resolution_status = 'OPEN'
GROUP BY sc.service_name, sc.namespace, sc.min_replicas, sc.max_replicas,
         mc.pod_count, mc.cpu_utilization_avg, mc.memory_utilization_avg,
         mc.response_time_p95_ms, mc.error_rate_percentage, mc.availability_percentage,
         mc.total_cost_per_hour, sp.prediction_id, sp.scaling_action, sp.confidence_score;

-- Cost Savings Summary View
CREATE VIEW cost_savings_summary AS
SELECT 
    DATE(se.occurred_at) as savings_date,
    se.service_name,
    se.namespace,
    COUNT(*) as scaling_events,
    SUM(CASE WHEN se.cost_savings > 0 THEN se.cost_savings ELSE 0 END) as total_savings,
    AVG(CASE WHEN se.cost_savings > 0 THEN se.cost_savings ELSE NULL END) as avg_savings_per_event,
    SUM(CASE WHEN se.scaling_delta > 0 THEN 1 ELSE 0 END) as scale_up_events,
    SUM(CASE WHEN se.scaling_delta < 0 THEN 1 ELSE 0 END) as scale_down_events,
    AVG(se.performance_improvement) as avg_performance_improvement
FROM scaling_events se
WHERE se.occurred_at >= CURRENT_DATE - INTERVAL '30 days'
AND se.success = true
GROUP BY DATE(se.occurred_at), se.service_name, se.namespace
ORDER BY savings_date DESC, total_savings DESC;

-- ML Model Performance Summary View
CREATE VIEW ml_model_performance_summary AS
SELECT 
    m.model_id,
    m.model_name,
    m.model_type,
    m.algorithm,
    m.model_status,
    m.test_accuracy,
    m.prediction_count,
    m.successful_predictions,
    CASE 
        WHEN m.prediction_count > 0 THEN 
            ROUND((m.successful_predictions::numeric / m.prediction_count * 100), 2)
        ELSE NULL 
    END as success_rate_percentage,
    m.average_prediction_time_ms,
    m.model_drift_score,
    m.data_drift_score,
    m.requires_retraining,
    m.deployed_at,
    m.last_prediction_at,
    CASE 
        WHEN m.test_accuracy >= 0.9 AND m.model_drift_score < 0.3 THEN 'EXCELLENT'
        WHEN m.test_accuracy >= 0.8 AND m.model_drift_score < 0.5 THEN 'GOOD'
        WHEN m.test_accuracy >= 0.7 AND m.model_drift_score < 0.7 THEN 'FAIR'
        ELSE 'POOR'
    END as performance_rating
FROM ml_models m
WHERE m.model_status IN ('DEPLOYED', 'SERVING', 'MONITORING')
ORDER BY m.test_accuracy DESC, m.prediction_count DESC;

-- Functions for advanced analytics

-- Function to calculate prediction accuracy over time
CREATE OR REPLACE FUNCTION calculate_prediction_accuracy(
    p_service_name VARCHAR(100),
    p_namespace VARCHAR(100),
    p_days INTEGER DEFAULT 30
) RETURNS TABLE (
    date_period DATE,
    total_predictions BIGINT,
    validated_predictions BIGINT,
    avg_accuracy DOUBLE PRECISION,
    avg_confidence DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        DATE(sp.predicted_at) as date_period,
        COUNT(*)::BIGINT as total_predictions,
        COUNT(CASE WHEN sp.prediction_accuracy IS NOT NULL THEN 1 END)::BIGINT as validated_predictions,
        AVG(sp.prediction_accuracy) as avg_accuracy,
        AVG(sp.confidence_score) as avg_confidence
    FROM scaling_predictions sp
    WHERE sp.service_name = p_service_name
    AND sp.namespace = p_namespace
    AND sp.predicted_at >= CURRENT_DATE - INTERVAL '1 day' * p_days
    GROUP BY DATE(sp.predicted_at)
    ORDER BY date_period DESC;
END;
$$ LANGUAGE plpgsql;

-- Function to get service scaling history
CREATE OR REPLACE FUNCTION get_service_scaling_history(
    p_service_name VARCHAR(100),
    p_namespace VARCHAR(100),
    p_hours INTEGER DEFAULT 24
) RETURNS TABLE (
    event_time TIMESTAMP,
    from_replicas INTEGER,
    to_replicas INTEGER,
    trigger_reason TEXT,
    success BOOLEAN,
    cost_impact DECIMAL(19,4),
    performance_improvement DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        se.occurred_at as event_time,
        se.from_replicas,
        se.to_replicas,
        se.trigger_reason,
        se.success,
        se.cost_savings as cost_impact,
        se.performance_improvement
    FROM scaling_events se
    WHERE se.service_name = p_service_name
    AND se.namespace = p_namespace
    AND se.occurred_at >= CURRENT_TIMESTAMP - INTERVAL '1 hour' * p_hours
    ORDER BY se.occurred_at DESC;
END;
$$ LANGUAGE plpgsql;

-- Comments for documentation
COMMENT ON TABLE scaling_predictions IS 'ML-generated predictions for service scaling needs';
COMMENT ON TABLE scaling_actions IS 'Scaling actions to be executed or already completed';
COMMENT ON TABLE metrics_collections IS 'Collected metrics from various sources for ML analysis';
COMMENT ON TABLE ml_models IS 'Machine learning models used for predictions';
COMMENT ON TABLE scaling_events IS 'Audit log of all scaling events and their outcomes';
COMMENT ON TABLE cost_optimization_analysis IS 'Cost optimization analysis and recommendations';
COMMENT ON TABLE anomaly_detection_results IS 'Detected anomalies in service metrics';
COMMENT ON TABLE service_configurations IS 'Configuration settings for each service';

-- Grant permissions (adjust based on your application user)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO predictive_scaling_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO predictive_scaling_user;