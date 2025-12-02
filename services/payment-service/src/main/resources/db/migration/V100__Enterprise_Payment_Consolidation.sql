-- =====================================================
-- ENTERPRISE PAYMENT SERVICE CONSOLIDATION MIGRATION
-- Version: 100.0.0
-- Date: 2025-01-15
-- Description: Comprehensive payment service consolidation
-- =====================================================

-- =====================================================
-- PHASE 1: BACKUP AND PREPARATION
-- =====================================================

-- Create backup schema for rollback capability
CREATE SCHEMA IF NOT EXISTS payment_backup;

-- Backup existing payment_requests table
CREATE TABLE payment_backup.payment_requests_v99 AS 
SELECT * FROM payment_requests;

-- Backup other critical tables
CREATE TABLE payment_backup.scheduled_payments_v99 AS 
SELECT * FROM scheduled_payments;

CREATE TABLE payment_backup.split_payments_v99 AS 
SELECT * FROM split_payments;

-- =====================================================
-- PHASE 2: ENHANCED PAYMENT_REQUESTS TABLE
-- =====================================================

-- Add new columns to existing payment_requests table
ALTER TABLE payment_requests
    -- Core identifiers
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) UNIQUE,
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(50),
    
    -- Payment classification
    ADD COLUMN IF NOT EXISTS payment_type VARCHAR(50) CHECK (payment_type IN ('P2P', 'MERCHANT', 'BILL_PAY', 'INTERNATIONAL', 'CRYPTO', 'GROUP', 'RECURRING', 'INSTANT')),
    ADD COLUMN IF NOT EXISTS payment_category VARCHAR(50),
    ADD COLUMN IF NOT EXISTS merchant_category_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS processing_priority VARCHAR(20) DEFAULT 'NORMAL' CHECK (processing_priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT', 'INSTANT')),
    ADD COLUMN IF NOT EXISTS recipient_type VARCHAR(20) CHECK (recipient_type IN ('USER', 'MERCHANT', 'BUSINESS', 'EXTERNAL', 'SYSTEM')),
    
    -- Temporal controls
    ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS recurrence_pattern VARCHAR(50),
    ADD COLUMN IF NOT EXISTS recurrence_end_date TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS max_occurrences INTEGER CHECK (max_occurrences >= 1 AND max_occurrences <= 1000),
    
    -- Split and group payments
    ADD COLUMN IF NOT EXISTS is_split_payment BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS split_payment_id UUID,
    ADD COLUMN IF NOT EXISTS split_percentage DECIMAL(5,2) CHECK (split_percentage >= 0.01 AND split_percentage <= 100.00),
    ADD COLUMN IF NOT EXISTS is_group_payment BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS group_payment_id UUID,
    
    -- External references
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS memo VARCHAR(500),
    ADD COLUMN IF NOT EXISTS category VARCHAR(50),
    ADD COLUMN IF NOT EXISTS merchant_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS customer_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS order_id VARCHAR(50),
    
    -- Metadata and extensibility
    ADD COLUMN IF NOT EXISTS tags JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS metadata JSONB,
    ADD COLUMN IF NOT EXISTS custom_fields JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS business_context JSONB DEFAULT '{}',
    
    -- Approval workflow
    ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approval_workflow_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS approver_ids UUID[],
    ADD COLUMN IF NOT EXISTS approval_threshold_amount DECIMAL(19,4),
    ADD COLUMN IF NOT EXISTS approval_threshold_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS required_approvals INTEGER DEFAULT 1 CHECK (required_approvals >= 1 AND required_approvals <= 10),
    
    -- Partial payments and limits
    ADD COLUMN IF NOT EXISTS allow_partial_payment BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS minimum_amount DECIMAL(19,4),
    ADD COLUMN IF NOT EXISTS minimum_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS maximum_amount DECIMAL(19,4),
    ADD COLUMN IF NOT EXISTS maximum_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS daily_limit_amount DECIMAL(19,4),
    ADD COLUMN IF NOT EXISTS daily_limit_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS monthly_limit_amount DECIMAL(19,4),
    ADD COLUMN IF NOT EXISTS monthly_limit_currency VARCHAR(3),
    
    -- Security and compliance
    ADD COLUMN IF NOT EXISTS security_level VARCHAR(20) DEFAULT 'STANDARD' CHECK (security_level IN ('STANDARD', 'ENHANCED', 'MAXIMUM')),
    ADD COLUMN IF NOT EXISTS security_token VARCHAR(255),
    ADD COLUMN IF NOT EXISTS requires_kyc BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requires_aml_check BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS compliance_level VARCHAR(20) DEFAULT 'STANDARD' CHECK (compliance_level IN ('STANDARD', 'ENHANCED', 'PREMIUM', 'INSTITUTIONAL')),
    ADD COLUMN IF NOT EXISTS risk_score DECIMAL(5,2) CHECK (risk_score >= 0 AND risk_score <= 100),
    ADD COLUMN IF NOT EXISTS fraud_check_required BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS sanctions_check_required BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pep_check_required BOOLEAN DEFAULT FALSE,
    
    -- Notifications
    ADD COLUMN IF NOT EXISTS notify_sender BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notify_recipient BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notification_channels TEXT[],
    ADD COLUMN IF NOT EXISTS notification_template VARCHAR(100),
    ADD COLUMN IF NOT EXISTS custom_message VARCHAR(1000),
    
    -- Geographic and regulatory
    ADD COLUMN IF NOT EXISTS sender_country CHAR(2),
    ADD COLUMN IF NOT EXISTS recipient_country CHAR(2),
    ADD COLUMN IF NOT EXISTS regulatory_context VARCHAR(20) DEFAULT 'DOMESTIC' CHECK (regulatory_context IN ('DOMESTIC', 'INTERNATIONAL', 'HIGH_RISK', 'SANCTIONS')),
    ADD COLUMN IF NOT EXISTS jurisdiction VARCHAR(50),
    ADD COLUMN IF NOT EXISTS tax_reporting_required BOOLEAN DEFAULT FALSE,
    
    -- Fee management
    ADD COLUMN IF NOT EXISTS fee_structure VARCHAR(20) DEFAULT 'SENDER_PAYS' CHECK (fee_structure IN ('SENDER_PAYS', 'RECIPIENT_PAYS', 'SPLIT', 'EXTERNAL', 'WAIVED')),
    ADD COLUMN IF NOT EXISTS estimated_fees_amount DECIMAL(19,4),
    ADD COLUMN IF NOT EXISTS estimated_fees_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS maximum_fee_amount DECIMAL(19,4),
    ADD COLUMN IF NOT EXISTS maximum_fee_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS fee_calculation_method VARCHAR(20) DEFAULT 'DYNAMIC' CHECK (fee_calculation_method IN ('FIXED', 'PERCENTAGE', 'TIERED', 'DYNAMIC')),
    
    -- Source and traceability
    ADD COLUMN IF NOT EXISTS source_channel VARCHAR(20) CHECK (source_channel IN ('WEB', 'MOBILE', 'API', 'PARTNER', 'ATM', 'BRANCH', 'CALL_CENTER')),
    ADD COLUMN IF NOT EXISTS source_application VARCHAR(100),
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ip_address INET,
    ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255),
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(100),
    
    -- Retry and idempotency
    ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0 CHECK (retry_count >= 0 AND retry_count <= 5),
    ADD COLUMN IF NOT EXISTS original_request_id UUID,
    
    -- Performance and caching
    ADD COLUMN IF NOT EXISTS cache_ttl_seconds INTEGER DEFAULT 300 CHECK (cache_ttl_seconds >= 0 AND cache_ttl_seconds <= 86400),
    ADD COLUMN IF NOT EXISTS processing_timeout_seconds INTEGER DEFAULT 30 CHECK (processing_timeout_seconds >= 1 AND processing_timeout_seconds <= 300),
    
    -- Audit fields
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(20) DEFAULT '2.0.0';

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_payment_requests_idempotency ON payment_requests(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_payment_requests_correlation ON payment_requests(correlation_id);
CREATE INDEX IF NOT EXISTS idx_payment_requests_type ON payment_requests(payment_type);
CREATE INDEX IF NOT EXISTS idx_payment_requests_status_type ON payment_requests(status, payment_type);
CREATE INDEX IF NOT EXISTS idx_payment_requests_sender ON payment_requests(requestor_id);
CREATE INDEX IF NOT EXISTS idx_payment_requests_recipient ON payment_requests(recipient_id);
CREATE INDEX IF NOT EXISTS idx_payment_requests_scheduled ON payment_requests(scheduled_at) WHERE scheduled_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_requests_group ON payment_requests(group_payment_id) WHERE group_payment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_requests_split ON payment_requests(split_payment_id) WHERE split_payment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_requests_risk ON payment_requests(risk_score) WHERE risk_score > 75;

-- =====================================================
-- PHASE 3: CONSOLIDATED PAYMENT SERVICES TABLE
-- =====================================================

-- Create unified payment services configuration
CREATE TABLE IF NOT EXISTS payment_service_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(50) UNIQUE NOT NULL,
    service_type VARCHAR(50) NOT NULL CHECK (service_type IN ('CORE', 'GROUP', 'RECURRING', 'BILL', 'MERCHANT', 'INTERNATIONAL', 'CRYPTO')),
    is_active BOOLEAN DEFAULT TRUE,
    configuration JSONB NOT NULL,
    feature_flags JSONB DEFAULT '{}',
    rate_limits JSONB DEFAULT '{}',
    circuit_breaker_config JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- PHASE 4: PAYMENT EVENT SOURCING
-- =====================================================

CREATE TABLE IF NOT EXISTS payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    event_data JSONB NOT NULL,
    event_metadata JSONB,
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sequence_number BIGSERIAL
);

