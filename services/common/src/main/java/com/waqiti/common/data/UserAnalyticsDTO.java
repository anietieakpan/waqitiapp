package com.waqiti.common.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * User Analytics DTO for user behavior analysis
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class UserAnalyticsDTO {
    private String userId;
    private String userType;
    private LocalDateTime lastLoginDate;
    private int loginCount30Days;
    private int transactionCount30Days;
    private BigDecimal totalSpend30Days;
    private String preferredPaymentMethod;
    private String mostUsedMerchantCategory;
    private Map<String, Integer> channelUsage;
    private double engagementScore;
    private String riskProfile;
    private LocalDateTime joinDate;
}