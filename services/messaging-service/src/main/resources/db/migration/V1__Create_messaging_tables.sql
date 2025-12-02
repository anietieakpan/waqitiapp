-- Messaging Service Database Schema
-- Creates tables for end-to-end encrypted messaging and key management

-- User key bundles for Signal protocol
CREATE TABLE user_key_bundles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    registration_id INTEGER NOT NULL,
    
    -- Identity keys
    identity_key TEXT NOT NULL,
    
    -- Signed pre-keys
    signed_pre_key_id INTEGER NOT NULL,
    signed_pre_key TEXT NOT NULL,
    signed_pre_key_signature TEXT NOT NULL,
    signed_pre_key_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Metadata
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(user_id, device_id)
);

-- One-time pre-keys
CREATE TABLE pre_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_bundle_id UUID NOT NULL REFERENCES user_key_bundles(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL,
    public_key TEXT NOT NULL,
    private_key TEXT NOT NULL, -- Encrypted in application layer
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(key_bundle_id, key_id)
);

-- Message threads/conversations
CREATE TABLE message_threads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_type VARCHAR(20) NOT NULL CHECK (thread_type IN ('DIRECT', 'GROUP')),
    name VARCHAR(255),
    description TEXT,
    
    -- Thread metadata
    participant_count INTEGER DEFAULT 0,
    message_count INTEGER DEFAULT 0,
    last_message_at TIMESTAMP WITH TIME ZONE,
    
    -- Settings
    encryption_enabled BOOLEAN DEFAULT TRUE,
    auto_delete_after_days INTEGER,
    
    -- Audit
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Thread participants
CREATE TABLE thread_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES message_threads(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    
    -- Participant status
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED', 'MUTED')),
    role VARCHAR(20) DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'ADMIN', 'OWNER')),
    
    -- Participation details
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_read_at TIMESTAMP WITH TIME ZONE,
    last_active_at TIMESTAMP WITH TIME ZONE,
    
    -- Notification settings
    notifications_enabled BOOLEAN DEFAULT TRUE,
    mention_notifications_enabled BOOLEAN DEFAULT TRUE,
    
    UNIQUE(thread_id, user_id)
);

-- Messages
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES message_threads(id),
    sender_id UUID NOT NULL,
    
    -- Message content (encrypted)
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' CHECK (message_type IN ('TEXT', 'IMAGE', 'FILE', 'PAYMENT', 'SYSTEM')),
    encrypted_content TEXT NOT NULL,
    content_hash VARCHAR(64), -- For integrity verification
    
    -- Encryption details
    sender_key_id INTEGER,
    recipient_key_id INTEGER,
    
    -- Message metadata
    reply_to_message_id UUID REFERENCES messages(id),
    edited_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    
    -- Payment-related fields
    payment_id UUID,
    payment_amount DECIMAL(15,2),
    payment_currency VARCHAR(3),
    
    -- Delivery tracking
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE,
    
    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Message delivery status per recipient
CREATE TABLE message_delivery_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL,
    
    -- Delivery status
    status VARCHAR(20) DEFAULT 'SENT' CHECK (status IN ('SENT', 'DELIVERED', 'READ', 'FAILED')),
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    
    -- Error details for failed delivery
    error_code VARCHAR(50),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(message_id, recipient_id)
);

-- Message attachments
CREATE TABLE message_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    
    -- File details
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    
    -- Storage details
    storage_key VARCHAR(500) NOT NULL, -- S3 key or similar
    encrypted_key TEXT, -- Encryption key for file
    
    -- Metadata
    thumbnail_storage_key VARCHAR(500),
    duration_seconds INTEGER, -- For audio/video
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Session management for double ratchet
CREATE TABLE message_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    sender_device_id VARCHAR(100) NOT NULL,
    recipient_device_id VARCHAR(100) NOT NULL,
    
    -- Session state (encrypted)
    session_data TEXT NOT NULL,
    
    -- Session metadata
    established_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    message_count INTEGER DEFAULT 0,
    
    UNIQUE(sender_id, recipient_id, sender_device_id, recipient_device_id)
);

