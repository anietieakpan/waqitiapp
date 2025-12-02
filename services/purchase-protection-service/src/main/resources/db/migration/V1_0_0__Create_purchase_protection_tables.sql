-- Purchase Protection Service Database Schema
-- Version: 1.0.0
-- Description: Create tables for purchase protection, claims, and disputes

-- Protection Policies Table
CREATE TABLE protection_policies (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL UNIQUE,
    buyer_id VARCHAR(36) NOT NULL,
    seller_id VARCHAR(36) NOT NULL,
    transaction_amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    coverage_type VARCHAR(20) NOT NULL,
    coverage_amount DECIMAL(19, 4) NOT NULL,
    protection_fee DECIMAL(19, 4) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    risk_score DOUBLE PRECISION,
    risk_level VARCHAR(10),
    seller_verified BOOLEAN DEFAULT FALSE,
    seller_rating DOUBLE PRECISION,
    requires_escrow BOOLEAN DEFAULT FALSE,
    escrow_id VARCHAR(36),
    escrow_status VARCHAR(30),
    item_description TEXT,
    item_category VARCHAR(50),
    fee_collected BOOLEAN DEFAULT FALSE,
    total_fees DECIMAL(19, 4) DEFAULT 0,
    has_active_claim BOOLEAN DEFAULT FALSE,
    last_claim_at TIMESTAMP,
    extended BOOLEAN DEFAULT FALSE,
    extension_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    INDEX idx_policies_transaction (transaction_id),
    INDEX idx_policies_buyer (buyer_id),
    INDEX idx_policies_seller (seller_id),
    INDEX idx_policies_status (status),
    INDEX idx_policies_end_date (end_date)
);

-- Purchase Evidence Table
CREATE TABLE purchase_evidence (
    policy_id VARCHAR(36) NOT NULL,
    evidence_url VARCHAR(500) NOT NULL,
    
    FOREIGN KEY (policy_id) REFERENCES protection_policies(id) ON DELETE CASCADE,
    INDEX idx_evidence_policy (policy_id)
);

-- Policy Metadata Table
CREATE TABLE policy_metadata (
    policy_id VARCHAR(36) NOT NULL,
    key VARCHAR(100) NOT NULL,
    value TEXT,
    
    PRIMARY KEY (policy_id, key),
    FOREIGN KEY (policy_id) REFERENCES protection_policies(id) ON DELETE CASCADE
);

