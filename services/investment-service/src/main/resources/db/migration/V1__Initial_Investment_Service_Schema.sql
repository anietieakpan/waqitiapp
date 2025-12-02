-- Investment Service Database Schema
-- Initial schema for investment accounts, portfolios, orders, holdings, and auto-invest

-- Create investment accounts table
CREATE TABLE investment_accounts (
    id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    wallet_account_id VARCHAR(255) NOT NULL,
    cash_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    invested_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_change DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_change_percent DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_return DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_return_percent DECIMAL(19,4) NOT NULL DEFAULT 0,
    realized_gains DECIMAL(19,4) NOT NULL DEFAULT 0,
    unrealized_gains DECIMAL(19,4) NOT NULL DEFAULT 0,
    dividend_earnings DECIMAL(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    kyc_verified BOOLEAN NOT NULL DEFAULT FALSE,
    pattern_day_trader BOOLEAN NOT NULL DEFAULT FALSE,
    day_trades INTEGER NOT NULL DEFAULT 0,
    risk_profile VARCHAR(50),
    investment_goals TEXT,
    risk_tolerance DECIMAL(19,4),
    brokerage_account_id VARCHAR(255),
    brokerage_provider VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    metadata TEXT,
    CONSTRAINT chk_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED', 'CLOSED', 'RESTRICTED', 'UNDER_REVIEW'))
);

-- Create indexes for investment_accounts
CREATE INDEX idx_investment_accounts_customer_id ON investment_accounts(customer_id);
CREATE INDEX idx_investment_accounts_status ON investment_accounts(status);
CREATE INDEX idx_investment_accounts_brokerage_provider ON investment_accounts(brokerage_provider);
CREATE INDEX idx_investment_accounts_created_at ON investment_accounts(created_at);

-- Create portfolios table
CREATE TABLE portfolios (
    id VARCHAR(255) PRIMARY KEY,
    investment_account_id VARCHAR(255) NOT NULL UNIQUE,
    total_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_cost DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_return DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_return_percent DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_change DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_change_percent DECIMAL(19,4) NOT NULL DEFAULT 0,
    realized_gains DECIMAL(19,4) NOT NULL DEFAULT 0,
    unrealized_gains DECIMAL(19,4) NOT NULL DEFAULT 0,
    dividend_earnings DECIMAL(19,4) NOT NULL DEFAULT 0,
    number_of_positions INTEGER NOT NULL DEFAULT 0,
    cash_percentage DECIMAL(19,4) NOT NULL DEFAULT 0,
    equity_percentage DECIMAL(19,4) NOT NULL DEFAULT 0,
    etf_percentage DECIMAL(19,4) NOT NULL DEFAULT 0,
    crypto_percentage DECIMAL(19,4) NOT NULL DEFAULT 0,
    diversification_score DECIMAL(19,4) NOT NULL DEFAULT 0,
    risk_score DECIMAL(19,4) NOT NULL DEFAULT 0,
    volatility DECIMAL(19,4) NOT NULL DEFAULT 0,
    sharpe_ratio DECIMAL(19,4) NOT NULL DEFAULT 0,
    beta DECIMAL(19,4) NOT NULL DEFAULT 0,
    alpha DECIMAL(19,4) NOT NULL DEFAULT 0,
    top_performer VARCHAR(50),
    worst_performer VARCHAR(50),
    top_performer_return DECIMAL(19,4),
    worst_performer_return DECIMAL(19,4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_rebalanced_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    metadata TEXT,
    CONSTRAINT fk_portfolio_account FOREIGN KEY (investment_account_id) REFERENCES investment_accounts(id)
);

-- Create indexes for portfolios
CREATE INDEX idx_portfolios_investment_account_id ON portfolios(investment_account_id);
CREATE INDEX idx_portfolios_total_value ON portfolios(total_value);
CREATE INDEX idx_portfolios_updated_at ON portfolios(updated_at);

-- Create investment orders table
CREATE TABLE investment_orders (
    id VARCHAR(255) PRIMARY KEY,
    investment_account_id VARCHAR(255) NOT NULL,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    symbol VARCHAR(20) NOT NULL,
    instrument_type VARCHAR(50) NOT NULL,
    side VARCHAR(20) NOT NULL,
    order_type VARCHAR(50) NOT NULL,
    time_in_force VARCHAR(20) NOT NULL,
    quantity DECIMAL(19,8) NOT NULL,
    limit_price DECIMAL(19,4),
    stop_price DECIMAL(19,4),
    executed_quantity DECIMAL(19,8) NOT NULL DEFAULT 0,
    executed_price DECIMAL(19,4),
    average_price DECIMAL(19,4),
    order_amount DECIMAL(19,4) NOT NULL,
    commission DECIMAL(19,4) NOT NULL DEFAULT 0,
    fees DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_cost DECIMAL(19,4),
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    brokerage_order_id VARCHAR(255),
    brokerage_provider VARCHAR(100),
    reject_reason TEXT,
    notes TEXT,
    is_day_trade BOOLEAN NOT NULL DEFAULT FALSE,
    parent_order_id VARCHAR(255),
    linked_order_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    filled_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    expired_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    metadata TEXT,
    CONSTRAINT fk_order_account FOREIGN KEY (investment_account_id) REFERENCES investment_accounts(id),
    CONSTRAINT chk_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('MARKET', 'LIMIT', 'STOP', 'STOP_LIMIT', 'TRAILING_STOP', 'MOC', 'LOC')),
    CONSTRAINT chk_time_in_force CHECK (time_in_force IN ('DAY', 'GTC', 'GTD', 'IOC', 'FOK', 'MOO', 'MOC')),
    CONSTRAINT chk_status CHECK (status IN ('NEW', 'PENDING_SUBMIT', 'ACCEPTED', 'PENDING_CANCEL', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'EXPIRED', 'REJECTED', 'FAILED'))
);

-- Create indexes for investment_orders
CREATE INDEX idx_investment_orders_account_id ON investment_orders(investment_account_id);
CREATE INDEX idx_investment_orders_symbol ON investment_orders(symbol);
CREATE INDEX idx_investment_orders_status ON investment_orders(status);
CREATE INDEX idx_investment_orders_created_at ON investment_orders(created_at);
CREATE INDEX idx_investment_orders_brokerage_order_id ON investment_orders(brokerage_order_id);
CREATE INDEX idx_investment_orders_parent_order_id ON investment_orders(parent_order_id);

-- Create investment holdings table
CREATE TABLE investment_holdings (
    id VARCHAR(255) PRIMARY KEY,
    investment_account_id VARCHAR(255) NOT NULL,
    portfolio_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    instrument_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    quantity DECIMAL(19,8) NOT NULL DEFAULT 0,
    average_cost DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_cost DECIMAL(19,4) NOT NULL DEFAULT 0,
    current_price DECIMAL(19,4) NOT NULL DEFAULT 0,
    market_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_change DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_change_percent DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_return DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_return_percent DECIMAL(19,4) NOT NULL DEFAULT 0,
    realized_gains DECIMAL(19,4) NOT NULL DEFAULT 0,
    unrealized_gains DECIMAL(19,4) NOT NULL DEFAULT 0,
    dividend_earnings DECIMAL(19,4) NOT NULL DEFAULT 0,
    portfolio_percentage DECIMAL(19,4) NOT NULL DEFAULT 0,
    previous_close DECIMAL(19,4),
    day_low DECIMAL(19,4),
    day_high DECIMAL(19,4),
    fifty_two_week_low DECIMAL(19,4),
    fifty_two_week_high DECIMAL(19,4),
    volume BIGINT,
    average_volume BIGINT,
    market_cap DECIMAL(19,2),
    pe_ratio DECIMAL(19,4),
    dividend_yield DECIMAL(19,4),
    beta DECIMAL(19,4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    first_purchase_date TIMESTAMP,
    last_purchase_date TIMESTAMP,
    last_price_update TIMESTAMP,
    version BIGINT DEFAULT 0,
    metadata TEXT,
    CONSTRAINT fk_holding_account FOREIGN KEY (investment_account_id) REFERENCES investment_accounts(id),
    CONSTRAINT fk_holding_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
    CONSTRAINT uk_account_symbol UNIQUE (investment_account_id, symbol)
);

-- Create indexes for investment_holdings
CREATE INDEX idx_investment_holdings_account_id ON investment_holdings(investment_account_id);
CREATE INDEX idx_investment_holdings_portfolio_id ON investment_holdings(portfolio_id);
CREATE INDEX idx_investment_holdings_symbol ON investment_holdings(symbol);
CREATE INDEX idx_investment_holdings_market_value ON investment_holdings(market_value);
CREATE INDEX idx_investment_holdings_updated_at ON investment_holdings(updated_at);

-- Create watchlist items table
CREATE TABLE watchlist_items (
    id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    instrument_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    exchange VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    current_price DECIMAL(19,4) NOT NULL DEFAULT 0,
    previous_close DECIMAL(19,4),
    day_change DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_change_percent DECIMAL(19,4) NOT NULL DEFAULT 0,
    day_low DECIMAL(19,4),
    day_high DECIMAL(19,4),
    fifty_two_week_low DECIMAL(19,4),
    fifty_two_week_high DECIMAL(19,4),
    volume BIGINT,
    average_volume BIGINT,
    market_cap DECIMAL(19,2),
    pe_ratio DECIMAL(19,4),
    dividend_yield DECIMAL(19,4),
    beta DECIMAL(19,4),
    target_price DECIMAL(19,4),
    notes TEXT,
    alerts_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    price_alert_above DECIMAL(19,4),
    price_alert_below DECIMAL(19,4),
    percent_change_alert DECIMAL(19,4),
    volume_alert BOOLEAN DEFAULT FALSE,
    volume_alert_threshold DECIMAL(19,4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_price_update TIMESTAMP,
    last_alert_sent TIMESTAMP,
    version BIGINT DEFAULT 0,
    metadata TEXT,
    CONSTRAINT uk_customer_symbol UNIQUE (customer_id, symbol)
);

-- Create indexes for watchlist_items
CREATE INDEX idx_watchlist_customer_id ON watchlist_items(customer_id);
CREATE INDEX idx_watchlist_symbol ON watchlist_items(symbol);
CREATE INDEX idx_watchlist_alerts_enabled ON watchlist_items(alerts_enabled);
CREATE INDEX idx_watchlist_updated_at ON watchlist_items(updated_at);

-- Create auto invest table
CREATE TABLE auto_invest (
    id VARCHAR(255) PRIMARY KEY,
    investment_account_id VARCHAR(255) NOT NULL,
    plan_name VARCHAR(100) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    day_of_month INTEGER DEFAULT 1,
    day_of_week INTEGER,
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    rebalance_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    rebalance_threshold DECIMAL(19,4) DEFAULT 5.0,
    fractional_shares_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    total_invested DECIMAL(19,4) NOT NULL DEFAULT 0,
    execution_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    last_execution_date TIMESTAMP,
    next_execution_date TIMESTAMP,
    last_execution_status VARCHAR(50),
    last_execution_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    metadata TEXT,
    CONSTRAINT chk_frequency CHECK (frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY')),
    CONSTRAINT chk_ai_status CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED', 'COMPLETED', 'PENDING'))
);

-- Create indexes for auto_invest
CREATE INDEX idx_auto_invest_account_id ON auto_invest(investment_account_id);
CREATE INDEX idx_auto_invest_status ON auto_invest(status);
CREATE INDEX idx_auto_invest_next_execution ON auto_invest(next_execution_date);

-- Create auto invest allocations table
CREATE TABLE auto_invest_allocations (
    id VARCHAR(255) PRIMARY KEY,
    auto_invest_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    instrument_type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    percentage DECIMAL(19,4) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    min_investment DECIMAL(19,4),
    max_investment DECIMAL(19,4),
    notes TEXT,
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_allocation_auto_invest FOREIGN KEY (auto_invest_id) REFERENCES auto_invest(id) ON DELETE CASCADE
);

-- Create indexes for auto_invest_allocations
CREATE INDEX idx_auto_invest_allocations_auto_invest_id ON auto_invest_allocations(auto_invest_id);
CREATE INDEX idx_auto_invest_allocations_symbol ON auto_invest_allocations(symbol);

-- Create investment transfers table
CREATE TABLE investment_transfers (
    id VARCHAR(255) PRIMARY KEY,
    investment_account_id VARCHAR(255) NOT NULL,
    transfer_number VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    from_account_id VARCHAR(255) NOT NULL,
    to_account_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    wallet_transaction_id VARCHAR(255),
    brokerage_transfer_id VARCHAR(255),
    description TEXT,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    version BIGINT DEFAULT 0,
    metadata TEXT,
    CONSTRAINT fk_transfer_account FOREIGN KEY (investment_account_id) REFERENCES investment_accounts(id),
    CONSTRAINT chk_transfer_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'DIVIDEND', 'FEE', 'INTEREST')),
    CONSTRAINT chk_transfer_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REVERSED'))
);

-- Create indexes for investment_transfers
CREATE INDEX idx_investment_transfers_account_id ON investment_transfers(investment_account_id);
CREATE INDEX idx_investment_transfers_status ON investment_transfers(status);
CREATE INDEX idx_investment_transfers_type ON investment_transfers(type);
CREATE INDEX idx_investment_transfers_created_at ON investment_transfers(created_at);

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Add triggers for updated_at columns
CREATE TRIGGER update_investment_accounts_updated_at BEFORE UPDATE ON investment_accounts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_portfolios_updated_at BEFORE UPDATE ON portfolios FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_investment_orders_updated_at BEFORE UPDATE ON investment_orders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_investment_holdings_updated_at BEFORE UPDATE ON investment_holdings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_watchlist_items_updated_at BEFORE UPDATE ON watchlist_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_auto_invest_updated_at BEFORE UPDATE ON auto_invest FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_investment_transfers_updated_at BEFORE UPDATE ON investment_transfers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add comments to tables
COMMENT ON TABLE investment_accounts IS 'Investment brokerage accounts for customers';
COMMENT ON TABLE portfolios IS 'Investment portfolios containing holdings and performance metrics';
COMMENT ON TABLE investment_orders IS 'Buy and sell orders for investments';
COMMENT ON TABLE investment_holdings IS 'Current investment positions and holdings';
COMMENT ON TABLE watchlist_items IS 'Customer watchlists for tracking investments';
COMMENT ON TABLE auto_invest IS 'Automatic investment plans for dollar-cost averaging';
COMMENT ON TABLE auto_invest_allocations IS 'Asset allocations for auto-invest plans';
COMMENT ON TABLE investment_transfers IS 'Deposits and withdrawals between wallet and investment accounts';