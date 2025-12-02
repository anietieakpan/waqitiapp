package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReportResult {

    private UUID reportId;
    
    private ReconciliationReportData reportData;
    
    private boolean successful;
    
    private String message;
    
    private String reportUrl;
    
    private byte[] reportContent;
    
    private String reportFormat;
    
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();
    
    private String generatedBy;
    
    private Long generationTimeMs;
    
    private Map<String, Object> metadata;
    
    private ReportDeliveryStatus deliveryStatus;

    public enum ReportDeliveryStatus {
        GENERATED,
        DELIVERED,
        FAILED,
        PENDING_DELIVERY,
        SCHEDULED
    }

    public boolean isSuccessful() {
        return successful;
    }

    public boolean hasContent() {
        return reportContent != null && reportContent.length > 0;
    }

    public boolean hasUrl() {
        return reportUrl != null && !reportUrl.isEmpty();
    }

    public boolean isDelivered() {
        return ReportDeliveryStatus.DELIVERED.equals(deliveryStatus);
    }

    public boolean hasFailed() {
        return !successful || ReportDeliveryStatus.FAILED.equals(deliveryStatus);
    }
}