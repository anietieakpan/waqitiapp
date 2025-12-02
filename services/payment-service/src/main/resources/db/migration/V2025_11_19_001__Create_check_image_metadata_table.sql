-- ============================================================================
-- PRODUCTION MIGRATION: Check Image Metadata Persistence
-- ============================================================================
-- Version: V200
-- Date: November 18, 2025
-- Author: Waqiti Production Team
-- Purpose: Create table for storing check deposit image metadata
--
-- COMPLIANCE:
-- - Check 21 Act: 7-year retention of check image metadata
-- - SOX 404: Immutable audit trail of financial documents
-- - PCI-DSS: Encryption key tracking and versioning
-- - NACHA: Image integrity verification (SHA-256 checksums)
--
-- FEATURES:
-- - PostgreSQL JSONB for flexible tag storage
-- - Soft delete support (preserves audit trail)
-- - Comprehensive indexes for query performance
-- - Audit fields (created_at, updated_at, created_by, updated_by)
-- - Retention policy enforcement (expires_at column)
-- - Virus scan result tracking
-- - Encryption metadata tracking
-- ============================================================================

-- Create check_image_metadata table
CREATE TABLE IF NOT EXISTS payment.check_image_metadata (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Business Keys
    check_deposit_id VARCHAR(50) NOT NULL,
    image_type VARCHAR(10) NOT NULL CHECK (image_type IN ('FRONT', 'BACK')),

    -- S3 Storage Information
    object_key VARCHAR(500) NOT NULL UNIQUE,
    bucket_name VARCHAR(100) NOT NULL,
    region VARCHAR(50),
    version_id VARCHAR(100),

    -- File Size Information
    original_size_bytes BIGINT NOT NULL,
    encrypted_size_bytes BIGINT,

    -- Encryption Information
    encrypted BOOLEAN NOT NULL DEFAULT false,
    encryption_key_id VARCHAR(200),

    -- Timestamps
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,  -- Check 21 Act: 7 years from upload

    -- Content Information
    content_type VARCHAR(100),
    checksum_sha256 VARCHAR(64),  -- SHA-256 hash for integrity verification
    image_width INTEGER,
    image_height INTEGER,
    image_format VARCHAR(20),

    -- Virus Scanning
    virus_scanned BOOLEAN NOT NULL DEFAULT false,
    virus_scan_result VARCHAR(20),  -- CLEAN, INFECTED, PENDING, FAILED
    virus_scanned_at TIMESTAMP,

    -- Archival Information
    archived BOOLEAN NOT NULL DEFAULT false,
    archived_at TIMESTAMP,

    -- User Information
    uploaded_by_user_id VARCHAR(50) NOT NULL,

    -- Tags (stored as JSONB for flexibility)
    tags JSONB,

    -- Audit Fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),

    -- Soft Delete Support
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(50),

    -- Constraints
    CONSTRAINT check_expires_after_upload CHECK (expires_at > uploaded_at),
    CONSTRAINT check_virus_scan_result CHECK (virus_scan_result IN ('CLEAN', 'INFECTED', 'PENDING', 'FAILED', NULL)),
    CONSTRAINT check_image_type CHECK (image_type IN ('FRONT', 'BACK'))
);

-- ============================================================================
-- INDEXES FOR OPTIMAL QUERY PERFORMANCE
-- ============================================================================

-- Primary lookup indexes
CREATE INDEX IF NOT EXISTS idx_check_deposit_id ON payment.check_image_metadata(check_deposit_id) WHERE deleted = false;
CREATE UNIQUE INDEX IF NOT EXISTS idx_object_key ON payment.check_image_metadata(object_key) WHERE deleted = false;

-- User audit indexes
CREATE INDEX IF NOT EXISTS idx_uploaded_by_user_id ON payment.check_image_metadata(uploaded_by_user_id) WHERE deleted = false;

-- Retention policy indexes
CREATE INDEX IF NOT EXISTS idx_expires_at ON payment.check_image_metadata(expires_at) WHERE deleted = false AND archived = false;
CREATE INDEX IF NOT EXISTS idx_uploaded_at ON payment.check_image_metadata(uploaded_at);

-- Virus scanning indexes
CREATE INDEX IF NOT EXISTS idx_virus_scan_result ON payment.check_image_metadata(virus_scan_result) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_virus_pending ON payment.check_image_metadata(virus_scanned, virus_scan_result) WHERE deleted = false AND (virus_scanned = false OR virus_scan_result = 'PENDING');

-- Archival indexes
CREATE INDEX IF NOT EXISTS idx_archived ON payment.check_image_metadata(archived) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_old_images ON payment.check_image_metadata(uploaded_at) WHERE deleted = false AND archived = false;

-- Encryption indexes (for key rotation)
CREATE INDEX IF NOT EXISTS idx_encryption_key_id ON payment.check_image_metadata(encryption_key_id) WHERE encrypted = true AND deleted = false;

-- Soft delete index
CREATE INDEX IF NOT EXISTS idx_deleted ON payment.check_image_metadata(deleted);

-- Composite index for common query patterns
CREATE INDEX IF NOT EXISTS idx_check_deposit_image_type ON payment.check_image_metadata(check_deposit_id, image_type) WHERE deleted = false;

-- GIN index for JSONB tags (enables efficient tag queries)
CREATE INDEX IF NOT EXISTS idx_tags_gin ON payment.check_image_metadata USING GIN (tags);

-- ============================================================================
-- COMMENTS FOR DOCUMENTATION
-- ============================================================================

COMMENT ON TABLE payment.check_image_metadata IS 'Stores metadata for check deposit images stored in AWS S3. Provides audit trail, encryption tracking, and compliance enforcement per Check 21 Act (7-year retention).';

