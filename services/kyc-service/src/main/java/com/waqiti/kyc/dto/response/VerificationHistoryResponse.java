package com.waqiti.kyc.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class VerificationHistoryResponse {

    private String userId;
    private Integer totalAttempts;
    private Integer successfulAttempts;
    private Integer failedAttempts;
    private LocalDateTime firstAttemptAt;
    private LocalDateTime lastAttemptAt;
    private List<VerificationAttempt> attempts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationAttempt {
        private String id;
        private String status;
        private String level;
        private String provider;
        private LocalDateTime attemptedAt;
        private LocalDateTime completedAt;
        private String result;
        private String failureReason;
        private List<String> documentsUsed;
        private Integer durationSeconds;
    }
}