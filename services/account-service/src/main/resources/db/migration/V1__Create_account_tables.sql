-- Account Service Database Schema
-- Creates tables for account management, balances, and financial operations

-- Main accounts table
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20) UNIQUE NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS', 'CREDIT', 'INVESTMENT', 'BUSINESS')),
    
    -- Owner information
    user_id UUID NOT NULL,
    business_id UUID, -- For business accounts
    
    -- Account details
    name VARCHAR(255) NOT NULL,
    description TEXT,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Status and settings
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED', 'FROZEN')),
    is_primary BOOLEAN DEFAULT FALSE,
    overdraft_enabled BOOLEAN DEFAULT FALSE,
    overdraft_limit DECIMAL(15,2) DEFAULT 0.00,
    
    -- Limits
    daily_transaction_limit DECIMAL(15,2),
    monthly_transaction_limit DECIMAL(15,2),
    annual_transaction_limit DECIMAL(15,2),
    
    -- External references
    external_account_id VARCHAR(100), -- For linked bank accounts
    routing_number VARCHAR(20),
    swift_code VARCHAR(20),
    
    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_by UUID,
    updated_by UUID
);

-- Account balances with currency support
CREATE TABLE account_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    currency VARCHAR(3) NOT NULL,
    
    -- Balance amounts
    available_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    current_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    pending_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    held_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    
    -- Balance metadata
    last_transaction_at TIMESTAMP WITH TIME ZONE,
    balance_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(account_id, currency),
    CHECK (current_balance = available_balance + pending_balance + held_balance)
);

-- Balance history for audit trail
CREATE TABLE balance_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    currency VARCHAR(3) NOT NULL,
    
    -- Balance changes
    previous_balance DECIMAL(15,2) NOT NULL,
    new_balance DECIMAL(15,2) NOT NULL,
    change_amount DECIMAL(15,2) NOT NULL,
    balance_type VARCHAR(20) NOT NULL CHECK (balance_type IN ('AVAILABLE', 'PENDING', 'HELD')),
    
    -- Change details
    transaction_id UUID,
    reference_id VARCHAR(100),
    change_reason VARCHAR(50) NOT NULL,
    description TEXT,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID
);

-- Account statements
CREATE TABLE account_statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    
    -- Statement period
    statement_date DATE NOT NULL,
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    
    -- Statement balances
    opening_balance DECIMAL(15,2) NOT NULL,
    closing_balance DECIMAL(15,2) NOT NULL,
    
    -- Statement summary
    total_credits DECIMAL(15,2) DEFAULT 0.00,
    total_debits DECIMAL(15,2) DEFAULT 0.00,
    transaction_count INTEGER DEFAULT 0,
    
    -- Statement file
    statement_file_path VARCHAR(500),
    statement_format VARCHAR(10) DEFAULT 'PDF',
    
    -- Status
    status VARCHAR(20) DEFAULT 'GENERATED' CHECK (status IN ('GENERATED', 'SENT', 'VIEWED')),
    generated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    
    UNIQUE(account_id, statement_date)
);

-- Account permissions for shared/business accounts
CREATE TABLE account_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    
    -- Permission levels
    permission_level VARCHAR(20) NOT NULL CHECK (permission_level IN ('VIEW', 'TRANSACT', 'ADMIN', 'OWNER')),
    
    -- Specific permissions
    can_view_balance BOOLEAN DEFAULT TRUE,
    can_view_transactions BOOLEAN DEFAULT TRUE,
    can_send_money BOOLEAN DEFAULT FALSE,
    can_receive_money BOOLEAN DEFAULT TRUE,
    can_manage_settings BOOLEAN DEFAULT FALSE,
    can_add_users BOOLEAN DEFAULT FALSE,
    
    -- Transaction limits for this user
    daily_limit DECIMAL(15,2),
    monthly_limit DECIMAL(15,2),
    
    -- Status
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    
    -- Audit
    granted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by UUID,
    
    UNIQUE(account_id, user_id)
);

-- Account settings and preferences
CREATE TABLE account_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    
    -- Notification settings
    email_notifications BOOLEAN DEFAULT TRUE,
    sms_notifications BOOLEAN DEFAULT FALSE,
    push_notifications BOOLEAN DEFAULT TRUE,
    
    -- Transaction notifications
    notify_on_deposit BOOLEAN DEFAULT TRUE,
    notify_on_withdrawal BOOLEAN DEFAULT TRUE,
    notify_on_low_balance BOOLEAN DEFAULT TRUE,
    low_balance_threshold DECIMAL(15,2) DEFAULT 100.00,
    
    -- Security settings
    require_2fa_for_transactions BOOLEAN DEFAULT FALSE,
    transaction_approval_required BOOLEAN DEFAULT FALSE,
    
    -- Statement preferences
    statement_frequency VARCHAR(20) DEFAULT 'MONTHLY' CHECK (statement_frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY')),
    statement_delivery VARCHAR(20) DEFAULT 'EMAIL' CHECK (statement_delivery IN ('EMAIL', 'MAIL', 'BOTH')),
    
    -- Other preferences
    preferred_currency VARCHAR(3) DEFAULT 'USD',
    timezone VARCHAR(50) DEFAULT 'UTC',
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(account_id)
);

-- Indexes for performance
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_business_id ON accounts(business_id) WHERE business_id IS NOT NULL;
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_external_account_id ON accounts(external_account_id) WHERE external_account_id IS NOT NULL;

CREATE INDEX idx_account_balances_account_id ON account_balances(account_id);
CREATE INDEX idx_account_balances_currency ON account_balances(currency);

CREATE INDEX idx_balance_history_account_id ON balance_history(account_id);
CREATE INDEX idx_balance_history_created_at ON balance_history(created_at);
CREATE INDEX idx_balance_history_transaction_id ON balance_history(transaction_id) WHERE transaction_id IS NOT NULL;

CREATE INDEX idx_account_statements_account_id ON account_statements(account_id);
CREATE INDEX idx_account_statements_statement_date ON account_statements(statement_date);

CREATE INDEX idx_account_permissions_account_id ON account_permissions(account_id);
CREATE INDEX idx_account_permissions_user_id ON account_permissions(user_id);
CREATE INDEX idx_account_permissions_status ON account_permissions(status);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE
    ON accounts FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_account_settings_updated_at BEFORE UPDATE
    ON account_settings FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE accounts IS 'Main accounts table for all account types';
COMMENT ON TABLE account_balances IS 'Current balances for accounts in multiple currencies';
COMMENT ON TABLE balance_history IS 'Audit trail for all balance changes';
COMMENT ON TABLE account_statements IS 'Generated account statements';
COMMENT ON TABLE account_permissions IS 'User permissions for shared accounts';
COMMENT ON TABLE account_settings IS 'Account preferences and settings';