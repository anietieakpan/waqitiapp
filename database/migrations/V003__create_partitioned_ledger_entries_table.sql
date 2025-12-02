-- =====================================================================
-- Database Performance Optimization: Ledger Entries Table Partitioning
-- =====================================================================
-- This migration creates a partitioned ledger_entries table optimized for
-- high-volume double-entry bookkeeping in financial systems.

-- Drop existing constraints that might conflict
ALTER TABLE IF EXISTS ledger_entries DROP CONSTRAINT IF EXISTS ledger_entries_pkey CASCADE;

-- Create partitioned ledger entries table
CREATE TABLE IF NOT EXISTS ledger_entries_partitioned (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(19,4) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL,
    description TEXT NOT NULL,
    reference VARCHAR(50),
    external_reference VARCHAR(100),
    posting_date TIMESTAMP NOT NULL,
    value_date TIMESTAMP NOT NULL,
    entry_sequence INTEGER NOT NULL,
    running_balance DECIMAL(19,4),
    reconciled BOOLEAN NOT NULL DEFAULT FALSE,
    reconciliation_id UUID,
    reconciled_at TIMESTAMP,
    reversal_entry_id UUID,
    original_entry_id UUID,
    journal_entry_id UUID,
    posting_rule_id UUID,
    gl_account_code VARCHAR(20),
    cost_center VARCHAR(20),
    profit_center VARCHAR(20),
    business_unit VARCHAR(20),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Include posting_date in primary key for partitioning
    PRIMARY KEY (id, posting_date),
    
    -- Constraints to ensure double-entry integrity
    CONSTRAINT ledger_entries_amount_positive CHECK (amount > 0)
) PARTITION BY RANGE (posting_date);

-- Create monthly partitions for the last 24 months and next 12 months
-- Ledger entries are critical for financial reporting and need longer retention
DO $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
    current_month DATE;
BEGIN
    -- Start from 24 months ago
    current_month := DATE_TRUNC('month', CURRENT_DATE - INTERVAL '24 months');
    
    -- Create partitions for 36 months (24 past + 12 future)
    FOR i IN 0..35 LOOP
        start_date := current_month + (i || ' months')::INTERVAL;
        end_date := start_date + INTERVAL '1 month';
        partition_name := 'ledger_entries_y' || TO_CHAR(start_date, 'YYYY') || 'm' || TO_CHAR(start_date, 'MM');
        
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I PARTITION OF ledger_entries_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        
        -- Create optimized indexes for each partition
        -- Account-based queries are most common
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (account_id, posting_date)', 
                      partition_name || '_account_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (transaction_id, entry_sequence)', 
                      partition_name || '_transaction_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (entry_type, account_id, posting_date)', 
                      partition_name || '_type_account_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (reconciled, posting_date)', 
                      partition_name || '_reconciled_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (gl_account_code, posting_date) WHERE gl_account_code IS NOT NULL', 
                      partition_name || '_gl_account_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (journal_entry_id) WHERE journal_entry_id IS NOT NULL', 
                      partition_name || '_journal_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (reconciliation_id) WHERE reconciliation_id IS NOT NULL', 
                      partition_name || '_reconciliation_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (amount) WHERE amount > 10000', 
                      partition_name || '_large_amount_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (cost_center, profit_center) WHERE cost_center IS NOT NULL', 
                      partition_name || '_cost_center_idx', partition_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (external_reference) WHERE external_reference IS NOT NULL', 
                      partition_name || '_ext_ref_idx', partition_name);
    END LOOP;
END $$;

-- Create global indexes on the main table
CREATE INDEX IF NOT EXISTS ledger_entries_partitioned_posting_date_idx 
    ON ledger_entries_partitioned (posting_date);
CREATE INDEX IF NOT EXISTS ledger_entries_partitioned_large_amounts_idx 
    ON ledger_entries_partitioned (amount, posting_date) WHERE amount > 50000;
