-- Email Outbox Table - Transactional Outbox Pattern
-- Ensures reliable email delivery with automatic retry

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Recipient information
    recipient_email VARCHAR(255) NOT NULL,
    recipient_name VARCHAR(255),

    -- Sender information
    sender_email VARCHAR(255) NOT NULL,
    sender_name VARCHAR(255),

    -- Email content
    subject VARCHAR(500) NOT NULL,
    html_content TEXT NOT NULL,
    plain_text_content TEXT,

    -- Template support
    template_id VARCHAR(100),
    template_data JSONB,

    -- Attachments (filename -> base64/URL mapping)
    attachments JSONB,

    -- Email metadata
    email_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 5,

    -- Retry logic
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP,

    -- SendGrid integration
    sendgrid_message_id VARCHAR(255),

    -- Error tracking
    error_message TEXT,
    error_stack_trace TEXT,

    -- Delivery tracking
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    opened_at TIMESTAMP,
    clicked_at TIMESTAMP,
    bounced_at TIMESTAMP,
    bounce_reason VARCHAR(500),

    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT chk_priority CHECK (priority >= 1 AND priority <= 10),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0)
);

-- Indexes for efficient querying

-- Index for finding emails ready to send (most frequent query)
CREATE INDEX idx_email_status_created
    ON email_outbox(status, created_at);

-- Index for retry scheduling
CREATE INDEX idx_email_next_retry
    ON email_outbox(next_retry_at, status)
    WHERE status = 'RETRY_SCHEDULED';

-- Index for finding emails by recipient (for user queries)
CREATE INDEX idx_email_recipient
    ON email_outbox(recipient_email);

-- Index for SendGrid webhook lookups
CREATE INDEX idx_email_sendgrid_message_id
    ON email_outbox(sendgrid_message_id)
    WHERE sendgrid_message_id IS NOT NULL;

-- Index for monitoring stuck emails
CREATE INDEX idx_email_stuck
    ON email_outbox(status, created_at)
    WHERE status IN ('PENDING', 'SENDING');

-- Index for email type analysis
CREATE INDEX idx_email_type_status
    ON email_outbox(email_type, status);

-- Comments for documentation
COMMENT ON TABLE email_outbox IS 'Transactional outbox for reliable email delivery with automatic retry';
COMMENT ON COLUMN email_outbox.status IS 'PENDING, SENDING, SENT, DELIVERED, OPENED, CLICKED, BOUNCED, FAILED, RETRY_SCHEDULED, CANCELLED';
COMMENT ON COLUMN email_outbox.priority IS 'Priority 1-10, where 1 is highest priority';
COMMENT ON COLUMN email_outbox.retry_count IS 'Number of retry attempts made';
COMMENT ON COLUMN email_outbox.next_retry_at IS 'When to retry next (exponential backoff)';
COMMENT ON COLUMN email_outbox.sendgrid_message_id IS 'SendGrid message ID for webhook correlation';
