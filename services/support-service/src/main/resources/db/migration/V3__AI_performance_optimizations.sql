-- AI performance optimizations and additional features
-- Creates indexes, views, and stored procedures for better AI chatbot performance

-- Add missing fields to support_tickets for AI analysis
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS ai_analysis_completed BOOLEAN DEFAULT FALSE;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS ai_confidence_score DECIMAL(5,4);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS ai_suggested_category VARCHAR(50);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS ai_suggested_priority VARCHAR(20);
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS resolution_time_minutes INTEGER;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS reopened_count INTEGER DEFAULT 0;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS is_vip BOOLEAN DEFAULT FALSE;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS first_response_time_minutes INTEGER;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS satisfaction_score INTEGER;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS is_escalated BOOLEAN DEFAULT FALSE;
ALTER TABLE support_tickets ADD COLUMN IF NOT EXISTS sla_breach_at TIMESTAMP WITH TIME ZONE;

-- Add additional fields to ticket_messages for AI processing
ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS ai_processed BOOLEAN DEFAULT FALSE;
ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS detected_intent VARCHAR(100);
ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS sentiment VARCHAR(20);
ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS entities JSONB;
ALTER TABLE ticket_messages ADD COLUMN IF NOT EXISTS auto_generated BOOLEAN DEFAULT FALSE;

-- Performance indexes for AI queries
CREATE INDEX IF NOT EXISTS idx_support_tickets_ai_analysis ON support_tickets(ai_analysis_completed, created_at);
CREATE INDEX IF NOT EXISTS idx_support_tickets_resolution_time ON support_tickets(category, priority, resolution_time_minutes) WHERE resolution_time_minutes IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_support_tickets_similar_search ON support_tickets(category, status, ai_confidence_score);
CREATE INDEX IF NOT EXISTS idx_support_tickets_sla_breach ON support_tickets(sla_breach_at) WHERE sla_breach_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_messages_ai_processing ON ticket_messages(ai_processed, created_at);
CREATE INDEX IF NOT EXISTS idx_ticket_messages_intent ON ticket_messages(detected_intent) WHERE detected_intent IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ticket_messages_sentiment ON ticket_messages(sentiment) WHERE sentiment IS NOT NULL;

-- Materialized view for AI training data
CREATE MATERIALIZED VIEW IF NOT EXISTS ai_training_data AS
SELECT 
    t.id as ticket_id,
    t.subject,
    t.description,
    t.category,
    t.priority,
    t.status,
    t.resolution_summary as resolution,
    t.resolution_time_minutes,
    t.satisfaction_score,
    tm.content as customer_message,
    tm.detected_intent,
    tm.sentiment,
    ta.suggested_category,
    ta.suggested_priority,
    ta.estimated_resolution_time
FROM support_tickets t
LEFT JOIN ticket_messages tm ON t.id = tm.ticket_id AND tm.message_type = 'CUSTOMER'
LEFT JOIN ticket_ai_analysis ta ON t.id = ta.ticket_id AND ta.is_current = true
WHERE t.status IN ('RESOLVED', 'CLOSED')
AND t.satisfaction_score IS NOT NULL
AND t.resolution_time_minutes IS NOT NULL;

-- Create indexes on the materialized view
CREATE INDEX IF NOT EXISTS idx_ai_training_data_category ON ai_training_data(category);
CREATE INDEX IF NOT EXISTS idx_ai_training_data_priority ON ai_training_data(priority);
CREATE INDEX IF NOT EXISTS idx_ai_training_data_satisfaction ON ai_training_data(satisfaction_score);

-- View for agent workload and performance
CREATE VIEW agent_performance AS
SELECT 
    sa.id as agent_id,
    sa.display_name,
    sa.status,
    COUNT(t.id) as total_tickets,
    COUNT(CASE WHEN t.status = 'OPEN' THEN 1 END) as open_tickets,
    COUNT(CASE WHEN t.status = 'RESOLVED' THEN 1 END) as resolved_tickets,
    AVG(t.resolution_time_minutes) as avg_resolution_time,
    AVG(t.satisfaction_score::DECIMAL) as avg_satisfaction,
    COUNT(CASE WHEN t.created_at >= CURRENT_DATE - INTERVAL '30 days' THEN 1 END) as tickets_last_30_days
FROM support_agents sa
LEFT JOIN support_tickets t ON sa.user_id::text = t.assigned_to::text
GROUP BY sa.id, sa.display_name, sa.status;

-- View for knowledge base analytics
CREATE VIEW kb_analytics AS
SELECT 
    ka.id,
    ka.title,
    ka.category_id,
    kc.name as category_name,
    ka.view_count,
    ka.helpful_votes,
    ka.not_helpful_votes,
    CASE 
        WHEN (ka.helpful_votes + ka.not_helpful_votes) > 0 
        THEN ROUND((ka.helpful_votes::DECIMAL / (ka.helpful_votes + ka.not_helpful_votes) * 100), 2)
        ELSE 0 
    END as helpfulness_percentage,
    ka.average_rating,
    ka.total_ratings,
    ka.created_at,
    ka.updated_at
FROM kb_articles ka
LEFT JOIN kb_categories kc ON ka.category_id = kc.id
WHERE ka.is_published = true;