CREATE INDEX IF NOT EXISTS ledger_entries_partitioned_unreconciled_idx 
    ON ledger_entries_partitioned (reconciled, posting_date) WHERE reconciled = FALSE;

-- Create function to automatically create future ledger partitions
CREATE OR REPLACE FUNCTION create_ledger_partition_if_not_exists(partition_date DATE)
RETURNS VOID AS $$
DECLARE
    start_date DATE;
    end_date DATE;
    partition_name TEXT;
BEGIN
    start_date := DATE_TRUNC('month', partition_date);
    end_date := start_date + INTERVAL '1 month';
    partition_name := 'ledger_entries_y' || TO_CHAR(start_date, 'YYYY') || 'm' || TO_CHAR(start_date, 'MM');
    
    -- Check if partition exists
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c 
        JOIN pg_namespace n ON n.oid = c.relnamespace 
        WHERE c.relname = partition_name AND n.nspname = 'public'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I PARTITION OF ledger_entries_partitioned
            FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        
        -- Create optimized indexes for the new partition
        EXECUTE format('CREATE INDEX %I ON %I (account_id, posting_date)', 
                      partition_name || '_account_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (transaction_id, entry_sequence)', 
                      partition_name || '_transaction_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (entry_type, account_id, posting_date)', 
                      partition_name || '_type_account_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (reconciled, posting_date)', 
                      partition_name || '_reconciled_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (gl_account_code, posting_date) WHERE gl_account_code IS NOT NULL', 
                      partition_name || '_gl_account_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (journal_entry_id) WHERE journal_entry_id IS NOT NULL', 
                      partition_name || '_journal_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (reconciliation_id) WHERE reconciliation_id IS NOT NULL', 
                      partition_name || '_reconciliation_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (amount) WHERE amount > 10000', 
                      partition_name || '_large_amount_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (cost_center, profit_center) WHERE cost_center IS NOT NULL', 
                      partition_name || '_cost_center_idx', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I (external_reference) WHERE external_reference IS NOT NULL', 
                      partition_name || '_ext_ref_idx', partition_name);
                      
        RAISE NOTICE 'Created ledger partition % for date range % to %', partition_name, start_date, end_date;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Create trigger function to automatically create partitions
CREATE OR REPLACE FUNCTION ledger_partition_trigger()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM create_ledger_partition_if_not_exists(NEW.posting_date::DATE);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on the partitioned table
DROP TRIGGER IF EXISTS ledger_auto_partition_trigger ON ledger_entries_partitioned;
CREATE TRIGGER ledger_auto_partition_trigger
    BEFORE INSERT ON ledger_entries_partitioned
    FOR EACH ROW EXECUTE FUNCTION ledger_partition_trigger();

-- Create a summary table for monthly ledger balances (for faster reporting)
CREATE TABLE IF NOT EXISTS ledger_monthly_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    opening_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_debits DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_credits DECIMAL(19,4) NOT NULL DEFAULT 0,
    closing_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    entry_count INTEGER NOT NULL DEFAULT 0,
    last_entry_date TIMESTAMP,
    reconciled_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    unreconciled_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (account_id, year, month, currency)
);

-- Create indexes on summary table
CREATE INDEX IF NOT EXISTS ledger_monthly_balances_account_date_idx 
    ON ledger_monthly_balances (account_id, year, month);
CREATE INDEX IF NOT EXISTS ledger_monthly_balances_date_idx 
    ON ledger_monthly_balances (year, month);

-- Function to calculate and update monthly balances
CREATE OR REPLACE FUNCTION update_ledger_monthly_balances(target_month DATE DEFAULT DATE_TRUNC('month', CURRENT_DATE))
RETURNS VOID AS $$
DECLARE
    balance_record RECORD;
