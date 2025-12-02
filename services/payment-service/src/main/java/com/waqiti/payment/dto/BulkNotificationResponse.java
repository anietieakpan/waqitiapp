package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for bulk notification responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkNotificationResponse {
    
    private String batchId;
    private int totalRequested;
    private int totalSent;
    private int totalFailed;
    private int totalPending;
    
    private List<String> successfulRecipients;
    private List<String> failedRecipients;
    private Map<String, String> failureReasons;
    
    private Instant processedAt;
    private Long processingTimeMs;
    
    private String status; // PROCESSING, COMPLETED, FAILED
    private String message;
    
    @Builder.Default
    private boolean success = true;
}