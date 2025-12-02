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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KYCStatusResponse {

    private String userId;
    private KYCStatus currentStatus;
    private VerificationLevel currentLevel;
    private VerificationLevel targetLevel;
    private LocalDateTime lastVerifiedAt;
    private LocalDateTime expiresAt;
    private Boolean isActive;
    private Boolean canUpgrade;
    private List<String> requiredDocuments;
    private List<String> completedChecks;
    private List<String> pendingChecks;
    private String nextAction;
    private Integer daysUntilExpiry;
    private ComplianceStatus complianceStatus;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceStatus {
        private Boolean isCompliant;
        private List<String> regions;
        private List<String> restrictions;
        private LocalDateTime lastCheckedAt;
    }
}