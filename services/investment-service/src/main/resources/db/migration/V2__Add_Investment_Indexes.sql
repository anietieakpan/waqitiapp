-- Indexes for Investment Account
CREATE INDEX idx_investment_account_user_id ON investment_account(user_id);
CREATE INDEX idx_investment_account_status ON investment_account(status);
CREATE INDEX idx_investment_account_created_at ON investment_account(created_at);

-- Indexes for Portfolio
CREATE INDEX idx_portfolio_account_id ON portfolio(account_id);
CREATE INDEX idx_portfolio_updated ON portfolio(last_updated);

-- Indexes for Investment Holding
CREATE INDEX idx_investment_holding_portfolio_id ON investment_holding(portfolio_id);
CREATE INDEX idx_investment_holding_symbol ON investment_holding(symbol);
CREATE INDEX idx_investment_holding_updated ON investment_holding(last_updated);
CREATE INDEX idx_investment_holding_portfolio_symbol ON investment_holding(portfolio_id, symbol);

-- Indexes for Investment Order
CREATE INDEX idx_investment_order_account_id ON investment_order(account_id);
CREATE INDEX idx_investment_order_status ON investment_order(status);
CREATE INDEX idx_investment_order_symbol ON investment_order(symbol);
CREATE INDEX idx_investment_order_order_type ON investment_order(order_type);
CREATE INDEX idx_investment_order_submitted_at ON investment_order(submitted_at);
CREATE INDEX idx_investment_order_filled_at ON investment_order(filled_at);
CREATE INDEX idx_investment_order_account_status ON investment_order(account_id, status);
CREATE INDEX idx_investment_order_order_number ON investment_order(order_number);

-- Indexes for Auto Invest
CREATE INDEX idx_auto_invest_account_id ON auto_invest(account_id);
CREATE INDEX idx_auto_invest_status ON auto_invest(status);
CREATE INDEX idx_auto_invest_next_execution ON auto_invest(next_execution_date);
CREATE INDEX idx_auto_invest_status_execution ON auto_invest(status, next_execution_date);

-- Indexes for Auto Invest Allocation
CREATE INDEX idx_auto_invest_allocation_auto_invest_id ON auto_invest_allocation(auto_invest_id);
CREATE INDEX idx_auto_invest_allocation_symbol ON auto_invest_allocation(symbol);

-- Indexes for Watchlist Item
CREATE INDEX idx_watchlist_account_id ON watchlist_item(account_id);
CREATE INDEX idx_watchlist_symbol ON watchlist_item(symbol);
CREATE INDEX idx_watchlist_account_symbol ON watchlist_item(account_id, symbol);

-- Indexes for Transfer
CREATE INDEX idx_transfer_account_id ON transfer(account_id);
CREATE INDEX idx_transfer_status ON transfer(status);
CREATE INDEX idx_transfer_type ON transfer(type);
CREATE INDEX idx_transfer_created_at ON transfer(created_at);
CREATE INDEX idx_transfer_account_status ON transfer(account_id, status);

-- Performance optimization indexes for common queries
CREATE INDEX idx_investment_holding_market_value ON investment_holding(market_value);
CREATE INDEX idx_investment_order_composite ON investment_order(account_id, status, submitted_at DESC);
CREATE INDEX idx_auto_invest_active ON auto_invest(status, next_execution_date) WHERE status = 'ACTIVE';

-- Add comments
COMMENT ON INDEX idx_investment_account_user_id IS 'Index for finding accounts by user';
COMMENT ON INDEX idx_investment_order_composite IS 'Composite index for order history queries';
COMMENT ON INDEX idx_auto_invest_active IS 'Partial index for active auto-invest plans scheduled for execution';