-- ============================================================================
-- V13 Migration: Create Missing ElementCollection Tables
-- ============================================================================
--
-- CRITICAL FIX: Create database tables for JPA @ElementCollection mappings
-- that were defined in entities but missing from database schema.
--
-- These collections are critical for:
-- - Customer risk profile analysis (high-risk countries, risk factors)
-- - SAR filing documentation (transaction IDs, attachments, evidence)
-- - Compliance rule configuration (conditions, jurisdictions)
--
-- Without these tables, persistence operations fail with:
-- org.hibernate.PersistenceException: Collection not found
--
-- Author: Waqiti Engineering Team
-- Date: 2025-11-19
-- JIRA: COMP-1235 (Production Readiness - ElementCollection Tables)
-- ============================================================================

-- ============================================================================
-- CUSTOMER RISK PROFILE COLLECTIONS
-- ============================================================================

-- High-risk countries associated with customer profiles
CREATE TABLE customer_high_risk_countries (
    customer_risk_profile_id UUID NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    added_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_customer_high_risk_countries
        PRIMARY KEY (customer_risk_profile_id, country_code),
    CONSTRAINT fk_customer_risk_countries_profile
        FOREIGN KEY (customer_risk_profile_id)
        REFERENCES customer_risk_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_customer_high_risk_countries_profile
ON customer_high_risk_countries(customer_risk_profile_id);

CREATE INDEX idx_customer_high_risk_countries_country
ON customer_high_risk_countries(country_code);

-- ============================================================================

-- Risk factors contributing to customer risk score
CREATE TABLE customer_risk_factors (
    customer_risk_profile_id UUID NOT NULL,
    factor_type VARCHAR(50) NOT NULL,
    factor_description VARCHAR(500),
    risk_weight DECIMAL(5,2),
    detected_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_customer_risk_factors
        PRIMARY KEY (customer_risk_profile_id, factor_type, detected_date),
    CONSTRAINT fk_customer_risk_factors_profile
        FOREIGN KEY (customer_risk_profile_id)
        REFERENCES customer_risk_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_customer_risk_factors_profile
ON customer_risk_factors(customer_risk_profile_id);

CREATE INDEX idx_customer_risk_factors_type
ON customer_risk_factors(factor_type);

-- ============================================================================

-- Transaction patterns observed for the customer
CREATE TABLE customer_transaction_patterns (
    customer_risk_profile_id UUID NOT NULL,
    pattern_type VARCHAR(50) NOT NULL,
    pattern_description VARCHAR(500),
    frequency INTEGER,
    last_observed TIMESTAMP,

    CONSTRAINT pk_customer_transaction_patterns
        PRIMARY KEY (customer_risk_profile_id, pattern_type),
    CONSTRAINT fk_customer_transaction_patterns_profile
        FOREIGN KEY (customer_risk_profile_id)
        REFERENCES customer_risk_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_customer_transaction_patterns_profile
ON customer_transaction_patterns(customer_risk_profile_id);

-- ============================================================================

-- Countries where customer frequently transacts
CREATE TABLE customer_preferred_countries (
    customer_risk_profile_id UUID NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    transaction_count INTEGER,
    total_amount DECIMAL(19,4),

    CONSTRAINT pk_customer_preferred_countries
        PRIMARY KEY (customer_risk_profile_id, country_code),
    CONSTRAINT fk_customer_preferred_countries_profile
        FOREIGN KEY (customer_risk_profile_id)
        REFERENCES customer_risk_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_customer_preferred_countries_profile
ON customer_preferred_countries(customer_risk_profile_id);

-- ============================================================================

-- Payment methods used by customer
CREATE TABLE customer_payment_methods (
    customer_risk_profile_id UUID NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    usage_count INTEGER,
    last_used TIMESTAMP,

    CONSTRAINT pk_customer_payment_methods
        PRIMARY KEY (customer_risk_profile_id, payment_method),
    CONSTRAINT fk_customer_payment_methods_profile
        FOREIGN KEY (customer_risk_profile_id)
        REFERENCES customer_risk_profiles(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_customer_payment_methods_profile
ON customer_payment_methods(customer_risk_profile_id);

-- ============================================================================
-- SAR FILING COLLECTIONS
-- ============================================================================

-- Transaction IDs referenced in SAR
CREATE TABLE sar_transaction_ids (
    sar_id UUID NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    transaction_date TIMESTAMP,
    transaction_amount DECIMAL(19,4),
    sequence_order INTEGER,

    CONSTRAINT pk_sar_transaction_ids
        PRIMARY KEY (sar_id, transaction_id),
    CONSTRAINT fk_sar_transaction_ids_sar
        FOREIGN KEY (sar_id)
        REFERENCES suspicious_activities(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_sar_transaction_ids_sar
ON sar_transaction_ids(sar_id);

CREATE INDEX idx_sar_transaction_ids_transaction
ON sar_transaction_ids(transaction_id);

-- ============================================================================

-- Account IDs involved in suspicious activity
CREATE TABLE sar_account_ids (
    sar_id UUID NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    account_type VARCHAR(50),
    account_role VARCHAR(50), -- PRIMARY, SECONDARY, BENEFICIARY

    CONSTRAINT pk_sar_account_ids
        PRIMARY KEY (sar_id, account_id),
    CONSTRAINT fk_sar_account_ids_sar
        FOREIGN KEY (sar_id)
        REFERENCES suspicious_activities(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_sar_account_ids_sar
ON sar_account_ids(sar_id);

CREATE INDEX idx_sar_account_ids_account
ON sar_account_ids(account_id);

-- ============================================================================

-- Attachments and supporting documentation for SAR
CREATE TABLE sar_attachments (
    sar_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50),
    file_size_bytes BIGINT,
    s3_bucket VARCHAR(255),
    s3_key VARCHAR(500),
    upload_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_by UUID,

    CONSTRAINT pk_sar_attachments
        PRIMARY KEY (sar_id, attachment_id),
    CONSTRAINT fk_sar_attachments_sar
        FOREIGN KEY (sar_id)
        REFERENCES suspicious_activities(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_sar_attachments_sar
ON sar_attachments(sar_id);

CREATE INDEX idx_sar_attachments_attachment
ON sar_attachments(attachment_id);

-- ============================================================================

-- Evidence supporting the SAR filing
CREATE TABLE sar_evidence (
    sar_id UUID NOT NULL,
    evidence_id UUID NOT NULL,
    evidence_type VARCHAR(50) NOT NULL, -- TRANSACTION, COMMUNICATION, DOCUMENT, etc.
    evidence_description VARCHAR(1000),
    evidence_source VARCHAR(255),
    collected_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_sar_evidence
        PRIMARY KEY (sar_id, evidence_id),
    CONSTRAINT fk_sar_evidence_sar
        FOREIGN KEY (sar_id)
        REFERENCES suspicious_activities(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_sar_evidence_sar
ON sar_evidence(sar_id);

CREATE INDEX idx_sar_evidence_type
ON sar_evidence(evidence_type);

-- ============================================================================

-- Risk factors identified in SAR
CREATE TABLE sar_risk_factors (
    sar_id UUID NOT NULL,
    risk_factor_type VARCHAR(50) NOT NULL,
    risk_factor_description VARCHAR(500),
    severity VARCHAR(20), -- LOW, MEDIUM, HIGH, CRITICAL

    CONSTRAINT pk_sar_risk_factors
        PRIMARY KEY (sar_id, risk_factor_type),
    CONSTRAINT fk_sar_risk_factors_sar
        FOREIGN KEY (sar_id)
        REFERENCES suspicious_activities(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_sar_risk_factors_sar
ON sar_risk_factors(sar_id);

CREATE INDEX idx_sar_risk_factors_type
ON sar_risk_factors(risk_factor_type);

-- ============================================================================
-- COMPLIANCE RULE COLLECTIONS
-- ============================================================================

-- Conditions that must be met for rule to fire
CREATE TABLE rule_conditions (
    rule_id UUID NOT NULL,
    condition_id UUID NOT NULL,
    condition_type VARCHAR(50) NOT NULL,
    condition_field VARCHAR(100),
    condition_operator VARCHAR(20), -- EQUALS, GREATER_THAN, LESS_THAN, CONTAINS, etc.
    condition_value VARCHAR(500),
    logical_operator VARCHAR(10), -- AND, OR
    sequence_order INTEGER,

    CONSTRAINT pk_rule_conditions
        PRIMARY KEY (rule_id, condition_id),
    CONSTRAINT fk_rule_conditions_rule
        FOREIGN KEY (rule_id)
        REFERENCES compliance_rules(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_rule_conditions_rule
ON rule_conditions(rule_id);

CREATE INDEX idx_rule_conditions_type
ON rule_conditions(condition_type);

-- ============================================================================

-- Customer types this rule applies to
CREATE TABLE rule_customer_types (
    rule_id UUID NOT NULL,
    customer_type VARCHAR(50) NOT NULL, -- INDIVIDUAL, BUSINESS, TRUST, etc.

    CONSTRAINT pk_rule_customer_types
        PRIMARY KEY (rule_id, customer_type),
    CONSTRAINT fk_rule_customer_types_rule
        FOREIGN KEY (rule_id)
        REFERENCES compliance_rules(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_rule_customer_types_rule
ON rule_customer_types(rule_id);

-- ============================================================================

-- Transaction types this rule applies to
CREATE TABLE rule_transaction_types (
    rule_id UUID NOT NULL,
    transaction_type VARCHAR(50) NOT NULL, -- TRANSFER, DEPOSIT, WITHDRAWAL, etc.

    CONSTRAINT pk_rule_transaction_types
        PRIMARY KEY (rule_id, transaction_type),
    CONSTRAINT fk_rule_transaction_types_rule
        FOREIGN KEY (rule_id)
        REFERENCES compliance_rules(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_rule_transaction_types_rule
ON rule_transaction_types(rule_id);

-- ============================================================================

-- Countries this rule applies to
CREATE TABLE rule_countries (
    rule_id UUID NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    rule_application VARCHAR(20), -- INCLUDE, EXCLUDE

    CONSTRAINT pk_rule_countries
        PRIMARY KEY (rule_id, country_code),
    CONSTRAINT fk_rule_countries_rule
        FOREIGN KEY (rule_id)
        REFERENCES compliance_rules(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_rule_countries_rule
ON rule_countries(rule_id);

CREATE INDEX idx_rule_countries_country
ON rule_countries(country_code);

-- ============================================================================

-- Secondary actions to take when rule fires
CREATE TABLE rule_secondary_actions (
    rule_id UUID NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_description VARCHAR(500),
    sequence_order INTEGER,

    CONSTRAINT pk_rule_secondary_actions
        PRIMARY KEY (rule_id, action_type, sequence_order),
    CONSTRAINT fk_rule_secondary_actions_rule
        FOREIGN KEY (rule_id)
        REFERENCES compliance_rules(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_rule_secondary_actions_rule
ON rule_secondary_actions(rule_id);

-- ============================================================================

-- Regulations this rule helps enforce
CREATE TABLE rule_regulations (
    rule_id UUID NOT NULL,
    regulation_code VARCHAR(50) NOT NULL, -- BSA, PATRIOT_ACT, OFAC, PCI_DSS, etc.
    regulation_section VARCHAR(100),

    CONSTRAINT pk_rule_regulations
        PRIMARY KEY (rule_id, regulation_code),
    CONSTRAINT fk_rule_regulations_rule
        FOREIGN KEY (rule_id)
        REFERENCES compliance_rules(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_rule_regulations_rule
ON rule_regulations(rule_id);

CREATE INDEX idx_rule_regulations_code
ON rule_regulations(regulation_code);

-- ============================================================================

-- Jurisdictions where this rule applies
CREATE TABLE rule_jurisdictions (
    rule_id UUID NOT NULL,
    jurisdiction_code VARCHAR(50) NOT NULL, -- US, EU, UK, etc.
    jurisdiction_level VARCHAR(20), -- FEDERAL, STATE, INTERNATIONAL

    CONSTRAINT pk_rule_jurisdictions
        PRIMARY KEY (rule_id, jurisdiction_code),
    CONSTRAINT fk_rule_jurisdictions_rule
        FOREIGN KEY (rule_id)
        REFERENCES compliance_rules(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_rule_jurisdictions_rule
ON rule_jurisdictions(rule_id);

CREATE INDEX idx_rule_jurisdictions_code
ON rule_jurisdictions(jurisdiction_code);

-- ============================================================================
-- VERIFICATION QUERIES (commented out - for manual testing)
-- ============================================================================
-- SELECT table_name FROM information_schema.tables
-- WHERE table_schema = 'public'
-- AND table_name LIKE '%customer_%'
-- OR table_name LIKE '%sar_%'
-- OR table_name LIKE '%rule_%'
-- ORDER BY table_name;

-- SELECT COUNT(*) AS constraint_count
-- FROM information_schema.table_constraints
-- WHERE table_schema = 'public'
-- AND constraint_type = 'FOREIGN KEY'
-- AND table_name IN (
--     'customer_high_risk_countries', 'customer_risk_factors', 'customer_transaction_patterns',
--     'sar_transaction_ids', 'sar_account_ids', 'sar_attachments',
--     'rule_conditions', 'rule_customer_types', 'rule_transaction_types'
-- );

-- ============================================================================
-- ROLLBACK SCRIPT (if needed for emergency rollback)
-- ============================================================================
-- DROP TABLE IF EXISTS rule_jurisdictions CASCADE;
-- DROP TABLE IF EXISTS rule_regulations CASCADE;
-- DROP TABLE IF EXISTS rule_secondary_actions CASCADE;
-- DROP TABLE IF EXISTS rule_countries CASCADE;
-- DROP TABLE IF EXISTS rule_transaction_types CASCADE;
-- DROP TABLE IF EXISTS rule_customer_types CASCADE;
-- DROP TABLE IF EXISTS rule_conditions CASCADE;
-- DROP TABLE IF EXISTS sar_risk_factors CASCADE;
-- DROP TABLE IF EXISTS sar_evidence CASCADE;
-- DROP TABLE IF EXISTS sar_attachments CASCADE;
-- DROP TABLE IF EXISTS sar_account_ids CASCADE;
-- DROP TABLE IF EXISTS sar_transaction_ids CASCADE;
-- DROP TABLE IF EXISTS customer_payment_methods CASCADE;
-- DROP TABLE IF EXISTS customer_preferred_countries CASCADE;
-- DROP TABLE IF EXISTS customer_transaction_patterns CASCADE;
-- DROP TABLE IF EXISTS customer_risk_factors CASCADE;
-- DROP TABLE IF EXISTS customer_high_risk_countries CASCADE;
