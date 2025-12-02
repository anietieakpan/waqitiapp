-- NFC Payment Tables Migration
-- Creates tables for NFC transactions, sessions, devices, and contacts

-- NFC Transactions Table
CREATE TABLE nfc_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id VARCHAR(64) UNIQUE NOT NULL,
    payment_id VARCHAR(64),
    transfer_id VARCHAR(64),
    transaction_type VARCHAR(32) NOT NULL COMMENT 'MERCHANT_PAYMENT, P2P_TRANSFER, CONTACT_EXCHANGE',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED',
    amount DECIMAL(19,2),
    currency VARCHAR(3),
    processing_fee DECIMAL(19,2),
    net_amount DECIMAL(19,2),
    
    -- Participant information
    sender_id VARCHAR(64),
    recipient_id VARCHAR(64),
    merchant_id VARCHAR(64),
    customer_id VARCHAR(64),
    
    -- NFC specific fields
    nfc_session_id VARCHAR(64),
    nfc_protocol_version VARCHAR(32),
    device_id VARCHAR(128),
    sender_device_id VARCHAR(128),
    recipient_device_id VARCHAR(128),
    secure_element_used BOOLEAN DEFAULT FALSE,
    
    -- Security and fraud detection
    signature VARCHAR(1024),
    security_level VARCHAR(32),
    fraud_check_passed BOOLEAN DEFAULT FALSE,
    risk_score DECIMAL(5,2),
    device_fingerprint VARCHAR(512),
    
    -- Location data
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    location_accuracy VARCHAR(10),
    
    -- Processing details
    processing_method VARCHAR(64),
    processing_time_ms BIGINT,
    authorization_code VARCHAR(64),
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Settlement information
    estimated_settlement TIMESTAMP NULL,
    settlement_method VARCHAR(64),
    settlement_status VARCHAR(32),
    
    -- Error information
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    
    -- Additional information
    description VARCHAR(255),
    message VARCHAR(255),
    order_id VARCHAR(64),
    
    -- Receipt and documentation
    receipt_url VARCHAR(512),
    invoice_url VARCHAR(512),
    confirmation_code VARCHAR(64),
    
    -- Blockchain/audit trail
    blockchain_tx_hash VARCHAR(128),
    audit_trail_id VARCHAR(64),
    
    -- Client information
    client_ip VARCHAR(45),
    user_agent VARCHAR(512),
    
    -- Additional metadata
    metadata TEXT,
    
    -- Indexes
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_payment_id (payment_id),
    INDEX idx_transfer_id (transfer_id),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_status (status),
    INDEX idx_customer_id (customer_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_sender_id (sender_id),
    INDEX idx_recipient_id (recipient_id),
    INDEX idx_nfc_session_id (nfc_session_id),
    INDEX idx_device_id (device_id),
    INDEX idx_created_at (created_at),
    INDEX idx_processed_at (processed_at),
    INDEX idx_risk_score (risk_score),
    INDEX idx_fraud_check (fraud_check_passed),
    INDEX idx_amount (amount),
    INDEX idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NFC Sessions Table
CREATE TABLE nfc_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) UNIQUE NOT NULL,
    session_token VARCHAR(128) UNIQUE NOT NULL,
    session_type VARCHAR(32) NOT NULL COMMENT 'MERCHANT, P2P, CONTACT_EXCHANGE',
    status VARCHAR(32) NOT NULL COMMENT 'ACTIVE, EXPIRED, CANCELLED, COMPLETED',
    user_id VARCHAR(64),
    merchant_id VARCHAR(64),
    device_id VARCHAR(128) NOT NULL,
    
    -- Session configuration
    amount DECIMAL(19,2),
    currency VARCHAR(3),
    description VARCHAR(255),
    order_id VARCHAR(64),
    
    -- NFC protocol information
    nfc_protocol_version VARCHAR(32),
    encryption_algorithm VARCHAR(32),
    
    -- Security settings
    security_level VARCHAR(32) COMMENT 'LOW, MEDIUM, HIGH',
    require_biometric BOOLEAN DEFAULT FALSE,
    require_pin BOOLEAN DEFAULT FALSE,
    
    -- Session limits
    max_transaction_amount DECIMAL(19,2),
    max_transaction_count INTEGER,
    remaining_transactions INTEGER,
    
    -- Cryptographic keys (stored as byte arrays)
    public_key BLOB,
    private_key BLOB,
    
    -- Location data
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    location_accuracy VARCHAR(10),
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- WebSocket information for real-time updates
    websocket_url VARCHAR(512),
    websocket_token VARCHAR(128),
    
    -- QR code for backup payment method
    qr_code TEXT,
    deep_link_url VARCHAR(512),
    
    -- Usage statistics
    transaction_count INTEGER DEFAULT 0,
    total_amount_processed DECIMAL(19,2) DEFAULT 0.00,
    last_transaction_id VARCHAR(64),
    
    -- Additional metadata
    metadata TEXT,
    
    -- Indexes
    INDEX idx_session_id (session_id),
    INDEX idx_session_token (session_token),
    INDEX idx_session_type (session_type),
    INDEX idx_status (status),
    INDEX idx_user_id (user_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_device_id (device_id),
    INDEX idx_created_at (created_at),
    INDEX idx_expires_at (expires_at),
    INDEX idx_last_activity (last_activity_at),
    INDEX idx_active_sessions (status, expires_at),
    INDEX idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NFC Devices Table
CREATE TABLE nfc_devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(128) UNIQUE NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    device_name VARCHAR(100),
    device_type VARCHAR(32) COMMENT 'PHONE, TABLET, WEARABLE',
    platform VARCHAR(16) COMMENT 'ANDROID, IOS',
    platform_version VARCHAR(32),
    
    -- NFC capabilities
    nfc_supported BOOLEAN DEFAULT FALSE,
    nfc_enabled BOOLEAN DEFAULT FALSE,
    secure_element_available BOOLEAN DEFAULT FALSE,
    secure_element_enabled BOOLEAN DEFAULT FALSE,
    hce_supported BOOLEAN DEFAULT FALSE,
    reader_mode_supported BOOLEAN DEFAULT FALSE,
    
    -- Device security
    device_fingerprint VARCHAR(512),
    is_rooted_jailbroken BOOLEAN DEFAULT FALSE,
    hardware_backed_keystore BOOLEAN DEFAULT FALSE,
    biometric_available BOOLEAN DEFAULT FALSE,
    
    -- Device status
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, BLOCKED',
    is_trusted BOOLEAN DEFAULT FALSE,
    trust_score DECIMAL(5,2) DEFAULT 0.00,
    
    -- Registration information
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP,
    last_location_lat DECIMAL(10,8),
    last_location_lng DECIMAL(11,8),
    
    -- Usage statistics
    total_transactions INTEGER DEFAULT 0,
    successful_transactions INTEGER DEFAULT 0,
    failed_transactions INTEGER DEFAULT 0,
    total_amount DECIMAL(19,2) DEFAULT 0.00,
    
    -- Additional metadata
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_device_id (device_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_is_trusted (is_trusted),
    INDEX idx_platform (platform),
    INDEX idx_nfc_supported (nfc_supported),
    INDEX idx_secure_element (secure_element_available),
    INDEX idx_trust_score (trust_score),
    INDEX idx_last_seen (last_seen_at),
    INDEX idx_user_device (user_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NFC Contacts Table
CREATE TABLE nfc_contacts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    connection_id VARCHAR(64) UNIQUE NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    contact_user_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(100),
    contact_display_name VARCHAR(100),
    
    -- Contact information
    avatar_url VARCHAR(255),
    contact_avatar_url VARCHAR(255),
    phone_number VARCHAR(20),
    email VARCHAR(100),
    company_name VARCHAR(100),
    job_title VARCHAR(100),
    bio TEXT,
    
    -- Cryptographic information
    public_key TEXT,
    contact_public_key TEXT,
    
    -- Connection details
    connection_type VARCHAR(32) DEFAULT 'MUTUAL' COMMENT 'MUTUAL, ONE_WAY',
    connection_status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT 'ACTIVE, BLOCKED, PENDING',
    is_favorite BOOLEAN DEFAULT FALSE,
    
    -- Sharing preferences
    share_phone_number BOOLEAN DEFAULT FALSE,
    share_email BOOLEAN DEFAULT FALSE,
    share_address BOOLEAN DEFAULT FALSE,
    share_profile_picture BOOLEAN DEFAULT FALSE,
    
    -- Payment capabilities
    allow_payment_requests BOOLEAN DEFAULT FALSE,
    allow_direct_payments BOOLEAN DEFAULT FALSE,
    
    -- Social platform integration
    social_platform_username VARCHAR(100),
    social_platform_type VARCHAR(32),
    
    -- Exchange information
    exchanged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exchange_location_lat DECIMAL(10,8),
    exchange_location_lng DECIMAL(11,8),
    nfc_session_id VARCHAR(64),
    
    -- Device information
    device_id VARCHAR(128),
    contact_device_id VARCHAR(128),
    
    -- Security information
    security_level VARCHAR(32),
    mutual_verification BOOLEAN DEFAULT FALSE,
    
    -- Interaction statistics
    total_interactions INTEGER DEFAULT 0,
    last_interaction_at TIMESTAMP,
    total_payments_sent DECIMAL(19,2) DEFAULT 0.00,
    total_payments_received DECIMAL(19,2) DEFAULT 0.00,
    
    -- Trust metrics
    trust_score DECIMAL(5,2) DEFAULT 50.00,
    is_verified BOOLEAN DEFAULT FALSE,
    mutual_connections_count INTEGER DEFAULT 0,
    
    -- Additional metadata
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE KEY unique_user_contact (user_id, contact_user_id),
    
    -- Indexes
    INDEX idx_connection_id (connection_id),
    INDEX idx_user_id (user_id),
    INDEX idx_contact_user_id (contact_user_id),
    INDEX idx_connection_status (connection_status),
    INDEX idx_connection_type (connection_type),
    INDEX idx_is_favorite (is_favorite),
    INDEX idx_exchanged_at (exchanged_at),
    INDEX idx_nfc_session_id (nfc_session_id),
    INDEX idx_trust_score (trust_score),
    INDEX idx_mutual_verification (mutual_verification),
    INDEX idx_payment_capabilities (allow_payment_requests, allow_direct_payments),
    INDEX idx_social_platform (social_platform_type, social_platform_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NFC Analytics Table (for tracking NFC usage patterns)
CREATE TABLE nfc_analytics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_type VARCHAR(64) NOT NULL COMMENT 'SESSION_START, SESSION_END, PAYMENT_ATTEMPT, etc.',
    user_id VARCHAR(64),
    device_id VARCHAR(128),
    session_id VARCHAR(64),
    transaction_id VARCHAR(64),
    
    -- Event details
    event_data JSON,
    event_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Location data
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    
    -- Performance metrics
    processing_time_ms BIGINT,
    success BOOLEAN,
    error_code VARCHAR(64),
    
    -- Additional context
    user_agent VARCHAR(512),
    client_ip VARCHAR(45),
    
    -- Indexes
    INDEX idx_event_type (event_type),
    INDEX idx_user_id (user_id),
    INDEX idx_device_id (device_id),
    INDEX idx_session_id (session_id),
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_event_timestamp (event_timestamp),
    INDEX idx_success (success),
    INDEX idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add foreign key constraints (optional, depending on your architecture)
-- ALTER TABLE nfc_transactions ADD CONSTRAINT fk_nfc_transaction_session 
--     FOREIGN KEY (nfc_session_id) REFERENCES nfc_sessions(session_id);

-- Create additional indexes for performance
CREATE INDEX idx_nfc_transactions_user_lookup ON nfc_transactions (customer_id, merchant_id, sender_id, recipient_id, created_at);
CREATE INDEX idx_nfc_transactions_status_date ON nfc_transactions (status, created_at);
CREATE INDEX idx_nfc_transactions_fraud ON nfc_transactions (fraud_check_passed, risk_score);
CREATE INDEX idx_nfc_sessions_active ON nfc_sessions (status, expires_at, created_at);
CREATE INDEX idx_nfc_devices_user_status ON nfc_devices (user_id, status, is_trusted);
CREATE INDEX idx_nfc_contacts_user_status ON nfc_contacts (user_id, connection_status, is_favorite);

-- Create views for common queries
CREATE VIEW v_active_nfc_sessions AS
SELECT 
    s.*,
    TIMESTAMPDIFF(SECOND, NOW(), s.expires_at) as seconds_remaining,
    CASE WHEN s.expires_at > NOW() THEN 1 ELSE 0 END as is_active
FROM nfc_sessions s
WHERE s.status = 'ACTIVE';

CREATE VIEW v_nfc_transaction_summary AS
SELECT 
    DATE(created_at) as transaction_date,
    transaction_type,
    status,
    COUNT(*) as transaction_count,
    SUM(amount) as total_amount,
    AVG(processing_time_ms) as avg_processing_time,
    SUM(CASE WHEN fraud_check_passed = 1 THEN 1 ELSE 0 END) as passed_fraud_check,
    SUM(CASE WHEN secure_element_used = 1 THEN 1 ELSE 0 END) as secure_element_used
FROM nfc_transactions
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
GROUP BY DATE(created_at), transaction_type, status;

CREATE VIEW v_user_nfc_stats AS
SELECT 
    COALESCE(t.customer_id, t.merchant_id, t.sender_id, t.recipient_id) as user_id,
    COUNT(*) as total_transactions,
    SUM(CASE WHEN t.status = 'SUCCESS' THEN 1 ELSE 0 END) as successful_transactions,
    SUM(CASE WHEN t.status = 'FAILED' THEN 1 ELSE 0 END) as failed_transactions,
    SUM(CASE WHEN t.status = 'SUCCESS' THEN t.amount ELSE 0 END) as total_amount,
    AVG(t.processing_time_ms) as avg_processing_time,
    MAX(t.created_at) as last_transaction_at
FROM nfc_transactions t
GROUP BY COALESCE(t.customer_id, t.merchant_id, t.sender_id, t.recipient_id);

-- Create triggers for audit logging
DELIMITER //

CREATE TRIGGER nfc_transaction_audit 
AFTER INSERT ON nfc_transactions
FOR EACH ROW
BEGIN
    INSERT INTO nfc_analytics (
        event_type, 
        user_id, 
        device_id, 
        session_id, 
        transaction_id,
        event_data,
        success
    ) VALUES (
        'TRANSACTION_CREATED',
        COALESCE(NEW.customer_id, NEW.merchant_id, NEW.sender_id, NEW.recipient_id),
        NEW.device_id,
        NEW.nfc_session_id,
        NEW.transaction_id,
        JSON_OBJECT(
            'transaction_type', NEW.transaction_type,
            'amount', NEW.amount,
            'currency', NEW.currency,
            'security_level', NEW.security_level
        ),
        CASE WHEN NEW.status = 'SUCCESS' THEN 1 ELSE 0 END
    );
END//

CREATE TRIGGER nfc_session_audit 
AFTER INSERT ON nfc_sessions
FOR EACH ROW
BEGIN
    INSERT INTO nfc_analytics (
        event_type, 
        user_id, 
        device_id, 
        session_id,
        event_data
    ) VALUES (
        'SESSION_CREATED',
        COALESCE(NEW.user_id, NEW.merchant_id),
        NEW.device_id,
        NEW.session_id,
        JSON_OBJECT(
            'session_type', NEW.session_type,
            'security_level', NEW.security_level,
            'expires_at', NEW.expires_at
        )
    );
END//

DELIMITER ;