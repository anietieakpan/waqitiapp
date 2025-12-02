package com.waqiti.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTopicOperationResponse {
    private int totalRequested;
    private int successCount;
    private int failureCount;
    private int skippedCount;
    private List<String> successfulTopics;
    private List<BulkOperationError> errors;
    private long processingTimeMs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkOperationError {
        private String topicName;
        private String errorCode;
        private String errorMessage;
    }
}