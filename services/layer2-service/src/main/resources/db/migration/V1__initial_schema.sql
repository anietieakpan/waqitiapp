-- Layer2 Service Initial Schema
-- Created: 2025-09-27
-- Description: Blockchain Layer2 scaling solutions and state channels schema

CREATE TABLE IF NOT EXISTS layer2_channel (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id VARCHAR(100) UNIQUE NOT NULL,
    channel_type VARCHAR(50) NOT NULL,
    blockchain_network VARCHAR(50) NOT NULL,
    channel_status VARCHAR(20) NOT NULL DEFAULT 'OPENING',
    participant_addresses TEXT[] NOT NULL,
    initiator_address VARCHAR(100) NOT NULL,
    responder_address VARCHAR(100),
    multi_party BOOLEAN DEFAULT FALSE,
    total_participants INTEGER NOT NULL DEFAULT 2,
    channel_balance DECIMAL(30, 18) NOT NULL DEFAULT 0,
    initial_deposits JSONB NOT NULL,
    current_balances JSONB NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    asset_contract_address VARCHAR(100),
    channel_capacity DECIMAL(30, 18),
    opened_at TIMESTAMP,
    last_state_update TIMESTAMP,
    challenge_period_seconds INTEGER DEFAULT 86400,
    dispute_deadline TIMESTAMP,
    closed_at TIMESTAMP,
    closure_reason VARCHAR(100),
    settlement_transaction_hash VARCHAR(100),
    on_chain_updates INTEGER DEFAULT 0,
    off_chain_transactions INTEGER DEFAULT 0,
    total_volume DECIMAL(30, 18) DEFAULT 0,
    channel_metadata JSONB,
    smart_contract_address VARCHAR(100),
    nonce INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS layer2_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id VARCHAR(100) UNIQUE NOT NULL,
    channel_id VARCHAR(100) NOT NULL,
    state_number INTEGER NOT NULL,
    state_hash VARCHAR(100) NOT NULL,
    previous_state_hash VARCHAR(100),
    state_type VARCHAR(50) NOT NULL,
    balances JSONB NOT NULL,
    locked_amounts JSONB,
    pending_transfers JSONB,
    state_data JSONB NOT NULL,
    signatures JSONB NOT NULL,
    is_final BOOLEAN DEFAULT FALSE,
    is_challenged BOOLEAN DEFAULT FALSE,
    challenge_timestamp TIMESTAMP,
    challenger_address VARCHAR(100),
    is_settled BOOLEAN DEFAULT FALSE,
    settlement_timestamp TIMESTAMP,
    created_by_address VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_layer2_state_channel FOREIGN KEY (channel_id) REFERENCES layer2_channel(channel_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS layer2_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    channel_id VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    transaction_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sender_address VARCHAR(100) NOT NULL,
    receiver_address VARCHAR(100) NOT NULL,
    amount DECIMAL(30, 18) NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    fee DECIMAL(30, 18) DEFAULT 0,
    nonce INTEGER NOT NULL,
    transaction_data JSONB,
    payment_hash VARCHAR(100),
    payment_preimage VARCHAR(100),
    lock_timeout INTEGER,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    signature VARCHAR(500) NOT NULL,
    state_id VARCHAR(100),
    confirmation_count INTEGER DEFAULT 0,
    is_finalized BOOLEAN DEFAULT FALSE,
    finalized_at TIMESTAMP,
    revert_reason VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_layer2_transaction_channel FOREIGN KEY (channel_id) REFERENCES layer2_channel(channel_id) ON DELETE CASCADE,
    CONSTRAINT fk_layer2_transaction_state FOREIGN KEY (state_id) REFERENCES layer2_state(state_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS layer2_deposit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deposit_id VARCHAR(100) UNIQUE NOT NULL,
    channel_id VARCHAR(100) NOT NULL,
    depositor_address VARCHAR(100) NOT NULL,
    deposit_amount DECIMAL(30, 18) NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    deposit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    on_chain_transaction_hash VARCHAR(100),
    block_number BIGINT,
    confirmations INTEGER DEFAULT 0,
    required_confirmations INTEGER DEFAULT 12,
    deposited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    credited_at TIMESTAMP,
    channel_balance_before DECIMAL(30, 18),
    channel_balance_after DECIMAL(30, 18),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_layer2_deposit_channel FOREIGN KEY (channel_id) REFERENCES layer2_channel(channel_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS layer2_withdrawal (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    withdrawal_id VARCHAR(100) UNIQUE NOT NULL,
    channel_id VARCHAR(100) NOT NULL,
    withdrawer_address VARCHAR(100) NOT NULL,
    withdrawal_amount DECIMAL(30, 18) NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    withdrawal_status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    withdrawal_type VARCHAR(50) NOT NULL,
    challenge_period_end TIMESTAMP,
    on_chain_transaction_hash VARCHAR(100),
    block_number BIGINT,
    confirmations INTEGER DEFAULT 0,
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    finalized_at TIMESTAMP,
    channel_balance_before DECIMAL(30, 18),
    channel_balance_after DECIMAL(30, 18),
    withdrawal_proof JSONB,
    signature VARCHAR(500),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_layer2_withdrawal_channel FOREIGN KEY (channel_id) REFERENCES layer2_channel(channel_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS layer2_dispute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id VARCHAR(100) UNIQUE NOT NULL,
    channel_id VARCHAR(100) NOT NULL,
    dispute_type VARCHAR(50) NOT NULL,
    dispute_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    challenger_address VARCHAR(100) NOT NULL,
    challenged_state_id VARCHAR(100) NOT NULL,
    challenge_state_id VARCHAR(100),
    dispute_reason VARCHAR(255) NOT NULL,
    dispute_evidence JSONB,
    challenge_transaction_hash VARCHAR(100),
    challenge_block_number BIGINT,
    challenge_period_end TIMESTAMP NOT NULL,
    response_required BOOLEAN DEFAULT TRUE,
    response_deadline TIMESTAMP,
    responder_address VARCHAR(100),
    response_state_id VARCHAR(100),
    response_submitted BOOLEAN DEFAULT FALSE,
    response_timestamp TIMESTAMP,
    resolution VARCHAR(100),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(100),
    settlement_transaction_hash VARCHAR(100),
    penalty_amount DECIMAL(30, 18) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_layer2_dispute_channel FOREIGN KEY (channel_id) REFERENCES layer2_channel(channel_id) ON DELETE CASCADE,
    CONSTRAINT fk_layer2_dispute_challenged_state FOREIGN KEY (challenged_state_id) REFERENCES layer2_state(state_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS layer2_rollup_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id VARCHAR(100) UNIQUE NOT NULL,
    rollup_type VARCHAR(50) NOT NULL,
    batch_number INTEGER NOT NULL,
    batch_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    blockchain_network VARCHAR(50) NOT NULL,
    transaction_count INTEGER DEFAULT 0,
    transactions TEXT[] NOT NULL,
    merkle_root VARCHAR(100) NOT NULL,
    state_root_before VARCHAR(100) NOT NULL,
    state_root_after VARCHAR(100) NOT NULL,
    proof_type VARCHAR(50),
    validity_proof JSONB,
    fraud_proof_window_seconds INTEGER DEFAULT 604800,
    fraud_proof_deadline TIMESTAMP,
    submitted_at TIMESTAMP,
    submission_transaction_hash VARCHAR(100),
    submission_block_number BIGINT,
    confirmed_at TIMESTAMP,
    finalized_at TIMESTAMP,
    total_gas_used BIGINT,
    batch_size_bytes INTEGER,
    compression_ratio DECIMAL(5, 4),
    sequencer_address VARCHAR(100),
    aggregator_address VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS layer2_bridge_transfer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id VARCHAR(100) UNIQUE NOT NULL,
    transfer_type VARCHAR(50) NOT NULL,
    transfer_status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    source_chain VARCHAR(50) NOT NULL,
    destination_chain VARCHAR(50) NOT NULL,
    sender_address VARCHAR(100) NOT NULL,
    receiver_address VARCHAR(100) NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    asset_contract_address VARCHAR(100),
    transfer_amount DECIMAL(30, 18) NOT NULL,
    bridge_fee DECIMAL(30, 18) DEFAULT 0,
    source_transaction_hash VARCHAR(100),
    source_block_number BIGINT,
    destination_transaction_hash VARCHAR(100),
    destination_block_number BIGINT,
    relay_transaction_hash VARCHAR(100),
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    relayed_at TIMESTAMP,
    completed_at TIMESTAMP,
    challenge_period_end TIMESTAMP,
    merkle_proof JSONB,
    signatures JSONB,
    validator_confirmations INTEGER DEFAULT 0,
    required_confirmations INTEGER DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS layer2_validator (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    validator_id VARCHAR(100) UNIQUE NOT NULL,
    validator_address VARCHAR(100) UNIQUE NOT NULL,
    validator_type VARCHAR(50) NOT NULL,
    validator_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    stake_amount DECIMAL(30, 18) NOT NULL,
    reputation_score INTEGER DEFAULT 100,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP,
    blocks_validated INTEGER DEFAULT 0,
    blocks_proposed INTEGER DEFAULT 0,
    challenges_won INTEGER DEFAULT 0,
    challenges_lost INTEGER DEFAULT 0,
    slashed_amount DECIMAL(30, 18) DEFAULT 0,
    rewards_earned DECIMAL(30, 18) DEFAULT 0,
    uptime_percentage DECIMAL(5, 4) DEFAULT 1,
    commission_rate DECIMAL(5, 4) DEFAULT 0.1,
    is_active BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS layer2_gas_estimation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estimation_id VARCHAR(100) UNIQUE NOT NULL,
    blockchain_network VARCHAR(50) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    estimated_gas BIGINT NOT NULL,
    gas_price_gwei DECIMAL(18, 9) NOT NULL,
    estimated_cost_eth DECIMAL(30, 18) NOT NULL,
    estimated_cost_usd DECIMAL(18, 2),
    layer1_cost_eth DECIMAL(30, 18),
    layer2_cost_eth DECIMAL(30, 18),
    savings_percentage DECIMAL(5, 4),
    confidence_level DECIMAL(5, 4),
    estimation_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expiry_timestamp TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS layer2_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_channels INTEGER DEFAULT 0,
    active_channels INTEGER DEFAULT 0,
    total_transactions BIGINT DEFAULT 0,
    transaction_volume DECIMAL(30, 18) DEFAULT 0,
    total_deposits BIGINT DEFAULT 0,
    deposit_volume DECIMAL(30, 18) DEFAULT 0,
    total_withdrawals BIGINT DEFAULT 0,
    withdrawal_volume DECIMAL(30, 18) DEFAULT 0,
    disputes_opened INTEGER DEFAULT 0,
    disputes_resolved INTEGER DEFAULT 0,
    rollup_batches_submitted INTEGER DEFAULT 0,
    avg_batch_size INTEGER,
    bridge_transfers INTEGER DEFAULT 0,
    bridge_volume DECIMAL(30, 18) DEFAULT 0,
    gas_savings_eth DECIMAL(30, 18) DEFAULT 0,
    by_channel_type JSONB,
    by_blockchain_network JSONB,
    performance_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS layer2_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_layer2_channels INTEGER DEFAULT 0,
    active_channels INTEGER DEFAULT 0,
    total_transactions BIGINT DEFAULT 0,
    transaction_volume DECIMAL(30, 18) DEFAULT 0,
    avg_transaction_cost_usd DECIMAL(18, 6),
    total_gas_saved_eth DECIMAL(30, 18) DEFAULT 0,
    rollup_efficiency DECIMAL(5, 4),
    bridge_transfers INTEGER DEFAULT 0,
    bridge_volume DECIMAL(30, 18) DEFAULT 0,
    by_blockchain JSONB,
    by_operation_type JSONB,
    validator_performance JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_layer2_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_layer2_channel_type ON layer2_channel(channel_type);
CREATE INDEX idx_layer2_channel_status ON layer2_channel(channel_status);
CREATE INDEX idx_layer2_channel_network ON layer2_channel(blockchain_network);
CREATE INDEX idx_layer2_channel_initiator ON layer2_channel(initiator_address);

CREATE INDEX idx_layer2_state_channel ON layer2_state(channel_id);
CREATE INDEX idx_layer2_state_number ON layer2_state(state_number);
CREATE INDEX idx_layer2_state_hash ON layer2_state(state_hash);
CREATE INDEX idx_layer2_state_final ON layer2_state(is_final) WHERE is_final = true;
CREATE INDEX idx_layer2_state_challenged ON layer2_state(is_challenged) WHERE is_challenged = true;

CREATE INDEX idx_layer2_transaction_channel ON layer2_transaction(channel_id);
CREATE INDEX idx_layer2_transaction_sender ON layer2_transaction(sender_address);
CREATE INDEX idx_layer2_transaction_receiver ON layer2_transaction(receiver_address);
CREATE INDEX idx_layer2_transaction_status ON layer2_transaction(transaction_status);
CREATE INDEX idx_layer2_transaction_timestamp ON layer2_transaction(timestamp DESC);

CREATE INDEX idx_layer2_deposit_channel ON layer2_deposit(channel_id);
CREATE INDEX idx_layer2_deposit_depositor ON layer2_deposit(depositor_address);
CREATE INDEX idx_layer2_deposit_status ON layer2_deposit(deposit_status);
CREATE INDEX idx_layer2_deposit_transaction ON layer2_deposit(on_chain_transaction_hash);

CREATE INDEX idx_layer2_withdrawal_channel ON layer2_withdrawal(channel_id);
CREATE INDEX idx_layer2_withdrawal_withdrawer ON layer2_withdrawal(withdrawer_address);
CREATE INDEX idx_layer2_withdrawal_status ON layer2_withdrawal(withdrawal_status);
CREATE INDEX idx_layer2_withdrawal_transaction ON layer2_withdrawal(on_chain_transaction_hash);

CREATE INDEX idx_layer2_dispute_channel ON layer2_dispute(channel_id);
CREATE INDEX idx_layer2_dispute_challenger ON layer2_dispute(challenger_address);
CREATE INDEX idx_layer2_dispute_status ON layer2_dispute(dispute_status);
CREATE INDEX idx_layer2_dispute_challenged_state ON layer2_dispute(challenged_state_id);

CREATE INDEX idx_layer2_rollup_batch_type ON layer2_rollup_batch(rollup_type);
CREATE INDEX idx_layer2_rollup_batch_number ON layer2_rollup_batch(batch_number);
CREATE INDEX idx_layer2_rollup_batch_status ON layer2_rollup_batch(batch_status);
CREATE INDEX idx_layer2_rollup_batch_network ON layer2_rollup_batch(blockchain_network);

CREATE INDEX idx_layer2_bridge_transfer_type ON layer2_bridge_transfer(transfer_type);
CREATE INDEX idx_layer2_bridge_transfer_status ON layer2_bridge_transfer(transfer_status);
CREATE INDEX idx_layer2_bridge_transfer_sender ON layer2_bridge_transfer(sender_address);
CREATE INDEX idx_layer2_bridge_transfer_source ON layer2_bridge_transfer(source_chain);

CREATE INDEX idx_layer2_validator_address ON layer2_validator(validator_address);
CREATE INDEX idx_layer2_validator_type ON layer2_validator(validator_type);
CREATE INDEX idx_layer2_validator_status ON layer2_validator(validator_status);
CREATE INDEX idx_layer2_validator_active ON layer2_validator(is_active) WHERE is_active = true;

CREATE INDEX idx_layer2_gas_estimation_network ON layer2_gas_estimation(blockchain_network);
CREATE INDEX idx_layer2_gas_estimation_operation ON layer2_gas_estimation(operation_type);
CREATE INDEX idx_layer2_gas_estimation_timestamp ON layer2_gas_estimation(estimation_timestamp DESC);

CREATE INDEX idx_layer2_analytics_period ON layer2_analytics(period_end DESC);
CREATE INDEX idx_layer2_statistics_period ON layer2_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_layer2_channel_updated_at BEFORE UPDATE ON layer2_channel
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_layer2_deposit_updated_at BEFORE UPDATE ON layer2_deposit
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_layer2_withdrawal_updated_at BEFORE UPDATE ON layer2_withdrawal
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_layer2_dispute_updated_at BEFORE UPDATE ON layer2_dispute
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_layer2_rollup_batch_updated_at BEFORE UPDATE ON layer2_rollup_batch
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_layer2_bridge_transfer_updated_at BEFORE UPDATE ON layer2_bridge_transfer
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_layer2_validator_updated_at BEFORE UPDATE ON layer2_validator
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();