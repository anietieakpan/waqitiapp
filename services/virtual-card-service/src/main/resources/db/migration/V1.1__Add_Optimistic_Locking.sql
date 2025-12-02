-- Add optimistic locking version column to virtual_cards table
-- This prevents race conditions during concurrent card transactions

ALTER TABLE virtual_cards 
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Create index for performance
CREATE INDEX idx_virtual_cards_version ON virtual_cards(version);

-- Update comment for documentation
COMMENT ON COLUMN virtual_cards.version IS 'Optimistic locking version to prevent concurrent modification race conditions in financial operations';