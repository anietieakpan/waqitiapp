package com.waqiti.webhook.model;

/**
 * Status enumeration for webhooks and deliveries
 */
public enum WebhookStatus {
    ACTIVE,
    DISABLED,
    PENDING,
    FAILED,
    COMPLETED,
    RETRYING,
    MAX_RETRIES_EXCEEDED,
    CANCELLED
}