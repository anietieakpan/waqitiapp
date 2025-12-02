package com.waqiti.webhook.dto;

import com.waqiti.webhook.model.WebhookEventType;
import com.waqiti.webhook.model.WebhookStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscriptionDTO {
    private String id;
    private String userId;
    private String clientId;
    private String url;
    private List<WebhookEventType> eventTypes;
    private Boolean isActive;
    private WebhookStatus status;
    private String description;
    private Map<String, String> headers;
    private Integer timeout;
    private Integer maxRetries;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastDeliveryAt;
    private Long successfulDeliveries;
    private Long failedDeliveries;
    private String error; // For error responses
}