package com.waqiti.webhook.entity;

public enum WebhookPriority {
    LOW("Low priority webhook"),
    NORMAL("Normal priority webhook"),
    HIGH("High priority webhook"),
    CRITICAL("Critical priority webhook - immediate delivery");
    
    private final String description;
    
    WebhookPriority(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getPriorityValue() {
        switch (this) {
            case CRITICAL: return 0;
            case HIGH: return 1;
            case NORMAL: return 2;
            case LOW: return 3;
            default: return 2;
        }
    }
}
