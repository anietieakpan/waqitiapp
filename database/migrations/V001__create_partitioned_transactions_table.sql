-- =====================================================================
-- Database Performance Optimization: Transaction Table Partitioning
-- =====================================================================
-- This migration creates a partitioned transactions table optimized for
-- high-volume financial transaction processing at production scale.

-- Drop existing constraints that might conflict
ALTER TABLE IF EXISTS transactions DROP CONSTRAINT IF EXISTS transactions_pkey CASCADE;
ALTER TABLE IF EXISTS transactions DROP CONSTRAINT IF EXISTS idx_transaction_number CASCADE;

-- Create partitioned transactions table
CREATE TABLE IF NOT EXISTS transactions_partitioned (
    id UUID NOT NULL,
    transaction_number VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    source_account_id UUID,
    target_account_id UUID,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    fee_amount DECIMAL(19,4),
    exchange_rate DECIMAL(19,8),
    converted_amount DECIMAL(19,4),
    converted_currency VARCHAR(3),
    description TEXT NOT NULL,
    reference VARCHAR(50),
    external_reference VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    initiated_by UUID,
    approved_by UUID,
    batch_id UUID,
    priority VARCHAR(20) NOT NULL,
    transaction_date TIMESTAMP NOT NULL,
    value_date TIMESTAMP NOT NULL,
    settlement_date TIMESTAMP,
    authorized_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retry_attempts INTEGER NOT NULL DEFAULT 3,
    idempotency_key VARCHAR(255),
    metadata TEXT,
    reversal_transaction_id UUID,
    original_transaction_id UUID,
    parent_transaction_id UUID,
    reconciliation_id UUID,
    compliance_check_id UUID,
    risk_score INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Ensure transaction_date is included in primary key for partitioning
    PRIMARY KEY (id, transaction_date)
) PARTITION BY RANGE (transaction_date);

-- Create monthly partitions for the last 12 months and next 6 months
DO $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
    current_month DATE;
BEGIN
    -- Start from 12 months ago
    current_month := DATE_TRUNC('month', CURRENT_DATE - INTERVAL '12 months');
    
    -- Create partitions for 18 months (12 past + 6 future)
    FOR i IN 0..17 LOOP
        start_date := current_month + (i || ' months')::INTERVAL;
        end_date := start_date + INTERVAL '1 month';
        partition_name := 'transactions_y' || TO_CHAR(start_date, 'YYYY') || 'm' || TO_CHAR(start_date, 'MM');
        
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I PARTITION OF transactions_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        
        -- Create optimized indexes for each partition
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (source_account_id, transaction_date)', 
                      partition_name || '_source_account_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (target_account_id, transaction_date)', 
                      partition_name || '_target_account_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (status, transaction_date)', 
                      partition_name || '_status_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (transaction_type, transaction_date)', 
                      partition_name || '_type_idx', partition_name);
        EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I (transaction_number)', 
                      partition_name || '_number_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (external_reference) WHERE external_reference IS NOT NULL', 
                      partition_name || '_ext_ref_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (batch_id) WHERE batch_id IS NOT NULL', 
                      partition_name || '_batch_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (idempotency_key) WHERE idempotency_key IS NOT NULL', 
                      partition_name || '_idempotency_idx', partition_name);
    END LOOP;
END $$;

-- Create global indexes on the main table
CREATE UNIQUE INDEX IF NOT EXISTS transactions_partitioned_number_global_idx 
    ON transactions_partitioned (transaction_number);
CREATE INDEX IF NOT EXISTS transactions_partitioned_date_idx 
    ON transactions_partitioned (transaction_date);
CREATE INDEX IF NOT EXISTS transactions_partitioned_amount_idx 
    ON transactions_partitioned (amount) WHERE amount > 10000;
CREATE INDEX IF NOT EXISTS transactions_partitioned_compliance_idx 
    ON transactions_partitioned (compliance_check_id) WHERE compliance_check_id IS NOT NULL;

