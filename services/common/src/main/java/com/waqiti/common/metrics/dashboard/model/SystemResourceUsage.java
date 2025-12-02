package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * System resource usage metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemResourceUsage {
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private Double networkIn;
    private Double networkOut;
    private Long threadCount;
    private Long openFileDescriptors;
    private Map<String, Double> resourceByContainer;
}