-- Performance indexes for all Waqiti services
-- This migration adds missing indexes to improve query performance

-- ========================================
-- Payment Service Indexes
-- ========================================

-- Split Payments
CREATE INDEX IF NOT EXISTS idx_split_payments_organizer_id ON split_payments(organizer_id);
CREATE INDEX IF NOT EXISTS idx_split_payments_status ON split_payments(status);
CREATE INDEX IF NOT EXISTS idx_split_payments_created_at ON split_payments(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_split_payments_expires_at ON split_payments(expires_at);
CREATE INDEX IF NOT EXISTS idx_split_payments_status_expires ON split_payments(status, expires_at) WHERE status = 'PENDING';

-- Split Payment Participants
CREATE INDEX IF NOT EXISTS idx_split_payment_participants_user_id ON split_payment_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_split_payment_participants_status ON split_payment_participants(status);
CREATE INDEX IF NOT EXISTS idx_split_payment_participants_split_payment_id ON split_payment_participants(split_payment_id);
CREATE INDEX IF NOT EXISTS idx_split_payment_participants_user_status ON split_payment_participants(user_id, status);

-- Scheduled Payments
CREATE INDEX IF NOT EXISTS idx_scheduled_payments_user_id ON scheduled_payments(user_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_payments_status ON scheduled_payments(status);
CREATE INDEX IF NOT EXISTS idx_scheduled_payments_next_execution ON scheduled_payments(next_execution_date);
CREATE INDEX IF NOT EXISTS idx_scheduled_payments_user_status ON scheduled_payments(user_id, status);

-- Scheduled Payment Executions
CREATE INDEX IF NOT EXISTS idx_scheduled_payment_executions_payment_id ON scheduled_payment_executions(scheduled_payment_id);
CREATE INDEX IF NOT EXISTS idx_scheduled_payment_executions_status ON scheduled_payment_executions(status);
CREATE INDEX IF NOT EXISTS idx_scheduled_payment_executions_executed_at ON scheduled_payment_executions(executed_at DESC);

-- Payment Methods
CREATE INDEX IF NOT EXISTS idx_payment_methods_user_id ON payment_methods(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_methods_status ON payment_methods(status);
CREATE INDEX IF NOT EXISTS idx_payment_methods_type ON payment_methods(type);
CREATE INDEX IF NOT EXISTS idx_payment_methods_default ON payment_methods(user_id, is_default) WHERE is_default = true;

-- Payment Requests
CREATE INDEX IF NOT EXISTS idx_payment_requests_requester_id ON payment_requests(requester_id);
CREATE INDEX IF NOT EXISTS idx_payment_requests_payer_id ON payment_requests(payer_id);
CREATE INDEX IF NOT EXISTS idx_payment_requests_status ON payment_requests(status);
CREATE INDEX IF NOT EXISTS idx_payment_requests_created_at ON payment_requests(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_requests_expires_at ON payment_requests(expires_at);

-- ========================================
-- Transaction Service Indexes
-- ========================================

-- Transactions
CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_sender_id ON transactions(sender_id);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver_id ON transactions(receiver_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_reference_number ON transactions(reference_number);
CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_user_type_status ON transactions(user_id, type, status);

-- Transaction Events (already has indexes in domain)

-- ========================================
-- Wallet Service Indexes
-- ========================================

-- Wallets
CREATE INDEX IF NOT EXISTS idx_wallets_user_id ON wallets(user_id);
CREATE INDEX IF NOT EXISTS idx_wallets_status ON wallets(status);
CREATE INDEX IF NOT EXISTS idx_wallets_type ON wallets(type);
CREATE INDEX IF NOT EXISTS idx_wallets_currency ON wallets(currency);
CREATE INDEX IF NOT EXISTS idx_wallets_user_currency ON wallets(user_id, currency);

-- Wallet Transactions
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_id ON wallet_transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_type ON wallet_transactions(type);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_status ON wallet_transactions(status);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_created_at ON wallet_transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_date ON wallet_transactions(wallet_id, created_at DESC);

-- ========================================
-- User Service Indexes
-- ========================================

-- Users
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users(phone_number);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_last_login ON users(last_login_at DESC);

-- User Profiles
CREATE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_profiles_verification_status ON user_profiles(verification_status);

-- ========================================
-- Notification Service Indexes
-- ========================================

-- Notifications
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_type ON notifications(type);
CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications(user_id, read) WHERE read = false;

-- Device Tokens
CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_device_tokens_active ON device_tokens(active);
CREATE INDEX IF NOT EXISTS idx_device_tokens_platform ON device_tokens(platform);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user_active ON device_tokens(user_id, active) WHERE active = true;

-- Topic Subscriptions
CREATE INDEX IF NOT EXISTS idx_topic_subscriptions_device_token_id ON topic_subscriptions(device_token_id);
CREATE INDEX IF NOT EXISTS idx_topic_subscriptions_topic ON topic_subscriptions(topic);
CREATE INDEX IF NOT EXISTS idx_topic_subscriptions_active ON topic_subscriptions(active);

-- ========================================
-- Messaging Service Indexes
-- ========================================

-- Messages
CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_conversation_date ON messages(conversation_id, created_at DESC);

-- Conversations
CREATE INDEX IF NOT EXISTS idx_conversations_type ON conversations(type);
CREATE INDEX IF NOT EXISTS idx_conversations_created_at ON conversations(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_last_message_at ON conversations(last_message_at DESC);

-- Conversation Participants
CREATE INDEX IF NOT EXISTS idx_conversation_participants_user_id ON conversation_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_participants_conversation_id ON conversation_participants(conversation_id);
CREATE INDEX IF NOT EXISTS idx_conversation_participants_user_conversation ON conversation_participants(user_id, conversation_id);

-- ========================================
-- KYC Service Indexes
-- ========================================

-- KYC Verifications
CREATE INDEX IF NOT EXISTS idx_kyc_verifications_user_id ON kyc_verifications(user_id);
CREATE INDEX IF NOT EXISTS idx_kyc_verifications_status ON kyc_verifications(status);
CREATE INDEX IF NOT EXISTS idx_kyc_verifications_level ON kyc_verifications(verification_level);
CREATE INDEX IF NOT EXISTS idx_kyc_verifications_created_at ON kyc_verifications(created_at DESC);

-- ========================================
-- Rewards Service Indexes
-- ========================================

-- Rewards
CREATE INDEX IF NOT EXISTS idx_rewards_user_id ON rewards(user_id);
CREATE INDEX IF NOT EXISTS idx_rewards_type ON rewards(type);
CREATE INDEX IF NOT EXISTS idx_rewards_status ON rewards(status);
CREATE INDEX IF NOT EXISTS idx_rewards_created_at ON rewards(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rewards_expires_at ON rewards(expires_at);
CREATE INDEX IF NOT EXISTS idx_rewards_user_status ON rewards(user_id, status);

-- Points Transactions
CREATE INDEX IF NOT EXISTS idx_points_transactions_user_id ON points_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_points_transactions_type ON points_transactions(type);
CREATE INDEX IF NOT EXISTS idx_points_transactions_created_at ON points_transactions(created_at DESC);

-- ========================================
-- Merchant Service Indexes
-- ========================================

-- Merchants (already has unique index on merchant_id)
CREATE INDEX IF NOT EXISTS idx_merchants_status ON merchants(status);
CREATE INDEX IF NOT EXISTS idx_merchants_category ON merchants(category);
CREATE INDEX IF NOT EXISTS idx_merchants_created_at ON merchants(created_at DESC);

-- Merchant Payments (already has unique index on payment_id)
CREATE INDEX IF NOT EXISTS idx_merchant_payments_merchant_id ON merchant_payments(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_payments_user_id ON merchant_payments(user_id);
CREATE INDEX IF NOT EXISTS idx_merchant_payments_status ON merchant_payments(status);
CREATE INDEX IF NOT EXISTS idx_merchant_payments_created_at ON merchant_payments(created_at DESC);

-- ========================================
-- Investment Service Indexes
-- ========================================

-- Investment Accounts (already has unique index on account_number)
CREATE INDEX IF NOT EXISTS idx_investment_accounts_user_id ON investment_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_investment_accounts_status ON investment_accounts(status);
CREATE INDEX IF NOT EXISTS idx_investment_accounts_type ON investment_accounts(type);

-- Investment Orders (already has unique index on order_number)
CREATE INDEX IF NOT EXISTS idx_investment_orders_account_id ON investment_orders(investment_account_id);
CREATE INDEX IF NOT EXISTS idx_investment_orders_status ON investment_orders(status);
CREATE INDEX IF NOT EXISTS idx_investment_orders_type ON investment_orders(order_type);
CREATE INDEX IF NOT EXISTS idx_investment_orders_created_at ON investment_orders(created_at DESC);

-- Investment Holdings
CREATE INDEX IF NOT EXISTS idx_investment_holdings_portfolio_id ON investment_holdings(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_investment_holdings_account_id ON investment_holdings(investment_account_id);
CREATE INDEX IF NOT EXISTS idx_investment_holdings_symbol ON investment_holdings(symbol);

-- ========================================
-- Family Account Service Indexes
-- ========================================

-- Family Accounts (already has unique index on family_id)
CREATE INDEX IF NOT EXISTS idx_family_accounts_primary_account_holder ON family_accounts(primary_account_holder_id);
CREATE INDEX IF NOT EXISTS idx_family_accounts_status ON family_accounts(status);

-- Family Members
CREATE INDEX IF NOT EXISTS idx_family_members_family_account_id ON family_members(family_account_id);
CREATE INDEX IF NOT EXISTS idx_family_members_user_id ON family_members(user_id);
CREATE INDEX IF NOT EXISTS idx_family_members_role ON family_members(role);
CREATE INDEX IF NOT EXISTS idx_family_members_status ON family_members(status);

-- Family Spending Rules
CREATE INDEX IF NOT EXISTS idx_family_spending_rules_family_account_id ON family_spending_rules(family_account_id);
CREATE INDEX IF NOT EXISTS idx_family_spending_rules_member_id ON family_spending_rules(family_member_id);
CREATE INDEX IF NOT EXISTS idx_family_spending_rules_active ON family_spending_rules(is_active);

-- ========================================
-- Recurring Payment Service Indexes
-- ========================================

-- Recurring Payments
CREATE INDEX IF NOT EXISTS idx_recurring_payments_user_id ON recurring_payments(user_id);
CREATE INDEX IF NOT EXISTS idx_recurring_payments_status ON recurring_payments(status);
CREATE INDEX IF NOT EXISTS idx_recurring_payments_next_execution ON recurring_payments(next_execution_date);
CREATE INDEX IF NOT EXISTS idx_recurring_payments_user_status ON recurring_payments(user_id, status);

-- Recurring Templates
CREATE INDEX IF NOT EXISTS idx_recurring_templates_user_id ON recurring_templates(user_id);
CREATE INDEX IF NOT EXISTS idx_recurring_templates_category ON recurring_templates(category);
CREATE INDEX IF NOT EXISTS idx_recurring_templates_favorite ON recurring_templates(user_id, is_favorite) WHERE is_favorite = true;

-- Recurring Executions
CREATE INDEX IF NOT EXISTS idx_recurring_executions_payment_id ON recurring_executions(recurring_payment_id);
CREATE INDEX IF NOT EXISTS idx_recurring_executions_status ON recurring_executions(status);
CREATE INDEX IF NOT EXISTS idx_recurring_executions_executed_at ON recurring_executions(executed_at DESC);

-- ========================================
-- GDPR Service Indexes
-- ========================================

-- Consent Records
CREATE INDEX IF NOT EXISTS idx_consent_records_user_id ON consent_records(user_id);
CREATE INDEX IF NOT EXISTS idx_consent_records_purpose ON consent_records(purpose);
CREATE INDEX IF NOT EXISTS idx_consent_records_status ON consent_records(status);
CREATE INDEX IF NOT EXISTS idx_consent_records_user_purpose_status ON consent_records(user_id, purpose, status);

-- Data Subject Requests
CREATE INDEX IF NOT EXISTS idx_data_subject_requests_user_id ON data_subject_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_data_subject_requests_type ON data_subject_requests(request_type);
CREATE INDEX IF NOT EXISTS idx_data_subject_requests_status ON data_subject_requests(status);
CREATE INDEX IF NOT EXISTS idx_data_subject_requests_created_at ON data_subject_requests(created_at DESC);

-- ========================================
-- Composite Indexes for Common Query Patterns
-- ========================================

-- Payment patterns
CREATE INDEX IF NOT EXISTS idx_payment_user_date_status ON payments(user_id, created_at DESC, status);
CREATE INDEX IF NOT EXISTS idx_payment_sender_receiver ON payments(sender_id, receiver_id);

-- Transaction patterns
CREATE INDEX IF NOT EXISTS idx_transaction_user_type_date ON transactions(user_id, type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transaction_status_date ON transactions(status, created_at DESC) WHERE status = 'PENDING';

-- Notification patterns
CREATE INDEX IF NOT EXISTS idx_notification_user_type_read ON notifications(user_id, type, read);
CREATE INDEX IF NOT EXISTS idx_notification_expire_status ON notifications(expires_at, read) WHERE read = false;

-- ========================================
-- Partial Indexes for Specific Queries
-- ========================================

-- Active users only
CREATE INDEX IF NOT EXISTS idx_users_active_email ON users(email) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_users_active_phone ON users(phone_number) WHERE status = 'ACTIVE';

-- Pending transactions only
CREATE INDEX IF NOT EXISTS idx_transactions_pending ON transactions(created_at) WHERE status = 'PENDING';

-- Unread notifications only
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(user_id, created_at DESC) WHERE read = false;

-- Active payment methods only
CREATE INDEX IF NOT EXISTS idx_payment_methods_active ON payment_methods(user_id, type) WHERE status = 'ACTIVE';

-- ========================================
-- Function-based Indexes
-- ========================================

-- Case-insensitive email search
CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users(LOWER(email));

-- Date-based queries
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(DATE(created_at));
CREATE INDEX IF NOT EXISTS idx_payments_month ON payments(DATE_TRUNC('month', created_at));

-- ========================================
-- ANALYZE tables after index creation
-- ========================================
ANALYZE;