-- Create function to automatically create future partitions
CREATE OR REPLACE FUNCTION create_transaction_partition_if_not_exists(partition_date DATE)
RETURNS VOID AS $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
BEGIN
    start_date := DATE_TRUNC('month', partition_date);
    end_date := start_date + INTERVAL '1 month';
    partition_name := 'transactions_y' || TO_CHAR(start_date, 'YYYY') || 'm' || TO_CHAR(start_date, 'MM');
    
    -- Check if partition exists
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c 
        JOIN pg_namespace n ON n.oid = c.relnamespace 
        WHERE c.relname = partition_name AND n.nspname = 'public'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I PARTITION OF transactions_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        
        -- Create optimized indexes for the new partition
        EXECUTE format('CREATE INDEX %I ON %I (source_account_id, transaction_date)', 
                      partition_name || '_source_account_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (target_account_id, transaction_date)', 
                      partition_name || '_target_account_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (status, transaction_date)', 
                      partition_name || '_status_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (transaction_type, transaction_date)', 
                      partition_name || '_type_idx', partition_name);
        EXECUTE format('CREATE UNIQUE INDEX %I ON %I (transaction_number)', 
                      partition_name || '_number_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (external_reference) WHERE external_reference IS NOT NULL', 
                      partition_name || '_ext_ref_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (batch_id) WHERE batch_id IS NOT NULL', 
                      partition_name || '_batch_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (idempotency_key) WHERE idempotency_key IS NOT NULL', 
                      partition_name || '_idempotency_idx', partition_name);
                      
        RAISE NOTICE 'Created partition % for date range % to %', partition_name, start_date, end_date;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create trigger function to automatically create partitions
CREATE OR REPLACE FUNCTION transaction_partition_trigger()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM create_transaction_partition_if_not_exists(NEW.transaction_date::DATE);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on the partitioned table
DROP TRIGGER IF EXISTS transaction_auto_partition_trigger ON transactions_partitioned;
CREATE TRIGGER transaction_auto_partition_trigger
    BEFORE INSERT ON transactions_partitioned
    FOR EACH ROW EXECUTE FUNCTION transaction_partition_trigger();

-- Data migration from existing transactions table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'transactions' AND table_schema = 'public') THEN
        -- Migrate data in batches to avoid locking
        INSERT INTO transactions_partitioned 
        SELECT * FROM transactions 
        ON CONFLICT (transaction_number) DO NOTHING;
        
        -- Rename old table for backup
        ALTER TABLE transactions RENAME TO transactions_backup_pre_partition;
        
        RAISE NOTICE 'Migrated data from transactions to transactions_partitioned';
    END IF;
END $$;

-- Create a view to maintain backward compatibility
CREATE OR REPLACE VIEW transactions AS 
SELECT * FROM transactions_partitioned;

-- Create function for partition maintenance (dropping old partitions)
CREATE OR REPLACE FUNCTION drop_old_transaction_partitions(months_to_keep INTEGER DEFAULT 24)
RETURNS VOID AS $$
DECLARE
    partition_record RECORD;
    cutoff_date DATE;
BEGIN
    cutoff_date := DATE_TRUNC('month', CURRENT_DATE - (months_to_keep || ' months')::INTERVAL);
    
    FOR partition_record IN
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'transactions_y%m%' 
        AND schemaname = 'public'
    LOOP
        -- Extract date from partition name to check if it's old enough
        DECLARE
            year_part INTEGER;
            month_part INTEGER;
            partition_date DATE;
        BEGIN
            year_part := SUBSTRING(partition_record.tablename FROM 'transactions_y(\d{4})m')::INTEGER;
            month_part := SUBSTRING(partition_record.tablename FROM 'transactions_y\d{4}m(\d{2})')::INTEGER;
            partition_date := MAKE_DATE(year_part, month_part, 1);
            
            IF partition_date < cutoff_date THEN
                EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', partition_record.tablename);
                RAISE NOTICE 'Dropped old partition: %', partition_record.tablename;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Could not process partition: %', partition_record.tablename;
        END;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Grant appropriate permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON transactions_partitioned TO waqiti_user;
GRANT SELECT ON transactions TO waqiti_user;
GRANT EXECUTE ON FUNCTION create_transaction_partition_if_not_exists(DATE) TO waqiti_user;
GRANT EXECUTE ON FUNCTION drop_old_transaction_partitions(INTEGER) TO waqiti_user;

-- Create comments for documentation
COMMENT ON TABLE transactions_partitioned IS 'Main transactions table partitioned by transaction_date for optimal performance at scale';
COMMENT ON FUNCTION create_transaction_partition_if_not_exists(DATE) IS 'Automatically creates transaction partitions for the specified date if they do not exist';
COMMENT ON FUNCTION drop_old_transaction_partitions(INTEGER) IS 'Drops transaction partitions older than specified months (default 24 months)';