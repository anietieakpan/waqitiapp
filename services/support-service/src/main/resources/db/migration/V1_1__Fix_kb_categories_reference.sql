-- Fix the kb_categories table creation order issue
-- This migration creates kb_categories first, then adds the foreign key to kb_articles

-- Drop the existing kb_articles table to recreate with proper foreign key
ALTER TABLE kb_articles DROP CONSTRAINT IF EXISTS kb_articles_category_id_fkey;

-- Ensure kb_categories exists with correct structure
-- (This is safe as it's a CREATE TABLE IF NOT EXISTS equivalent)
CREATE TABLE IF NOT EXISTS kb_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

-- Now add the foreign key constraint to kb_articles if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'kb_articles_category_id_fkey'
    ) THEN
        ALTER TABLE kb_articles ADD CONSTRAINT kb_articles_category_id_fkey 
        FOREIGN KEY (category_id) REFERENCES kb_categories(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Add missing columns to kb_articles for AI features
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS embedding_vector VECTOR(1536);
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS language VARCHAR(10) DEFAULT 'en';
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS is_published BOOLEAN DEFAULT FALSE;
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS is_featured BOOLEAN DEFAULT FALSE;
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS difficulty_level VARCHAR(20) DEFAULT 'BEGINNER';
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS estimated_read_time INTEGER; -- in minutes
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS external_url VARCHAR(500);
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS video_url VARCHAR(500);

-- Update the status constraint to include the actual status values used
ALTER TABLE kb_articles DROP CONSTRAINT IF EXISTS kb_articles_status_check;
ALTER TABLE kb_articles ADD CONSTRAINT kb_articles_status_check 
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'REVIEW'));

-- Add helpful/not helpful tracking columns that were missing
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS average_rating DECIMAL(3,2);
ALTER TABLE kb_articles ADD COLUMN IF NOT EXISTS total_ratings INTEGER DEFAULT 0;

-- Create indexes that might have been missing
CREATE INDEX IF NOT EXISTS idx_kb_articles_language ON kb_articles(language);
CREATE INDEX IF NOT EXISTS idx_kb_articles_is_published ON kb_articles(is_published);
CREATE INDEX IF NOT EXISTS idx_kb_articles_is_featured ON kb_articles(is_featured);
CREATE INDEX IF NOT EXISTS idx_kb_articles_difficulty_level ON kb_articles(difficulty_level);

-- Full-text search index for kb_articles
CREATE INDEX IF NOT EXISTS idx_kb_articles_title_fulltext ON kb_articles USING gin(to_tsvector('english', title));
CREATE INDEX IF NOT EXISTS idx_kb_articles_content_fulltext ON kb_articles USING gin(to_tsvector('english', content));

-- Comments
COMMENT ON COLUMN kb_articles.embedding_vector IS 'Vector embedding for semantic search';
COMMENT ON COLUMN kb_articles.language IS 'Primary language of the article';
COMMENT ON COLUMN kb_articles.is_published IS 'Whether the article is published and visible';
COMMENT ON COLUMN kb_articles.is_featured IS 'Whether the article should be featured prominently';
COMMENT ON COLUMN kb_articles.difficulty_level IS 'Difficulty level: BEGINNER, INTERMEDIATE, ADVANCED';
COMMENT ON COLUMN kb_articles.estimated_read_time IS 'Estimated reading time in minutes';

-- Insert some default categories if they don't exist
INSERT INTO kb_categories (name, slug, description, sort_order, is_active) 
VALUES 
    ('Getting Started', 'getting-started', 'Basic information for new users', 1, true),
    ('Payments', 'payments', 'Payment and transaction related topics', 2, true),
    ('Security', 'security', 'Account security and privacy topics', 3, true),
    ('Technical Issues', 'technical-issues', 'App and technical problem resolution', 4, true),
    ('Account Management', 'account-management', 'Account settings and profile management', 5, true)
ON CONFLICT (slug) DO NOTHING;