-- Protection Claims Table
CREATE TABLE protection_claims (
    id VARCHAR(36) PRIMARY KEY,
    policy_id VARCHAR(36) NOT NULL,
    claim_type VARCHAR(30) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    description TEXT,
    claim_amount DECIMAL(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    filed_at TIMESTAMP NOT NULL,
    fraud_score DOUBLE PRECISION,
    fraud_check_result VARCHAR(20),
    investigation_reason TEXT,
    auto_approved BOOLEAN DEFAULT FALSE,
    approved_at TIMESTAMP,
    approved_amount DECIMAL(19, 4),
    approved_by VARCHAR(36),
    rejection_reason TEXT,
    rejected_at TIMESTAMP,
    rejected_by VARCHAR(36),
    paid_at TIMESTAMP,
    payment_reference VARCHAR(100),
    payment_failure_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    FOREIGN KEY (policy_id) REFERENCES protection_policies(id),
    INDEX idx_claims_policy (policy_id),
    INDEX idx_claims_status (status),
    INDEX idx_claims_type (claim_type),
    INDEX idx_claims_filed_at (filed_at)
);

-- Claim Evidence Table
CREATE TABLE claim_evidence (
    claim_id VARCHAR(36) NOT NULL,
    evidence_url VARCHAR(500) NOT NULL,
    
    FOREIGN KEY (claim_id) REFERENCES protection_claims(id) ON DELETE CASCADE,
    INDEX idx_claim_evidence (claim_id)
);

-- Claim Documents Table
CREATE TABLE claim_documents (
    id VARCHAR(36) PRIMARY KEY,
    claim_id VARCHAR(36) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    document_url VARCHAR(500) NOT NULL,
    description TEXT,
    uploaded_at TIMESTAMP NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    
    FOREIGN KEY (claim_id) REFERENCES protection_claims(id) ON DELETE CASCADE,
    INDEX idx_documents_claim (claim_id)
);

-- Claim Notes Table
CREATE TABLE claim_notes (
    id VARCHAR(36) PRIMARY KEY,
    claim_id VARCHAR(36) NOT NULL,
    note TEXT NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    is_internal BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (claim_id) REFERENCES protection_claims(id) ON DELETE CASCADE,
    INDEX idx_notes_claim (claim_id)
);

-- Claim Metadata Table
CREATE TABLE claim_metadata (
    claim_id VARCHAR(36) NOT NULL,
    key VARCHAR(100) NOT NULL,
    value TEXT,
    
    PRIMARY KEY (claim_id, key),
    FOREIGN KEY (claim_id) REFERENCES protection_claims(id) ON DELETE CASCADE
);

-- Disputes Table
CREATE TABLE disputes (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    buyer_id VARCHAR(36) NOT NULL,
    seller_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    initiated_by VARCHAR(36) NOT NULL,
    initiated_at TIMESTAMP NOT NULL,
    deadline_at TIMESTAMP NOT NULL,
    has_protection BOOLEAN DEFAULT FALSE,
    protection_policy_id VARCHAR(36),
    seller_response TEXT,
    seller_responded_at TIMESTAMP,
    funds_held BOOLEAN DEFAULT FALSE,
    mediator_id VARCHAR(36),
    mediation_started_at TIMESTAMP,
    
    -- Embedded DisputeResolution fields
    dispute_id VARCHAR(36),
    decision VARCHAR(30),
    refund_amount DECIMAL(19, 4),
    seller_payout DECIMAL(19, 4),
    resolution_reason TEXT,
    resolved_by VARCHAR(36),
    resolved_at TIMESTAMP,
    auto_resolved BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    
    INDEX idx_disputes_transaction (transaction_id),
    INDEX idx_disputes_buyer (buyer_id),
    INDEX idx_disputes_seller (seller_id),
    INDEX idx_disputes_status (status),
    INDEX idx_disputes_deadline (deadline_at)
);

-- Buyer Evidence Table
CREATE TABLE buyer_evidence (
    dispute_id VARCHAR(36) NOT NULL,
    evidence_url VARCHAR(500) NOT NULL,
    
    FOREIGN KEY (dispute_id) REFERENCES disputes(id) ON DELETE CASCADE,
    INDEX idx_buyer_evidence (dispute_id)
);

-- Seller Evidence Table
CREATE TABLE seller_evidence (
    dispute_id VARCHAR(36) NOT NULL,
    evidence_url VARCHAR(500) NOT NULL,
    
    FOREIGN KEY (dispute_id) REFERENCES disputes(id) ON DELETE CASCADE,
    INDEX idx_seller_evidence (dispute_id)
);

-- Dispute Messages Table
CREATE TABLE dispute_messages (
    id VARCHAR(36) PRIMARY KEY,
    dispute_id VARCHAR(36) NOT NULL,
    sender_id VARCHAR(36) NOT NULL,
    sender_type VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    is_system_message BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP NOT NULL,
    
    FOREIGN KEY (dispute_id) REFERENCES disputes(id) ON DELETE CASCADE,
    INDEX idx_messages_dispute (dispute_id),
    INDEX idx_messages_sender (sender_id)
);

-- Message Attachments Table
CREATE TABLE message_attachments (
    message_id VARCHAR(36) NOT NULL,
    attachment_url VARCHAR(500) NOT NULL,
    
    FOREIGN KEY (message_id) REFERENCES dispute_messages(id) ON DELETE CASCADE,
    INDEX idx_attachments_message (message_id)
);

-- Dispute Events Table
CREATE TABLE dispute_events (
    id VARCHAR(36) PRIMARY KEY,
    dispute_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    description VARCHAR(500) NOT NULL,
    performed_by VARCHAR(36),
    old_status VARCHAR(20),
    new_status VARCHAR(20),
    timestamp TIMESTAMP NOT NULL,
    
    FOREIGN KEY (dispute_id) REFERENCES disputes(id) ON DELETE CASCADE,
    INDEX idx_events_dispute (dispute_id),
    INDEX idx_events_timestamp (timestamp)
);

-- Event Details Table
CREATE TABLE event_details (
    event_id VARCHAR(36) NOT NULL,
    key VARCHAR(100) NOT NULL,
    value TEXT,
    
    PRIMARY KEY (event_id, key),
    FOREIGN KEY (event_id) REFERENCES dispute_events(id) ON DELETE CASCADE
);

-- Dispute Metadata Table
CREATE TABLE dispute_metadata (
    dispute_id VARCHAR(36) NOT NULL,
    key VARCHAR(100) NOT NULL,
    value TEXT,
    
    PRIMARY KEY (dispute_id, key),
    FOREIGN KEY (dispute_id) REFERENCES disputes(id) ON DELETE CASCADE
);

-- Seller Profiles Table (for seller verification)
CREATE TABLE seller_profiles (
    id VARCHAR(36) PRIMARY KEY,
    seller_id VARCHAR(36) NOT NULL UNIQUE,
    verified BOOLEAN DEFAULT FALSE,
    verification_date TIMESTAMP,
    rating DECIMAL(3, 2),
    total_transactions INTEGER DEFAULT 0,
    successful_transactions INTEGER DEFAULT 0,
    dispute_rate DECIMAL(5, 4),
    trust_score DECIMAL(5, 2),
    account_age_days INTEGER,
    business_name VARCHAR(255),
    business_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_seller_profiles_seller (seller_id),
    INDEX idx_seller_verified (verified)
);

-- Risk Assessment Table
CREATE TABLE risk_assessments (
    id VARCHAR(36) PRIMARY KEY,
    policy_id VARCHAR(36),
    transaction_id VARCHAR(36),
    score DOUBLE PRECISION NOT NULL,
    level VARCHAR(10) NOT NULL,
    factors JSON,
    assessed_at TIMESTAMP NOT NULL,
    
    INDEX idx_risk_policy (policy_id),
    INDEX idx_risk_transaction (transaction_id)
);

-- Escrow Transactions Table
CREATE TABLE escrow_transactions (
    id VARCHAR(36) PRIMARY KEY,
    policy_id VARCHAR(36) NOT NULL,
    transaction_id VARCHAR(36) NOT NULL,
    buyer_id VARCHAR(36) NOT NULL,
    seller_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    held_at TIMESTAMP,
    released_at TIMESTAMP,
    released_to VARCHAR(10),
    release_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (policy_id) REFERENCES protection_policies(id),
    INDEX idx_escrow_policy (policy_id),
    INDEX idx_escrow_status (status)
);

-- Protection Statistics Table (for reporting)
CREATE TABLE protection_statistics (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    total_policies INTEGER DEFAULT 0,
    active_policies INTEGER DEFAULT 0,
    total_protected_amount DECIMAL(19, 4) DEFAULT 0,
    total_claims INTEGER DEFAULT 0,
    successful_claims INTEGER DEFAULT 0,
    total_claims_paid DECIMAL(19, 4) DEFAULT 0,
    total_disputes INTEGER DEFAULT 0,
    disputes_won INTEGER DEFAULT 0,
    last_calculated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_user_stats (user_id),
    INDEX idx_stats_user (user_id)
);

-- Create update trigger for updated_at columns
DELIMITER $$
CREATE TRIGGER update_policies_updated_at BEFORE UPDATE ON protection_policies
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END$$

CREATE TRIGGER update_claims_updated_at BEFORE UPDATE ON protection_claims
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END$$

CREATE TRIGGER update_disputes_updated_at BEFORE UPDATE ON disputes
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END$$
DELIMITER ;