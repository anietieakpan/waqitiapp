-- Tax Reporting System Database Schema
-- Version: 100 (major tax reporting feature)
-- Description: Comprehensive IRS tax reporting implementation (1099-B, 1099-DIV, 1099-INT)
-- Compliance: IRC Sections 6045, 6042, 6049, IRS Publication 1220
-- Created: 2025-10-01

-- =============================================================================
-- Tax Documents Table (Form 1099-B, 1099-DIV, 1099-INT, etc.)
-- =============================================================================

CREATE TABLE tax_documents (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Entity References
    user_id UUID NOT NULL,
    investment_account_id VARCHAR(255) NOT NULL,

    -- Document Classification
    document_type VARCHAR(20) NOT NULL CHECK (document_type IN ('FORM_1099_B', 'FORM_1099_DIV', 'FORM_1099_INT', 'FORM_1099_MISC')),
    tax_year INTEGER NOT NULL,
    document_number VARCHAR(50) NOT NULL UNIQUE,

    -- Correction Tracking
    is_corrected BOOLEAN NOT NULL DEFAULT false,
    original_document_id UUID,
    correction_number INTEGER,

    -- =================================================================
    -- Taxpayer Information
    -- =================================================================
    taxpayer_tin VARCHAR(500), -- Encrypted
    taxpayer_name VARCHAR(255) NOT NULL,
    taxpayer_address_line1 VARCHAR(255),
    taxpayer_address_line2 VARCHAR(255),
    taxpayer_city VARCHAR(100),
    taxpayer_state VARCHAR(2),
    taxpayer_zip VARCHAR(10),

    -- =================================================================
    -- Payer Information (Waqiti)
    -- =================================================================
    payer_tin VARCHAR(11) NOT NULL,
    payer_name VARCHAR(255) NOT NULL,
    payer_address VARCHAR(500),

    -- =================================================================
    -- Form 1099-B Fields (Broker Transactions)
    -- =================================================================
    proceeds_from_sales DECIMAL(19,4),
    cost_basis DECIMAL(19,4),
    wash_sale_loss_disallowed DECIMAL(19,4),
    federal_tax_withheld DECIMAL(19,4),
    short_term_covered BOOLEAN,
    short_term_not_covered BOOLEAN,
    long_term_covered BOOLEAN,
    long_term_not_covered BOOLEAN,
    is_ordinary_income BOOLEAN,
    aggregate_profit_loss DECIMAL(19,4),

    -- =================================================================
    -- Form 1099-DIV Fields (Dividends)
    -- =================================================================
    total_ordinary_dividends DECIMAL(19,4),
    qualified_dividends DECIMAL(19,4),
    total_capital_gain_distributions DECIMAL(19,4),
    section_1250_gain DECIMAL(19,4),
    section_1202_gain DECIMAL(19,4),
    collectibles_gain DECIMAL(19,4),
    section_897_dividends DECIMAL(19,4),
    section_897_capital_gain DECIMAL(19,4),
    nondividend_distributions DECIMAL(19,4),
    div_federal_tax_withheld DECIMAL(19,4),
    section_199a_dividends DECIMAL(19,4),
    investment_expenses DECIMAL(19,4),
    foreign_tax_paid DECIMAL(19,4),
    foreign_country VARCHAR(100),
    cash_liquidation_distributions DECIMAL(19,4),
    noncash_liquidation_distributions DECIMAL(19,4),
    exempt_interest_dividends DECIMAL(19,4),
    private_activity_bond_dividends DECIMAL(19,4),

    -- =================================================================
    -- State Tax Information
    -- =================================================================
    state_tax_withheld DECIMAL(19,4),
    state_payer_number VARCHAR(20),
    state_distribution DECIMAL(19,4),

    -- =================================================================
    -- Transaction Details (JSONB)
    -- =================================================================
    transaction_details JSONB,
    dividend_details JSONB,

    -- =================================================================
    -- Filing and Delivery
    -- =================================================================
    generated_at DATE NOT NULL,
    filing_status VARCHAR(30) NOT NULL CHECK (filing_status IN (
        'PENDING_GENERATION', 'GENERATED', 'PENDING_REVIEW', 'REVIEWED',
        'PENDING_IRS_FILING', 'FILED_WITH_IRS', 'PENDING_RECIPIENT_DELIVERY',
        'DELIVERED_TO_RECIPIENT', 'COMPLETED', 'FAILED', 'CORRECTED'
    )),
    filed_at DATE,
    irs_confirmation_number VARCHAR(100),
    delivered_to_recipient_at DATE,
    delivery_method VARCHAR(20) CHECK (delivery_method IN ('EMAIL', 'POSTAL_MAIL', 'ONLINE_PORTAL', 'SECURE_DOWNLOAD')),

    -- File paths
    pdf_file_path VARCHAR(500),
    fire_xml_file_path VARCHAR(500),
    digital_signature VARCHAR(500),

    -- =================================================================
    -- Compliance and Audit
    -- =================================================================
    calculation_notes TEXT,
    reviewed_by UUID,
    reviewed_at DATE,
    review_notes TEXT,

    -- =================================================================
    -- Audit Trail (extends BaseEntity)
    -- =================================================================
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    metadata JSONB,

    -- Constraints
    CONSTRAINT fk_tax_doc_original FOREIGN KEY (original_document_id) REFERENCES tax_documents(id)
);

