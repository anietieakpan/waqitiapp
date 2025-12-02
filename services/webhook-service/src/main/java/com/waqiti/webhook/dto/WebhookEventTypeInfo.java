package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing information about a webhook event type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventTypeInfo {

    private String type;
    private String description;
    private String category;
    private boolean isCritical;
    private boolean isFinancial;
}
