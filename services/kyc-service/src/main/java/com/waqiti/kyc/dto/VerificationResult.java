package com.waqiti.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResult {
    
    private String verificationId;
    
    private boolean success;
    
    private int score; // 0-100, where 100 is highest confidence
    
    private String status; // PASS, FAIL, MANUAL_REVIEW, ERROR
    
    private String provider;
    
    private LocalDateTime verifiedAt;
    
    private Map<String, Object> details;
    
    private String reportUrl; // URL to detailed report if available
    
    private String errorMessage;
    
    private String errorCode;
    
    // Convenience methods
    public boolean requiresManualReview() {
        return "MANUAL_REVIEW".equals(status);
    }
    
    public boolean isHighConfidence() {
        return score >= 85;
    }
    
    public boolean isMediumConfidence() {
        return score >= 60 && score < 85;
    }
    
    public boolean isLowConfidence() {
        return score < 60;
    }
}