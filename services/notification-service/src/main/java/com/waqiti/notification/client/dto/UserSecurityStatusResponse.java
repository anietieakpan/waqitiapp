package com.waqiti.notification.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityStatusResponse {
    private UUID userId;
    private String username;
    private String email;
    private LocalDateTime lastSecurityReviewDate;
    private LocalDateTime lastPasswordChangeDate;
    private LocalDateTime lastTwoFactorUpdate;
    private Integer daysSinceLastReview;
    private Integer daysSincePasswordChange;
    private boolean twoFactorEnabled;
    private boolean biometricEnabled;
    private String securityLevel;
    private List<String> securityRecommendations;
    private Integer failedLoginAttempts;
    private LocalDateTime lastFailedLoginDate;
    private List<String> recentSecurityEvents;
    private boolean requiresImmediateReview;
    private String riskScore;
    private LocalDateTime nextScheduledReview;
}