-- =============================================================================
-- Indexes for Tax Documents
-- =============================================================================

CREATE INDEX idx_tax_doc_user_year ON tax_documents(user_id, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_doc_account_year ON tax_documents(investment_account_id, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_doc_type_year ON tax_documents(document_type, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_doc_status ON tax_documents(filing_status) WHERE deleted = false;
CREATE INDEX idx_tax_doc_generated_at ON tax_documents(generated_at DESC) WHERE deleted = false;
CREATE INDEX idx_tax_doc_filed_at ON tax_documents(filed_at DESC) WHERE filed_at IS NOT NULL AND deleted = false;
CREATE INDEX idx_tax_doc_corrected ON tax_documents(is_corrected, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_doc_number ON tax_documents(document_number) WHERE deleted = false;
CREATE INDEX idx_tax_doc_pending_filing ON tax_documents(filing_status, generated_at)
    WHERE filing_status IN ('PENDING_IRS_FILING', 'REVIEWED') AND deleted = false;
CREATE INDEX idx_tax_doc_pending_delivery ON tax_documents(filing_status, generated_at)
    WHERE filing_status = 'PENDING_RECIPIENT_DELIVERY' AND deleted = false;

-- JSONB indexes
CREATE INDEX idx_tax_doc_transaction_details ON tax_documents USING gin(transaction_details) WHERE transaction_details IS NOT NULL;
CREATE INDEX idx_tax_doc_dividend_details ON tax_documents USING gin(dividend_details) WHERE dividend_details IS NOT NULL;

-- =============================================================================
-- Tax Transactions Table (Detailed Lot-Level Tracking)
-- =============================================================================

CREATE TABLE tax_transactions (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Entity References
    user_id UUID NOT NULL,
    investment_account_id VARCHAR(255) NOT NULL,
    tax_year INTEGER NOT NULL,

    -- Transaction Classification
    transaction_type VARCHAR(30) NOT NULL CHECK (transaction_type IN (
        'STOCK_SALE', 'STOCK_PURCHASE', 'DIVIDEND_ORDINARY', 'DIVIDEND_QUALIFIED',
        'DIVIDEND_CAPITAL_GAIN', 'RETURN_OF_CAPITAL', 'INTEREST_INCOME',
        'BOND_INTEREST', 'OPTION_EXERCISE', 'OPTION_ASSIGNMENT',
        'STOCK_SPLIT', 'MERGER_ACQUISITION', 'SPINOFF'
    )),

    -- Related References
    order_id VARCHAR(255),
    tax_document_id UUID,

    -- =================================================================
    -- Security Information
    -- =================================================================
    symbol VARCHAR(20) NOT NULL,
    security_name VARCHAR(255),
    cusip VARCHAR(9),
    instrument_type VARCHAR(30),

    -- =================================================================
    -- Transaction Details (Sales - 1099-B)
    -- =================================================================
    acquisition_date DATE,
    sale_date DATE,
    quantity DECIMAL(19,8),
    proceeds DECIMAL(19,4),
    cost_basis DECIMAL(19,4),
    sale_commission DECIMAL(19,4),
    purchase_commission DECIMAL(19,4),
    net_proceeds DECIMAL(19,4),
    adjusted_cost_basis DECIMAL(19,4),
    gain_loss DECIMAL(19,4),

    -- Holding Period
    holding_period_type VARCHAR(20) CHECK (holding_period_type IN ('SHORT_TERM', 'LONG_TERM')),
    holding_period_days INTEGER,

    -- =================================================================
    -- Wash Sale Tracking
    -- =================================================================
    is_wash_sale BOOLEAN NOT NULL DEFAULT false,
    wash_sale_loss_disallowed DECIMAL(19,4),
    related_wash_sale_transaction_id UUID,
    wash_sale_adjustment DECIMAL(19,4),

    -- =================================================================
    -- Dividend Details (1099-DIV)
    -- =================================================================
    dividend_payment_date DATE,
    dividend_ex_date DATE,
    dividend_amount DECIMAL(19,4),
    dividend_type VARCHAR(30) CHECK (dividend_type IN (
        'ORDINARY', 'QUALIFIED', 'CAPITAL_GAIN', 'RETURN_OF_CAPITAL', 'EXEMPT_INTEREST'
    )),
    is_qualified_dividend BOOLEAN NOT NULL DEFAULT false,
    return_of_capital DECIMAL(19,4),
    foreign_tax_paid DECIMAL(19,4),
    foreign_country VARCHAR(100),

    -- =================================================================
    -- Cost Basis Calculation
    -- =================================================================
    cost_basis_method VARCHAR(20) CHECK (cost_basis_method IN (
        'FIFO', 'LIFO', 'SPECIFIC_ID', 'AVERAGE_COST', 'HIFO'
    )),
    is_covered_security BOOLEAN NOT NULL DEFAULT true,
    is_noncovered_security BOOLEAN NOT NULL DEFAULT false,

    -- =================================================================
    -- Reporting Status
    -- =================================================================
    reported_on_1099 BOOLEAN NOT NULL DEFAULT false,
    form_1099_document_number VARCHAR(50),
    reporting_year INTEGER,
    irs_reporting_code VARCHAR(5),

    -- =================================================================
    -- Additional Tax Attributes
    -- =================================================================
    is_ordinary_income BOOLEAN NOT NULL DEFAULT false,
    is_collectibles_gain BOOLEAN NOT NULL DEFAULT false,
    is_section_1256 BOOLEAN NOT NULL DEFAULT false,
    adjustment_description TEXT,
    tax_notes TEXT,

    -- =================================================================
    -- Audit Trail
    -- =================================================================
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    metadata JSONB,

    -- Constraints
    CONSTRAINT fk_tax_txn_document FOREIGN KEY (tax_document_id) REFERENCES tax_documents(id),
    CONSTRAINT fk_tax_txn_wash_sale FOREIGN KEY (related_wash_sale_transaction_id) REFERENCES tax_transactions(id)
);

-- =============================================================================
-- Indexes for Tax Transactions
-- =============================================================================

CREATE INDEX idx_tax_txn_user_year ON tax_transactions(user_id, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_txn_account_year ON tax_transactions(investment_account_id, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_txn_symbol_year ON tax_transactions(symbol, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_txn_type ON tax_transactions(transaction_type) WHERE deleted = false;
CREATE INDEX idx_tax_txn_sale_date ON tax_transactions(sale_date) WHERE sale_date IS NOT NULL AND deleted = false;
CREATE INDEX idx_tax_txn_reported ON tax_transactions(reported_on_1099, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_txn_wash_sale ON tax_transactions(is_wash_sale, tax_year) WHERE is_wash_sale = true AND deleted = false;
CREATE INDEX idx_tax_txn_holding_period ON tax_transactions(holding_period_type, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_txn_order_id ON tax_transactions(order_id) WHERE order_id IS NOT NULL AND deleted = false;
CREATE INDEX idx_tax_txn_covered ON tax_transactions(is_covered_security, tax_year) WHERE deleted = false;
CREATE INDEX idx_tax_txn_dividend_date ON tax_transactions(dividend_payment_date)
    WHERE dividend_payment_date IS NOT NULL AND deleted = false;
CREATE INDEX idx_tax_txn_qualified_div ON tax_transactions(is_qualified_dividend, tax_year)
    WHERE is_qualified_dividend = true AND deleted = false;

-- Composite indexes for common queries
CREATE INDEX idx_tax_txn_user_symbol_dates ON tax_transactions(user_id, symbol, sale_date, acquisition_date)
    WHERE deleted = false;
CREATE INDEX idx_tax_txn_wash_sale_window ON tax_transactions(user_id, symbol, transaction_type, sale_date)
    WHERE transaction_type IN ('STOCK_PURCHASE', 'STOCK_SALE') AND deleted = false;

-- =============================================================================
-- Comments for Documentation
-- =============================================================================

COMMENT ON TABLE tax_documents IS 'IRS tax documents (1099-B, 1099-DIV, 1099-INT) with FIRE XML support';
COMMENT ON TABLE tax_transactions IS 'Detailed tax-reportable transactions with lot-level tracking and wash sale detection';

COMMENT ON COLUMN tax_documents.document_type IS 'Form type: FORM_1099_B, FORM_1099_DIV, FORM_1099_INT, FORM_1099_MISC';
COMMENT ON COLUMN tax_documents.filing_status IS 'Workflow status from generation through IRS filing and recipient delivery';
COMMENT ON COLUMN tax_documents.irs_confirmation_number IS 'FIRE TCC confirmation number from IRS';
COMMENT ON COLUMN tax_documents.transaction_details IS 'JSONB array of individual transactions for 1099-B';
COMMENT ON COLUMN tax_documents.dividend_details IS 'JSONB array of dividend payments for 1099-DIV';

COMMENT ON COLUMN tax_transactions.transaction_type IS 'Transaction classification for tax reporting';
COMMENT ON COLUMN tax_transactions.holding_period_type IS 'SHORT_TERM (≤365 days) or LONG_TERM (>365 days)';
COMMENT ON COLUMN tax_transactions.is_wash_sale IS 'IRC Section 1091 wash sale flag (30 days before/after)';
COMMENT ON COLUMN tax_transactions.cost_basis_method IS 'FIFO, LIFO, SPECIFIC_ID, AVERAGE_COST, HIFO';
COMMENT ON COLUMN tax_transactions.is_covered_security IS 'Broker reports cost basis to IRS (post-2011 stocks)';

-- =============================================================================
-- Triggers for Automatic Timestamp Updates
-- =============================================================================

CREATE OR REPLACE FUNCTION update_tax_document_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_tax_document_updated_at
    BEFORE UPDATE ON tax_documents
    FOR EACH ROW
    EXECUTE FUNCTION update_tax_document_timestamp();

CREATE OR REPLACE FUNCTION update_tax_transaction_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_tax_transaction_updated_at
    BEFORE UPDATE ON tax_transactions
    FOR EACH ROW
    EXECUTE FUNCTION update_tax_transaction_timestamp();

-- =============================================================================
-- Trigger for Automatic Tax Year Calculation
-- =============================================================================

CREATE OR REPLACE FUNCTION set_tax_year_from_sale_date()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.sale_date IS NOT NULL AND NEW.tax_year IS NULL THEN
        NEW.tax_year = EXTRACT(YEAR FROM NEW.sale_date);
    END IF;
    IF NEW.dividend_payment_date IS NOT NULL AND NEW.tax_year IS NULL THEN
        NEW.tax_year = EXTRACT(YEAR FROM NEW.dividend_payment_date);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_set_tax_year
    BEFORE INSERT ON tax_transactions
    FOR EACH ROW
    EXECUTE FUNCTION set_tax_year_from_sale_date();

-- =============================================================================
-- Grant Permissions
-- =============================================================================

-- Grant appropriate permissions to investment service user
-- GRANT SELECT, INSERT, UPDATE ON tax_documents TO investment_service;
-- GRANT SELECT, INSERT, UPDATE ON tax_transactions TO investment_service;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO investment_service;

-- =============================================================================
-- Validation Queries (for testing)
-- =============================================================================

-- Verify indexes exist:
-- SELECT tablename, indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename IN ('tax_documents', 'tax_transactions');

-- Check for orphaned tax transactions (no document reference):
-- SELECT COUNT(*) FROM tax_transactions WHERE reported_on_1099 = true AND tax_document_id IS NULL;

-- Verify wash sale detection logic:
-- SELECT * FROM tax_transactions WHERE is_wash_sale = true AND wash_sale_loss_disallowed IS NOT NULL;

-- =============================================================================
-- Migration Complete
-- =============================================================================
