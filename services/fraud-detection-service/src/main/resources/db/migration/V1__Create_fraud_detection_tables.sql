-- Fraud Detection Service Database Schema
-- Version: 1.0.0
-- Description: Initial schema for fraud detection system

-- Fraud rules table
CREATE TABLE fraud_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(255) NOT NULL UNIQUE,
    rule_type VARCHAR(50) NOT NULL,
    rule_expression TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    score_weight DECIMAL(5,2) NOT NULL DEFAULT 1.0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    description TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Fraud scores table
CREATE TABLE fraud_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transaction_id UUID,
    score DECIMAL(5,2) NOT NULL CHECK (score >= 0 AND score <= 100),
    risk_level VARCHAR(20) NOT NULL CHECK (risk_level IN ('VERY_LOW', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    rules_triggered JSONB,
    factors JSONB,
    model_version VARCHAR(50),
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    INDEX idx_fraud_scores_user_id (user_id),
    INDEX idx_fraud_scores_transaction_id (transaction_id),
    INDEX idx_fraud_scores_calculated_at (calculated_at DESC),
    INDEX idx_fraud_scores_risk_level (risk_level)
);

-- Fraud events table
CREATE TABLE fraud_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL,
    transaction_id UUID,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    description TEXT,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by VARCHAR(255),
    resolution_notes TEXT,
    metadata JSONB,
    
    INDEX idx_fraud_events_user_id (user_id),
    INDEX idx_fraud_events_status (status),
    INDEX idx_fraud_events_detected_at (detected_at DESC),
    INDEX idx_fraud_events_severity (severity)
);

-- Blacklist table
CREATE TABLE fraud_blacklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_type VARCHAR(50) NOT NULL,
    list_value VARCHAR(500) NOT NULL,
    reason TEXT,
    risk_score DECIMAL(5,2),
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    added_by VARCHAR(255),
    metadata JSONB,
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    UNIQUE(list_type, list_value),
    INDEX idx_fraud_blacklist_type_value (list_type, list_value),
    INDEX idx_fraud_blacklist_expires (expires_at)
);

-- Whitelist table
CREATE TABLE fraud_whitelist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    list_type VARCHAR(50) NOT NULL,
    list_value VARCHAR(500) NOT NULL,
    reason TEXT,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    added_by VARCHAR(255),
    metadata JSONB,
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    UNIQUE(list_type, list_value),
    INDEX idx_fraud_whitelist_type_value (list_type, list_value),
    INDEX idx_fraud_whitelist_expires (expires_at)
);

-- Device fingerprints table
CREATE TABLE device_fingerprints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    fingerprint_hash VARCHAR(255) NOT NULL,
    device_type VARCHAR(50),
    os_name VARCHAR(100),
    os_version VARCHAR(50),
    browser_name VARCHAR(100),
    browser_version VARCHAR(50),
    ip_address INET,
    location_country VARCHAR(2),
    location_city VARCHAR(100),
    trust_score DECIMAL(5,2),
    is_trusted BOOLEAN DEFAULT false,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    
    UNIQUE(user_id, device_id),
    INDEX idx_device_fingerprints_user_id (user_id),
    INDEX idx_device_fingerprints_hash (fingerprint_hash),
    INDEX idx_device_fingerprints_trust (trust_score)
);

-- Velocity tracking table
CREATE TABLE velocity_tracking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    metric_type VARCHAR(100) NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    sum_amount DECIMAL(20,2),
    max_amount DECIMAL(20,2),
    unique_values JSONB,
    metadata JSONB,
    
    UNIQUE(user_id, metric_type, window_start),
    INDEX idx_velocity_tracking_user_metric (user_id, metric_type),
    INDEX idx_velocity_tracking_window (window_start, window_end)
);

-- ML model metadata table
CREATE TABLE ml_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name VARCHAR(255) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    model_type VARCHAR(100) NOT NULL,
    accuracy DECIMAL(5,4),
    precision_score DECIMAL(5,4),
    recall_score DECIMAL(5,4),
    f1_score DECIMAL(5,4),
    training_date TIMESTAMP WITH TIME ZONE NOT NULL,
    deployed_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT false,
    model_path TEXT,
    features JSONB,
    hyperparameters JSONB,
    metadata JSONB,
    
    UNIQUE(model_name, model_version),
    INDEX idx_ml_models_active (is_active),
    INDEX idx_ml_models_deployed (deployed_at DESC)
);

-- Fraud patterns table
CREATE TABLE fraud_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern_name VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(100) NOT NULL,
    pattern_signature JSONB NOT NULL,
    confidence_score DECIMAL(5,2),
    occurrences INTEGER DEFAULT 0,
    first_detected TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_detected TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB,
    
    INDEX idx_fraud_patterns_type (pattern_type),
    INDEX idx_fraud_patterns_confidence (confidence_score DESC),
    INDEX idx_fraud_patterns_last_detected (last_detected DESC)
);

-- Graph relationships for fraud rings
CREATE TABLE fraud_graph_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_type VARCHAR(50) NOT NULL,
    node_value VARCHAR(500) NOT NULL,
    risk_score DECIMAL(5,2),
    properties JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(node_type, node_value),
    INDEX idx_fraud_graph_nodes_type (node_type),
    INDEX idx_fraud_graph_nodes_risk (risk_score DESC)
);

CREATE TABLE fraud_graph_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_node_id UUID NOT NULL REFERENCES fraud_graph_nodes(id) ON DELETE CASCADE,
    target_node_id UUID NOT NULL REFERENCES fraud_graph_nodes(id) ON DELETE CASCADE,
    edge_type VARCHAR(100) NOT NULL,
    weight DECIMAL(5,2) DEFAULT 1.0,
    properties JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(source_node_id, target_node_id, edge_type),
    INDEX idx_fraud_graph_edges_source (source_node_id),
    INDEX idx_fraud_graph_edges_target (target_node_id),
    INDEX idx_fraud_graph_edges_type (edge_type)
);

-- Case management table
CREATE TABLE fraud_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_number VARCHAR(50) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    assigned_to VARCHAR(255),
    total_loss DECIMAL(20,2),
    recovered_amount DECIMAL(20,2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    evidence JSONB,
    notes TEXT,
    
    INDEX idx_fraud_cases_user_id (user_id),
    INDEX idx_fraud_cases_status (status),
    INDEX idx_fraud_cases_assigned (assigned_to),
    INDEX idx_fraud_cases_created (created_at DESC)
);

-- Audit trail
CREATE TABLE fraud_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    performed_by VARCHAR(255),
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_values JSONB,
    new_values JSONB,
    metadata JSONB,
    
    INDEX idx_fraud_audit_entity (entity_type, entity_id),
    INDEX idx_fraud_audit_performed (performed_at DESC),
    INDEX idx_fraud_audit_user (performed_by)
);

-- Create update trigger for updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_fraud_rules_updated_at BEFORE UPDATE ON fraud_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_cases_updated_at BEFORE UPDATE ON fraud_cases
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_fraud_graph_nodes_updated_at BEFORE UPDATE ON fraud_graph_nodes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create partitioning for high-volume tables
CREATE TABLE fraud_scores_partitioned (
    LIKE fraud_scores INCLUDING ALL
) PARTITION BY RANGE (calculated_at);

-- Create monthly partitions for fraud scores
CREATE TABLE fraud_scores_2024_01 PARTITION OF fraud_scores_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE fraud_scores_2024_02 PARTITION OF fraud_scores_partitioned
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Add more partitions as needed

-- Grant permissions (adjust as needed)
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO fraud_service_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO fraud_service_app;