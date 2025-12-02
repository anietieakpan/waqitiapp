package com.waqiti.webhook.domain;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook payload domain class
 * Represents the data structure for webhook delivery
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebhookPayload {
    private String eventId;
    private String eventType;
    private String endpointUrl;
    private Map<String, Object> data;
    private String secret;
    private WebhookPriority priority;
    private Map<String, String> customHeaders;
    private LocalDateTime timestamp;
    private String correlationId;
    private String tenantId;
    private Integer version;
    private Map<String, Object> metadata;
    
    public enum WebhookPriority {
        LOW(3),
        NORMAL(2),
        HIGH(1),
        CRITICAL(0);
        
        private final int order;
        
        WebhookPriority(int order) {
            this.order = order;
        }
        
        public int getOrder() {
            return order;
        }
    }
    
    public WebhookPriority getPriority() {
        return priority != null ? priority : WebhookPriority.NORMAL;
    }
    
    public boolean isCritical() {
        return getPriority() == WebhookPriority.CRITICAL;
    }
    
    public boolean isHighPriority() {
        return getPriority() == WebhookPriority.HIGH || getPriority() == WebhookPriority.CRITICAL;
    }
}