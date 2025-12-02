-- Comprehensive Performance Indexes for Waqiti Platform
-- This migration adds critical indexes for query optimization across all services

-- =====================================================
-- USER SERVICE INDEXES
-- =====================================================

-- User authentication and lookup indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_username_active ON users(username) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_active ON users(email) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_phone_active ON users(phone_number) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_external_id ON users(external_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_keycloak_id ON users(keycloak_id) WHERE keycloak_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_last_activity ON users(last_activity_at DESC) WHERE last_activity_at IS NOT NULL;

-- User fraud and security indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_account_locked ON users(account_locked) WHERE account_locked = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_transaction_restricted ON users(transaction_restricted) WHERE transaction_restricted = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_enhanced_monitoring ON users(enhanced_monitoring) WHERE enhanced_monitoring = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_fraud_risk_score ON users(fraud_risk_score) WHERE fraud_risk_score > 0.5;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_requires_manual_review ON users(requires_manual_review) WHERE requires_manual_review = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_frozen_at ON users(frozen_at DESC) WHERE frozen_at IS NOT NULL;

-- User profile indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_profiles_dob ON user_profiles(date_of_birth);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_profiles_country ON user_profiles(country);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_profiles_preferred_currency ON user_profiles(preferred_currency);

-- MFA and verification indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mfa_configs_user_enabled ON mfa_configurations(user_id) WHERE enabled = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mfa_codes_user_created ON mfa_verification_codes(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mfa_codes_code_type ON mfa_verification_codes(code, verification_type) WHERE used = false;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_verification_tokens_token ON verification_tokens(token) WHERE used = false;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_verification_tokens_user_type ON verification_tokens(user_id, type) WHERE used = false;

-- Family guardianship indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_guardian_guardian ON family_guardianships(guardian_user_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_guardian_dependent ON family_guardianships(dependent_user_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approvals_guardian ON guardian_approval_requests(guardian_id, status) WHERE status = 'PENDING';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_guardian_approvals_created ON guardian_approval_requests(created_at DESC);

-- =====================================================
-- WALLET SERVICE INDEXES
-- =====================================================

-- Wallet lookup and balance indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_currency ON wallets(user_id, currency, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_user_type ON wallets(user_id, wallet_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_balance_range ON wallets(balance) WHERE balance > 0;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_frozen ON wallets(is_frozen) WHERE is_frozen = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallets_created_at ON wallets(created_at DESC);

-- Transaction indexes for fast queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_wallet_created ON transactions(wallet_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_user_created ON transactions(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_source_target ON transactions(source_wallet_id, target_wallet_id) WHERE status = 'COMPLETED';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_status_type ON transactions(status, type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_reference ON transactions(reference) WHERE reference IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_external_id ON transactions(external_id) WHERE external_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_amount_range ON transactions(amount) WHERE amount > 10000;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_monitoring ON transactions(monitoring_enabled) WHERE monitoring_enabled = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_blocked ON transactions(blocked_at DESC) WHERE blocked_at IS NOT NULL;

-- Wallet holds and reservations
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_holds_wallet_status ON wallet_holds(wallet_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_holds_expires ON wallet_holds(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fund_reservations_wallet ON fund_reservations(wallet_id, status) WHERE status IN ('RESERVED', 'PENDING');
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fund_reservations_expires ON fund_reservations(expiry_time) WHERE status = 'RESERVED';

-- =====================================================
-- PAYMENT SERVICE INDEXES
-- =====================================================

-- Payment processing indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_user_created ON payments(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_status_created ON payments(status, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_merchant_status ON payments(merchant_id, status) WHERE merchant_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_reference ON payments(reference_number) WHERE reference_number IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_provider_ref ON payments(provider, external_reference) WHERE external_reference IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_amount_range ON payments(amount) WHERE amount > 1000;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_scheduled ON payments(scheduled_at) WHERE status = 'SCHEDULED';

-- Payment methods
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_active ON payment_methods(user_id, is_default) WHERE is_active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_type ON payment_methods(payment_type, is_active);

-- Split payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_split_payments_initiator ON split_payments(initiator_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_split_participants_payment ON split_payment_participants(split_payment_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_split_participants_user ON split_payment_participants(user_id, status) WHERE status = 'PENDING';

-- Scheduled payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_payments_user ON scheduled_payments(user_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_payments_next ON scheduled_payments(next_execution_date) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_scheduled_executions_payment ON scheduled_payment_executions(scheduled_payment_id, execution_date DESC);

-- Payment links
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_links_code ON payment_links(link_code) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_links_expires ON payment_links(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_link_txns_link ON payment_link_transactions(payment_link_id, created_at DESC);

-- NFC and offline payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_nfc_sessions_device ON nfc_sessions(device_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_nfc_transactions_session ON nfc_transactions(session_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_offline_payments_device ON offline_payments(device_id, synced) WHERE synced = false;

-- =====================================================
-- CRYPTO SERVICE INDEXES
-- =====================================================

-- Crypto wallets
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_wallets_user_currency ON crypto_wallets(user_id, currency, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_wallets_multisig ON crypto_wallets(multi_sig_address);

-- Crypto transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_txns_wallet ON crypto_transactions(wallet_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_txns_status ON crypto_transactions(status, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_txns_hash ON crypto_transactions(tx_hash) WHERE tx_hash IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_txns_blockchain_confirms ON crypto_transactions(blockchain_confirmations) WHERE status = 'PENDING';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_txns_high_value ON crypto_transactions(amount_usd) WHERE amount_usd > 10000;

-- Crypto addresses
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_addresses_wallet ON crypto_addresses(wallet_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_addresses_address ON crypto_addresses(address);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_addresses_label ON crypto_addresses(label) WHERE label IS NOT NULL;

-- Risk and compliance
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sanctioned_addresses_address ON sanctioned_addresses(address, active) WHERE active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_high_risk_addresses_address ON high_risk_addresses(address, active) WHERE active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_fraud_wallet ON crypto_fraud_events(wallet_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_crypto_fraud_risk ON crypto_fraud_events(risk_level) WHERE risk_level IN ('HIGH', 'CRITICAL');

-- =====================================================
-- KYC SERVICE INDEXES
-- =====================================================

-- KYC profiles
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_profiles_user ON kyc_profiles(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_profiles_status ON kyc_profiles(verification_status, updated_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_profiles_level ON kyc_profiles(verification_level) WHERE verification_status = 'VERIFIED';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_profiles_expiry ON kyc_profiles(verification_expiry_date) WHERE verification_status = 'VERIFIED';

-- KYC documents
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_profile ON kyc_documents(kyc_profile_id, document_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_status ON kyc_documents(verification_status, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_documents_expiry ON kyc_documents(expiry_date) WHERE expiry_date IS NOT NULL;

-- KYC verifications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_verifications_profile ON kyc_verifications(kyc_profile_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_verifications_type_status ON kyc_verifications(verification_type, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_verifications_reviewer ON kyc_verifications(reviewed_by, review_date DESC) WHERE reviewed_by IS NOT NULL;

-- =====================================================
-- FRAUD SERVICE INDEXES
-- =====================================================

-- Fraud alerts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_alerts_user ON fraud_alerts(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_alerts_status ON fraud_alerts(status, severity);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_alerts_transaction ON fraud_alerts(transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_alerts_risk_score ON fraud_alerts(risk_score DESC) WHERE risk_score > 0.7;

-- Fraud rules
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_rules_active ON fraud_rules(rule_type, is_active) WHERE is_active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_rules_priority ON fraud_rules(priority DESC) WHERE is_active = true;

-- Blacklisted entities
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_blacklist_type_value ON blacklisted_entities(entity_type, entity_value, is_active) WHERE is_active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_blacklist_expiry ON blacklisted_entities(expiry_date) WHERE is_active = true AND expiry_date IS NOT NULL;

-- =====================================================
-- COMPLIANCE SERVICE INDEXES
-- =====================================================

-- Compliance checks
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_checks_user ON compliance_checks(user_id, check_type, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_checks_status ON compliance_checks(status, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compliance_checks_risk ON compliance_checks(risk_level) WHERE risk_level IN ('HIGH', 'CRITICAL');

-- Sanctions screening
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sanctions_user ON sanctions_screenings(user_id, screening_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sanctions_status ON sanctions_screenings(status, has_matches);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sanctions_matches ON sanctions_screenings(has_matches) WHERE has_matches = true;

-- AML alerts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_user ON aml_alerts(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_status ON aml_alerts(status, severity);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aml_alerts_investigation ON aml_alerts(investigation_status) WHERE investigation_status = 'PENDING';

-- SARs (Suspicious Activity Reports)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sars_user ON suspicious_activity_reports(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sars_status ON suspicious_activity_reports(status, filing_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sars_filed ON suspicious_activity_reports(filed_with_regulator) WHERE filed_with_regulator = true;

-- =====================================================
-- MERCHANT SERVICE INDEXES
-- =====================================================

-- Merchants
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_status ON merchants(status, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_category ON merchants(business_category, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_verification ON merchants(verification_status) WHERE verification_status != 'VERIFIED';

-- Merchant accounts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_accounts_merchant ON merchant_accounts(merchant_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_accounts_settlement ON merchant_accounts(settlement_schedule, next_settlement_date);

-- Merchant settlements
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_settlements_merchant ON merchant_settlements(merchant_id, settlement_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_settlements_status ON merchant_settlements(status, settlement_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchant_settlements_pending ON merchant_settlements(status) WHERE status = 'PENDING';

-- =====================================================
-- INVESTMENT SERVICE INDEXES
-- =====================================================

-- Investment accounts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_accounts_user ON investment_accounts(user_id, account_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_accounts_status ON investment_accounts(status) WHERE status = 'ACTIVE';

-- Investment portfolios
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_portfolios_account ON investment_portfolios(account_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_portfolios_value ON investment_portfolios(total_value DESC) WHERE total_value > 0;

-- Investment transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_txns_account ON investment_transactions(account_id, transaction_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_txns_type ON investment_transactions(transaction_type, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_investment_txns_symbol ON investment_transactions(symbol, transaction_date DESC);

-- Market orders
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_market_orders_account ON market_orders(account_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_market_orders_status ON market_orders(status, created_at DESC) WHERE status IN ('PENDING', 'OPEN');
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_market_orders_symbol ON market_orders(symbol, order_type);

-- =====================================================
-- CARD SERVICE INDEXES
-- =====================================================

-- Cards
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cards_user_status ON cards(user_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cards_number_masked ON cards(card_number_last4, card_type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cards_expiry ON cards(expiry_date) WHERE status = 'ACTIVE';

-- Virtual cards
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_virtual_cards_user ON virtual_cards(user_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_virtual_cards_expiry ON virtual_cards(expiry_date) WHERE status = 'ACTIVE';

-- Card transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_card_txns_card ON card_transactions(card_id, transaction_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_card_txns_merchant ON card_transactions(merchant_name, transaction_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_card_txns_status ON card_transactions(status, transaction_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_card_txns_disputed ON card_transactions(is_disputed) WHERE is_disputed = true;

-- =====================================================
-- NOTIFICATION SERVICE INDEXES
-- =====================================================

-- Notifications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_user_type ON notifications(user_id, notification_type, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_scheduled ON notifications(scheduled_at) WHERE status = 'SCHEDULED';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_failed ON notifications(status, retry_count) WHERE status = 'FAILED' AND retry_count < 3;

-- =====================================================
-- LOAN SERVICE INDEXES
-- =====================================================

-- Loans
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loans_user_status ON loans(user_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loans_due_date ON loans(next_payment_date) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loans_overdue ON loans(days_overdue) WHERE days_overdue > 0;

-- Loan applications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loan_apps_user ON loan_applications(user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loan_apps_status ON loan_applications(status, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loan_apps_pending ON loan_applications(status) WHERE status IN ('PENDING', 'UNDER_REVIEW');

-- Loan payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loan_payments_loan ON loan_payments(loan_id, payment_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_loan_payments_status ON loan_payments(status, payment_date DESC);

-- =====================================================
-- REWARDS SERVICE INDEXES
-- =====================================================

-- User rewards accounts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rewards_accounts_user ON user_rewards_account(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rewards_accounts_tier ON user_rewards_account(tier_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rewards_accounts_points ON user_rewards_account(points_balance DESC) WHERE points_balance > 0;

-- Rewards transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rewards_txns_account ON rewards_transactions(account_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rewards_txns_type ON rewards_transactions(transaction_type, status);

-- =====================================================
-- ATM SERVICE INDEXES
-- =====================================================

-- ATM cards
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_atm_cards_account ON atm_cards(account_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_atm_cards_number ON atm_cards(card_number) WHERE status != 'CANCELLED';

-- ATM transactions
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_atm_txns_card ON atm_transactions(card_id, transaction_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_atm_txns_terminal ON atm_transactions(terminal_id, transaction_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_atm_txns_status ON atm_transactions(status, transaction_date DESC);

-- Cardless withdrawals
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cardless_code ON cardless_withdrawals(withdrawal_code) WHERE status = 'PENDING';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cardless_expiry ON cardless_withdrawals(expires_at) WHERE status = 'PENDING';

-- =====================================================
-- FAMILY ACCOUNT SERVICE INDEXES
-- =====================================================

-- Family accounts
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_accounts_owner ON family_accounts(owner_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_accounts_created ON family_accounts(created_at DESC);

-- Family members
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_members_account ON family_members(family_account_id, status) WHERE status = 'ACTIVE';
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_members_user ON family_members(user_id, role);

-- Family spending rules
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_rules_account ON family_spending_rules(family_account_id, is_active) WHERE is_active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_family_rules_member ON family_spending_rules(member_id, is_active) WHERE is_active = true;

-- =====================================================
-- EXPENSE SERVICE INDEXES
-- =====================================================

-- Expenses
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expenses_user_date ON expenses(user_id, expense_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expenses_category ON expenses(category_id, expense_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expenses_merchant ON expenses(merchant_name) WHERE merchant_name IS NOT NULL;

-- Budgets
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_budgets_user_period ON budgets(user_id, period_start, period_end);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_budgets_category ON budgets(category_id, is_active) WHERE is_active = true;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_budgets_exceeded ON budgets(amount_spent, budget_limit) WHERE amount_spent > budget_limit;

-- =====================================================
-- ANALYTICS AND REPORTING INDEXES
-- =====================================================

-- Transaction aggregation indexes for reporting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_daily_aggregates ON transactions(DATE(created_at), status, type);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_monthly_aggregates ON transactions(DATE_TRUNC('month', created_at), user_id, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_daily_aggregates ON payments(DATE(created_at), status, payment_method);

-- User activity indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_daily_activity ON users(DATE(last_activity_at)) WHERE last_activity_at IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_registration_trends ON users(DATE(created_at), status);

-- =====================================================
-- COMPOSITE INDEXES FOR COMPLEX QUERIES
-- =====================================================

-- User transaction history with filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_user_filter ON transactions(user_id, type, status, created_at DESC);

-- Wallet balance reconciliation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_wallet_reconciliation ON wallets(id, balance, available_balance, updated_at);

-- Payment settlement processing
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_settlement ON payments(merchant_id, status, created_at) 
    WHERE status IN ('COMPLETED', 'PENDING_SETTLEMENT');

-- Fraud detection patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_fraud_pattern ON transactions(user_id, amount, created_at DESC) 
    WHERE amount > 1000;

-- KYC verification workflow
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_kyc_workflow ON kyc_profiles(user_id, verification_status, verification_level, updated_at DESC);

-- =====================================================
-- PARTIAL INDEXES FOR SPECIFIC QUERIES
-- =====================================================

-- Active subscriptions and recurring payments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_active_subscriptions ON scheduled_payments(user_id, next_execution_date) 
    WHERE status = 'ACTIVE' AND payment_type = 'SUBSCRIPTION';

-- High-value transactions requiring review
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_high_value_review ON transactions(created_at DESC, amount) 
    WHERE amount > 50000 AND status = 'PENDING';

-- Recently failed transactions for retry
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_failed_retry ON transactions(created_at DESC, failure_reason) 
    WHERE status = 'FAILED' AND created_at > NOW() - INTERVAL '24 hours';

-- Users requiring enhanced due diligence
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_edd_users ON users(created_at DESC, fraud_risk_score) 
    WHERE fraud_risk_score > 0.8 OR enhanced_monitoring = true;

-- =====================================================
-- TEXT SEARCH INDEXES
-- =====================================================

-- Full-text search on user data
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_search ON users USING gin(
    to_tsvector('english', COALESCE(username, '') || ' ' || COALESCE(email, ''))
);

-- Full-text search on merchant names
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_merchants_search ON merchants USING gin(
    to_tsvector('english', COALESCE(business_name, '') || ' ' || COALESCE(legal_name, ''))
);

-- Transaction description search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_txn_description_search ON transactions USING gin(
    to_tsvector('english', COALESCE(description, ''))
);

-- =====================================================
-- PERFORMANCE STATISTICS UPDATE
-- =====================================================

-- Update table statistics for query planner
ANALYZE users;
ANALYZE wallets;
ANALYZE transactions;
ANALYZE payments;
ANALYZE crypto_wallets;
ANALYZE crypto_transactions;
ANALYZE kyc_profiles;
ANALYZE fraud_alerts;
ANALYZE merchants;
ANALYZE cards;

-- =====================================================
-- INDEX MAINTENANCE COMMENTS
-- =====================================================

COMMENT ON INDEX idx_users_username_active IS 'Fast user authentication lookups';
COMMENT ON INDEX idx_transactions_wallet_created IS 'Transaction history queries';
COMMENT ON INDEX idx_payments_user_created IS 'Payment history and reporting';
COMMENT ON INDEX idx_crypto_txns_high_value IS 'High-value crypto transaction monitoring';
COMMENT ON INDEX idx_fraud_alerts_risk_score IS 'High-risk fraud detection queries';
COMMENT ON INDEX idx_kyc_workflow IS 'KYC verification workflow optimization';
COMMENT ON INDEX idx_users_search IS 'Full-text search on user information';

-- End of performance indexes migration