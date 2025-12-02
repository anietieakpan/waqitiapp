package com.waqiti.webhook.entity;

/**
 * Enums for webhook management
 */

public enum WebhookStatus {
    PENDING("Webhook pending delivery"),
    IN_PROGRESS("Webhook delivery in progress"),
    DELIVERED("Webhook delivered successfully"),
    PENDING_RETRY("Webhook pending retry after failure"),
    FAILED("Webhook delivery failed permanently"),
    EXPIRED("Webhook expired without delivery"),
    CANCELLED("Webhook delivery cancelled");
    
    private final String description;
    
    WebhookStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED || this == EXPIRED || this == CANCELLED;
    }
    
    public boolean canRetry() {
        return this == PENDING || this == PENDING_RETRY || this == IN_PROGRESS;
    }
}