-- Stored procedure for calculating ticket resolution time
CREATE OR REPLACE FUNCTION calculate_resolution_time()
RETURNS TRIGGER AS $$
BEGIN
    -- Calculate resolution time when ticket is resolved
    IF NEW.status = 'RESOLVED' AND OLD.status != 'RESOLVED' THEN
        NEW.resolved_at = CURRENT_TIMESTAMP;
        NEW.resolution_time_minutes = EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - NEW.created_at)) / 60;
    END IF;
    
    -- Calculate first response time
    IF NEW.first_response_at IS NULL AND EXISTS (
        SELECT 1 FROM ticket_messages 
        WHERE ticket_id = NEW.id 
        AND message_type = 'AGENT' 
        AND created_at > NEW.created_at
    ) THEN
        SELECT MIN(created_at) INTO NEW.first_response_at
        FROM ticket_messages 
        WHERE ticket_id = NEW.id 
        AND message_type = 'AGENT';
        
        NEW.first_response_time_minutes = EXTRACT(EPOCH FROM (NEW.first_response_at - NEW.created_at)) / 60;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply the trigger
DROP TRIGGER IF EXISTS calculate_resolution_time_trigger ON support_tickets;
CREATE TRIGGER calculate_resolution_time_trigger
    BEFORE UPDATE ON support_tickets
    FOR EACH ROW
    EXECUTE FUNCTION calculate_resolution_time();

-- Stored procedure for automatic ticket escalation
CREATE OR REPLACE FUNCTION check_sla_breach()
RETURNS void AS $$
BEGIN
    -- Mark tickets that have breached SLA
    UPDATE support_tickets SET 
        sla_breach_at = CURRENT_TIMESTAMP,
        is_escalated = true
    WHERE status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED')
    AND sla_breach_at IS NULL
    AND (
        (priority = 'CRITICAL' AND created_at < CURRENT_TIMESTAMP - INTERVAL '4 hours') OR
        (priority = 'HIGH' AND created_at < CURRENT_TIMESTAMP - INTERVAL '8 hours') OR
        (priority = 'MEDIUM' AND created_at < CURRENT_TIMESTAMP - INTERVAL '24 hours') OR
        (priority = 'LOW' AND created_at < CURRENT_TIMESTAMP - INTERVAL '48 hours')
    );
END;
$$ LANGUAGE plpgsql;

-- Function to get similar resolved tickets for AI suggestions
CREATE OR REPLACE FUNCTION get_similar_resolved_tickets(
    p_category VARCHAR(50),
    p_priority VARCHAR(20),
    p_search_terms TEXT,
    p_limit INTEGER DEFAULT 5
)
RETURNS TABLE (
    ticket_id UUID,
    subject VARCHAR(500),
    resolution_summary TEXT,
    resolution_time_minutes INTEGER,
    satisfaction_score INTEGER,
    similarity_score DECIMAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.id,
        t.subject,
        t.resolution_summary,
        t.resolution_time_minutes,
        t.satisfaction_score,
        -- Simple similarity score based on category and priority match
        CASE 
            WHEN t.category = p_category AND t.priority = p_priority THEN 1.0
            WHEN t.category = p_category THEN 0.8
            WHEN t.priority = p_priority THEN 0.6
            ELSE 0.4
        END as similarity_score
    FROM support_tickets t
    WHERE t.status = 'RESOLVED'
    AND t.resolution_summary IS NOT NULL
    AND t.satisfaction_score >= 4
    AND (
        p_search_terms IS NULL 
        OR t.subject ILIKE '%' || p_search_terms || '%'
        OR t.description ILIKE '%' || p_search_terms || '%'
    )
    ORDER BY similarity_score DESC, t.satisfaction_score DESC, t.resolved_at DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

-- Function to update knowledge article statistics
CREATE OR REPLACE FUNCTION update_article_stats()
RETURNS TRIGGER AS $$
BEGIN
    -- Update view count when article is accessed
    IF TG_OP = 'UPDATE' AND NEW.view_count > OLD.view_count THEN
        -- View count was incremented, no additional action needed
        RETURN NEW;
    END IF;
    
    -- Recalculate average rating based on feedback
    UPDATE kb_articles SET
        average_rating = (
            SELECT AVG(rating::DECIMAL)
            FROM article_feedback 
            WHERE article_id = NEW.id 
            AND rating IS NOT NULL
        ),
        total_ratings = (
            SELECT COUNT(*)
            FROM article_feedback 
            WHERE article_id = NEW.id 
            AND rating IS NOT NULL
        )
    WHERE id = NEW.id;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply article stats trigger
CREATE TRIGGER update_article_stats_trigger
    AFTER UPDATE ON kb_articles
    FOR EACH ROW
    EXECUTE FUNCTION update_article_stats();

-- Refresh the materialized view periodically
CREATE OR REPLACE FUNCTION refresh_ai_training_data()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW ai_training_data;
END;
$$ LANGUAGE plpgsql;

-- Additional comments for documentation
COMMENT ON MATERIALIZED VIEW ai_training_data IS 'Pre-aggregated data for AI model training and analysis';
COMMENT ON VIEW agent_performance IS 'Real-time view of agent performance metrics';
COMMENT ON VIEW kb_analytics IS 'Knowledge base article performance and analytics';
COMMENT ON FUNCTION calculate_resolution_time() IS 'Automatically calculates ticket resolution times';
COMMENT ON FUNCTION check_sla_breach() IS 'Identifies and escalates tickets that breach SLA';
COMMENT ON FUNCTION get_similar_resolved_tickets(VARCHAR, VARCHAR, TEXT, INTEGER) IS 'Finds similar resolved tickets for AI suggestions';
COMMENT ON FUNCTION update_article_stats() IS 'Updates knowledge article statistics and ratings';
COMMENT ON FUNCTION refresh_ai_training_data() IS 'Refreshes the AI training data materialized view';