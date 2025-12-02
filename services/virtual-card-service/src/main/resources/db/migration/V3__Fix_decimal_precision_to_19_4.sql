-- Fix DECIMAL(15,2) to DECIMAL(19,4) for all financial amounts in Virtual Card service
-- Priority: HIGH - Critical for card transaction accuracy and spending limit precision
-- Impact: Prevents precision loss in card transactions and limit calculations

-- card_programs table - Fix all limit and fee fields
ALTER TABLE card_programs
    ALTER COLUMN default_spending_limit TYPE DECIMAL(19,4),
    ALTER COLUMN max_spending_limit TYPE DECIMAL(19,4),
    ALTER COLUMN virtual_card_fee TYPE DECIMAL(19,4),
    ALTER COLUMN physical_card_fee TYPE DECIMAL(19,4),
    ALTER COLUMN replacement_fee TYPE DECIMAL(19,4);

-- cards table - Fix all limit and spending fields
ALTER TABLE cards
    ALTER COLUMN spending_limit TYPE DECIMAL(19,4),
    ALTER COLUMN daily_limit TYPE DECIMAL(19,4),
    ALTER COLUMN monthly_limit TYPE DECIMAL(19,4),
    ALTER COLUMN total_spent TYPE DECIMAL(19,4);

-- card_controls table - Fix per-transaction limit
ALTER TABLE card_controls
    ALTER COLUMN per_transaction_limit TYPE DECIMAL(19,4);

-- card_transactions table - Fix all amount and fee fields
ALTER TABLE card_transactions
    ALTER COLUMN amount TYPE DECIMAL(19,4),
    ALTER COLUMN interchange_fee TYPE DECIMAL(19,4),
    ALTER COLUMN foreign_exchange_fee TYPE DECIMAL(19,4),
    ALTER COLUMN original_amount TYPE DECIMAL(19,4);

-- card_statements table - Fix all balance and summary fields
ALTER TABLE card_statements
    ALTER COLUMN opening_balance TYPE DECIMAL(19,4),
    ALTER COLUMN closing_balance TYPE DECIMAL(19,4),
    ALTER COLUMN total_purchases TYPE DECIMAL(19,4),
    ALTER COLUMN total_refunds TYPE DECIMAL(19,4),
    ALTER COLUMN total_fees TYPE DECIMAL(19,4);

-- Add comments for documentation
COMMENT ON COLUMN card_programs.default_spending_limit IS 'Default spending limit with 4 decimal precision for accurate limit enforcement';
COMMENT ON COLUMN cards.spending_limit IS 'Card spending limit with 4 decimal precision to prevent precision loss';
COMMENT ON COLUMN card_transactions.amount IS 'Transaction amount with 4 decimal precision for accurate settlement';
COMMENT ON COLUMN card_transactions.interchange_fee IS 'Interchange fee with 4 decimal precision for accurate fee calculation';
COMMENT ON COLUMN card_statements.closing_balance IS 'Closing balance with 4 decimal precision for accurate statement reconciliation';

-- Analyze tables to update statistics
ANALYZE card_programs;
ANALYZE cards;
ANALYZE card_controls;
ANALYZE card_transactions;
ANALYZE card_statements;
