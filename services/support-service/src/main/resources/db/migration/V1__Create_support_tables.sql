-- Support Service Database Schema
-- Creates tables for customer support, tickets, and knowledge base

-- Support tickets
CREATE TABLE support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number VARCHAR(20) UNIQUE NOT NULL,
    
    -- Customer information
    user_id UUID,
    customer_email VARCHAR(255),
    customer_name VARCHAR(255),
    customer_phone VARCHAR(20),
    
    -- Ticket details
    subject VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    subcategory VARCHAR(50),
    
    -- Priority and status
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT', 'CRITICAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'WAITING_INTERNAL', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    
    -- Assignment
    assigned_to UUID, -- Support agent ID
    assigned_team VARCHAR(50),
    escalated_to UUID, -- Manager ID
    escalation_reason TEXT,
    
    -- SLA tracking
    sla_tier VARCHAR(20) DEFAULT 'STANDARD',
    first_response_due_at TIMESTAMP WITH TIME ZONE,
    resolution_due_at TIMESTAMP WITH TIME ZONE,
    first_response_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    
    -- Resolution details
    resolution_summary TEXT,
    resolution_category VARCHAR(50),
    customer_satisfaction_rating INTEGER CHECK (customer_satisfaction_rating >= 1 AND customer_satisfaction_rating <= 5),
    customer_feedback TEXT,
    
    -- Metadata
    channel VARCHAR(20) DEFAULT 'EMAIL' CHECK (channel IN ('EMAIL', 'CHAT', 'PHONE', 'WEB', 'MOBILE', 'SOCIAL')),
    language VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(50) DEFAULT 'UTC',
    
    -- Tags and references
    tags TEXT[], -- Array of tags
    related_transaction_id UUID,
    related_account_id UUID,
    external_ticket_id VARCHAR(100),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID
);

-- Ticket messages/responses
CREATE TABLE ticket_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    
    -- Message details
    message_type VARCHAR(20) NOT NULL CHECK (message_type IN ('CUSTOMER', 'AGENT', 'SYSTEM', 'INTERNAL')),
    sender_id UUID,
    sender_name VARCHAR(255),
    sender_email VARCHAR(255),
    
    -- Content
    subject VARCHAR(500),
    content TEXT NOT NULL,
    content_type VARCHAR(20) DEFAULT 'TEXT' CHECK (content_type IN ('TEXT', 'HTML', 'MARKDOWN')),
    
    -- Visibility
    is_internal BOOLEAN DEFAULT FALSE,
    is_public BOOLEAN DEFAULT TRUE,
    
    -- Status
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    read_by UUID,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Ticket attachments
CREATE TABLE ticket_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID REFERENCES support_tickets(id) ON DELETE CASCADE,
    message_id UUID REFERENCES ticket_messages(id) ON DELETE CASCADE,
    
    -- File details
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    
    -- Storage
    storage_path VARCHAR(500) NOT NULL,
    storage_bucket VARCHAR(100),
    
    -- Security
    is_public BOOLEAN DEFAULT FALSE,
    requires_authentication BOOLEAN DEFAULT TRUE,
    virus_scan_status VARCHAR(20) DEFAULT 'PENDING' CHECK (virus_scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'ERROR')),
    virus_scan_result TEXT,
    
    -- Audit
    uploaded_by UUID,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    CHECK ((ticket_id IS NOT NULL AND message_id IS NULL) OR (ticket_id IS NULL AND message_id IS NOT NULL))
);

-- Knowledge base articles
CREATE TABLE kb_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Article content
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(200) UNIQUE NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    
    -- Organization
    category_id UUID REFERENCES kb_categories(id),
    tags TEXT[],
    
    -- Status and visibility
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'INTERNAL', 'CUSTOMER_ONLY')),
    
    -- SEO and metadata
    meta_title VARCHAR(200),
    meta_description VARCHAR(500),
    keywords TEXT[],
    
    -- Statistics
    view_count INTEGER DEFAULT 0,
    helpful_votes INTEGER DEFAULT 0,
    not_helpful_votes INTEGER DEFAULT 0,
    
    -- Scheduling
    published_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_by UUID
);

