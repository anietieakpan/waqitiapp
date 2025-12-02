-- ==================================================================================
-- Crypto Transaction Patterns Table - For Fraud Detection & AML
-- ==================================================================================

CREATE TABLE IF NOT EXISTS crypto_transaction_patterns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL COMMENT 'User performing the crypto transaction',
    transaction_id VARCHAR(100) NOT NULL UNIQUE COMMENT 'Blockchain transaction hash',
    crypto_currency VARCHAR(10) NOT NULL COMMENT 'BTC, ETH, USDT, etc.',
    amount_crypto DECIMAL(30, 10) NOT NULL COMMENT 'Amount in cryptocurrency',
    amount_usd DECIMAL(20, 2) COMMENT 'USD equivalent at transaction time',
    from_address VARCHAR(255) COMMENT 'Source wallet address',
    to_address VARCHAR(255) COMMENT 'Destination wallet address',
    transaction_timestamp TIMESTAMP NOT NULL COMMENT 'When transaction occurred',
    transaction_type VARCHAR(50) COMMENT 'DEPOSIT, WITHDRAWAL, TRADE, SWAP',
    device_fingerprint VARCHAR(255) COMMENT 'Device ID for fraud detection',
    ip_address VARCHAR(45) COMMENT 'IP address of transaction origin',
    country_code VARCHAR(3) COMMENT 'ISO 3166-1 alpha-3',
    risk_score DECIMAL(5, 2) COMMENT 'ML-calculated risk score 0-100',
    is_suspicious BOOLEAN DEFAULT FALSE COMMENT 'Flagged by fraud detection',
    flagged_reason VARCHAR(500) COMMENT 'Why flagged as suspicious',
    reviewed_by VARCHAR(36) COMMENT 'Admin user ID who reviewed',
    review_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_timestamp (transaction_timestamp DESC),
    INDEX idx_user_timestamp (user_id, transaction_timestamp DESC),
    INDEX idx_to_address (to_address),
    INDEX idx_from_address (from_address),
    INDEX idx_device (device_fingerprint),
    INDEX idx_risk_score (risk_score DESC),
    INDEX idx_country (country_code),
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_suspicious (is_suspicious, review_status),
    INDEX idx_crypto_currency (crypto_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Crypto transaction patterns for fraud detection and AML monitoring';
