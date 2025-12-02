-- Add version and audit fields to investment service tables
-- Critical for preventing concurrent updates on investment portfolios and orders

-- ===================================
-- PORTFOLIOS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking (critical for portfolio value updates)
ALTER TABLE portfolios ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE portfolios ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE portfolios ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE portfolios ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE portfolios ADD COLUMN modified_by VARCHAR(255);

-- Create indexes for performance
CREATE INDEX idx_portfolios_version ON portfolios(version);
CREATE INDEX idx_portfolios_created_at ON portfolios(created_at);
CREATE INDEX idx_portfolios_updated_at ON portfolios(updated_at);

-- ===================================
-- INVESTMENT_ORDERS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE investment_orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE investment_orders ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE investment_orders ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE investment_orders ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE investment_orders ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_investment_orders_version ON investment_orders(version);
CREATE INDEX idx_investment_orders_created_at ON investment_orders(created_at);
CREATE INDEX idx_investment_orders_status_created_at ON investment_orders(status, created_at);

-- ===================================
-- INVESTMENT_HOLDINGS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE investment_holdings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE investment_holdings ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE investment_holdings ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE investment_holdings ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE investment_holdings ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_investment_holdings_version ON investment_holdings(version);
CREATE INDEX idx_investment_holdings_created_at ON investment_holdings(created_at);

-- ===================================
-- INVESTMENT_ACCOUNTS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE investment_accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE investment_accounts ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE investment_accounts ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE investment_accounts ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE investment_accounts ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_investment_accounts_version ON investment_accounts(version);
CREATE INDEX idx_investment_accounts_created_at ON investment_accounts(created_at);
CREATE INDEX idx_investment_accounts_user_id ON investment_accounts(user_id);

-- ===================================
-- AUTO_INVEST TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE auto_invest ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE auto_invest ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE auto_invest ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE auto_invest ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE auto_invest ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_auto_invest_version ON auto_invest(version);
CREATE INDEX idx_auto_invest_created_at ON auto_invest(created_at);
CREATE INDEX idx_auto_invest_user_active ON auto_invest(user_id, is_active);

-- ===================================
-- TRANSFERS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE transfers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE transfers ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE transfers ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE transfers ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE transfers ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_transfers_version ON transfers(version);
CREATE INDEX idx_transfers_created_at ON transfers(created_at);
CREATE INDEX idx_transfers_status_created_at ON transfers(status, created_at);

-- ===================================
-- AUTO_INVEST_ALLOCATIONS TABLE UPDATES
-- ===================================

-- Add version column for optimistic locking
ALTER TABLE auto_invest_allocations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add audit timestamp fields
ALTER TABLE auto_invest_allocations ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE auto_invest_allocations ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Add audit user tracking fields
ALTER TABLE auto_invest_allocations ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM';
ALTER TABLE auto_invest_allocations ADD COLUMN modified_by VARCHAR(255);

-- Create indexes
CREATE INDEX idx_auto_invest_allocations_version ON auto_invest_allocations(version);
CREATE INDEX idx_auto_invest_allocations_created_at ON auto_invest_allocations(created_at);

-- ===================================
-- ADD TABLE COMMENTS FOR DOCUMENTATION
-- ===================================

COMMENT ON COLUMN portfolios.version IS 'Version field for optimistic locking to prevent concurrent portfolio value updates';
COMMENT ON COLUMN investment_orders.version IS 'Version field for optimistic locking to prevent concurrent order updates';
COMMENT ON COLUMN investment_holdings.version IS 'Version field for optimistic locking to prevent concurrent holding updates';

-- ===================================
-- UPDATE EXISTING RECORDS
-- ===================================

-- Set initial values for existing records
UPDATE portfolios SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE investment_orders SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE investment_holdings SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE investment_accounts SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE auto_invest SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE transfers SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;
UPDATE auto_invest_allocations SET created_by = 'SYSTEM', version = 0 WHERE created_by IS NULL;

-- ===================================
-- CREATE TRIGGERS FOR UPDATED_AT FIELDS
-- ===================================

-- Create function if not exists
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to all investment tables
CREATE TRIGGER update_portfolios_updated_at BEFORE UPDATE ON portfolios 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_investment_orders_updated_at BEFORE UPDATE ON investment_orders 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_investment_holdings_updated_at BEFORE UPDATE ON investment_holdings 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_investment_accounts_updated_at BEFORE UPDATE ON investment_accounts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auto_invest_updated_at BEFORE UPDATE ON auto_invest 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_transfers_updated_at BEFORE UPDATE ON transfers 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_auto_invest_allocations_updated_at BEFORE UPDATE ON auto_invest_allocations 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===================================
-- CREATE ADDITIONAL PERFORMANCE INDEXES
-- ===================================

-- Portfolio performance indexes
CREATE INDEX idx_portfolios_account_value ON portfolios(investment_account_id, total_value);
CREATE INDEX idx_portfolios_return_performance ON portfolios(total_return DESC, updated_at DESC);

-- Order processing indexes  
CREATE INDEX idx_investment_orders_execution ON investment_orders(status, order_type, created_at);
CREATE INDEX idx_investment_orders_user_pending ON investment_orders(user_id, status) WHERE status IN ('PENDING', 'PARTIALLY_FILLED');

-- Holdings analysis indexes
CREATE INDEX idx_investment_holdings_portfolio_symbol ON investment_holdings(portfolio_id, symbol);
CREATE INDEX idx_investment_holdings_cost_basis ON investment_holdings(cost_basis, updated_at);

-- Auto-invest processing indexes
CREATE INDEX idx_auto_invest_next_execution ON auto_invest(next_execution_date) WHERE is_active = true;
CREATE INDEX idx_auto_invest_frequency ON auto_invest(frequency, is_active);

-- Transfer tracking indexes
CREATE INDEX idx_transfers_user_status ON transfers(user_id, status, created_at);
CREATE INDEX idx_transfers_amount_date ON transfers(amount DESC, created_at DESC);

-- Allocation optimization indexes
CREATE INDEX idx_auto_invest_allocations_symbol_allocation ON auto_invest_allocations(symbol, allocation_percentage);

-- ===================================
-- ADD CONSTRAINTS FOR DATA INTEGRITY
-- ===================================

-- Ensure portfolio percentages don't exceed 100%
-- (This would be implemented as a check constraint or trigger based on business rules)

-- Ensure positive values for financial amounts
ALTER TABLE portfolios ADD CONSTRAINT chk_portfolio_positive_values 
    CHECK (total_value >= 0 AND total_cost >= 0);

ALTER TABLE investment_orders ADD CONSTRAINT chk_order_positive_amounts 
    CHECK (quantity >= 0 AND (limit_price IS NULL OR limit_price >= 0));

ALTER TABLE investment_holdings ADD CONSTRAINT chk_holding_positive_values
    CHECK (quantity >= 0 AND cost_basis >= 0 AND current_value >= 0);

ALTER TABLE transfers ADD CONSTRAINT chk_transfer_positive_amount
    CHECK (amount > 0);