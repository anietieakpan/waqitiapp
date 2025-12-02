package com.waqiti.reconciliation.dto;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AnalyticsRequestDto {
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final List<String> accountIds;
    private final String timeFrame; // HOURLY, DAILY, WEEKLY, MONTHLY
    private final List<String> metrics; // Which metrics to include
    private final boolean includeDetails;
    private final String groupBy; // account, currency, etc.
    
    public boolean isValidTimeFrame() {
        return timeFrame != null && 
            List.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY").contains(timeFrame.toUpperCase());
    }
    
    public boolean isValidDateRange() {
        return startDate != null && endDate != null && 
            startDate.isBefore(endDate);
    }
}