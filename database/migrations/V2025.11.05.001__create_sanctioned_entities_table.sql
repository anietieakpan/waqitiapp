-- ==================================================================================
-- CRITICAL MIGRATION: Sanctioned Entities for OFAC/AML Compliance
-- ==================================================================================
-- This migration creates tables for sanctions screening against:
-- - OFAC SDN (Specially Designated Nationals)
-- - UN Security Council Consolidated List
-- - EU Sanctions List
-- - UK HM Treasury Consolidated List
--
-- REGULATORY REQUIREMENT: Required for BSA/AML compliance
-- ==================================================================================

-- Main sanctioned entities table
CREATE TABLE IF NOT EXISTS sanctioned_entities (
    id VARCHAR(255) PRIMARY KEY COMMENT 'Unique identifier from sanctions list',
    entity_name VARCHAR(500) NOT NULL COMMENT 'Full legal name of sanctioned entity',
    entity_type VARCHAR(50) NOT NULL COMMENT 'INDIVIDUAL, ORGANIZATION, VESSEL, AIRCRAFT',
    sanctions_list VARCHAR(100) NOT NULL COMMENT 'Source list: OFAC_SDN, UN_1267, EU_SANCTIONS, etc.',
    country VARCHAR(3) COMMENT 'ISO 3166-1 alpha-3 country code',
    added_date TIMESTAMP NOT NULL COMMENT 'Date added to sanctions list',
    removed_date TIMESTAMP NULL COMMENT 'Date removed from sanctions list (if deactivated)',
    is_active BOOLEAN DEFAULT TRUE NOT NULL COMMENT 'Currently sanctioned',
    description TEXT COMMENT 'Additional information about sanctions',
    reference_url VARCHAR(1000) COMMENT 'Official source URL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_entity_name (entity_name),
    INDEX idx_entity_name_lower ((LOWER(entity_name))),
    INDEX idx_sanctions_list (sanctions_list),
    INDEX idx_country (country),
    INDEX idx_is_active (is_active),
    INDEX idx_added_date (added_date),
    INDEX idx_entity_type (entity_type),
    INDEX idx_composite_search (entity_type, sanctions_list, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Sanctioned entities from OFAC, UN, EU lists - CRITICAL for AML compliance';

-- Aliases table (aka "Also Known As")
CREATE TABLE IF NOT EXISTS sanctioned_entity_aliases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sanctioned_entity_id VARCHAR(255) NOT NULL,
    alias VARCHAR(500) NOT NULL COMMENT 'Alternative name/spelling',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (sanctioned_entity_id) REFERENCES sanctioned_entities(id) ON DELETE CASCADE,
    INDEX idx_alias (alias),
    INDEX idx_alias_lower ((LOWER(alias))),
    INDEX idx_entity_id (sanctioned_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Known aliases for sanctioned entities';

-- Associates table (known associates/related parties)
CREATE TABLE IF NOT EXISTS sanctioned_entity_associates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sanctioned_entity_id VARCHAR(255) NOT NULL,
    associate_name VARCHAR(500) NOT NULL,
    relationship_type VARCHAR(100) COMMENT 'FAMILY_MEMBER, BUSINESS_PARTNER, AFFILIATE, etc.',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (sanctioned_entity_id) REFERENCES sanctioned_entities(id) ON DELETE CASCADE,
    INDEX idx_associate_name (associate_name),
    INDEX idx_associate_name_lower ((LOWER(associate_name))),
    INDEX idx_entity_id (sanctioned_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Known associates of sanctioned entities';

-- Create audit table for sanctions screening checks
CREATE TABLE IF NOT EXISTS sanctions_screening_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36),
    entity_name VARCHAR(500) NOT NULL,
    screening_result ENUM('CLEAR', 'MATCH', 'POTENTIAL_MATCH') NOT NULL,
    matched_entity_id VARCHAR(255),
    match_score DECIMAL(5, 2) COMMENT 'Fuzzy match confidence score',
    screening_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    screened_by VARCHAR(100) COMMENT 'System or user ID that initiated screening',
    
    INDEX idx_user_id (user_id),
    INDEX idx_screening_result (screening_result),
    INDEX idx_timestamp (screening_timestamp DESC),
    FOREIGN KEY (matched_entity_id) REFERENCES sanctioned_entities(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit trail of all sanctions screening checks - REQUIRED for compliance';
