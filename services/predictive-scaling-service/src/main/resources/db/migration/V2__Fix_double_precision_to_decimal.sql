-- Fix CRITICAL: Replace DOUBLE PRECISION with DECIMAL(19,4) for all monetary fields
-- DOUBLE PRECISION should NEVER be used for money due to floating point errors
-- This migration ensures financial accuracy for cost calculations

-- Fix scaling_predictions table cost fields
ALTER TABLE scaling_predictions
    ALTER COLUMN current_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN predicted_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN cost_impact TYPE DECIMAL(19,4);

-- Fix scaling_actions table cost fields
ALTER TABLE scaling_actions
    ALTER COLUMN current_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN target_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN cost_impact_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN estimated_savings TYPE DECIMAL(19,4);

-- Fix metrics_collections table cost fields
ALTER TABLE metrics_collections
    ALTER COLUMN cpu_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN memory_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN storage_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN network_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN total_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN cost_per_transaction TYPE DECIMAL(19,4);

-- Fix cost_optimization_analysis table cost fields
ALTER TABLE cost_optimization_analysis
    ALTER COLUMN current_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN optimized_cost_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN potential_savings_per_hour TYPE DECIMAL(19,4),
    ALTER COLUMN potential_savings_per_month TYPE DECIMAL(19,4);

-- Fix anomaly_detection_results table cost fields
ALTER TABLE anomaly_detection_results
    ALTER COLUMN cost_impact TYPE DECIMAL(19,4);

-- Fix service_configurations table cost fields
ALTER TABLE service_configurations
    ALTER COLUMN max_cost_per_hour TYPE DECIMAL(19,4);

-- Update views that use cost fields
DROP VIEW IF EXISTS service_health_dashboard;
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

COMMENT ON COLUMN scaling_predictions.current_cost_per_hour IS 'Current cost per hour in USD - DECIMAL(19,4) for financial accuracy';
COMMENT ON COLUMN scaling_predictions.predicted_cost_per_hour IS 'Predicted cost per hour in USD - DECIMAL(19,4) for financial accuracy';
COMMENT ON COLUMN scaling_predictions.cost_impact IS 'Cost impact in USD - DECIMAL(19,4) for financial accuracy';

-- Add validation to ensure costs are non-negative
ALTER TABLE scaling_predictions
    ADD CONSTRAINT chk_scaling_predictions_costs_non_negative
    CHECK (current_cost_per_hour >= 0 AND predicted_cost_per_hour >= 0);

ALTER TABLE scaling_actions
    ADD CONSTRAINT chk_scaling_actions_costs_non_negative
    CHECK (current_cost_per_hour >= 0 AND target_cost_per_hour >= 0);

ALTER TABLE metrics_collections
    ADD CONSTRAINT chk_metrics_collections_costs_non_negative
    CHECK (total_cost_per_hour >= 0 OR total_cost_per_hour IS NULL);

ALTER TABLE cost_optimization_analysis
    ADD CONSTRAINT chk_cost_optimization_costs_non_negative
    CHECK (current_cost_per_hour >= 0 AND optimized_cost_per_hour >= 0);
