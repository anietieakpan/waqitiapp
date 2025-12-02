-- Initialize Lightning Network database for Waqiti
-- This script creates the dedicated database and user for Lightning operations

-- Create Lightning database
CREATE DATABASE waqiti_lightning;

-- Create Lightning user with appropriate permissions
CREATE USER waqiti_lightning WITH PASSWORD 'lightning_secure_password_2024';

-- Grant permissions to Lightning user
GRANT ALL PRIVILEGES ON DATABASE waqiti_lightning TO waqiti_lightning;

-- Connect to Lightning database to set up schema
\c waqiti_lightning;

-- Grant schema permissions
GRANT ALL ON SCHEMA public TO waqiti_lightning;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO waqiti_lightning;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO waqiti_lightning;

-- Create extensions needed for Lightning operations
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- Create custom data types for Lightning
DO $$ 
BEGIN
    -- Invoice status enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'invoice_status') THEN
        CREATE TYPE invoice_status AS ENUM (
            'PENDING',
            'PAID', 
            'EXPIRED',
            'CANCELLED',
            'PROCESSING',
            'FAILED'
        );
    END IF;

    -- Payment status enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
        CREATE TYPE payment_status AS ENUM (
            'PENDING',
            'IN_FLIGHT',
            'COMPLETED',
            'FAILED',
            'CANCELLED'
        );
    END IF;

    -- Payment type enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_type') THEN
        CREATE TYPE payment_type AS ENUM (
            'INVOICE',
            'KEYSEND',
            'STREAM',
            'SWAP'
        );
    END IF;

    -- Channel status enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'channel_status') THEN
        CREATE TYPE channel_status AS ENUM (
            'PENDING_OPEN',
            'ACTIVE',
            'INACTIVE', 
            'CLOSING',
            'FORCE_CLOSING',
            'CLOSED',
            'FORCE_CLOSED',
            'WAITING_CLOSE',
            'NEEDS_ATTENTION'
        );
    END IF;

    -- Stream status enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'stream_status') THEN
        CREATE TYPE stream_status AS ENUM (
            'ACTIVE',
            'PAUSED',
            'COMPLETED',
            'FAILED',
            'STOPPED'
        );
    END IF;

    -- Swap status enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'swap_status') THEN
        CREATE TYPE swap_status AS ENUM (
            'PENDING',
            'CONFIRMED',
            'COMPLETED',
            'FAILED',
            'EXPIRED'
        );
    END IF;

    -- Swap type enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'swap_type') THEN
        CREATE TYPE swap_type AS ENUM (
            'SUBMARINE',
            'REVERSE_SUBMARINE',
            'LOOP_IN',
            'LOOP_OUT'
        );
    END IF;

    -- LNURL type enum
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'lnurl_type') THEN
        CREATE TYPE lnurl_type AS ENUM (
            'PAY',
            'WITHDRAW',
            'AUTH',
            'CHANNEL'
        );
    END IF;

    -- Webhook status enum  
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'webhook_status') THEN
        CREATE TYPE webhook_status AS ENUM (
            'ACTIVE',
            'INACTIVE',
            'FAILED',
            'SUSPENDED'
        );
    END IF;

END$$;

-- Create functions for Lightning operations

-- Function to generate Lightning address
CREATE OR REPLACE FUNCTION generate_lightning_address(username TEXT, domain TEXT DEFAULT 'pay.waqiti.com')
RETURNS TEXT AS $$
BEGIN
    RETURN lower(username) || '@' || domain;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to calculate channel balance ratio
CREATE OR REPLACE FUNCTION calculate_balance_ratio(local_balance BIGINT, capacity BIGINT)
RETURNS DECIMAL(5,4) AS $$
BEGIN
    IF capacity IS NULL OR capacity = 0 THEN
        RETURN 0.0;
    END IF;
    RETURN ROUND((local_balance::DECIMAL / capacity::DECIMAL), 4);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to check if channel needs rebalancing
CREATE OR REPLACE FUNCTION needs_rebalancing(local_balance BIGINT, capacity BIGINT, threshold DECIMAL DEFAULT 0.3)
RETURNS BOOLEAN AS $$
DECLARE
    ratio DECIMAL(5,4);
BEGIN
    ratio := calculate_balance_ratio(local_balance, capacity);
    RETURN ratio < (0.5 - threshold) OR ratio > (0.5 + threshold);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to calculate fee from amount and fee rate
CREATE OR REPLACE FUNCTION calculate_fee(amount_sat BIGINT, fee_rate_ppm BIGINT)
RETURNS BIGINT AS $$
BEGIN
    RETURN GREATEST(1, (amount_sat * fee_rate_ppm / 1000000));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Function to generate payment hash (simulation for testing)
CREATE OR REPLACE FUNCTION generate_payment_hash()
RETURNS TEXT AS $$
BEGIN
    RETURN encode(gen_random_bytes(32), 'hex');
END;
$$ LANGUAGE plpgsql VOLATILE;

-- Create audit trigger function for Lightning operations
CREATE OR REPLACE FUNCTION lightning_audit_trigger()
RETURNS TRIGGER AS $$
DECLARE
    audit_table_name TEXT;
    audit_record RECORD;