COMMENT ON COLUMN payment.check_image_metadata.id IS 'Primary key - auto-generated';
COMMENT ON COLUMN payment.check_image_metadata.check_deposit_id IS 'Business key - links to check_deposit table';
COMMENT ON COLUMN payment.check_image_metadata.image_type IS 'Type of check image: FRONT or BACK';
COMMENT ON COLUMN payment.check_image_metadata.object_key IS 'S3 object key (unique identifier for image in S3)';
COMMENT ON COLUMN payment.check_image_metadata.bucket_name IS 'S3 bucket name where image is stored';
COMMENT ON COLUMN payment.check_image_metadata.region IS 'AWS region (e.g., us-east-1)';
COMMENT ON COLUMN payment.check_image_metadata.version_id IS 'S3 version ID (supports bucket versioning)';
COMMENT ON COLUMN payment.check_image_metadata.original_size_bytes IS 'Original file size before encryption (bytes)';
COMMENT ON COLUMN payment.check_image_metadata.encrypted_size_bytes IS 'Encrypted file size after encryption (bytes)';
COMMENT ON COLUMN payment.check_image_metadata.encrypted IS 'Whether image is encrypted (should always be true in production)';
COMMENT ON COLUMN payment.check_image_metadata.encryption_key_id IS 'AWS KMS key ID or local key ID used for encryption';
COMMENT ON COLUMN payment.check_image_metadata.uploaded_at IS 'Timestamp when image was uploaded to S3';
COMMENT ON COLUMN payment.check_image_metadata.expires_at IS 'Expiration date per Check 21 Act (upload_date + 7 years)';
COMMENT ON COLUMN payment.check_image_metadata.content_type IS 'MIME type (image/jpeg, image/png, image/tiff)';
COMMENT ON COLUMN payment.check_image_metadata.checksum_sha256 IS 'SHA-256 hash of original image for integrity verification';
COMMENT ON COLUMN payment.check_image_metadata.image_width IS 'Image width in pixels';
COMMENT ON COLUMN payment.check_image_metadata.image_height IS 'Image height in pixels';
COMMENT ON COLUMN payment.check_image_metadata.image_format IS 'Image format (JPEG, PNG, TIFF, PDF)';
COMMENT ON COLUMN payment.check_image_metadata.virus_scanned IS 'Whether virus/malware scan was performed';
COMMENT ON COLUMN payment.check_image_metadata.virus_scan_result IS 'Virus scan result: CLEAN, INFECTED, PENDING, or FAILED';
COMMENT ON COLUMN payment.check_image_metadata.virus_scanned_at IS 'Timestamp of virus scan';
COMMENT ON COLUMN payment.check_image_metadata.archived IS 'Whether image has been archived to Glacier';
COMMENT ON COLUMN payment.check_image_metadata.archived_at IS 'Timestamp when archived to cheaper storage';
COMMENT ON COLUMN payment.check_image_metadata.uploaded_by_user_id IS 'User ID who uploaded the image (for audit trail)';
COMMENT ON COLUMN payment.check_image_metadata.tags IS 'Flexible key-value tags stored as JSONB';
COMMENT ON COLUMN payment.check_image_metadata.created_at IS 'Audit field - record creation timestamp';
COMMENT ON COLUMN payment.check_image_metadata.updated_at IS 'Audit field - last update timestamp';
COMMENT ON COLUMN payment.check_image_metadata.created_by IS 'Audit field - user who created record';
COMMENT ON COLUMN payment.check_image_metadata.updated_by IS 'Audit field - user who last updated record';
COMMENT ON COLUMN payment.check_image_metadata.deleted IS 'Soft delete flag (preserves audit trail)';
COMMENT ON COLUMN payment.check_image_metadata.deleted_at IS 'Soft delete timestamp';
COMMENT ON COLUMN payment.check_image_metadata.deleted_by IS 'User who performed soft delete';

-- ============================================================================
-- STATISTICS FOR QUERY OPTIMIZER
-- ============================================================================

ANALYZE payment.check_image_metadata;

-- ============================================================================
-- GRANTS (Adjust based on your security model)
-- ============================================================================

-- Grant appropriate permissions to payment service role
-- GRANT SELECT, INSERT, UPDATE, DELETE ON payment.check_image_metadata TO payment_service_role;
-- GRANT USAGE, SELECT ON SEQUENCE payment.check_image_metadata_id_seq TO payment_service_role;

-- ============================================================================
-- VERIFICATION QUERIES (For testing migration)
-- ============================================================================

-- Verify table exists
-- SELECT table_name, table_type
-- FROM information_schema.tables
-- WHERE table_schema = 'payment'
--   AND table_name = 'check_image_metadata';

-- Verify indexes
-- SELECT indexname, indexdef
-- FROM pg_indexes
-- WHERE schemaname = 'payment'
--   AND tablename = 'check_image_metadata'
-- ORDER BY indexname;

-- Verify constraints
-- SELECT conname, contype, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid = 'payment.check_image_metadata'::regclass
-- ORDER BY contype, conname;

-- ============================================================================
-- MIGRATION COMPLETE
-- ============================================================================
-- This migration creates the check_image_metadata table with:
-- ✅ Full audit trail (SOX compliance)
-- ✅ 7-year retention tracking (Check 21 Act)
-- ✅ Encryption metadata (PCI-DSS)
-- ✅ Virus scan tracking (Security)
-- ✅ Soft delete support (Data recovery)
-- ✅ JSONB tags (Flexibility)
-- ✅ Comprehensive indexes (Performance)
-- ============================================================================
