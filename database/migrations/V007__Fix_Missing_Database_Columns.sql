-- Fix missing database columns and schema issues
-- This migration addresses database schema mismatches identified in the forensic analysis

-- ==============================================================================
-- PAYMENT PROCESSING TABLE FIXES
-- ==============================================================================

-- Add missing columns to payment_processing table (if table exists)
DO $$
BEGIN
    -- Check if payment_processing table exists
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payment_processing') THEN
        -- Add customer_id column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'payment_processing' AND column_name = 'customer_id') THEN
            ALTER TABLE payment_processing ADD COLUMN customer_id UUID;
            ALTER TABLE payment_processing ADD CONSTRAINT fk_payment_processing_customer 
                FOREIGN KEY (customer_id) REFERENCES users(id);
        END IF;
        
        -- Add merchant_id column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'payment_processing' AND column_name = 'merchant_id') THEN
            ALTER TABLE payment_processing ADD COLUMN merchant_id UUID;
            -- Note: merchant table may not exist yet, so we'll add constraint later if needed
        END IF;
        
        -- Add provider_id column if it doesn't exist
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'payment_processing' AND column_name = 'provider_id') THEN
            ALTER TABLE payment_processing ADD COLUMN provider_id VARCHAR(100);
        END IF;
        
        -- Add indexes for performance
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_customer_id 
            ON payment_processing(customer_id);
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_merchant_id 
            ON payment_processing(merchant_id);
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_processing_provider_id 
            ON payment_processing(provider_id);
    END IF;
END $$;

-- ==============================================================================
-- GUARDIAN APPROVAL SYSTEM TABLES
-- ==============================================================================

-- Create guardian_approval_requests table if it doesn't exist
CREATE TABLE IF NOT EXISTS guardian_approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_request_id VARCHAR(255) UNIQUE NOT NULL,
    dependent_user_id UUID NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    required_guardian_ids UUID[] NOT NULL,
    approved_guardian_ids UUID[] DEFAULT '{}',
    rejected_guardian_ids UUID[] DEFAULT '{}',
    approval_threshold INTEGER NOT NULL DEFAULT 1,
    request_data JSONB,
    rejection_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    
    CONSTRAINT chk_guardian_approval_status 
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'PARTIALLY_APPROVED')),
    CONSTRAINT chk_guardian_approval_threshold_positive 
        CHECK (approval_threshold > 0),
    CONSTRAINT chk_guardian_required_guardians_not_empty 
        CHECK (array_length(required_guardian_ids, 1) > 0),
    CONSTRAINT fk_guardian_approval_dependent_user 
        FOREIGN KEY (dependent_user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes for guardian_approval_requests
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approval_dependent_user 
    ON guardian_approval_requests(dependent_user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approval_status 
    ON guardian_approval_requests(status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approval_expires_at 
    ON guardian_approval_requests(expires_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approval_action_type 
    ON guardian_approval_requests(action_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approval_created_at 
    ON guardian_approval_requests(created_at DESC);

-- Create GIN index for guardian IDs arrays (for efficient array operations)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approval_required_guardians 
    ON guardian_approval_requests USING gin(required_guardian_ids);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approval_approved_guardians 
    ON guardian_approval_requests USING gin(approved_guardian_ids);

-- ==============================================================================
-- FAMILY GUARDIANSHIP ENHANCEMENTS
-- ==============================================================================

-- Add missing columns to family_guardianships table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'family_guardianships') THEN
        -- Add guardian_type column if missing
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'family_guardianships' AND column_name = 'guardian_type') THEN
            ALTER TABLE family_guardianships ADD COLUMN guardian_type VARCHAR(50) DEFAULT 'PRIMARY';
        END IF;
        
        -- Add approval_required column if missing
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'family_guardianships' AND column_name = 'approval_required') THEN
            ALTER TABLE family_guardianships ADD COLUMN approval_required BOOLEAN DEFAULT true;
        END IF;
        
        -- Add spending_limit column if missing
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'family_guardianships' AND column_name = 'spending_limit') THEN
            ALTER TABLE family_guardianships ADD COLUMN spending_limit DECIMAL(19,2);
        END IF;
        
        -- Create composite index for efficient queries
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_guardianships_composite 
            ON family_guardianships(guardian_user_id, status, created_at DESC);
    END IF;
END $$;

-- ==============================================================================
-- TRANSACTION PROCESSING ENHANCEMENTS
-- ==============================================================================

-- Add missing columns to transactions table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'transactions') THEN
        -- Add reconciliation flag if missing
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'transactions' AND column_name = 'requires_reconciliation') THEN
            ALTER TABLE transactions ADD COLUMN requires_reconciliation BOOLEAN DEFAULT false;
        END IF;
        
        -- Add idempotency key if missing
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'transactions' AND column_name = 'idempotency_key') THEN
            ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(255) UNIQUE;
        END IF;
        
        -- Add provider information if missing
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'transactions' AND column_name = 'provider_transaction_id') THEN
            ALTER TABLE transactions ADD COLUMN provider_transaction_id VARCHAR(255);
        END IF;
        
        -- Create indexes
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reconciliation 
            ON transactions(requires_reconciliation) WHERE requires_reconciliation = true;
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_provider_id 
            ON transactions(provider_transaction_id);
    END IF;
