-- ATM Service Schema V4: Check Processing and Imaging (Check 21 Act Compliance)
-- Created: 2025-11-15
-- Description: Check imaging, OCR, and hold management for remote deposit capture

-- Check Images Table (Check 21 Act)
CREATE TABLE IF NOT EXISTS check_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deposit_id UUID NOT NULL,
    account_id UUID NOT NULL,

    -- Check information
    check_number VARCHAR(50) NOT NULL,
    routing_number VARCHAR(9),
    account_number_hash VARCHAR(255),  -- Hashed for security

    -- Financial fields with DECIMAL(19,4) precision
    amount DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Image storage (S3/cloud references)
    front_image_url TEXT NOT NULL,
    back_image_url TEXT NOT NULL,
    image_format VARCHAR(10) NOT NULL CHECK (image_format IN ('JPEG', 'PNG', 'TIFF')),

    -- Image quality metrics
    front_image_size_bytes BIGINT CHECK (front_image_size_bytes BETWEEN 50000 AND 5000000),  -- 50KB-5MB
    back_image_size_bytes BIGINT CHECK (back_image_size_bytes BETWEEN 50000 AND 5000000),
    resolution_dpi INTEGER CHECK (resolution_dpi >= 200),  -- Check 21 requirement
    image_quality_score DECIMAL(5, 2) CHECK (image_quality_score BETWEEN 0 AND 100),

    -- OCR processing
    ocr_enabled BOOLEAN DEFAULT TRUE,
    ocr_provider VARCHAR(20) CHECK (ocr_provider IN ('TESSERACT', 'AWS_TEXTRACT', 'GOOGLE_VISION')),
    ocr_confidence DECIMAL(5, 2) CHECK (ocr_confidence BETWEEN 0 AND 100),
    ocr_extracted_amount DECIMAL(19, 4),
    ocr_extracted_date DATE,
    ocr_extracted_payee VARCHAR(255),

    -- Validation
    micr_validation_passed BOOLEAN,
    amount_validation_passed BOOLEAN,
    manual_review_required BOOLEAN DEFAULT FALSE,
    manual_review_reason VARCHAR(255),

    -- Processing status
    processing_status VARCHAR(20) NOT NULL CHECK (processing_status IN ('PENDING', 'PROCESSING', 'VALIDATED', 'REJECTED', 'MANUAL_REVIEW')),
    rejection_reason VARCHAR(255),

    -- Timestamps
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    validated_at TIMESTAMP,

    -- Audit and compliance (7-year retention for Check 21)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retention_until_date DATE NOT NULL,  -- 7 years from deposit
    version BIGINT NOT NULL DEFAULT 0,

    -- Foreign keys
    CONSTRAINT fk_check_image_deposit FOREIGN KEY (deposit_id) REFERENCES atm_deposits(id) ON DELETE RESTRICT,

    -- Constraints
    CONSTRAINT chk_ocr_amount_match CHECK (
        ocr_extracted_amount IS NULL OR
        ABS(ocr_extracted_amount - amount) < 0.01
    )
);

