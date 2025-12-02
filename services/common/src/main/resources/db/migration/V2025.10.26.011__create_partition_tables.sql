-- Refresh schedule for materialized views (run via cron or scheduler)
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_employee_training_summary;
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_phishing_campaign_performance;
-- REFRESH MATERIALIZED VIEW CONCURRENTLY mv_quarterly_assessment_stats;

-- ============================================================================
-- V2025.10.26.011__create_partition_tables.sql
-- Table Partitioning for Large Tables
-- ============================================================================

-- Partition audit logs by month for better performance
CREATE TABLE security_awareness_audit_logs_partitioned (
                                                           LIKE security_awareness_audit_logs INCLUDING ALL
) PARTITION BY RANGE (timestamp);

-- Create partitions for current year
CREATE TABLE security_awareness_audit_logs_2025_q1
    PARTITION OF security_awareness_audit_logs_partitioned
    FOR VALUES FROM ('2025-01-01') TO ('2025-04-01');

CREATE TABLE security_awareness_audit_logs_2025_q2
    PARTITION OF security_awareness_audit_logs_partitioned
    FOR VALUES FROM ('2025-04-01') TO ('2025-07-01');

CREATE TABLE security_awareness_audit_logs_2025_q3
    PARTITION OF security_awareness_audit_logs_partitioned
    FOR VALUES FROM ('2025-07-01') TO ('2025-10-01');

CREATE TABLE security_awareness_audit_logs_2025_q4
    PARTITION OF security_awareness_audit_logs_partitioned
    FOR VALUES FROM ('2025-10-01') TO ('2026-01-01');