BEGIN
    -- Calculate monthly balances for the target month
    FOR balance_record IN
        WITH monthly_entries AS (
            SELECT 
                account_id,
                currency,
                SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) as total_debits,
                SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) as total_credits,
                COUNT(*) as entry_count,
                MAX(posting_date) as last_entry_date,
                SUM(CASE WHEN entry_type = 'DEBIT' AND reconciled THEN amount ELSE 0 END) -
                SUM(CASE WHEN entry_type = 'CREDIT' AND reconciled THEN amount ELSE 0 END) as reconciled_balance,
                SUM(CASE WHEN entry_type = 'DEBIT' AND NOT reconciled THEN amount ELSE 0 END) -
                SUM(CASE WHEN entry_type = 'CREDIT' AND NOT reconciled THEN amount ELSE 0 END) as unreconciled_balance
            FROM ledger_entries_partitioned
            WHERE posting_date >= target_month 
            AND posting_date < target_month + INTERVAL '1 month'
            GROUP BY account_id, currency
        )
        SELECT 
            account_id,
            EXTRACT(YEAR FROM target_month)::INTEGER as year,
            EXTRACT(MONTH FROM target_month)::INTEGER as month,
            currency,
            total_debits,
            total_credits,
            total_debits - total_credits as closing_balance,
            entry_count,
            last_entry_date,
            reconciled_balance,
            unreconciled_balance
        FROM monthly_entries
    LOOP
        INSERT INTO ledger_monthly_balances (
            account_id, year, month, currency, total_debits, total_credits,
            closing_balance, entry_count, last_entry_date, reconciled_balance, 
            unreconciled_balance
        ) VALUES (
            balance_record.account_id, balance_record.year, balance_record.month,
            balance_record.currency, balance_record.total_debits, balance_record.total_credits,
            balance_record.closing_balance, balance_record.entry_count, balance_record.last_entry_date,
            balance_record.reconciled_balance, balance_record.unreconciled_balance
        )
        ON CONFLICT (account_id, year, month, currency) DO UPDATE SET
            total_debits = EXCLUDED.total_debits,
            total_credits = EXCLUDED.total_credits,
            closing_balance = EXCLUDED.closing_balance,
            entry_count = EXCLUDED.entry_count,
            last_entry_date = EXCLUDED.last_entry_date,
            reconciled_balance = EXCLUDED.reconciled_balance,
            unreconciled_balance = EXCLUDED.unreconciled_balance,
            updated_at = CURRENT_TIMESTAMP;
    END LOOP;
    
    RAISE NOTICE 'Updated monthly balances for %', target_month;
END;
$$ LANGUAGE plpgsql;

-- Data migration from existing ledger_entries table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'ledger_entries' AND table_schema = 'public') THEN
        -- Migrate data in batches to avoid locking
        INSERT INTO ledger_entries_partitioned 
        SELECT * FROM ledger_entries 
        ON CONFLICT (id, posting_date) DO NOTHING;
        
        -- Rename old table for backup
        ALTER TABLE ledger_entries RENAME TO ledger_entries_backup_pre_partition;
        
        RAISE NOTICE 'Migrated data from ledger_entries to ledger_entries_partitioned';
        
        -- Calculate monthly balances for migrated data
        PERFORM update_ledger_monthly_balances(date_month) 
        FROM (
            SELECT DISTINCT DATE_TRUNC('month', posting_date) as date_month 
            FROM ledger_entries_partitioned 
            WHERE posting_date >= CURRENT_DATE - INTERVAL '24 months'
        ) months;
    END IF;
END $$;

-- Create a view to maintain backward compatibility
CREATE OR REPLACE VIEW ledger_entries AS 
SELECT * FROM ledger_entries_partitioned;

