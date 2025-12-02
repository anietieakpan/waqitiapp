-- ============================================================================
-- V14 Migration: Create Sanctions List Storage Tables
-- ============================================================================
--
-- CRITICAL: Database schema for storing and versioning sanctions lists from:
-- - OFAC (Office of Foreign Assets Control) - US Treasury
-- - EU (European Union Consolidated Sanctions List)
-- - UN (United Nations Security Council Consolidated List)
--
-- REGULATORY REQUIREMENT:
-- Real-time sanctions screening requires up-to-date sanctions data.
-- Lists must be updated daily and versioned for audit compliance.
--
-- FEATURES:
-- - Multi-source sanctions list storage
-- - Version tracking for audit trails
-- - Alias/variant name support
-- - Historical data retention
-- - Efficient lookup indexes
-- - Diff tracking for changes
--
-- Author: Waqiti Engineering Team
-- Date: 2025-11-19
-- JIRA: COMP-1236 (OFAC Auto-Update Implementation)
-- ============================================================================

-- ============================================================================
-- SANCTIONS LIST METADATA
-- ============================================================================

CREATE TABLE sanctions_list_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- List identification
    list_source VARCHAR(20) NOT NULL, -- OFAC, EU, UN
    list_name VARCHAR(100) NOT NULL,
    list_type VARCHAR(50) NOT NULL, -- SDN, CONSOLIDATED, TARGETED, etc.

    -- Version tracking
    version_id VARCHAR(50) NOT NULL,
    version_date TIMESTAMP NOT NULL,
    download_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- List statistics
    total_entries INTEGER NOT NULL DEFAULT 0,
    new_entries INTEGER DEFAULT 0,
    modified_entries INTEGER DEFAULT 0,
    removed_entries INTEGER DEFAULT 0,

    -- Source information
    source_url VARCHAR(500),
    source_file_name VARCHAR(255),
    source_file_size_bytes BIGINT,
    source_file_hash VARCHAR(64), -- SHA-256 hash

    -- Processing status
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED
    processing_started_at TIMESTAMP,
    processing_completed_at TIMESTAMP,
    processing_duration_ms INTEGER,
    processing_error TEXT,

    -- Activation
    is_active BOOLEAN NOT NULL DEFAULT false,
    activated_at TIMESTAMP,
    superseded_by UUID,
    superseded_at TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) DEFAULT 'SYSTEM',

    CONSTRAINT uk_sanctions_list_version
        UNIQUE (list_source, version_id),
    CONSTRAINT fk_superseded_by
        FOREIGN KEY (superseded_by)
        REFERENCES sanctions_list_metadata(id)
);

-- Indexes for sanctions_list_metadata
CREATE INDEX idx_sanctions_list_source ON sanctions_list_metadata(list_source);
CREATE INDEX idx_sanctions_list_active ON sanctions_list_metadata(is_active, list_source);
CREATE INDEX idx_sanctions_list_version_date ON sanctions_list_metadata(version_date DESC);
CREATE INDEX idx_sanctions_list_download ON sanctions_list_metadata(download_timestamp DESC);
CREATE INDEX idx_sanctions_list_status ON sanctions_list_metadata(processing_status);

-- Comments
COMMENT ON TABLE sanctions_list_metadata IS 'Tracks versions and metadata for all sanctions lists (OFAC, EU, UN)';
COMMENT ON COLUMN sanctions_list_metadata.version_id IS 'Unique version identifier from the list source';
COMMENT ON COLUMN sanctions_list_metadata.is_active IS 'Only one version per source should be active';
COMMENT ON COLUMN sanctions_list_metadata.source_file_hash IS 'SHA-256 hash for integrity verification';

-- ============================================================================
-- SANCTIONED ENTITIES (Main Table)
-- ============================================================================

