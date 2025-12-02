-- Chat sessions and AI features
-- Creates tables for chatbot sessions, AI analysis, and FAQ management

-- Chat sessions for AI chatbot
CREATE TABLE chat_sessions (
    session_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(255),
    
    -- Session details
    start_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP WITH TIME ZONE,
    last_activity TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRANSFERRED', 'ENDED', 'EXPIRED')),
    
    -- Agent assignment for handoff
    agent_id VARCHAR(255),
    
    -- Session statistics
    message_count INTEGER DEFAULT 0,
    average_response_time INTEGER DEFAULT 0, -- in milliseconds
    
    -- AI metrics
    confidence_scores DECIMAL[],
    handoff_requested BOOLEAN DEFAULT FALSE,
    handoff_reason TEXT,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Chat session metadata for storing additional context
CREATE TABLE chat_session_metadata (
    session_id VARCHAR(50) NOT NULL REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    metadata_key VARCHAR(100) NOT NULL,
    metadata_value TEXT,
    
    PRIMARY KEY (session_id, metadata_key),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Chat messages (stored separately for analytics)
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(50) NOT NULL REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    
    -- Message details
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'AGENT')),
    content TEXT NOT NULL,
    
    -- Message metadata
    user_id VARCHAR(255),
    agent_id VARCHAR(255),
    
    -- AI processing results
    intent VARCHAR(50),
    confidence DECIMAL(5,4),
    entities JSONB,
    sentiment VARCHAR(20),
    sentiment_score DECIMAL(5,4),
    
    -- Response metadata
    response_time_ms INTEGER,
    model_used VARCHAR(100),
    tokens_used INTEGER,
    
    -- Timestamp
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- FAQ management
CREATE TABLE faqs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- FAQ content
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    
    -- Organization
    category VARCHAR(100),
    tags TEXT[],
    
    -- Search optimization
    keywords TEXT[],
    search_terms TEXT[], -- Common search terms that should match this FAQ
    
    -- Statistics
    view_count INTEGER DEFAULT 0,
    helpful_votes INTEGER DEFAULT 0,
    not_helpful_votes INTEGER DEFAULT 0,
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 0, -- For ordering
    
    -- Language support
    language VARCHAR(10) DEFAULT 'en',
    
    -- AI relevance score for embeddings
    relevance_score DECIMAL(5,4),
    embedding_vector VECTOR(1536), -- OpenAI embedding dimension
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);

-- AI ticket analysis for storing ML insights
CREATE TABLE ticket_ai_analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    
    -- Intent analysis
    detected_intent VARCHAR(100),
    intent_confidence DECIMAL(5,4),
    entities JSONB,
    
    -- Sentiment analysis
    sentiment VARCHAR(20),
    sentiment_score DECIMAL(5,4),
    urgency_score DECIMAL(5,4),
    
    -- Category and priority suggestions
    suggested_category VARCHAR(50),
    suggested_priority VARCHAR(20),
    suggested_tags TEXT[],
    
    -- Resolution predictions
    estimated_resolution_time INTEGER, -- in minutes
    similar_tickets UUID[],
    resolution_templates TEXT[],
    
    -- AI model information
    model_version VARCHAR(50),
    processing_timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Status
    is_current BOOLEAN DEFAULT TRUE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Agent AI suggestions
CREATE TABLE agent_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    agent_id UUID,
    
    -- Suggestion content
    suggestion_type VARCHAR(50) NOT NULL, -- 'RESPONSE', 'ACTION', 'ESCALATION', 'RESOLUTION'
    suggestion_content TEXT NOT NULL,
    template_used VARCHAR(100),
    
    -- Metadata
    confidence DECIMAL(5,4),
    based_on_tickets UUID[], -- Similar tickets used for suggestion
    
    -- Agent feedback
    was_helpful BOOLEAN,
    was_used BOOLEAN DEFAULT FALSE,
    agent_feedback TEXT,
    
    -- Timing
    suggested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE
);

-- Knowledge article feedback
CREATE TABLE article_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL REFERENCES kb_articles(id) ON DELETE CASCADE,
    
    -- User information
    user_id UUID,
    user_email VARCHAR(255),
    
    -- Feedback details
    is_helpful BOOLEAN,
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    feedback_type VARCHAR(50), -- 'ACCURACY', 'CLARITY', 'COMPLETENESS', 'OUTDATED'
    
    -- Context
    search_query TEXT, -- What they searched for to find this article
    user_agent TEXT,
    ip_address INET,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Search analytics for content gap analysis