CREATE INDEX idx_payment_events_aggregate ON payment_events(aggregate_id, sequence_number);
CREATE INDEX idx_payment_events_correlation ON payment_events(correlation_id);
CREATE INDEX idx_payment_events_type ON payment_events(event_type);

-- =====================================================
-- PHASE 5: PAYMENT SAGA ORCHESTRATION
-- =====================================================

CREATE TABLE IF NOT EXISTS payment_sagas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_type VARCHAR(50) NOT NULL,
    saga_state VARCHAR(50) NOT NULL,
    saga_data JSONB NOT NULL,
    current_step VARCHAR(100),
    completed_steps JSONB DEFAULT '[]',
    compensation_data JSONB,
    correlation_id VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

CREATE INDEX idx_payment_sagas_correlation ON payment_sagas(correlation_id);
CREATE INDEX idx_payment_sagas_state ON payment_sagas(saga_state);

-- =====================================================
-- PHASE 6: UNIFIED WALLET INTEGRATION
-- =====================================================

CREATE TABLE IF NOT EXISTS payment_wallet_integration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    wallet_id UUID NOT NULL,
    wallet_type VARCHAR(50) NOT NULL,
    integration_type VARCHAR(50) NOT NULL CHECK (integration_type IN ('DEBIT', 'CREDIT', 'RESERVE', 'RELEASE', 'TRANSFER')),
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    reservation_id VARCHAR(100),
    initiated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

