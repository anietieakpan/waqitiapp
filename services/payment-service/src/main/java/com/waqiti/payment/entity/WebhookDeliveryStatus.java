package com.waqiti.payment.entity;

/**
 * Webhook delivery status enumeration
 */
public enum WebhookDeliveryStatus {
    PENDING,
    SUCCESS,
    FAILED,
    RETRY_SCHEDULED,
    MAX_RETRIES_EXCEEDED,
    CIRCUIT_BREAKER_OPEN,
    DEAD_LETTER,
    ARCHIVED
}