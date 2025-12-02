package com.waqiti.webhook.entity;

public enum WebhookHttpMethod {
    POST("POST request"),
    PUT("PUT request"),
    PATCH("PATCH request");
    
    private final String description;
    
    WebhookHttpMethod(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
