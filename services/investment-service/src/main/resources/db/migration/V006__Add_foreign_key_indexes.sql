-- Add Foreign Key Indexes for Performance
-- Service: investment-service
-- Date: 2025-10-18
-- Description: Add missing indexes on foreign key columns to prevent table scans
-- Impact: Significantly improves query performance and prevents deadlocks
-- Priority: HIGH - Production Performance Issue
--
-- Problem:
-- PostgreSQL does NOT automatically create indexes on foreign key columns.
-- Without these indexes:
-- - JOINs perform full table scans
-- - DELETE/UPDATE on parent table scans entire child table
-- - High risk of deadlocks under concurrent load
-- - Slow query performance at scale
--
-- Solution:
-- Create indexes on all foreign key columns
-- Performance improvement: 10-100x faster for FK constraint checks
--
-- Total Indexes: 6

CREATE INDEX IF NOT EXISTS idx_portfolios_user_id ON portfolios(user_id);
CREATE INDEX IF NOT EXISTS idx_holdings_portfolio_id ON holdings(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_orders_portfolio_id ON orders(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_orders_placed_by ON orders(placed_by);
CREATE INDEX IF NOT EXISTS idx_tax_documents_portfolio_id ON tax_documents(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_tax_documents_generated_for_user ON tax_documents(generated_for_user);

-- Index comments for documentation
COMMENT ON INDEX idx_portfolios_user_id IS 'Foreign key index for portfolios.user_id - Performance optimization';
COMMENT ON INDEX idx_holdings_portfolio_id IS 'Foreign key index for holdings.portfolio_id - Performance optimization';
COMMENT ON INDEX idx_orders_portfolio_id IS 'Foreign key index for orders.portfolio_id - Performance optimization';
COMMENT ON INDEX idx_orders_placed_by IS 'Foreign key index for orders.placed_by - Performance optimization';
COMMENT ON INDEX idx_tax_documents_portfolio_id IS 'Foreign key index for tax_documents.portfolio_id - Performance optimization';
COMMENT ON INDEX idx_tax_documents_generated_for_user IS 'Foreign key index for tax_documents.generated_for_user - Performance optimization';
