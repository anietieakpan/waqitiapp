-- Add version and audit fields to transactions table for optimistic locking and compliance
-- Critical for preventing concurrent update conflicts in financial transactions

-- Add version column for optimistic locking
ALTER TABLE transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit fields for compliance tracking
ALTER TABLE transactions ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE transactions ADD COLUMN modified_by VARCHAR(255);

-- Create index on version column for performance
CREATE INDEX idx_transactions_version ON transactions(version);

-- Create index on audit fields for compliance queries
CREATE INDEX idx_transactions_created_by ON transactions(created_by);
CREATE INDEX idx_transactions_modified_by ON transactions(modified_by);

-- Add similar fields to other critical financial entities
-- Accounts table
ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE accounts ADD COLUMN modified_by VARCHAR(255);

-- Wallets table (if exists in this service)
ALTER TABLE wallets ADD COLUMN version BIGINT NOT NULL DEFAULT 0 WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wallets');
ALTER TABLE wallets ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM' WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wallets');
ALTER TABLE wallets ADD COLUMN modified_by VARCHAR(255) WHERE EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wallets');

-- Add comment for documentation
COMMENT ON COLUMN transactions.version IS 'Version field for optimistic locking to prevent concurrent update conflicts';
COMMENT ON COLUMN transactions.created_by IS 'User who created the transaction for audit trail';
COMMENT ON COLUMN transactions.modified_by IS 'User who last modified the transaction for audit trail';

-- Update existing records to have proper audit information
UPDATE transactions SET created_by = 'SYSTEM' WHERE created_by IS NULL;
UPDATE transactions SET version = 0 WHERE version IS NULL;