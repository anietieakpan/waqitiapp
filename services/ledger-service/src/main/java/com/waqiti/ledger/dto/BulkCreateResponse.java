package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Bulk Create Response DTO
 * 
 * Response structure for bulk creation operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCreateResponse {
    
    private int totalRequested;
    private int successCount;
    private int failureCount;
    private List<CreatedItem> createdItems;
    private List<FailedItem> failedItems;
    private Map<String, Object> summary;
    private LocalDateTime processedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedItem {
        private String id;
        private String code;
        private String name;
        private String type;
        private Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        private int index;
        private String code;
        private String name;
        private String reason;
        private Map<String, Object> data;
    }
    
    public boolean isCompleteSuccess() {
        return failureCount == 0 && successCount == totalRequested;
    }
    
    public boolean isPartialSuccess() {
        return successCount > 0 && failureCount > 0;
    }
    
    public boolean isCompleteFailure() {
        return successCount == 0 && failureCount == totalRequested;
    }
}