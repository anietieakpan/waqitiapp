package com.waqiti.gdpr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for user activity logs in GDPR export
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogsDataDTO {

    private String userId;
    private List<ActivityLogDTO> activityLogs;
    private List<LoginHistoryDTO> loginHistory;
    private List<AuditEventDTO> auditEvents;
    private ActivitySummaryDTO summary;

    // Data retrieval metadata
    private boolean dataRetrievalFailed;
    private String failureReason;
    private boolean requiresManualReview;
    private LocalDateTime retrievedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityLogDTO {
        private String activityId;
        private String activityType;
        private String action;
        private LocalDateTime timestamp;
        private String ipAddress;
        private String userAgent;
        private String location;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginHistoryDTO {
        private LocalDateTime loginAt;
        private String ipAddress;
        private String device;
        private String location;
        private String loginMethod;
        private boolean successful;
        private String failureReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEventDTO {
        private String eventId;
        private String eventType;
        private LocalDateTime occurredAt;
        private String action;
        private String resource;
        private String outcome;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivitySummaryDTO {
        private Integer totalActivities;
        private Integer totalLogins;
        private LocalDateTime firstActivityDate;
        private LocalDateTime lastActivityDate;
        private List<String> mostCommonActions;
    }
}
