package com.waqiti.gdpr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for user consent records in GDPR export
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentsDataDTO {

    private String userId;
    private List<ConsentRecordDTO> consentRecords;
    private List<ConsentHistoryDTO> consentHistory;
    private ConsentSummaryDTO summary;

    // Data retrieval metadata
    private boolean dataRetrievalFailed;
    private String failureReason;
    private boolean requiresManualReview;
    private LocalDateTime retrievedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentRecordDTO {
        private String consentId;
        private String purpose;
        private String legalBasis;
        private boolean granted;
        private LocalDateTime grantedAt;
        private LocalDateTime withdrawnAt;
        private String version;
        private String ipAddress;
        private String userAgent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentHistoryDTO {
        private String consentId;
        private String action;
        private LocalDateTime actionAt;
        private String reason;
        private String performedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentSummaryDTO {
        private Integer totalConsents;
        private Integer activeConsents;
        private Integer withdrawnConsents;
        private LocalDateTime lastConsentUpdate;
    }
}
