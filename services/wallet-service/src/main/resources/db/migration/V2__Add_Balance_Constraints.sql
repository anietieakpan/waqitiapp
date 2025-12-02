-- Add balance integrity constraints to prevent data corruption
-- These constraints ensure financial data consistency and prevent negative balances

-- Add constraint to ensure available balance doesn't exceed total balance
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_balance_consistency 
CHECK (available_balance <= balance);

-- Add constraint to ensure reserved balance is non-negative
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_reserved_balance_positive 
CHECK (reserved_balance >= 0);

-- Add constraint to ensure pending balance is non-negative  
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_pending_balance_positive 
CHECK (pending_balance >= 0);

-- Add constraint to ensure total balance equation is valid
-- total_balance = available_balance + reserved_balance + pending_balance
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_total_balance_equation 
CHECK (balance = available_balance + reserved_balance + pending_balance);

-- Add constraint to prevent negative balances unless credit limit allows it
ALTER TABLE wallets ADD CONSTRAINT chk_wallet_overdraft_limit 
CHECK (
    credit_limit IS NULL OR 
    available_balance >= (credit_limit * -1)
);

-- Add similar constraints for account balances
ALTER TABLE accounts ADD CONSTRAINT chk_account_balance_consistency 
CHECK (available_balance <= current_balance OR credit_limit IS NOT NULL);

-- Ensure account balances respect credit limits
ALTER TABLE accounts ADD CONSTRAINT chk_account_credit_limit 
CHECK (
    credit_limit IS NULL OR 
    (current_balance + credit_limit) >= 0
);

-- Add constraint for transaction amounts
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_amount_positive 
CHECK (amount > 0);

-- Create function to validate balance updates atomically
CREATE OR REPLACE FUNCTION validate_balance_update()
RETURNS TRIGGER AS $$
BEGIN
    -- Validate wallet balance consistency
    IF TG_TABLE_NAME = 'wallets' THEN
        IF NEW.available_balance > NEW.balance THEN
            RAISE EXCEPTION 'Available balance (%) cannot exceed total balance (%)', 
                NEW.available_balance, NEW.balance;
        END IF;
        
        IF NEW.available_balance < 0 AND (NEW.credit_limit IS NULL OR NEW.available_balance < (NEW.credit_limit * -1)) THEN
            RAISE EXCEPTION 'Available balance (%) exceeds credit limit (%)', 
                NEW.available_balance, COALESCE(NEW.credit_limit, 0);
        END IF;
    END IF;
    
    -- Validate account balance consistency
    IF TG_TABLE_NAME = 'accounts' THEN
        IF NEW.current_balance + COALESCE(NEW.credit_limit, 0) < 0 THEN
            RAISE EXCEPTION 'Current balance (%) with credit limit (%) cannot be negative', 
                NEW.current_balance, COALESCE(NEW.credit_limit, 0);
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for balance validation
CREATE TRIGGER wallet_balance_validation_trigger
    BEFORE INSERT OR UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION validate_balance_update();

CREATE TRIGGER account_balance_validation_trigger
    BEFORE INSERT OR UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION validate_balance_update();

-- Create atomic balance update function for wallets
CREATE OR REPLACE FUNCTION atomic_wallet_balance_update(
    p_wallet_id UUID,
    p_debit_amount DECIMAL(19,4) DEFAULT 0,
    p_credit_amount DECIMAL(19,4) DEFAULT 0,
    p_reserve_amount DECIMAL(19,4) DEFAULT 0,
    p_release_amount DECIMAL(19,4) DEFAULT 0
) RETURNS BOOLEAN AS $$
DECLARE
    current_wallet RECORD;
    new_balance DECIMAL(19,4);
    new_available DECIMAL(19,4);
    new_reserved DECIMAL(19,4);
BEGIN
    -- Get current wallet state with row lock
    SELECT * INTO current_wallet 
    FROM wallets 
    WHERE id = p_wallet_id 
    FOR UPDATE;
    
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Wallet not found: %', p_wallet_id;
    END IF;
    
    -- Calculate new balances
    new_balance := current_wallet.balance - p_debit_amount + p_credit_amount;
    new_available := current_wallet.available_balance - p_debit_amount + p_credit_amount - p_reserve_amount + p_release_amount;
    new_reserved := current_wallet.reserved_balance + p_reserve_amount - p_release_amount;
    
    -- Validate overdraft limits
    IF new_available < 0 AND (current_wallet.credit_limit IS NULL OR new_available < (current_wallet.credit_limit * -1)) THEN
        RAISE EXCEPTION 'Insufficient funds. Available: %, Credit Limit: %', 
            new_available, COALESCE(current_wallet.credit_limit, 0);
    END IF;
    
    -- Update wallet atomically
    UPDATE wallets SET
        balance = new_balance,
        available_balance = new_available,
        reserved_balance = new_reserved,
        updated_at = NOW()
    WHERE id = p_wallet_id;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- Create indexes for performance on balance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_balance_status 
ON wallets (balance, status) WHERE status = 'ACTIVE';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts_balance_status 
ON accounts (current_balance, status) WHERE status = 'ACTIVE';

-- Create audit trigger for balance changes
CREATE OR REPLACE FUNCTION audit_balance_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        -- Log significant balance changes
        IF ABS(NEW.balance - OLD.balance) > 0.01 OR ABS(NEW.available_balance - OLD.available_balance) > 0.01 THEN
            INSERT INTO audit_events (
                entity_type,
                entity_id,
                event_type,
                old_values,
                new_values,
                changed_by,
                changed_at
            ) VALUES (
                TG_TABLE_NAME,
                NEW.id,
                'BALANCE_CHANGE',
                json_build_object(
                    'balance', OLD.balance,
                    'available_balance', OLD.available_balance,
                    'reserved_balance', OLD.reserved_balance
                ),
                json_build_object(
                    'balance', NEW.balance,
                    'available_balance', NEW.available_balance,
                    'reserved_balance', NEW.reserved_balance
                ),
                current_setting('app.current_user_id', true),
                NOW()
            );
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply audit triggers
CREATE TRIGGER wallet_balance_audit_trigger
    AFTER UPDATE ON wallets
    FOR EACH ROW EXECUTE FUNCTION audit_balance_changes();

CREATE TRIGGER account_balance_audit_trigger
    AFTER UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION audit_balance_changes();