BEGIN
    audit_table_name := TG_TABLE_NAME || '_audit';
    
    IF TG_OP = 'DELETE' THEN
        audit_record := OLD;
    ELSE
        audit_record := NEW;
    END IF;
    
    -- Log the operation (simplified version)
    INSERT INTO lightning_operations_log (
        table_name,
        operation,
        record_id,
        user_id,
        timestamp,
        data_before,
        data_after
    ) VALUES (
        TG_TABLE_NAME,
        TG_OP,
        COALESCE(NEW.id, OLD.id),
        COALESCE(NEW.user_id, OLD.user_id, 'system'),
        NOW(),
        CASE WHEN TG_OP != 'INSERT' THEN row_to_json(OLD) END,
        CASE WHEN TG_OP != 'DELETE' THEN row_to_json(NEW) END
    );
    
    RETURN audit_record;
END;
$$ LANGUAGE plpgsql;

-- Create audit log table
CREATE TABLE IF NOT EXISTS lightning_operations_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_name VARCHAR(64) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    record_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    data_before JSONB,
    data_after JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for audit log
CREATE INDEX IF NOT EXISTS idx_lightning_audit_table_operation ON lightning_operations_log(table_name, operation);
CREATE INDEX IF NOT EXISTS idx_lightning_audit_user_timestamp ON lightning_operations_log(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_lightning_audit_record ON lightning_operations_log(record_id);

-- Grant permissions on functions and audit table
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO waqiti_lightning;
GRANT ALL PRIVILEGES ON lightning_operations_log TO waqiti_lightning;

-- Create performance monitoring views
CREATE OR REPLACE VIEW lightning_performance_summary AS
SELECT 
    'invoices' as metric_type,
    COUNT(*) as total_count,
    COUNT(*) FILTER (WHERE status = 'PAID') as success_count,
    ROUND(COUNT(*) FILTER (WHERE status = 'PAID') * 100.0 / COUNT(*), 2) as success_rate,
    AVG(EXTRACT(EPOCH FROM (settled_at - created_at))) as avg_settlement_time_seconds
FROM lightning_invoices 
WHERE created_at >= NOW() - INTERVAL '24 hours'

UNION ALL

SELECT 
    'payments' as metric_type,
    COUNT(*) as total_count, 
    COUNT(*) FILTER (WHERE status = 'COMPLETED') as success_count,
    ROUND(COUNT(*) FILTER (WHERE status = 'COMPLETED') * 100.0 / COUNT(*), 2) as success_rate,
    AVG(EXTRACT(EPOCH FROM (completed_at - created_at))) as avg_settlement_time_seconds
FROM lightning_payments
WHERE created_at >= NOW() - INTERVAL '24 hours';

-- Create channel health monitoring view
CREATE OR REPLACE VIEW channel_health_summary AS
SELECT 
    id,
    user_id,
    remote_pubkey,
    capacity,
    local_balance,
    remote_balance,
    calculate_balance_ratio(local_balance, capacity) as balance_ratio,
    needs_rebalancing(local_balance, capacity) as needs_rebalancing,
    status,
    EXTRACT(EPOCH FROM (NOW() - COALESCE(last_activity_at, opened_at))) / 3600 as hours_since_activity,
    total_fees_earned,
    CASE 
        WHEN capacity > 0 THEN ROUND((total_fees_earned * 100.0 / capacity), 4)
        ELSE 0
    END as earnings_rate_percent
FROM lightning_channels 
WHERE status = 'ACTIVE';

-- Grant permissions on views
GRANT SELECT ON lightning_performance_summary TO waqiti_lightning;
GRANT SELECT ON channel_health_summary TO waqiti_lightning;

-- Create notification function for critical events
CREATE OR REPLACE FUNCTION notify_lightning_event()
RETURNS TRIGGER AS $$
BEGIN
    -- Notify on payment failures
    IF TG_TABLE_NAME = 'lightning_payments' AND NEW.status = 'FAILED' THEN
        PERFORM pg_notify('lightning_payment_failed', NEW.id);
    END IF;
    
    -- Notify on channel issues
    IF TG_TABLE_NAME = 'lightning_channels' AND NEW.status = 'NEEDS_ATTENTION' THEN
        PERFORM pg_notify('lightning_channel_attention', NEW.id);
    END IF;
    
    -- Notify on large payments
    IF TG_TABLE_NAME = 'lightning_payments' AND NEW.amount_sat > 1000000 THEN
        PERFORM pg_notify('lightning_large_payment', NEW.id);
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Set ownership
ALTER DATABASE waqiti_lightning OWNER TO waqiti_lightning;

COMMENT ON DATABASE waqiti_lightning IS 'Dedicated database for Waqiti Lightning Network operations and data persistence';

-- Log successful initialization
INSERT INTO lightning_operations_log (
    table_name,
    operation, 
    record_id,
    user_id,
    data_after
) VALUES (
    'database_init',
    'CREATE',
    'waqiti_lightning',
    'system',
    '{"status": "initialized", "timestamp": "' || NOW() || '"}'
);