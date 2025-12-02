-- Currency Service Initial Schema
-- Created: 2025-09-27
-- Description: Currency management, exchange rates, and conversion schema

CREATE TABLE IF NOT EXISTS currency (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    currency_code VARCHAR(3) UNIQUE NOT NULL,
    currency_name VARCHAR(100) NOT NULL,
    currency_symbol VARCHAR(10),
    decimal_places INTEGER NOT NULL DEFAULT 2,
    is_crypto BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    is_base_currency BOOLEAN DEFAULT FALSE,
    country_code VARCHAR(3),
    central_bank VARCHAR(255),
    iso_numeric_code VARCHAR(3),
    minor_unit_name VARCHAR(50),
    major_unit_name VARCHAR(50),
    display_format VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exchange_rate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_id VARCHAR(100) UNIQUE NOT NULL,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    rate_type VARCHAR(20) NOT NULL,
    bid_rate DECIMAL(18, 8),
    ask_rate DECIMAL(18, 8),
    mid_rate DECIMAL(18, 8),
    spread DECIMAL(10, 6),
    rate_date TIMESTAMP NOT NULL,
    valid_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_to TIMESTAMP,
    source VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(100),
    is_current BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exchange_rate_from FOREIGN KEY (from_currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_exchange_rate_to FOREIGN KEY (to_currency) REFERENCES currency(currency_code)
);

CREATE TABLE IF NOT EXISTS currency_conversion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversion_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    from_amount DECIMAL(18, 2) NOT NULL,
    to_amount DECIMAL(18, 2) NOT NULL,
    exchange_rate DECIMAL(18, 8) NOT NULL,
    rate_id VARCHAR(100),
    conversion_fee DECIMAL(15, 2) DEFAULT 0,
    fee_currency VARCHAR(3),
    net_amount DECIMAL(18, 2) NOT NULL,
    conversion_type VARCHAR(50) NOT NULL,
    conversion_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settlement_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reference_number VARCHAR(100),
    transaction_id VARCHAR(100),
    account_debited VARCHAR(100),
    account_credited VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversion_from FOREIGN KEY (from_currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_conversion_to FOREIGN KEY (to_currency) REFERENCES currency(currency_code)
);

CREATE TABLE IF NOT EXISTS rate_provider (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id VARCHAR(100) UNIQUE NOT NULL,
    provider_name VARCHAR(255) NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    description TEXT,
    api_endpoint VARCHAR(500),
    api_key_encrypted VARCHAR(255),
    update_frequency_minutes INTEGER NOT NULL DEFAULT 60,
    supported_currencies TEXT[],
    priority INTEGER DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    last_update_at TIMESTAMP,
    success_rate DECIMAL(5, 4),
    average_response_time_ms INTEGER,
    error_count INTEGER DEFAULT 0,
    configuration JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rate_feed_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id VARCHAR(100) UNIQUE NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    feed_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    rates_updated INTEGER DEFAULT 0,
    rates_failed INTEGER DEFAULT 0,
    response_time_ms INTEGER,
    error_message TEXT,
    raw_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rate_feed_provider FOREIGN KEY (provider_id) REFERENCES rate_provider(provider_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS currency_pair (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pair_id VARCHAR(100) UNIQUE NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    pair_name VARCHAR(10) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_tradable BOOLEAN DEFAULT TRUE,
    min_conversion_amount DECIMAL(18, 2),
    max_conversion_amount DECIMAL(18, 2),
    spread_markup DECIMAL(5, 4) DEFAULT 0,
    conversion_fee_rate DECIMAL(5, 4) DEFAULT 0,
    daily_volume DECIMAL(18, 2) DEFAULT 0,
    volatility DECIMAL(5, 4),
    last_trade_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_currency_pair_base FOREIGN KEY (base_currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_currency_pair_quote FOREIGN KEY (quote_currency) REFERENCES currency(currency_code),
    CONSTRAINT unique_currency_pair UNIQUE (base_currency, quote_currency)
);

CREATE TABLE IF NOT EXISTS rate_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    rate_date DATE NOT NULL,
    open_rate DECIMAL(18, 8),
    high_rate DECIMAL(18, 8),
    low_rate DECIMAL(18, 8),
    close_rate DECIMAL(18, 8),
    volume DECIMAL(18, 2),
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rate_history_from FOREIGN KEY (from_currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_rate_history_to FOREIGN KEY (to_currency) REFERENCES currency(currency_code),
    CONSTRAINT unique_rate_history_date UNIQUE (from_currency, to_currency, rate_date, source)
);

CREATE TABLE IF NOT EXISTS currency_alert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    alert_type VARCHAR(20) NOT NULL,
    target_rate DECIMAL(18, 8) NOT NULL,
    current_rate DECIMAL(18, 8),
    condition_type VARCHAR(20) NOT NULL,
    notification_method VARCHAR(20) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    triggered BOOLEAN DEFAULT FALSE,
    triggered_at TIMESTAMP,
    trigger_rate DECIMAL(18, 8),
    notification_sent BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_currency_alert_from FOREIGN KEY (from_currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_currency_alert_to FOREIGN KEY (to_currency) REFERENCES currency(currency_code)
);

CREATE TABLE IF NOT EXISTS currency_hedge (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hedge_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    hedge_type VARCHAR(50) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    hedge_currency VARCHAR(3) NOT NULL,
    notional_amount DECIMAL(18, 2) NOT NULL,
    hedge_ratio DECIMAL(5, 4) NOT NULL,
    strike_rate DECIMAL(18, 8),
    premium_paid DECIMAL(15, 2),
    premium_currency VARCHAR(3),
    start_date DATE NOT NULL,
    maturity_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    market_value DECIMAL(18, 2),
    unrealized_pnl DECIMAL(18, 2),
    settlement_method VARCHAR(20),
    counterparty VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_currency_hedge_base FOREIGN KEY (base_currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_currency_hedge_hedge FOREIGN KEY (hedge_currency) REFERENCES currency(currency_code)
);

CREATE TABLE IF NOT EXISTS currency_exposure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exposure_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID,
    portfolio_id VARCHAR(100),
    currency VARCHAR(3) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    exposure_amount DECIMAL(18, 2) NOT NULL,
    exposure_type VARCHAR(50) NOT NULL,
    source VARCHAR(100) NOT NULL,
    valuation_date DATE NOT NULL,
    market_value_base DECIMAL(18, 2) NOT NULL,
    exchange_rate_used DECIMAL(18, 8) NOT NULL,
    sensitivity_1pct DECIMAL(18, 2),
    var_1day DECIMAL(18, 2),
    is_hedged BOOLEAN DEFAULT FALSE,
    hedge_ratio DECIMAL(5, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_currency_exposure_currency FOREIGN KEY (currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_currency_exposure_base FOREIGN KEY (base_currency) REFERENCES currency(currency_code)
);

CREATE TABLE IF NOT EXISTS currency_forward (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forward_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id UUID NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    quote_currency VARCHAR(3) NOT NULL,
    notional_amount DECIMAL(18, 2) NOT NULL,
    forward_rate DECIMAL(18, 8) NOT NULL,
    spot_rate DECIMAL(18, 8) NOT NULL,
    forward_points DECIMAL(18, 8) NOT NULL,
    value_date DATE NOT NULL,
    trade_date DATE NOT NULL,
    maturity_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    settlement_method VARCHAR(20) NOT NULL,
    market_value DECIMAL(18, 2),
    unrealized_pnl DECIMAL(18, 2),
    counterparty VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_currency_forward_base FOREIGN KEY (base_currency) REFERENCES currency(currency_code),
    CONSTRAINT fk_currency_forward_quote FOREIGN KEY (quote_currency) REFERENCES currency(currency_code)
);

CREATE TABLE IF NOT EXISTS currency_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_conversions INTEGER NOT NULL DEFAULT 0,
    total_conversion_volume DECIMAL(18, 2) DEFAULT 0,
    base_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    avg_conversion_amount DECIMAL(18, 2),
    top_currency_pairs JSONB,
    total_fees_collected DECIMAL(18, 2) DEFAULT 0,
    by_currency JSONB,
    by_customer_type JSONB,
    volatility_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_currency_period UNIQUE (period_start, period_end, base_currency)
);

-- Insert major currencies
INSERT INTO currency (currency_code, currency_name, currency_symbol, decimal_places, country_code, major_unit_name, minor_unit_name) VALUES
    ('USD', 'US Dollar', '$', 2, 'US', 'dollar', 'cent'),
    ('EUR', 'Euro', '€', 2, 'EU', 'euro', 'cent'),
    ('GBP', 'British Pound', '£', 2, 'GB', 'pound', 'penny'),
    ('JPY', 'Japanese Yen', '¥', 0, 'JP', 'yen', 'sen'),
    ('CAD', 'Canadian Dollar', 'C$', 2, 'CA', 'dollar', 'cent'),
    ('AUD', 'Australian Dollar', 'A$', 2, 'AU', 'dollar', 'cent'),
    ('CHF', 'Swiss Franc', 'CHF', 2, 'CH', 'franc', 'centime'),
    ('CNY', 'Chinese Yuan', '¥', 2, 'CN', 'yuan', 'fen'),
    ('INR', 'Indian Rupee', '₹', 2, 'IN', 'rupee', 'paisa'),
    ('BRL', 'Brazilian Real', 'R$', 2, 'BR', 'real', 'centavo')
ON CONFLICT (currency_code) DO NOTHING;

-- Set USD as base currency
UPDATE currency SET is_base_currency = true WHERE currency_code = 'USD';

-- Indexes for performance
CREATE INDEX idx_currency_code ON currency(currency_code);
CREATE INDEX idx_currency_active ON currency(is_active) WHERE is_active = true;
CREATE INDEX idx_currency_crypto ON currency(is_crypto);

CREATE INDEX idx_exchange_rate_pair ON exchange_rate(from_currency, to_currency);
CREATE INDEX idx_exchange_rate_current ON exchange_rate(is_current) WHERE is_current = true;
CREATE INDEX idx_exchange_rate_date ON exchange_rate(rate_date DESC);
CREATE INDEX idx_exchange_rate_valid ON exchange_rate(valid_from, valid_to);

CREATE INDEX idx_currency_conversion_customer ON currency_conversion(customer_id);
CREATE INDEX idx_currency_conversion_pair ON currency_conversion(from_currency, to_currency);
CREATE INDEX idx_currency_conversion_date ON currency_conversion(conversion_date DESC);
CREATE INDEX idx_currency_conversion_status ON currency_conversion(status);

CREATE INDEX idx_rate_provider_active ON rate_provider(is_active) WHERE is_active = true;
CREATE INDEX idx_rate_provider_priority ON rate_provider(priority);

CREATE INDEX idx_rate_feed_log_provider ON rate_feed_log(provider_id);
CREATE INDEX idx_rate_feed_log_timestamp ON rate_feed_log(feed_timestamp DESC);
CREATE INDEX idx_rate_feed_log_status ON rate_feed_log(status);

CREATE INDEX idx_currency_pair_base ON currency_pair(base_currency);
CREATE INDEX idx_currency_pair_quote ON currency_pair(quote_currency);
CREATE INDEX idx_currency_pair_active ON currency_pair(is_active) WHERE is_active = true;

CREATE INDEX idx_rate_history_pair ON rate_history(from_currency, to_currency);
CREATE INDEX idx_rate_history_date ON rate_history(rate_date DESC);

CREATE INDEX idx_currency_alert_customer ON currency_alert(customer_id);
CREATE INDEX idx_currency_alert_pair ON currency_alert(from_currency, to_currency);
CREATE INDEX idx_currency_alert_active ON currency_alert(is_active) WHERE is_active = true;

CREATE INDEX idx_currency_hedge_customer ON currency_hedge(customer_id);
CREATE INDEX idx_currency_hedge_pair ON currency_hedge(base_currency, hedge_currency);
CREATE INDEX idx_currency_hedge_status ON currency_hedge(status);
CREATE INDEX idx_currency_hedge_maturity ON currency_hedge(maturity_date);

CREATE INDEX idx_currency_exposure_customer ON currency_exposure(customer_id);
CREATE INDEX idx_currency_exposure_currency ON currency_exposure(currency);
CREATE INDEX idx_currency_exposure_date ON currency_exposure(valuation_date DESC);

CREATE INDEX idx_currency_forward_customer ON currency_forward(customer_id);
CREATE INDEX idx_currency_forward_pair ON currency_forward(base_currency, quote_currency);
CREATE INDEX idx_currency_forward_status ON currency_forward(status);
CREATE INDEX idx_currency_forward_maturity ON currency_forward(maturity_date);

CREATE INDEX idx_currency_statistics_period ON currency_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_currency_updated_at BEFORE UPDATE ON currency
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_currency_conversion_updated_at BEFORE UPDATE ON currency_conversion
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_rate_provider_updated_at BEFORE UPDATE ON rate_provider
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_currency_pair_updated_at BEFORE UPDATE ON currency_pair
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_currency_alert_updated_at BEFORE UPDATE ON currency_alert
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_currency_hedge_updated_at BEFORE UPDATE ON currency_hedge
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_currency_exposure_updated_at BEFORE UPDATE ON currency_exposure
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_currency_forward_updated_at BEFORE UPDATE ON currency_forward
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();