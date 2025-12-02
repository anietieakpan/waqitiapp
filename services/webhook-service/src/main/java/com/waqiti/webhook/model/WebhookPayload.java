package com.waqiti.webhook.model;

import com.waqiti.webhook.entity.WebhookEventType;
import com.waqiti.webhook.entity.WebhookPriority;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook payload to be delivered
 */
@Data
@Builder
public class WebhookPayload {
    
    private String eventId;
    private String eventType;
    private String endpointUrl;
    private String secret;
    private WebhookPriority priority;
    private LocalDateTime timestamp;
    private Object data;
    private Map<String, String> customHeaders;
    private Map<String, Object> metadata;
    
    // Retry configuration override
    private Integer maxRetries;
    private Long initialDelayMs;
    private Double backoffMultiplier;
    
    /**
     * Create payment webhook payload
     */
    public static WebhookPayload payment(String eventId, String eventType, String endpointUrl, 
                                        String secret, Object paymentData) {
        return WebhookPayload.builder()
            .eventId(eventId)
            .eventType(eventType)
            .endpointUrl(endpointUrl)
            .secret(secret)
            .priority(WebhookPriority.HIGH)
            .timestamp(LocalDateTime.now())
            .data(paymentData)
            .build();
    }
    
    /**
     * Create critical webhook payload
     */
    public static WebhookPayload critical(String eventId, WebhookEventType eventType, 
                                         String endpointUrl, String secret, Object data) {
        return WebhookPayload.builder()
            .eventId(eventId)
            .eventType(eventType.name())
            .endpointUrl(endpointUrl)
            .secret(secret)
            .priority(WebhookPriority.CRITICAL)
            .timestamp(LocalDateTime.now())
            .data(data)
            .maxRetries(10) // More retries for critical events
            .initialDelayMs(500L) // Faster initial retry
            .build();
    }
    
    /**
     * Create notification webhook payload
     */
    public static WebhookPayload notification(String eventId, String eventType, 
                                             String endpointUrl, Object data) {
        return WebhookPayload.builder()
            .eventId(eventId)
            .eventType(eventType)
            .endpointUrl(endpointUrl)
            .priority(WebhookPriority.NORMAL)
            .timestamp(LocalDateTime.now())
            .data(data)
            .build();
    }
    
    /**
     * Check if payload is for critical event
     */
    public boolean isCritical() {
        return priority == WebhookPriority.CRITICAL ||
               (eventType != null && WebhookEventType.valueOf(eventType).isCritical());
    }
    
    /**
     * Check if payload is for financial event
     */
    public boolean isFinancial() {
        try {
            return eventType != null && WebhookEventType.valueOf(eventType).isFinancial();
        } catch (IllegalArgumentException e) {
            return eventType != null && (
                eventType.contains("PAYMENT") || 
                eventType.contains("TRANSACTION") || 
                eventType.contains("WALLET")
            );
        }
    }
}