CREATE TABLE sanctioned_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- List metadata reference
    list_metadata_id UUID NOT NULL,

    -- Entity identification
    source_id VARCHAR(50) NOT NULL, -- Original ID from source list
    entity_type VARCHAR(20) NOT NULL, -- INDIVIDUAL, ENTITY, VESSEL, AIRCRAFT

    -- Primary information
    primary_name VARCHAR(500) NOT NULL,
    name_normalized VARCHAR(500) NOT NULL, -- Normalized for matching

    -- Individual-specific fields
    title VARCHAR(100),
    first_name VARCHAR(100),
    middle_name VARCHAR(100),
    last_name VARCHAR(100),
    maiden_name VARCHAR(100),
    gender VARCHAR(10),
    date_of_birth DATE,
    place_of_birth VARCHAR(255),

    -- Entity-specific fields
    organization_type VARCHAR(100),

    -- Location information
    nationality VARCHAR(3), -- ISO 3166-1 alpha-3
    country_of_residence VARCHAR(3),
    address TEXT,
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(3),

    -- Identification documents
    passport_number VARCHAR(50),
    passport_country VARCHAR(3),
    national_id_number VARCHAR(50),
    national_id_country VARCHAR(3),
    tax_id_number VARCHAR(50),
    ssn VARCHAR(20),

    -- Vessel/Aircraft specific
    vessel_call_sign VARCHAR(50),
    vessel_type VARCHAR(100),
    vessel_tonnage INTEGER,
    vessel_flag VARCHAR(3),
    vessel_owner VARCHAR(255),
    aircraft_tail_number VARCHAR(50),
    aircraft_manufacturer VARCHAR(100),
    aircraft_model VARCHAR(100),

    -- Sanctions details
    program_name VARCHAR(200),
    sanctions_type VARCHAR(100), -- BLOCKING, SDN, ASSET_FREEZE, etc.
    listing_date DATE,
    effective_date DATE,
    expiry_date DATE,
    legal_basis TEXT,
    remarks TEXT,

    -- Risk scoring
    match_score_threshold DECIMAL(5,2) DEFAULT 85.00, -- Fuzzy match threshold
    risk_level VARCHAR(20) DEFAULT 'HIGH', -- HIGH, CRITICAL

    -- Status
    is_active BOOLEAN NOT NULL DEFAULT true,
    removed_date DATE,
    removal_reason TEXT,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    last_verified_at TIMESTAMP,

    CONSTRAINT fk_sanctioned_entity_metadata
        FOREIGN KEY (list_metadata_id)
        REFERENCES sanctions_list_metadata(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_sanctioned_entity_source
        UNIQUE (list_metadata_id, source_id)
);

-- Indexes for sanctioned_entities
CREATE INDEX idx_sanctioned_entities_metadata ON sanctioned_entities(list_metadata_id);
CREATE INDEX idx_sanctioned_entities_active ON sanctioned_entities(is_active);
CREATE INDEX idx_sanctioned_entities_type ON sanctioned_entities(entity_type);
CREATE INDEX idx_sanctioned_entities_source_id ON sanctioned_entities(source_id);

-- Full-text search index on names
CREATE INDEX idx_sanctioned_entities_primary_name ON sanctioned_entities USING gin(to_tsvector('english', primary_name));
CREATE INDEX idx_sanctioned_entities_normalized ON sanctioned_entities(name_normalized);

-- Location indexes
CREATE INDEX idx_sanctioned_entities_nationality ON sanctioned_entities(nationality);
CREATE INDEX idx_sanctioned_entities_country ON sanctioned_entities(country);

-- Date indexes
CREATE INDEX idx_sanctioned_entities_dob ON sanctioned_entities(date_of_birth);
CREATE INDEX idx_sanctioned_entities_listing ON sanctioned_entities(listing_date);

-- Document indexes
CREATE INDEX idx_sanctioned_entities_passport ON sanctioned_entities(passport_number) WHERE passport_number IS NOT NULL;
CREATE INDEX idx_sanctioned_entities_national_id ON sanctioned_entities(national_id_number) WHERE national_id_number IS NOT NULL;
CREATE INDEX idx_sanctioned_entities_vessel ON sanctioned_entities(vessel_call_sign) WHERE vessel_call_sign IS NOT NULL;

-- Comments
COMMENT ON TABLE sanctioned_entities IS 'Sanctioned individuals, entities, vessels, and aircraft from OFAC/EU/UN lists';
COMMENT ON COLUMN sanctioned_entities.name_normalized IS 'Lowercase, diacritics removed, for fuzzy matching';
COMMENT ON COLUMN sanctioned_entities.match_score_threshold IS 'Minimum Jaro-Winkler score for positive match (default 85%)';

