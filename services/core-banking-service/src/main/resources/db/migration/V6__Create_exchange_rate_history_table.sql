-- Create exchange rate history table
CREATE TABLE exchange_rate_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(19,8) NOT NULL,
    mid_market_rate DECIMAL(19,8),
    bid_rate DECIMAL(19,8),
    ask_rate DECIMAL(19,8),
    rate_date TIMESTAMP NOT NULL,
    source VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create indexes for efficient querying
CREATE INDEX idx_exchange_history_currencies_date ON exchange_rate_history(from_currency, to_currency, rate_date);
CREATE INDEX idx_exchange_history_date ON exchange_rate_history(rate_date);
CREATE INDEX idx_exchange_history_source ON exchange_rate_history(source);

-- Add constraints
ALTER TABLE exchange_rate_history ADD CONSTRAINT chk_exchange_rate_positive CHECK (rate > 0);
ALTER TABLE exchange_rate_history ADD CONSTRAINT chk_exchange_currencies_different CHECK (from_currency != to_currency);
ALTER TABLE exchange_rate_history ADD CONSTRAINT chk_exchange_currency_format CHECK (
    from_currency ~ '^[A-Z]{3}$' AND to_currency ~ '^[A-Z]{3}$'
);

-- Create unique constraint to prevent duplicate rates for same currency pair and date
CREATE UNIQUE INDEX idx_exchange_history_unique_rate ON exchange_rate_history(
    from_currency, to_currency, date_trunc('hour', rate_date)
);

-- Insert some initial historical data for common currency pairs (last 30 days)
DO $$
DECLARE
    current_date TIMESTAMP;
    rate_value DECIMAL(19,8);
    day_counter INTEGER;
BEGIN
    current_date := NOW() - INTERVAL '30 days';
    
    -- USD/EUR historical rates (approximate)
    FOR day_counter IN 0..29 LOOP
        rate_value := 0.85 + (RANDOM() * 0.10); -- Random rate between 0.85 and 0.95
        
        INSERT INTO exchange_rate_history (
            from_currency, to_currency, rate, mid_market_rate, 
            rate_date, source, created_at
        ) VALUES (
            'USD', 'EUR', rate_value, rate_value,
            current_date + (day_counter * INTERVAL '1 day'), 
            'INITIAL_DATA', NOW()
        );
        
        -- Reverse rate
        INSERT INTO exchange_rate_history (
            from_currency, to_currency, rate, mid_market_rate,
            rate_date, source, created_at
        ) VALUES (
            'EUR', 'USD', 1/rate_value, 1/rate_value,
            current_date + (day_counter * INTERVAL '1 day'),
            'INITIAL_DATA', NOW()
        );
    END LOOP;
    
    -- USD/GBP historical rates
    current_date := NOW() - INTERVAL '30 days';
    FOR day_counter IN 0..29 LOOP
        rate_value := 0.75 + (RANDOM() * 0.10); -- Random rate between 0.75 and 0.85
        
        INSERT INTO exchange_rate_history (
            from_currency, to_currency, rate, mid_market_rate,
            rate_date, source, created_at
        ) VALUES (
            'USD', 'GBP', rate_value, rate_value,
            current_date + (day_counter * INTERVAL '1 day'),
            'INITIAL_DATA', NOW()
        );
        
        INSERT INTO exchange_rate_history (
            from_currency, to_currency, rate, mid_market_rate,
            rate_date, source, created_at
        ) VALUES (
            'GBP', 'USD', 1/rate_value, 1/rate_value,
            current_date + (day_counter * INTERVAL '1 day'),
            'INITIAL_DATA', NOW()
        );
    END LOOP;
END $$;