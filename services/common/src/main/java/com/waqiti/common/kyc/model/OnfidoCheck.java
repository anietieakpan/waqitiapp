package com.waqiti.common.kyc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Onfido check model for KYC verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnfidoCheck {
    
    private String id;
    private String href;
    private String status;
    private String result;
    private String type;
    private List<String> tags;
    private List<OnfidoReport> reports;
    private LocalDateTime createdAt;
    private String downloadUri;
    private String formUri;
    private String redirectUri;
    private Map<String, Object> options;
    
    /**
     * Onfido report model
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnfidoReport {
        private String id;
        private String href;
        private String name;
        private String status;
        private String result;
        private String subResult;
        private String variant;
        private LocalDateTime createdAt;
        private Map<String, Object> breakdown;
        private List<String> properties;
        private Map<String, Object> options;
    }
    
    /**
     * Check if verification is complete
     */
    public boolean isComplete() {
        return "complete".equals(status);
    }
    
    /**
     * Check if verification passed
     */
    public boolean isPassed() {
        return isComplete() && "clear".equals(result);
    }
    
    /**
     * Check if verification needs review
     */
    public boolean needsReview() {
        return "consider".equals(result);
    }
    
    /**
     * Check if verification failed
     */
    public boolean isFailed() {
        return "unidentified".equals(result) || "rejected".equals(result);
    }
}