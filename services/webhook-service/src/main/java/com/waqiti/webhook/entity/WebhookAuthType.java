package com.waqiti.webhook.entity;

public enum WebhookAuthType {
    NONE("No authentication"),
    BASIC("Basic authentication"),
    BEARER("Bearer token authentication"),
    HMAC_SHA256("HMAC-SHA256 signature"),
    HMAC_SHA512("HMAC-SHA512 signature"),
    API_KEY("API key authentication"),
    OAUTH2("OAuth 2.0 authentication");
    
    private final String description;
    
    WebhookAuthType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean requiresSecret() {
        return this != NONE;
    }
}
