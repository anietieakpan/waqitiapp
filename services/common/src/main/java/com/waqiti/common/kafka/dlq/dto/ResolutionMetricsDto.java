package com.waqiti.common.kafka.dlq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionMetricsDto {
    private long totalResolved;
    private double avgResolutionTimeHours;
    private double medianResolutionTimeHours;
    private Map<String, Long> resolutionActionDistribution;
    private Map<String, Long> resolvedByUser;
}
