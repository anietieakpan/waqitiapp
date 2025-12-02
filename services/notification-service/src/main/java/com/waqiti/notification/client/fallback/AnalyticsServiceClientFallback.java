package com.waqiti.notification.client.fallback;

import com.waqiti.notification.client.AnalyticsServiceClient;
import com.waqiti.notification.client.dto.WeeklyActivitySummary;
import com.waqiti.notification.client.dto.UserTransactionStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
public class AnalyticsServiceClientFallback implements AnalyticsServiceClient {
    
    @Override
    public WeeklyActivitySummary getUserWeeklyActivity(UUID userId) {
        log.warn("Fallback: Unable to fetch weekly activity for userId {} from analytics service", userId);
        return WeeklyActivitySummary.builder()
            .userId(userId)
            .weekStart(LocalDateTime.now().minusDays(7))
            .weekEnd(LocalDateTime.now())
            .transactionCount(0)
            .totalSpent(BigDecimal.ZERO)
            .totalReceived(BigDecimal.ZERO)
            .insights(Collections.emptyList())
            .recommendations(Collections.emptyList())
            .build();
    }
    
    @Override
    public UserTransactionStats getUserTransactionStats(UUID userId, LocalDateTime from, LocalDateTime to) {
        log.warn("Fallback: Unable to fetch transaction stats for userId {} from {} to {} from analytics service", 
            userId, from, to);
        return UserTransactionStats.builder()
            .userId(userId)
            .periodStart(from != null ? from : LocalDateTime.now().minusDays(30))
            .periodEnd(to != null ? to : LocalDateTime.now())
            .totalTransactions(0L)
            .totalVolume(BigDecimal.ZERO)
            .totalIncoming(BigDecimal.ZERO)
            .totalOutgoing(BigDecimal.ZERO)
            .dailyStats(Collections.emptyList())
            .categoryStats(Collections.emptyList())
            .merchantStats(Collections.emptyList())
            .build();
    }
    
    @Override
    public MilestoneStatus getUserMilestoneStatus(UUID userId) {
        log.warn("Fallback: Unable to fetch milestone status for userId {} from analytics service", userId);
        MilestoneStatus status = new MilestoneStatus();
        status.totalTransactions = 0;
        status.milestoneReward = "N/A";
        status.nextMilestone = "N/A";
        status.transactionsToNextMilestone = 0;
        return status;
    }
    
    @Override
    public AnniversaryStats getUserAnniversaryStats(UUID userId, int years) {
        log.warn("Fallback: Unable to fetch anniversary stats for userId {} ({} years) from analytics service", 
            userId, years);
        AnniversaryStats stats = new AnniversaryStats();
        stats.yearsSinceJoining = years;
        stats.totalTransactions = 0;
        stats.totalVolume = "$0.00";
        stats.mostUsedFeature = "N/A";
        stats.milestoneReward = "N/A";
        return stats;
    }
}