END $$;

-- ==============================================================================
-- WALLET ENHANCEMENTS
-- ==============================================================================

-- Add missing columns to wallets table if it exists
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'wallets') THEN
        -- Add reserved_balance column if missing
        IF NOT EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_name = 'wallets' AND column_name = 'reserved_balance') THEN
            ALTER TABLE wallets ADD COLUMN reserved_balance DECIMAL(19,2) DEFAULT 0.00;
            ALTER TABLE wallets ADD CONSTRAINT chk_wallets_reserved_balance_non_negative 
                CHECK (reserved_balance >= 0);
        END IF;
        
        -- Add available_balance calculated column constraint
        -- available_balance = balance - reserved_balance
        IF NOT EXISTS (SELECT FROM information_schema.table_constraints 
                      WHERE constraint_name = 'chk_wallets_available_balance') THEN
            ALTER TABLE wallets ADD CONSTRAINT chk_wallets_available_balance 
                CHECK ((balance - reserved_balance) >= 0);
        END IF;
    END IF;
END $$;

-- ==============================================================================
-- AUDIT AND LOGGING ENHANCEMENTS
-- ==============================================================================

-- Create audit_events table if it doesn't exist (for comprehensive audit trail)
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    user_id UUID,
    session_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    event_data JSONB,
    risk_score DECIMAL(3,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_audit_risk_score CHECK (risk_score >= 0.00 AND risk_score <= 1.00)
);

