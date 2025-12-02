--
-- Migration V5: Create goal_templates table
-- Author: Waqiti Development Team
-- Date: 2025-11-19
-- Purpose: Pre-defined goal templates for quick goal creation
--

-- Create goal_templates table
CREATE TABLE IF NOT EXISTS goal_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    suggested_target_amount DECIMAL(19,4),
    suggested_duration_months INTEGER,
    icon VARCHAR(50),
    color VARCHAR(7),
    image_url VARCHAR(500),
    tags TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    popularity INTEGER NOT NULL DEFAULT 0,
    usage_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_goal_templates_popularity CHECK (popularity >= 0 AND popularity <= 100),
    CONSTRAINT chk_goal_templates_usage_count CHECK (usage_count >= 0)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_goal_templates_category ON goal_templates(category);
CREATE INDEX IF NOT EXISTS idx_goal_templates_popularity ON goal_templates(popularity DESC);
CREATE INDEX IF NOT EXISTS idx_goal_templates_active ON goal_templates(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_goal_templates_name_lower ON goal_templates(LOWER(name));

-- Create full-text search index for templates
CREATE INDEX IF NOT EXISTS idx_goal_templates_search ON goal_templates USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '') || ' ' || COALESCE(tags, '')));

-- Add comments
COMMENT ON TABLE goal_templates IS 'Pre-defined savings goal templates for users to choose from';
COMMENT ON COLUMN goal_templates.popularity IS 'Popularity score 0-100 for recommendations';
COMMENT ON COLUMN goal_templates.usage_count IS 'Number of times this template has been used';

-- Trigger to update updated_at
CREATE TRIGGER update_goal_templates_updated_at
    BEFORE UPDATE ON goal_templates
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Insert default templates
INSERT INTO goal_templates (name, description, category, suggested_target_amount, suggested_duration_months, icon, color, popularity, tags) VALUES
('Emergency Fund', 'Build a safety net for unexpected expenses', 'EMERGENCY_FUND', 5000.00, 12, 'shield', '#FF5733', 95, 'emergency,safety,essential'),
('Dream Vacation', 'Save for your dream vacation destination', 'VACATION', 3000.00, 18, 'plane', '#3498DB', 90, 'travel,vacation,holiday'),
('New Car', 'Save for a down payment on a new vehicle', 'CAR', 10000.00, 24, 'car', '#2ECC71', 85, 'car,vehicle,transportation'),
('Home Down Payment', 'Build savings for your first home', 'HOME', 50000.00, 60, 'home', '#E74C3C', 88, 'home,house,real-estate'),
('Wedding', 'Save for your special day', 'WEDDING', 15000.00, 24, 'ring', '#F39C12', 80, 'wedding,marriage,celebration'),
('Education Fund', 'Save for education or training', 'EDUCATION', 20000.00, 48, 'graduation-cap', '#9B59B6', 82, 'education,college,learning'),
('Retirement Savings', 'Build your retirement nest egg', 'RETIREMENT', 100000.00, 120, 'piggy-bank', '#1ABC9C', 75, 'retirement,pension,future'),
('New Gadget', 'Save for the latest technology', 'GADGET', 1500.00, 6, 'laptop', '#34495E', 70, 'gadget,technology,electronics'),
('Medical Expenses', 'Save for healthcare needs', 'HEALTH', 5000.00, 12, 'heart', '#E67E22', 78, 'health,medical,healthcare'),
('Start a Business', 'Save to launch your business', 'BUSINESS', 25000.00, 36, 'briefcase', '#16A085', 65, 'business,startup,entrepreneurship');

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE goal_templates TO waqiti_savings;
