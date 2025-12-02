package com.waqiti.webhook.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for bulk webhook subscription updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkWebhookUpdateRequest {

    @NotEmpty(message = "Subscription IDs list cannot be empty")
    private List<String> subscriptionIds;

    private Boolean isActive;
    private Integer maxRetries;
    private Integer timeout;
}