-- Create indexes for audit_events
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_created_at 
    ON audit_events(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_user_id 
    ON audit_events(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_entity 
    ON audit_events(entity_type, entity_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_event_type 
    ON audit_events(event_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_risk_score 
    ON audit_events(risk_score) WHERE risk_score > 0.7;

-- Create GIN index for event_data JSONB queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_events_data 
    ON audit_events USING gin(event_data);

-- ==============================================================================
-- KAFKA EVENT TRACKING
-- ==============================================================================

-- Create kafka_events table for tracking message processing
CREATE TABLE IF NOT EXISTS kafka_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_name VARCHAR(255) NOT NULL,
    partition_id INTEGER,
    offset_number BIGINT,
    message_key VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMP,
    processing_status VARCHAR(20) DEFAULT 'PENDING',
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_kafka_events_status 
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DLQ')),
    CONSTRAINT chk_kafka_events_retry_count 
        CHECK (retry_count >= 0)
);

-- Create indexes for kafka_events
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kafka_events_topic_partition 
    ON kafka_events(topic_name, partition_id, offset_number);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kafka_events_status 
    ON kafka_events(processing_status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kafka_events_created_at 
    ON kafka_events(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kafka_events_failed 
    ON kafka_events(created_at) WHERE processing_status = 'FAILED';

-- ==============================================================================
-- PAYMENT FRAUD DETECTION
-- ==============================================================================

-- Create fraud_detection_results table
CREATE TABLE IF NOT EXISTS fraud_detection_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    payment_id UUID,
    user_id UUID NOT NULL,
    risk_score DECIMAL(5,4) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    fraud_indicators JSONB,
    ml_model_version VARCHAR(50),
    rules_triggered TEXT[],
    decision VARCHAR(20) NOT NULL,
    decision_reason TEXT,
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_fraud_risk_score CHECK (risk_score >= 0.0000 AND risk_score <= 1.0000),
    CONSTRAINT chk_fraud_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_fraud_decision CHECK (decision IN ('APPROVE', 'REVIEW', 'BLOCK', 'CHALLENGE')),
    CONSTRAINT fk_fraud_detection_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_fraud_detection_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id)
);

-- Create indexes for fraud_detection_results
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_detection_transaction 
    ON fraud_detection_results(transaction_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_detection_user_score 
    ON fraud_detection_results(user_id, risk_score DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_detection_decision 
    ON fraud_detection_results(decision, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_detection_high_risk 
    ON fraud_detection_results(created_at) WHERE risk_level IN ('HIGH', 'CRITICAL');

-- ==============================================================================
-- DATA INTEGRITY CONSTRAINTS
-- ==============================================================================

-- Add additional constraints for data integrity
DO $$
BEGIN
    -- Ensure transaction amounts are positive
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'transactions') THEN
        IF NOT EXISTS (SELECT FROM information_schema.table_constraints 
                      WHERE constraint_name = 'chk_transactions_amount_positive_enhanced') THEN
            -- Only add if not already exists
            BEGIN
                ALTER TABLE transactions ADD CONSTRAINT chk_transactions_amount_positive_enhanced 
                    CHECK (amount > 0);
            EXCEPTION 
                WHEN duplicate_object THEN NULL; -- Ignore if constraint already exists
            END;
        END IF;
    END IF;
    
    -- Ensure wallet balances are non-negative
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'wallets') THEN
        IF NOT EXISTS (SELECT FROM information_schema.table_constraints 
                      WHERE constraint_name = 'chk_wallets_balance_non_negative_enhanced') THEN
            BEGIN
                ALTER TABLE wallets ADD CONSTRAINT chk_wallets_balance_non_negative_enhanced 
                    CHECK (balance >= 0);
            EXCEPTION 
                WHEN duplicate_object THEN NULL;
            END;
        END IF;
    END IF;
END $$;

-- ==============================================================================
-- PERFORMANCE OPTIMIZATION INDEXES
-- ==============================================================================

-- Create additional performance indexes based on common query patterns
DO $$
BEGIN
    -- Users table indexes
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'users') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_status_created 
            ON users(status, created_at DESC);
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_kyc_status 
            ON users(kyc_status) WHERE kyc_status IS NOT NULL;
    END IF;
    
    -- Payments table indexes (if exists)
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'payments') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_status_amount 
            ON payments(status, amount DESC);
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_created_at_status 
            ON payments(created_at DESC, status);
    END IF;
    
    -- Wallets table composite indexes
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'wallets') THEN
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_currency_status 
            ON wallets(user_id, currency, status);
        CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_balance_currency 
            ON wallets(balance DESC, currency) WHERE status = 'ACTIVE';
    END IF;
END $$;

-- ==============================================================================
-- CLEANUP AND MAINTENANCE
-- ==============================================================================

-- Update table statistics after schema changes
ANALYZE;

-- Log migration completion
INSERT INTO schema_migrations_log (migration_version, description, executed_at) 
VALUES ('V007', 'Fix Missing Database Columns and Schema Issues', NOW())
ON CONFLICT (migration_version) DO NOTHING;