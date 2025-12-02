package com.waqiti.common.kafka.dlq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for dashboard statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private long totalCases;
    private long pendingCases;
    private long inReviewCases;
    private long resolvedToday;
    private long criticalCases;
    private double avgResolutionTime; // in hours
}