-- Check Holds Table (Funds Availability)
CREATE TABLE IF NOT EXISTS check_holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deposit_id UUID NOT NULL,
    check_image_id UUID,
    account_id UUID NOT NULL,

    -- Hold information
    hold_type VARCHAR(20) NOT NULL CHECK (hold_type IN ('LOCAL_CHECK', 'NONLOCAL_CHECK', 'NEW_ACCOUNT', 'LARGE_DEPOSIT', 'REPEATED_OVERDRAFT', 'REASONABLE_DOUBT')),

    -- Financial fields with DECIMAL(19,4) precision
    hold_amount DECIMAL(19, 4) NOT NULL CHECK (hold_amount > 0),
    available_amount DECIMAL(19, 4) DEFAULT 0.0000 CHECK (available_amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Hold period (Regulation CC compliance)
    hold_days INTEGER NOT NULL CHECK (hold_days BETWEEN 0 AND 9),
    hold_start_date DATE NOT NULL,
    hold_release_date DATE NOT NULL,

    -- Status
    hold_status VARCHAR(20) NOT NULL CHECK (hold_status IN ('ACTIVE', 'RELEASED', 'EXTENDED', 'CANCELLED')),
    release_reason VARCHAR(255),

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    -- Foreign keys
    CONSTRAINT fk_hold_deposit FOREIGN KEY (deposit_id) REFERENCES atm_deposits(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hold_check_image FOREIGN KEY (check_image_id) REFERENCES check_images(id) ON DELETE SET NULL,

    -- Constraints
    CONSTRAINT chk_release_date CHECK (hold_release_date >= hold_start_date),
    CONSTRAINT chk_available_lte_hold CHECK (available_amount <= hold_amount)
);

-- ATM Balance Inquiries Table
CREATE TABLE IF NOT EXISTS atm_inquiries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    atm_id UUID NOT NULL,
    card_id UUID,

    -- Balance information (returned to customer)
    available_balance DECIMAL(19, 4) NOT NULL,
    current_balance DECIMAL(19, 4) NOT NULL,
    pending_transactions_count INTEGER DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    -- Inquiry type
    inquiry_type VARCHAR(20) NOT NULL CHECK (inquiry_type IN ('BALANCE', 'MINI_STATEMENT', 'TRANSACTION_HISTORY')),

    -- Fee information
    inquiry_fee DECIMAL(19, 4) DEFAULT 0.0000 CHECK (inquiry_fee >= 0),
    fee_waived BOOLEAN DEFAULT FALSE,

    -- Timestamps
    inquiry_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_available_lte_current CHECK (available_balance <= current_balance)
);

-- Indexes for performance
CREATE INDEX idx_check_images_deposit ON check_images(deposit_id);
CREATE INDEX idx_check_images_account ON check_images(account_id, captured_at DESC);
CREATE INDEX idx_check_images_status ON check_images(processing_status);
CREATE INDEX idx_check_images_review ON check_images(manual_review_required) WHERE manual_review_required = TRUE;
CREATE INDEX idx_check_images_retention ON check_images(retention_until_date);

CREATE INDEX idx_check_holds_deposit ON check_holds(deposit_id);
CREATE INDEX idx_check_holds_account ON check_holds(account_id, hold_status);
CREATE INDEX idx_check_holds_release ON check_holds(hold_release_date) WHERE hold_status = 'ACTIVE';
CREATE INDEX idx_check_holds_active ON check_holds(account_id, hold_status, hold_release_date) WHERE hold_status = 'ACTIVE';

CREATE INDEX idx_inquiries_account ON atm_inquiries(account_id, inquiry_date DESC);
CREATE INDEX idx_inquiries_atm ON atm_inquiries(atm_id, inquiry_date DESC);
CREATE INDEX idx_inquiries_date ON atm_inquiries(inquiry_date DESC);

-- Update triggers
CREATE TRIGGER trg_check_images_updated_at BEFORE UPDATE ON check_images
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

CREATE TRIGGER trg_check_holds_updated_at BEFORE UPDATE ON check_holds
    FOR EACH ROW EXECUTE FUNCTION update_cards_updated_at();

-- Auto-set retention date (7 years for Check 21 compliance)
CREATE OR REPLACE FUNCTION set_check_retention_date()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.retention_until_date IS NULL THEN
        NEW.retention_until_date := CURRENT_DATE + INTERVAL '7 years';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_retention_date BEFORE INSERT ON check_images
    FOR EACH ROW EXECUTE FUNCTION set_check_retention_date();

-- Comments
COMMENT ON TABLE check_images IS 'Check 21 Act compliant check image storage with OCR and validation';
COMMENT ON TABLE check_holds IS 'Regulation CC compliant check hold management for funds availability';
COMMENT ON TABLE atm_inquiries IS 'ATM balance inquiry transactions';
COMMENT ON COLUMN check_images.retention_until_date IS 'Check 21 requires 7-year image retention';
COMMENT ON COLUMN check_images.resolution_dpi IS 'Check 21 requires minimum 200 DPI resolution';
COMMENT ON COLUMN check_holds.hold_days IS 'Regulation CC: 2 days local, 5 days nonlocal, 9 days new account';