-- Knowledge base categories
CREATE TABLE kb_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(200) UNIQUE NOT NULL,
    description TEXT,
    
    -- Hierarchy
    parent_id UUID REFERENCES kb_categories(id),
    sort_order INTEGER DEFAULT 0,
    
    -- Display
    icon VARCHAR(100),
    color VARCHAR(7), -- Hex color code
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Support agents
CREATE TABLE support_agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    
    -- Agent details
    employee_id VARCHAR(50) UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    
    -- Capabilities
    departments TEXT[] NOT NULL,
    languages TEXT[] NOT NULL,
    skills TEXT[],
    
    -- Availability
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' CHECK (status IN ('ONLINE', 'BUSY', 'AWAY', 'OFFLINE')),
    max_concurrent_tickets INTEGER DEFAULT 10,
    current_ticket_count INTEGER DEFAULT 0,
    
    -- Performance metrics
    average_rating DECIMAL(3,2) DEFAULT 0.00,
    total_tickets_handled INTEGER DEFAULT 0,
    tickets_resolved_this_month INTEGER DEFAULT 0,
    
    -- Settings
    auto_assign_enabled BOOLEAN DEFAULT TRUE,
    email_notifications_enabled BOOLEAN DEFAULT TRUE,
    
    -- Audit
    hired_at DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);

-- Ticket escalation rules
CREATE TABLE escalation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- Conditions
    categories TEXT[],
    priorities TEXT[],
    sla_tiers TEXT[],
    
    -- Timing
    escalate_after_hours INTEGER NOT NULL,
    escalate_on_weekends BOOLEAN DEFAULT TRUE,
    escalate_on_holidays BOOLEAN DEFAULT FALSE,
    
    -- Action
    escalate_to_user_id UUID,
    escalate_to_team VARCHAR(50),
    increase_priority BOOLEAN DEFAULT FALSE,
    send_notification BOOLEAN DEFAULT TRUE,
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- SLA configurations
CREATE TABLE sla_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    
    -- Conditions
    category VARCHAR(50),
    priority VARCHAR(20),
    customer_tier VARCHAR(20),
    
    -- Response times (in minutes)
    first_response_time INTEGER NOT NULL,
    resolution_time INTEGER NOT NULL,
    
    -- Business hours
    business_hours_only BOOLEAN DEFAULT TRUE,
    timezone VARCHAR(50) DEFAULT 'UTC',
    
    -- Status
    is_active BOOLEAN DEFAULT TRUE,
    is_default BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_support_tickets_user_id ON support_tickets(user_id);
CREATE INDEX idx_support_tickets_status ON support_tickets(status);
CREATE INDEX idx_support_tickets_priority ON support_tickets(priority);
CREATE INDEX idx_support_tickets_assigned_to ON support_tickets(assigned_to);
CREATE INDEX idx_support_tickets_created_at ON support_tickets(created_at);
CREATE INDEX idx_support_tickets_category ON support_tickets(category);
CREATE INDEX idx_support_tickets_ticket_number ON support_tickets(ticket_number);

CREATE INDEX idx_ticket_messages_ticket_id ON ticket_messages(ticket_id);
CREATE INDEX idx_ticket_messages_created_at ON ticket_messages(created_at);
CREATE INDEX idx_ticket_messages_message_type ON ticket_messages(message_type);

CREATE INDEX idx_ticket_attachments_ticket_id ON ticket_attachments(ticket_id);
CREATE INDEX idx_ticket_attachments_message_id ON ticket_attachments(message_id);

CREATE INDEX idx_kb_articles_status ON kb_articles(status);
CREATE INDEX idx_kb_articles_category_id ON kb_articles(category_id);
CREATE INDEX idx_kb_articles_slug ON kb_articles(slug);

CREATE INDEX idx_kb_categories_parent_id ON kb_categories(parent_id);
CREATE INDEX idx_kb_categories_slug ON kb_categories(slug);

CREATE INDEX idx_support_agents_user_id ON support_agents(user_id);
CREATE INDEX idx_support_agents_status ON support_agents(status);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_support_tickets_updated_at BEFORE UPDATE
    ON support_tickets FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_ticket_messages_updated_at BEFORE UPDATE
    ON ticket_messages FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_kb_articles_updated_at BEFORE UPDATE
    ON kb_articles FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_kb_categories_updated_at BEFORE UPDATE
    ON kb_categories FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_support_agents_updated_at BEFORE UPDATE
    ON support_agents FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE support_tickets IS 'Customer support tickets and issues';
COMMENT ON TABLE ticket_messages IS 'Messages and responses within support tickets';
COMMENT ON TABLE ticket_attachments IS 'File attachments for support tickets';
COMMENT ON TABLE kb_articles IS 'Knowledge base articles for self-service support';
COMMENT ON TABLE kb_categories IS 'Categories for organizing knowledge base articles';
COMMENT ON TABLE support_agents IS 'Support team members and their capabilities';
COMMENT ON TABLE escalation_rules IS 'Rules for automatic ticket escalation';
COMMENT ON TABLE sla_configurations IS 'Service level agreement configurations';