-- Indexes for performance
CREATE INDEX idx_user_key_bundles_user_id ON user_key_bundles(user_id);
CREATE INDEX idx_user_key_bundles_device_id ON user_key_bundles(device_id);

CREATE INDEX idx_pre_keys_key_bundle_id ON pre_keys(key_bundle_id);
CREATE INDEX idx_pre_keys_used ON pre_keys(used, used_at);

CREATE INDEX idx_message_threads_created_by ON message_threads(created_by);
CREATE INDEX idx_message_threads_last_message_at ON message_threads(last_message_at);

CREATE INDEX idx_thread_participants_thread_id ON thread_participants(thread_id);
CREATE INDEX idx_thread_participants_user_id ON thread_participants(user_id);
CREATE INDEX idx_thread_participants_status ON thread_participants(status);

CREATE INDEX idx_messages_thread_id ON messages(thread_id);
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_sent_at ON messages(sent_at);
CREATE INDEX idx_messages_thread_sent_at ON messages(thread_id, sent_at);
CREATE INDEX idx_messages_payment_id ON messages(payment_id) WHERE payment_id IS NOT NULL;

CREATE INDEX idx_message_delivery_status_message_id ON message_delivery_status(message_id);
CREATE INDEX idx_message_delivery_status_recipient_id ON message_delivery_status(recipient_id);
CREATE INDEX idx_message_delivery_status_status ON message_delivery_status(status);

CREATE INDEX idx_message_attachments_message_id ON message_attachments(message_id);

CREATE INDEX idx_message_sessions_sender_recipient ON message_sessions(sender_id, recipient_id);
CREATE INDEX idx_message_sessions_last_used_at ON message_sessions(last_used_at);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_key_bundles_updated_at BEFORE UPDATE
    ON user_key_bundles FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_message_threads_updated_at BEFORE UPDATE
    ON message_threads FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_messages_updated_at BEFORE UPDATE
    ON messages FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

CREATE TRIGGER update_message_delivery_status_updated_at BEFORE UPDATE
    ON message_delivery_status FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- Functions to update thread metadata
CREATE OR REPLACE FUNCTION update_thread_message_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE message_threads 
        SET message_count = message_count + 1,
            last_message_at = NEW.sent_at
        WHERE id = NEW.thread_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE message_threads 
        SET message_count = message_count - 1
        WHERE id = OLD.thread_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_thread_on_message_change
    AFTER INSERT OR DELETE ON messages
    FOR EACH ROW EXECUTE PROCEDURE update_thread_message_count();

-- Function to update participant count
CREATE OR REPLACE FUNCTION update_thread_participant_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE message_threads 
        SET participant_count = participant_count + 1
        WHERE id = NEW.thread_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE message_threads 
        SET participant_count = participant_count - 1
        WHERE id = OLD.thread_id;
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' AND OLD.status = 'ACTIVE' AND NEW.status != 'ACTIVE' THEN
        UPDATE message_threads 
        SET participant_count = participant_count - 1
        WHERE id = NEW.thread_id;
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' AND OLD.status != 'ACTIVE' AND NEW.status = 'ACTIVE' THEN
        UPDATE message_threads 
        SET participant_count = participant_count + 1
        WHERE id = NEW.thread_id;
        RETURN NEW;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_thread_on_participant_change
    AFTER INSERT OR DELETE OR UPDATE ON thread_participants
    FOR EACH ROW EXECUTE PROCEDURE update_thread_participant_count();

-- Comments for documentation
COMMENT ON TABLE user_key_bundles IS 'Signal protocol key bundles for end-to-end encryption';
COMMENT ON TABLE pre_keys IS 'One-time pre-keys for Perfect Forward Secrecy';
COMMENT ON TABLE message_threads IS 'Message conversation threads';
COMMENT ON TABLE thread_participants IS 'Users participating in message threads';
COMMENT ON TABLE messages IS 'Encrypted messages within threads';
COMMENT ON TABLE message_delivery_status IS 'Per-recipient delivery status for messages';
COMMENT ON TABLE message_attachments IS 'File attachments for messages';
COMMENT ON TABLE message_sessions IS 'Double ratchet session state for encryption';