-- ============================================================================
-- ENTITY ALIASES (Name Variants)
-- ============================================================================

CREATE TABLE sanctioned_entity_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sanctioned_entity_id UUID NOT NULL,

    -- Alias information
    alias_type VARCHAR(50) NOT NULL, -- AKA, FKA, NICKNAME, WEAK_AKA, etc.
    alias_name VARCHAR(500) NOT NULL,
    alias_name_normalized VARCHAR(500) NOT NULL,

    -- Alias quality
    alias_quality VARCHAR(20) DEFAULT 'STRONG', -- STRONG, WEAK, LOW

    -- Metadata
    is_primary BOOLEAN DEFAULT false,
    language_code VARCHAR(3),
    script_type VARCHAR(20), -- LATIN, CYRILLIC, ARABIC, etc.

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alias_entity
        FOREIGN KEY (sanctioned_entity_id)
        REFERENCES sanctioned_entities(id)
        ON DELETE CASCADE
);

-- Indexes for aliases
CREATE INDEX idx_aliases_entity ON sanctioned_entity_aliases(sanctioned_entity_id);
CREATE INDEX idx_aliases_name ON sanctioned_entity_aliases USING gin(to_tsvector('english', alias_name));
CREATE INDEX idx_aliases_normalized ON sanctioned_entity_aliases(alias_name_normalized);
CREATE INDEX idx_aliases_type ON sanctioned_entity_aliases(alias_type);
CREATE INDEX idx_aliases_quality ON sanctioned_entity_aliases(alias_quality);

-- Comments
COMMENT ON TABLE sanctioned_entity_aliases IS 'Alternative names, aliases, and variants for sanctioned entities';
COMMENT ON COLUMN sanctioned_entity_aliases.alias_quality IS 'Reliability of the alias (STRONG = confirmed, WEAK = possible)';

-- ============================================================================
-- SANCTIONS PROGRAMS
-- ============================================================================

CREATE TABLE sanctions_programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Program identification
    program_code VARCHAR(50) NOT NULL UNIQUE,
    program_name VARCHAR(200) NOT NULL,
    program_type VARCHAR(100),

    -- Jurisdiction
    jurisdiction VARCHAR(10) NOT NULL, -- US, EU, UN
    issuing_authority VARCHAR(200),

    -- Legal basis
    legal_reference VARCHAR(500),
    executive_order VARCHAR(100),
    regulation_reference VARCHAR(100),

    -- Program details
    description TEXT,
    target_countries VARCHAR(500), -- Comma-separated ISO codes
    target_sectors VARCHAR(500),

    -- Status
    is_active BOOLEAN NOT NULL DEFAULT true,
    effective_date DATE,
    termination_date DATE,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Indexes
CREATE INDEX idx_sanctions_programs_code ON sanctions_programs(program_code);
CREATE INDEX idx_sanctions_programs_jurisdiction ON sanctions_programs(jurisdiction);
CREATE INDEX idx_sanctions_programs_active ON sanctions_programs(is_active);

-- Comments
COMMENT ON TABLE sanctions_programs IS 'Sanctions programs (e.g., UKRAINE-EO13661, IRAN, SYRIA)';

-- ============================================================================
-- ENTITY PROGRAM LINKAGE
-- ============================================================================

