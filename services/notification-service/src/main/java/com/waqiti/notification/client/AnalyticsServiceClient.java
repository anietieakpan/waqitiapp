package com.waqiti.notification.client;

import com.waqiti.notification.client.dto.WeeklyActivitySummary;
import com.waqiti.notification.client.dto.UserTransactionStats;
import com.waqiti.notification.client.fallback.AnalyticsServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(
    name = "analytics-service",
    fallback = AnalyticsServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface AnalyticsServiceClient {
    
    @GetMapping("/api/v1/analytics/user/{userId}/weekly-summary")
    WeeklyActivitySummary getUserWeeklyActivity(@PathVariable("userId") UUID userId);
    
    @GetMapping("/api/v1/analytics/user/{userId}/transaction-stats")
    UserTransactionStats getUserTransactionStats(
        @PathVariable("userId") UUID userId,
        @RequestParam(value = "from", required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(value = "to", required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    );
    
    @GetMapping("/api/v1/analytics/user/{userId}/milestone-status")
    MilestoneStatus getUserMilestoneStatus(@PathVariable("userId") UUID userId);
    
    @GetMapping("/api/v1/analytics/user/{userId}/anniversary-stats")
    AnniversaryStats getUserAnniversaryStats(
        @PathVariable("userId") UUID userId,
        @RequestParam("years") int years
    );
    
    /**
     * Data transfer objects for analytics responses
     */
    class MilestoneStatus {
        public int totalTransactions;
        public String milestoneReward;
        public String nextMilestone;
        public int transactionsToNextMilestone;
    }
    
    class AnniversaryStats {
        public int yearsSinceJoining;
        public long totalTransactions;
        public String totalVolume;
        public String mostUsedFeature;
        public String milestoneReward;
    }
}