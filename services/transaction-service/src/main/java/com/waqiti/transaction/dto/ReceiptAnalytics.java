package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Receipt analytics and statistics DTO
 */
@Data
@Builder
public class ReceiptAnalytics {

    private long totalReceiptsGenerated;
    private long totalReceiptsEmailed;
    private long totalReceiptsDownloaded;
    private long totalStorageUsed; // in bytes
    
    private Map<String, Long> receiptsByFormat;
    private Map<String, Long> receiptsByMonth;
    private Map<String, Long> receiptsByDay;
    
    private double averageReceiptSize;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    private TopRecipients topRecipients;
    private SecurityMetrics securityMetrics;
    
    @Data
    @Builder
    public static class TopRecipients {
        private Map<String, Long> byEmailCount;
        private Map<String, Long> byDownloadCount;
    }
    
    @Data
    @Builder
    public static class SecurityMetrics {
        private long securityValidationsPassed;
        private long securityValidationsFailed;
        private long suspiciousActivityDetected;
        private double averageSecurityScore;
    }
}