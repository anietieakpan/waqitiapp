package com.waqiti.kyc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.waqiti.kyc.domain.KYCVerification.KYCStatus;
import com.waqiti.kyc.domain.KYCVerification.VerificationLevel;
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
    private String verificationUrl; // For redirect-based verification
    
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> metadata;
    
    private RiskAssessment riskAssessment;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private String level; // LOW, MEDIUM, HIGH
        private Integer score;
        private List<String> flags;
        private String recommendation;
    }
}