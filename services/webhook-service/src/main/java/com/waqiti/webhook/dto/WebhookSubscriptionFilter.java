package com.waqiti.webhook.dto;

import com.waqiti.webhook.model.WebhookEventType;
import com.waqiti.webhook.model.WebhookStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter for querying webhook subscriptions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscriptionFilter {

    private String userId;
    private String clientId;
    private String tenantId;
    private WebhookEventType eventType;
    private WebhookStatus status;
    private Boolean isActive;
    private String urlPattern;
    private String searchTerm;
}