CREATE TABLE sanctioned_entity_programs (
    sanctioned_entity_id UUID NOT NULL,
    sanctions_program_id UUID NOT NULL,

    listing_date DATE,
    remarks TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_entity_programs
        PRIMARY KEY (sanctioned_entity_id, sanctions_program_id),
    CONSTRAINT fk_entity_programs_entity
        FOREIGN KEY (sanctioned_entity_id)
        REFERENCES sanctioned_entities(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_entity_programs_program
        FOREIGN KEY (sanctions_program_id)
        REFERENCES sanctions_programs(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_entity_programs_entity ON sanctioned_entity_programs(sanctioned_entity_id);
CREATE INDEX idx_entity_programs_program ON sanctioned_entity_programs(sanctions_program_id);

-- ============================================================================
-- SANCTIONS UPDATE HISTORY (Change Tracking)
-- ============================================================================

CREATE TABLE sanctions_update_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Update identification
    list_source VARCHAR(20) NOT NULL,
    old_version_id VARCHAR(50),
    new_version_id VARCHAR(50) NOT NULL,

    -- Change summary
    change_type VARCHAR(20) NOT NULL, -- ADDED, MODIFIED, REMOVED
    entity_count INTEGER NOT NULL,

    -- Detailed changes
    changes_json JSONB, -- Detailed diff in JSON format

    -- Processing
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,

    -- Notifications
    compliance_team_notified BOOLEAN DEFAULT false,
    notification_sent_at TIMESTAMP,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_update_history_source ON sanctions_update_history(list_source);
CREATE INDEX idx_update_history_detected ON sanctions_update_history(detected_at DESC);
CREATE INDEX idx_update_history_type ON sanctions_update_history(change_type);
CREATE INDEX idx_update_history_changes ON sanctions_update_history USING gin(changes_json);

-- Comments
COMMENT ON TABLE sanctions_update_history IS 'Tracks all changes to sanctions lists for audit and alerting';
COMMENT ON COLUMN sanctions_update_history.changes_json IS 'Detailed diff including entity IDs and field changes';

-- ============================================================================
-- SCREENING MATCH CACHE (Performance Optimization)
-- ============================================================================

CREATE TABLE sanctions_screening_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Search input
    search_name VARCHAR(500) NOT NULL,
    search_name_normalized VARCHAR(500) NOT NULL,
    search_parameters JSONB, -- Additional search criteria

    -- Match results
    has_match BOOLEAN NOT NULL,
    match_count INTEGER DEFAULT 0,
    highest_match_score DECIMAL(5,2),
    matched_entity_ids UUID[], -- Array of sanctioned_entity IDs

    -- Cache metadata
    list_version_hash VARCHAR(64) NOT NULL, -- Hash of active list versions
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    hit_count INTEGER DEFAULT 0,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_screening_cache
        UNIQUE (search_name_normalized, list_version_hash)
);

-- Indexes
CREATE INDEX idx_screening_cache_name ON sanctions_screening_cache(search_name_normalized);
CREATE INDEX idx_screening_cache_expires ON sanctions_screening_cache(expires_at);
CREATE INDEX idx_screening_cache_version ON sanctions_screening_cache(list_version_hash);

-- Auto-cleanup of expired cache entries (PostgreSQL)
-- This can be run as a scheduled job or trigger
-- DELETE FROM sanctions_screening_cache WHERE expires_at < NOW();

-- Comments
COMMENT ON TABLE sanctions_screening_cache IS 'Performance cache for sanctions screening results (6-hour TTL)';
COMMENT ON COLUMN sanctions_screening_cache.list_version_hash IS 'Invalidated when any sanctions list updates';

-- ============================================================================
-- VERIFICATION QUERIES (commented out - for manual testing)
-- ============================================================================

-- Count total sanctioned entities:
-- SELECT list_source, COUNT(*) as entity_count
-- FROM sanctioned_entities se
-- JOIN sanctions_list_metadata slm ON se.list_metadata_id = slm.id
-- WHERE se.is_active = true AND slm.is_active = true
-- GROUP BY list_source;

-- Find most recent list versions:
-- SELECT list_source, version_id, version_date, total_entries
-- FROM sanctions_list_metadata
-- WHERE is_active = true
-- ORDER BY list_source, version_date DESC;

-- ============================================================================
-- ROLLBACK SCRIPT (if needed for emergency rollback)
-- ============================================================================
-- DROP TABLE IF EXISTS sanctions_screening_cache CASCADE;
-- DROP TABLE IF EXISTS sanctions_update_history CASCADE;
-- DROP TABLE IF EXISTS sanctioned_entity_programs CASCADE;
-- DROP TABLE IF EXISTS sanctions_programs CASCADE;
-- DROP TABLE IF EXISTS sanctioned_entity_aliases CASCADE;
-- DROP TABLE IF EXISTS sanctioned_entities CASCADE;
-- DROP TABLE IF EXISTS sanctions_list_metadata CASCADE;