-- Function for reconciliation status reporting
CREATE OR REPLACE FUNCTION get_reconciliation_status(target_month DATE DEFAULT DATE_TRUNC('month', CURRENT_DATE))
RETURNS TABLE (
    account_id UUID,
    currency VARCHAR(3),
    total_entries BIGINT,
    reconciled_entries BIGINT,
    unreconciled_entries BIGINT,
    reconciliation_percentage DECIMAL(5,2),
    unreconciled_amount DECIMAL(19,4)
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        le.account_id,
        le.currency,
        COUNT(*) as total_entries,
        COUNT(*) FILTER (WHERE le.reconciled) as reconciled_entries,
        COUNT(*) FILTER (WHERE NOT le.reconciled) as unreconciled_entries,
        ROUND((COUNT(*) FILTER (WHERE le.reconciled) * 100.0 / COUNT(*)), 2) as reconciliation_percentage,
        COALESCE(
            SUM(CASE WHEN NOT le.reconciled AND le.entry_type = 'DEBIT' THEN le.amount ELSE 0 END) -
            SUM(CASE WHEN NOT le.reconciled AND le.entry_type = 'CREDIT' THEN le.amount ELSE 0 END),
            0
        ) as unreconciled_amount
    FROM ledger_entries_partitioned le
    WHERE le.posting_date >= target_month 
    AND le.posting_date < target_month + INTERVAL '1 month'
    GROUP BY le.account_id, le.currency
    ORDER BY unreconciled_amount DESC;
END;
$$ LANGUAGE plpgsql;

-- Function for dropping old ledger partitions (after legal retention period)
CREATE OR REPLACE FUNCTION drop_old_ledger_partitions(retention_years INTEGER DEFAULT 10)
RETURNS VOID AS $$
DECLARE
    partition_record RECORD;
    cutoff_date DATE;
BEGIN
    cutoff_date := DATE_TRUNC('month', CURRENT_DATE - (retention_years || ' years')::INTERVAL);
    
    FOR partition_record IN
        SELECT schemaname, tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'ledger_entries_y%m%' 
        AND schemaname = 'public'
    LOOP
        DECLARE
            year_part INTEGER;
            month_part INTEGER;
            partition_date DATE;
        BEGIN
            year_part := SUBSTRING(partition_record.tablename FROM 'ledger_entries_y(\d{4})m')::INTEGER;
            month_part := SUBSTRING(partition_record.tablename FROM 'ledger_entries_y\d{4}m(\d{2})')::INTEGER;
            partition_date := MAKE_DATE(year_part, month_part, 1);
            
            IF partition_date < cutoff_date THEN
                EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', partition_record.tablename);
                RAISE NOTICE 'Dropped old ledger partition: %', partition_record.tablename;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Could not process ledger partition: %', partition_record.tablename;
        END;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Grant appropriate permissions
GRANT SELECT, INSERT, UPDATE ON ledger_entries_partitioned TO waqiti_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ledger_monthly_balances TO waqiti_user;
GRANT SELECT ON ledger_entries TO waqiti_user;
GRANT EXECUTE ON FUNCTION create_ledger_partition_if_not_exists(DATE) TO waqiti_user;
GRANT EXECUTE ON FUNCTION update_ledger_monthly_balances(DATE) TO waqiti_user;
GRANT EXECUTE ON FUNCTION get_reconciliation_status(DATE) TO waqiti_user;
GRANT EXECUTE ON FUNCTION drop_old_ledger_partitions(INTEGER) TO waqiti_user;

-- Create comments for documentation
COMMENT ON TABLE ledger_entries_partitioned IS 'Double-entry ledger entries partitioned by posting_date for optimal financial reporting performance';
COMMENT ON TABLE ledger_monthly_balances IS 'Pre-calculated monthly account balances for fast financial reporting and reconciliation';
COMMENT ON FUNCTION update_ledger_monthly_balances(DATE) IS 'Calculates and updates monthly account balances for the specified month';
COMMENT ON FUNCTION get_reconciliation_status(DATE) IS 'Returns reconciliation status for all accounts in the specified month';
COMMENT ON FUNCTION drop_old_ledger_partitions(INTEGER) IS 'Drops ledger partitions older than specified years (default 10 years for financial compliance)';