CREATE INDEX idx_payment_wallet_payment ON payment_wallet_integration(payment_id);
CREATE INDEX idx_payment_wallet_wallet ON payment_wallet_integration(wallet_id);

-- =====================================================
-- PHASE 7: MIGRATION PROCEDURES
-- =====================================================

-- Function to migrate recurring payments
CREATE OR REPLACE FUNCTION migrate_recurring_payments() RETURNS void AS $$
BEGIN
    INSERT INTO payment_requests (
        id,
        requestor_id,
        recipient_id,
        amount,
        currency,
        description,
        status,
        payment_type,
        recurrence_pattern,
        scheduled_at,
        recurrence_end_date,
        created_at,
        updated_at
    )
    SELECT 
        id,
        sender_id,
        recipient_id,
        amount,
        currency,
        description,
        status,
        'RECURRING',
        frequency,
        start_date::timestamp,
        end_date::timestamp,
        created_at,
        updated_at
    FROM recurring_payments
    WHERE NOT EXISTS (
        SELECT 1 FROM payment_requests pr 
        WHERE pr.id = recurring_payments.id
    );
END;
$$ LANGUAGE plpgsql;

-- Function to migrate split payments
CREATE OR REPLACE FUNCTION migrate_split_payments() RETURNS void AS $$
BEGIN
    INSERT INTO payment_requests (
        id,
        requestor_id,
        recipient_id,
        amount,
        currency,
        description,
        status,
        payment_type,
        is_split_payment,
        split_payment_id,
        created_at,
        updated_at
    )
    SELECT 
        sp.id,
        sp.organizer_id,
        sp.organizer_id, -- Will be updated by participants
        sp.total_amount,
        sp.currency,
        sp.description,
        sp.status,
        'GROUP',
        true,
        sp.id,
        sp.created_at,
        sp.updated_at
    FROM split_payments sp
    WHERE NOT EXISTS (
        SELECT 1 FROM payment_requests pr 
        WHERE pr.id = sp.id
    );
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- PHASE 8: DATA MIGRATION EXECUTION
-- =====================================================

