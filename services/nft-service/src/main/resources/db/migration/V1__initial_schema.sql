-- NFT Service Initial Schema
-- Created: 2025-09-27
-- Description: NFT minting, ownership, marketplace, and metadata management schema

CREATE TABLE IF NOT EXISTS nft_collection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id VARCHAR(100) UNIQUE NOT NULL,
    collection_name VARCHAR(255) NOT NULL,
    collection_symbol VARCHAR(50) NOT NULL,
    description TEXT,
    collection_type VARCHAR(50) NOT NULL,
    blockchain_network VARCHAR(50) NOT NULL,
    contract_address VARCHAR(100) UNIQUE NOT NULL,
    contract_standard VARCHAR(50) NOT NULL,
    creator_address VARCHAR(100) NOT NULL,
    owner_address VARCHAR(100) NOT NULL,
    collection_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    total_supply INTEGER DEFAULT 0,
    max_supply INTEGER,
    minted_count INTEGER DEFAULT 0,
    burned_count INTEGER DEFAULT 0,
    circulating_supply INTEGER DEFAULT 0,
    royalty_percentage DECIMAL(5, 4) DEFAULT 0,
    royalty_recipient_address VARCHAR(100),
    base_uri VARCHAR(1000),
    collection_image_url VARCHAR(1000),
    collection_banner_url VARCHAR(1000),
    external_url VARCHAR(1000),
    category VARCHAR(100),
    tags TEXT[],
    is_verified BOOLEAN DEFAULT FALSE,
    is_featured BOOLEAN DEFAULT FALSE,
    floor_price DECIMAL(30, 18),
    floor_price_currency VARCHAR(50),
    total_volume DECIMAL(30, 18) DEFAULT 0,
    total_sales BIGINT DEFAULT 0,
    unique_owners INTEGER DEFAULT 0,
    metadata JSONB,
    deployment_transaction_hash VARCHAR(100),
    deployed_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS nft_token (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_id VARCHAR(100) UNIQUE NOT NULL,
    collection_id VARCHAR(100) NOT NULL,
    token_number VARCHAR(100) NOT NULL,
    token_uri VARCHAR(1000) NOT NULL,
    token_name VARCHAR(255) NOT NULL,
    description TEXT,
    blockchain_network VARCHAR(50) NOT NULL,
    contract_address VARCHAR(100) NOT NULL,
    token_standard VARCHAR(50) NOT NULL,
    owner_address VARCHAR(100) NOT NULL,
    creator_address VARCHAR(100) NOT NULL,
    token_status VARCHAR(20) NOT NULL DEFAULT 'MINTED',
    minted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mint_transaction_hash VARCHAR(100),
    mint_block_number BIGINT,
    burned_at TIMESTAMP,
    burn_transaction_hash VARCHAR(100),
    image_url VARCHAR(1000),
    animation_url VARCHAR(1000),
    external_url VARCHAR(1000),
    attributes JSONB NOT NULL,
    rarity_score DECIMAL(10, 4),
    rarity_rank INTEGER,
    rarity_tier VARCHAR(50),
    metadata_hash VARCHAR(100),
    metadata_frozen BOOLEAN DEFAULT FALSE,
    is_locked BOOLEAN DEFAULT FALSE,
    lock_expires_at TIMESTAMP,
    last_sale_price DECIMAL(30, 18),
    last_sale_currency VARCHAR(50),
    last_sale_date TIMESTAMP,
    total_sales INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    favorite_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_token_collection FOREIGN KEY (collection_id) REFERENCES nft_collection(collection_id) ON DELETE CASCADE,
    CONSTRAINT unique_collection_token_number UNIQUE (collection_id, token_number)
);

