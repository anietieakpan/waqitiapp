-- ============================================================================
-- V2025.10.26.010__create_materialized_views.sql
-- Materialized Views for Performance
-- ============================================================================

-- Employee training completion summary
CREATE MATERIALIZED VIEW mv_employee_training_summary AS
SELECT
    e.id as employee_id,
    e.email,
    e.first_name,
    e.last_name,
    COUNT(etr.id) as total_assigned,
    COUNT(CASE WHEN etr.status = 'COMPLETED' THEN 1 END) as completed,
    COUNT(CASE WHEN etr.status = 'FAILED' THEN 1 END) as failed,
    COUNT(CASE WHEN etr.status = 'NOT_STARTED' THEN 1 END) as not_started,
    ROUND(
            CASE
                WHEN COUNT(etr.id) > 0 THEN
                    (COUNT(CASE WHEN etr.status = 'COMPLETED' THEN 1 END)::DECIMAL / COUNT(etr.id)) * 100
                ELSE 0
                END, 2
    ) as completion_percentage,
    MAX(etr.completed_at) as last_training_completed
FROM employees e
         LEFT JOIN employee_training_records etr ON e.id = etr.employee_id
WHERE e.is_active = true
GROUP BY e.id, e.email, e.first_name, e.last_name;

CREATE UNIQUE INDEX idx_mv_training_summary_employee ON mv_employee_training_summary(employee_id);

-- Phishing campaign performance summary
CREATE MATERIALIZED VIEW mv_phishing_campaign_performance AS
SELECT
    psc.id as campaign_id,
    psc.campaign_name,
    psc.template_type,
    psc.status,
    psc.total_targeted,
    psc.total_delivered,
    psc.total_opened,
    psc.total_clicked,
    psc.total_submitted_data,
    psc.total_reported,
    ROUND(
            CASE
                WHEN psc.total_delivered > 0 THEN
                    (psc.total_opened::DECIMAL / psc.total_delivered) * 100
                ELSE 0
                END, 2
    ) as open_rate,
    ROUND(
            CASE
                WHEN psc.total_delivered > 0 THEN
                    (psc.total_clicked::DECIMAL / psc.total_delivered) * 100
                ELSE 0
                END, 2
    ) as click_rate,
    ROUND(
            CASE
                WHEN psc.total_delivered > 0 THEN
                    (psc.total_submitted_data::DECIMAL / psc.total_delivered) * 100
                ELSE 0
                END, 2
    ) as submission_rate,
    ROUND(
            CASE
                WHEN psc.total_delivered > 0 THEN
                    (psc.total_reported::DECIMAL / psc.total_delivered) * 100
                ELSE 0
                END, 2
    ) as report_rate,
    psc.created_at,
    psc.actual_start,
    psc.actual_end
FROM phishing_simulation_campaigns psc;

CREATE UNIQUE INDEX idx_mv_phishing_performance_campaign ON mv_phishing_campaign_performance(campaign_id);

-- Quarterly assessment statistics
CREATE MATERIALIZED VIEW mv_quarterly_assessment_stats AS
SELECT
    qsa.id as assessment_id,
    qsa.assessment_name,
    qsa.quarter,
    qsa.year,
    qsa.assessment_type,
    qsa.status,
    COUNT(DISTINCT ar.employee_id) as total_participants,
    COUNT(CASE WHEN ar.completed_at IS NOT NULL THEN 1 END) as total_completed,
    COUNT(CASE WHEN ar.passed = true THEN 1 END) as total_passed,
    COUNT(CASE WHEN ar.passed = false THEN 1 END) as total_failed,
    ROUND(AVG(ar.score_percentage), 2) as average_score,
    ROUND(
            CASE
                WHEN COUNT(DISTINCT ar.employee_id) > 0 THEN
                    (COUNT(CASE WHEN ar.completed_at IS NOT NULL THEN 1 END)::DECIMAL /
                 COUNT(DISTINCT ar.employee_id)) * 100
                ELSE 0
                END, 2
    ) as completion_rate,
    ROUND(
            CASE
                WHEN COUNT(CASE WHEN ar.completed_at IS NOT NULL THEN 1 END) > 0 THEN
                    (COUNT(CASE WHEN ar.passed = true THEN 1 END)::DECIMAL /
                 COUNT(CASE WHEN ar.completed_at IS NOT NULL THEN 1 END)) * 100
                ELSE 0
                END, 2
    ) as pass_rate
FROM quarterly_security_assessments qsa
         LEFT JOIN assessment_results ar ON qsa.id = ar.assessment_id
GROUP BY qsa.id, qsa.assessment_name, qsa.quarter, qsa.year, qsa.assessment_type, qsa.status;

CREATE UNIQUE INDEX idx_mv_assessment_stats_assessment ON mv_quarterly_assessment_stats(assessment_id);