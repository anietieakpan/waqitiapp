package com.waqiti.analytics.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Analytics request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsRequest {
    private UUID userId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private List<String> categories;
    private List<String> metrics;
    private String timeGranularity; // HOURLY, DAILY, WEEKLY, MONTHLY
    private boolean includeForecasts;
    private boolean includeAnomalies;
    private boolean includeBehaviorAnalysis;
}