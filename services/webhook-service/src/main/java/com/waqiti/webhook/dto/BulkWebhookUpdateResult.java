package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result DTO for bulk webhook subscription updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkWebhookUpdateResult {

    private int successCount;
    private int failureCount;
    private List<String> successIds;
    private List<String> failedIds;
}
