package com.waqiti.common.webhook;

/**
 * Webhook status enum
 */
public enum WebhookStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    RETRYING,
    DEAD_LETTER
}