CREATE TABLE IF NOT EXISTS nft_ownership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ownership_id VARCHAR(100) UNIQUE NOT NULL,
    token_id VARCHAR(100) NOT NULL,
    owner_address VARCHAR(100) NOT NULL,
    previous_owner_address VARCHAR(100),
    ownership_type VARCHAR(50) NOT NULL DEFAULT 'FULL',
    quantity DECIMAL(30, 18) NOT NULL DEFAULT 1,
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acquisition_type VARCHAR(50) NOT NULL,
    acquisition_transaction_hash VARCHAR(100),
    acquisition_block_number BIGINT,
    acquisition_price DECIMAL(30, 18),
    acquisition_currency VARCHAR(50),
    ownership_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    transferred_at TIMESTAMP,
    transfer_transaction_hash VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_ownership_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_listing (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id VARCHAR(100) UNIQUE NOT NULL,
    token_id VARCHAR(100) NOT NULL,
    seller_address VARCHAR(100) NOT NULL,
    listing_type VARCHAR(50) NOT NULL,
    listing_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    price DECIMAL(30, 18) NOT NULL,
    currency VARCHAR(50) NOT NULL,
    quantity DECIMAL(30, 18) NOT NULL DEFAULT 1,
    quantity_available DECIMAL(30, 18) NOT NULL DEFAULT 1,
    minimum_bid DECIMAL(30, 18),
    reserve_price DECIMAL(30, 18),
    auction_start_time TIMESTAMP,
    auction_end_time TIMESTAMP,
    highest_bid DECIMAL(30, 18),
    highest_bidder_address VARCHAR(100),
    total_bids INTEGER DEFAULT 0,
    marketplace VARCHAR(100) NOT NULL,
    listing_fee DECIMAL(30, 18),
    royalty_amount DECIMAL(30, 18),
    expiration_date TIMESTAMP,
    auto_renew BOOLEAN DEFAULT FALSE,
    signature VARCHAR(500),
    nonce INTEGER,
    smart_contract_address VARCHAR(100),
    listed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sold_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(255),
    view_count INTEGER DEFAULT 0,
    favorite_count INTEGER DEFAULT 0,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_listing_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_bid (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_id VARCHAR(100) UNIQUE NOT NULL,
    listing_id VARCHAR(100) NOT NULL,
    token_id VARCHAR(100) NOT NULL,
    bidder_address VARCHAR(100) NOT NULL,
    bid_amount DECIMAL(30, 18) NOT NULL,
    currency VARCHAR(50) NOT NULL,
    bid_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    bid_type VARCHAR(50) NOT NULL,
    quantity DECIMAL(30, 18) DEFAULT 1,
    bid_expiration TIMESTAMP,
    signature VARCHAR(500),
    nonce INTEGER,
    is_highest_bid BOOLEAN DEFAULT FALSE,
    outbid_at TIMESTAMP,
    accepted_at TIMESTAMP,
    rejected_at TIMESTAMP,
    rejection_reason VARCHAR(255),
    cancelled_at TIMESTAMP,
    cancellation_reason VARCHAR(255),
    funds_locked BOOLEAN DEFAULT FALSE,
    escrow_transaction_hash VARCHAR(100),
    refund_transaction_hash VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_bid_listing FOREIGN KEY (listing_id) REFERENCES nft_listing(listing_id) ON DELETE CASCADE,
    CONSTRAINT fk_nft_bid_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_sale (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id VARCHAR(100) UNIQUE NOT NULL,
    token_id VARCHAR(100) NOT NULL,
    listing_id VARCHAR(100),
    seller_address VARCHAR(100) NOT NULL,
    buyer_address VARCHAR(100) NOT NULL,
    sale_type VARCHAR(50) NOT NULL,
    sale_price DECIMAL(30, 18) NOT NULL,
    currency VARCHAR(50) NOT NULL,
    quantity DECIMAL(30, 18) NOT NULL DEFAULT 1,
    platform_fee DECIMAL(30, 18),
    royalty_fee DECIMAL(30, 18),
    royalty_recipient_address VARCHAR(100),
    seller_proceeds DECIMAL(30, 18),
    transaction_hash VARCHAR(100) NOT NULL,
    block_number BIGINT NOT NULL,
    marketplace VARCHAR(100) NOT NULL,
    sale_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_token_address VARCHAR(100),
    gas_fee DECIMAL(30, 18),
    total_cost DECIMAL(30, 18),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_sale_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_transfer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id VARCHAR(100) UNIQUE NOT NULL,
    token_id VARCHAR(100) NOT NULL,
    from_address VARCHAR(100) NOT NULL,
    to_address VARCHAR(100) NOT NULL,
    transfer_type VARCHAR(50) NOT NULL,
    quantity DECIMAL(30, 18) NOT NULL DEFAULT 1,
    transaction_hash VARCHAR(100) NOT NULL,
    block_number BIGINT NOT NULL,
    blockchain_network VARCHAR(50) NOT NULL,
    gas_fee DECIMAL(30, 18),
    transfer_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    related_sale_id VARCHAR(100),
    is_mint BOOLEAN DEFAULT FALSE,
    is_burn BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_transfer_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metadata_id VARCHAR(100) UNIQUE NOT NULL,
    token_id VARCHAR(100) NOT NULL,
    metadata_type VARCHAR(50) NOT NULL,
    metadata_uri VARCHAR(1000) NOT NULL,
    metadata_content JSONB NOT NULL,
    metadata_hash VARCHAR(100),
    storage_type VARCHAR(50) NOT NULL,
    storage_location VARCHAR(1000),
    is_pinned BOOLEAN DEFAULT FALSE,
    pin_service VARCHAR(100),
    is_frozen BOOLEAN DEFAULT FALSE,
    frozen_at TIMESTAMP,
    version INTEGER DEFAULT 1,
    previous_version_id VARCHAR(100),
    cached BOOLEAN DEFAULT FALSE,
    cache_expires_at TIMESTAMP,
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_metadata_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_royalty (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    royalty_id VARCHAR(100) UNIQUE NOT NULL,
    collection_id VARCHAR(100),
    token_id VARCHAR(100),
    sale_id VARCHAR(100),
    royalty_type VARCHAR(50) NOT NULL,
    recipient_address VARCHAR(100) NOT NULL,
    royalty_percentage DECIMAL(5, 4) NOT NULL,
    royalty_amount DECIMAL(30, 18) NOT NULL,
    currency VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    transaction_hash VARCHAR(100),
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_royalty_collection FOREIGN KEY (collection_id) REFERENCES nft_collection(collection_id) ON DELETE CASCADE,
    CONSTRAINT fk_nft_royalty_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE,
    CONSTRAINT fk_nft_royalty_sale FOREIGN KEY (sale_id) REFERENCES nft_sale(sale_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_favorite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    favorite_id VARCHAR(100) UNIQUE NOT NULL,
    user_address VARCHAR(100) NOT NULL,
    token_id VARCHAR(100),
    collection_id VARCHAR(100),
    favorited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_favorite_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE,
    CONSTRAINT fk_nft_favorite_collection FOREIGN KEY (collection_id) REFERENCES nft_collection(collection_id) ON DELETE CASCADE,
    CONSTRAINT unique_user_token_favorite UNIQUE (user_address, token_id),
    CONSTRAINT unique_user_collection_favorite UNIQUE (user_address, collection_id)
);

CREATE TABLE IF NOT EXISTS nft_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id VARCHAR(100) UNIQUE NOT NULL,
    token_id VARCHAR(100) NOT NULL,
    collection_id VARCHAR(100) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    from_address VARCHAR(100),
    to_address VARCHAR(100),
    price DECIMAL(30, 18),
    currency VARCHAR(50),
    quantity DECIMAL(30, 18) DEFAULT 1,
    transaction_hash VARCHAR(100),
    block_number BIGINT,
    marketplace VARCHAR(100),
    activity_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nft_activity_token FOREIGN KEY (token_id) REFERENCES nft_token(token_id) ON DELETE CASCADE,
    CONSTRAINT fk_nft_activity_collection FOREIGN KEY (collection_id) REFERENCES nft_collection(collection_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS nft_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    analytics_id VARCHAR(100) UNIQUE NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    total_collections INTEGER DEFAULT 0,
    active_collections INTEGER DEFAULT 0,
    total_tokens BIGINT DEFAULT 0,
    tokens_minted BIGINT DEFAULT 0,
    tokens_burned BIGINT DEFAULT 0,
    total_sales BIGINT DEFAULT 0,
    sales_volume DECIMAL(30, 18) DEFAULT 0,
    avg_sale_price DECIMAL(30, 18),
    floor_price_avg DECIMAL(30, 18),
    total_listings BIGINT DEFAULT 0,
    active_listings INTEGER DEFAULT 0,
    total_bids BIGINT DEFAULT 0,
    unique_buyers INTEGER DEFAULT 0,
    unique_sellers INTEGER DEFAULT 0,
    by_collection JSONB,
    by_marketplace JSONB,
    by_blockchain JSONB,
    top_collections JSONB,
    trending_collections JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS nft_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_nft_collections INTEGER DEFAULT 0,
    total_nfts_minted BIGINT DEFAULT 0,
    total_nft_sales BIGINT DEFAULT 0,
    nft_sales_volume DECIMAL(30, 18) DEFAULT 0,
    avg_nft_price DECIMAL(30, 18),
    unique_nft_traders INTEGER DEFAULT 0,
    total_marketplace_fees DECIMAL(30, 18) DEFAULT 0,
    total_royalties_paid DECIMAL(30, 18) DEFAULT 0,
    by_blockchain_network JSONB,
    by_collection_category JSONB,
    by_marketplace JSONB,
    top_selling_collections JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_nft_period UNIQUE (period_start, period_end)
);

-- Indexes for performance
CREATE INDEX idx_nft_collection_creator ON nft_collection(creator_address);
CREATE INDEX idx_nft_collection_owner ON nft_collection(owner_address);
CREATE INDEX idx_nft_collection_network ON nft_collection(blockchain_network);
CREATE INDEX idx_nft_collection_status ON nft_collection(collection_status);
CREATE INDEX idx_nft_collection_verified ON nft_collection(is_verified) WHERE is_verified = true;
CREATE INDEX idx_nft_collection_featured ON nft_collection(is_featured) WHERE is_featured = true;

CREATE INDEX idx_nft_token_collection ON nft_token(collection_id);
CREATE INDEX idx_nft_token_owner ON nft_token(owner_address);
CREATE INDEX idx_nft_token_creator ON nft_token(creator_address);
CREATE INDEX idx_nft_token_contract ON nft_token(contract_address);
CREATE INDEX idx_nft_token_status ON nft_token(token_status);
CREATE INDEX idx_nft_token_minted ON nft_token(minted_at DESC);

CREATE INDEX idx_nft_ownership_token ON nft_ownership(token_id);
CREATE INDEX idx_nft_ownership_owner ON nft_ownership(owner_address);
CREATE INDEX idx_nft_ownership_status ON nft_ownership(ownership_status);
CREATE INDEX idx_nft_ownership_acquired ON nft_ownership(acquired_at DESC);

CREATE INDEX idx_nft_listing_token ON nft_listing(token_id);
CREATE INDEX idx_nft_listing_seller ON nft_listing(seller_address);
CREATE INDEX idx_nft_listing_status ON nft_listing(listing_status);
CREATE INDEX idx_nft_listing_marketplace ON nft_listing(marketplace);
CREATE INDEX idx_nft_listing_type ON nft_listing(listing_type);
CREATE INDEX idx_nft_listing_price ON nft_listing(price);

CREATE INDEX idx_nft_bid_listing ON nft_bid(listing_id);
CREATE INDEX idx_nft_bid_token ON nft_bid(token_id);
CREATE INDEX idx_nft_bid_bidder ON nft_bid(bidder_address);
CREATE INDEX idx_nft_bid_status ON nft_bid(bid_status);
CREATE INDEX idx_nft_bid_highest ON nft_bid(is_highest_bid) WHERE is_highest_bid = true;

CREATE INDEX idx_nft_sale_token ON nft_sale(token_id);
CREATE INDEX idx_nft_sale_seller ON nft_sale(seller_address);
CREATE INDEX idx_nft_sale_buyer ON nft_sale(buyer_address);
CREATE INDEX idx_nft_sale_timestamp ON nft_sale(sale_timestamp DESC);
CREATE INDEX idx_nft_sale_marketplace ON nft_sale(marketplace);

CREATE INDEX idx_nft_transfer_token ON nft_transfer(token_id);
CREATE INDEX idx_nft_transfer_from ON nft_transfer(from_address);
CREATE INDEX idx_nft_transfer_to ON nft_transfer(to_address);
CREATE INDEX idx_nft_transfer_timestamp ON nft_transfer(transfer_timestamp DESC);
CREATE INDEX idx_nft_transfer_transaction ON nft_transfer(transaction_hash);

CREATE INDEX idx_nft_metadata_token ON nft_metadata(token_id);
CREATE INDEX idx_nft_metadata_type ON nft_metadata(metadata_type);
CREATE INDEX idx_nft_metadata_hash ON nft_metadata(metadata_hash);

CREATE INDEX idx_nft_royalty_collection ON nft_royalty(collection_id);
CREATE INDEX idx_nft_royalty_token ON nft_royalty(token_id);
CREATE INDEX idx_nft_royalty_recipient ON nft_royalty(recipient_address);
CREATE INDEX idx_nft_royalty_status ON nft_royalty(payment_status);

CREATE INDEX idx_nft_favorite_user ON nft_favorite(user_address);
CREATE INDEX idx_nft_favorite_token ON nft_favorite(token_id);
CREATE INDEX idx_nft_favorite_collection ON nft_favorite(collection_id);

CREATE INDEX idx_nft_activity_token ON nft_activity(token_id);
CREATE INDEX idx_nft_activity_collection ON nft_activity(collection_id);
CREATE INDEX idx_nft_activity_type ON nft_activity(activity_type);
CREATE INDEX idx_nft_activity_timestamp ON nft_activity(activity_timestamp DESC);

CREATE INDEX idx_nft_analytics_period ON nft_analytics(period_end DESC);
CREATE INDEX idx_nft_statistics_period ON nft_statistics(period_end DESC);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_nft_collection_updated_at BEFORE UPDATE ON nft_collection
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_nft_token_updated_at BEFORE UPDATE ON nft_token
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_nft_ownership_updated_at BEFORE UPDATE ON nft_ownership
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_nft_listing_updated_at BEFORE UPDATE ON nft_listing
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_nft_bid_updated_at BEFORE UPDATE ON nft_bid
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_nft_metadata_updated_at BEFORE UPDATE ON nft_metadata
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_nft_royalty_updated_at BEFORE UPDATE ON nft_royalty
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();