CREATE TABLE search_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Search details
    query TEXT NOT NULL,
    normalized_query TEXT, -- Cleaned/normalized version
    
    -- Results
    results_count INTEGER DEFAULT 0,
    clicked_result_id UUID, -- If they clicked on a result
    click_position INTEGER, -- Position of clicked result
    
    -- User context
    user_id UUID,
    session_id VARCHAR(50),
    user_agent TEXT,
    
    -- Outcome
    was_helpful BOOLEAN,
    created_ticket BOOLEAN DEFAULT FALSE,
    escalated_to_agent BOOLEAN DEFAULT FALSE,
    
    -- Metadata
    language VARCHAR(10) DEFAULT 'en',
    channel VARCHAR(20) DEFAULT 'WEB',
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- AI model training data
CREATE TABLE ai_training_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Conversation context
    session_id VARCHAR(50),
    ticket_id UUID,
    
    -- Training pair
    user_input TEXT NOT NULL,
    expected_response TEXT NOT NULL,
    actual_response TEXT,
    
    -- Quality metrics
    human_rating INTEGER CHECK (human_rating >= 1 AND human_rating <= 5),
    automated_score DECIMAL(5,4),
    
    -- Labels
    intent VARCHAR(100),
    entities JSONB,
    conversation_outcome VARCHAR(50), -- 'RESOLVED', 'ESCALATED', 'ABANDONED'
    
    -- Training metadata
    is_approved_for_training BOOLEAN DEFAULT FALSE,
    approved_by UUID,
    approved_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_chat_sessions_user_id ON chat_sessions(user_id);
CREATE INDEX idx_chat_sessions_status ON chat_sessions(status);
CREATE INDEX idx_chat_sessions_last_activity ON chat_sessions(last_activity);
CREATE INDEX idx_chat_sessions_agent_id ON chat_sessions(agent_id);

CREATE INDEX idx_chat_messages_session_id ON chat_messages(session_id);
CREATE INDEX idx_chat_messages_timestamp ON chat_messages(timestamp);
CREATE INDEX idx_chat_messages_role ON chat_messages(role);
CREATE INDEX idx_chat_messages_intent ON chat_messages(intent);

CREATE INDEX idx_faqs_category ON faqs(category);
CREATE INDEX idx_faqs_is_active ON faqs(is_active);
CREATE INDEX idx_faqs_language ON faqs(language);
CREATE INDEX idx_faqs_priority ON faqs(priority);

CREATE INDEX idx_ticket_ai_analysis_ticket_id ON ticket_ai_analysis(ticket_id);
CREATE INDEX idx_ticket_ai_analysis_is_current ON ticket_ai_analysis(is_current);
CREATE INDEX idx_ticket_ai_analysis_detected_intent ON ticket_ai_analysis(detected_intent);

CREATE INDEX idx_agent_suggestions_ticket_id ON agent_suggestions(ticket_id);
CREATE INDEX idx_agent_suggestions_agent_id ON agent_suggestions(agent_id);
CREATE INDEX idx_agent_suggestions_suggestion_type ON agent_suggestions(suggestion_type);
CREATE INDEX idx_agent_suggestions_suggested_at ON agent_suggestions(suggested_at);

CREATE INDEX idx_article_feedback_article_id ON article_feedback(article_id);
CREATE INDEX idx_article_feedback_is_helpful ON article_feedback(is_helpful);
CREATE INDEX idx_article_feedback_created_at ON article_feedback(created_at);

CREATE INDEX idx_search_analytics_query ON search_analytics(normalized_query);
CREATE INDEX idx_search_analytics_created_at ON search_analytics(created_at);
CREATE INDEX idx_search_analytics_user_id ON search_analytics(user_id);

CREATE INDEX idx_ai_training_conversations_intent ON ai_training_conversations(intent);
CREATE INDEX idx_ai_training_conversations_is_approved ON ai_training_conversations(is_approved_for_training);
CREATE INDEX idx_ai_training_conversations_outcome ON ai_training_conversations(conversation_outcome);

-- Full-text search indexes for FAQs
CREATE INDEX idx_faqs_question_fulltext ON faqs USING gin(to_tsvector('english', question));
CREATE INDEX idx_faqs_answer_fulltext ON faqs USING gin(to_tsvector('english', answer));

-- Vector similarity index for FAQs (requires pgvector extension)
-- CREATE INDEX idx_faqs_embedding_vector ON faqs USING ivfflat (embedding_vector vector_cosine_ops) WITH (lists = 100);

-- Add updated_at triggers
CREATE TRIGGER update_chat_sessions_updated_at BEFORE UPDATE
    ON chat_sessions FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_faqs_updated_at BEFORE UPDATE
    ON faqs FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE chat_sessions IS 'AI chatbot conversation sessions';
COMMENT ON TABLE chat_messages IS 'Individual messages within chat sessions';
COMMENT ON TABLE faqs IS 'Frequently asked questions for AI chatbot';
COMMENT ON TABLE ticket_ai_analysis IS 'AI analysis results for support tickets';
COMMENT ON TABLE agent_suggestions IS 'AI-generated suggestions for support agents';
COMMENT ON TABLE article_feedback IS 'User feedback on knowledge base articles';
COMMENT ON TABLE search_analytics IS 'Analytics data for search queries and content gaps';
COMMENT ON TABLE ai_training_conversations IS 'Training data for AI model improvement';