-- Database Migration Template for Waqiti Services
-- Copy this template for new migrations and follow the standardized patterns

-- Migration: V{version}__{description}.sql
-- Example: V001__Create_user_tables.sql

-- =====================================================================
-- STANDARDIZED PATTERNS FOR WAQITI DATABASE MIGRATIONS
-- =====================================================================

-- 1. TIMESTAMP COLUMNS - Always use TIMESTAMP WITH TIME ZONE
-- 2. ID COLUMNS - Always use UUID with gen_random_uuid() default
-- 3. AUDIT COLUMNS - Include created_at, updated_at, created_by, updated_by
-- 4. VERSION COLUMNS - Include version for optimistic locking
-- 5. INDEXES - Create appropriate indexes for query performance
-- 6. CONSTRAINTS - Add business rule constraints

-- =====================================================================
-- TABLE CREATION TEMPLATE
-- =====================================================================

-- Example table following Waqiti standards
CREATE TABLE IF NOT EXISTS example_entities (
    -- Primary Key - Always UUID
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Business Fields
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    
    -- JSON/JSONB fields for flexible data (when appropriate)
    metadata JSONB,
    
    -- Decimal fields for financial data (always DECIMAL(19,4))
    amount DECIMAL(19,4),
    
    -- Audit Fields (Required for all tables)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    -- Optimistic Locking (Required for frequently updated tables)
    version BIGINT NOT NULL DEFAULT 0
);

-- =====================================================================
-- CONSTRAINTS TEMPLATE
-- =====================================================================

-- Check constraints for business rules
ALTER TABLE example_entities 
ADD CONSTRAINT check_status 
CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED'));

-- Amount constraints for financial fields
ALTER TABLE example_entities 
ADD CONSTRAINT check_amount_positive 
CHECK (amount IS NULL OR amount >= 0);

-- =====================================================================
-- INDEXES TEMPLATE
-- =====================================================================

-- Standard indexes
CREATE INDEX IF NOT EXISTS idx_example_entities_status ON example_entities(status);
CREATE INDEX IF NOT EXISTS idx_example_entities_created_at ON example_entities(created_at);
CREATE INDEX IF NOT EXISTS idx_example_entities_updated_at ON example_entities(updated_at);

-- Business-specific indexes
CREATE INDEX IF NOT EXISTS idx_example_entities_name ON example_entities(name);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_example_entities_status_created_at 
ON example_entities(status, created_at);

-- JSONB indexes (when using JSONB fields)
CREATE INDEX IF NOT EXISTS idx_example_entities_metadata_gin 
ON example_entities USING gin(metadata);

-- =====================================================================
-- TRIGGERS TEMPLATE (for updated_at automation)
-- =====================================================================

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to automatically update updated_at
CREATE TRIGGER update_example_entities_updated_at 
    BEFORE UPDATE ON example_entities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================================
-- FOREIGN KEY CONSTRAINTS (when referencing other service tables)
-- =====================================================================

-- Example foreign key (only within same service)
-- ALTER TABLE example_entities 
-- ADD CONSTRAINT fk_example_entities_parent_id 
-- FOREIGN KEY (parent_id) REFERENCES parent_entities(id);

-- NOTE: DO NOT create foreign keys across services/databases
-- Use application-level consistency instead

-- =====================================================================
-- COMMENTS FOR DOCUMENTATION
-- =====================================================================

COMMENT ON TABLE example_entities IS 'Example entities for demonstration purposes';
COMMENT ON COLUMN example_entities.id IS 'Unique identifier for the entity';
COMMENT ON COLUMN example_entities.status IS 'Current status of the entity';
COMMENT ON COLUMN example_entities.amount IS 'Financial amount with 4 decimal precision';
COMMENT ON COLUMN example_entities.metadata IS 'Additional metadata in JSON format';
COMMENT ON COLUMN example_entities.version IS 'Version for optimistic locking';

-- =====================================================================
-- ROLLBACK TEMPLATE (create corresponding rollback file)
-- =====================================================================

-- Create a corresponding rollback file: V{version}__{description}__rollback.sql
-- Example content for rollback:
-- DROP TRIGGER IF EXISTS update_example_entities_updated_at ON example_entities;
-- DROP INDEX IF EXISTS idx_example_entities_status;
-- DROP TABLE IF EXISTS example_entities CASCADE;