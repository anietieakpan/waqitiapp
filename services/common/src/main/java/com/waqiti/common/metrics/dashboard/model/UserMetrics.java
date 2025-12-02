package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * User metrics data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMetrics {
    private Long totalUsers;
    private Long activeUsers;
    private Long newUsers;
    private Long verifiedUsers;
    private Long premiumUsers;
    private Double userGrowthRate;
    private Map<String, Long> usersByCountry;
    private Map<String, Long> usersByTier;
}