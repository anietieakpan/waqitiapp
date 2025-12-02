-- Rollback script for V2__Fix_double_precision_to_decimal.sql
-- WARNING: This rolls back CRITICAL financial precision fixes
-- Should only be used in emergency situations

-- Drop constraints first
ALTER TABLE cost_optimization_analysis DROP CONSTRAINT IF EXISTS chk_cost_optimization_costs_non_negative;
ALTER TABLE metrics_collections DROP CONSTRAINT IF EXISTS chk_metrics_collections_costs_non_negative;
ALTER TABLE scaling_actions DROP CONSTRAINT IF EXISTS chk_scaling_actions_costs_non_negative;
ALTER TABLE scaling_predictions DROP CONSTRAINT IF EXISTS chk_scaling_predictions_costs_non_negative;

-- Rollback scaling_predictions table
ALTER TABLE scaling_predictions
    ALTER COLUMN current_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN predicted_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN cost_impact TYPE DOUBLE PRECISION;

-- Rollback scaling_actions table
ALTER TABLE scaling_actions
    ALTER COLUMN current_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN target_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN cost_impact_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN estimated_savings TYPE DOUBLE PRECISION;

-- Rollback metrics_collections table
ALTER TABLE metrics_collections
    ALTER COLUMN cpu_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN memory_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN storage_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN network_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN total_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN cost_per_transaction TYPE DOUBLE PRECISION;

-- Rollback cost_optimization_analysis table
ALTER TABLE cost_optimization_analysis
    ALTER COLUMN current_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN optimized_cost_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN potential_savings_per_hour TYPE DOUBLE PRECISION,
    ALTER COLUMN potential_savings_per_month TYPE DOUBLE PRECISION;

-- Rollback anomaly_detection_results table
ALTER TABLE anomaly_detection_results
    ALTER COLUMN cost_impact TYPE DOUBLE PRECISION;

-- Rollback service_configurations table
ALTER TABLE service_configurations
    ALTER COLUMN max_cost_per_hour TYPE DOUBLE PRECISION;

-- Recreate view with original types
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
