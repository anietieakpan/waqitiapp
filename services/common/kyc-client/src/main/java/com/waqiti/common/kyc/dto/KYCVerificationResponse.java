package com.waqiti.common.kyc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KYCVerificationResponse {

    private String id;
    private String userId;
    private KYCStatus status;
    private VerificationLevel verificationLevel;
    private String provider;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime expiresAt;
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<DocumentResponse> documents;
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> checks;
    
    private String rejectionReason;
    private Integer attemptCount;
    private Boolean canRetry;
    private String nextAction;
    private String verificationUrl;
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> metadata;
    
    private RiskAssessment riskAssessment;
    
    public enum KYCStatus {
        NOT_STARTED,
        PENDING,
        IN_PROGRESS,
        PENDING_REVIEW,
        REQUIRES_ADDITIONAL_INFO,
        APPROVED,
        REJECTED,
        EXPIRED,
        CANCELLED
    }
    
    public enum VerificationLevel {
        BASIC,
        INTERMEDIATE,
        ADVANCED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private String level;
        private Integer score;
        private List<String> flags;
        private String recommendation;
    }
}