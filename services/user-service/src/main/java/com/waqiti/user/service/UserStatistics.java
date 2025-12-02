package com.waqiti.user.service;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User statistics model for caching user metrics
 */
@Data
@Builder
public class UserStatistics {
    private long totalUsers;
    private long activeToday;
    private long newUsersThisWeek;
    private LocalDateTime calculatedAt;
}