-- Execute migrations
SELECT migrate_recurring_payments();
SELECT migrate_split_payments();

-- =====================================================
-- PHASE 9: CONSTRAINTS AND VALIDATION
-- =====================================================

-- Add constraints for data integrity
ALTER TABLE payment_requests
    ADD CONSTRAINT chk_payment_amount_positive CHECK (amount > 0),
    ADD CONSTRAINT chk_expiry_future CHECK (expiry_date > created_at),
    ADD CONSTRAINT chk_split_percentage_valid CHECK (
        (is_split_payment = false) OR 
        (is_split_payment = true AND split_percentage IS NOT NULL)
    );

-- =====================================================
-- PHASE 10: AUDIT AND COMPLETION
-- =====================================================

-- Create migration audit log
CREATE TABLE IF NOT EXISTS payment_migration_audit (
    id SERIAL PRIMARY KEY,
    migration_version VARCHAR(20) NOT NULL,
    migration_type VARCHAR(50) NOT NULL,
    records_migrated INTEGER,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20),
    error_message TEXT
);

-- Log migration completion
INSERT INTO payment_migration_audit (
    migration_version,
    migration_type,
    records_migrated,
    completed_at,
    status
) VALUES (
    '100.0.0',
    'ENTERPRISE_CONSOLIDATION',
    (SELECT COUNT(*) FROM payment_requests),
    CURRENT_TIMESTAMP,
    'COMPLETED'
);

-- Grant appropriate permissions
GRANT SELECT, INSERT, UPDATE ON payment_requests TO payment_service_role;
GRANT SELECT ON payment_events TO payment_service_role;
GRANT SELECT, INSERT, UPDATE ON payment_sagas TO payment_service_role;
GRANT SELECT, INSERT, UPDATE ON payment_wallet_integration TO payment_service_role;

-- =====================================================
-- ROLLBACK PLAN (IF NEEDED)
-- =====================================================
-- To rollback: 
-- 1. DROP TABLE payment_requests CASCADE;
-- 2. CREATE TABLE payment_requests AS SELECT * FROM payment_backup.payment_requests_v99;
-- 3. Restore original indexes and constraints