-- Add audit and version fields to savings service tables
-- Critical for financial compliance and data integrity

-- ===================================
-- SAVINGS_ACCOUNTS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE savings_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE savings_accounts ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE savings_accounts ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE savings_accounts ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE savings_accounts ADD COLUMN modified_by VARCHAR(255);

-- Create indexes for performance and auditing
CREATE INDEX idx_savings_accounts_version ON savings_accounts(version);
CREATE INDEX idx_savings_accounts_created_at ON savings_accounts(created_at);
CREATE INDEX idx_savings_accounts_updated_at ON savings_accounts(updated_at);
CREATE INDEX idx_savings_accounts_created_by ON savings_accounts(created_by);

-- ===================================
-- SAVINGS_GOALS TABLE UPDATES  
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE savings_goals ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE savings_goals ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE savings_goals ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE savings_goals ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE savings_goals ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_savings_goals_version ON savings_goals(version);
CREATE INDEX idx_savings_goals_created_at ON savings_goals(created_at);

-- ===================================
-- SAVINGS_CONTRIBUTIONS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE savings_contributions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE savings_contributions ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE savings_contributions ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE savings_contributions ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE savings_contributions ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_savings_contributions_version ON savings_contributions(version);
CREATE INDEX idx_savings_contributions_created_at ON savings_contributions(created_at);

-- ===================================
-- AUTO_SAVE_RULES TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE auto_save_rules ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields  
ALTER TABLE auto_save_rules ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE auto_save_rules ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE auto_save_rules ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE auto_save_rules ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_auto_save_rules_version ON auto_save_rules(version);
CREATE INDEX idx_auto_save_rules_created_at ON auto_save_rules(created_at);

-- ===================================
-- MILESTONES TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking  
ALTER TABLE milestones ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE milestones ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE milestones ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE milestones ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE milestones ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_milestones_version ON milestones(version);
CREATE INDEX idx_milestones_created_at ON milestones(created_at);

-- ===================================
-- ADD TABLE COMMENTS FOR DOCUMENTATION
-- ===================================

COMMENT ON COLUMN savings_accounts.version IS 'Version field for optimistic locking to prevent concurrent update conflicts';
COMMENT ON COLUMN savings_accounts.created_at IS 'Timestamp when the savings account was created';
COMMENT ON COLUMN savings_accounts.updated_at IS 'Timestamp when the savings account was last modified';
COMMENT ON COLUMN savings_accounts.created_by IS 'User who created the savings account';
COMMENT ON COLUMN savings_accounts.modified_by IS 'User who last modified the savings account';

-- ===================================
-- UPDATE EXISTING RECORDS
-- ===================================

-- Set initial values for existing records
UPDATE savings_accounts SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE savings_goals SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE savings_contributions SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE auto_save_rules SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE milestones SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;

-- ===================================
-- CREATE TRIGGERS FOR UPDATED_AT FIELDS
-- ===================================

-- Trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to all tables
CREATE TRIGGER update_savings_accounts_updated_at BEFORE UPDATE ON savings_accounts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_savings_goals_updated_at BEFORE UPDATE ON savings_goals 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_savings_contributions_updated_at BEFORE UPDATE ON savings_contributions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auto_save_rules_updated_at BEFORE UPDATE ON auto_save_rules 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_milestones_updated_at BEFORE UPDATE ON milestones 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();